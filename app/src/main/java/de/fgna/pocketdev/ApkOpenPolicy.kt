package de.fgna.pocketdev

import android.content.Context
import android.widget.Toast
import de.fgna.pocketdev.artifact.ApkInstaller
import java.io.File

object ApkOpenPolicy {
    fun open(context: Context, path: String) {
        val file = File(path)
        if (!file.isFile) {
            Toast.makeText(context, "Downloaded APK is no longer available.", Toast.LENGTH_LONG).show()
            return
        }
        val marker = File(file.absolutePath + ".verified.sha256")
        if (!marker.isFile) {
            Toast.makeText(context, "Run Get APK again so PocketDev can verify the download before installing it.", Toast.LENGTH_LONG).show()
            return
        }
        ApkInstaller.install(context, file)
    }
}
