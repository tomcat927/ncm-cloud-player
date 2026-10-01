package com.ncmcloud.player.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncmcloud.player.domain.CloudSong
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 拖动超过容器宽度这个比例，或松手时甩动速度超过阈值，判定为确认切歌（与 Melodia 一致）
private const val SwipeConfirmFraction = 0.32f
private const val SwipeConfirmVelocityPx = 1200f

// 确认切换滑出/未达阈值回弹共用同一档弹簧手感
private val SwipeCoverSpringSpec: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private enum class SwipeDirection { NEXT, PREVIOUS }

private class SwipeCoverLayerSpec(
    val layerKey: String,
    val song: CloudSong,
    // 参数名不能叫 translationX，否则会遮蔽 graphicsLayer 作用域里的同名属性
    val translationXProvider: () -> Float,
)

/**
 * 封面左右滑动切歌（复刻 Melodia SwipeToSkipCover 的手势与观感）：
 * 当前/上一首/下一首三张封面各自跟手平移；确认后停在滑出终点，
 * 等播放数据（currentSong）真正到位再把位移归零，避免请求期间画面硬跳。
 */
@Composable
fun SwipeToSkipCover(
    currentSong: CloudSong,
    previousSong: CloudSong?,
    nextSong: CloudSong?,
    onConfirmPrevious: () -> Unit,
    onConfirmNext: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape,
    onCancelPending: () -> Boolean = { false },
) {
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // 手指按住拖拽期间 offsetX 完全交给手势，下面的数据同步逻辑不能插手
    var isDragActive by remember { mutableStateOf(false) }
    var isTransitioning by remember { mutableStateOf(false) }
    var isDragConfirmed by remember { mutableStateOf(false) }

    // 过渡期间冻结三张预览封面：队列索引往往先于播放数据更新，不冻结会被提前顶掉
    var displayedCurrentSong by remember { mutableStateOf(currentSong) }
    var displayedPreviousSong by remember { mutableStateOf(previousSong) }
    var displayedNextSong by remember { mutableStateOf(nextSong) }
    // 按钮/自动切歌触发时旧封面的滑出动画信息
    var outgoingSong by remember { mutableStateOf<CloudSong?>(null) }
    var outgoingDirection by remember { mutableStateOf(SwipeDirection.NEXT) }
    var pendingSlideIn by remember { mutableStateOf(false) }

    if (!isTransitioning) {
        val prevMatches = previousSong?.songId == currentSong.songId
        val nextMatches = nextSong?.songId == currentSong.songId
        if (prevMatches || nextMatches) {
            isTransitioning = true
        } else {
            displayedCurrentSong = currentSong
            displayedPreviousSong = previousSong
            displayedNextSong = nextSong
        }
    }
    val canSwipeToPrevious = displayedPreviousSong != null
    val canSwipeToNext = displayedNextSong != null

    var syncedSongId by remember { mutableStateOf(currentSong.songId) }
    if (!isDragActive && settleJob?.isActive != true && syncedSongId != currentSong.songId) {
        val direction = if (isDragConfirmed) {
            null
        } else when {
            displayedNextSong?.songId == currentSong.songId -> SwipeDirection.NEXT
            displayedPreviousSong?.songId == currentSong.songId -> SwipeDirection.PREVIOUS
            else -> null
        }
        if (direction != null) {
            outgoingSong = displayedCurrentSong
            outgoingDirection = direction
            offsetX = when (direction) {
                SwipeDirection.NEXT -> containerWidthPx
                SwipeDirection.PREVIOUS -> -containerWidthPx
            }
            pendingSlideIn = true
        } else {
            outgoingSong = null
            offsetX = 0f
        }
        displayedCurrentSong = currentSong
        displayedPreviousSong = previousSong
        displayedNextSong = nextSong
        syncedSongId = currentSong.songId
        isTransitioning = false
        isDragConfirmed = false
    }
    LaunchedEffect(pendingSlideIn) {
        if (pendingSlideIn) {
            animate(offsetX, 0f, 0f, SwipeCoverSpringSpec) { value, _ ->
                offsetX = value
            }
            outgoingSong = null
            pendingSlideIn = false
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .then(
                if (containerWidthPx > 0f && (canSwipeToPrevious || canSwipeToNext)) {
                    Modifier.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            settleJob?.cancel()
                            // 位移最多跟到上一首/下一首完全到位为止
                            val maxNext = if (canSwipeToNext) containerWidthPx else 0f
                            val maxPrevious = if (canSwipeToPrevious) containerWidthPx else 0f
                            offsetX = (offsetX + delta).coerceIn(-maxNext, maxPrevious)
                        },
                        onDragStarted = { isDragActive = true },
                        onDragStopped = { velocity ->
                            isDragActive = false
                            val threshold = containerWidthPx * SwipeConfirmFraction
                            val confirmNext = canSwipeToNext &&
                                (offsetX < -threshold || velocity < -SwipeConfirmVelocityPx)
                            val confirmPrevious = canSwipeToPrevious &&
                                (offsetX > threshold || velocity > SwipeConfirmVelocityPx)
                            // 未再次确认但已有已确认的切歌在途，说明是在快速滑回取消
                            val wasTransitioning = isTransitioning
                            if (confirmNext || confirmPrevious) {
                                isTransitioning = true
                                isDragConfirmed = true
                            }
                            settleJob = scope.launch {
                                when {
                                    confirmNext -> {
                                        animate(offsetX, -containerWidthPx, velocity, SwipeCoverSpringSpec) { value, _ ->
                                            offsetX = value
                                        }
                                        onConfirmNext()
                                    }
                                    confirmPrevious -> {
                                        animate(offsetX, containerWidthPx, velocity, SwipeCoverSpringSpec) { value, _ ->
                                            offsetX = value
                                        }
                                        onConfirmPrevious()
                                    }
                                    else -> {
                                        animate(offsetX, 0f, velocity, SwipeCoverSpringSpec) { value, _ ->
                                            offsetX = value
                                        }
                                        if (wasTransitioning) {
                                            if (onCancelPending()) {
                                                isTransitioning = false
                                                isDragConfirmed = false
                                            } else {
                                                isDragConfirmed = false
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        val current = displayedCurrentSong
        val outgoing = outgoingSong
        val layers = buildList {
            if (outgoing != null) {
                when (outgoingDirection) {
                    SwipeDirection.NEXT -> {
                        add(SwipeCoverLayerSpec("${outgoing.songId}-outgoing", outgoing) { offsetX - containerWidthPx })
                        add(SwipeCoverLayerSpec("${current.songId}-current", current) { offsetX })
                        displayedNextSong?.let {
                            add(SwipeCoverLayerSpec("${it.songId}-next", it) { offsetX + containerWidthPx })
                        }
                    }
                    SwipeDirection.PREVIOUS -> {
                        displayedPreviousSong?.let {
                            add(SwipeCoverLayerSpec("${it.songId}-prev", it) { offsetX - containerWidthPx })
                        }
                        add(SwipeCoverLayerSpec("${current.songId}-current", current) { offsetX })
                        add(SwipeCoverLayerSpec("${outgoing.songId}-outgoing", outgoing) { offsetX + containerWidthPx })
                    }
                }
            } else {
                displayedPreviousSong?.let {
                    add(SwipeCoverLayerSpec("${it.songId}-prev", it) { offsetX - containerWidthPx })
                }
                add(SwipeCoverLayerSpec("${current.songId}-current", current) { offsetX })
                displayedNextSong?.let {
                    add(SwipeCoverLayerSpec("${it.songId}-next", it) { offsetX + containerWidthPx })
                }
            }
        }
        layers.forEach { layer ->
            key(layer.layerKey) {
                SwipeCoverLayer(
                    song = layer.song,
                    translationXProvider = layer.translationXProvider,
                    shape = shape,
                )
            }
        }
    }
}

@Composable
private fun SwipeCoverLayer(
    song: CloudSong,
    translationXProvider: () -> Float,
    shape: Shape,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = translationXProvider() }
            .aspectRatio(1f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (song.albumPicUrl.isNotBlank()) {
            AsyncImage(
                model = song.albumPicUrl,
                contentDescription = song.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(62.dp),
            )
        }
    }
}
