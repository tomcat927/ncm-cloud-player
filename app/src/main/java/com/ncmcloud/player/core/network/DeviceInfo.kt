package com.ncmcloud.player.core.network

import android.os.Build

internal object DeviceInfo {
    val brand: String get() = Build.BRAND
    val model: String get() = Build.MODEL
    val board: String get() = Build.BOARD
    val osRelease: String get() = Build.VERSION.RELEASE
}
