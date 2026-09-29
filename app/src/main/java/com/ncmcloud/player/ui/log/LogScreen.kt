package com.ncmcloud.player.ui.log

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ncmcloud.player.core.log.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    var logs by remember { mutableStateOf(AppLogger.getLogText()) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("诊断日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { logs = AppLogger.getLogText() }) { Text("刷新") }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(logs))
                    }) { Text("复制") }
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, logs)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享日志"))
                    }) { Text("分享") }
                    TextButton(onClick = {
                        AppLogger.clearLogs()
                        logs = AppLogger.getLogText()
                    }) { Text("清空") }
                },
            )
        },
    ) { padding ->
        if (logs.isBlank()) {
            Text(
                "暂无日志",
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(logs.split("\n")) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}


