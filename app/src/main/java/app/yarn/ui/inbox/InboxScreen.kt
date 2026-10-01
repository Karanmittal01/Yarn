package app.yarn.ui.inbox

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.data.db.MessageStatus
import app.yarn.data.prefs.SwipeAction
import app.yarn.data.repo.InboxFilter
import app.yarn.data.repo.InboxView
import app.yarn.intelligence.Category
import app.yarn.telephony.DefaultSmsApp
import app.yarn.ui.common.Avatar
import app.yarn.ui.common.CategoryPickerDialog
import app.yarn.ui.common.CategoryUi
import app.yarn.ui.common.ConfirmDialog
import app.yarn.ui.common.Format
import kotlinx.coroutines.launch

sealed interface InboxDestination {
    data object Starred : InboxDestination
    data object Archived : InboxDestination
    data object Spam : InboxDestination
    data object Blocked : InboxDestination
    data object Settings : InboxDestination
}

@Composable
fun InboxScreen(
    vm: InboxViewModel,
    title: String,
    selectedConversation: Long?,
    onOpen: (Long) -> Unit,
    onNewMessage: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onNavigate: (InboxDestination) -> Unit,
    onBack: (() -> Unit)? = null,
    showChips: Boolean = true,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val counts by vm.chipCounts.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val spamUnread by vm.spamUnread.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<Set<Long>?>(null) }
    var pickCategory by remember { mutableStateOf<Set<Long>?>(null) }
    val selecting = selection.isNotEmpty()
    val listState = rememberLazyListState()

    BackHandler(enabled = selecting) { vm.clearSelection() }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            val r = snackbar.showSnackbar(e.message, actionLabel = if (e.undo != null) "Undo" else null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.runUndo(e)
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    items = items.orEmpty().filter { it.conversation.id in selection },
                    view = filter.view,
                    onClose = vm::clearSelection,
                    onSelectAll = vm::selectAll,
                    vm = vm,
                    onDelete = { confirmDelete = selection },
                    onCategory = { pickCategory = selection },
                    onShare = {
                        val ids = selection
                        vm.clearSelection()
                        scope.launch {
                            val text = vm.transcript(ids)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share conversation"))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search messages") }
                        if (onBack == null) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Starred") }, onClick = { menu = false; onNavigate(InboxDestination.Starred) })
                                    DropdownMenuItem(text = { Text("Archived") }, onClick = { menu = false; onNavigate(InboxDestination.Archived) })
                                    DropdownMenuItem(
                                        text = { Text(if (spamUnread > 0) "Spam & blocked ($spamUnread)" else "Spam & blocked") },
                                        onClick = { menu = false; onNavigate(InboxDestination.Spam) },
                                    )
                                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onNavigate(InboxDestination.Settings) })
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        floatingActionButton = {
            if (onNewMessage != null && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = onNewMessage,
                    icon = { Icon(Icons.AutoMirrored.Outlined.Chat, null) },
                    text = { Text("Start chat") },
                    expanded = listState.firstVisibleItemIndex == 0,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            StatusBanners(status, onSetDefault = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) } })
            if (showChips) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.chips, key = { it.label }) { chip ->
                        val selected = chip.filter == filter
                        val count = counts[chip.label] ?: 0
                        FilterChip(
                            selected = selected,
                            onClick = { vm.setFilter(chip.filter) },
                            label = { Text(if (count > 0 && chip.filter.view != InboxView.ALL) "${chip.label} · $count" else chip.label) },
                            leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else chip.icon?.let { c -> { Icon(CategoryUi.icon(c), null, Modifier.size(18.dp)) } },
                            modifier = Modifier.semantics { stateDescription = if (selected) "Selected" else "Not selected" },
                        )
                    }
                }
            }
            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize())
                list.isEmpty() -> EmptyInbox(filter, status.sync.running)
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.conversation.id }) { item ->
                        val id = item.conversation.id
                        SwipeableRow(
                            enabled = !selecting && filter.view != InboxView.BLOCKED,
                            start = settings.swipeStart,
                            end = settings.swipeEnd,
                            onAction = { action ->
                                when (action) {
                                    SwipeAction.ARCHIVE -> vm.archive(setOf(id), archived = !item.conversation.archived)
                                    SwipeAction.DELETE -> confirmDelete = setOf(id)
                                    SwipeAction.READ -> if (item.conversation.unreadCount > 0) vm.markRead(setOf(id)) else vm.markUnread(setOf(id))
                                    SwipeAction.PIN -> vm.pin(!item.conversation.pinned, setOf(id))
                                    SwipeAction.NONE -> Unit
                                }
                            },
                        ) {
                            ConversationRow(
                                item = item,
                                selected = id in selection,
                                highlighted = id == selectedConversation,
                                onClick = { if (selecting) vm.toggle(id) else onOpen(id) },
                                onLongClick = { vm.toggle(id) },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { ids ->
        ConfirmDialog(
            title = "Delete ${ids.size} conversation${if (ids.size > 1) "s" else ""}?",
            text = "Messages are removed from this phone, including the system message store. This can't be undone.",
            confirm = "Delete",
            destructive = true,
            onConfirm = { vm.delete(ids) },
            onDismiss = { confirmDelete = null; vm.clearSelection() },
        )
    }
    pickCategory?.let { ids ->
        CategoryPickerDialog(
            current = items.orEmpty().firstOrNull { it.conversation.id in ids }?.conversation?.category?.let(Category::fromName),
            onPick = { c, always -> vm.setCategory(c, always, ids) },
            onDismiss = { pickCategory = null; vm.clearSelection() },
        )
    }
}

@Composable
private fun SwipeableRow(enabled: Boolean, start: SwipeAction, end: SwipeAction, onAction: (SwipeAction) -> Unit, content: @Composable () -> Unit) {
    if (!enabled || (start == SwipeAction.NONE && end == SwipeAction.NONE)) { content(); return }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onAction(start)
                SwipeToDismissBoxValue.EndToStart -> onAction(end)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // snap back; the list updates from the database
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = start != SwipeAction.NONE,
        enableDismissFromEndToStart = end != SwipeAction.NONE,
        backgroundContent = {
            val action = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) start else end
            val (icon, label) = swipeVisual(action)
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        },
    ) { content() }
}

private fun swipeVisual(a: SwipeAction): Pair<ImageVector, String> = when (a) {
    SwipeAction.ARCHIVE -> Icons.Outlined.Archive to "Archive"
    SwipeAction.DELETE -> Icons.Outlined.Delete to "Delete"
    SwipeAction.READ -> Icons.Filled.MarkChatRead to "Read / unread"
    SwipeAction.PIN -> Icons.Outlined.PushPin to "Pin"
    SwipeAction.NONE -> Icons.Outlined.MoreVert to ""
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(item: InboxItem, selected: Boolean, highlighted: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = item.conversation
    val context = LocalContext.current
    val unread = c.unreadCount > 0
    val category = Category.fromName(c.category)
    val bg = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        highlighted -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surface
    }
    val time = Format.listTime(context, c.lastMessageAt)
    val preview = when {
        !c.draft.isNullOrBlank() -> "Draft: ${c.draft}"
        c.snippetFromMe -> "You: ${c.snippet}"
        else -> c.snippet
    }
    val a11y = buildString {
        append(c.title)
        if (unread) append(", ${c.unreadCount} unread")
        if (c.pinned) append(", pinned")
        if (c.hasFailed) append(", has unsent messages")
        append(". $preview. $time")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = a11y; stateDescription = if (selected) "Selected" else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (selected) {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Avatar(c.title, item.photoUri, isGroup = c.isGroup)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f, fill = false),
                )
                if (c.pinned) Icon(Icons.Filled.PushPin, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                if (c.starred) Icon(Icons.Outlined.Star, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                if (c.muted) Icon(Icons.Outlined.NotificationsOff, null, Modifier.padding(start = 4.dp).size(14.dp))
                Spacer(Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.labelMedium, color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    c.hasFailed -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.padding(end = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.error)
                    c.snippetFromMe && c.snippetStatus in MessageStatus.OUTGOING_PENDING -> Icon(Icons.Outlined.Schedule, null, Modifier.padding(end = 4.dp).size(16.dp))
                }
                Text(
                    preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Badge(Modifier.padding(start = 8.dp), containerColor = MaterialTheme.colorScheme.primary) { Text(if (c.unreadCount > 99) "99+" else c.unreadCount.toString()) }
                }
            }
            if (category != null && category != Category.PERSONAL && !c.isGroup) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CategoryUi.icon(category), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        CategoryUi.label(category) + if (c.importance >= 65 && unread) " · Important" else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    items: List<InboxItem>,
    view: InboxView,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    vm: InboxViewModel,
    onDelete: () -> Unit,
    onCategory: () -> Unit,
    onShare: () -> Unit,
) {
    val anyUnread = items.any { it.conversation.unreadCount > 0 }
    val allPinned = items.isNotEmpty() && items.all { it.conversation.pinned }
    val allStarred = items.isNotEmpty() && items.all { it.conversation.starred }
    val allMuted = items.isNotEmpty() && items.all { it.conversation.muted }
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Clear selection") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, "Select all") }
            if (view == InboxView.BLOCKED) {
                TextButton(onClick = { vm.unblock() }) { Text("Unblock") }
                return@TopAppBar
            }
            IconButton(onClick = { if (anyUnread) vm.markRead() else vm.markUnread() }) {
                Icon(if (anyUnread) Icons.Filled.MarkChatRead else Icons.Filled.MarkChatUnread, if (anyUnread) "Mark as read" else "Mark as unread")
            }
            val archived = view == InboxView.ARCHIVED
            IconButton(onClick = { vm.archive(archived = !archived) }) {
                Icon(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (archived) "Unarchive" else "Archive")
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (allPinned) "Unpin" else "Pin") }, onClick = { menu = false; vm.pin(!allPinned) })
                    DropdownMenuItem(text = { Text(if (allStarred) "Unstar" else "Star") }, onClick = { menu = false; vm.star(!allStarred) })
                    DropdownMenuItem(text = { Text(if (allMuted) "Unmute" else "Mute notifications") }, onClick = { menu = false; vm.mute(!allMuted) })
                    DropdownMenuItem(text = { Text("Move to category…") }, onClick = { menu = false; onCategory() })
                    if (view == InboxView.SPAM) {
                        DropdownMenuItem(text = { Text("Not spam") }, onClick = { menu = false; vm.spam(false) })
                    } else {
                        DropdownMenuItem(text = { Text("Report spam") }, onClick = { menu = false; vm.spam(true) })
                    }
                    DropdownMenuItem(text = { Text("Block") }, onClick = { menu = false; vm.block() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Share as text") }, onClick = { menu = false; onShare() })
                }
            }
        },
    )
}

@Composable
private fun StatusBanners(status: InboxStatus, onSetDefault: () -> Unit) {
    AnimatedVisibility(!status.isDefault) {
        Banner(
            icon = Icons.Outlined.ErrorOutline,
            text = "Yarn isn't your default SMS app, so it can't send or receive messages yet.",
            action = "Set default", onAction = onSetDefault,
        )
    }
    AnimatedVisibility(status.waiting > 0 || (status.airplaneMode && status.isDefault)) {
        Banner(
            icon = Icons.Outlined.SignalCellularConnectedNoInternet0Bar,
            text = when {
                status.waiting > 0 && status.airplaneMode -> "Airplane mode is on. ${status.waiting} message(s) will send when you're back on the network."
                status.waiting > 0 -> "${status.waiting} message(s) waiting for mobile signal. They'll send automatically."
                else -> "Airplane mode is on. New messages will be queued and sent later."
            },
        )
    }
    AnimatedVisibility(status.failed > 0) {
        Banner(icon = Icons.Outlined.ErrorOutline, text = "${status.failed} message(s) failed to send. Open the conversation to retry.")
    }
    AnimatedVisibility(status.sync.running) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Importing and organising your messages… ${status.sync.imported}", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyInbox(filter: InboxFilter, importing: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    importing -> "Importing your messages…"
                    filter.view == InboxView.ARCHIVED -> "No archived conversations"
                    filter.view == InboxView.SPAM -> "No spam. Suspected spam and scams land here, never silently deleted."
                    filter.view == InboxView.BLOCKED -> "No blocked conversations"
                    filter.view == InboxView.STARRED -> "No starred conversations"
                    filter.view == InboxView.ALL -> "No conversations yet"
                    else -> "Nothing here right now"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
