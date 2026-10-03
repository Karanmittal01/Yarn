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

    val items: StateFlow<List<InboxItem>?> = _filter
        .flatMapLatest { c.conversations.observe(it) }
        .combine(c.contacts.version) { list, _ -> list }
        .combine(c.sims.sims) { list, sims -> list.map { toItem(it, sims) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun toItem(c0: ConversationEntity, sims: List<SimInfo>): InboxItem {
        val address = c0.addresses.firstOrNull().orEmpty()
        val category = Category.fromName(c0.category)
        val business = !c0.isGroup && SenderNames.isBusiness(address)
        val otp = if (category == Category.OTP && !c0.snippetFromMe && System.currentTimeMillis() - c0.lastMessageAt < OTP_FRESH_MS) {
            c.engine.extract(c0.snippet, c0.lastMessageAt).firstOrNull { it.type == EntityType.OTP }?.value
        } else null
        return InboxItem(
            conversation = c0,
            photoUri = if (c0.isGroup || business) null else c.contacts.lookup(address)?.photoUri,
            isBusiness = business,
            group = InboxGroup.of(category),
            otpCode = otp,
            sim = if (sims.size > 1) sims.firstOrNull { it.subId == c0.lastSubId } else null,
        )
    }

    /** Only groups that actually contain conversations are offered as filters. */
    val chips: StateFlow<List<InboxChip>> = combine(c.db.conversations().totalsByCategory(), _filter) { totals, current ->
        val byName = totals.associateBy { it.category }
        val unreadAll = totals.sumOf { it.unread }
        buildList {
            add(InboxChip(R.string.chip_all, InboxFilter()))
            if (unreadAll > 0 || current.view == InboxView.UNREAD) add(InboxChip(R.string.filter_unread, InboxFilter(InboxView.UNREAD), unread = unreadAll))
            InboxGroup.entries.forEach { g ->
                val total = g.categories.sumOf { byName[it.name]?.total ?: 0 }
                val unread = g.categories.sumOf { byName[it.name]?.unread ?: 0 }
                if (total > 0 || current == g.filter) add(InboxChip(g.label, g.filter, g, unread))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(InboxChip(R.string.chip_all, InboxFilter())))

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

    fun delete(ids: Set<Long> = selected()) = act(ids) {
        c.conversations.delete(it)
        _events.tryEmit(UndoEvent(c.app.resources.getQuantityString(R.plurals.n_conversations_deleted, it.size, it.size), null))
    }

    fun markRead(ids: Set<Long> = selected()) = act(ids) { c.conversations.markRead(it) }
    fun markUnread(ids: Set<Long> = selected()) = act(ids) { c.conversations.markUnread(it) }

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
        private const val OTP_FRESH_MS = 30 * 60_000L
        private const val CLEAN_HINT_SNOOZE_MS = 30 * 86_400_000L
    }
}
