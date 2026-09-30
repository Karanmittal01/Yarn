package app.yarn.intelligence

/** Per-conversation signals for prioritisation, supplied by the app. */
data class PriorityContext(
    /** Fraction of this sender's messages the user replied to (0..1), or null if unknown. */
    val replyRate: Float? = null,
    /** User pinned or starred this conversation. */
    val pinned: Boolean = false,
    val muted: Boolean = false,
)

data class AnalyzerConfig(
    val spamSensitivity: SpamSensitivity = SpamSensitivity.MEDIUM,
    val dayFirstDates: Boolean = true,
    val learningEnabled: Boolean = true,
)

/**
 * Orchestrates the on-device pipeline: extraction -> spam -> categorisation -> priority.
 * Pure Kotlin, deterministic, no network. Typical cost is well under a millisecond per message.
 */
class MessageAnalyzer(
    private val config: AnalyzerConfig = AnalyzerConfig(),
    private val learning: LearningState = LearningState(),
) {
    private val extractor = EntityExtractor(dayFirst = config.dayFirstDates)
    private val spamDetector = SpamDetector(config.spamSensitivity)

    fun extract(text: String, time: Long): List<Entity> = extractor.extract(text, time)

    fun analyze(input: MessageInput, context: PriorityContext = PriorityContext(), extraEntities: List<Entity> = emptyList()): Analysis {
        val entities = merge(extractor.extract(input.text, input.timestampMillis), extraEntities)
        val spam = applySpamLearning(input, spamDetector.assess(input, entities))
        val classification = classify(input, entities)
        val category = if (spam.verdict.isFiltered) Category.SPAM else classification.category
        val priority = PriorityScorer.score(input, category, spam, entities, context)
        return Analysis(classification, spam, entities, priority, PriorityScorer.isImportant(priority, category))
    }

    fun classify(input: MessageInput, entities: List<Entity>): Classification {
        val addr = LearningState.normalizeAddress(input.sender.address)
        learning.senderCategories[addr]?.let {
            return Classification(it, 1f, listOf("You chose to always file this sender under ${it.label()}"), Classification.Source.SENDER_RULE)
        }
        if (config.learningEnabled) {
            learning.similarCorrection(input) { it.category != null }?.let { (c, sim) ->
                return Classification(
                    c.category!!, sim.coerceIn(0.6f, 0.99f),
                    listOf("You moved a similar message to ${c.category.label()}"),
                    Classification.Source.SIMILAR_CORRECTION,
                )
            }
        }

        val rules = CategoryRules.score(input, entities)
        val learned = if (config.learningEnabled) learning.categoryPosterior(input) else emptyMap()
        if (learned.isEmpty()) {
            val top = rules.top
            return Classification(top, rules.topProbability, rules.reasons[top].orEmpty().take(3), Classification.Source.RULES)
        }
        val w = (learning.categoryExamples / 40f).coerceAtMost(0.6f)
        val blended = rules.probabilities.mapValues { (c, p) -> (1 - w) * p + w * (learned[c] ?: 0f) }
        val (top, p) = blended.maxBy { it.value }
        val ruleTop = rules.top
        return if (top != ruleTop && (learned[top] ?: 0f) > 0.5f) {
            val ev = learning.evidence(input, top)
            val why = if (ev.isEmpty()) "Learned from your past corrections" else "Learned from your corrections (words like “${ev.joinToString("”, “")}”)"
            Classification(top, p, listOf(why), Classification.Source.LEARNED)
        } else {
            Classification(top, p, rules.reasons[top].orEmpty().take(3), Classification.Source.RULES)
        }
    }

    private fun applySpamLearning(input: MessageInput, base: SpamAssessment): SpamAssessment {
        val addr = LearningState.normalizeAddress(input.sender.address)
        when (learning.senderTrust[addr]) {
            true -> return base.copy(verdict = if (base.verdict == SpamVerdict.PROMOTIONAL) SpamVerdict.PROMOTIONAL else SpamVerdict.CLEAN, reasons = listOf("You marked this sender as not spam") + base.reasons)
            false -> return base.copy(verdict = SpamVerdict.SPAM, score = maxOf(base.score, 0.9f), reasons = listOf("You marked this sender as spam") + base.reasons)
            null -> Unit
        }
        if (!config.learningEnabled) return base
        learning.similarCorrection(input) { it.isSpam != null }?.let { (c, _) ->
            return if (c.isSpam == true) {
                base.copy(verdict = if (base.verdict == SpamVerdict.SCAM) SpamVerdict.SCAM else SpamVerdict.SPAM, score = maxOf(base.score, 0.8f), reasons = listOf("You marked a similar message as spam") + base.reasons)
            } else {
                base.copy(verdict = SpamVerdict.CLEAN, score = minOf(base.score, 0.2f), reasons = listOf("You marked a similar message as not spam"))
            }
        }
        val learned = learning.spamPosterior(input) ?: return base
        val combined = 0.6f * base.score + 0.4f * learned
        return when {
            base.verdict == SpamVerdict.CLEAN && learned > 0.9f && combined >= 0.5f ->
                base.copy(verdict = SpamVerdict.SPAM, score = combined, reasons = base.reasons + "Resembles messages you marked as spam")
            base.verdict == SpamVerdict.SPAM && learned < 0.1f ->
                base.copy(verdict = SpamVerdict.CLEAN, score = combined, reasons = listOf("Resembles messages you marked as not spam"))
            else -> base
        }
    }

    private fun merge(primary: List<Entity>, extra: List<Entity>): List<Entity> {
        if (extra.isEmpty()) return primary
        val out = primary.toMutableList()
        for (e in extra) if (out.none { it.start < e.end && e.start < it.end }) out += e
        return out.sortedBy { it.start }
    }
}

object PriorityScorer {
    private val URGENT = Regex("(?i)\\b(urgent|asap|emergency|immediately|important|call me|call back|hospital|accident|right now|help)\\b")

    fun score(input: MessageInput, category: Category, spam: SpamAssessment, entities: List<Entity>, ctx: PriorityContext): Int {
        if (!input.isIncoming) return 0
        if (spam.verdict.isFiltered) return 0
        var s = when (category) {
            Category.OTP -> 80
            Category.PERSONAL -> if (input.sender.kind == SenderKind.CONTACT) 60 else 40
            Category.WORK -> 55
            Category.BANKING -> 55
            Category.PAYMENTS -> 50
            Category.BILLS -> 50
            Category.TRAVEL -> 50
            Category.DELIVERY -> 40
            Category.UPDATES -> 30
            Category.SHOPPING -> 25
            Category.PROMOTIONS -> 5
            Category.SPAM -> 0
        }
        if (URGENT.containsMatchIn(input.text)) s += 20
        if (input.sender.isStarredContact) s += 15
        if (ctx.pinned) s += 10
        if (input.text.contains('?') && category == Category.PERSONAL) s += 5
        ctx.replyRate?.let { s += ((it - 0.3f) * 30).toInt() }
        entities.firstOrNull { it.type == EntityType.AMOUNT && it.attributes["direction"] == "debit" }?.let { s += 5 }
        if (entities.any { it.type == EntityType.DATE_TIME } && category != Category.PROMOTIONS) s += 5
        if (spam.verdict == SpamVerdict.PROMOTIONAL) s -= 20
        if (ctx.muted) s -= 30
        return s.coerceIn(0, 100)
    }

    fun isImportant(score: Int, category: Category): Boolean = score >= 65 && category != Category.PROMOTIONS && category != Category.SPAM
}

fun Category.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }.let { if (this == Category.OTP) "OTP" else it }
