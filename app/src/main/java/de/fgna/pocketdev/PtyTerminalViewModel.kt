package de.fgna.pocketdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fgna.pocketdev.data.SshProfileRepository
import de.fgna.pocketdev.ssh.PtyTerminalSession
import de.fgna.pocketdev.ssh.SshProfile
import de.fgna.pocketdev.ssh.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val session = PtyTerminalSession()
    private val screen = TerminalScreen()
    private val _state = MutableStateFlow(PtyTerminalState())
    val state = _state.asStateFlow()
    private var identity: String? = null

    fun start(profile: SshProfile, directory: String) {
        val id = "${profile.host}:${profile.port}:${profile.username}:$directory"
        if (identity == id) return
        if (identity != null) session.close()
        identity = id
        _state.value = PtyTerminalState(started = true, running = true)
        viewModelScope.launch {
            try {
                val secret = withContext(Dispatchers.IO) {
                    SshProfileRepository(getApplication()).loadSecret()
                } ?: error("No SSH credentials stored")
                session.run(profile, secret, directory) { chunk ->
                    val rendered = screen.accept(chunk)
                    _state.update { it.copy(output = rendered) }
                }
                _state.update { it.copy(running = false, output = it.output + "\n[SSH terminal closed]") }
            } catch (error: Exception) {
                _state.update {
                    it.copy(running = false, error = error.message ?: "SSH terminal failed")
                }
            }
        }
    }

    /** All SSH output-stream operations execute away from Compose's main thread. */
    fun send(text: String, clearOnSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            val accepted = withContext(Dispatchers.IO) { session.send(text) }
            if (accepted) clearOnSuccess?.invoke()
            else _state.update { it.copy(error = "Terminal input was not sent. Check the SSH connection.") }
        }
    }

    fun interrupt() = send("\u0003")

    fun resize(columns: Int, rows: Int) {
        viewModelScope.launch {
            val resized = withContext(Dispatchers.IO) { session.resize(columns, rows) }
            if (resized) _state.update { it.copy(columns = columns, rows = rows, output = screen.resize(columns, rows)) }
            else _state.update { it.copy(error = "Terminal resize failed.") }
        }
    }

    fun close() {
        session.close()
        identity = null
        _state.update { it.copy(running = false) }
    }

    override fun onCleared() {
        session.close()
        super.onCleared()
    }
}
