package com.ncmcloud.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ncmcloud.player.playback.PlayerController
import org.koin.core.context.GlobalContext

@Composable
fun PlayerBar(modifier: Modifier = Modifier) {
    val playerController = remember { GlobalContext.get().get<PlayerController>() }
    val nowPlaying by playerController.nowPlaying.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()

    if (nowPlaying == null) return

    Surface(modifier = modifier, tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                nowPlaying?.song?.displayTitle.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { playerController.togglePlay() }) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                )
            }
            IconButton(onClick = { playerController.stop() }) {
                Icon(Icons.Filled.Stop, contentDescription = "停止")
            }
        }
    }
}
