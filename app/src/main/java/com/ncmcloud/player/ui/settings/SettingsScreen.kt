package com.ncmcloud.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncmcloud.player.BuildConfig
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.core.preferences.SettingsPreferences
import com.ncmcloud.player.ui.cloud.CloudViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val viewModel: CloudViewModel = koinViewModel()
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 系统返回键回到云盘列表，而不是退出应用
    BackHandler { onBack() }

    val settingsPreferences = remember { GlobalContext.get().get<SettingsPreferences>() }
    val scope = rememberCoroutineScope()
    val lyricCacheEnabled by settingsPreferences.lyricCacheEnabled.collectAsState(initial = true)
    val lyricTranslationEnabled by settingsPreferences.lyricTranslationEnabled.collectAsState(initial = true)
    val lyricFontSize by settingsPreferences.lyricFontSize.collectAsState(
        initial = SettingsPreferences.DEFAULT_LYRIC_FONT_SIZE,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AboutHeader()
            }

            item {
                SettingsSection(title = "应用") {
                    SettingsActionRow(
                        icon = Icons.Filled.BugReport,
                        title = "诊断日志",
                        subtitle = "查看、复制或上传远程日志",
                        onClick = onOpenLogs,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsActionRow(
                        icon = Icons.Filled.SystemUpdateAlt,
                        title = "检查更新",
                        subtitle = "获取最新的热更新包",
                        onClick = onCheckUpdate,
                    )
                }
            }

            item {
                SettingsSection(title = "歌词") {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Storage,
                        title = "歌词缓存",
                        subtitle = "按曲目缓存歌词，开启后切歌不再重复请求",
                        checked = lyricCacheEnabled,
                        onCheckedChange = { scope.launch { settingsPreferences.setLyricCacheEnabled(it) } },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsSwitchRow(
                        icon = Icons.Filled.Subtitles,
                        title = "显示翻译",
                        subtitle = "在歌词行下方显示译文",
                        checked = lyricTranslationEnabled,
                        onCheckedChange = { scope.launch { settingsPreferences.setLyricTranslationEnabled(it) } },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsSliderRow(
                        icon = Icons.Filled.FormatSize,
                        title = "歌词字号",
                        valueText = "${lyricFontSize}sp",
                        value = lyricFontSize.toFloat(),
                        valueRange = 16f..26f,
                        steps = 4,
                        showReset = lyricFontSize != SettingsPreferences.DEFAULT_LYRIC_FONT_SIZE,
                        onReset = {
                            scope.launch {
                                settingsPreferences.setLyricFontSize(SettingsPreferences.DEFAULT_LYRIC_FONT_SIZE)
                            }
                        },
                        onFinished = { scope.launch { settingsPreferences.setLyricFontSize(it) } },
                    )
                }
            }

            item {
                SettingsSection(title = "账号") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogoutDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "退出登录",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "退出后需要重新登录网易云账号",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录？") },
            text = {
                Text("退出后需要重新登录才能继续使用云盘歌曲。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                        onBack()
                    },
                ) {
                    Text(
                        "退出登录",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun AboutHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                "ncm-cloud-player",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                versionLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                AppLogger.i("UI", "点击:设置-$title")
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                AppLogger.i("UI", "点击:设置-$title=$it")
                onCheckedChange(it)
            },
        )
    }
}

@Composable
private fun SettingsSliderRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    showReset: Boolean,
    onReset: () -> Unit,
    onFinished: (Int) -> Unit,
) {
    // 拖动过程只更新本地值，松手才落盘，避免 DataStore 被连续写入
    var sliderValue by remember(value) { mutableStateOf(value) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 15.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        valueText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showReset) {
                        Text(
                            "恢复默认",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onReset),
                        )
                    }
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onFinished(sliderValue.roundToInt()) },
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}

private fun versionLabel(): String =
    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
