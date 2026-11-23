package com.hydrogen.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps Hydrogen's process running while a reply is still arriving, so switching apps does
 * not freeze the process and abort the connection mid-reply. It holds no data and does no
 * work itself: the chat request still runs in the ViewModel. It is started only while the app
 * is in the foreground (when a send begins) and stopped as soon as no reply is in flight.
 */
class ReplyKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Replies in progress", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_reply)
            .setContentTitle("Orbit is replying")
            .setContentText("Your reply keeps arriving while you use other apps.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hydrogen_replies"
        private const val NOTIFICATION_ID = 4101

        /**
         * Starts or stops the keep-alive; failures (e.g. background start limits) are ignored.
         *
         * Uses startService, not startForegroundService. A send only begins while the app is on screen, so a plain
         * start is allowed, and the service promotes itself in onStartCommand. startForegroundService instead
         * makes Android kill the whole app if the reply ends (and stopService runs) before onStartCommand gets
         * to call startForeground, which happened on a real phone with a fast reply.
         */
        fun setActive(context: Context, active: Boolean) {
            val intent = Intent(context, ReplyKeepAliveService::class.java)
            runCatching {
                if (active) context.startService(intent) else context.stopService(intent)
            }
        }
    }
}
