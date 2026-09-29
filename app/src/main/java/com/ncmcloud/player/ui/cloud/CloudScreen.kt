package com.ncmcloud.player.ui.cloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ncmcloud.player.domain.CloudSong
import com.ncmcloud.player.ui.player.PlayerBar
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen() {
    val viewModel: CloudViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("云盘歌曲") }) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is CloudState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is CloudState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        IconButton(onClick = { viewModel.loadFirstPage() }) {
                            Text("重试")
                        }
                    }
                }
                is CloudState.Content -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.songs, key = { it.songId }) { song ->
                            CloudSongRow(
                                song = song,
                                onClick = { viewModel.play(song) },
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                IconButton(onClick = { viewModel.loadNextPage() }) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
            }
            PlayerBar(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}

@Composable
private fun CloudSongRow(song: CloudSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
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
        Text("${song.bitrate / 1000}kbps", style = MaterialTheme.typography.labelSmall)
    }
}
