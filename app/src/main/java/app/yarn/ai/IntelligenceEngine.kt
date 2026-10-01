package app.yarn.ai

import android.util.Log
import app.yarn.data.contacts.ContactsRepository
import app.yarn.data.db.CorrectionEntity
import app.yarn.data.db.ExtractedEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.SenderRuleEntity
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.AppSettings
import app.yarn.data.prefs.SettingsRepository
import app.yarn.intelligence.AnalyzerConfig
import app.yarn.intelligence.Analysis
import app.yarn.intelligence.Category
import app.yarn.intelligence.Classification
import app.yarn.intelligence.Correction
import app.yarn.intelligence.Entity
import app.yarn.intelligence.EntityType
import app.yarn.intelligence.LearningState
import app.yarn.intelligence.MessageAnalyzer
import app.yarn.intelligence.MessageInput
import app.yarn.intelligence.PriorityContext
import app.yarn.intelligence.SenderInfo
import app.yarn.intelligence.SenderKind
import app.yarn.intelligence.SpamAssessment
import app.yarn.intelligence.SpamVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-facing wrapper around the pure-Kotlin analyzer: resolves senders against contacts, applies
 * user settings, persists results, and rebuilds the learned model whenever the user corrects it.
 */
class IntelligenceEngine(
    private val db: YarnDatabase,
    private val settings: SettingsRepository,
    private val contacts: ContactsRepository,
    scope: CoroutineScope,
) {
    @Volatile private var analyzer = MessageAnalyzer()
    @Volatile private var current = AppSettings()
    private val reloadLock = Mutex()

    init {
        scope.launch {
            var previous: AppSettings? = null
            settings.settings.collect { s ->
                val p = previous
                current = s
                previous = s
                if (p == null || p.spamSensitivity != s.spamSensitivity || p.learningEnabled != s.learningEnabled || p.dayFirstDates != s.dayFirstDates) {
                    reload()
                }
            }
        }
    }

    /** Rebuilds the learned state from persisted corrections and sender rules. */
    suspend fun reload() = reloadLock.withLock {
        val s = settings.current()
        current = s
        val corrections = db.learning().corrections().map { c ->
            Correction(
                text = c.text,
                senderAddress = c.senderAddress,
                senderKind = runCatching { SenderKind.valueOf(c.senderKind) }.getOrDefault(SenderKind.PHONE_NUMBER),
                category = Category.fromName(c.category),
                isSpam = c.isSpam,
                timestampMillis = c.createdAt,
            )
        }
        val rules = db.learning().rules()
        val learning = LearningState(
            corrections = if (s.learningEnabled) corrections else emptyList(),
            senderCategories = rules.mapNotNull { r -> Category.fromName(r.category)?.let { r.address to it } }.toMap(),
            senderTrust = rules.mapNotNull { r -> r.trusted?.let { r.address to it } }.toMap(),
        )
        analyzer = MessageAnalyzer(
            AnalyzerConfig(spamSensitivity = s.spamSensitivity, dayFirstDates = s.dayFirstDates, learningEnabled = s.learningEnabled),
            learning,
        )
        Log.d(TAG, "Model rebuilt: ${learning.categoryExamples} category and ${learning.spamExamples} spam examples")
    }

    fun senderInfo(address: String): SenderInfo {
        val contact = contacts.lookup(address)
        return SenderInfo(address, isContact = contact != null, contactName = contact?.name, isStarredContact = contact?.starred == true)
    }

    fun extract(text: String, time: Long): List<Entity> = if (current.aiEnabled) analyzer.extract(text, time) else emptyList()

    fun analyze(
        text: String,
        address: String,
        time: Long,
        incoming: Boolean,
        hasAttachments: Boolean = false,
        context: PriorityContext = PriorityContext(),
    ): Analysis {
        val sender = senderInfo(address)
        val s = current
        if (!s.aiEnabled) {
            return Analysis(
                Classification(basicCategory(sender), 0f, emptyList(), Classification.Source.RULES),
                SpamAssessment(SpamVerdict.CLEAN, 0f, emptyList()), emptyList(), 0, false,
            )
        }
        val raw = analyzer.analyze(MessageInput(text, sender, time, incoming, hasAttachments), context)
        var result = raw
        if (!s.autoCategorize) {
            result = result.copy(classification = Classification(basicCategory(sender), 0f, emptyList(), Classification.Source.RULES))
        }
        if (!s.spamFilter && result.spam.verdict.isFiltered) {
            // Keep link warnings, but never hide messages when the user disabled filtering.
            result = result.copy(spam = result.spam.copy(verdict = SpamVerdict.CLEAN))
        }
        return result
    }

    private fun basicCategory(sender: SenderInfo) = if (sender.isBusinessLike) Category.UPDATES else Category.PERSONAL

    /** Applies analysis to a message row and returns the updated row plus entity rows. */
    fun annotate(m: MessageEntity, context: PriorityContext = PriorityContext()): Pair<MessageEntity, List<ExtractedEntity>> {
        val address = if (m.outgoing) m.address.substringBefore(',') else m.address
        val a = analyze(m.body, address, m.date, !m.outgoing, m.hasAttachments, context)
        val updated = m.copy(
            category = if (m.outgoing) m.category else a.category.name,
            categorySource = a.classification.source.name,
            categoryConfidence = a.classification.confidence,
            categoryReasons = a.classification.reasons.joinToString("\n"),
            spamVerdict = a.spam.verdict.name,
            spamScore = a.spam.score,
            spamReasons = a.spam.reasons.joinToString("\n"),
            riskyUrls = a.spam.riskyUrls.joinToString("\n"),
            priority = a.priority,
            important = a.isImportant,
            analyzed = true,
        )
        return updated to a.entities.map { it.toRow(m.id, "rules") }
    }

    /** Analyses and persists a stored message (entities included). */
    suspend fun analyzeAndStore(messageId: Long, context: PriorityContext = PriorityContext()): MessageEntity? {
        val m = db.messages().get(messageId) ?: return null
        val (updated, entities) = annotate(m, context)
        db.messages().update(updated)
        db.messages().replaceEntities(m.id, "rules", entities)
        return updated
    }

    // ---- learning from the user -----------------------------------------------------------

    /**
     * User moved messages/conversations to [category]. We store each as a correction and, when
     * [alwaysForSender] is set, a sticky sender rule. The model is rebuilt immediately.
     */
    suspend fun learnCategory(messages: List<MessageEntity>, category: Category, alwaysForSender: Boolean) {
        val now = System.currentTimeMillis()
        for (m in messages.filter { !it.outgoing }) {
            val kind = senderInfo(m.address).kind
            db.learning().insertCorrection(
                CorrectionEntity(
                    messageId = m.id, text = m.body.take(2000), senderAddress = m.address, senderKind = kind.name,
                    fromCategory = m.category, category = category.name, isSpam = null, createdAt = now,
                ),
            )
            if (alwaysForSender) {
                val key = LearningState.normalizeAddress(m.address)
                val existing = db.learning().rule(key)
                db.learning().upsertRule(
                    SenderRuleEntity(key, m.address, category.name, existing?.trusted, now),
                )
            }
        }
        reload()
    }

    suspend fun learnSpam(messages: List<MessageEntity>, isSpam: Boolean) {
        val now = System.currentTimeMillis()
        val senders = HashSet<String>()
        for (m in messages.filter { !it.outgoing }) {
            val kind = senderInfo(m.address).kind
            db.learning().insertCorrection(
                CorrectionEntity(
                    messageId = m.id, text = m.body.take(2000), senderAddress = m.address, senderKind = kind.name,
                    fromCategory = m.category, category = null, isSpam = isSpam, createdAt = now,
                ),
            )
            senders += m.address
        }
        for (address in senders) {
            val key = LearningState.normalizeAddress(address)
            val existing = db.learning().rule(key)
            db.learning().upsertRule(SenderRuleEntity(key, address, existing?.category, isSpam.not(), now))
        }
        reload()
    }

    suspend fun resetLearning() {
        db.learning().clearCorrections()
        db.learning().clearRules()
        reload()
    }

    companion object {
        private const val TAG = "IntelligenceEngine"

        fun Entity.toRow(messageId: Long, source: String) = ExtractedEntity(
            messageId = messageId, type = type.name, text = text, value = value, start = start, end = end,
            attributes = attributes, source = source,
        )

        fun ExtractedEntity.typeOrNull(): EntityType? = runCatching { EntityType.valueOf(type) }.getOrNull()
    }
}
