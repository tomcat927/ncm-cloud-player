package com.ncmcloud.player.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ncmcloud.player.BuildConfig
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun UpdateDialog() {
    val viewModel: UpdateViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setAppForeground(true)
                    viewModel.refreshInstallPermission()
                }

                Lifecycle.Event.ON_PAUSE -> viewModel.setAppForeground(false)

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val s = state) {
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismiss() },
                title = { Text("发现新版本 ${s.info.tagName}") },
                text = { Text(s.info.releaseNotes ?: "点击更新下载并安装最新版本。") },
                confirmButton = {
                    TextButton(onClick = { viewModel.download() }) { Text("更新") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismiss() }) { Text("稍后") }
                },
            )
        }

        is UpdateState.NoUpdate -> {
            // 已是最新版本：顶部短暂提示后自动消失，无需点击关闭
            LaunchedEffect(Unit) {
                delay(2000)
                viewModel.dismiss()
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 72.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        "已是最新版本 ${BuildConfig.VERSION_NAME}",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
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

        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismiss() },
                title = { Text("更新已下载完成") },
                text = { Text("${s.tagName} 已就绪，是否立即安装？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.install() }) { Text("立即安装") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismiss() }) { Text("稍后") }
                },
            )
        }

        is UpdateState.InstallPermissionRequired -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismiss() },
                title = { Text("需要安装权限") },
                text = {
                    Text("系统要求先允许本应用安装未知应用。授权后返回，我会自动继续拉起安装。")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.openInstallPermissionSettings() }) { Text("去授权") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismiss() }) { Text("稍后") }
                },
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
