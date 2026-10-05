package app.yarn.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
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
 * Gemini Nano through the ML Kit GenAI Prompt API (AICore). Runs entirely on-device on supported
 * phones (recent Pixel and Galaxy flagships); elsewhere every call reports UNAVAILABLE and the app
 * falls back to its built-in engines. Free, no API key, no network at inference time.
 */
class GeminiNanoService(@Suppress("unused") private val context: Context) {
    private val model by lazy { Generation.getClient() }
    private val lock = Mutex()
    @Volatile private var cachedState: EngineState? = null

    private fun Int.toState() = when (this) {
        FeatureStatus.AVAILABLE -> EngineState.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> EngineState.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> EngineState.DOWNLOADING
        else -> EngineState.UNAVAILABLE
    }

    suspend fun state(): EngineState = runCatching { model.checkStatus().toState() }
        .getOrDefault(EngineState.UNAVAILABLE).also { cachedState = it }

    suspend fun summarizerState(): EngineState = state()
    suspend fun rewriterState(): EngineState = state()

    /** Downloads the model through AICore if the phone supports it. Silent no-op otherwise. */
    suspend fun ensureDownloaded() {
        if (state() != EngineState.DOWNLOADABLE) return
        runCatching { model.download().collect { } }.onFailure { Log.w(TAG, "Gemini Nano download failed", it) }
        state()
    }

    suspend fun download(onProgress: (Long, Long) -> Unit): Boolean {
        ensureDownloaded()
        onProgress(1, 1)
        return state() == EngineState.AVAILABLE
    }

    suspend fun generate(prompt: String, maxTokens: Int = 256, temperature: Float = 0.2f): String? {
        if ((cachedState ?: state()) != EngineState.AVAILABLE) return null
        return lock.withLock {
            runCatching {
                val request = generateContentRequest(TextPart(prompt)) {
                    this.temperature = temperature
                    this.maxOutputTokens = maxTokens
                    this.topK = 16
                }
                model.generateContent(request).candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
            }.onFailure { Log.w(TAG, "Gemini Nano generation failed", it) }.getOrNull()
        }
    }

    suspend fun summarize(conversationText: String): String? = generate(
        "Summarize this text-message conversation in at most 3 short bullet points. " +
            "Mention decisions, dates, amounts and anything the reader still needs to do. " +
            "Use only facts from the conversation. Reply with the bullet points only.\n\n$conversationText",
        maxTokens = 220,
    )

    suspend fun rewrite(text: String, style: RewriteStyle, @Suppress("UNUSED_PARAMETER") languageTag: String): List<String>? {
        val instruction = when (style) {
            RewriteStyle.SHORTEN -> "Make this message shorter while keeping its meaning."
            RewriteStyle.ELABORATE -> "Expand this message with a little more detail, keeping the same intent."
            RewriteStyle.FRIENDLY -> "Rewrite this message in a warm, friendly tone."
            RewriteStyle.PROFESSIONAL -> "Rewrite this message in a polite, professional tone."
            RewriteStyle.REPHRASE -> "Rephrase this message with the same meaning."
            RewriteStyle.EMOJIFY -> "Add a few fitting emojis to this message without changing the words much."
        }
        return generate("$instruction Keep the same language. Reply with only the rewritten message.\n\nMessage: $text", maxTokens = 200, temperature = 0.5f)
            ?.removeSurrounding("\"")?.let { listOf(it) }
    }

    /**
     * Sorts an SMS into one of Yarn's inbox groups. Used only for messages the built-in
     * rules are unsure about. Returns null when the model is unavailable or answers unclearly.
     */
    suspend fun classify(sender: String, header: String, text: String): String? {
        val prompt = """
            You sort SMS for an Indian messaging app by WHAT THE MESSAGE IS, never by which company sent it.
            PERSONAL: written by a person to you (friends, family, colleagues, a neighbour).
            OTP: contains a one-time password, verification code, login code or delivery PIN.
            TRANSACTIONS: money moved or is owed: debited, credited, paid, spent, charged, refunded, cashback credited, wallet or gift card balance, bill or EMI due, investments. This includes payments from Zomato, Swiggy, Amazon, Uber or any app, not just banks.
            SHOPPING: the progress of an order: placed, confirmed, packed, shipped, out for delivery, delivered, failed delivery, return pickup, cancelled.
            TRAVEL: trips and rides: train PNRs, flights, boarding, gates, buses, hotels, cabs and bike rides (driver arriving), travel bookings and changes.
            UPDATES: appointments, government notices, telecom or app service notices, account changes (login alerts, card blocked, KYC done) and safety advice ("we never ask for your OTP", "beware of fraud"). Banks send these too: no money moved means it is not TRANSACTIONS.
            OFFERS: advertising: sales, discounts, coupon codes, cashback offers, loan or card offers, pre-approved loans, "eligible for" a card, reward points to redeem, lounge or voucher perks to claim, new plans, "order now", "shop now", "apply now" — even when a bank sends it.
            The sender header ends in -P (promotional, always OFFERS), -S (service), -T (transactional) or -G (government); for -S and -T read the message itself.
            Sender: $sender ($header)
            Message: ${text.take(600)}
            Answer with one word: PERSONAL, OTP, TRANSACTIONS, SHOPPING, TRAVEL, UPDATES or OFFERS.
        """.trimIndent()
        val answer = generate(prompt, maxTokens = 4, temperature = 0f)?.uppercase() ?: return null
        return listOf("TRANSACTIONS", "SHOPPING", "PERSONAL", "TRAVEL", "UPDATES", "OFFERS", "OTP").firstOrNull { answer.contains(it) }
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
