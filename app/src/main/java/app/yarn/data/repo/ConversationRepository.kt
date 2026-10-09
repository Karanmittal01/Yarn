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
import app.yarn.intelligence.group
import app.yarn.intelligence.CategoryGroup
import app.yarn.intelligence.PriorityContext
import app.yarn.messaging.MessageSender
import app.yarn.notifications.Notifier
import app.yarn.telephony.PhoneNumbers
import app.yarn.telephony.SystemBlockList
import app.yarn.telephony.TelephonyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.DateFormat
import java.util.Date
import app.yarn.R

enum class InboxView { ALL, IMPORTANT, UNREAD, CATEGORY, ARCHIVED, SPAM, BLOCKED, STARRED }

data class InboxFilter(val view: InboxView = InboxView.ALL, val categories: Set<Category> = emptySet())

/**
 * The six inbox groups people see. The analyser still works with finer categories internally
 * (useful for priority and notifications), but the UI keeps choices simple.
 */
enum class InboxGroup(@androidx.annotation.StringRes val label: Int, val kind: CategoryGroup, val representative: Category) {
    PERSONAL(R.string.group_personal, CategoryGroup.PERSONAL, Category.PERSONAL),
    OTP(R.string.group_otp, CategoryGroup.OTP, Category.OTP),
    TRANSACTIONS(R.string.group_transactions, CategoryGroup.TRANSACTIONS, Category.BANKING),
    SHOPPING(R.string.group_shopping, CategoryGroup.SHOPPING, Category.SHOPPING),
    TRAVEL(R.string.group_travel, CategoryGroup.TRAVEL, Category.TRAVEL),
    UPDATES(R.string.group_updates, CategoryGroup.UPDATES, Category.UPDATES),
    OFFERS(R.string.group_offers, CategoryGroup.OFFERS, Category.PROMOTIONS);

    val categories: Set<Category> get() = Category.entries.filter { it.group == kind }.toSet()
    val filter: InboxFilter get() = InboxFilter(InboxView.CATEGORY, categories)

    companion object {
        fun of(category: Category?): InboxGroup? = category?.group?.let { k -> entries.firstOrNull { it.kind == k } }
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
        if (filter.view == InboxView.CATEGORY) return observeSection(filter.categories)
        val where = when (filter.view) {
            InboxView.ALL, InboxView.CATEGORY -> live
            InboxView.IMPORTANT -> "$live AND ((unreadCount > 0 AND importance >= 65) OR starred = 1)"
            InboxView.UNREAD -> "$live AND unreadCount > 0"
            InboxView.ARCHIVED -> "$base AND archived = 1 AND spam = 0"
            InboxView.SPAM -> "$base AND spam = 1"
            InboxView.BLOCKED -> "blocked = 1"
            InboxView.STARRED -> "$base AND starred = 1"
        }
        val order = if (filter.view == InboxView.ALL) "pinned DESC, pinnedAt DESC, lastMessageAt DESC" else "lastMessageAt DESC"
        return db.conversations().observe(SimpleSQLiteQuery("SELECT * FROM conversations WHERE $where ORDER BY $order"))
    }

    /**
     * A section lists every chat with a received message of its kind, so a bank chat can be in
     * OTP, Transactions and Offers at once. Each row shows only that section's newest message and
     * unread count. A chat the user moved to a category stays there as a whole. Personal shows
     * people's chats in full.
     */
    private fun observeSection(categories: Set<Category>): Flow<List<ConversationEntity>> {
        val personal = Category.PERSONAL in categories
        return db.conversations().observeSections(SimpleSQLiteQuery(sectionQuery(categories))).map { rows ->
            if (personal) rows.map { it.conversation }
            else rows.map { r ->
                r.conversation.copy(
                    snippet = r.sectionSnippet.take(200).ifBlank { if (r.sectionAttachments) "Attachment" else "" },
                    snippetFromMe = false,
                    snippetStatus = app.yarn.data.db.MessageStatus.RECEIVED,
                    lastMessageAt = r.sectionDate,
                    unreadCount = r.sectionUnread,
                    draft = null,
                    hasFailed = false,
                )
            }
        }
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

    suspend fun titleFor(id: Long): String = db.conversations().get(id)?.title ?: context.getString(R.string.unknown)

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

    /**
     * Reads one section of these chats ([categories]), leaving their other messages unread. Chats the
     * user moved to a category are one section, so they're read whole.
     */
    suspend fun markRead(conversationIds: Collection<Long>, categories: Set<Category>) {
        if (categories.isEmpty()) return markRead(conversationIds)
        val (locked, open) = db.conversations().getAll(conversationIds).partition { it.categoryLocked }
        markRead(locked.map { it.id })
        val ids = open.map { it.id }
        if (ids.isEmpty()) return
        val names = categories.map { it.name }
        val sms = db.messages().unreadProviderIdsIn(ids, names, MessageKind.SMS)
        val mms = db.messages().unreadProviderIdsIn(ids, names, MessageKind.MMS)
        db.messages().markReadIn(ids, names)
        providerLock.withLock { store.markRead(sms, mms) }
        ids.forEach { db.conversations().refresh(it); if (db.conversations().get(it)?.unreadCount == 0) notifier.cancel(it) }
    }

    suspend fun markUnread(conversationIds: Collection<Long>, categories: Set<Category>) {
        if (categories.isEmpty()) return markUnread(conversationIds)
        for (c in db.conversations().getAll(conversationIds)) {
            if (c.categoryLocked) { markUnread(listOf(c.id)); continue }
            val latest = db.messages().latestIncomingIn(c.id, categories.map { it.name }) ?: continue
            db.messages().markUnread(latest.id)
            latest.providerId?.let { pid -> providerLock.withLock { store.markUnread(latest.kind, pid) } }
            db.conversations().refresh(c.id)
        }
    }

    /** Deletes one section of these chats; the rest of each chat stays where it belongs. Returns how many messages went. */
    suspend fun deleteSection(conversationIds: Collection<Long>, categories: Set<Category>): Int {
        val (locked, open) = db.conversations().getAll(conversationIds).partition { it.categoryLocked }
        if (locked.isNotEmpty()) delete(locked.map { it.id })
        val ids = if (open.isEmpty()) emptyList() else db.messages().idsIn(open.map { it.id }, categories.map { it.name })
        deleteMessages(ids)
        return ids.size + locked.sumOf { it.messageCount }
    }

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

    /** Deletes messages here and in the system store, in batches so thousands go quickly. */
    suspend fun deleteMessages(ids: Collection<Long>, onProgress: (Int) -> Unit = {}) {
        val touched = HashSet<Long>()
        var done = 0
        for (chunk in ids.chunked(DELETE_BATCH)) {
            val messages = db.messages().getAll(chunk)
            deleteLocalFiles(chunk)
            providerLock.withLock {
                val sms = ArrayList<Long>()
                messages.forEach { m ->
                    m.providerId?.let { if (m.kind == MessageKind.SMS) sms += it else store.deleteMms(it) }
                    m.extraProviderIds?.split(',')?.mapNotNull { it.toLongOrNull() }?.let(sms::addAll)
                }
                store.deleteSms(sms)
            }
            db.messages().delete(chunk)
            messages.mapTo(touched) { it.conversationId }
            done += chunk.size
            onProgress(done)
        }
        touched.forEach { db.conversations().refresh(it) }
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
            appendLine(context.getString(R.string.transcript_header, c.title))
            appendLine()
            msgs.forEach { m -> appendLine("[${fmt.format(Date(m.date))}] ${if (m.outgoing) context.getString(R.string.me) else senderName(m)}: ${m.body}${if (m.hasAttachments) context.getString(R.string.attachment_tag) else ""}") }
        }
    }

    fun senderName(m: MessageEntity): String {
        if (m.outgoing) return context.getString(R.string.you)
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

private const val DELETE_BATCH = 400

private fun sqlList(categories: Collection<Category>) = categories.joinToString(",") { "'${it.name}'" }

/** How long a received code stays on its chat's row in the inbox, whatever arrives after it. */
const val FRESH_CODE_MS = 24 * 60 * 60 * 1000L

/** Emits "now minus a day", refreshed every few minutes so old codes leave the inbox rows on time. */
fun freshCodeCutoff(): Flow<Long> = kotlinx.coroutines.flow.flow {
    while (true) {
        emit(System.currentTimeMillis() - FRESH_CODE_MS)
        kotlinx.coroutines.delay(10 * 60 * 1000L)
    }
}

/** SQL for one inbox section's rows (see [ConversationRepository.observe]); returns [app.yarn.data.db.SectionRow]s. */
internal fun sectionQuery(categories: Set<Category>): String {
    val names = sqlList(categories)
    val inSection = "((k.categoryLocked = 0 AND m.category IN ($names)) OR (k.categoryLocked = 1 AND k.category IN ($names)))"
    val section = "SELECT m.conversationId AS cid, m.body AS body, MAX(m.date) AS date, m.hasAttachments AS att, " +
        "SUM(CASE WHEN m.read = 0 THEN 1 ELSE 0 END) AS unread FROM messages m JOIN conversations k ON k.id = m.conversationId " +
        "WHERE m.outgoing = 0 AND $inSection GROUP BY m.conversationId"
    val live = "c.blocked = 0 AND c.archived = 0 AND c.spam = 0 AND (c.messageCount > 0 OR (c.draft IS NOT NULL AND c.draft != ''))"
    val columns = "SELECT c.*, COALESCE(s.body, '') AS sectionSnippet, COALESCE(s.date, 0) AS sectionDate, " +
        "COALESCE(s.unread, 0) AS sectionUnread, COALESCE(s.att, 0) AS sectionAttachments FROM conversations c "
    // Personal also keeps chats with nothing received yet (only sent messages or a draft).
    return if (Category.PERSONAL in categories) {
        columns + "LEFT JOIN ($section) s ON s.cid = c.id WHERE $live AND (s.cid IS NOT NULL OR c.category IN ($names)) " +
            "ORDER BY c.pinned DESC, c.pinnedAt DESC, c.lastMessageAt DESC"
    } else {
        columns + "JOIN ($section) s ON s.cid = c.id WHERE $live ORDER BY c.pinned DESC, c.pinnedAt DESC, s.date DESC"
    }
}
