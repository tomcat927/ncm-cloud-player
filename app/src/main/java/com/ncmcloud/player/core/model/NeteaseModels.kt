package com.ncmcloud.player.core.model

import kotlinx.serialization.Serializable

@Serializable
class EmptyBody

@Serializable
data class Artist(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val img1v1Url: String = "",
)

@Serializable
data class Album(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
)

@Serializable
data class Track(
    val id: Long = 0,
    val name: String = "",
    val ar: List<Artist> = emptyList(),
    val al: Album = Album(),
    val fee: Int = 0,
    val dt: Long = 0,
)
