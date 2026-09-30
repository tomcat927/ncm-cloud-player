package com.ncmcloud.player.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.koin.core.context.GlobalContext

class UpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val notificationId = 0x7D
    private val channelId = "update_download"

    override suspend fun doWork(): Result {
        val info = UpdateInfo(
            tagName = inputData.getString("tagName").orEmpty(),
            versionCode = inputData.getLong("versionCode", 0L),
            downloadUrl = inputData.getString("downloadUrl").orEmpty(),
            fallbackDownloadUrl = inputData.getString("fallbackDownloadUrl").orEmpty(),
            checksumUrl = inputData.getString("checksumUrl").orEmpty(),
            fallbackChecksumUrl = inputData.getString("fallbackChecksumUrl").orEmpty(),
            releaseUrl = inputData.getString("releaseUrl").orEmpty(),
            releaseNotes = inputData.getString("releaseNotes"),
        )
        setForeground(createForegroundInfo(-1f))
        val updateService = GlobalContext.get().get<UpdateService>()
        return try {
            updateService.downloadAndInstall(info) { progress ->
                runCatching { setProgress(workDataOf("progress" to progress)) }
                runCatching { setForeground(createForegroundInfo(progress)) }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: "下载或安装失败")))
        }
    }

    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "应用更新", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("正在下载更新")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
        if (progress < 0) {
            builder.setContentText("下载中…")
                .setProgress(0, 0, true)
        } else {
            val percent = (progress * 100).toInt()
            builder.setContentText("$percent%")
                .setProgress(100, percent, false)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, builder.build())
        }
    }
}
