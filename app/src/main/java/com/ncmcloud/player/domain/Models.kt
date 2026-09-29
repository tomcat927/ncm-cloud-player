package com.ncmcloud.player.domain

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
