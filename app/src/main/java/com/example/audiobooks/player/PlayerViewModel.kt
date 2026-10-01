package com.example.audiobooks.player

import androidx.lifecycle.ViewModel
import com.example.audiobooks.data.db.model.BookWithTracks

class PlayerViewModel(
    private val playerController: PlayerController
) : ViewModel() {
    val player = playerController.player
    val lastPlayed = playerController.lastPlayed
    val isPlaying = playerController.isPlaying
    val nowPlaying = playerController.nowPlaying
    val listenedProgress = playerController.listenedProgress

    fun loadBook(book: BookWithTracks) {
        val folderResumeState = playerController.getResumeForCollection(book.book.collectionId)
        if (folderResumeState != null && folderResumeState.bookId == book.book.id) {
            playerController.setBookQueue(
                book = book,
                startTrackId = folderResumeState.trackId,
                startPositionMs = folderResumeState.positionMs,
                autoPlay = false
            )
            return
        }

        val resumeState = lastPlayed.value
        if (resumeState != null && resumeState.bookId == book.book.id) {
            playerController.setBookQueue(
                book = book,
                startTrackId = resumeState.trackId,
                startPositionMs = resumeState.positionMs,
                autoPlay = false
            )
        } else {
            playerController.setBookQueue(book)
        }
    }

    fun playTrack(book: BookWithTracks, trackId: Long) {
        playerController.playTrack(book, trackId)
    }

    fun play() = playerController.play()
    fun pause() = playerController.pause()
    fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }
    fun seekNext() = playerController.seekToNext()
    fun seekPrev() = playerController.seekToPrevious()
    fun seekBack10Seconds() = playerController.seekBack10Seconds()
    fun seekForward10Seconds() = playerController.seekForward10Seconds()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun persistPlaybackState() = playerController.persistCurrentPlaybackPosition()

    override fun onCleared() {
        playerController.release()
    }
}
