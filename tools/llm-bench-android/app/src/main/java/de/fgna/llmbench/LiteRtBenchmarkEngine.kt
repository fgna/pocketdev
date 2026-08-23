package de.fgna.llmbench

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class LiteRtRun(
    val text: String,
    val initializationMs: Long,
    val generationMs: Long,
    val firstOutputMs: Long?,
    val coldRun: Boolean,
    val backend: String,
)

internal object LiteRtBenchmarkRuntime {
    private const val TAG = "LLMBench-LiteRT"

    private data class LoadedEngine(
        val modelPath: String,
        val engine: Engine,
        val backend: String,
        val contextTokens: Int,
    )

    private data class LoadResult(val loaded: LoadedEngine, val initMs: Long)
    private data class MessageResult(val text: String, val firstOutputMs: Long?)

    private val mutex = Mutex()
    private var loaded: LoadedEngine? = null

    suspend fun invalidate(modelPath: String? = null) = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            val current = loaded
            if (current != null && (modelPath == null || current.modelPath == modelPath)) {
                loaded = null
                runCatching { current.engine.close() }
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun run(modelPath: String, prompt: String, maxTokens: Int = 800): LiteRtRun = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            val model = File(modelPath)
            require(model.isFile && model.length() > 0L) { "Keine gültige .litertlm-Datei geladen." }

            val contextBudget = maxOf(8_192, maxTokens + 2_048)
            val current = loaded
            val cold = current == null || current.modelPath != modelPath || current.contextTokens < contextBudget
            val load = if (cold) {
                closeLoaded()
                loadPreferred(modelPath, contextBudget)
            } else {
                LoadResult(requireNotNull(current), 0L)
            }
            loaded = load.loaded

            try {
                conversation(load.loaded, prompt, load.initMs, cold)
            } catch (gpuError: Throwable) {
                if (load.loaded.backend != "GPU") throw gpuError
                Log.w(TAG, "GPU inference failed; retrying on CPU", gpuError)
                closeLoaded()
                val cpu = loadBackend(modelPath, Backend.CPU(), "CPU", contextBudget)
                loaded = cpu.loaded
                conversation(cpu.loaded, prompt, load.initMs + cpu.initMs, true)
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun conversation(
        loaded: LoadedEngine,
        prompt: String,
        initMs: Long,
        cold: Boolean,
    ): LiteRtRun {
        val conversation = loaded.engine.createConversation()
        try {
            val started = System.nanoTime()
            val message = send(conversation, prompt, started)
            return LiteRtRun(
                text = message.text.trim(),
                initializationMs = initMs,
                generationMs = elapsedMs(started),
                firstOutputMs = message.firstOutputMs,
                coldRun = cold,
                backend = loaded.backend,
            )
        } finally {
            conversation.close()
        }
    }

    private fun loadPreferred(modelPath: String, contextTokens: Int): LoadResult =
        runCatching { loadBackend(modelPath, Backend.GPU(), "GPU", contextTokens) }
            .getOrElse { gpuError ->
                Log.w(TAG, "GPU initialization failed; trying CPU", gpuError)
                loadBackend(modelPath, Backend.CPU(), "CPU", contextTokens)
            }

    private fun loadBackend(modelPath: String, backend: Backend, name: String, contextTokens: Int): LoadResult {
        val started = System.nanoTime()
        val engine = Engine(EngineConfig(modelPath = modelPath, backend = backend, maxNumTokens = contextTokens))
        try {
            engine.initialize()
            return LoadResult(
                LoadedEngine(modelPath, engine, name, contextTokens),
                elapsedMs(started),
            )
        } catch (t: Throwable) {
            runCatching { engine.close() }
            throw t
        }
    }

    private suspend fun send(
        conversation: com.google.ai.edge.litertlm.Conversation,
        prompt: String,
        started: Long,
    ): MessageResult = suspendCancellableCoroutine { continuation ->
        val output = StringBuilder()
        val first = AtomicLong(-1L)
        conversation.sendMessageAsync(
            Contents.of(prompt),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    first.compareAndSet(-1L, elapsedMs(started))
                    output.append(message.toString())
                }

                override fun onDone() {
                    if (continuation.isActive) {
                        continuation.resume(MessageResult(output.toString(), first.get().takeIf { it >= 0L }))
                    }
                }

                override fun onError(throwable: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(throwable)
                }
            },
        )
        continuation.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
    }

    private fun closeLoaded() {
        val current = loaded ?: return
        loaded = null
        runCatching { current.engine.close() }
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000L
}

internal class LiteRtBenchmarkEngine(private val modelPath: String, private val modelLabel: String) {
    suspend fun run(prompt: BenchmarkPrompt): BenchmarkResult {
        val wallStarted = System.nanoTime()
        return try {
            val run = LiteRtBenchmarkRuntime.run(modelPath, prompt.text)
            val finished = System.nanoTime()
            val outputTokens = BenchmarkMath.estimateTokens(run.text)
            val firstNs = if (run.firstOutputMs != null) {
                wallStarted + (run.initializationMs + run.firstOutputMs) * 1_000_000L
            } else {
                finished
            }
            BenchmarkResult(
                model = "$modelLabel [${run.backend}${if (run.coldRun) ", cold" else ", warm"}]",
                prompt = prompt,
                ttftMs = BenchmarkMath.ttftMs(wallStarted, firstNs),
                totalMs = BenchmarkMath.totalMs(wallStarted, finished),
                outputTokens = outputTokens,
                tokensPerSecond = BenchmarkMath.tokensPerSecond(outputTokens, firstNs, finished),
                tokenCountSource = TokenCountSource.ESTIMATED,
                output = run.text,
                error = null,
            )
        } catch (t: Throwable) {
            val finished = System.nanoTime()
            BenchmarkResult(
                model = modelLabel,
                prompt = prompt,
                ttftMs = BenchmarkMath.totalMs(wallStarted, finished),
                totalMs = BenchmarkMath.totalMs(wallStarted, finished),
                outputTokens = 0,
                tokensPerSecond = 0.0,
                tokenCountSource = TokenCountSource.ESTIMATED,
                output = "",
                error = t.message ?: t::class.java.simpleName,
            )
        }
    }
}
