package de.fgna.pocketdev

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
import de.fgna.pocketdev.data.SshProfileRepository
import de.fgna.pocketdev.ssh.LocalPortForwardConfig
import de.fgna.pocketdev.ssh.LocalPortForwardFailure
import de.fgna.pocketdev.ssh.LocalPortForwardSession
import de.fgna.pocketdev.ssh.LocalPortForwardState
import de.fgna.pocketdev.ssh.SshjLocalForwardTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Process-local status survives Activity recreation. A killed process always starts stopped. */
internal object LocalPortForwardRuntime {
    private val mutableState = MutableStateFlow<LocalPortForwardState>(LocalPortForwardState.Stopped)
    val state = mutableState.asStateFlow()
    private var session: LocalPortForwardSession? = null

    @Synchronized fun start(context: Context, config: LocalPortForwardConfig) {
        if (state.value is LocalPortForwardState.Starting || state.value is LocalPortForwardState.Active) return
        lateinit var current: LocalPortForwardSession
        current = LocalPortForwardSession(config, ::SshjLocalForwardTransport) { value ->
            synchronized(this) { if (session === current) mutableState.value = value }
        }
        session = current
        mutableState.value = LocalPortForwardState.Starting(config)
        Thread({
            try {
                // Credentials never enter an Intent, saved UI state or a key file.
                val repository = SshProfileRepository(context.applicationContext)
                val profile = repository.load()?.profile
                val secret = repository.loadSecret()
                if (profile == null || secret.isNullOrBlank()) current.fail(LocalPortForwardFailure.SSH_CREDENTIALS_MISSING)
                else current.run(profile, secret)
            } catch (_: Exception) {
                current.fail(LocalPortForwardFailure.SSH_CONNECTION_FAILED)
            }
        }, "pocketdev-tunnel-session").apply { isDaemon = true }.start()
    }

    fun stop(failure: LocalPortForwardFailure? = null) {
        val current = synchronized(this) { session }
        // SSH disconnect may block; service callbacks and Compose must remain responsive.
        Thread({ if (failure == null) current?.close() else current?.fail(failure) },
            "pocketdev-tunnel-cleanup").apply { isDaemon = true }.start()
    }

    fun reject(config: LocalPortForwardConfig) {
        synchronized(this) {
            if (state.value !is LocalPortForwardState.Active && state.value !is LocalPortForwardState.Starting) {
                mutableState.value = LocalPortForwardState.Failed(config, LocalPortForwardFailure.BACKGROUND_UNAVAILABLE)
            }
        }
    }
}

/** Only owns the explicit tunnel; its Stop action never cancels commands, PTYs or transfers. */
class LocalPortForwardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "SSH browser authentication tunnel", NotificationManager.IMPORTANCE_LOW),
        )
        scope.launch {
            LocalPortForwardRuntime.state.collect { state ->
                if (started) {
                    when (state) {
                        is LocalPortForwardState.Starting, is LocalPortForwardState.Active ->
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(state, true))
                        else -> {
                            stopping = true
                            stopForeground(STOP_FOREGROUND_DETACH)
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(state, false))
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopping = true
            LocalPortForwardRuntime.stop()
            val state = LocalPortForwardRuntime.state.value
            if (!started || (state !is LocalPortForwardState.Starting && state !is LocalPortForwardState.Active)) {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        val config = runCatching {
            LocalPortForwardConfig(
                intent?.getIntExtra("localPort", 1455) ?: 1455,
                intent?.getStringExtra("remoteHost") ?: "127.0.0.1",
                intent?.getIntExtra("remotePort", 1455) ?: 1455,
            )
        }.getOrElse { stopSelf(startId); return START_NOT_STICKY }
        // Never recreate a tunnel from a null redelivery after process death.
        if (intent?.action != ACTION_START) { stopSelf(startId); return START_NOT_STICKY }
        started = true
        stopping = false
        try {
            val existing = LocalPortForwardRuntime.state.value
            val pending = notification(if (existing is LocalPortForwardState.Starting ||
                existing is LocalPortForwardState.Active) existing else LocalPortForwardState.Starting(config), true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION, pending, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else startForeground(NOTIFICATION, pending)
            LocalPortForwardRuntime.start(this, config)
        } catch (_: Exception) {
            LocalPortForwardRuntime.reject(config)
            LocalPortForwardRuntime.stop(LocalPortForwardFailure.BACKGROUND_UNAVAILABLE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopping = true
        LocalPortForwardRuntime.stop()
        // The state collector ends the foreground service after cleanup has completed.
        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopping = true
        LocalPortForwardRuntime.stop(LocalPortForwardFailure.BACKGROUND_STOPPED)
        stopForeground(STOP_FOREGROUND_DETACH)
        val state = LocalPortForwardRuntime.state.value
        val config = when (state) {
            is LocalPortForwardState.Starting -> state.config
            is LocalPortForwardState.Active -> state.config
            else -> null
        }
        if (config != null) getSystemService(NotificationManager::class.java).notify(NOTIFICATION,
            notification(LocalPortForwardState.Failed(config, LocalPortForwardFailure.BACKGROUND_STOPPED), false))
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        if (!stopping) {
            val previous = LocalPortForwardRuntime.state.value
            LocalPortForwardRuntime.stop(LocalPortForwardFailure.BACKGROUND_STOPPED)
            val terminal = when (previous) {
                is LocalPortForwardState.Starting -> LocalPortForwardState.Failed(previous.config, LocalPortForwardFailure.BACKGROUND_STOPPED)
                is LocalPortForwardState.Active -> LocalPortForwardState.Failed(previous.config, LocalPortForwardFailure.BACKGROUND_STOPPED)
                else -> previous
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(terminal, false))
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(state: LocalPortForwardState, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 89, Intent(this, InteractiveMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("PocketDev · SSH tunnel")
            .setContentText(localPortForwardStatus(state))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
        if (ongoing) builder.addAction(android.R.drawable.ic_media_pause, "Stop tunnel",
            PendingIntent.getService(this, 89, Intent(this, LocalPortForwardService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return builder.build()
    }

    companion object {
        private const val CHANNEL = "pocketdev_local_forward"
        private const val NOTIFICATION = 1289
        private const val ACTION_START = "de.fgna.pocketdev.START_TUNNEL"
        private const val ACTION_STOP = "de.fgna.pocketdev.STOP_TUNNEL"

        fun start(context: Context, config: LocalPortForwardConfig) {
            try {
                context.startForegroundService(Intent(context, LocalPortForwardService::class.java).setAction(ACTION_START)
                    .putExtra("localPort", config.localPort).putExtra("remoteHost", config.remoteHost)
                    .putExtra("remotePort", config.remotePort))
            } catch (_: Exception) {
                LocalPortForwardRuntime.reject(config)
            }
        }

        fun stop(context: Context) {
            if (LocalPortForwardRuntime.state.value is LocalPortForwardState.Starting ||
                LocalPortForwardRuntime.state.value is LocalPortForwardState.Active) {
                context.startService(Intent(context, LocalPortForwardService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}

internal fun localPortForwardStatus(state: LocalPortForwardState): String = when (state) {
    LocalPortForwardState.Stopped -> "Tunnel stopped · no local forwarding"
    is LocalPortForwardState.Starting -> "Starting tunnel on 127.0.0.1:${state.config.localPort}…"
    is LocalPortForwardState.Active -> "Active · 127.0.0.1:${state.config.localPort} → ${state.config.remoteHost}:${state.config.remotePort}"
    is LocalPortForwardState.Failed -> when (state.failure) {
        LocalPortForwardFailure.LOCAL_PORT_UNAVAILABLE -> "Tunnel stopped · local port unavailable. Stop the other listener or choose another local port."
        LocalPortForwardFailure.SSH_HOST_UNVERIFIED -> "Tunnel stopped · connect and trust the SSH host in Server first."
        LocalPortForwardFailure.SSH_CREDENTIALS_MISSING -> "Tunnel stopped · save SSH credentials in Server first."
        LocalPortForwardFailure.SSH_CONNECTION_FAILED -> "Tunnel stopped · SSH connection failed. Check network, credentials and pinned host key, then restart."
        LocalPortForwardFailure.SSH_DISCONNECTED -> "Tunnel stopped · SSH disconnected. Check the network and restart the tunnel."
        LocalPortForwardFailure.LISTENER_FAILED -> "Tunnel stopped · forwarding failed. Check the remote listener and SSH AllowTcpForwarding, then restart."
        LocalPortForwardFailure.BACKGROUND_UNAVAILABLE -> "Tunnel stopped · Android could not start background protection. Return to PocketDev and retry."
        LocalPortForwardFailure.BACKGROUND_STOPPED -> "Tunnel stopped · Android ended background protection. Return to PocketDev and restart."
    }
}
