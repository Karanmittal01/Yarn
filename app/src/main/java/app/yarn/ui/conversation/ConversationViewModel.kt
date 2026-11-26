package app.yarn.ui.conversation

import android.net.Uri
import app.yarn.safeScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
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
import app.yarn.data.repo.InboxGroup
import app.yarn.intelligence.Category
import app.yarn.intelligence.SpamVerdict
import app.yarn.intelligence.SummaryMessage
import app.yarn.messaging.AttachmentDraft
import app.yarn.messaging.MediaTools
import app.yarn.messaging.Reactions
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.yarn.R

data class SharePayload(val text: String = "", val attachments: List<AttachmentDraft> = emptyList())

/** One-shot hand-off of shared/forwarded content into a conversation's composer. */
class ShareHolder {
    @Volatile var pending: SharePayload? = null
    fun take(): SharePayload? = pending.also { pending = null }
}

sealed interface TranslationUi {
    data object Loading : TranslationUi
    /** [from] is the source language code ("en", "hi"…). */
    data class Done(val text: String, val from: String, val online: Boolean = false) : TranslationUi
    /** The language pack isn't downloaded and the phone is on mobile data. */
    data class NeedsDownload(val from: String) : TranslationUi
    data class Error(val reason: TranslationError) : TranslationUi
}

enum class TranslationError { SAME_LANGUAGE, UNKNOWN_LANGUAGE, UNSUPPORTED, OFFLINE, FAILED }

sealed interface SummaryUi {
    data object Hidden : SummaryUi
    data object Loading : SummaryUi
    data class Ready(val summary: AssistantSummary) : SummaryUi
}

sealed interface RewriteUi {
    data object Hidden : RewriteUi
    data object Loading : RewriteUi
    data class Ready(val original: String, val options: List<String>, val engine: Engine) : RewriteUi
    data class Error(@androidx.annotation.StringRes val message: Int) : RewriteUi
}

data class ScamWarning(val verdict: SpamVerdict, val reasons: List<String>)

/**
 * [openedFrom] is the inbox section the chat was opened from: at first only that kind of message is
 * shown (and marked read), until the user asks to see the whole chat.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationViewModel(private val c: AppContainer, val conversationId: Long, openedFrom: InboxGroup? = null) : ViewModel() {
    val conversation: StateFlow<ConversationEntity?> = c.db.conversations().observe(conversationId)
        .stateIn(safeScope, SharingStarted.Eagerly, null)

    /** The chat doesn't exist (any more), e.g. opened from an old notification or shortcut. */
    val gone: StateFlow<Boolean> = c.db.conversations().observe(conversationId).map { it == null }
        .stateIn(safeScope, SharingStarted.Eagerly, false)

    private val _section = MutableStateFlow(openedFrom)

    /** The section being shown, or null for the whole chat. A chat the user moved to a category is always whole. */
    val section: StateFlow<InboxGroup?> = combine(_section, conversation) { s, conv -> s?.takeUnless { conv?.categoryLocked == true } }
        .stateIn(safeScope, SharingStarted.Eagerly, openedFrom)

    fun showWholeChat() {
        _section.value = null
        markReadNow()
    }

    /** Reactions that came as text ("Loved “…”"), shown as emoji on the messages they're about. */
    val reactions: SharedFlow<Reactions.State> = c.db.messages().reactionTexts(conversationId)
        .mapLatest { Reactions.resolve(c.db.messages(), conversationId, it) }
        .flowOn(Dispatchers.IO)
        .shareIn(safeScope, SharingStarted.Eagerly, replay = 1)

    private val pages: Flow<PagingData<MessageWithAttachments>> = section.flatMapLatest { s ->
        Pager(PagingConfig(pageSize = 40, prefetchDistance = 40, enablePlaceholders = true, initialLoadSize = 80)) {
            if (s == null) c.db.messages().paging(conversationId) else c.db.messages().pagingIn(conversationId, s.categories.map { it.name })
        }.flow
    }.cachedIn(viewModelScope)

    /** The chat, without reaction texts that now show as emoji on their message. */
    val messages: Flow<PagingData<MessageWithAttachments>> = combine(pages, reactions.map { it.hidden }.distinctUntilChanged()) { page, hidden ->
        if (hidden.isEmpty()) page else page.filter { it.message.id !in hidden }
    }

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

    /** "Moved to the bin" messages, with the bin ids that Undo brings back. */
    private val _undo = MutableSharedFlow<Pair<String, List<Long>>>(extraBufferCapacity = 4)
    val undo: SharedFlow<Pair<String, List<Long>>> = _undo

    fun restoreFromBin(binIds: List<Long>) { c.scope.launch { c.conversations.restoreFromBin(binIds) } }

    val settings = c.settings.settings.stateIn(safeScope, SharingStarted.Eagerly, app.yarn.data.prefs.AppSettings())

    @Volatile private var visible = false
    private var smartJob: Job? = null

    init {
        // Mark read as soon as the chat opens, before any transition animation finishes.
        markReadNow()
        viewModelScope.launch(app.yarn.CrashLog.handler) {
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
        viewModelScope.launch(app.yarn.CrashLog.handler) {
            text.drop(1).debounce(600).distinctUntilChanged().collect { c.conversations.saveDraft(conversationId, it) }
        }
        // Refresh smart replies, scam warnings and read state whenever the thread changes.
        viewModelScope.launch(app.yarn.CrashLog.handler) {
            // Keyed on the messages, not lastMessageAt: saving the draft while typing bumps that every pause.
            conversation.filterNotNull().map { it.messagesKey() }.distinctUntilChanged().collect { key ->
                if (visible && key.unread > 0) c.conversations.markRead(listOf(conversationId), section.value?.categories.orEmpty())
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
        val categories = section.value?.categories.orEmpty()
        c.scope.launch {
            c.conversations.markRead(listOf(conversationId), categories)
            if (categories.isEmpty()) c.notifier.cancel(conversationId)
        }
    }

    override fun onCleared() {
        markReadNow()
        super.onCleared()
    }

    /** The messages smart replies were last worked out for; the same messages don't run the model again. */
    private var repliesFor: List<Pair<Long, Long>>? = null

    private fun refreshInsights() {
        smartJob?.cancel()
        smartJob = viewModelScope.launch(Dispatchers.Default + app.yarn.CrashLog.handler) {
            // Reactions sent as text ("Loved “…”") aren't something to reply to.
            val recent = c.db.messages().recent(conversationId, 12).filter { app.yarn.intelligence.SmsReactions.parse(it.body) == null }
            val conv = conversation.value ?: return@launch
            val lastIncoming = recent.firstOrNull { !it.outgoing }
            _scam.value = lastIncoming?.let { m ->
                val verdict = runCatching { SpamVerdict.valueOf(m.spamVerdict) }.getOrDefault(SpamVerdict.CLEAN)
                if (verdict == SpamVerdict.SCAM || verdict == SpamVerdict.SPAM || conv.spam) {
                    ScamWarning(verdict, m.spamReasons.lines().filter { it.isNotBlank() })
                } else null
            }
            val category = Category.fromName(conv.category) ?: Category.PERSONAL
            val seen = recent.map { it.id to it.date }
            if (seen == repliesFor) return@launch
            _smartReplies.value = if (conv.spam || conv.blocked) emptyList<String>() to Engine.RULES else c.assistant.smartReplies(recent, category)
            repliesFor = seen
        }
    }

    // ---- composer -----------------------------------------------------------------------------

    fun setSim(subId: Int) {
        _subId.value = subId
        viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.setPreferredSim(conversationId, subId) }
    }

    fun addAttachments(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO + app.yarn.CrashLog.handler) {
            val drafts = uris.mapNotNull { MediaTools.importAttachment(c.app, it) }
            if (drafts.size < uris.size) _events.tryEmit(c.app.getString(R.string.attachments_failed))
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
        viewModelScope.launch(app.yarn.CrashLog.handler) {
            try {
                c.sender.queue(conversationId, body, files, _subId.value, scheduledAt)
                if (scheduledAt != null) _events.tryEmit(c.app.getString(R.string.message_scheduled))
            } catch (e: Exception) {
                text.value = body
                _attachments.value = files
                _events.tryEmit(c.app.getString(R.string.queue_failed, e.message.orEmpty()))
            }
        }
    }

    /**
     * Reacts to [target] with [emoji], sent as a text in the words iPhones use ("Loved “…”"), which
     * Google Messages and Yarn show as an emoji on the message. Your own emoji again takes it back.
     */
    fun react(target: MessageWithAttachments, emoji: String) {
        clearSelection()
        val m = target.message
        val mine = reactions.replayCache.firstOrNull()?.byMessage?.get(m.id)?.firstOrNull { it.from == null }?.emoji
        val media = target.attachments.firstOrNull()?.mimeType.orEmpty().let {
            when {
                it.startsWith("image/") -> app.yarn.intelligence.SmsReactions.Media.IMAGE
                it.startsWith("video/") -> app.yarn.intelligence.SmsReactions.Media.VIDEO
                else -> app.yarn.intelligence.SmsReactions.Media.OTHER
            }
        }
        val body = app.yarn.intelligence.SmsReactions.compose(emoji, m.body.takeIf { it.isNotBlank() }, media, removed = mine == emoji)
        viewModelScope.launch(app.yarn.CrashLog.handler) {
            try {
                c.sender.queue(conversationId, body, emptyList(), _subId.value)
            } catch (e: Exception) {
                _events.tryEmit(c.app.getString(R.string.queue_failed, e.message.orEmpty()))
            }
        }
    }

    /** Your own reaction on a message, if any. */
    fun myReaction(state: Reactions.State, messageId: Long): String? = state.byMessage[messageId]?.firstOrNull { it.from == null }?.emoji

    fun sendQuickReply(reply: String) {
        text.value = reply
        send()
    }

    /** Cancels a delayed/scheduled/failed message and puts its text back in the composer. */
    fun undoSend(messageId: Long) = viewModelScope.launch(app.yarn.CrashLog.handler) {
        val m = c.sender.cancel(messageId) ?: return@launch
        text.value = listOf(m.body, text.value).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun retry(messageId: Long) = viewModelScope.launch(app.yarn.CrashLog.handler) { c.sender.retry(messageId) }
    fun download(messageId: Long) = viewModelScope.launch(app.yarn.CrashLog.handler) { c.incoming.download(messageId) }

    // ---- selection & message actions ----------------------------------------------------------

    fun toggle(id: Long) { _selection.value = _selection.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { _selection.value = emptySet() }
    fun selectAll(ids: List<Long>) { _selection.value = ids.toSet() }

    fun deleteSelected() {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch(app.yarn.CrashLog.handler) {
            val kept = c.conversations.deleteMessages(ids)
            val res = c.app.resources
            if (kept.isEmpty()) _events.tryEmit(res.getQuantityString(R.plurals.n_messages_deleted, ids.size, ids.size))
            else _undo.tryEmit(res.getQuantityString(R.plurals.moved_to_bin_n, ids.size, ids.size) to kept)
        }
    }

    fun starSelected(starred: Boolean) {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.setMessagesStarred(ids, starred) }
    }

    suspend fun selectedMessages(): List<MessageWithAttachments> = c.db.messages().getFull(_selection.value)

    /** Prepares a forward: content goes to the new-conversation screen via the share holder. */
    fun forward(messages: List<MessageWithAttachments>) {
        viewModelScope.launch(Dispatchers.IO + app.yarn.CrashLog.handler) {
            val text = messages.joinToString("\n") { it.message.body }.trim()
            val files = messages.flatMap { it.attachments }.mapNotNull { a -> MediaTools.importAttachment(c.app, Uri.parse(a.uri)) }
            c.shareHolder.pending = SharePayload(text, files)
            clearSelection()
        }
    }

    fun recategorize(messageIds: Collection<Long>, category: Category, always: Boolean) = viewModelScope.launch(app.yarn.CrashLog.handler) {
        c.conversations.recategorizeMessages(messageIds, category, always)
        clearSelection()
        _events.tryEmit(c.app.getString(R.string.will_learn))
    }

    fun markSpam(spam: Boolean) = viewModelScope.launch(app.yarn.CrashLog.handler) {
        c.conversations.setSpam(listOf(conversationId), spam)
        _scam.value = null
        _events.tryEmit(c.app.getString(if (spam) R.string.reported_spam else R.string.marked_not_spam))
    }

    fun block() = viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.block(listOf(conversationId)) }
    fun unblock() = viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.unblock(listOf(conversationId)) }
    fun archive() = viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.setArchived(listOf(conversationId), true) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.delete(listOf(conversationId)); onDone() }
    fun mute(muted: Boolean) = viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.setMuted(listOf(conversationId), muted) }
    fun pin(pinned: Boolean) = viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.setPinned(listOf(conversationId), pinned) }
    fun rename(title: String) = viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.rename(conversationId, title) }
    fun setCategory(category: Category, always: Boolean) = viewModelScope.launch(app.yarn.CrashLog.handler) { c.conversations.setCategory(listOf(conversationId), category, always) }

    // ---- AI ---------------------------------------------------------------------------------

    /** Detected language per message (ML Kit code), "" when it isn't worth offering a translation. */
    private val _languages = MutableStateFlow<Map<Long, String>>(emptyMap())
    val languages: StateFlow<Map<Long, String>> = _languages
    private val detecting = HashSet<Long>()

    /** Identifies a message's language once, so the chat can offer a one-tap translation. */
    fun detectLanguage(m: MessageEntity) {
        if (m.outgoing || !detecting.add(m.id)) return
        viewModelScope.launch(Dispatchers.Default + app.yarn.CrashLog.handler) {
            val lang = if (worthTranslating(m.body)) c.translation.translatableLanguage(m.body, allowOnline = settings.value.onlineTranslation) else null
            _languages.update { it + (m.id to lang.orEmpty()) }
        }
    }

    /** Translates [m] into [target] or, if it's already translated, goes back to the original. */
    fun translate(m: MessageEntity, target: String, allowMobileData: Boolean = false) {
        if (_translations.value[m.id] is TranslationUi.Done) {
            _translations.update { it - m.id }
            return
        }
        _translations.update { it + (m.id to TranslationUi.Loading) }
        viewModelScope.launch(app.yarn.CrashLog.handler) {
            val hint = _languages.value[m.id]?.takeIf { it.isNotEmpty() }
            val ui = when (val r = c.translation.translate(m.body, target, allowMobileData, hint, allowOnline = settings.value.onlineTranslation)) {
                is TranslationService.Result.Success -> TranslationUi.Done(r.text, r.sourceLanguage, r.online)
                is TranslationService.Result.NeedsDownload -> TranslationUi.NeedsDownload(r.sourceLanguage)
                TranslationService.Result.SameLanguage -> TranslationUi.Error(TranslationError.SAME_LANGUAGE)
                TranslationService.Result.UnknownLanguage -> TranslationUi.Error(TranslationError.UNKNOWN_LANGUAGE)
                is TranslationService.Result.Unsupported -> TranslationUi.Error(TranslationError.UNSUPPORTED)
                TranslationService.Result.Offline -> TranslationUi.Error(TranslationError.OFFLINE)
                is TranslationService.Result.Failed -> TranslationUi.Error(TranslationError.FAILED)
            }
            _translations.update { it + (m.id to ui) }
        }
    }

    fun dismissTranslation(id: Long) = _translations.update { it - id }

    fun summarize() {
        _summary.value = SummaryUi.Loading
        viewModelScope.launch(app.yarn.CrashLog.handler) {
            val conv = conversation.value
            val unread = c.db.messages().unread(conversationId)
            val source = if (unread.size >= 5) unread else c.db.messages().recent(conversationId, 60).reversed()
            val msgs = source.map { SummaryMessage(if (it.outgoing) c.app.getString(R.string.you) else senderName(it.address), it.body, it.date, it.outgoing) }
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
        viewModelScope.launch(app.yarn.CrashLog.handler) {
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

    companion object {
        /** A few real words: codes, links and one-word replies aren't worth translating. */
        fun worthTranslating(body: String): Boolean {
            val words = body.split(Regex("\\s+")).count { w -> w.count(Char::isLetter) >= 2 && !w.startsWith("http") }
            return words >= 3 && body.count(Char::isLetter) >= 12
        }
    }
}

/** What changes when a message arrives, is read, sent or deleted, and not when the draft is saved. */
internal data class MessagesKey(val count: Int, val unread: Int, val snippet: String, val fromMe: Boolean, val status: Int)

internal fun app.yarn.data.db.ConversationEntity.messagesKey() = MessagesKey(messageCount, unreadCount, snippet, snippetFromMe, snippetStatus)

