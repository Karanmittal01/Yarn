package app.yarn.ai

import app.yarn.data.db.MessageEntity
import app.yarn.data.prefs.SettingsRepository
import app.yarn.intelligence.Category
import app.yarn.intelligence.ConversationSummary
import app.yarn.intelligence.ExtractiveSummarizer
import app.yarn.intelligence.QuickReplies
import app.yarn.intelligence.SummaryMessage

/** Which engine produced a generative result; shown to the user for transparency. */
enum class Engine(val label: String) {
    GEMINI_NANO("Gemini Nano (on-device)"),
    LOCAL_LLM("Local model (on-device)"),
    SELF_HOSTED("Your self-hosted AI"),
    EXTRACTIVE("Quick summary (on-device)"),
    ML_KIT("ML Kit (on-device)"),
    RULES("Built-in suggestions"),
}

data class AssistantSummary(val summary: ConversationSummary, val engine: Engine, val freeText: String? = null)

sealed interface RewriteResult {
    data class Success(val options: List<String>, val engine: Engine) : RewriteResult
    data class Unavailable(val reason: String) : RewriteResult
}

data class EngineStatus(
    val nanoSummarizer: EngineState,
    val nanoRewriter: EngineState,
    val localLlm: EngineState,
    val selfHosted: EngineState,
    val mlEntitiesDownloaded: Boolean,
)

/**
 * Picks the best available engine for each generative task, always preferring on-device.
 * Order: Gemini Nano -> user-imported local model -> user's self-hosted endpoint (opt-in) ->
 * deterministic fallback. Messaging never depends on any of these.
 */
class AssistantService(
    private val settings: SettingsRepository,
    private val nano: GeminiNanoService,
    private val localLlm: LocalLlmService,
    private val selfHosted: SelfHostedAiService,
    private val smartReply: SmartReplyService,
    private val language: LanguageService,
    private val mlEntities: MlEntityService,
) {
    private val extractive = ExtractiveSummarizer()

    suspend fun status(): EngineStatus {
        val s = settings.current()
        return EngineStatus(
            nanoSummarizer = nano.summarizerState(),
            nanoRewriter = nano.rewriterState(),
            localLlm = localLlm.state(s.localLlmPath),
            selfHosted = if (s.selfHostedAiEnabled && s.selfHostedAiUrl.isNotBlank()) EngineState.AVAILABLE else EngineState.NOT_CONFIGURED,
            mlEntitiesDownloaded = mlEntities.isModelDownloaded(),
        )
    }

    suspend fun summarize(messages: List<SummaryMessage>): AssistantSummary {
        val fallback = extractive.summarize(messages)
        val s = settings.current()
        if (!s.aiEnabled || messages.isEmpty()) return AssistantSummary(fallback, Engine.EXTRACTIVE)
        val transcript = messages.takeLast(80).joinToString("\n") { "${it.sender}: ${it.text.replace('\n', ' ')}" }
        val lang = language.identify(transcript)

        if (lang == null || lang.startsWith("en")) {
            nano.summarize(transcript)?.let { text ->
                return AssistantSummary(fallback.copy(bullets = bulletLines(text), method = "gemini-nano"), Engine.GEMINI_NANO, text)
            }
        }
        val prompt = """
            Summarize this text-message conversation in at most 3 short bullet points.
            Mention decisions, dates, amounts and anything the reader still needs to do.
            Only use facts from the conversation. Reply with the bullet points only.

            $transcript
        """.trimIndent()
        s.localLlmPath?.let { path ->
            localLlm.generate(path, prompt)?.let { text ->
                return AssistantSummary(fallback.copy(bullets = bulletLines(text), method = "local-llm"), Engine.LOCAL_LLM, text)
            }
        }
        if (s.selfHostedAiEnabled && s.selfHostedAiUrl.isNotBlank()) {
            selfHosted.complete(s.selfHostedAiUrl, s.selfHostedAiModel, prompt)?.let { text ->
                return AssistantSummary(fallback.copy(bullets = bulletLines(text), method = "self-hosted"), Engine.SELF_HOSTED, text)
            }
        }
        return AssistantSummary(fallback, Engine.EXTRACTIVE)
    }

    suspend fun rewrite(text: String, style: RewriteStyle): RewriteResult {
        val s = settings.current()
        if (!s.aiEnabled) return RewriteResult.Unavailable("AI features are turned off in Settings.")
        val lang = language.identify(text) ?: "en"
        nano.rewrite(text, style, lang)?.takeIf { it.isNotEmpty() }?.let { return RewriteResult.Success(it.distinct(), Engine.GEMINI_NANO) }
        val instruction = when (style) {
            RewriteStyle.SHORTEN -> "Make this message shorter while keeping its meaning."
            RewriteStyle.ELABORATE -> "Expand this message with a little more detail, keeping the same intent."
            RewriteStyle.FRIENDLY -> "Rewrite this message in a warm, friendly tone."
            RewriteStyle.PROFESSIONAL -> "Rewrite this message in a polite, professional tone."
            RewriteStyle.REPHRASE -> "Rephrase this message differently with the same meaning."
            RewriteStyle.EMOJIFY -> "Add a few fitting emojis to this message without changing the words much."
        }
        val prompt = "$instruction Keep the same language. Reply with only the rewritten message.\n\nMessage: $text"
        s.localLlmPath?.let { path ->
            localLlm.generate(path, prompt, maxTokens = 512)?.let { return RewriteResult.Success(listOf(clean(it)), Engine.LOCAL_LLM) }
        }
        if (s.selfHostedAiEnabled && s.selfHostedAiUrl.isNotBlank()) {
            selfHosted.complete(s.selfHostedAiUrl, s.selfHostedAiModel, prompt)?.let { return RewriteResult.Success(listOf(clean(it)), Engine.SELF_HOSTED) }
        }
        return RewriteResult.Unavailable(
            "Rewriting needs an on-device language model. This phone doesn't offer Gemini Nano; " +
                "you can import a free open model (e.g. Gemma or Qwen) in Settings › AI.",
        )
    }

    /** Reply suggestions: ML Kit Smart Reply first, then built-in intent rules. */
    suspend fun smartReplies(recent: List<MessageEntity>, category: Category): Pair<List<String>, Engine> {
        val s = settings.current()
        if (!s.aiEnabled || !s.smartReplies || recent.isEmpty()) return emptyList<String>() to Engine.RULES
        if (category != Category.PERSONAL && category != Category.WORK) return emptyList<String>() to Engine.RULES
        val ordered = recent.sortedBy { it.date }.filter { it.body.isNotBlank() }
        val last = ordered.lastOrNull() ?: return emptyList<String>() to Engine.RULES
        if (last.outgoing) return emptyList<String>() to Engine.RULES
        val ml = smartReply.suggest(ordered.map { SmartReplyService.Turn(it.body, it.date, it.outgoing, it.address) })
        if (ml.isNotEmpty()) return ml to Engine.ML_KIT
        return QuickReplies.suggest(last.body, category) to Engine.RULES
    }

    private fun bulletLines(text: String): List<String> =
        text.lines().map { it.trim().removePrefix("*").removePrefix("-").removePrefix("•").trim() }.filter { it.isNotBlank() }.take(5)

    private fun clean(text: String) = text.trim().removePrefix("\"").removeSuffix("\"").trim()
}
