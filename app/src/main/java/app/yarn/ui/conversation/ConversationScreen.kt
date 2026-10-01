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
    val selection by vm.selection.collectAsStateWithLifecycle()
    val translations by vm.translations.collectAsStateWithLifecycle()
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
                    title = { Text("${selection.size} selected") },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Clear selection") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    actions = {
                        IconButton(onClick = {
                            val ids = (0 until items.itemCount).mapNotNull { items.peek(it)?.message?.id }
                            vm.selectAll(ids)
                        }) { Icon(Icons.Outlined.SelectAll, "Select all") }
                        IconButton(onClick = {
                            scope.launch {
                                val msgs = vm.selectedMessages()
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm.setPrimaryClip(ClipData.newPlainText("messages", msgs.joinToString("\n") { it.message.body }))
                                vm.clearSelection()
                            }
                        }) { Icon(Icons.Outlined.ContentCopy, "Copy text") }
                        IconButton(onClick = { scope.launch { vm.forward(vm.selectedMessages()); onForward() } }) { Icon(Icons.AutoMirrored.Outlined.Forward, "Forward") }
                        IconButton(onClick = { confirmDeleteMessages = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                        var more by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, "More actions") }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(text = { Text("Star") }, leadingIcon = { Icon(Icons.Outlined.Star, null) }, onClick = { more = false; vm.starSelected(true) })
                                DropdownMenuItem(text = { Text("Unstar") }, onClick = { more = false; vm.starSelected(false) })
                                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                    more = false
                                    scope.launch {
                                        val msgs = vm.selectedMessages()
                                        Actions.share(context, msgs.joinToString("\n") { it.message.body }, msgs.flatMap { m -> m.attachments.map { Uri.parse(it.uri) to it.mimeType } })
                                        vm.clearSelection()
                                    }
                                })
                                DropdownMenuItem(text = { Text("Move to category…") }, onClick = { more = false; pickCategory = selection })
                                if (selection.size == 1) {
                                    DropdownMenuItem(text = { Text("Details") }, onClick = {
                                        more = false
                                        scope.launch { details = vm.selectedMessages().firstOrNull(); vm.clearSelection() }
                                    })
                                    DropdownMenuItem(text = { Text("Translate") }, onClick = {
                                        more = false
                                        scope.launch { vm.selectedMessages().firstOrNull()?.let { vm.translate(it.message) }; vm.clearSelection() }
                                    })
                                    DropdownMenuItem(text = { Text("Save attachments") }, onClick = {
                                        more = false
                                        scope.launch {
                                            val atts = vm.selectedMessages().flatMap { it.attachments }
                                            val ok = atts.count { Actions.saveToDownloads(context, Uri.parse(it.uri), it.mimeType, it.fileName) }
                                            Toast.makeText(context, if (atts.isEmpty()) "No attachments" else "Saved $ok to Downloads", Toast.LENGTH_SHORT).show()
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
                    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(conv.title, photo, size = 36.dp, isGroup = conv.isGroup)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                val sub = when {
                                    conv.isGroup -> "${conv.addresses.size} people"
                                    conv.displayName != conv.addresses.firstOrNull() && PhoneNumbers.isDialable(conv.addresses.first()) -> PhoneNumbers.format(conv.addresses.first(), PhoneNumbers.countryIso(context))
                                    else -> Category.fromName(conv.category)?.let { CategoryUi.label(it) }
                                }
                                sub?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    },
                    actions = {
                        if (!conv.isGroup && PhoneNumbers.isDialable(conv.addresses.firstOrNull().orEmpty())) {
                            IconButton(onClick = { Actions.dial(context, conv.addresses.first()) }) { Icon(Icons.Outlined.Call, "Call") }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Summarise") }, onClick = { menu = false; vm.summarize() })
                                DropdownMenuItem(text = { Text(if (conv.pinned) "Unpin" else "Pin") }, onClick = { menu = false; vm.pin(!conv.pinned) })
                                DropdownMenuItem(text = { Text(if (conv.muted) "Unmute" else "Mute notifications") }, onClick = { menu = false; vm.mute(!conv.muted) })
                                DropdownMenuItem(text = { Text("Move to category…") }, onClick = { menu = false; pickCategory = emptySet() })
                                if (conv.isGroup) DropdownMenuItem(text = { Text("Rename group") }, onClick = { menu = false; rename = true })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(if (conv.spam) "Not spam" else "Report spam") }, onClick = { menu = false; vm.markSpam(!conv.spam) })
                                DropdownMenuItem(text = { Text(if (conv.blocked) "Unblock" else "Block") }, onClick = { menu = false; if (conv.blocked) vm.unblock() else vm.block() })
                                DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; vm.archive(); onClosed() })
                                DropdownMenuItem(text = { Text("Delete conversation") }, onClick = { menu = false; confirmDelete = true })
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
                    !isDefault -> ReplyBlocker("Set Yarn as your default SMS app to send messages.", "Set default") {
                        DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) }
                    }
                    conv.blocked -> ReplyBlocker("You blocked this conversation.", "Unblock") { vm.unblock() }
                    !canReply -> ReplyBlocker("This sender can't receive replies.", null, null)
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
                                onTranslate = { vm.translate(it.message) },
                            ),
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
            "Delete this conversation?", "All messages in it will be removed from this phone.", "Delete",
            onConfirm = { vm.delete(onClosed) }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
    if (confirmDeleteMessages) {
        ConfirmDialog(
            "Delete ${selection.size} message(s)?", "They will be removed from this phone.", "Delete",
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
            title = { Text("Group name") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(conv.displayName) }) },
            confirmButton = { TextButton(onClick = { vm.rename(name); rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
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
                Text(if (scam) "Likely scam" else "Suspected spam", fontWeight = FontWeight.SemiBold)
            }
            w.reasons.take(3).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
            if (scam) Text("Don't share codes, passwords or card details, and avoid links in this conversation.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNotSpam) { Text("Not spam") }
                OutlinedButton(onClick = onBlock) { Text("Block") }
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
        title = { Text("Message details") },
        text = {
            Column {
                @Composable fun row(label: String, value: String) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
                row("Type", if (m.kind == 0) "SMS (text message)" else "MMS (multimedia message)")
                row(if (m.outgoing) "To" else "From", if (m.outgoing) m.address.split(',').joinToString { vm.senderName(it) } else "${vm.senderName(m.address)} (${m.address})")
                row(if (m.outgoing) "Sent" else "Received", Format.dateTime(context, m.date))
                if (!m.outgoing && m.dateSent > 0) row("Sent by sender", Format.dateTime(context, m.dateSent))
                statusLabel(m.status, m.errorCode)?.let { row("Status", it + if (m.errorCode != 0) " (code ${m.errorCode})" else "") }
                if (m.subId >= 0) row("SIM", context.container.sims.current().firstOrNull { it.subId == m.subId }?.label ?: "SIM ${m.subId}")
                if (!m.outgoing && category != null) {
                    row("Category", "${CategoryUi.label(category)} · ${sourceLabel(m.categorySource)}")
                    m.categoryReasons.lines().filter { it.isNotBlank() }.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (m.spamReasons.isNotBlank()) {
                    row("Safety", m.spamVerdict.lowercase().replaceFirstChar { it.uppercase() } + " (score ${"%.2f".format(m.spamScore)})")
                    m.spamReasons.lines().filter { it.isNotBlank() }.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (m.priority > 0) row("Priority", "${m.priority}/100" + if (m.important) " · Important" else "")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun sourceLabel(source: String) = when (source) {
    "SENDER_RULE" -> "your rule for this sender"
    "SIMILAR_CORRECTION" -> "learned from your corrections"
    "LEARNED" -> "learned from your corrections"
    "USER" -> "set by you"
    else -> "on-device rules"
}
