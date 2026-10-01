package com.ncmcloud.player.data

import com.ncmcloud.player.core.model.Album
import com.ncmcloud.player.domain.CloudSong
import com.ncmcloud.player.feature.cloud.data.CloudApi
import com.ncmcloud.player.feature.cloud.data.CloudListRequest

// 云盘概览：count/size/maxSize 由列表接口随每页返回，是全盘值（不随分页变化）
data class CloudDiskSummary(
    val songCount: Int,
    val usedBytes: Long,
    val totalBytes: Long,
)

data class CloudPage(
    val songs: List<CloudSong>,
    val summary: CloudDiskSummary,
)

class CloudRepository(private val cloudApi: CloudApi) {
    suspend fun getCloudSongs(limit: Int = 100, offset: Int = 0): CloudPage {
        val resp = cloudApi.getCloudSongs(CloudListRequest(limit = limit, offset = offset))
        if (!resp.isSuccess) throw IllegalStateException("云盘接口返回 code=${resp.code}")
        val songs = resp.data.map { item ->
            val artists = item.simpleSong.ar
            val album: Album = item.simpleSong.al
            val artistName = item.artist.ifBlank { artists.joinToString("/") { it.name } }
            CloudSong(
                songId = item.songId,
                songName = item.songName,
                artist = artistName,
                album = item.album.ifBlank { album.name },
                fileName = item.fileName,
                fileSize = item.fileSize,
                bitrate = item.bitrate,
                addTime = item.addTime,
                matchType = item.matchType,
                simpleSongId = item.songId,
                albumPicUrl = album.picUrl,
            )
        }
        return CloudPage(
            songs = songs,
            summary = CloudDiskSummary(
                songCount = resp.count,
                usedBytes = resp.size,
                totalBytes = resp.maxSize,
            ),
        )
    }
}
