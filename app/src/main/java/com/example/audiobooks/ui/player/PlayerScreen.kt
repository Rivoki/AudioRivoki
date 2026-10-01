package com.example.audiobooks.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.audiobooks.R
import com.example.audiobooks.data.db.model.BookWithTracks
import com.example.audiobooks.player.PlayerController
import com.example.audiobooks.ui.BookNameFormatter
import com.example.audiobooks.ui.theme.LocalIsDarkMode
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    currentBook: BookWithTracks?,
    lastPlayed: PlayerController.LastPlayedState?,
    nowPlaying: PlayerController.NowPlayingState?,
    listenedProgress: Map<Long, Float>,
    onTrackSelect: (Long) -> Unit
) {
    val isDarkMode = LocalIsDarkMode.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Плеер", style = MaterialTheme.typography.titleLarge)
        Text(currentBook?.book?.title?.let { BookNameFormatter.toCyrillicAnalog(it) } ?: "Книга не выбрана")

        if (lastPlayed != null) {
            val bookName = BookNameFormatter.toCyrillicAnalog(lastPlayed.bookTitle)
            Text("Последнее: $bookName — ${lastPlayed.trackTitle}")
        }

        if (currentBook != null) {
            Text("Треки", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentBook.tracks.sortedBy { it.track.sortOrder }, key = { it.track.id }) { track ->
                    val trackId = track.track.id
                    val isCurrent = nowPlaying?.trackId == trackId
                    val listenedRatio = listenedProgress[trackId] ?: 0f
                    val isListened = listenedRatio >= 0.6f && !isCurrent
                    val rowColor = when {
                        isCurrent -> if (isDarkMode) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        isListened -> if (isDarkMode) {
                            Color(0xFF66BB6A).copy(alpha = 0.42f)
                        } else {
                            Color(0xFF4CAF50).copy(alpha = 0.25f)
                        }
                        else -> Color.Transparent
                    }
                    Text(
                        text = "${track.track.sortOrder + 1}. ${BookNameFormatter.toCyrillicAnalog(track.track.title)}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowColor, RoundedCornerShape(8.dp))
                            .clickable { onTrackSelect(track.track.id) }
                            .padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun StickyPlayerControls(
    player: Player?,
    isPlaying: Boolean,
    nowPlaying: PlayerController.NowPlayingState?,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onSeekBack10: () -> Unit,
    onSeekForward10: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val isDarkMode = LocalIsDarkMode.current
    var timelinePositionMs by remember { mutableLongStateOf(0L) }
    var timelineDurationMs by remember { mutableLongStateOf(1L) }
    val sliderValue = remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            val duration = player?.duration?.takeIf { it > 0L } ?: 1L
            val position = player?.currentPosition ?: 0L
            timelineDurationMs = duration
            timelinePositionMs = position
            if (!isScrubbing) {
                sliderValue.floatValue = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            delay(300)
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (player != null) {
                Text(if (isPlaying) "📕 ⏸" else "📕 ▶", style = MaterialTheme.typography.titleMedium)
                if (nowPlaying != null) {
                    Text(
                        text = "Сейчас: ${BookNameFormatter.toCyrillicAnalog(nowPlaying.trackTitle)} " +
                            "из папки ${BookNameFormatter.toCyrillicAnalog(nowPlaying.folderName)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text("Выберите книгу для воспроизведения.")
            }

            Slider(
                value = sliderValue.floatValue,
                onValueChange = {
                    isScrubbing = true
                    sliderValue.floatValue = it
                },
                onValueChangeFinished = {
                    val seekPosition = (sliderValue.floatValue * timelineDurationMs).toLong()
                    onSeekTo(seekPosition)
                    isScrubbing = false
                },
                enabled = player != null,
                colors = SliderDefaults.colors(
                    inactiveTrackColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(timelinePositionMs), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(timelineDurationMs), style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerImageButton(
                    drawableRes = if (isDarkMode) R.drawable.back_icon else R.drawable.back_icon_dark,
                    contentDescription = "Предыдущий трек",
                    enabled = player != null,
                    onClick = onPrev
                )
                PlayerImageButton(
                    drawableRes = if (isDarkMode) R.drawable.tenback_icon else R.drawable.tenback_icon_dark,
                    contentDescription = "Назад 10 секунд",
                    enabled = player != null,
                    onClick = onSeekBack10
                )
                PlayerImageButton(
                    drawableRes = if (isDarkMode) {
                        if (isPlaying) R.drawable.pause_icon else R.drawable.play_icon
                    } else {
                        if (isPlaying) R.drawable.pause_icon_dark else R.drawable.play_icon_dark
                    },
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    enabled = player != null,
                    onClick = onTogglePlayPause
                )
                PlayerImageButton(
                    drawableRes = if (isDarkMode) R.drawable.tenforw_icon else R.drawable.tenforw_icon_dark,
                    contentDescription = "Вперёд 10 секунд",
                    enabled = player != null,
                    onClick = onSeekForward10
                )
                PlayerImageButton(
                    drawableRes = if (isDarkMode) R.drawable.forward_icon else R.drawable.forward_icon_dark,
                    contentDescription = "Следующий трек",
                    enabled = player != null,
                    onClick = onNext
                )
            }
        }
    }
}

@Composable
private fun PlayerImageButton(
    drawableRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp)
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
