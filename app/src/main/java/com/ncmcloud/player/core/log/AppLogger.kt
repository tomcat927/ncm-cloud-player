package com.ncmcloud.player.core.log

import android.util.Log
import com.ncmcloud.player.core.AppEnvironment

object AppLogger {
    fun d(tag: String, msg: String, tr: Throwable? = null) {
        if (AppEnvironment.isDebug) Log.d(tag, msg, tr)
    }
    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
    }
    fun w(tag: String, msg: String, tr: Throwable? = null) {
        Log.w(tag, msg, tr)
    }
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(tag, msg, tr)
    }
}
