package com.ncmcloud.player.playback

import com.ncmcloud.player.core.player.data.LyricResponse
import kotlin.math.abs

data class WordInfo(
    val text: String,
    // 相对整行起始的偏移
    val startOffsetMs: Long,
    val durationMs: Long,
)

data class LyricLine(
    val timeMs: Long,
    val durationMs: Long = 0,
    val text: String,
    val translation: String? = null,
    val roma: String? = null,
    // LRC 行为空；YRC 行带逐字时间
    val words: List<WordInfo> = emptyList(),
)

// LazyColumn 的稳定 key：时间戳 + 下标，防同时间戳行 key 冲突
fun lyricLineKey(index: Int, line: LyricLine): String = "${line.timeMs}_$index"

// 二分查找 positionMs 所在的歌词行下标，无命中返回 -1
fun List<LyricLine>.indexOfLineAt(positionMs: Long): Int {
    var lo = 0
    var hi = lastIndex
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].timeMs <= positionMs) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return ans
}

// 解析器与合并逻辑参照 Melodia（shared/.../core/player/domain/LyricParser.kt）
object LyricParser {

    private val YrcLineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    // 注意：元组右括号与字符类内的左括号都必须转义——Android 的 ICU 正则不接受裸括号
    // （桌面版 Java 正则允许，CI 编译期发现不了），否则 PatternSyntaxException 会让整个解析器失效
    private val YrcWordRegex = Regex("""\((\d+),(\d+),\d+\)([^\(\n]+)""")
    private val LrcTimeTagRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun fromResponse(resp: LyricResponse): List<LyricLine> {
        if (resp.noLyric) return listOf(LyricLine(timeMs = 0L, text = "纯音乐，请欣赏"))
        if (resp.uncollected) return emptyList()
        val yrc = resp.yrc?.lyric?.takeIf { it.isNotBlank() }?.let(::parseYrc) ?: emptyList()
        val lines = yrc.ifEmpty {
            resp.lrc?.lyric?.takeIf { it.isNotBlank() }?.let(::parseLrc) ?: emptyList()
        }
        if (lines.isEmpty()) return emptyList()
        // YRC 场景优先用配套的 ytlrc 翻译
        val translationText = if (yrc.isNotEmpty()) {
            resp.ytlrc?.lyric?.takeIf { it.isNotBlank() } ?: resp.tlyric?.lyric?.takeIf { it.isNotBlank() }
        } else {
            resp.tlyric?.lyric?.takeIf { it.isNotBlank() }
        }
        val translations = translationText?.let(::parseLrc) ?: emptyList()
        val romaLines = resp.romalrc?.lyric?.takeIf { it.isNotBlank() }?.let(::parseLrc) ?: emptyList()
        return lines.map { line ->
            line.copy(
                translation = nearestText(line.timeMs, translations),
                roma = nearestText(line.timeMs, romaLines),
            )
        }
    }

    fun parseYrc(content: String): List<LyricLine> =
        content.lines().mapNotNull { raw ->
            val lineMatch = YrcLineRegex.find(raw.trim()) ?: return@mapNotNull null
            val start = lineMatch.groupValues[1].toLong()
            val duration = lineMatch.groupValues[2].toLong()
            val words = YrcWordRegex.findAll(lineMatch.groupValues[3]).map { wordMatch ->
                WordInfo(
                    text = wordMatch.groupValues[3],
                    startOffsetMs = (wordMatch.groupValues[1].toLong() - start).coerceAtLeast(0L),
                    durationMs = wordMatch.groupValues[2].toLong(),
                )
            }.toList()
            val text = words.joinToString("") { it.text }.trim()
            if (text.isEmpty()) null else LyricLine(timeMs = start, durationMs = duration, text = text, words = words)
        }.sortedBy { it.timeMs }

    fun parseLrc(content: String): List<LyricLine> =
        content.lines().flatMap { raw ->
            val tags = LrcTimeTagRegex.findAll(raw).toList()
            if (tags.isEmpty()) return@flatMap emptyList()
            val text = raw.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) return@flatMap emptyList()
            tags.map { tag ->
                LyricLine(timeMs = tagToMs(tag), text = text)
            }
        }.sortedBy { it.timeMs }

    private fun tagToMs(tag: MatchResult): Long {
        val minutes = tag.groupValues[1].toLong()
        val seconds = tag.groupValues[2].toLong()
        val fraction = tag.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1000L + fractionMs
    }

    // 翻译/罗马音按时间戳就近匹配，容差 150ms（与 Melodia 一致）
    private fun nearestText(timeMs: Long, candidates: List<LyricLine>): String? =
        candidates.minByOrNull { abs(it.timeMs - timeMs) }
            ?.takeIf { abs(it.timeMs - timeMs) < 150L && it.text.isNotBlank() }
            ?.text
}
