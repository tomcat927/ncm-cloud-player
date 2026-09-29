package com.ncmcloud.player.data

import com.ncmcloud.player.core.player.data.PlaybackApi
import com.ncmcloud.player.core.player.data.SongUrlRequest

class PlaybackRepository(private val playbackApi: PlaybackApi) {
    suspend fun getSongUrl(songId: Long, level: String = "exhigh"): String? {
        val resp = playbackApi.getSongUrl(SongUrlRequest(ids = songId.toString(), level = level))
        if (!resp.isSuccess) throw IllegalStateException("播放地址接口返回 code=${resp.code}")
        return resp.data.firstOrNull { !it.url.isNullOrEmpty() }?.url
    }
}
