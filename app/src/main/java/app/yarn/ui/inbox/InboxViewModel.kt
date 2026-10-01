package app.yarn.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yarn.AppContainer
import app.yarn.data.db.ConversationEntity
import app.yarn.data.prefs.AppSettings
import app.yarn.data.repo.InboxFilter
import app.yarn.data.repo.InboxView
import app.yarn.data.repo.SyncProgress
import app.yarn.intelligence.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxItem(val conversation: ConversationEntity, val photoUri: String?)

data class InboxChip(val label: String, val filter: InboxFilter, val icon: Category? = null)

data class InboxStatus(
    val isDefault: Boolean = true,
    val airplaneMode: Boolean = false,
    val waiting: Int = 0,
    val failed: Int = 0,
    val sync: SyncProgress = SyncProgress(),
)

data class UndoEvent(val message: String, val undo: (suspend () -> Unit)?)

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(private val c: AppContainer, initial: InboxFilter) : ViewModel() {
    private val _filter = MutableStateFlow(initial)
    val filter: StateFlow<InboxFilter> = _filter

    val items: StateFlow<List<InboxItem>?> = _filter
        .flatMapLatest { c.conversations.observe(it) }
        .combine(c.contacts.version) { list, _ -> list }
        .map { list -> list.map { InboxItem(it, if (it.isGroup) null else c.contacts.lookup(it.addresses.firstOrNull().orEmpty())?.photoUri) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chips: List<InboxChip> = listOf(
        InboxChip("All", InboxFilter()),
        InboxChip("Important", InboxFilter(InboxView.IMPORTANT)),
        InboxChip("Unread", InboxFilter(InboxView.UNREAD)),
        InboxChip("Personal", InboxFilter(InboxView.CATEGORY, setOf(Category.PERSONAL)), Category.PERSONAL),
        InboxChip("OTP", InboxFilter(InboxView.CATEGORY, setOf(Category.OTP)), Category.OTP),
        InboxChip("Transactions", InboxFilter(InboxView.CATEGORY, InboxFilter.TRANSACTIONS - Category.OTP), Category.BANKING),
        InboxChip("Orders", InboxFilter(InboxView.CATEGORY, InboxFilter.ORDERS), Category.DELIVERY),
        InboxChip("Travel", InboxFilter(InboxView.CATEGORY, setOf(Category.TRAVEL)), Category.TRAVEL),
        InboxChip("Work", InboxFilter(InboxView.CATEGORY, setOf(Category.WORK)), Category.WORK),
        InboxChip("Updates", InboxFilter(InboxView.CATEGORY, setOf(Category.UPDATES)), Category.UPDATES),
        InboxChip("Promotions", InboxFilter(InboxView.CATEGORY, setOf(Category.PROMOTIONS)), Category.PROMOTIONS),
    )

    /** Unread count per chip label. */
    val chipCounts: StateFlow<Map<String, Int>> = combine(
        c.db.conversations().unreadByCategory(), c.db.conversations().importantUnread(),
    ) { byCategory, important ->
        val map = byCategory.associate { it.category to it.unread }
        chips.associate { chip ->
            chip.label to when (chip.filter.view) {
                InboxView.CATEGORY -> chip.filter.categories.sumOf { map[it.name] ?: 0 }
                InboxView.IMPORTANT -> important
                InboxView.UNREAD, InboxView.ALL -> map.values.sum()
                else -> 0
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val spamUnread = c.db.conversations().unreadSpam().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val status: StateFlow<InboxStatus> = combine(
        c.isDefaultSmsApp, c.airplaneMode, c.db.messages().waitingForService(), c.db.messages().failedCount(), c.sync.progress,
    ) { def, air, waiting, failed, sync -> InboxStatus(def, air, waiting, failed, sync) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxStatus())

    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

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
        _events.tryEmit(UndoEvent(if (archived) "${it.size} archived" else "${it.size} moved to inbox") { c.conversations.setArchived(it, !archived) })
    }

    fun delete(ids: Set<Long> = selected()) = act(ids) {
        c.conversations.delete(it)
        _events.tryEmit(UndoEvent("${it.size} conversation${if (it.size > 1) "s" else ""} deleted", null))
    }

    fun markRead(ids: Set<Long> = selected()) = act(ids) { c.conversations.markRead(it) }
    fun markUnread(ids: Set<Long> = selected()) = act(ids) { c.conversations.markUnread(it) }

    fun pin(pinned: Boolean, ids: Set<Long> = selected()) = act(ids) { c.conversations.setPinned(it, pinned) }
    fun star(starred: Boolean, ids: Set<Long> = selected()) = act(ids) { c.conversations.setStarred(it, starred) }
    fun mute(muted: Boolean, ids: Set<Long> = selected()) = act(ids) { c.conversations.setMuted(it, muted) }

    fun block(ids: Set<Long> = selected()) = act(ids) {
        c.conversations.block(it)
        _events.tryEmit(UndoEvent("Blocked. New messages from these senders will be dropped.") { c.conversations.unblock(it) })
    }

    fun unblock(ids: Set<Long> = selected()) = act(ids) { c.conversations.unblock(it) }

    fun spam(isSpam: Boolean, ids: Set<Long> = selected()) = act(ids) {
        c.conversations.setSpam(it, isSpam)
        _events.tryEmit(UndoEvent(if (isSpam) "Moved to spam. Yarn will learn from this." else "Moved to inbox. Yarn will trust these senders.", null))
    }

    fun setCategory(category: Category, always: Boolean, ids: Set<Long> = selected()) = act(ids) {
        c.conversations.setCategory(it, category, always)
        _events.tryEmit(UndoEvent("Moved to ${app.yarn.ui.common.CategoryUi.label(category)}. Yarn will learn from this.", null))
    }

    suspend fun transcript(ids: Set<Long>): String = buildString {
        ids.forEach { append(c.conversations.transcript(it)).append("\n\n") }
    }.trim()

    fun runUndo(e: UndoEvent) { e.undo?.let { u -> viewModelScope.launch { u() } } }
}
