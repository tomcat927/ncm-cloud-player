package com.ncmcloud.player.data

import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.feature.playlist.data.PlaylistApi
import com.ncmcloud.player.feature.playlist.data.PlaylistCreateRequest
import com.ncmcloud.player.feature.playlist.data.PlaylistDeleteRequest
import com.ncmcloud.player.feature.playlist.data.PlaylistDetailRequest
import com.ncmcloud.player.feature.playlist.data.PlaylistTracksRequest
import com.ncmcloud.player.feature.playlist.data.PlaylistUpdateNameRequest
import com.ncmcloud.player.feature.playlist.data.UserPlaylistsRequest

private const val TAG = "PlaylistRepository"

// 网易服务器端歌单仓库：歌单数据全部存于网易服务端
class PlaylistRepository(private val playlistApi: PlaylistApi) {

    suspend fun getMyPlaylists(uid: Long): List<NeteasePlaylistSummary> {
        val resp = playlistApi.getUserPlaylists(UserPlaylistsRequest(uid = uid))
        if (!resp.isSuccess) throw IllegalStateException("用户歌单接口返回 code=${resp.code}")
        return resp.playlist.map {
            NeteasePlaylistSummary(
                id = it.id,
                name = it.name,
                trackCount = it.trackCount,
                coverUrl = it.coverImgUrl,
            )
        }
    }

    suspend fun getPlaylistDetail(id: Long): NeteasePlaylistDetailData {
        val resp = playlistApi.getPlaylistDetail(PlaylistDetailRequest(id = id))
        if (!resp.isSuccess) throw IllegalStateException("歌单详情接口返回 code=${resp.code}")
        val detail = resp.playlist ?: throw IllegalStateException("歌单详情为空")
        return NeteasePlaylistDetailData(id = detail.id, name = detail.name, tracks = detail.tracks)
    }

    suspend fun addTrack(playlistId: Long, songId: Long) =
        manipulate("add", playlistId, songId)

    suspend fun removeTrack(playlistId: Long, songId: Long) =
        manipulate("del", playlistId, songId)

    suspend fun createPlaylist(name: String): Long {
        val resp = playlistApi.createPlaylist(PlaylistCreateRequest(name = name))
        if (!resp.isSuccess) throw IllegalStateException("创建歌单失败 code=${resp.code}")
        return resp.id
    }

    suspend fun renamePlaylist(id: Long, name: String) {
        val resp = playlistApi.updatePlaylistName(
            PlaylistUpdateNameRequest(id = id, name = name)
        )
        if (!resp.isSuccess) throw IllegalStateException("重命名失败 code=${resp.code}")
    }

    suspend fun deletePlaylist(id: Long) {
        val resp = playlistApi.deletePlaylist(PlaylistDeleteRequest(ids = "[$id]"))
        if (!resp.isSuccess) throw IllegalStateException("删除歌单失败 code=${resp.code}")
    }

    private suspend fun manipulate(op: String, playlistId: Long, songId: Long) {
        val resp = playlistApi.manipulateTracks(
            PlaylistTracksRequest(
                op = op,
                pid = playlistId,
                trackIds = "[$songId]",
            ),
        )
        if (!resp.isSuccess) {
            AppLogger.e(TAG, "歌单歌曲操作失败 op=$op pid=$playlistId songId=$songId code=${resp.code} msg=${resp.message}")
            throw IllegalStateException(resp.message ?: "歌单操作失败 code=${resp.code}")
        }
    }
}

data class NeteasePlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val coverUrl: String,
)

data class NeteasePlaylistDetailData(
    val id: Long,
    val name: String,
    val tracks: List<com.ncmcloud.player.feature.playlist.data.NeteaseTrack>,
)
