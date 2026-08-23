package de.fgna.llmbench

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LocalModelStore {
    private const val DIRECTORY = "models"
    private const val FILE_NAME = "benchmark-model.litertlm"

    fun file(context: Context): File = File(File(context.noBackupFilesDir, DIRECTORY), FILE_NAME)

    fun readyFile(context: Context): File? = file(context).takeIf { it.isFile && it.length() > 0L }

    suspend fun import(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        val destination = file(context)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "$FILE_NAME.part")
        temporary.delete()

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Modelldatei konnte nicht geöffnet werden." }
            temporary.outputStream().use { output -> input.copyTo(output) }
        }

        check(temporary.length() > 0L) { "Modelldatei ist leer." }
        LiteRtBenchmarkRuntime.invalidate(destination.absolutePath)
        destination.delete()
        check(temporary.renameTo(destination)) { "Modelldatei konnte nicht gespeichert werden." }
        destination.length()
    }
}
