package com.ncmcloud.player.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun UpdateDialog() {
    val viewModel: UpdateViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismiss() },
                title = { Text("发现新版本 ${s.info.tagName}") },
                text = {
                    Text(s.info.releaseNotes ?: "点击更新下载并安装最新版本。")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.download() }) { Text("更新") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismiss() }) { Text("稍后") }
                },
            )
        }
        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("正在下载更新") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (s.progress < 0) {
                            CircularProgressIndicator()
                            Text("下载中…", style = MaterialTheme.typography.bodySmall)
                        } else {
                            val percent = (s.progress * 100).toInt()
                            LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("$percent%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {},
            )
        }
        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismiss() },
                title = { Text("更新失败") },
                text = { Text(s.message, color = MaterialTheme.colorScheme.error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismiss() }) { Text("关闭") }
                },
            )
        }
        else -> {}
    }
}
