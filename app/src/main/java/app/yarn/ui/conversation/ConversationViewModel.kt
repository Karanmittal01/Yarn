package app.yarn.ui.conversation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.yarn.AppContainer
import app.yarn.ai.AssistantSummary
import app.yarn.ai.Engine
import app.yarn.ai.RewriteResult
import app.yarn.ai.RewriteStyle
import app.yarn.ai.TranslationService
import app.yarn.data.contacts.ContactInfo
import app.yarn.data.db.ConversationEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.MessageWithAttachments
import app.yarn.intelligence.Category
import app.yarn.intelligence.SpamVerdict
import app.yarn.intelligence.SummaryMessage
import app.yarn.messaging.AttachmentDraft
import app.yarn.messaging.MediaTools
import app.yarn.telephony.PhoneNumbers
import app.yarn.telephony.SimInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SharePayload(val text: String = "", val attachments: List<AttachmentDraft> = emptyList())

/** One-shot hand-off of shared/forwarded content into a conversation's composer. */
class ShareHolder {
    @Volatile var pending: SharePayload? = null
    fun take(): SharePayload? = pending.also { pending = null }
}

sealed interface TranslationUi {
    data object Loading : TranslationUi
    data class Done(val text: String, val from: String) : TranslationUi
    data class Error(val message: String) : TranslationUi
}

sealed interface SummaryUi {
    data object Hidden : SummaryUi
    data object Loading : SummaryUi
    data class Ready(val summary: AssistantSummary) : SummaryUi
}

sealed interface RewriteUi {
    data object Hidden : RewriteUi
    data object Loading : RewriteUi
    data class Ready(val original: String, val options: List<String>, val engine: Engine) : RewriteUi
    data class Error(val message: String) : RewriteUi
}

data class ScamWarning(val verdict: SpamVerdict, val reasons: List<String>)

@OptIn(FlowPreview::class)
class ConversationViewModel(private val c: AppContainer, val conversationId: Long) : ViewModel() {
    val conversation: StateFlow<ConversationEntity?> = c.db.conversations().observe(conversationId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val messages: Flow<PagingData<MessageWithAttachments>> = Pager(
        PagingConfig(pageSize = 40, prefetchDistance = 40, enablePlaceholders = true, initialLoadSize = 80),
    ) { c.db.messages().paging(conversationId) }.flow.cachedIn(viewModelScope)

    val sims: StateFlow<List<SimInfo>> = c.sims.sims
    val isDefault = c.isDefaultSmsApp

    private val _subId = MutableStateFlow(-1)
    val subId: StateFlow<Int> = _subId

    val text = MutableStateFlow("")
    private val _attachments = MutableStateFlow<List<AttachmentDraft>>(emptyList())
    val attachments: StateFlow<List<AttachmentDraft>> = _attachments

    private val _participants = MutableStateFlow<Map<String, ContactInfo?>>(emptyMap())
    val participants: StateFlow<Map<String, ContactInfo?>> = _participants

    private val _smartReplies = MutableStateFlow<Pair<List<String>, Engine>>(emptyList<String>() to Engine.RULES)
    val smartReplies: StateFlow<Pair<List<String>, Engine>> = _smartReplies

    private val _scam = MutableStateFlow<ScamWarning?>(null)
    val scam: StateFlow<ScamWarning?> = _scam

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    private val _translations = MutableStateFlow<Map<Long, TranslationUi>>(emptyMap())
    val translations: StateFlow<Map<Long, TranslationUi>> = _translations

    private val _summary = MutableStateFlow<SummaryUi>(SummaryUi.Hidden)
    val summary: StateFlow<SummaryUi> = _summary

    private val _rewrite = MutableStateFlow<RewriteUi>(RewriteUi.Hidden)
    val rewrite: StateFlow<RewriteUi> = _rewrite

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, app.yarn.data.prefs.AppSettings())

    @Volatile private var visible = false
    private var smartJob: Job? = null

    init {
        // Mark read as soon as the chat opens, before any transition animation finishes.
        markReadNow()
        viewModelScope.launch {
            val conv = conversation.filterNotNull().first()
            _subId.value = c.sims.resolveSubId(conv.preferredSubId)
            val shared = c.shareHolder.take()
            text.value = listOfNotNull(conv.draft?.takeIf { it.isNotBlank() }, shared?.text?.takeIf { it.isNotBlank() }).joinToString(" ")
            shared?.attachments?.let { _attachments.value = it }
            withContext(Dispatchers.IO) {
                _participants.value = conv.addresses.associateWith { c.contacts.lookup(it) }
            }
        }
        // Persist drafts as the user types, so nothing is lost if the app is killed.
        viewModelScope.launch {
            text.drop(1).debounce(600).distinctUntilChanged().collect { c.conversations.saveDraft(conversationId, it) }
        }
        // Refresh smart replies, scam warnings and read state whenever the thread changes.
        viewModelScope.launch {
            conversation.filterNotNull().map { it.lastMessageAt to it.unreadCount }.distinctUntilChanged().collect { (_, unread) ->
                if (visible && unread > 0) c.conversations.markRead(listOf(conversationId))
                refreshInsights()
            }
        }
    }

    fun onVisible(isVisible: Boolean) {
        visible = isVisible
        // Opening a chat (or leaving it) counts as reading it. This runs in the app's scope, not the
        // screen's, so pressing back right away can't cancel it half-way.
        markReadNow()
    }

    private fun markReadNow() {
        c.scope.launch {
            c.conversations.markRead(listOf(conversationId))
            c.notifier.cancel(conversationId)
        }
    }

    override fun onCleared() {
        markReadNow()
        super.onCleared()
    }

    private fun refreshInsights() {
        smartJob?.cancel()
        smartJob = viewModelScope.launch(Dispatchers.Default) {
            val recent = c.db.messages().recent(conversationId, 12)
            val conv = conversation.value ?: return@launch
            val lastIncoming = recent.firstOrNull { !it.outgoing }
            _scam.value = lastIncoming?.let { m ->
                val verdict = runCatching { SpamVerdict.valueOf(m.spamVerdict) }.getOrDefault(SpamVerdict.CLEAN)
                if (verdict == SpamVerdict.SCAM || verdict == SpamVerdict.SPAM || conv.spam) {
                    ScamWarning(verdict, m.spamReasons.lines().filter { it.isNotBlank() })
                } else null
            }
            val category = Category.fromName(conv.category) ?: Category.PERSONAL
            _smartReplies.value = if (conv.spam || conv.blocked) emptyList<String>() to Engine.RULES else c.assistant.smartReplies(recent, category)
        }
    }

    // ---- composer -----------------------------------------------------------------------------

    fun setSim(subId: Int) {
        _subId.value = subId
        viewModelScope.launch { c.conversations.setPreferredSim(conversationId, subId) }
    }

    fun addAttachments(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val drafts = uris.mapNotNull { MediaTools.importAttachment(c.app, it) }
            if (drafts.size < uris.size) _events.tryEmit("Some attachments couldn't be added")
            _attachments.value = _attachments.value + drafts
        }
    }

    fun removeAttachment(a: AttachmentDraft) {
        _attachments.value = _attachments.value - a
        a.uri.path?.let { java.io.File(it).delete() }
    }

    fun send(scheduledAt: Long? = null) {
        val body = text.value.trim()
        val files = _attachments.value
        if (body.isEmpty() && files.isEmpty()) return
        text.value = ""
        _attachments.value = emptyList()
        _smartReplies.value = emptyList<String>() to Engine.RULES
        viewModelScope.launch {
            try {
                c.sender.queue(conversationId, body, files, _subId.value, scheduledAt)
                if (scheduledAt != null) _events.tryEmit("Message scheduled")
            } catch (e: Exception) {
                text.value = body
                _attachments.value = files
                _events.tryEmit("Couldn't queue message: ${e.message}")
            }
        }
    }

    fun sendQuickReply(reply: String) {
        text.value = reply
        send()
    }

    /** Cancels a delayed/scheduled/failed message and puts its text back in the composer. */
    fun undoSend(messageId: Long) = viewModelScope.launch {
        val m = c.sender.cancel(messageId) ?: return@launch
        text.value = listOf(m.body, text.value).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun retry(messageId: Long) = viewModelScope.launch { c.sender.retry(messageId) }
    fun download(messageId: Long) = viewModelScope.launch { c.incoming.download(messageId) }

    // ---- selection & message actions ----------------------------------------------------------

    fun toggle(id: Long) { _selection.value = _selection.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { _selection.value = emptySet() }
    fun selectAll(ids: List<Long>) { _selection.value = ids.toSet() }

    fun deleteSelected() {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { c.conversations.deleteMessages(ids); _events.tryEmit("${ids.size} message(s) deleted") }
    }

    fun starSelected(starred: Boolean) {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { c.conversations.setMessagesStarred(ids, starred) }
    }

    suspend fun selectedMessages(): List<MessageWithAttachments> = c.db.messages().getFull(_selection.value)

    /** Prepares a forward: content goes to the new-conversation screen via the share holder. */
    fun forward(messages: List<MessageWithAttachments>) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = messages.joinToString("\n") { it.message.body }.trim()
            val files = messages.flatMap { it.attachments }.mapNotNull { a -> MediaTools.importAttachment(c.app, Uri.parse(a.uri)) }
            c.shareHolder.pending = SharePayload(text, files)
            clearSelection()
        }
    }

    fun recategorize(messageIds: Collection<Long>, category: Category, always: Boolean) = viewModelScope.launch {
        c.conversations.recategorizeMessages(messageIds, category, always)
        clearSelection()
        _events.tryEmit("Got it — Yarn will learn from this")
    }

    fun markSpam(spam: Boolean) = viewModelScope.launch {
        c.conversations.setSpam(listOf(conversationId), spam)
        _scam.value = null
        _events.tryEmit(if (spam) "Reported as spam" else "Marked as not spam. This sender is now trusted.")
    }

    fun block() = viewModelScope.launch { c.conversations.block(listOf(conversationId)) }
    fun unblock() = viewModelScope.launch { c.conversations.unblock(listOf(conversationId)) }
    fun archive() = viewModelScope.launch { c.conversations.setArchived(listOf(conversationId), true) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.conversations.delete(listOf(conversationId)); onDone() }
    fun mute(muted: Boolean) = viewModelScope.launch { c.conversations.setMuted(listOf(conversationId), muted) }
    fun pin(pinned: Boolean) = viewModelScope.launch { c.conversations.setPinned(listOf(conversationId), pinned) }
    fun rename(title: String) = viewModelScope.launch { c.conversations.rename(conversationId, title) }
    fun setCategory(category: Category, always: Boolean) = viewModelScope.launch { c.conversations.setCategory(listOf(conversationId), category, always) }

    // ---- AI ---------------------------------------------------------------------------------

    fun translate(m: MessageEntity) {
        if (_translations.value[m.id] is TranslationUi.Done) {
            _translations.value = _translations.value - m.id // toggle back to original
            return
        }
        _translations.value = _translations.value + (m.id to TranslationUi.Loading)
        viewModelScope.launch {
            val target = settings.value.translateTarget
            val result = c.translation.translate(m.body, target, allowMobileData = false)
            val ui = when (result) {
                is TranslationService.Result.Success -> TranslationUi.Done(result.text, java.util.Locale.forLanguageTag(result.sourceLanguage).displayLanguage)
                TranslationService.Result.SameLanguage -> TranslationUi.Error("Already in your language")
                TranslationService.Result.UnknownLanguage -> TranslationUi.Error("Couldn't detect the language")
                is TranslationService.Result.Unsupported -> TranslationUi.Error("Language not supported for offline translation")
                is TranslationService.Result.Failed -> TranslationUi.Error("Language pack not downloaded yet. Connect to Wi-Fi and try again.")
            }
            _translations.value = _translations.value + (m.id to ui)
        }
    }

    fun summarize() {
        _summary.value = SummaryUi.Loading
        viewModelScope.launch {
            val conv = conversation.value
            val unread = c.db.messages().unread(conversationId)
            val source = if (unread.size >= 5) unread else c.db.messages().recent(conversationId, 60).reversed()
            val msgs = source.map { SummaryMessage(if (it.outgoing) "You" else senderName(it.address), it.body, it.date, it.outgoing) }
            val result = c.assistant.summarize(msgs)
            _summary.value = SummaryUi.Ready(result)
            if (conv == null) _summary.value = SummaryUi.Hidden
        }
    }

    fun dismissSummary() { _summary.value = SummaryUi.Hidden }

    fun rewrite(style: RewriteStyle) {
        val original = text.value.trim()
        if (original.isEmpty()) return
        _rewrite.value = RewriteUi.Loading
        viewModelScope.launch {
            _rewrite.value = when (val r = c.assistant.rewrite(original, style)) {
                is RewriteResult.Success -> RewriteUi.Ready(original, r.options, r.engine)
                is RewriteResult.Unavailable -> RewriteUi.Error(r.reason)
            }
        }
    }

    fun applyRewrite(option: String) { text.value = option; _rewrite.value = RewriteUi.Hidden }
    fun dismissRewrite() { _rewrite.value = RewriteUi.Hidden }

    fun senderName(address: String): String =
        _participants.value[address]?.name ?: c.contacts.lookup(address)?.name ?: PhoneNumbers.format(address, PhoneNumbers.countryIso(c.app))

    fun isPendingOutgoing(m: MessageEntity) = m.outgoing && m.status in MessageStatus.OUTGOING_PENDING
}
