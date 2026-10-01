package com.ncmcloud.player.domain

/**
 * 播放模式：顺序播放为默认（播完停止），列表循环与随机播完绕回，
 * 单曲循环交给 ExoPlayer 的 REPEAT_MODE_ONE 原生处理。
 */
enum class PlayMode(val label: String) {
    ORDER("顺序播放"),
    LIST_LOOP("列表循环"),
    SINGLE_LOOP("单曲循环"),
    SHUFFLE("随机播放");

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): PlayMode =
            entries.firstOrNull { it.name == name } ?: ORDER
    }
}

data class CloudSong(
    val songId: Long,
    val songName: String,
    val artist: String,
    val album: String,
    val fileName: String,
    val fileSize: Long,
    val bitrate: Int,
    val addTime: Long,
    val matchType: String,
    val simpleSongId: Long,
    val albumPicUrl: String,
) {
    val displayTitle: String get() = songName.ifBlank { fileName }
    val displayArtist: String get() = artist.ifBlank { "未知艺人" }
}
