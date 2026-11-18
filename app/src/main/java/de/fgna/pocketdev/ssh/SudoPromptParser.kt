package de.fgna.pocketdev.ssh

/** Strip the sudo marker without withholding an unrelated program's short input prompt. */
internal class SudoPromptParser(private val emit: (CommandEvent) -> Unit) {
    private var pending = ""

    fun accept(chunk: String) {
        pending += chunk
        while (true) {
            val markerIndex = pending.indexOf(MARKER)
            if (markerIndex >= 0) {
                output(pending.substring(0, markerIndex))
                emit(CommandEvent.SudoPasswordRequired)
                pending = pending.substring(markerIndex + MARKER.length)
                continue
            }
            val keep = (minOf(MARKER.length - 1, pending.length) downTo 1)
                .firstOrNull { pending.endsWith(MARKER.substring(0, it)) } ?: 0
            output(pending.dropLast(keep))
            pending = pending.takeLast(keep)
            return
        }
    }

    fun finish() {
        output(pending)
        pending = ""
    }

    private fun output(text: String) {
        if (text.isNotEmpty()) emit(CommandEvent.Output(OutputStreamKind.STDERR, text))
    }

    companion object {
        const val MARKER = "POCKETDEV_SUDO_PASSWORD_REQUIRED"
    }
}
