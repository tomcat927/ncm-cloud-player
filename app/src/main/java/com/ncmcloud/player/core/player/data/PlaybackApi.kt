package com.ncmcloud.player.core.player.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface PlaybackApi {
    @POST("/eapi/song/enhance/player/url/v1")
    suspend fun getSongUrl(
        @Body body: SongUrlRequest
    ): SongUrlResponse
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
