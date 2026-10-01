package com.ncmcloud.player.data

import android.content.Context
import com.ncmcloud.player.core.player.data.LyricResponse
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 歌词磁盘缓存：云盘曲目稳定，按 songId 缓存原始歌词响应，避免每次切歌重复请求。
 * 文件位于 filesDir/lyrics/<songId>.json；缓存关闭时读写都会跳过（不主动清文件）。
 */
class LyricsCache(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dir: File
        get() = File(context.filesDir, "lyrics").apply { mkdirs() }

    fun load(songId: Long): LyricResponse? = runCatching {
        val file = File(dir, "$songId.json")
        if (!file.exists()) return null
        json.decodeFromString<LyricResponse>(file.readText())
    }.getOrNull()

    fun save(songId: Long, response: LyricResponse) {
        runCatching {
            File(dir, "$songId.json").writeText(json.encodeToString(LyricResponse.serializer(), response))
        }
    }

    fun clear() {
        runCatching { dir.deleteRecursively() }
    }
}
