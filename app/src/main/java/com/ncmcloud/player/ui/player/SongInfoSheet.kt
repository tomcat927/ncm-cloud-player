package com.ncmcloud.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ncmcloud.player.core.preferences.SettingsPreferences
import com.ncmcloud.player.playback.NowPlaying
import com.ncmcloud.player.playback.PlayerController
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 网易接口的 level 取值 → 展示文案，顺序即菜单顺序
private data class QualityOption(val level: String, val label: String, val desc: String)

private val QualityOptions = listOf(
    QualityOption("standard", "标准", "128kbps"),
    QualityOption("higher", "较高", "192kbps"),
    QualityOption("exhigh", "极高", "320kbps"),
    QualityOption("lossless", "无损", "FLAC"),
    QualityOption("hires", "Hi-Res", "高解析度无损"),
)

/**
 * 播放页三个点菜单：歌曲元信息 + 播放音质切换。
 * 音质只在装载歌曲取地址时读取，切换后从下一首（或重新播放当前首）生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoSheet(
    playerController: PlayerController,
    onDismiss: () -> Unit,
) {
    val nowPlaying = playerController.nowPlaying.collectAsState().value ?: return
    val song = nowPlaying.song
    val duration by playerController.duration.collectAsState()
    val settingsPreferences = remember { GlobalContext.get().get<SettingsPreferences>() }
    val playQuality by settingsPreferences.playQuality.collectAsState(initial = "exhigh")
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("歌曲信息", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            InfoRow("标题", song.displayTitle)
            InfoRow("歌手", song.displayArtist)
            InfoRow("专辑", song.album.ifBlank { "未知专辑" })
            InfoRow("格式", formatLabel(song.fileName))
            InfoRow("时长", formatDuration(duration))
            InfoRow("文件码率", if (song.bitrate > 0) "${song.bitrate / 1000} kbps" else "未知")
            InfoRow("文件大小", formatFileSize(song.fileSize))
            InfoRow("实际下发", actualStreamLabel(nowPlaying))
            Text(
                "实际下发 = 本次取址服务端返回的码率与格式，与文件码率对比可确认音质档位是否生效",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow("加入云盘", formatDate(song.addTime))
            InfoRow("匹配状态", matchLabel(song.matchType))

            Spacer(modifier = Modifier.height(6.dp))
            Text("播放音质", style = MaterialTheme.typography.titleMedium)
            Text(
                "当前：${QualityOptions.firstOrNull { it.level == playQuality }?.label ?: playQuality}，切换后下一首歌生效",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QualityOptions.forEach { option ->
                val selected = playQuality == option.level
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            scope.launch { settingsPreferences.setPlayQuality(option.level) }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            option.desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 点击由整行处理，RadioButton 本身不再响应
                    RadioButton(selected = selected, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatLabel(fileName: String): String =
    fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase() ?: "未知"

// 服务端本次实际返回的码率/格式/大小，任一缺失就跳过，全缺则显示未知
private fun actualStreamLabel(nowPlaying: NowPlaying): String = buildList {
    nowPlaying.actualBitrate.takeIf { it > 0 }?.let { add("${it / 1000} kbps") }
    nowPlaying.actualType?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    nowPlaying.actualSize.takeIf { it > 0 }?.let { add(formatFileSize(it)) }
}.joinToString(" · ").ifBlank { "未知" }

private fun formatDate(timeMs: Long): String =
    if (timeMs <= 0L) {
        "未知"
    } else {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timeMs))
    }

// 真机核实过 matched/unmatched 两种取值，其余原样展示
private fun matchLabel(matchType: String): String = when (matchType) {
    "matched" -> "已匹配"
    "unmatched" -> "未匹配"
    "" -> "未知"
    else -> matchType
}
