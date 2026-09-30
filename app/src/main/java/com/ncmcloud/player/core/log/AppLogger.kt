package com.ncmcloud.player.core.log

import android.content.Context
import android.util.Log
import com.ncmcloud.player.BuildConfig
import com.ncmcloud.player.core.AppEnvironment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_LINES = 2000
    private val buffer = ArrayDeque<String>()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val versionTag = "v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
    private val lock = Any()

    fun init(context: Context) {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        logFile = File(dir, "app_log.txt")
    }

    fun d(tag: String, msg: String, tr: Throwable? = null) = log("D", tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null) = log("I", tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log("W", tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log("E", tag, msg, tr)

    private fun log(level: String, tag: String, msg: String, tr: Throwable? = null) {
        if (level == "D" && !AppEnvironment.isDebug) return
        val time = dateFormat.format(Date())
        val text = buildString {
            append("[$time][$level][$tag][$versionTag] $msg")
            if (tr != null) append("\n").append(tr.stackTraceToString())
        }
        if (AppEnvironment.isDebug || level != "D") {
            val priority = when (level) {
                "E" -> Log.ERROR
                "W" -> Log.WARN
                "I" -> Log.INFO
                else -> Log.DEBUG
            }
            Log.println(priority, tag, msg)
        }
        synchronized(lock) {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(text)
            runCatching {
                logFile?.let { FileWriter(it, true).use { it.appendLine(text) } }
            }
        }
    }

    fun getLogText(): String = synchronized(lock) {
        buffer.joinToString("\n")
    }

    fun clearLogs(): Boolean = synchronized(lock) {
        buffer.clear()
        logFile?.let { it.delete(); it.createNewFile() } ?: false
    }
}
