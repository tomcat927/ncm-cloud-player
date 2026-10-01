package com.ncmcloud.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.domain.PlayMode
import com.ncmcloud.player.domain.CloudSong
import com.ncmcloud.player.playback.PlayerController
import org.koin.core.context.GlobalContext

@Composable
fun PlayerBar(
    modifier: Modifier = Modifier,
    onOpenDetail: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val playerController = remember { GlobalContext.get().get<PlayerController>() }
    val nowPlaying by playerController.nowPlaying.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val canNext by playerController.canSkipNext.collectAsState()
    val canPrev by playerController.canSkipPrevious.collectAsState()

    val song = nowPlaying?.song ?: return

    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp)
                .clickable(onClick = onOpenDetail)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniPlayerCover(
                coverUrl = song.albumPicUrl,
                contentDescription = song.displayTitle,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = {
                AppLogger.i("UI", "点击:迷你条-播放队列")
                onOpenQueue()
            }) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "播放队列")
            }
            IconButton(onClick = {
                AppLogger.i("UI", "点击:迷你条-上一首")
                playerController.skipToPrevious()
            }, enabled = canPrev) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
            }
            IconButton(onClick = {
                AppLogger.i("UI", "点击:迷你条-播放暂停(播放中=$isPlaying)")
                playerController.togglePlay()
            }) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                )
            }
            IconButton(onClick = {
                AppLogger.i("UI", "点击:迷你条-下一首")
                playerController.skipToNext()
            }, enabled = canNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
            }
            IconButton(onClick = {
                AppLogger.i("UI", "点击:迷你条-停止")
                playerController.stop()
            }) {
                Icon(Icons.Filled.Stop, contentDescription = "停止")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueSheet(
    playerController: PlayerController,
    onDismiss: () -> Unit,
) {
    val queue by playerController.queue.collectAsState()
    val currentIndex by playerController.currentIndex.collectAsState()
    val playMode by playerController.playMode.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "播放队列",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        // chip 文字本身即时更新为当前模式，无需额外弹层反馈
                        modifier = Modifier.clickable { playerController.cyclePlayMode() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            playMode.icon(),
                            contentDescription = playMode.label,
                            tint = if (playMode == PlayMode.ORDER) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            playMode.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (playMode == PlayMode.ORDER) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
            itemsIndexed(queue, key = { index, item -> "${item.songId}-$index" }) { index, song ->
                QueueRow(
                    song = song,
                    isCurrent = index == currentIndex,
                    onClick = {
                        AppLogger.i("UI", "点击:队列-第${index + 1}首《${song.displayTitle}》")
                        playerController.playAtIndex(index)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerCover(coverUrl: String, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueRow(song: CloudSong, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Text("播放中", style = MaterialTheme.typography.labelSmall)
        }
    }
}
