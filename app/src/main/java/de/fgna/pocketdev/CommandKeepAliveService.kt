package de.fgna.pocketdev

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class CommandKeepAliveService : Service() {
    private val activeProjects = linkedSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectId = intent?.getStringExtra(EXTRA_PROJECT_ID)?.takeIf { it.isNotBlank() }
        when (intent?.action) {
            ACTION_COMMAND_STARTED -> {
                if (projectId != null) activeProjects += projectId
                showForegroundNotification()
            }
            ACTION_COMMAND_FINISHED -> {
                if (projectId != null) activeProjects -= projectId
                if (activeProjects.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    showForegroundNotification()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showForegroundNotification() {
        val openPocketDev = Intent(this, InteractiveMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openPocketDev,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val count = activeProjects.size.coerceAtLeast(1)
        val notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("PocketDev · command running")
            .setContentText(if (count == 1) "Remote command is still running" else "$count remote commands are still running")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(android.app.Notification.CATEGORY_PROGRESS)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Running commands",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps PocketDev remote commands running while the app is in the background."
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "pocketdev_running_commands"
        private const val NOTIFICATION_ID = 1201
        private const val ACTION_COMMAND_STARTED = "de.fgna.pocketdev.COMMAND_STARTED"
        private const val ACTION_COMMAND_FINISHED = "de.fgna.pocketdev.COMMAND_FINISHED"
        private const val EXTRA_PROJECT_ID = "projectId"

        fun commandStarted(context: Context, projectId: String) {
            val intent = Intent(context, CommandKeepAliveService::class.java)
                .setAction(ACTION_COMMAND_STARTED)
                .putExtra(EXTRA_PROJECT_ID, projectId)
            context.startForegroundService(intent)
        }

        fun commandFinished(context: Context, projectId: String) {
            val intent = Intent(context, CommandKeepAliveService::class.java)
                .setAction(ACTION_COMMAND_FINISHED)
                .putExtra(EXTRA_PROJECT_ID, projectId)
            context.startService(intent)
        }
    }
}
