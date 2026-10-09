package app.yarn.ui.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.MessageWithAttachments
import app.yarn.intelligence.Category
import app.yarn.intelligence.SpamVerdict
import app.yarn.telephony.DefaultSmsApp
import app.yarn.telephony.PhoneNumbers
import app.yarn.ui.common.Avatar
import app.yarn.ui.common.CategoryPickerDialog
import app.yarn.ui.common.CategoryUi
import app.yarn.ui.common.ConfirmDialog
import app.yarn.ui.common.Format
import app.yarn.ui.common.container
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import app.yarn.R

@Composable
fun ConversationScreen(
    vm: ConversationViewModel,
    highlightMessageId: Long?,
    onBack: (() -> Unit)?,
    onForward: () -> Unit,
    onClosed: () -> Unit,
) {
    val context = LocalContext.current
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val items = vm.messages.collectAsLazyPagingItems()
    val text by vm.text.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val sims by vm.sims.collectAsStateWithLifecycle()
    val subId by vm.subId.collectAsStateWithLifecycle()
    val smart by vm.smartReplies.collectAsStateWithLifecycle()
    val scam by vm.scam.collectAsStateWithLifecycle()
    val section by vm.section.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val translations by vm.translations.collectAsStateWithLifecycle()
    val languages by vm.languages.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val rewrite by vm.rewrite.collectAsStateWithLifecycle()
    val isDefault by vm.isDefault.collectAsStateWithLifecycle()
    val participants by vm.participants.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteMessages by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf<Set<Long>?>(null) }
    var details by remember { mutableStateOf<MessageWithAttachments?>(null) }
    var rename by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(highlightMessageId) }
    val selecting = selection.isNotEmpty()
    // Messages in another language than Yarn's (or the one chosen in settings) get a one-tap Translate.
    val appLanguage = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    val translateInto = settings.translateTarget.ifBlank { appLanguage }
    val multiSim = sims.size > 1
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { context.container.refreshPlatformState() }

    // Mark as read while visible; stop when the screen goes to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> vm.onVisible(true)
                Lifecycle.Event.ON_PAUSE -> vm.onVisible(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.onVisible(false) }
    }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = selecting) { vm.clearSelection() }

    // Jump to a search result: find its index among newer messages and scroll there.
    LaunchedEffect(highlightMessageId) {
        val target = highlightMessageId ?: return@LaunchedEffect
        val m = context.container.db.messages().get(target) ?: return@LaunchedEffect
        val index = context.container.db.messages().countNewerThan(vm.conversationId, m.date)
        listState.scrollToItem(index)
        kotlinx.coroutines.delay(2500)
        highlight = null
    }
    // Keep the newest message in view when it arrives, unless the user scrolled up.
    LaunchedEffect(conversation?.lastMessageAt) {
        if (listState.firstVisibleItemIndex <= 1 && highlight == null) listState.animateScrollToItem(0)
    }

    val conv = conversation
    if (conv == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val canReply = conv.addresses.isNotEmpty() && conv.addresses.all { PhoneNumbers.isDialable(it) || PhoneNumbers.isEmail(it) }
    val photo = if (!conv.isGroup) participants[conv.addresses.firstOrNull()]?.photoUri else null

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selection.size}", style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, stringResource(R.string.clear_selection)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    actions = {
                        IconButton(onClick = {
                            val ids = (0 until items.itemCount).mapNotNull { items.peek(it)?.message?.id }
                            vm.selectAll(ids)
                        }) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all)) }
                        IconButton(onClick = {
                            scope.launch {
                                val msgs = vm.selectedMessages()
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm.setPrimaryClip(ClipData.newPlainText("messages", msgs.joinToString("\n") { it.message.body }))
                                vm.clearSelection()
                            }
                        }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy_text)) }
                        IconButton(onClick = { scope.launch { vm.forward(vm.selectedMessages()); onForward() } }) { Icon(Icons.AutoMirrored.Outlined.Forward, stringResource(R.string.forward)) }
                        IconButton(onClick = { confirmDeleteMessages = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                        var more by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions)) }
                            app.yarn.ui.common.YarnMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.star)) }, leadingIcon = { Icon(Icons.Outlined.Star, null) }, onClick = { more = false; vm.starSelected(true) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.unstar)) }, onClick = { more = false; vm.starSelected(false) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                    more = false
                                    scope.launch {
                                        val msgs = vm.selectedMessages()
                                        Actions.share(context, msgs.joinToString("\n") { it.message.body }, msgs.flatMap { m -> m.attachments.map { Uri.parse(it.uri) to it.mimeType } })
                                        vm.clearSelection()
                                    }
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_category)) }, onClick = { more = false; pickCategory = selection })
                                if (selection.size == 1) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.details)) }, onClick = {
                                        more = false
                                        scope.launch { details = vm.selectedMessages().firstOrNull(); vm.clearSelection() }
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.translate)) }, onClick = {
                                        more = false
                                        scope.launch { vm.selectedMessages().firstOrNull()?.let { vm.translate(it.message, translateInto, allowMobileData = false) }; vm.clearSelection() }
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.save_attachments)) }, onClick = {
                                        more = false
                                        scope.launch {
                                            val atts = vm.selectedMessages().flatMap { it.attachments }
                                            val ok = atts.count { Actions.saveToDownloads(context, Uri.parse(it.uri), it.mimeType, it.fileName) }
                                            Toast.makeText(context, if (atts.isEmpty()) context.getString(R.string.no_attachments) else context.getString(R.string.saved_to_downloads, ok), Toast.LENGTH_SHORT).show()
                                            vm.clearSelection()
                                        }
                                    })
                                }
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(conv.title, photo, size = 36.dp, isGroup = conv.isGroup)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                val sub = when {
                                    conv.isGroup -> pluralStringResource(R.plurals.n_people, conv.addresses.size, conv.addresses.size)
                                    conv.displayName != conv.addresses.firstOrNull() && PhoneNumbers.isDialable(conv.addresses.first()) -> PhoneNumbers.format(conv.addresses.first(), PhoneNumbers.countryIso(context))
                                    // Business senders show their short brand name in the list; here, the full sender ID (e.g. AD-SBIOTP-S).
                                    conv.addresses.firstOrNull()?.let { it.any(Char::isLetter) && it != conv.title } == true -> conv.addresses.first().uppercase()
                                    else -> Category.fromName(conv.category)?.let { CategoryUi.label(it) }
                                }
                                sub?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    },
                    actions = {
                        if (!conv.isGroup && PhoneNumbers.isDialable(conv.addresses.firstOrNull().orEmpty())) {
                            IconButton(onClick = { Actions.dial(context, conv.addresses.first()) }) { Icon(Icons.Outlined.Call, stringResource(R.string.call)) }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
                            app.yarn.ui.common.YarnMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.summarise)) }, onClick = { menu = false; vm.summarize() })
                                DropdownMenuItem(text = { Text(if (conv.pinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) }, onClick = { menu = false; vm.pin(!conv.pinned) })
                                DropdownMenuItem(text = { Text(if (conv.muted) stringResource(R.string.unmute) else stringResource(R.string.mute_notifications)) }, onClick = { menu = false; vm.mute(!conv.muted) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_category)) }, onClick = { menu = false; pickCategory = emptySet() })
                                if (conv.isGroup) DropdownMenuItem(text = { Text(stringResource(R.string.rename_group)) }, onClick = { menu = false; rename = true })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(if (conv.spam) stringResource(R.string.not_spam) else stringResource(R.string.report_spam)) }, onClick = { menu = false; vm.markSpam(!conv.spam) })
                                DropdownMenuItem(text = { Text(if (conv.blocked) stringResource(R.string.unblock) else stringResource(R.string.block)) }, onClick = { menu = false; if (conv.blocked) vm.unblock() else vm.block() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.archive)) }, onClick = { menu = false; vm.archive(); onClosed() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete_conversation)) }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Box(Modifier.imePadding()) {
                when {
                    !isDefault -> ReplyBlocker(stringResource(R.string.blocker_not_default), stringResource(R.string.set_default)) {
                        DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) }
                    }
                    conv.blocked -> ReplyBlocker(stringResource(R.string.blocker_blocked), stringResource(R.string.unblock)) { vm.unblock() }
                    !canReply -> ReplyBlocker(stringResource(R.string.blocker_no_reply), null, null)
                    else -> Composer(
                        text = text, onText = { vm.text.value = it },
                        attachments = attachments, onAddAttachments = vm::addAttachments, onRemoveAttachment = vm::removeAttachment,
                        sims = sims, subId = subId, onSim = vm::setSim,
                        smartReplies = if (settings.smartReplies) smart.first else emptyList(), onSmartReply = vm::sendQuickReply,
                        onSend = { vm.send() }, onSchedule = { vm.send(it) }, onRewrite = vm::rewrite,
                        isGroupMms = conv.isGroup && settings.groupMms,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            scam?.let { warning -> ScamBanner(warning, onNotSpam = { vm.markSpam(false) }, onBlock = { vm.block() }) }
            section?.let { SectionBar(it, onShowAll = vm::showWholeChat) }
            LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(count = items.itemCount, key = { index -> items.peek(index)?.message?.id ?: -index.toLong() - 1 }) { index ->
                    val item = items[index]
                    if (item == null) {
                        Spacer(Modifier.height(56.dp))
                        return@items
                    }
                    val older = if (index + 1 < items.itemCount) items.peek(index + 1) else null
                    Column {
                        if (older == null || !Format.sameDay(older.message.date, item.message.date)) {
                            Text(
                                Format.dayHeader(context, item.message.date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        val showSender = conv.isGroup && !item.message.outgoing &&
                            (older == null || older.message.outgoing || older.message.address != item.message.address)
                        if (settings.aiEnabled && !item.message.outgoing) LaunchedEffect(item.message.id) { vm.detectLanguage(item.message) }
                        val lang = languages[item.message.id]
                        MessageBubble(
                            item = item,
                            senderLabel = if (showSender) vm.senderName(item.message.address) else null,
                            selected = item.message.id in selection,
                            highlighted = item.message.id == highlight,
                            translation = translations[item.message.id],
                            showInsights = settings.aiEnabled && !selecting,
                            callbacks = BubbleCallbacks(
                                onClick = { if (selecting) vm.toggle(it.message.id) else if (it.message.status == MessageStatus.FAILED) vm.retry(it.message.id) },
                                onLongClick = { vm.toggle(it.message.id) },
                                onRetry = { vm.retry(it) },
                                onUndo = { vm.undoSend(it) },
                                onDownload = { vm.download(it) },
                                onTranslate = { it, mobile -> vm.translate(it.message, translateInto, mobile) },
                            ),
                            translateTo = translateInto.takeIf { settings.aiEnabled && !lang.isNullOrEmpty() && lang != it },
                            sim = if (multiSim) sims.firstOrNull { it.subId == item.message.subId } else null,
                        )
                    }
                }
            }
        }
    }

    SummarySheet(summary, vm::dismissSummary)
    RewriteSheet(rewrite, vm::applyRewrite, vm::dismissRewrite)

    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_conversation_q), stringResource(R.string.delete_conversation_text), stringResource(R.string.delete),
            onConfirm = { vm.delete(onClosed) }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
    if (confirmDeleteMessages) {
        ConfirmDialog(
            pluralStringResource(R.plurals.delete_messages_q, selection.size, selection.size), stringResource(R.string.delete_messages_text), stringResource(R.string.delete),
            onConfirm = { vm.deleteSelected() }, onDismiss = { confirmDeleteMessages = false }, destructive = true,
        )
    }
    pickCategory?.let { ids ->
        CategoryPickerDialog(
            current = Category.fromName(conv.category),
            onPick = { c, always -> if (ids.isEmpty()) vm.setCategory(c, always) else vm.recategorize(ids, c, always) },
            onDismiss = { pickCategory = null },
        )
    }
    details?.let { MessageDetailsDialog(it, vm, onDismiss = { details = null }) }
    if (rename) {
        var name by remember { mutableStateOf(conv.customTitle.orEmpty()) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.group_name)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(conv.displayName) }) },
            confirmButton = { TextButton(onClick = { vm.rename(name); rename = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ReplyBlocker(text: String, action: String?, onAction: (() -> Unit)?) {
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Shown when the chat was opened from a section tab and only that kind of message is listed. */
@Composable
internal fun SectionBar(section: app.yarn.data.repo.InboxGroup, onShowAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(app.yarn.ui.common.CategoryUi.groupIcon(section), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.section_only, stringResource(section.label)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowAll) { Text(stringResource(R.string.show_all_messages)) }
    }
}

@Composable
private fun ScamBanner(w: ScamWarning, onNotSpam: () -> Unit, onBlock: () -> Unit) {
    val scam = w.verdict == SpamVerdict.SCAM
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (scam) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GppBad, null)
                Spacer(Modifier.width(8.dp))
                Text(if (scam) stringResource(R.string.likely_scam) else stringResource(R.string.suspected_spam), fontWeight = FontWeight.SemiBold)
            }
            w.reasons.take(3).map { app.yarn.ui.common.ReasonText.localize(LocalContext.current, it) }.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
            if (scam) Text(stringResource(R.string.scam_advice), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNotSpam) { Text(stringResource(R.string.not_spam)) }
                OutlinedButton(onClick = onBlock) { Text(stringResource(R.string.block)) }
            }
        }
    }
}

@Composable
private fun MessageDetailsDialog(item: MessageWithAttachments, vm: ConversationViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val m = item.message
    val category = Category.fromName(m.category)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_details)) },
        text = {
            Column {
                @Composable fun row(label: String, value: String) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
                row(stringResource(R.string.d_type), if (m.kind == 0) stringResource(R.string.d_sms) else stringResource(R.string.d_mms))
                row(if (m.outgoing) stringResource(R.string.d_to) else stringResource(R.string.d_from), if (m.outgoing) m.address.split(',').joinToString { vm.senderName(it) } else "${vm.senderName(m.address)} (${m.address})")
                row(if (m.outgoing) stringResource(R.string.d_sent) else stringResource(R.string.d_received), Format.dateTime(context, m.date))
                if (!m.outgoing && m.dateSent > 0) row(stringResource(R.string.d_sent_by_sender), Format.dateTime(context, m.dateSent))
                statusLabel(context, m.status, m.errorCode)?.let { row(stringResource(R.string.d_status), it + if (m.errorCode != 0) " (${stringResource(R.string.d_code, m.errorCode)})" else "") }
                if (m.subId >= 0) row(stringResource(R.string.d_sim), context.container.sims.current().firstOrNull { it.subId == m.subId }?.let { app.yarn.ui.common.simLabel(it) } ?: stringResource(R.string.d_sim_id, m.subId))
                if (!m.outgoing && category != null) {
                    row(stringResource(R.string.d_category), "${CategoryUi.label(category)} · ${stringResource(sourceLabel(m.categorySource))}")
                    app.yarn.ui.common.ReasonText.lines(context, m.categoryReasons).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (m.spamReasons.isNotBlank()) {
                    row(stringResource(R.string.d_safety), stringResource(verdictLabel(m.spamVerdict)) + " (" + stringResource(R.string.d_score, "%.2f".format(m.spamScore)) + ")")
                    app.yarn.ui.common.ReasonText.lines(context, m.spamReasons).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (m.priority > 0) row(stringResource(R.string.d_priority), "${m.priority}/100" + if (m.important) " · " + stringResource(R.string.d_important) else "")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun sourceLabel(source: String) = when (source) {
    "SENDER_RULE" -> R.string.src_sender_rule
    "SIMILAR_CORRECTION", "LEARNED" -> R.string.src_learned
    "USER" -> R.string.src_user
    "LLM" -> R.string.src_model
    else -> R.string.src_rules
}

private fun verdictLabel(verdict: String) = when (verdict) {
    "SCAM" -> R.string.verdict_scam
    "SPAM" -> R.string.verdict_spam
    "PROMOTIONAL" -> R.string.verdict_promotional
    "SUSPICIOUS" -> R.string.verdict_suspicious
    else -> R.string.verdict_clean
}
