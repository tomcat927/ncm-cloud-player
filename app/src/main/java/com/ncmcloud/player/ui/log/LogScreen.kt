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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.core.log.RemoteLogConfig
import com.ncmcloud.player.core.log.RemoteLogService
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val remoteLogService = remember { GlobalContext.get().get<RemoteLogService>() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var logs by remember { mutableStateOf(AppLogger.getLogText()) }
    var message by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf(RemoteLogConfig(false, "", "", "/ncm-cloud-player/logs", null, null)) }

    LaunchedEffect(Unit) {
        config = runCatching { remoteLogService.loadConfig() }.getOrDefault(config)
    }

    if (showSettings) {
        RemoteLogSettingsDialog(
            initial = config,
            onDismiss = { showSettings = false },
            onSave = { enabled, baseUrl, username, password, targetPath ->
                scope.launch {
                    runCatching {
                        remoteLogService.saveConfig(enabled, baseUrl, username, password, targetPath)
                    }.onSuccess {
                        config = remoteLogService.loadConfig()
                        message = "已保存远程日志配置"
                        showSettings = false
                    }.onFailure { message = it.message ?: "保存失败" }
                }
            },
        )
    }

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
                        message = "已复制到剪贴板"
                    }) { Text("复制") }
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, logs)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享日志"))
                    }) { Text("分享") }
                    IconButton(onClick = {
                        scope.launch {
                            message = "上传中…"
                            val result = runCatching { remoteLogService.uploadDiagnosticLog() }
                            message = result.fold(
                                onSuccess = { "已上传：${it.remotePath} (${it.bytes} 字节)" },
                                onFailure = { it.message ?: "上传失败" },
                            )
                        }
                    }) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "上传远程日志")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "远程日志配置")
                    }
                    TextButton(onClick = {
                        AppLogger.clearLogs()
                        logs = AppLogger.getLogText()
                    }) { Text("清空") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (logs.isBlank()) {
                Text(
                    "暂无日志",
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteLogSettingsDialog(
    initial: RemoteLogConfig,
    onDismiss: () -> Unit,
    onSave: (Boolean, String, String, String, String) -> Unit,
) {
    val remoteLogService = remember { GlobalContext.get().get<RemoteLogService>() }
    val scope = rememberCoroutineScope()
    var testMessage by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf("") }
    var targetPath by remember { mutableStateOf(initial.targetPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("远程日志（OpenList）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Text("启用远程上传")
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("OpenList 地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（留空保持原密码）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetPath,
                    onValueChange = { targetPath = it },
                    label = { Text("远程目录") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    scope.launch {
                        testMessage = "测试中…"
                        testMessage = runCatching {
                            remoteLogService.testConnection(baseUrl, username, password)
                            "连接成功"
                        }.getOrElse { it.message ?: "连接失败" }
                    }
                }) { Text("测试连接") }
                testMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(enabled, baseUrl, username, password, targetPath) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

