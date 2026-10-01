package com.ncmcloud.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncmcloud.player.playback.LyricLine
import com.ncmcloud.player.playback.lyricLineKey
import kotlinx.coroutines.delay
import kotlin.math.abs

// ControlsInactive 与播放详情页同色系
private val LyricInactive = Color(0xFFB3B3B3)

/**
 * 逐行歌词列表：当前行自动弹簧行居中、点击行跳转播放；手动拖动暂停跟随 4 秒。
 * 返回封面的点击手势由外层歌词页容器处理（加载中/无歌词态没有列表，不能依赖列表滚动事件）。
 */
@Composable
fun LyricsList(
    lines: List<LyricLine>?,
    currentIndex: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var userScrolling by remember { mutableStateOf(false) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrolling = true
        } else if (userScrolling) {
            delay(4000L)
            userScrolling = false
        }
    }

    LaunchedEffect(currentIndex, userScrolling, lines) {
        if (userScrolling || currentIndex < 0 || lines.isNullOrEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == currentIndex }
        if (item == null) {
            listState.animateScrollToItem(currentIndex)
        } else {
            val delta = (item.offset + item.size / 2f) -
                (info.viewportStartOffset + info.viewportEndOffset) / 2f
            if (abs(delta) > 1f) springScrollBy(listState, delta)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewportHeightPx = it.height.toFloat() },
    ) {
        when {
            lines == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }

            lines.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无歌词", color = LyricInactive, fontSize = 15.sp)
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                // 上下各留半个视口，首尾行也能滚动到屏幕中央
                contentPadding = PaddingValues(
                    vertical = with(LocalDensity.current) { (viewportHeightPx / 2f).toDp() },
                ),
            ) {
                itemsIndexed(lines, key = { index, line -> lyricLineKey(index, line) }) { index, line ->
                    LyricLineRow(
                        line = line,
                        isCurrent = index == currentIndex,
                        onClick = { onSeek(line.timeMs) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "lyricLineScale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isCurrent) 1f else 0.72f
                transformOrigin = TransformOrigin.Center
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            line.text,
            color = if (isCurrent) Color.White else LyricInactive,
            fontSize = if (isCurrent) 20.sp else 17.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        line.translation?.takeIf { it.isNotBlank() }?.let { translation ->
            Text(
                translation,
                color = if (isCurrent) Color.White.copy(alpha = 0.72f) else LyricInactive.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// 弹簧逐帧滚动到目标位移（目标 = 当前行中心与视口中心的差值）。
// 必须包在 LazyListState.scroll {} 里：滚动作用域的 scrollBy 是非挂起成员，
// 才能在 animateTo 的逐帧回调里调用（模式抄自 Melodia springScrollToCentre）
private suspend fun springScrollBy(state: LazyListState, deltaPx: Float) {
    var last = 0f
    state.scroll {
        Animatable(0f).animateTo(
            deltaPx,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) {
            val delta = value - last
            scrollBy(delta)
            last = value
        }
    }
}
