package com.ncmcloud.player.update

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LATER) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        }
    }

    companion object {
        const val ACTION_LATER = "com.ncmcloud.player.action.UPDATE_INSTALL_LATER"
        const val NOTIFICATION_ID = 0x7E
    }
}
