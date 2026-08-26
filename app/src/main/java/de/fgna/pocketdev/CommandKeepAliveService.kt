package de.fgna.pocketdev

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import de.fgna.pocketdev.ssh.SshjCommandExecutor

class CommandKeepAliveService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var removeActiveListener: (() -> Unit)? = null
    private var lastActiveCount = 0

    private val stopWhenIdle = Runnable {
        if (SshjCommandExecutor.activeUserCommandCount() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        removeActiveListener = SshjCommandExecutor.addActiveCommandListener(::onActiveCommandCountChanged)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mainHandler.removeCallbacks(stopWhenIdle)
        showForegroundNotification(SshjCommandExecutor.activeUserCommandCount().coerceAtLeast(1))
        if (SshjCommandExecutor.activeUserCommandCount() == 0) {
            mainHandler.postDelayed(stopWhenIdle, STARTUP_GRACE_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(stopWhenIdle)
        removeActiveListener?.invoke()
        removeActiveListener = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onActiveCommandCountChanged(count: Int) {
        mainHandler.post {
            lastActiveCount = count
            mainHandler.removeCallbacks(stopWhenIdle)
            if (count > 0) {
                showForegroundNotification(count)
            } else {
                // Project pre-check/bootstrap and the real command can hand off through a
                // very short zero-command gap. Debounce shutdown so that hand-off does
                // not drop foreground protection between the two SSH sessions.
                mainHandler.postDelayed(stopWhenIdle, IDLE_DEBOUNCE_MS)
            }
        }
    }

    private fun showForegroundNotification(activeCount: Int = lastActiveCount.coerceAtLeast(1)) {
        val openPocketDev = Intent(this, InteractiveMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openPocketDev,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val count = activeCount.coerceAtLeast(1)
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
        private const val STARTUP_GRACE_MS = 3_000L
        private const val IDLE_DEBOUNCE_MS = 1_000L

        fun ensureRunning(context: Context) {
            context.startForegroundService(Intent(context, CommandKeepAliveService::class.java))
        }
    }
}
