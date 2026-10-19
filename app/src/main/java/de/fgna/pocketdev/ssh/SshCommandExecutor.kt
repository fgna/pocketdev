package de.fgna.pocketdev.ssh

import com.hierynomus.sshj.key.KeyAlgorithms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Signal
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.BufferedWriter
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

interface SshCommandExecutor {
    fun execute(
        profile: SshProfile,
        secret: String,
        command: String,
        stdinText: String? = null,
        commandId: String? = null,
        interactiveSudo: Boolean = true,
    ): Flow<CommandEvent>

    fun sendInput(commandId: String, text: String): Boolean = false
    fun captureInput(commandId: String): CommandInputHandle? = null
    fun cancel(commandId: String): Boolean = false
}

class SshjCommandExecutor : SshCommandExecutor {
    private data class ActiveCommand(
        val originalCommand: String,
        val ssh: SSHClient,
        val session: Session,
        val remote: Session.Command,
        val writer: BufferedWriter,
        val emit: (CommandEvent) -> Unit,
        val sudoGeneration: AtomicLong,
    )

    override fun sendInput(commandId: String, text: String): Boolean = sendInputTo(commandId, text)

    override fun captureInput(commandId: String): CommandInputHandle? = captureInputFor(commandId)

    override fun cancel(commandId: String): Boolean = cancelMatching(commandId)

    override fun execute(
        profile: SshProfile,
        secret: String,
        command: String,
        stdinText: String?,
        commandId: String?,
        interactiveSudo: Boolean,
    ): Flow<CommandEvent> = channelFlow {
        var ssh: SSHClient? = null
        var session: Session? = null
        var remote: Session.Command? = null
        var writer: BufferedWriter? = null
        var completion: CommandEvent? = null
        var discoveredFingerprint: String? = null
        val controlKey = commandId ?: command
        send(CommandEvent.Connecting)

        try {
            val targets = withContext(Dispatchers.IO) { resolveTargets(profile.host) }
            val failures = mutableListOf<String>()

            for (target in targets) {
                val candidate = createAndroidCompatibleClient()
                configureHostKeyVerifier(candidate, profile) { fingerprint -> discoveredFingerprint = fingerprint }
                try {
                    withContext(Dispatchers.IO) { candidate.connect(target, profile.port) }
                    ssh = candidate
                    break
                } catch (error: Exception) {
                    failures += "$target:${profile.port} -> ${error.message ?: error::class.java.simpleName}"
                    withContext(Dispatchers.IO) {
                        runCatching { candidate.disconnect() }
                        runCatching { candidate.close() }
                    }
                }
            }

            val connected = ssh ?: throw IllegalStateException(
                "Could not connect to ${profile.host}:${profile.port}. Tried: ${failures.joinToString(" | ")}",
            )

            if (profile.hostKeySha256.isBlank()) {
                val fingerprint = discoveredFingerprint
                    ?: throw IllegalStateException("Server host key fingerprint could not be read.")
                send(CommandEvent.HostKeyTrustRequired(fingerprint))
                return@channelFlow
            }

            withContext(Dispatchers.IO) {
                when (profile.authMode) {
                    AuthMode.PASSWORD -> connected.authPassword(profile.username, secret)
                    AuthMode.PRIVATE_KEY -> {
                        val keyFile = kotlin.io.path.createTempFile("pocketdev-key", ".pem").toFile()
                        try {
                            keyFile.writeText(secret)
                            connected.authPublickey(profile.username, connected.loadKeys(keyFile.absolutePath))
                        } finally {
                            keyFile.delete()
                        }
                    }
                }
            }
            send(CommandEvent.Connected)

            session = withContext(Dispatchers.IO) { connected.startSession() }
            val effectiveCommand = if (interactiveSudo) interactiveSudoCommand(command) else command
            remote = withContext(Dispatchers.IO) { session!!.exec(effectiveCommand) }
            writer = remote!!.outputStream.bufferedWriter()
            cancelledCommands.remove(controlKey)
            val sudoGeneration = AtomicLong(0)
            activeCommands[controlKey] = ActiveCommand(
                originalCommand = command,
                ssh = connected,
                session = session!!,
                remote = remote!!,
                writer = writer!!,
                emit = { event -> trySend(event); Unit },
                sudoGeneration = sudoGeneration,
            )
            if (commandId != null) {
                activeUserCommands += controlKey
                notifyActiveCommandListeners()
            }

            if (stdinText != null) {
                withContext(Dispatchers.IO) {
                    synchronized(writer!!) {
                        writer!!.write(stdinText)
                        writer!!.newLine()
                        writer!!.flush()
                    }
                }
            }

            val stdoutJob = launch(Dispatchers.IO) {
                stream(remote!!.inputStream) { text -> trySend(CommandEvent.Output(OutputStreamKind.STDOUT, text)) }
            }
            val stderrJob = launch(Dispatchers.IO) {
                if (interactiveSudo) {
                    streamInteractiveStderr(remote!!.errorStream) { event ->
                        if (event == CommandEvent.SudoPasswordRequired) sudoGeneration.incrementAndGet()
                        trySend(event)
                    }
                } else {
                    stream(remote!!.errorStream) { text -> trySend(CommandEvent.Output(OutputStreamKind.STDERR, text)) }
                }
            }

            withContext(Dispatchers.IO) { remote!!.join() }
            stdoutJob.join()
            stderrJob.join()
            val exitCode = if (cancelledCommands.contains(controlKey)) 130 else remote!!.exitStatus ?: -1
            completion = CommandEvent.Completed(exitCode)
        } catch (error: Exception) {
            if (cancelledCommands.contains(controlKey)) {
                completion = CommandEvent.Completed(130)
            } else {
                completion = CommandEvent.ConnectionFailed(error.message ?: error::class.java.simpleName)
            }
        } finally {
            retireInput(controlKey)
            if (commandId != null) {
                activeUserCommands.remove(controlKey)
                notifyActiveCommandListeners()
            }
            cancelledCommands.remove(controlKey)
            withContext(Dispatchers.IO) {
                runCatching { writer?.close() }
                runCatching { remote?.close() }
                runCatching { session?.close() }
                ssh?.let { client ->
                    runCatching { client.disconnect() }
                    runCatching { client.close() }
                }
            }
        }
        // Publish the terminal state only after control handles and resources are retired.
        // A new run in the same project cannot be removed by the previous run's cleanup.
        completion?.let { trySend(it) }
    }

    private fun interactiveSudoCommand(command: String): String = """
        # Keep SSH input available for sudo authentication even inside a pipeline.
        exec 3<&0
        sudo() {
          command sudo -S -p '$SUDO_PROMPT' -v <&3 || return
          command sudo -n "${'$'}@"
        }
        $command
    """.trimIndent()

    private fun createAndroidCompatibleClient(): SSHClient {
        val config = DefaultSecurityProviderConfig().apply {
            setKeyAlgorithms(listOf(KeyAlgorithms.RSASHA512(), KeyAlgorithms.RSASHA256()))
        }
        return SSHClient(config)
    }

    private fun configureHostKeyVerifier(
        ssh: SSHClient,
        profile: SshProfile,
        onDiscoveredFingerprint: (String) -> Unit,
    ) {
        if (profile.hostKeySha256.isBlank()) {
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    onDiscoveredFingerprint(SecurityUtils.getFingerprint(key))
                    return true
                }

                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
            })
        } else {
            ssh.addHostKeyVerifier(profile.hostKeySha256.trim())
        }
    }

    private fun resolveTargets(host: String): List<String> {
        val addresses = InetAddress.getAllByName(host).toList()
        return addresses.sortedByDescending { it is Inet4Address }.mapNotNull { it.hostAddress }.distinct().ifEmpty { listOf(host) }
    }

    private fun stream(input: InputStream, onChunk: (String) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            onChunk(buffer.decodeToString(0, count))
        }
    }

    private fun streamInteractiveStderr(input: InputStream, emit: (CommandEvent) -> Unit) {
        val parser = SudoPromptParser(emit)
        stream(input, parser::accept)
        parser.finish()
    }

    companion object {
        private const val SUDO_PROMPT = SudoPromptParser.MARKER
        private val activeCommands = ConcurrentHashMap<String, ActiveCommand>()
        private val activeUserCommands = ConcurrentHashMap.newKeySet<String>()
        private val activeCommandListeners = ConcurrentHashMap.newKeySet<(Int) -> Unit>()
        private val cancelledCommands = ConcurrentHashMap.newKeySet<String>()

        private fun findActive(commandHint: String): Map.Entry<String, ActiveCommand>? {
            activeCommands[commandHint]?.let { exact ->
                return object : Map.Entry<String, ActiveCommand> {
                    override val key: String = commandHint
                    override val value: ActiveCommand = exact
                }
            }
            return activeCommands.entries.firstOrNull { (_, active) ->
                active.originalCommand == commandHint || active.originalCommand.contains(commandHint)
            }
        }

        fun activeUserCommandCount(): Int = activeUserCommands.size

        fun cancelAllActiveUserCommands(): Int {
            val commandIds = activeUserCommands.toList()
            return commandIds.count { commandId -> cancelMatching(commandId) }
        }

        fun addActiveCommandListener(listener: (Int) -> Unit): () -> Unit {
            activeCommandListeners += listener
            listener(activeUserCommandCount())
            return {
                activeCommandListeners -= listener
            }
        }

        private fun notifyActiveCommandListeners() {
            val count = activeUserCommandCount()
            activeCommandListeners.forEach { listener -> runCatching { listener(count) } }
        }

        private fun retireInput(commandId: String) {
            val active = activeCommands[commandId] ?: return
            synchronized(active.writer) {
                activeCommands.remove(commandId, active)
            }
        }

        fun captureInputFor(commandId: String): CommandInputHandle? {
            val active = activeCommands[commandId] ?: return null
            return CommandInputHandle(
                writer = active.writer,
                isCurrent = { activeCommands[commandId] === active && !cancelledCommands.contains(commandId) },
                onSent = {},
            )
        }

        fun sendInputTo(commandId: String, text: String): Boolean {
            val active = activeCommands[commandId] ?: return false
            val generation = active.sudoGeneration.get()
            val handle = captureInputFor(commandId) ?: return false
            if (!handle.sendLine(text)) return false
            active.emit(CommandEvent.SudoPasswordSubmitted(generation))
            return true
        }

        fun cancelMatching(commandHint: String): Boolean {
            val entry = findActive(commandHint) ?: return false
            val key = entry.key
            val active = entry.value
            cancelledCommands += key
            runCatching { active.remote.signal(Signal.INT) }
            runCatching { Thread.sleep(120) }
            runCatching { active.remote.close() }
            runCatching { active.session.close() }
            runCatching { active.ssh.disconnect() }
            runCatching { active.ssh.close() }
            return true
        }
    }
}
}@"
        }
        $command
    """.trimIndent()

    private fun createAndroidCompatibleClient(): SSHClient {
        val config = DefaultSecurityProviderConfig().apply {
            setKeyAlgorithms(listOf(KeyAlgorithms.RSASHA512(), KeyAlgorithms.RSASHA256()))
        }
        return SSHClient(config)
    }

    private fun configureHostKeyVerifier(
        ssh: SSHClient,
        profile: SshProfile,
        onDiscoveredFingerprint: (String) -> Unit,
    ) {
        if (profile.hostKeySha256.isBlank()) {
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    onDiscoveredFingerprint(SecurityUtils.getFingerprint(key))
                    return true
                }

                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
            })
        } else {
            ssh.addHostKeyVerifier(profile.hostKeySha256.trim())
        }
    }

    private fun resolveTargets(host: String): List<String> {
        val addresses = InetAddress.getAllByName(host).toList()
        return addresses.sortedByDescending { it is Inet4Address }.mapNotNull { it.hostAddress }.distinct().ifEmpty { listOf(host) }
    }

    private fun stream(input: InputStream, onChunk: (String) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            onChunk(buffer.decodeToString(0, count))
        }
    }

    private fun streamInteractiveStderr(input: InputStream, emit: (CommandEvent) -> Unit) {
        val parser = SudoPromptParser(emit)
        stream(input, parser::accept)
        parser.finish()
    }

    companion object {
        private const val SUDO_PROMPT = SudoPromptParser.MARKER
        private val activeCommands = ConcurrentHashMap<String, ActiveCommand>()
        private val activeUserCommands = ConcurrentHashMap.newKeySet<String>()
        private val activeCommandListeners = ConcurrentHashMap.newKeySet<(Int) -> Unit>()
        private val cancelledCommands = ConcurrentHashMap.newKeySet<String>()

        private fun findActive(commandHint: String): Map.Entry<String, ActiveCommand>? {
            activeCommands[commandHint]?.let { exact ->
                return object : Map.Entry<String, ActiveCommand> {
                    override val key: String = commandHint
                    override val value: ActiveCommand = exact
                }
            }
            return activeCommands.entries.firstOrNull { (_, active) ->
                active.originalCommand == commandHint || active.originalCommand.contains(commandHint)
            }
        }

        fun activeUserCommandCount(): Int = activeUserCommands.size

        fun cancelAllActiveUserCommands(): Int {
            val commandIds = activeUserCommands.toList()
            return commandIds.count { commandId -> cancelMatching(commandId) }
        }

        fun addActiveCommandListener(listener: (Int) -> Unit): () -> Unit {
            activeCommandListeners += listener
            listener(activeUserCommandCount())
            return {
                activeCommandListeners -= listener
            }
        }

        private fun notifyActiveCommandListeners() {
            val count = activeUserCommandCount()
            activeCommandListeners.forEach { listener -> runCatching { listener(count) } }
        }

        private fun retireInput(commandId: String) {
            val active = activeCommands[commandId] ?: return
            synchronized(active.writer) {
                activeCommands.remove(commandId, active)
            }
        }

        fun captureInputFor(commandId: String): CommandInputHandle? {
            val active = activeCommands[commandId] ?: return null
            return CommandInputHandle(
                writer = active.writer,
                isCurrent = { activeCommands[commandId] === active && !cancelledCommands.contains(commandId) },
                onSent = {},
            )
        }

        fun sendInputTo(commandId: String, text: String): Boolean {
            val active = activeCommands[commandId] ?: return false
            val generation = active.sudoGeneration.get()
            val handle = captureInputFor(commandId) ?: return false
            if (!handle.sendLine(text)) return false
            active.emit(CommandEvent.SudoPasswordSubmitted(generation))
            return true
        }

        fun cancelMatching(commandHint: String): Boolean {
            val entry = findActive(commandHint) ?: return false
            val key = entry.key
            val active = entry.value
            cancelledCommands += key
            runCatching { active.remote.signal(Signal.INT) }
            runCatching { Thread.sleep(120) }
            runCatching { active.remote.close() }
            runCatching { active.session.close() }
            runCatching { active.ssh.disconnect() }
            runCatching { active.ssh.close() }
            return true
        }
    }
}
