package com.ncmcloud.player.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ncmcloud.player.domain.PlayMode
import com.ncmcloud.player.playback.PlayerController
import java.util.Locale

private val DetailBackdrop = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF3C1616),
        Color(0xFF261314),
        Color(0xFF181112),
    ),
)

private val ControlsInactive = Color(0xFFB3B3B3)

@Composable
fun PlayerDetailScreen(
    playerController: PlayerController,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val nowPlaying by playerController.nowPlaying.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val canNext by playerController.canSkipNext.collectAsState()
    val canPrev by playerController.canSkipPrevious.collectAsState()
    val currentPosition by playerController.currentPosition.collectAsState()
    val duration by playerController.duration.collectAsState()
    val queue by playerController.queue.collectAsState()
    val currentIndex by playerController.currentIndex.collectAsState()
    val playMode by playerController.playMode.collectAsState()

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    val song = nowPlaying?.song ?: return

    LaunchedEffect(song.songId) {
        isSeeking = false
        seekPosition = 0f
    }

    val progress = if (duration > 0L) {
        if (isSeeking) seekPosition else (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DetailBackdrop),
    ) {
        if (song.albumPicUrl.isNotBlank()) {
            AsyncImage(
                model = song.albumPicUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.24f,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF3C1616).copy(alpha = 0.82f),
                            0.55f to Color(0xFF261314).copy(alpha = 0.72f),
                            1f to Color(0xFF181112).copy(alpha = 0.92f),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailIconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起播放页",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "NOW PLAYING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Text(
                        "云盘歌曲",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                DetailIconButton(onClick = onOpenQueue) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = "播放队列",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (song.albumPicUrl.isNotBlank()) {
                    AsyncImage(
                        model = song.albumPicUrl,
                        contentDescription = song.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(62.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                song.displayTitle,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 34.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                song.displayArtist,
                fontSize = 15.sp,
                color = ControlsInactive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.album.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    song.album,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(9.dp))
            Text(
                "${song.bitrate / 1000} kbps · ${formatFileSize(song.fileSize)} · 第 ${currentIndex + 1}/${queue.size} 首",
                fontSize = 12.sp,
                color = ControlsInactive.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(26.dp))
            DetailProgressSection(
                progress = progress,
                enabled = duration > 0L,
                onSeek = { value ->
                    isSeeking = true
                    seekPosition = value
                },
                onSeekFinished = { value ->
                    if (duration > 0L) {
                        playerController.seekTo((value * duration).toLong())
                    }
                    isSeeking = false
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatDuration(if (isSeeking) (seekPosition * duration).toLong() else currentPosition),
                    fontSize = 13.sp,
                    color = ControlsInactive,
                )
                Text(
                    formatDuration(duration),
                    fontSize = 13.sp,
                    color = ControlsInactive,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val context = LocalContext.current
                DetailIconButton(
                    onClick = {
                        val newMode = playerController.cyclePlayMode()
                        Toast.makeText(context, newMode.label, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        playMode.icon(),
                        contentDescription = playMode.label,
                        tint = if (playMode == PlayMode.ORDER) {
                            Color.White.copy(alpha = 0.45f)
                        } else {
                            Color.White
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                DetailIconButton(
                    onClick = { playerController.skipToPrevious() },
                    enabled = canPrev,
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { playerController.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.Black,
                        modifier = Modifier.size(42.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                DetailIconButton(
                    onClick = { playerController.skipToNext() },
                    enabled = canNext,
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(44.dp))
            }
        }
    }
}

@Composable
private fun DetailIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DetailProgressSection(
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    onSeekFinished: (Float) -> Unit,
) {
    var rawPosition by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    fun updateSeek(value: Float) {
        rawPosition = value.coerceIn(0f, 1f)
        onSeek(rawPosition)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        if (trackWidthPx <= 0f) return@detectTapGestures
                        updateSeek(offset.x / trackWidthPx)
                        if (tryAwaitRelease()) {
                            onSeekFinished(rawPosition)
                        }
                    },
                )
            }
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = enabled,
                state = rememberDraggableState { delta ->
                    if (trackWidthPx <= 0f) return@rememberDraggableState
                    updateSeek(rawPosition + delta / trackWidthPx)
                },
                onDragStarted = {
                    if (trackWidthPx > 0f && enabled) {
                        updateSeek(rawPosition)
                    }
                },
                onDragStopped = {
                    if (enabled) {
                        onSeekFinished(rawPosition)
                    }
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.24f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White),
        )
        if (trackWidthPx > 0f) {
            val thumbSizePx = with(density) { 12.dp.toPx() }
            val offset = with(density) {
                ((progress * trackWidthPx - thumbSizePx / 2f).coerceIn(0f, trackWidthPx - thumbSizePx)).toDp()
            }
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .size(12.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

private fun formatDuration(timeMs: Long): String {
    if (timeMs <= 0L) return "00:00"
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "未知大小"
    val megabytes = sizeBytes / 1024f / 1024f
    return if (megabytes >= 1f) {
        String.format(Locale.US, "%.1f MB", megabytes)
    } else {
        String.format(Locale.US, "%.0f KB", sizeBytes / 1024f)
    }
}
