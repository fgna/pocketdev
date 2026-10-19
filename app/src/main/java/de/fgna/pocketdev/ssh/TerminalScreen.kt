package de.fgna.pocketdev.ssh

/**
 * Bounded VT100-style screen for interactive SSH PTYs. Handles the display primitives
 * used by common terminal CLIs; unsupported controls are ignored instead of displayed.
 * Pure Kotlin, so rendering can be independently tested without an Android device.
 */
class TerminalScreen(private val columns: Int = 80, private val rows: Int = 24) {
    init { require(columns in 20..500 && rows in 5..300) }
    private var cells = Array(rows) { CharArray(columns) { ' ' } }
    private var savedMain: Array<CharArray>? = null
    private var row = 0
    private var col = 0
    private var savedRow = 0
    private var savedCol = 0
    private val control = StringBuilder()
    private var mode = 0 // 0 normal, 1 ESC, 2 CSI, 3 OSC, 4 OSC escape
    private var pendingCR = false

    @Synchronized fun accept(chunk: String): String {
        for (ch in chunk) {
            when (mode) {
                0 -> when (ch) {
                    '\u001b' -> mode = 1
                    '\r' -> { col = 0; pendingCR = true }
                    '\n' -> { if (!pendingCR) col = 0; newline(); pendingCR = false }
                    '\b' -> { col = (col - 1).coerceAtLeast(0); pendingCR = false }
                    '\t' -> { col = ((col / 8 + 1) * 8).coerceAtMost(columns - 1); pendingCR = false }
                    else -> if (ch >= ' ') {
                        pendingCR = false
                        cells[row][col] = ch
                        if (++col >= columns) { col = 0; newline() }
                    }
                }
                1 -> {
                    mode = 0
                    when (ch) {
                        '[' -> { mode = 2; control.clear() }
                        ']' -> { mode = 3; control.clear() }
                        '7' -> { savedRow = row; savedCol = col }
                        '8' -> { row = savedRow; col = savedCol }
                        'D' -> newline()
                        'M' -> { row = (row - 1).coerceAtLeast(0) }
                        'c' -> clear()
                    }
                }
                2 -> {
                    if (ch in '@'..'~') { csi(ch, control.toString()); mode = 0; control.clear() }
                    else if (control.length < 80) control.append(ch)
                    else mode = 0
                }
                3 -> when (ch) {
                    '\u0007' -> mode = 0
                    '\u001b' -> mode = 4
                    else -> if (control.length < 256) control.append(ch) else mode = 0
                }
                4 -> mode = if (ch == '\\') 0 else 3
            }
        }
        return render()
    }

    private fun newline() {
        if (row < rows - 1) row++
        else {
            for (r in 0 until rows - 1) cells[r] = cells[r + 1]
            cells[rows - 1] = CharArray(columns) { ' ' }
        }
    }

    private fun clear() { cells = Array(rows) { CharArray(columns) { ' ' } }; row = 0; col = 0 }

    private fun csi(command: Char, params: String) {
        val private = params.startsWith("?")
        val values = params.removePrefix("?").split(';').map { it.toIntOrNull() ?: 0 }
        fun count() = (values.firstOrNull() ?: 0).coerceAtLeast(1)
        when (command) {
            'A' -> row = (row - count()).coerceAtLeast(0)
            'B' -> row = (row + count()).coerceAtMost(rows - 1)
            'C' -> col = (col + count()).coerceAtMost(columns - 1)
            'D' -> col = (col - count()).coerceAtLeast(0)
            'G' -> col = (count() - 1).coerceIn(0, columns - 1)
            'H', 'f' -> {
                row = ((values.getOrNull(0) ?: 1).coerceAtLeast(1) - 1).coerceAtMost(rows - 1)
                col = ((values.getOrNull(1) ?: 1).coerceAtLeast(1) - 1).coerceAtMost(columns - 1)
            }
            'J' -> when (values.firstOrNull() ?: 0) {
                0 -> { for (c in col until columns) cells[row][c] = ' '; for (r in row + 1 until rows) cells[r].fill(' ') }
                1 -> { for (r in 0 until row) cells[r].fill(' '); for (c in 0..col) cells[row][c] = ' ' }
                2, 3 -> cells.forEach { it.fill(' ') }
            }
            'K' -> when (values.firstOrNull() ?: 0) {
                0 -> for (c in col until columns) cells[row][c] = ' '
                1 -> for (c in 0..col) cells[row][c] = ' '
                2 -> cells[row].fill(' ')
            }
            's' -> { savedRow = row; savedCol = col }
            'u' -> { row = savedRow; col = savedCol }
            'm' -> Unit // SGR colors are intentionally omitted from plain-text rendering.
            'h', 'l' -> if (private && values.contains(1049)) {
                if (command == 'h' && savedMain == null) { savedMain = cells; clear() }
                if (command == 'l' && savedMain != null) { cells = savedMain!!; savedMain = null; row = 0; col = 0 }
            }
        }
    }

    @Synchronized fun render(): String = cells.joinToString("\n") { String(it).trimEnd() }.trimEnd()
}
