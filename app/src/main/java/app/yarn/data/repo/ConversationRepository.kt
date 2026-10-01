package app.yarn.data.repo

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import app.yarn.ai.IntelligenceEngine
import app.yarn.data.contacts.ContactsRepository
import app.yarn.data.db.BlockRuleEntity
import app.yarn.data.db.ConversationEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageKind
import app.yarn.data.db.MessageSearchResult
import app.yarn.data.db.YarnDatabase
import app.yarn.intelligence.Category
import app.yarn.intelligence.PriorityContext
import app.yarn.messaging.MessageSender
import app.yarn.notifications.Notifier
import app.yarn.telephony.PhoneNumbers
import app.yarn.telephony.SystemBlockList
import app.yarn.telephony.TelephonyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.DateFormat
import java.util.Date

enum class InboxView { ALL, IMPORTANT, UNREAD, CATEGORY, ARCHIVED, SPAM, BLOCKED, STARRED }

data class InboxFilter(val view: InboxView = InboxView.ALL, val categories: Set<Category> = emptySet()) {
    companion object {
        val TRANSACTIONS = setOf(Category.OTP, Category.BANKING, Category.PAYMENTS, Category.BILLS)
        val ORDERS = setOf(Category.DELIVERY, Category.SHOPPING)
    }
}

/**
 * Conversation-level reads and bulk actions. Every action that changes OS-visible state
 * (read flags, deletions, blocks) is mirrored into the system provider when we are allowed to.
 */
class ConversationRepository(
    private val context: Context,
    private val db: YarnDatabase,
    private val store: TelephonyStore,
    private val contacts: ContactsRepository,
    private val engine: IntelligenceEngine,
    private val notifier: Notifier,
    private val providerLock: Mutex,
    scope: CoroutineScope,
) : MessageSender.ConversationWriter {

    @Volatile private var blockRules: List<BlockRuleEntity> = emptyList()

    init {
        scope.launch { db.blocks().observe().collect { blockRules = it } }
    }

    fun observe(filter: InboxFilter): Flow<List<ConversationEntity>> {
        val base = "blocked = 0 AND (messageCount > 0 OR (draft IS NOT NULL AND draft != ''))"
        val live = "$base AND archived = 0 AND spam = 0"
        val where = when (filter.view) {
            InboxView.ALL -> live
            InboxView.IMPORTANT -> "$live AND ((unreadCount > 0 AND importance >= 65) OR starred = 1)"
            InboxView.UNREAD -> "$live AND unreadCount > 0"
            InboxView.CATEGORY -> "$live AND category IN (${filter.categories.joinToString(",") { "'${it.name}'" }})"
            InboxView.ARCHIVED -> "$base AND archived = 1 AND spam = 0"
            InboxView.SPAM -> "$base AND spam = 1"
            InboxView.BLOCKED -> "blocked = 1"
            InboxView.STARRED -> "$base AND starred = 1"
        }
        val order = if (filter.view == InboxView.ALL || filter.view == InboxView.CATEGORY) "pinned DESC, pinnedAt DESC, lastMessageAt DESC" else "lastMessageAt DESC"
        return db.conversations().observe(SimpleSQLiteQuery("SELECT * FROM conversations WHERE $where ORDER BY $order"))
    }

    override suspend fun ensure(threadId: Long, addresses: List<String>) {
        val existing = db.conversations().get(threadId)
        val iso = PhoneNumbers.countryIso(context)
        if (existing == null) {
            val first = addresses.firstOrNull().orEmpty()
            db.conversations().insertIgnore(
                ConversationEntity(
                    id = threadId, addresses = addresses, displayName = contacts.displayName(addresses, iso),
                    hasBusinessSender = addresses.size == 1 && (first.any { it.isLetter() } || first.filter { it.isDigit() }.length in 3..8),
                ),
            )
        } else if (existing.addresses.isEmpty() && addresses.isNotEmpty()) {
            db.conversations().update(existing.copy(addresses = addresses, displayName = contacts.displayName(addresses, iso)))
        }
    }

    suspend fun titleFor(id: Long): String = db.conversations().get(id)?.title ?: "Unknown"

    /** Re-resolves contact names after the address book changes. */
    suspend fun refreshDisplayNames() {
        val iso = PhoneNumbers.countryIso(context)
        for (c in db.conversations().all()) {
            val name = contacts.displayName(c.addresses, iso)
            if (name != c.displayName) db.conversations().setDisplayName(c.id, name)
        }
    }

    suspend fun priorityContext(conversationId: Long): PriorityContext {
        val c = db.conversations().get(conversationId) ?: return PriorityContext()
        val incoming = db.messages().incomingCount(conversationId)
        val outgoing = db.messages().outgoingCount(conversationId)
        val rate = if (incoming >= 3) (outgoing.toFloat() / incoming).coerceIn(0f, 1f) else null
        return PriorityContext(replyRate = rate, pinned = c.pinned || c.starred, muted = c.muted)
    }

    // ---- blocking -----------------------------------------------------------------------------

    enum class Block { NONE, FILTER, DROP }

    fun blockDecision(address: String, text: String): Block {
        val key = PhoneNumbers.matchKey(address)
        if (blockRules.any { it.type == BlockRuleEntity.NUMBER && it.value == key }) return Block.DROP
        if (PhoneNumbers.isDialable(address) && SystemBlockList.isBlocked(context, address)) return Block.DROP
        val lower = text.lowercase()
        if (lower.isNotEmpty() && blockRules.any { it.type == BlockRuleEntity.KEYWORD && lower.contains(it.value) }) return Block.FILTER
        return Block.NONE
    }

    suspend fun blockNumbers(addresses: Collection<String>) {
        val now = System.currentTimeMillis()
        for (a in addresses) {
            db.blocks().insert(BlockRuleEntity(type = BlockRuleEntity.NUMBER, value = PhoneNumbers.matchKey(a), createdAt = now))
            if (PhoneNumbers.isDialable(a)) SystemBlockList.block(context, a)
        }
    }

    suspend fun unblockNumber(address: String) {
        db.blocks().delete(BlockRuleEntity.NUMBER, PhoneNumbers.matchKey(address))
        if (PhoneNumbers.isDialable(address)) SystemBlockList.unblock(context, address)
        val key = PhoneNumbers.matchKey(address)
        val affected = db.conversations().all().filter { c -> c.blocked && c.addresses.any { PhoneNumbers.matchKey(it) == key } }
        db.conversations().setBlocked(affected.map { it.id }, false)
    }

    suspend fun addKeywordFilter(keyword: String) {
        val k = keyword.trim().lowercase()
        if (k.length < 2) return
        db.blocks().insert(BlockRuleEntity(type = BlockRuleEntity.KEYWORD, value = k, createdAt = System.currentTimeMillis()))
    }

    suspend fun removeRule(id: Long) = db.blocks().delete(id)

    suspend fun block(conversationIds: Collection<Long>) {
        val convs = db.conversations().getAll(conversationIds)
        blockNumbers(convs.flatMap { it.addresses })
        db.conversations().setBlocked(conversationIds, true)
        conversationIds.forEach { notifier.cancel(it) }
        markRead(conversationIds)
    }

    suspend fun unblock(conversationIds: Collection<Long>) {
        val convs = db.conversations().getAll(conversationIds)
        convs.flatMap { it.addresses }.forEach { unblockNumber(it) }
        db.conversations().setBlocked(conversationIds, false)
    }

    // ---- bulk conversation actions ----------------------------------------------------------

    suspend fun markRead(conversationIds: Collection<Long>) {
        if (conversationIds.isEmpty()) return
        val sms = db.messages().unreadProviderIds(conversationIds, MessageKind.SMS)
        val mms = db.messages().unreadProviderIds(conversationIds, MessageKind.MMS)
        db.messages().markRead(conversationIds)
        providerLock.withLock { store.markRead(sms, mms) }
        conversationIds.forEach { db.conversations().refresh(it); notifier.cancel(it) }
    }

    suspend fun markUnread(conversationIds: Collection<Long>) {
        for (id in conversationIds) {
            db.messages().markLatestUnread(id)
            val latest = db.messages().recent(id, 20).firstOrNull { !it.outgoing }
            latest?.providerId?.let { pid -> providerLock.withLock { store.markUnread(latest.kind, pid) } }
            db.conversations().refresh(id)
        }
    }

    suspend fun setPinned(ids: Collection<Long>, pinned: Boolean) = db.conversations().setPinned(ids, pinned, System.currentTimeMillis())
    suspend fun setArchived(ids: Collection<Long>, archived: Boolean) {
        db.conversations().setArchived(ids, archived)
        if (archived) ids.forEach { notifier.cancel(it) }
    }
    suspend fun setMuted(ids: Collection<Long>, muted: Boolean) {
        db.conversations().setMuted(ids, muted)
        if (muted) ids.forEach { notifier.cancel(it) }
    }
    suspend fun setStarred(ids: Collection<Long>, starred: Boolean) = db.conversations().setStarred(ids, starred)

    suspend fun delete(conversationIds: Collection<Long>) {
        val messages = db.messages().inConversations(conversationIds)
        deleteLocalFiles(messages.map { it.id })
        providerLock.withLock { conversationIds.filter { it > 0 }.forEach { store.deleteThread(it) } }
        db.conversations().delete(conversationIds)
        conversationIds.forEach { notifier.cancel(it) }
    }

    /** Marks conversations as spam / not spam, teaches the model, and remembers the sender. */
    suspend fun setSpam(conversationIds: Collection<Long>, spam: Boolean) {
        val messages = db.messages().inConversations(conversationIds).filter { !it.outgoing }.sortedByDescending { it.date }.take(20)
        engine.learnSpam(messages, spam)
        for (id in conversationIds) {
            if (spam) {
                db.conversations().setCategory(listOf(id), Category.SPAM.name, locked = true)
            } else {
                val latest = db.messages().recent(id, 20).firstOrNull { !it.outgoing }
                db.conversations().setCategory(listOf(id), latest?.category?.takeIf { it != Category.SPAM.name } ?: Category.PERSONAL.name, locked = false)
            }
            reanalyze(id)
            if (spam) notifier.cancel(id)
        }
    }

    /** User moved conversations to a category. Optionally always apply to these senders. */
    suspend fun setCategory(conversationIds: Collection<Long>, category: Category, alwaysForSender: Boolean) {
        val latestIncoming = conversationIds.mapNotNull { id -> db.messages().recent(id, 10).firstOrNull { !it.outgoing } }
        engine.learnCategory(latestIncoming, category, alwaysForSender)
        db.conversations().setCategory(conversationIds, category.name, locked = true)
        conversationIds.forEach { db.conversations().refresh(it) }
    }

    /** Re-runs analysis on recent messages of a conversation (after learning changed). */
    suspend fun reanalyze(conversationId: Long) {
        val ctx = priorityContext(conversationId)
        db.messages().recent(conversationId, 50).forEach { engine.analyzeAndStore(it.id, ctx) }
        db.conversations().refresh(conversationId)
    }

    suspend fun setPreferredSim(conversationId: Long, subId: Int) = db.conversations().setPreferredSub(conversationId, subId)
    suspend fun rename(conversationId: Long, title: String?) = db.conversations().setCustomTitle(conversationId, title?.takeIf { it.isNotBlank() })
    suspend fun saveDraft(conversationId: Long, text: String) {
        val current = db.conversations().get(conversationId)?.draft.orEmpty()
        if (current == text.ifBlank { "" }) return // unchanged: don't bump the conversation to the top
        db.conversations().setDraft(conversationId, text.ifBlank { null }, System.currentTimeMillis())
        db.conversations().refresh(conversationId)
    }

    // ---- messages ---------------------------------------------------------------------------

    suspend fun deleteMessages(ids: Collection<Long>) {
        val messages = db.messages().getAll(ids)
        deleteLocalFiles(ids)
        providerLock.withLock {
            messages.forEach { m ->
                m.providerId?.let { if (m.kind == MessageKind.SMS) store.deleteSms(it) else store.deleteMms(it) }
                m.extraProviderIds?.split(',')?.mapNotNull { it.toLongOrNull() }?.forEach { store.deleteSms(it) }
            }
        }
        db.messages().delete(ids)
        messages.map { it.conversationId }.distinct().forEach { db.conversations().refresh(it) }
    }

    suspend fun setMessagesStarred(ids: Collection<Long>, starred: Boolean) = db.messages().setStarred(ids, starred)

    /** Correct the category of individual messages (from the conversation screen). */
    suspend fun recategorizeMessages(ids: Collection<Long>, category: Category, alwaysForSender: Boolean) {
        val messages = db.messages().getAll(ids)
        engine.learnCategory(messages, category, alwaysForSender)
        messages.forEach { db.messages().update(it.copy(category = category.name, categorySource = "USER", categoryReasons = "You chose this category")) }
        messages.map { it.conversationId }.distinct().forEach { db.conversations().refresh(it) }
    }

    private suspend fun deleteLocalFiles(messageIds: Collection<Long>) {
        db.messages().attachmentsFor(messageIds).forEach { a ->
            val uri = Uri.parse(a.uri)
            if (uri.scheme == "file") uri.path?.let { File(it).delete() }
        }
    }

    /** Plain-text transcript for sharing or exporting a conversation. */
    suspend fun transcript(conversationId: Long): String {
        val c = db.conversations().get(conversationId) ?: return ""
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val msgs = db.messages().recent(conversationId, 5000).sortedBy { it.date }
        return buildString {
            appendLine("Conversation with ${c.title}")
            appendLine()
            msgs.forEach { m -> appendLine("[${fmt.format(Date(m.date))}] ${if (m.outgoing) "Me" else senderName(m)}: ${m.body}${if (m.hasAttachments) " [attachment]" else ""}") }
        }
    }

    fun senderName(m: MessageEntity): String {
        if (m.outgoing) return "You"
        return contacts.lookup(m.address)?.name ?: PhoneNumbers.format(m.address, PhoneNumbers.countryIso(context))
    }

    // ---- search -------------------------------------------------------------------------------

    data class SearchResults(val conversations: List<ConversationEntity>, val messages: List<MessageSearchResult>)

    suspend fun search(query: String, categories: Set<Category> = emptySet(), starredOnly: Boolean = false): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults(emptyList(), emptyList())
        val like = "%" + q.replace("%", "").replace("_", "") + "%"
        val digits = q.filter { it.isDigit() }
        val convs = (db.conversations().searchByName(like) + if (digits.length >= 3) db.conversations().searchByName("%$digits%") else emptyList())
            .distinctBy { it.id }.filter { !it.blocked }
        val match = ftsQuery(q)
        val messages = if (match.isEmpty()) emptyList() else runCatching { db.messages().search(match, 300) }.getOrDefault(emptyList())
            .filter { r -> (categories.isEmpty() || Category.fromName(r.message.category) in categories) && (!starredOnly || r.message.starred) }
        return SearchResults(if (categories.isEmpty() && !starredOnly) convs else emptyList(), messages)
    }

    /** Turns free text into a safe FTS4 prefix query: every word must match (AND). */
    private fun ftsQuery(q: String): String =
        q.split(Regex("\\s+")).map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }.filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }
}
