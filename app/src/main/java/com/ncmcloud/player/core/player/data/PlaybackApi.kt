package com.ncmcloud.player.core.player.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import retrofit2.http.Body
import retrofit2.http.POST

interface PlaybackApi {
    @POST("/eapi/song/enhance/player/url/v1")
    suspend fun getSongUrl(
        @Body body: SongUrlRequest
    ): SongUrlResponse

    @POST("/eapi/song/lyric/v1")
    suspend fun getLyrics(
        @Body body: LyricRequest
    ): LyricResponse
}

@Serializable
data class SongUrlRequest(
    val ids: String,
    val level: String = "exhigh",
    val encodeType: String = "flac"
)

@Serializable
data class SongUrlResponse(
    val code: Int = 0,
    val data: List<SongUrlItem> = emptyList(),
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class SongUrlItem(
    val id: Long = 0,
    val url: String? = null,
    val br: Long = 0,
    val size: Long = 0,
    val md5: String? = null,
    val type: String? = null,
)

@Serializable
data class LyricRequest(
    val id: Long,
    val cp: Boolean = false,
    val tv: Int = -1,
    val lv: Int = -1,
    val rv: Int = -1,
    val kv: Int = -1,
    // yv 传 99 才会返回 yrc 逐字歌词
    val yv: Int = 99,
    val ytv: Int = -1,
    val yrv: Int = -1,
)

@Serializable
data class LyricTrack(
    val version: Int = 0,
    val lyric: String = "",
)

@Serializable
data class LyricResponse(
    val lrc: LyricTrack? = null,
    val tlyric: LyricTrack? = null,
    val romalrc: LyricTrack? = null,
    val yrc: LyricTrack? = null,
    val ytlrc: LyricTrack? = null,
    @SerialName("nolyric") val noLyric: Boolean = false,
    val uncollected: Boolean = false,
)
