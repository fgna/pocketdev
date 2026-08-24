package de.fgna.pocketdev.artifact

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import java.io.File

object ApkInstaller {
    private const val ACTION_INSTALL_RESULT = "de.fgna.pocketdev.APK_INSTALL_RESULT"

    fun install(context: Context, file: File) {
        require(file.isFile && file.length() > 0L) { "Downloaded APK is no longer available." }

        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                context,
                "Allow PocketDev to install unknown apps, then tap Open APK again.",
                Toast.LENGTH_LONG,
            ).show()
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)

        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val resultIntent = Intent(context, ApkInstallResultReceiver::class.java).apply {
                action = ACTION_INSTALL_RESULT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pendingIntent.intentSender)
        }

        Toast.makeText(context, "APK verified · starting Android installer…", Toast.LENGTH_SHORT).show()
    }
}

class ApkInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                } else {
                    Toast.makeText(context, "Android did not provide an install confirmation screen.", Toast.LENGTH_LONG).show()
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "APK installed successfully.", Toast.LENGTH_LONG).show()
            }

            else -> {
                val reason = statusName(status)
                val detail = message.trim().takeIf { it.isNotEmpty() }
                Toast.makeText(
                    context,
                    buildString {
                        append("Install failed · ")
                        append(reason)
                        if (detail != null) append(": $detail")
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun statusName(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "aborted"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "conflict"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "incompatible"
        PackageInstaller.STATUS_FAILURE_INVALID -> "invalid APK"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "insufficient storage"
        else -> "Android status $status"
    }
}
