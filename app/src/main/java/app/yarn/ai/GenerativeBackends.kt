package app.yarn.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class RewriteStyle { SHORTEN, ELABORATE, FRIENDLY, PROFESSIONAL, REPHRASE, EMOJIFY }

enum class EngineState { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, NOT_CONFIGURED }

/**
 * Gemini Nano through ML Kit GenAI (AICore). Runs entirely on-device on supported phones
 * (e.g. recent Pixel and Galaxy flagships); on other devices every call reports UNAVAILABLE
 * and the app falls back to other engines. Free, no API key, no network at inference time.
 */
class GeminiNanoService(private val context: Context) {
    private var summarizer: Summarizer? = null
    private val rewriters = HashMap<RewriteStyle, Rewriter>()
    private val lock = Mutex()

    private fun summarizerClient(): Summarizer = summarizer ?: Summarization.getClient(
        SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.CONVERSATION)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .setLongInputAutoTruncationEnabled(true)
            .build(),
    ).also { summarizer = it }

    private fun rewriterClient(style: RewriteStyle, languageTag: String): Rewriter? {
        val language = when (languageTag.substringBefore('-')) {
            "en" -> RewriterOptions.Language.ENGLISH
            "ja" -> RewriterOptions.Language.JAPANESE
            "de" -> RewriterOptions.Language.GERMAN
            "fr" -> RewriterOptions.Language.FRENCH
            "it" -> RewriterOptions.Language.ITALIAN
            "es" -> RewriterOptions.Language.SPANISH
            "ko" -> RewriterOptions.Language.KOREAN
            else -> return null
        }
        val type = when (style) {
            RewriteStyle.SHORTEN -> RewriterOptions.OutputType.SHORTEN
            RewriteStyle.ELABORATE -> RewriterOptions.OutputType.ELABORATE
            RewriteStyle.FRIENDLY -> RewriterOptions.OutputType.FRIENDLY
            RewriteStyle.PROFESSIONAL -> RewriterOptions.OutputType.PROFESSIONAL
            RewriteStyle.REPHRASE -> RewriterOptions.OutputType.REPHRASE
            RewriteStyle.EMOJIFY -> RewriterOptions.OutputType.EMOJIFY
        }
        if (language != RewriterOptions.Language.ENGLISH) {
            return Rewriting.getClient(RewriterOptions.builder(context).setOutputType(type).setLanguage(language).build())
        }
        return rewriters.getOrPut(style) {
            Rewriting.getClient(RewriterOptions.builder(context).setOutputType(type).setLanguage(language).build())
        }
    }

    private fun Int.toState() = when (this) {
        FeatureStatus.AVAILABLE -> EngineState.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> EngineState.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> EngineState.DOWNLOADING
        else -> EngineState.UNAVAILABLE
    }

    suspend fun summarizerState(): EngineState = runCatching {
        lock.withLock { summarizerClient() }.checkFeatureStatus().await().toState()
    }.getOrDefault(EngineState.UNAVAILABLE)

    suspend fun rewriterState(): EngineState = runCatching {
        lock.withLock { rewriterClient(RewriteStyle.REPHRASE, "en")!! }.checkFeatureStatus().await().toState()
    }.getOrDefault(EngineState.UNAVAILABLE)

    /** Asks AICore to download the on-device model; progress arrives via [onProgress] in bytes. */
    suspend fun download(onProgress: (Long, Long) -> Unit): Boolean = runCatching {
        var total = 0L
        val cb = object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) { total = bytesToDownload; onProgress(0, total) }
            override fun onDownloadProgress(totalBytesDownloaded: Long) = onProgress(totalBytesDownloaded, total)
            override fun onDownloadCompleted() = onProgress(total, total)
            override fun onDownloadFailed(e: GenAiException) { Log.w(TAG, "Gemini Nano download failed", e) }
        }
        lock.withLock { summarizerClient() }.downloadFeature(cb).await()
        runCatching { lock.withLock { rewriterClient(RewriteStyle.REPHRASE, "en")!! }.downloadFeature(cb).await() }
        true
    }.getOrDefault(false)

    suspend fun summarize(conversationText: String): String? {
        if (summarizerState() != EngineState.AVAILABLE) return null
        return runCatching {
            val client = lock.withLock { summarizerClient() }
            client.runInference(SummarizationRequest.builder(conversationText).build()).await().summary
        }.onFailure { Log.w(TAG, "Nano summarization failed", it) }.getOrNull()
    }

    suspend fun rewrite(text: String, style: RewriteStyle, languageTag: String): List<String>? {
        val client = lock.withLock { rewriterClient(style, languageTag) } ?: return null
        return runCatching {
            if (client.checkFeatureStatus().await() != FeatureStatus.AVAILABLE) return null
            client.runInference(RewritingRequest.builder(text).build()).await().results.map { it.text }
        }.onFailure { Log.w(TAG, "Nano rewrite failed", it) }.getOrNull()
    }

    companion object {
        private const val TAG = "GeminiNano"
    }
}

/**
 * Optional open-weight LLM executed locally with MediaPipe LLM Inference. The user imports a
 * model file they downloaded themselves (e.g. Gemma 3 1B IT or Qwen 2.5 1.5B Instruct in
 * .task/.litertlm format). No network access, no cost; quality depends on the model.
 */
class LocalLlmService(private val context: Context) {
    private var engine: LlmInference? = null
    private var loadedPath: String? = null
    private val lock = Mutex()

    val modelDir: File get() = File(context.noBackupFilesDir, "models").apply { mkdirs() }

    fun state(path: String?): EngineState = when {
        path == null -> EngineState.NOT_CONFIGURED
        File(path).isFile -> EngineState.AVAILABLE
        else -> EngineState.UNAVAILABLE
    }

    /** Copies a user-picked model file into private storage. Returns the stored path. */
    suspend fun import(uri: Uri, displayName: String): File = withContext(Dispatchers.IO) {
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "model.task" }
        val target = File(modelDir, safeName)
        val tmp = File(modelDir, "$safeName.part")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open model file" }
            tmp.outputStream().use { input.copyTo(it, 1 shl 20) }
        }
        if (!tmp.renameTo(target)) throw IllegalStateException("Could not store model")
        target
    }

    suspend fun release() = lock.withLock {
        engine?.close()
        engine = null
        loadedPath = null
    }

    suspend fun generate(path: String, prompt: String, maxTokens: Int = 768): String? = withContext(Dispatchers.Default) {
        lock.withLock {
            runCatching {
                if (engine == null || loadedPath != path) {
                    engine?.close()
                    engine = LlmInference.createFromOptions(
                        context,
                        LlmInference.LlmInferenceOptions.builder().setModelPath(path).setMaxTokens(maxTokens).build(),
                    )
                    loadedPath = path
                }
                engine!!.generateResponse(prompt).trim()
            }.onFailure { Log.w(TAG, "Local LLM failed", it); engine?.close(); engine = null; loadedPath = null }.getOrNull()
        }
    }

    companion object {
        private const val TAG = "LocalLlm"
    }
}

/**
 * Optional OpenAI-compatible endpoint that the user runs themselves (Ollama, llama.cpp server,
 * LM Studio on a home PC). Disabled by default; message text leaves the phone only when the user
 * turns this on and only for the explicit action they tap.
 */
class SelfHostedAiService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun complete(baseUrl: String, model: String, prompt: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(baseUrl.trimEnd('/') + "/v1/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val body = buildJsonObject {
                put("model", model)
                put("temperature", 0.3)
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "user"); put("content", prompt) })
                })
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(text) as JsonObject
            root["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content.trim()
        }.onFailure { Log.w("SelfHostedAi", "Request failed", it) }.getOrNull()
    }
}
