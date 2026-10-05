package com.ncmcloud.player.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ncmcloud.player.core.log.AppLogger
import org.koin.core.context.GlobalContext

private const val TAG = "UpdateDownloadWorker"

class UpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val progressNotificationId = 0x7D
    private val completeNotificationId = UpdateNotificationReceiver.NOTIFICATION_ID
    private val downloadChannelId = "update_download"
    private val completeChannelId = "update_complete"

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
        AppLogger.i(TAG, "开始更新下载: tag=${info.tagName}, versionCode=${info.versionCode}")
        // App 已在后台时进程被重新拉起重试下载，前台服务启动会被系统拒绝——降级为普通后台任务继续下载
        runCatching { setForeground(createProgressForegroundInfo(-1f)) }
            .onFailure { AppLogger.w(TAG, "前台服务启动失败，按后台任务继续下载", it) }
        val updateService = GlobalContext.get().get<UpdateService>()
        return try {
            val file = updateService.downloadApk(info) { progress ->
                runCatching { setProgress(workDataOf("progress" to progress)) }
                runCatching { setForeground(createProgressForegroundInfo(progress)) }
            }
            AppLogger.i(TAG, "更新下载完成: tag=${info.tagName}, file=${file.absolutePath}, size=${file.length()}")
            showCompleteNotification(file, info.tagName)
            Result.success(
                workDataOf(
                    "apkPath" to file.absolutePath,
                    "tagName" to info.tagName,
                    // 输出带上目标版本号，供启动时过滤跨进程残留的完成记录
                    "versionCode" to info.versionCode,
                ),
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "更新下载失败: tag=${info.tagName}", e)
            Result.failure(workDataOf("error" to (e.message ?: "下载或安装失败")))
        }
    }

    private fun showCompleteNotification(file: java.io.File, tagName: String) {
        val context = applicationContext
        ensureChannel(completeChannelId, "更新下载完成", NotificationManager.IMPORTANCE_HIGH)
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        AppLogger.i(TAG, "准备显示完成通知: enabled=$notificationsEnabled, id=$completeNotificationId")
        val installIntent = GlobalContext.get().get<UpdateService>().createInstallIntent(file)
        val installPending = PendingIntent.getActivity(
            context,
            0,
            installIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val laterIntent = Intent(context, UpdateNotificationReceiver::class.java).apply {
            action = UpdateNotificationReceiver.ACTION_LATER
        }
        val laterPending = PendingIntent.getBroadcast(
            context,
            1,
            laterIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, completeChannelId)
            .setContentTitle("更新已下载完成")
            .setContentText("$tagName 已就绪，点击安装")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(installPending)
            .addAction(0, "稍后安装", laterPending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(completeNotificationId, notification)
        }.onSuccess {
            AppLogger.i(TAG, "完成通知已提交")
        }.onFailure {
            AppLogger.e(TAG, "完成通知提交失败", it)
        }
    }

    private fun createProgressForegroundInfo(progress: Float): ForegroundInfo {
        val context = applicationContext
        ensureChannel(downloadChannelId, "应用更新", NotificationManager.IMPORTANCE_LOW)
        val builder = NotificationCompat.Builder(context, downloadChannelId)
            .setContentTitle("正在下载更新")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
        if (progress < 0) {
            builder.setContentText("下载中…").setProgress(0, 0, true)
        } else {
            val percent = (progress * 100).toInt()
            builder.setContentText("$percent%").setProgress(100, percent, false)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(progressNotificationId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(progressNotificationId, builder.build())
        }
    }

    private fun ensureChannel(id: String, name: String, importance: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(NotificationChannel(id, name, importance))
            AppLogger.i(TAG, "已创建通知渠道: $id")
        }
    }
}
