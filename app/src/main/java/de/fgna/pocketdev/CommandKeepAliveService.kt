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
import de.fgna.pocketdev.transfer.TransferActivityRegistry

class CommandKeepAliveService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var removeCommandListener: (() -> Unit)? = null
    private var removeTransferListener: (() -> Unit)? = null
    private var lastCommandCount = 0
    private var lastTransferCount = 0

    private val stopWhenIdle = Runnable {
        if (SshjCommandExecutor.activeUserCommandCount() == 0 && TransferActivityRegistry.activeCount() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        removeCommandListener = SshjCommandExecutor.addActiveCommandListener { count ->
            onActivityCountChanged(commandCount = count, transferCount = TransferActivityRegistry.activeCount())
        }
        removeTransferListener = TransferActivityRegistry.addListener { count ->
            onActivityCountChanged(commandCount = SshjCommandExecutor.activeUserCommandCount(), transferCount = count)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_COMMANDS) {
            SshjCommandExecutor.cancelAllActiveUserCommands()
            mainHandler.removeCallbacks(stopWhenIdle)
            val transfers = TransferActivityRegistry.activeCount()
            if (transfers > 0) {
                showForegroundNotification(commandCount = 0, transferCount = transfers)
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        mainHandler.removeCallbacks(stopWhenIdle)
        val commands = SshjCommandExecutor.activeUserCommandCount()
        val transfers = TransferActivityRegistry.activeCount()
        val transferHint = intent?.getBooleanExtra(EXTRA_TRANSFER_HINT, false) == true
        when {
            commands > 0 || transfers > 0 -> showForegroundNotification(commands, transfers)
            transferHint -> showForegroundNotification(commandCount = 0, transferCount = 1)
            else -> showForegroundNotification(commandCount = 1, transferCount = 0)
        }
        if (commands == 0 && transfers == 0) {
            mainHandler.postDelayed(stopWhenIdle, STARTUP_GRACE_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(stopWhenIdle)
        removeCommandListener?.invoke()
        removeTransferListener?.invoke()
        removeCommandListener = null
        removeTransferListener = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onActivityCountChanged(commandCount: Int, transferCount: Int) {
        mainHandler.post {
            lastCommandCount = commandCount
            lastTransferCount = transferCount
            mainHandler.removeCallbacks(stopWhenIdle)
            if (commandCount > 0 || transferCount > 0) {
                showForegroundNotification(commandCount, transferCount)
            } else {
                // Command pre-check/bootstrap and transfer startup can hand off through a short
                // zero-work gap. Debounce shutdown so foreground protection is not dropped.
                mainHandler.postDelayed(stopWhenIdle, IDLE_DEBOUNCE_MS)
            }
        }
    }

    private fun showForegroundNotification(
        commandCount: Int = lastCommandCount,
        transferCount: Int = lastTransferCount,
    ) {
        val openPocketDev = Intent(this, InteractiveMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openPocketDev,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopCommands = Intent(this, CommandKeepAliveService::class.java).apply {
            action = ACTION_STOP_COMMANDS
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopCommands,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title: String
        val text: String
        when {
            commandCount > 0 && transferCount > 0 -> {
                title = "PocketDev · background work"
                text = "$commandCount command${if (commandCount == 1) "" else "s"} · $transferCount transfer${if (transferCount == 1) "" else "s"}"
            }
            transferCount > 0 -> {
                title = "PocketDev · transfer running"
                text = if (transferCount == 1) "File transfer is still running" else "$transferCount file transfers are still running"
            }
            else -> {
                val count = commandCount.coerceAtLeast(1)
                title = "PocketDev · command running"
                text = if (count == 1) "Remote command is still running" else "$count remote commands are still running"
            }
        }

        val builder = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(android.app.Notification.CATEGORY_PROGRESS)
        if (commandCount > 0) {
            builder.addAction(android.R.drawable.ic_media_pause, "Stop commands", stopPendingIntent)
        }
        val notification = builder.build()

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
                "Background work",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps PocketDev remote commands and file transfers running in the background."
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "pocketdev_running_commands"
        private const val NOTIFICATION_ID = 1201
        private const val ACTION_STOP_COMMANDS = "de.fgna.pocketdev.action.STOP_COMMANDS"
        private const val EXTRA_TRANSFER_HINT = "transferHint"
        private const val STARTUP_GRACE_MS = 3_000L
        private const val IDLE_DEBOUNCE_MS = 1_000L

        fun ensureRunning(context: Context, transferHint: Boolean = false) {
            val intent = Intent(context, CommandKeepAliveService::class.java)
                .putExtra(EXTRA_TRANSFER_HINT, transferHint)
            context.startForegroundService(intent)
        }
    }
}
