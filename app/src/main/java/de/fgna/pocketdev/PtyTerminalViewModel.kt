package de.fgna.pocketdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fgna.pocketdev.data.SshProfileRepository
import de.fgna.pocketdev.ssh.PtyTerminalSession
import de.fgna.pocketdev.ssh.SshProfile
import de.fgna.pocketdev.ssh.TerminalScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class PtyTerminalState(
    val output: String = "",
    val running: Boolean = false,
    val started: Boolean = false,
    val columns: Int = 80,
    val rows: Int = 24,
    val error: String? = null,
)

/** Retains a tab's live PTY and screen through Android Activity recreation. */
class PtyTerminalViewModel(application: Application) : AndroidViewModel(application) {
    // Cleanup must outlive viewModelScope cancellation during Activity teardown.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session = PtyTerminalSession()
    private var screen = TerminalScreen()
    @Volatile private var generation = 0L
    private val _state = MutableStateFlow(PtyTerminalState())
    val state = _state.asStateFlow()
    private var identity: String? = null
    private var viewportColumns = 80

    fun start(profile: SshProfile, directory: String) {
        val id = "${profile.host}:${profile.port}:${profile.username}:$directory"
        if (identity == id) return
        val previous = session
        // Closing an SSH socket may block; never perform it from a Compose callback.
        cleanupScope.launch { previous.close() }
        session = PtyTerminalSession()
        screen = TerminalScreen(viewportColumns, 24)
        val current = session
        val currentScreen = screen
        val token = ++generation
        identity = id
        _state.value = PtyTerminalState(started = true, running = true, columns = viewportColumns)
        viewModelScope.launch {
            try {
                val secret = withContext(Dispatchers.IO) {
                    SshProfileRepository(getApplication()).loadSecret()
                } ?: error("No SSH credentials stored")
                if (token != generation) return@launch
                current.run(profile, secret, directory, columns = { viewportColumns }) { chunk ->
                    val rendered = currentScreen.accept(chunk)
                    if (token == generation) _state.update { it.copy(output = rendered) }
                }
                if (token == generation) {
                    _state.update { it.copy(running = false, output = it.output + "\n[SSH terminal closed]") }
                }
            } catch (error: Exception) {
                if (token == generation) {
                    _state.update {
                        it.copy(running = false, error = error.message ?: "SSH terminal failed")
                    }
                }
            }
        }
    }

    /** All SSH output-stream operations execute away from Compose's main thread. */
    fun send(text: String, clearOnSuccess: (() -> Unit)? = null) {
        val current = session
        val token = generation
        viewModelScope.launch {
            val accepted = withContext(Dispatchers.IO) { current.send(text) }
            if (token != generation) return@launch
            if (accepted) clearOnSuccess?.invoke()
            else _state.update { it.copy(error = "Terminal input was not sent. Check the SSH connection.") }
        }
    }

    fun interrupt() = send("\u0003")

    /** Called by the measured viewport, including after rotation. Avoid redundant SSH resizes. */
    fun resizeToViewport(columns: Int) {
        val width = columns.coerceIn(20, 160)
        if (viewportColumns == width) return
        viewportColumns = width
        resize(width, _state.value.rows)
    }

    fun resize(columns: Int, rows: Int) {
        val current = session
        val currentScreen = screen
        val token = generation
        viewModelScope.launch {
            val resized = withContext(Dispatchers.IO) { current.resize(columns, rows) }
            if (token != generation) return@launch
            if (token != generation) return@launch
            // The SSH channel may still be connecting; allocatePTY reads viewportColumns on start.
            if (resized || _state.value.running) {
                _state.update { it.copy(columns = columns, rows = rows, output = currentScreen.resize(columns, rows)) }
            }
        }
    }

    fun close() {
        generation++
        val closing = session
        identity = null
        _state.update { it.copy(running = false) }
        cleanupScope.launch { closing.close() }
    }

    override fun onCleared() {
        // viewModelScope is cancelled at the end of onCleared; cleanup needs
        // its own short-lived IO coroutine so it cannot block the UI thread.
        val closing = session
        cleanupScope.launch { closing.close() }
        super.onCleared()
    }
}
