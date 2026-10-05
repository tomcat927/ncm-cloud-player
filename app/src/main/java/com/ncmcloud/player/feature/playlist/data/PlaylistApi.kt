package com.ncmcloud.player.feature.playlist.data

import com.ncmcloud.player.core.model.Album
import com.ncmcloud.player.core.model.Artist
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 网易云服务器端歌单接口：歌单数据存于网易服务端，重装/换设备不丢。
// 云盘歌曲可直接以云盘 songId 收藏进歌单（与 Melodia 同款用法）。
interface PlaylistApi {

    // 当前登录用户的歌单列表
    @POST("/eapi/user/playlist")
    suspend fun getUserPlaylists(
        @Body body: UserPlaylistsRequest
    ): UserPlaylistsResponse

    // 歌单详情：tracks 字段服务端约 1000 首截断，完整 id 顺序在 trackIds
    @POST("/eapi/v6/playlist/detail")
    suspend fun getPlaylistDetail(
        @Body body: PlaylistDetailRequest
    ): PlaylistDetailResponse

    // 歌单内歌曲添加/移除：op = "add" / "del"
    @POST("/eapi/playlist/manipulate/tracks")
    suspend fun manipulateTracks(
        @Body body: PlaylistTracksRequest
    ): PlaylistTracksResponse

    // 新建歌单
    @POST("/eapi/playlist/create")
    suspend fun createPlaylist(
        @Body body: PlaylistCreateRequest
    ): PlaylistCreateResponse
}

@Serializable
data class UserPlaylistsRequest(
    val uid: Long,
    val limit: Int = 1000,
    val offset: Int = 0,
)

@Serializable
data class UserPlaylistsResponse(
    val code: Int = 0,
    val playlist: List<NeteasePlaylist> = emptyList(),
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class NeteasePlaylist(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String = "",
    val trackCount: Int = 0,
    val userId: Long = 0,
)

@Serializable
data class PlaylistDetailRequest(
    val id: Long,
    val n: Int = 1000,
    val s: Int = 8,
)

@Serializable
data class PlaylistDetailResponse(
    val code: Int = 0,
    val playlist: NeteasePlaylistDetail? = null,
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class NeteasePlaylistDetail(
    val id: Long = 0,
    val name: String = "",
    val trackCount: Int = 0,
    val tracks: List<NeteaseTrack> = emptyList(),
    val trackIds: List<NeteaseTrackId> = emptyList(),
)

@Serializable
data class NeteaseTrackId(val id: Long = 0)

// 歌单内曲目：云盘歌与网易曲库歌混排，统一为简化结构
@Serializable
data class NeteaseTrack(
    val id: Long = 0,
    val name: String = "",
    val ar: List<Artist> = emptyList(),
    val al: Album = Album(),
)

@Serializable
data class PlaylistTracksRequest(
    val op: String, // "add" / "del"
    val pid: Long,
    // 网易要求的格式是 JSON 数组字符串："[123]"
    val trackIds: String,
    val imme: String = "true",
)

@Serializable
data class PlaylistTracksResponse(
    val code: Int = 0,
    val message: String? = null,
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PlaylistCreateRequest(
    val name: String,
    val privacy: Int = 0,
    val type: String = "NORMAL",
)

@Serializable
data class PlaylistCreateResponse(
    val code: Int = 0,
    val id: Long = 0,
) {
    val isSuccess: Boolean get() = code == 200
}
