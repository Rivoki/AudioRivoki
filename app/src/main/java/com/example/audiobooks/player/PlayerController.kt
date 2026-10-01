package com.example.audiobooks.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.os.Process
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerNotificationManager
import com.example.audiobooks.MainActivity
import com.example.audiobooks.R
import com.example.audiobooks.data.db.model.BookWithTracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class PlayerController(context: Context) {
    data class LastPlayedState(
        val collectionId: Long,
        val bookId: Long,
        val trackId: Long,
        val bookTitle: String,
        val trackTitle: String,
        val positionMs: Long
    )

    data class CollectionResumeState(
        val bookId: Long,
        val trackId: Long,
        val positionMs: Long
    )

    data class NowPlayingState(
        val trackId: Long,
        val trackTitle: String,
        val folderName: String
    )

    private val prefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val _lastPlayed = MutableStateFlow(loadLastPlayed())
    val lastPlayed: StateFlow<LastPlayedState?> = _lastPlayed.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
    val nowPlaying: StateFlow<NowPlayingState?> = _nowPlaying.asStateFlow()
    private val _listenedProgress = MutableStateFlow(loadTrackProgress())
    val listenedProgress: StateFlow<Map<Long, Float>> = _listenedProgress.asStateFlow()
    private val _collectionResumeMap = MutableStateFlow(loadCollectionResumeMap())

    private var currentCollectionId: Long? = null
    private var currentBookId: Long? = null
    private var currentBookTitle: String = ""
    private var trackIndexById: Map<Long, Int> = emptyMap()
    private var trackTitleById: Map<Long, String> = emptyMap()
    private var trackPathById: Map<Long, String> = emptyMap()
    private val notificationManager: PlayerNotificationManager
    private val headsetMediaSession: MediaSession
    private var headsetTapCount: Int = 0
    private var headsetTapJob: kotlinx.coroutines.Job? = null

    init {
        createNotificationChannel(context)
        notificationManager = createNotificationManager(context)
        notificationManager.setPlayer(player)
        player.setSeekParameters(SeekParameters.EXACT)
        headsetMediaSession = createHeadsetMediaSession(context)
        headsetMediaSession.isActive = true
        updateMediaSessionPlaybackState()

        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateNowPlayingState()
                    persistCurrentPlaybackPosition()
                    updateTrackProgress()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (!isPlaying) {
                        persistCurrentPlaybackPosition()
                    }
                    updateMediaSessionPlaybackState()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    persistCurrentPlaybackPosition()
                    updateTrackProgress()
                }
            }
        )

        scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    persistCurrentPlaybackPosition()
                    updateTrackProgress()
                }
                delay(1000)
            }
        }
    }

    fun setBookQueue(
        book: BookWithTracks,
        startTrackId: Long? = null,
        startPositionMs: Long? = null,
        autoPlay: Boolean = false
    ) {
        val sortedTracks = book.tracks.sortedBy { it.track.sortOrder }
        val items = sortedTracks.map {
            MediaItem.Builder()
                .setUri(Uri.parse(it.track.filePath))
                .setMediaId(it.track.id.toString())
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.track.title)
                        .setArtist(it.track.artist)
                        .build()
                )
                .build()
        }
        currentCollectionId = book.book.collectionId
        currentBookId = book.book.id
        currentBookTitle = book.book.title
        trackIndexById = sortedTracks.mapIndexed { index, track -> track.track.id to index }.toMap()
        trackTitleById = sortedTracks.associate { it.track.id to it.track.title }
        trackPathById = sortedTracks.associate { it.track.id to it.track.filePath }

        player.setMediaItems(items)
        player.prepare()
        if (startTrackId != null) {
            trackIndexById[startTrackId]?.let { index -> player.seekTo(index, 0L) }
        }
        if (startPositionMs != null && startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        if (autoPlay) player.play()
        updateNowPlayingState()
    }

    fun playTrack(book: BookWithTracks, trackId: Long) {
        if (currentBookId != book.book.id || player.mediaItemCount != book.tracks.size) {
            setBookQueue(book, startTrackId = trackId, startPositionMs = 0L, autoPlay = true)
            return
        }
        val targetIndex = trackIndexById[trackId] ?: return
        player.seekTo(targetIndex, 0L)
        player.play()
        updateNowPlayingState()
        persistCurrentPlaybackPosition()
    }

    fun play() {
        player.play()
        updateNowPlayingState()
        persistCurrentPlaybackPosition()
    }

    fun pause() {
        player.pause()
        persistCurrentPlaybackPosition()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        persistCurrentPlaybackPosition()
    }

    fun seekBack10Seconds() {
        val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
        player.seekTo(target)
        persistCurrentPlaybackPosition()
    }

    fun seekForward10Seconds() {
        val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (player.currentPosition + 10_000L).coerceAtMost(duration)
        player.seekTo(target)
        persistCurrentPlaybackPosition()
    }

    fun seekToNext() {
        player.seekToNextMediaItem()
        persistCurrentPlaybackPosition()
    }

    fun seekToPrevious() {
        player.seekToPreviousMediaItem()
        persistCurrentPlaybackPosition()
    }

    fun release() {
        persistCurrentPlaybackPosition()
        updateTrackProgress()
        scope.cancel()
        headsetTapJob?.cancel()
        headsetMediaSession.isActive = false
        headsetMediaSession.release()
        notificationManager.setPlayer(null)
        player.release()
    }

    fun persistCurrentPlaybackPosition() {
        saveCurrentTrackAsLastPlayed()
    }

    fun getResumeForCollection(collectionId: Long): CollectionResumeState? {
        return _collectionResumeMap.value[collectionId]
    }

    fun clearPlaybackCacheForCollection(collectionId: Long, bookIds: List<Long>, trackIds: List<Long>) {
        if (trackIds.isNotEmpty()) {
            val filteredProgress = _listenedProgress.value.filterKeys { it !in trackIds }
            if (filteredProgress.size != _listenedProgress.value.size) {
                _listenedProgress.value = filteredProgress
                saveTrackProgress(filteredProgress)
            }
        }
        if (_collectionResumeMap.value.containsKey(collectionId)) {
            val updatedResume = _collectionResumeMap.value.toMutableMap().apply { remove(collectionId) }
            _collectionResumeMap.value = updatedResume
            saveCollectionResumeMap(updatedResume)
        }
        val currentLast = _lastPlayed.value
        if (currentLast != null && currentLast.bookId in bookIds) {
            clearLastPlayedCache()
        }
    }

    fun clearAllPlaybackCache() {
        _listenedProgress.value = emptyMap()
        saveTrackProgress(emptyMap())
        _collectionResumeMap.value = emptyMap()
        saveCollectionResumeMap(emptyMap())
        clearLastPlayedCache()
    }

    private fun saveCurrentTrackAsLastPlayed() {
        val collectionId = currentCollectionId ?: return
        val bookId = currentBookId ?: return
        val trackId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val trackTitle = trackTitleById[trackId] ?: return
        val state = LastPlayedState(
            collectionId = collectionId,
            bookId = bookId,
            trackId = trackId,
            bookTitle = currentBookTitle,
            trackTitle = trackTitle,
            positionMs = player.currentPosition.coerceAtLeast(0L)
        )
        prefs.edit()
            .putLong(KEY_LAST_COLLECTION_ID, state.collectionId)
            .putLong(KEY_LAST_BOOK_ID, state.bookId)
            .putLong(KEY_LAST_TRACK_ID, state.trackId)
            .putString(KEY_LAST_BOOK_TITLE, state.bookTitle)
            .putString(KEY_LAST_TRACK_TITLE, state.trackTitle)
            .putLong(KEY_LAST_POSITION_MS, state.positionMs)
            .apply()
        _lastPlayed.value = state

        val updatedResume = _collectionResumeMap.value.toMutableMap().apply {
            put(
                collectionId,
                CollectionResumeState(
                    bookId = state.bookId,
                    trackId = state.trackId,
                    positionMs = state.positionMs
                )
            )
        }
        _collectionResumeMap.value = updatedResume
        saveCollectionResumeMap(updatedResume)
    }

    private fun updateNowPlayingState() {
        val trackId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val trackTitle = trackTitleById[trackId] ?: return
        val folderName = trackPathById[trackId]
            ?.let { File(it).parentFile?.name }
            ?.takeIf { it.isNotBlank() }
            ?: currentBookTitle
        _nowPlaying.value = NowPlayingState(
            trackId = trackId,
            trackTitle = trackTitle,
            folderName = folderName
        )
    }

    private fun updateTrackProgress() {
        val trackId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val duration = player.duration.takeIf { it > 0L } ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val previous = _listenedProgress.value[trackId] ?: 0f
        if (progress > previous) {
            val updated = _listenedProgress.value.toMutableMap()
            updated[trackId] = progress
            _listenedProgress.value = updated
            saveTrackProgress(updated)
        }
    }

    private fun saveTrackProgress(progress: Map<Long, Float>) {
        val encoded = progress.entries.joinToString(separator = ";") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_TRACK_PROGRESS_MAP, encoded).apply()
    }

    private fun saveCollectionResumeMap(map: Map<Long, CollectionResumeState>) {
        val encoded = map.entries.joinToString(separator = ";") { entry ->
            "${entry.key},${entry.value.bookId},${entry.value.trackId},${entry.value.positionMs}"
        }
        prefs.edit().putString(KEY_COLLECTION_RESUME_MAP, encoded).apply()
    }

    private fun loadTrackProgress(): Map<Long, Float> {
        val encoded = prefs.getString(KEY_TRACK_PROGRESS_MAP, null).orEmpty()
        if (encoded.isBlank()) return emptyMap()
        return encoded.split(";").mapNotNull { token ->
            val parts = token.split(":")
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val progress = parts[1].toFloatOrNull() ?: return@mapNotNull null
            id to progress.coerceIn(0f, 1f)
        }.toMap()
    }

    private fun loadCollectionResumeMap(): Map<Long, CollectionResumeState> {
        val encoded = prefs.getString(KEY_COLLECTION_RESUME_MAP, null).orEmpty()
        if (encoded.isBlank()) return emptyMap()
        return encoded.split(";").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size != 4) return@mapNotNull null
            val collectionId = parts[0].toLongOrNull() ?: return@mapNotNull null
            val bookId = parts[1].toLongOrNull() ?: return@mapNotNull null
            val trackId = parts[2].toLongOrNull() ?: return@mapNotNull null
            val positionMs = parts[3].toLongOrNull() ?: return@mapNotNull null
            collectionId to CollectionResumeState(bookId, trackId, positionMs)
        }.toMap()
    }

    private fun createNotificationManager(context: Context): PlayerNotificationManager {
        return PlayerNotificationManager.Builder(
            context,
            NOTIFICATION_ID,
            CHANNEL_ID
        )
            .setMediaDescriptionAdapter(
                object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player): CharSequence {
                        return nowPlaying.value?.folderName ?: context.getString(R.string.app_name)
                    }

                    override fun createCurrentContentIntent(player: Player): PendingIntent? {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        return PendingIntent.getActivity(
                            context,
                            1001,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }

                    override fun getCurrentContentText(player: Player): CharSequence {
                        return "Плеер"
                    }

                    override fun getCurrentLargeIcon(
                        player: Player,
                        callback: PlayerNotificationManager.BitmapCallback
                    ) = null
                }
            )
            .setNotificationListener(
                object : PlayerNotificationManager.NotificationListener {
                    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                        player.pause()
                        persistCurrentPlaybackPosition()
                        Process.killProcess(Process.myPid())
                    }
                }
            )
            .build()
            .apply {
                setUseStopAction(true)
                setUseRewindAction(false)
                setUseFastForwardAction(false)
                setUsePreviousAction(true)
                setUseNextAction(true)
                setUseChronometer(false)
                setSmallIcon(R.drawable.ic_stat_audio)
            }
    }

    private fun createHeadsetMediaSession(context: Context): MediaSession {
        return MediaSession(context, "AudioRivokiHeadsetSession").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(
                object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (event != null &&
                            event.action == KeyEvent.ACTION_UP &&
                            (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK)
                        ) {
                            onHeadsetTap()
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }

                    override fun onPlay() = play()
                    override fun onPause() = pause()
                    override fun onSkipToNext() = seekToNext()
                    override fun onSkipToPrevious() = seekToPrevious()
                }
            )
        }
    }

    private fun onHeadsetTap() {
        headsetTapCount += 1
        headsetTapJob?.cancel()
        headsetTapJob = scope.launch {
            delay(300)
            when {
                headsetTapCount >= 3 -> seekBack10Seconds()
                headsetTapCount == 2 -> seekForward10Seconds()
                else -> if (player.isPlaying) pause() else play()
            }
            headsetTapCount = 0
        }
    }

    private fun updateMediaSessionPlaybackState() {
        val state = if (player.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO
        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, player.currentPosition, 1.0f)
            .build()
        headsetMediaSession.setPlaybackState(playbackState)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AudioRivoki playback",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun loadLastPlayed(): LastPlayedState? {
        val collectionId = prefs.getLong(KEY_LAST_COLLECTION_ID, -1L)
        val bookId = prefs.getLong(KEY_LAST_BOOK_ID, -1L)
        val trackId = prefs.getLong(KEY_LAST_TRACK_ID, -1L)
        val bookTitle = prefs.getString(KEY_LAST_BOOK_TITLE, null)
        val trackTitle = prefs.getString(KEY_LAST_TRACK_TITLE, null)
        val positionMs = prefs.getLong(KEY_LAST_POSITION_MS, 0L)
        return if (
            collectionId > 0 &&
            bookId > 0 &&
            trackId > 0 &&
            !bookTitle.isNullOrBlank() &&
            !trackTitle.isNullOrBlank()
        ) {
            LastPlayedState(collectionId, bookId, trackId, bookTitle, trackTitle, positionMs)
        } else {
            null
        }
    }

    private fun clearLastPlayedCache() {
        prefs.edit()
            .remove(KEY_LAST_COLLECTION_ID)
            .remove(KEY_LAST_BOOK_ID)
            .remove(KEY_LAST_TRACK_ID)
            .remove(KEY_LAST_BOOK_TITLE)
            .remove(KEY_LAST_TRACK_TITLE)
            .remove(KEY_LAST_POSITION_MS)
            .apply()
        _lastPlayed.value = null
    }

    companion object {
        private const val CHANNEL_ID = "audio_rivoki_playback"
        private const val NOTIFICATION_ID = 901
        private const val KEY_LAST_COLLECTION_ID = "last_collection_id"
        private const val KEY_LAST_BOOK_ID = "last_book_id"
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        private const val KEY_LAST_BOOK_TITLE = "last_book_title"
        private const val KEY_LAST_TRACK_TITLE = "last_track_title"
        private const val KEY_LAST_POSITION_MS = "last_position_ms"
        private const val KEY_TRACK_PROGRESS_MAP = "track_progress_map"
        private const val KEY_COLLECTION_RESUME_MAP = "collection_resume_map"
    }
}
