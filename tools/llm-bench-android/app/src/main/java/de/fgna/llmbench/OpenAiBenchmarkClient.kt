package de.fgna.llmbench

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class BenchmarkProfile(
    val baseUrl: String,
    val model: String,
    val apiKey: String = "",
)

internal object OpenAiStreamParser {
    fun delta(line: String): String? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isBlank() || payload == "[DONE]") return null
        return runCatching {
            JSONObject(payload)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content")
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun completionTokens(line: String): Int? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isBlank() || payload == "[DONE]") return null
        return runCatching {
            JSONObject(payload).optJSONObject("usage")
                ?.takeIf { it.has("completion_tokens") }
                ?.optInt("completion_tokens")
        }.getOrNull()
    }
}

internal class OpenAiBenchmarkClient(
    private val nanoTime: () -> Long = System::nanoTime,
) {
    suspend fun run(
        profile: BenchmarkProfile,
        prompt: BenchmarkPrompt,
        onDelta: (String) -> Unit = {},
    ): BenchmarkResult = withContext(Dispatchers.IO) {
        val startNs = nanoTime()
        var firstTokenNs: Long? = null
        var apiTokenCount: Int? = null
        val output = StringBuilder()

        try {
            require(profile.baseUrl.isNotBlank()) { "Base URL fehlt" }
            require(profile.model.isNotBlank()) { "Modellname fehlt" }

            val connection = openConnection(profile)
            try {
                val payload = JSONObject().apply {
                    put("model", profile.model.trim())
                    put("stream", true)
                    put("max_tokens", 800)
                    put("stream_options", JSONObject().put("include_usage", true))
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", prompt.text),
                        ),
                    )
                }

                connection.outputStream.use { out ->
                    out.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                if (code !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error("HTTP $code${if (errorBody.isBlank()) "" else ": $errorBody"}")
                }

                connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        OpenAiStreamParser.completionTokens(line)?.let { apiTokenCount = it }
                        OpenAiStreamParser.delta(line)?.let { delta ->
                            if (firstTokenNs == null) firstTokenNs = nanoTime()
                            output.append(delta)
                            onDelta(delta)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            val finishedNs = nanoTime()
            val firstNs = firstTokenNs ?: finishedNs
            val tokenCount = apiTokenCount ?: BenchmarkMath.estimateTokens(output.toString())
            BenchmarkResult(
                model = profile.model,
                prompt = prompt,
                ttftMs = BenchmarkMath.ttftMs(startNs, firstNs),
                totalMs = BenchmarkMath.totalMs(startNs, finishedNs),
                outputTokens = tokenCount,
                tokensPerSecond = BenchmarkMath.tokensPerSecond(tokenCount, firstNs, finishedNs),
                tokenCountSource = if (apiTokenCount != null) TokenCountSource.API else TokenCountSource.ESTIMATED,
                output = output.toString(),
                error = null,
            )
        } catch (t: Throwable) {
            val finishedNs = nanoTime()
            val firstNs = firstTokenNs ?: finishedNs
            val tokenCount = BenchmarkMath.estimateTokens(output.toString())
            BenchmarkResult(
                model = profile.model,
                prompt = prompt,
                ttftMs = BenchmarkMath.ttftMs(startNs, firstNs),
                totalMs = BenchmarkMath.totalMs(startNs, finishedNs),
                outputTokens = tokenCount,
                tokensPerSecond = BenchmarkMath.tokensPerSecond(tokenCount, firstNs, finishedNs),
                tokenCountSource = TokenCountSource.ESTIMATED,
                output = output.toString(),
                error = t.message ?: t::class.java.simpleName,
            )
        }
    }

    private fun openConnection(profile: BenchmarkProfile): HttpURLConnection {
        val normalized = profile.baseUrl.trim().trimEnd('/')
        val endpoint = if (normalized.endsWith("/v1")) "$normalized/chat/completions" else "$normalized/v1/chat/completions"
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            profile.apiKey.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }
}
