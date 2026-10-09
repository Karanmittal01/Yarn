package app.yarn.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yarn.AppContainer
import app.yarn.data.db.ConversationEntity
import app.yarn.data.prefs.AppSettings
import app.yarn.data.repo.Cleaner
import app.yarn.data.repo.InboxFilter
import app.yarn.data.repo.JunkKind
import app.yarn.data.repo.InboxGroup
import app.yarn.data.repo.InboxView
import app.yarn.data.repo.SyncProgress
import app.yarn.intelligence.Category
import app.yarn.intelligence.EntityType
import app.yarn.intelligence.SenderNames
import app.yarn.telephony.SimInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.yarn.R

data class InboxItem(
    val conversation: ConversationEntity,
    val photoUri: String?,
    /** Business sender (bank, shop, service) rather than a person. */
    val isBusiness: Boolean,
    val group: InboxGroup?,
    /** A fresh one-time code from the latest message, offered as a one-tap copy on the row. */
    val otpCode: String?,
    /** SIM of the latest message, set only on phones with more than one active SIM. */
    val sim: SimInfo? = null,
    /** When [otpCode] arrived; older codes are shown quieter since they have probably expired. */
    val codeAt: Long = 0,
)

data class InboxChip(@androidx.annotation.StringRes val label: Int, val filter: InboxFilter, val group: InboxGroup? = null, val unread: Int = 0)

data class InboxStatus(
    val isDefault: Boolean = true,
    val airplaneMode: Boolean = false,
    val waiting: Int = 0,
    val failed: Int = 0,
    val sync: SyncProgress = SyncProgress(),
)

data class UndoEvent(val message: String, val undo: (suspend () -> Unit)?)

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(private val c: AppContainer, private val initial: InboxFilter) : ViewModel() {
    private val _filter = MutableStateFlow(initial)
    val filter: StateFlow<InboxFilter> = _filter

    /** The newest code per chat from the last day, so it can be copied straight from the list. */
    private val freshCodes = app.yarn.data.repo.freshCodeCutoff()
        .flatMapLatest { c.db.conversations().freshCodes(it) }
        .map { list ->
            list.mapNotNull { f -> codeIn(f.body, f.date)?.let { f.conversationId to (it to f.date) } }.toMap()
        }

    /** The section (tab) being shown, if it lists only part of each chat. Personal shows chats whole. */
    val section: StateFlow<InboxGroup?> = _filter.map { sectionOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sectionOf(initial))

    val items: StateFlow<List<InboxItem>?> = _filter
        .flatMapLatest { f -> c.conversations.observe(f).map { sectionOf(f) to it } }
        .combine(c.contacts.version) { list, _ -> list }
        .combine(freshCodes) { list, codes -> list to codes }
        .combine(c.sims.sims) { (pair, codes), sims ->
            val (section, list) = pair
            list.map { toItem(it, sims, section, codes[it.id]) }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun codeIn(body: String, date: Long): String? =
        c.engine.extract(body, date).firstOrNull { it.type == EntityType.OTP }?.value

    private fun toItem(c0: ConversationEntity, sims: List<SimInfo>, section: InboxGroup?, fresh: Pair<String, Long>?): InboxItem {
        val address = c0.addresses.firstOrNull().orEmpty()
        val category = Category.fromName(c0.category)
        val business = !c0.isGroup && SenderNames.isBusiness(address)
        val code = when (section) {
            // The OTP tab's rows are codes, so every one shows its code (greyed once it's old).
            InboxGroup.OTP -> codeIn(c0.snippet, c0.lastMessageAt)?.let { it to c0.lastMessageAt }
            // Other sections show only their own kind of message.
            null -> fresh ?: if (category == Category.OTP && !c0.snippetFromMe &&
                System.currentTimeMillis() - c0.lastMessageAt < app.yarn.data.repo.FRESH_CODE_MS
            ) codeIn(c0.snippet, c0.lastMessageAt)?.let { it to c0.lastMessageAt } else null
            else -> null
        }
        return InboxItem(
            conversation = c0,
            photoUri = if (c0.isGroup || business) null else c.contacts.lookup(address)?.photoUri,
            isBusiness = business,
            group = section ?: InboxGroup.of(category),
            otpCode = code?.first,
            sim = if (sims.size > 1) sims.firstOrNull { it.subId == c0.lastSubId } else null,
            codeAt = code?.second ?: 0,
        )
    }

    /** Only groups that actually contain messages are offered as filters. */
    val chips: StateFlow<List<InboxChip>> = combine(c.db.conversations().totalsByCategory(), c.db.conversations().sectionCategories(), _filter) { totals, sections, current ->
        val unreadAll = totals.sumOf { it.unread }
        // A chat counts once per section it has messages in, and as unread there only if that part is unread.
        val bySection = sections.groupBy { Category.fromName(it.category)?.let(InboxGroup::of) }
        buildList {
            add(InboxChip(R.string.chip_all, InboxFilter()))
            if (unreadAll > 0 || current.view == InboxView.UNREAD) add(InboxChip(R.string.filter_unread, InboxFilter(InboxView.UNREAD), unread = unreadAll))
            InboxGroup.entries.forEach { g ->
                val rows = bySection[g].orEmpty()
                val unread = rows.filter { it.unread > 0 }.map { it.conversationId }.toSet().size
                if (rows.isNotEmpty() || current == g.filter) add(InboxChip(g.label, g.filter, g, unread))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(InboxChip(R.string.chip_all, InboxFilter())))

    /** Sorting / re-sorting progress, and any backup or restore under way. */
    val job = c.progress.job
    val backup = c.backup.state

    val spamUnread = c.db.conversations().unreadSpam().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val status: StateFlow<InboxStatus> = combine(
        c.isDefaultSmsApp, c.airplaneMode, c.db.messages().waitingForService(), c.db.messages().failedCount(), c.sync.progress,
    ) { def, air, waiting, failed, sync -> InboxStatus(def, air, waiting, failed, sync) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxStatus())

    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Old codes and offers that could be cleared; only counted while the inbox would suggest a clean-up. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val junk: StateFlow<Int> = c.settings.settings
        .map { s -> initial.view == InboxView.ALL && s.initialImportDone && !Cleaner.isAutoCleanOn(s) && System.currentTimeMillis() - s.cleanHintDismissedAt > CLEAN_HINT_SNOOZE_MS }
        .distinctUntilChanged()
        .flatMapLatest { eligible ->
            if (!eligible) flowOf(0)
            else c.db.conversations().totalsByCategory().debounce(2_000).map {
                val found = c.cleaner.scan()
                (found[JunkKind.CODES]?.size ?: 0) + (found[JunkKind.OFFERS]?.size ?: 0)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun dismissCleanHint() = viewModelScope.launch { c.settings.update { it.copy(cleanHintDismissedAt = System.currentTimeMillis()) } }
    fun dismissCleanReport() = viewModelScope.launch { c.settings.update { it.copy(autoCleanReport = -1) } }

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    private val _events = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UndoEvent> = _events

    fun setFilter(f: InboxFilter) { _filter.value = f; clearSelection() }

    fun toggle(id: Long) {
        _selection.value = _selection.value.let { if (id in it) it - id else it + id }
    }

    fun selectAll() { _selection.value = items.value.orEmpty().map { it.conversation.id }.toSet() }
    fun clearSelection() { _selection.value = emptySet() }

    private fun selected(): Set<Long> = _selection.value.also { clearSelection() }

    private fun act(ids: Set<Long>, block: suspend (Set<Long>) -> Unit) {
        if (ids.isEmpty()) return
        viewModelScope.launch { block(ids) }
    }

    fun archive(ids: Set<Long> = selected(), archived: Boolean = true) = act(ids) {
        c.conversations.setArchived(it, archived)
        _events.tryEmit(UndoEvent(if (archived) c.app.resources.getQuantityString(R.plurals.n_archived, it.size, it.size) else c.app.resources.getQuantityString(R.plurals.n_moved_inbox, it.size, it.size)) { c.conversations.setArchived(it, !archived) })
    }

    /** In a section, only that section's messages go; the rest of each chat stays in its own tab. */
    fun delete(ids: Set<Long> = selected()) = act(ids) {
        val section = section.value
        if (section == null) {
            c.conversations.delete(it)
            _events.tryEmit(UndoEvent(c.app.resources.getQuantityString(R.plurals.n_conversations_deleted, it.size, it.size), null))
        } else {
            val n = c.conversations.deleteSection(it, section.categories)
            _events.tryEmit(UndoEvent(c.app.resources.getQuantityString(R.plurals.n_messages_deleted, n, n), null))
        }
    }

    fun markRead(ids: Set<Long> = selected()) = act(ids) { c.conversations.markRead(it, section.value?.categories.orEmpty()) }
    fun markUnread(ids: Set<Long> = selected()) = act(ids) { c.conversations.markUnread(it, section.value?.categories.orEmpty()) }

    fun markAllRead() {
        viewModelScope.launch {
            val ids = c.db.messages().unreadConversationIds()
            if (ids.isEmpty()) {
                _events.tryEmit(UndoEvent(c.app.getString(R.string.all_caught_up_msg), null))
                return@launch
            }
            c.conversations.markRead(ids)
            _events.tryEmit(UndoEvent(c.app.resources.getQuantityString(R.plurals.n_marked_read, ids.size, ids.size)) { c.conversations.markUnread(ids) })
        }
    }

    fun pin(pinned: Boolean, ids: Set<Long> = selected()) = act(ids) { c.conversations.setPinned(it, pinned) }
    fun star(starred: Boolean, ids: Set<Long> = selected()) = act(ids) { c.conversations.setStarred(it, starred) }
    fun mute(muted: Boolean, ids: Set<Long> = selected()) = act(ids) { c.conversations.setMuted(it, muted) }

    fun block(ids: Set<Long> = selected()) = act(ids) {
        c.conversations.block(it)
        _events.tryEmit(UndoEvent(c.app.getString(R.string.blocked_msg)) { c.conversations.unblock(it) })
    }

    fun unblock(ids: Set<Long> = selected()) = act(ids) { c.conversations.unblock(it) }

    fun spam(isSpam: Boolean, ids: Set<Long> = selected()) = act(ids) {
        c.conversations.setSpam(it, isSpam)
        _events.tryEmit(UndoEvent(if (isSpam) c.app.getString(R.string.moved_spam_msg) else c.app.getString(R.string.moved_inbox_msg), null))
    }

    fun setCategory(category: Category, always: Boolean, ids: Set<Long> = selected()) = act(ids) {
        c.conversations.setCategory(it, category, always)
        _events.tryEmit(UndoEvent(c.app.getString(R.string.moved_to_group, c.app.getString(InboxGroup.of(category)?.label ?: R.string.category_generic)), null))
    }

    suspend fun transcript(ids: Set<Long>): String = buildString {
        ids.forEach { append(c.conversations.transcript(it)).append("\n\n") }
    }.trim()

    /** Swipe action: decides read/unread from the *current* row state, never a stale snapshot. */
    fun toggleRead(id: Long) {
        val current = items.value?.firstOrNull { it.conversation.id == id }?.conversation ?: return
        if (current.unreadCount > 0) markRead(setOf(id)) else markUnread(setOf(id))
    }

    fun togglePin(id: Long) {
        val current = items.value?.firstOrNull { it.conversation.id == id }?.conversation ?: return
        pin(!current.pinned, setOf(id))
    }

    fun copyCode(code: String) {
        app.yarn.notifications.NotificationActionReceiver.copySensitive(c.app, code)
        _events.tryEmit(UndoEvent(c.app.getString(R.string.code_copied_n, code), null))
    }

    fun refreshPlatformState() = c.refreshPlatformState()

    fun runUndo(e: UndoEvent) { e.undo?.let { u -> viewModelScope.launch { u() } } }

    companion object {
        fun sectionOf(f: InboxFilter): InboxGroup? =
            if (f.view != InboxView.CATEGORY) null
            else InboxGroup.entries.firstOrNull { it.categories == f.categories && it != InboxGroup.PERSONAL }

        private const val CLEAN_HINT_SNOOZE_MS = 30 * 86_400_000L
    }
}
