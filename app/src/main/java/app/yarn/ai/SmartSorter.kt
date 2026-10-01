package app.yarn.ai

import app.yarn.data.db.MessageEntity
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.SettingsRepository
import app.yarn.data.repo.InboxGroup
import app.yarn.intelligence.Category
import app.yarn.intelligence.CommercialSuffix
import app.yarn.intelligence.SenderAnalyzer
import app.yarn.intelligence.SenderNames

/**
 * Second opinion for messages the rule engine is unsure about, using Gemini Nano on the phone.
 * Only touches automatic decisions; anything the user corrected or pinned is left alone.
 */
class SmartSorter(
    private val db: YarnDatabase,
    private val nano: GeminiNanoService,
    private val settings: SettingsRepository,
) {
    suspend fun isAvailable(): Boolean {
        val s = settings.current()
        return s.aiEnabled && s.autoCategorize && nano.state() == EngineState.AVAILABLE
    }

    /** Sorts recent low-confidence messages. Returns how many were updated. */
    suspend fun sortRecent(limit: Int = 40): Int {
        if (!isAvailable()) return 0
        val since = System.currentTimeMillis() - 60L * 24 * 3_600_000
        val touched = HashSet<Long>()
        var count = 0
        for (m in db.messages().uncertain(CONFIDENCE_THRESHOLD, since, limit)) {
            if (sort(m)) { touched += m.conversationId; count++ }
        }
        touched.forEach { db.conversations().refresh(it) }
        return count
    }

    /** Sorts one newly received message if the rules weren't confident. */
    suspend fun sortOne(messageId: Long): Boolean {
        val m = db.messages().get(messageId) ?: return false
        if (m.outgoing || m.categorySource != "RULES" || m.categoryConfidence >= CONFIDENCE_THRESHOLD || m.body.isBlank()) return false
        if (!isAvailable()) return false
        return sort(m).also { if (it) db.conversations().refresh(m.conversationId) }
    }

    private suspend fun sort(m: MessageEntity): Boolean {
        val current = Category.fromName(m.category)
        // Certain signals are never second-guessed: a real one-time code, and promotional (-P)
        // headers, which Indian operators only allow for advertising.
        if (current == Category.OTP) return false
        if (SenderAnalyzer.commercialSuffix(m.address) == CommercialSuffix.PROMOTIONAL) return false
        val sender = SenderNames.pretty(m.address) ?: m.address
        val answer = nano.classify(sender, m.address, m.body)
        val group = answer?.let { runCatching { InboxGroup.valueOf(it) }.getOrNull() }
            // The model may only call something an OTP when the extractor found a code.
            ?.takeUnless { it == InboxGroup.OTP }
        val newCategory = when {
            group == null -> current ?: Category.PERSONAL
            InboxGroup.of(current) == group -> current!!
            else -> group.representative
        }
        db.messages().update(
            m.copy(
                category = newCategory.name,
                categorySource = "LLM",
                categoryConfidence = if (group != null) 0.9f else m.categoryConfidence,
                categoryReasons = if (group != null) "Sorted by Gemini Nano on your phone" else m.categoryReasons,
            ),
        )
        return group != null
    }

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.6f
    }
}
