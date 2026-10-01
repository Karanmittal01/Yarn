package app.yarn.ui.inbox

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import app.yarn.data.repo.InboxView
import app.yarn.intelligence.Category
import app.yarn.telephony.DefaultSmsApp
import app.yarn.ui.common.Avatar
import app.yarn.ui.common.CategoryPickerDialog
import app.yarn.ui.common.CategoryUi
import app.yarn.ui.common.ConfirmDialog
import app.yarn.ui.common.Format
import app.yarn.ui.common.YarnLogo
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
    val chips by vm.chips.collectAsStateWithLifecycle()
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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            when {
                selecting -> SelectionTopBar(
                    count = selection.size,
                    items = items.orEmpty().filter { it.conversation.id in selection },
                    view = filter.view,
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
                onBack != null -> TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                else -> SearchHeader(
                    onSearch = onSearch,
                    spamUnread = spamUnread,
                    onNavigate = onNavigate,
                )
            }
        },
        floatingActionButton = {
            if (onNewMessage != null && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = onNewMessage,
                    icon = { Icon(Icons.Outlined.Edit, null) },
                    text = { Text("Start chat") },
                    expanded = listState.firstVisibleItemIndex == 0,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            StatusBanners(status, onSetDefault = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) } })
            if (showChips && chips.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(chips, key = { it.label }) { chip ->
                        GroupChip(chip, selected = chip.filter == filter) { vm.setFilter(chip.filter) }
                    }
                }
            }
            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize())
                list.isEmpty() -> EmptyInbox(filter.view, status.sync.running)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 104.dp),
                ) {
                    items(list, key = { it.conversation.id }) { item ->
                        val id = item.conversation.id
                        SwipeableRow(
                            enabled = !selecting && filter.view != InboxView.BLOCKED,
                            start = settings.swipeStart,
                            end = settings.swipeEnd,
                            archived = item.conversation.archived,
                            onAction = { action ->
                                when (action) {
                                    SwipeAction.ARCHIVE -> vm.archive(setOf(id), archived = !item.conversation.archived)
                                    SwipeAction.DELETE -> confirmDelete = setOf(id)
                                    SwipeAction.READ -> vm.toggleRead(id)
                                    SwipeAction.PIN -> vm.togglePin(id)
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
                                onCopyCode = vm::copyCode,
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
private fun SearchHeader(onSearch: (() -> Unit)?, spamUnread: Int, onNavigate: (InboxDestination) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 4.dp)) {
                Box(Modifier.padding(start = 6.dp)) { YarnLogo(size = 30.dp) }
                Row(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(enabled = onSearch != null) { onSearch?.invoke() }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "Search messages" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Search messages", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(text = { Text("Starred") }, leadingIcon = { Icon(Icons.Outlined.Star, null) }, onClick = { menu = false; onNavigate(InboxDestination.Starred) })
                        DropdownMenuItem(text = { Text("Archived") }, leadingIcon = { Icon(Icons.Outlined.Inventory2, null) }, onClick = { menu = false; onNavigate(InboxDestination.Archived) })
                        DropdownMenuItem(
                            text = { Text(if (spamUnread > 0) "Spam & blocked · $spamUnread" else "Spam & blocked") },
                            leadingIcon = { Icon(Icons.Outlined.ErrorOutline, null) },
                            onClick = { menu = false; onNavigate(InboxDestination.Spam) },
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { menu = false; onNavigate(InboxDestination.Settings) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChip(chip: InboxChip, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "chipBg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "chipFg")
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(chip.label, style = MaterialTheme.typography.labelLarge, color = fg)
            if (chip.unread > 0) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text(if (chip.unread > 99) "99+" else chip.unread.toString(), style = MaterialTheme.typography.labelSmall, color = if (selected) fg else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SwipeableRow(
    enabled: Boolean,
    start: SwipeAction,
    end: SwipeAction,
    archived: Boolean,
    onAction: (SwipeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled || (start == SwipeAction.NONE && end == SwipeAction.NONE)) { content(); return }
    // The swipe state is remembered across recompositions, so always call the *latest* handler.
    val latestAction by rememberUpdatedState(onAction)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> latestAction(start)
                SwipeToDismissBoxValue.EndToStart -> latestAction(end)
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
            val fromStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = if (fromStart) start else end
            val (icon, color) = swipeVisual(action, archived)
            val iconScale by animateFloatAsState(if (state.targetValue != SwipeToDismissBoxValue.Settled) 1.15f else 0.9f, label = "swipeIcon")
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(color)
                    .padding(horizontal = 28.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.scale(iconScale))
            }
        },
    ) { content() }
}

@Composable
private fun swipeVisual(a: SwipeAction, archived: Boolean): Pair<ImageVector, Color> = when (a) {
    SwipeAction.ARCHIVE -> (if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive) to Color(0xFF1E9E6A)
    SwipeAction.DELETE -> Icons.Outlined.Delete to Color(0xFFD64545)
    SwipeAction.READ -> Icons.Outlined.MarkEmailUnread to MaterialTheme.colorScheme.primary
    SwipeAction.PIN -> Icons.Outlined.PushPin to Color(0xFFE08A1E)
    SwipeAction.NONE -> Icons.Outlined.MoreVert to Color.Transparent
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
    item: InboxItem,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyCode: (String) -> Unit = {},
) {
    val c = item.conversation
    val context = LocalContext.current
    val unread = c.unreadCount > 0
    val bg by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            highlighted -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surface
        },
        label = "rowBg",
    )
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
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = a11y; stateDescription = if (selected) "Selected" else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Avatar(
                    c.title, item.photoUri, size = 52.dp, isGroup = c.isGroup,
                    icon = if (item.isBusiness) item.group?.let(CategoryUi::groupIcon) ?: CategoryUi.default else null,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (c.pinned) Icon(Icons.Filled.PushPin, null, Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    if (c.muted) Icon(Icons.Outlined.NotificationsOff, null, Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    time, style = MaterialTheme.typography.labelMedium,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    c.hasFailed -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.padding(end = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.error)
                    !c.draft.isNullOrBlank() -> Icon(Icons.Outlined.Drafts, null, Modifier.padding(end = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    c.snippetFromMe && c.snippetStatus in MessageStatus.OUTGOING_PENDING -> Icon(Icons.Outlined.Schedule, null, Modifier.padding(end = 4.dp).size(16.dp))
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (c.unreadCount > 99) "99+" else c.unreadCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            item.otpCode?.let { code ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clip(CircleShape).clickable { onCopyCode(code) }.semantics { contentDescription = "Copy code $code" },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(code, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
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
        title = { Text("$count", style = MaterialTheme.typography.titleLarge, maxLines = 1) },
        navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Clear selection") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        actions = {
            if (view == InboxView.BLOCKED) {
                TextButton(onClick = { vm.unblock() }) { Text("Unblock") }
                return@TopAppBar
            }
            IconButton(onClick = { if (anyUnread) vm.markRead() else vm.markUnread() }) {
                Icon(if (anyUnread) Icons.Outlined.Drafts else Icons.Outlined.MarkEmailUnread, if (anyUnread) "Mark as read" else "Mark as unread")
            }
            val archived = view == InboxView.ARCHIVED
            IconButton(onClick = { vm.archive(archived = !archived) }) {
                Icon(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (archived) "Unarchive" else "Archive")
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                    DropdownMenuItem(text = { Text("Select all") }, onClick = { menu = false; vm.selectAll() })
                    DropdownMenuItem(text = { Text(if (allPinned) "Unpin" else "Pin") }, onClick = { menu = false; vm.pin(!allPinned) })
                    DropdownMenuItem(text = { Text(if (allStarred) "Unstar" else "Star") }, onClick = { menu = false; vm.star(!allStarred) })
                    DropdownMenuItem(text = { Text(if (allMuted) "Unmute" else "Mute") }, onClick = { menu = false; vm.mute(!allMuted) })
                    DropdownMenuItem(text = { Text("Move to…") }, onClick = { menu = false; onCategory() })
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    if (view == InboxView.SPAM) {
                        DropdownMenuItem(text = { Text("Not spam") }, onClick = { menu = false; vm.spam(false) })
                    } else {
                        DropdownMenuItem(text = { Text("Report spam") }, onClick = { menu = false; vm.spam(true) })
                    }
                    DropdownMenuItem(text = { Text("Block") }, onClick = { menu = false; vm.block() })
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
            text = "Set Yarn as your default SMS app to send and receive messages.",
            action = "Set default", onAction = onSetDefault, emphasis = true,
        )
    }
    AnimatedVisibility(status.waiting > 0 || (status.airplaneMode && status.isDefault)) {
        Banner(
            icon = Icons.Outlined.SignalCellularConnectedNoInternet0Bar,
            text = when {
                status.waiting > 0 && status.airplaneMode -> "Airplane mode is on · ${status.waiting} waiting to send"
                status.waiting > 0 -> "${status.waiting} waiting for signal · will send automatically"
                else -> "Airplane mode is on · messages will queue"
            },
        )
    }
    AnimatedVisibility(status.failed > 0) {
        Banner(icon = Icons.Outlined.ErrorOutline, text = "${status.failed} message(s) not sent · open the chat to retry")
    }
    AnimatedVisibility(status.sync.running) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text("Organising your messages… ${status.sync.imported}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, action: String? = null, onAction: (() -> Unit)? = null, emphasis: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (emphasis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun EmptyInbox(view: InboxView, importing: Boolean) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (view == InboxView.ALL) YarnLogo(size = 72.dp) else Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    importing -> "Bringing in your messages…"
                    view == InboxView.ARCHIVED -> "Nothing archived"
                    view == InboxView.SPAM -> "No spam"
                    view == InboxView.BLOCKED -> "No blocked conversations"
                    view == InboxView.ALL -> "No conversations yet"
                    else -> "All caught up"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (view) {
                    InboxView.SPAM -> "Suspected spam and scams land here — never deleted silently."
                    InboxView.ALL -> "Tap Start chat to send your first message."
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
