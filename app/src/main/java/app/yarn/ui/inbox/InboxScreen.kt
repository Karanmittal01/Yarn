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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.data.db.MessageStatus
import app.yarn.data.prefs.SwipeAction
import app.yarn.data.repo.InboxGroup
import app.yarn.data.repo.InboxView
import app.yarn.intelligence.Category
import app.yarn.telephony.DefaultSmsApp
import app.yarn.ui.common.Avatar
import app.yarn.ui.common.CategoryPickerDialog
import app.yarn.ui.common.CategoryUi
import app.yarn.ui.common.ConfirmDialog
import app.yarn.ui.common.Format
import app.yarn.ui.common.YarnLogo
import app.yarn.ui.common.YarnMenu
import app.yarn.ui.common.YarnMenuDivider
import app.yarn.ui.common.YarnMenuItem
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import app.yarn.R

sealed interface InboxDestination {
    data object Starred : InboxDestination
    data object Archived : InboxDestination
    data object Blocked : InboxDestination
    data object Settings : InboxDestination
    data object Cleanup : InboxDestination
    data object Bin : InboxDestination
    data object Spam : InboxDestination
    /** One settings page, opened straight from the profile page. */
    data class Setting(val page: app.yarn.ui.settings.SettingsPage) : InboxDestination
    /** Your profile and Yarn's settings. */
    data object Profile : InboxDestination
    /** Yarn's trial and subscription. */
    data object Plan : InboxDestination
}

@Composable
fun InboxScreen(
    vm: InboxViewModel,
    title: String,
    selectedConversation: Long?,
    /** Opens a chat; in a section tab, only that section's messages ([InboxGroup]) are shown at first. */
    onOpen: (Long, InboxGroup?) -> Unit,
    onNewMessage: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onNavigate: (InboxDestination) -> Unit,
    onBack: (() -> Unit)? = null,
    showChips: Boolean = true,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val section by vm.section.collectAsStateWithLifecycle()
    val chips by vm.chips.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val spamUnread by vm.spamUnread.collectAsStateWithLifecycle()
    val junk by vm.junk.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()
    val backupState by vm.backup.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val sims by vm.sims.collectAsStateWithLifecycle()
    val crash by vm.crash.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val plan by remember(context) { (context.applicationContext as app.yarn.YarnApplication).container.licence.access }.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.share_conversation)
    var confirmDelete by remember { mutableStateOf<Set<Long>?>(null) }
    var pickCategory by remember { mutableStateOf<Set<Long>?>(null) }
    val selecting = selection.isNotEmpty()
    val listState = rememberLazyListState()

    BackHandler(enabled = selecting) { vm.clearSelection() }

    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            val r = snackbar.showSnackbar(e.message, actionLabel = if (e.undo != null) undoLabel else null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.runUndo(e)
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshPlatformState() }
    // Asked once per app launch; "Not now" keeps the small banner as a way back.
    var defaultPromptDismissed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (onBack == null && !status.isDefault && !defaultPromptDismissed) {
        DefaultAppPrompt(
            onSetDefault = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) }; defaultPromptDismissed = true },
            onDismiss = { defaultPromptDismissed = true },
        )
    }

    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    // Google Messages-style two-tone layout: a soft header band, the list on a rounded sheet.
    val header = inboxHeaderColor()
    val sheet = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = header,
        topBar = {
            Column(Modifier.background(header)) {
                // Entering and leaving selection cross-fades the bar instead of swapping it.
                androidx.compose.animation.AnimatedContent(
                    targetState = selecting, transitionSpec = app.yarn.ui.theme.Motion.crossfade(), label = "inboxTopBar",
                ) { selectingBar ->
                when {
                    selectingBar -> SelectionTopBar(
                        count = selection.size,
                        items = items.orEmpty().filter { it.conversation.id in selection },
                        view = filter.view,
                        vm = vm,
                        onDelete = { confirmDelete = selection },
                        onCategory = { pickCategory = selection },
                        onShare = {
                            val ids = selection
                            vm.clearSelection()
                            scope.launch(app.yarn.CrashLog.handler) {
                                val text = vm.transcript(ids)
                                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), shareTitle)) }
                            }
                        },
                    )
                    onBack != null -> TopAppBar(
                        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                    else -> InboxHeader(title = title, onSearch = onSearch, me = me, collapsed = scrolled, onProfile = { onNavigate(InboxDestination.Profile) })
                }
                }
                if (showChips && chips.size > 1) {
                    // A new tab starts at the top of its list.
                    LaunchedEffect(filter) { listState.scrollToItem(0) }
                    LazyRow(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(chips, key = { it.label }) { chip ->
                            GroupChip(chip, selected = chip.filter == filter) { vm.setFilter(chip.filter) }
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        floatingActionButton = {
            if (onNewMessage != null && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = onNewMessage,
                    icon = { Icon(Icons.Outlined.Edit, null, Modifier.size(22.dp)) },
                    text = { Text(stringResource(R.string.start_chat), style = MaterialTheme.typography.titleMedium) },
                    expanded = !scrolled,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp, pressedElevation = 6.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = items
        Box(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(sheet),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = padding.calculateBottomPadding() + 110.dp),
            ) {
                item(key = "banners", contentType = "banners") {
                    TrialBanner(plan, onOpen = { onNavigate(InboxDestination.Plan) })
                    crash?.let { report ->
                        CrashBanner(
                            onSend = { shareFeedback(context, report); vm.dismissCrash() },
                            onDismiss = vm::dismissCrash,
                        )
                    }
                    StatusBanners(status, onSetDefault = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) } })
                    WorkBanners(job, backupState, importing = status.sync.running)
                    CleanBanners(
                        report = settings.autoCleanReport, junk = if (status.isDefault) junk else 0,
                        onClean = { onNavigate(InboxDestination.Cleanup) },
                        onDismissHint = vm::dismissCleanHint, onDismissReport = vm::dismissCleanReport,
                    )
                }
                when {
                    list == null -> Unit
                    list.isEmpty() -> item(key = "empty") { EmptyInbox(filter.view, status.sync.running) }
                    else -> items(list, key = { it.conversation.id }, contentType = { "row" }) { item ->
                        val id = item.conversation.id
                        Box(Modifier.animateItem(fadeInSpec = app.yarn.ui.theme.Motion.fade(220), placementSpec = app.yarn.ui.theme.Motion.offset, fadeOutSpec = app.yarn.ui.theme.Motion.fade(160))) {
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
                                onClick = { if (selecting) vm.toggle(id) else onOpen(id, section) },
                                onLongClick = { vm.toggle(id) },
                                onCopyCode = vm::copyCode,
                            )
                        }
                        }
                    }
                }
                if (!list.isNullOrEmpty()) {
                    item(key = "count", contentType = "count") { InboxCount(list) }
                }
            }
            // Far down a long list: one tap back to the newest, like Google Messages.
            val far by remember { derivedStateOf { listState.firstVisibleItemIndex > 8 } }
            androidx.compose.animation.AnimatedVisibility(
                visible = far && !selecting,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding() + 22.dp),
            ) {
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = { scope.launch(app.yarn.CrashLog.handler) { listState.animateScrollToItem(0) } },
                    modifier = Modifier.size(52.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                ) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.scroll_to_top)) }
            }
        }
    }

    confirmDelete?.let { ids ->
        ConfirmDialog(
            title = section?.let { pluralStringResource(R.plurals.delete_section_title, ids.size, ids.size, stringResource(it.label)) }
                ?: pluralStringResource(R.plurals.delete_conversations_title, ids.size, ids.size),
            text = (if (section != null) stringResource(R.string.section_delete_lead) + " " else "") + app.yarn.ui.bin.binNote(settings.binDays),
            confirm = stringResource(R.string.delete),
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

/**
 * The inbox's top bar is a search bar: Yarn's logo, "Search messages" and your picture, like Gmail
 * and Google's newer apps. No heading text; the app's name stays on the home screen and in Settings.
 * The bar tucks up a little once the list is scrolled.
 */
@Composable
internal fun InboxHeader(
    title: String,
    onSearch: (() -> Unit)?,
    me: app.yarn.data.contacts.ContactInfo?,
    collapsed: Boolean = false,
    onProfile: () -> Unit,
) {
    val top by androidx.compose.animation.core.animateDpAsState(if (collapsed) 4.dp else 14.dp, app.yarn.ui.theme.Motion.dp, label = "headerTop")
    val dark = app.yarn.ui.theme.LocalDarkTheme.current
    val pill = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else Color.White
    val searchLabel = stringResource(R.string.search_messages)
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = top, bottom = 10.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(pill)
            .then(if (onSearch != null) Modifier.clickable(onClickLabel = searchLabel, onClick = onSearch) else Modifier)
            .semantics { heading(); contentDescription = title }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YarnLogo(size = 24.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            searchLabel,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ProfileButton(me, onProfile, size = 32.dp)
    }
}

/** In the trial's last two days: a reminder, so the end never comes as a surprise. "Later" hides it until Yarn reopens. */
@Composable
internal fun TrialBanner(plan: app.yarn.billing.Access, onOpen: () -> Unit) {
    var later by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (later || plan !is app.yarn.billing.Access.Trial || plan.pending || plan.daysLeft > 2) return
    androidx.compose.material3.Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(pluralStringResource(R.plurals.plan_banner, plan.daysLeft, plan.daysLeft), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(stringResource(R.string.plan_banner_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 2.dp, end = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = { later = true }) { Text(stringResource(R.string.later)) }
                androidx.compose.material3.TextButton(onClick = onOpen) { Text(stringResource(R.string.plan_subscribe)) }
            }
        }
    }
}

/** Shown once after Yarn closed unexpectedly, so the report can be shared and the bug fixed. */
@Composable
private fun CrashBanner(onSend: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.crash_banner), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.crash_banner_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, end = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                androidx.compose.material3.TextButton(onClick = onSend) { Text(stringResource(R.string.send_report)) }
            }
        }
    }
}

/** Opens the share sheet with a feedback note and Yarn's error log (no message content). */
internal fun shareFeedback(context: android.content.Context, log: String) {
    val body = context.getString(R.string.feedback_body) + "\n\n\n---\n" +
        "Yarn ${app.yarn.BuildConfig.VERSION_NAME} (${app.yarn.BuildConfig.VERSION_CODE}) · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" + log
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
        .putExtra(Intent.EXTRA_TEXT, body.take(60_000))
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.help_feedback))) }
}

@Composable
internal fun GroupChip(chip: InboxChip, selected: Boolean, onClick: () -> Unit) {
    val idle = if (app.yarn.ui.theme.LocalDarkTheme.current) Color(0xFF3D3E43) else Color.White
    val label = stringResource(chip.label)
    val selectedDesc = stringResource(R.string.state_selected)
    val notSelectedDesc = stringResource(R.string.state_not_selected)
    val unreadDesc = stringResource(R.string.chip_unread, label, chip.unread)
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else idle, app.yarn.ui.theme.Motion.fade(240), label = "chipBg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, app.yarn.ui.theme.Motion.fade(240), label = "chipFg")
    Row(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) selectedDesc else notSelectedDesc
                if (chip.unread > 0) contentDescription = unreadDesc
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (chip.unread > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                if (chip.unread > 99) "99+" else chip.unread.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) fg.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun SwipeableRow(
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
            // Only paint the action colour while a swipe is in progress, never behind a resting row.
            if (state.dismissDirection == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            val fromStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = if (fromStart) start else end
            val (icon, color) = swipeVisual(action, archived)
            val iconScale by animateFloatAsState(if (state.targetValue != SwipeToDismissBoxValue.Settled) 1.15f else 0.9f, label = "swipeIcon")
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
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
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            highlighted -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surface
        },
        label = "rowBg",
    )
    val time = Format.listTime(context, c.lastMessageAt)
    // Snippets for media-only messages are stored in English; show them in Yarn's language.
    val snippet = when (c.snippet) {
        "Multimedia message" -> stringResource(R.string.mms_title)
        "Attachment" -> stringResource(R.string.attachment)
        else -> c.snippet
    }
    val preview = when {
        !c.draft.isNullOrBlank() -> stringResource(R.string.draft_prefix, c.draft.orEmpty())
        c.snippetFromMe -> stringResource(R.string.you_prefix, snippet)
        else -> snippet
    }
    val unreadText = stringResource(R.string.a11y_unread, c.unreadCount)
    val pinnedText = stringResource(R.string.a11y_pinned)
    val unsentText = stringResource(R.string.a11y_unsent)
    val simText = item.sim?.let { app.yarn.ui.common.simLabel(it) }
    val a11y = buildString {
        append(c.title)
        if (unread) append(unreadText)
        if (c.pinned) append(pinnedText)
        if (c.hasFailed) append(unsentText)
        append(". $preview. $time")
        simText?.let { append(". $it") }
    }
    val selectLabel = stringResource(R.string.select)
    val selectedDesc = stringResource(R.string.state_selected)
    val strong = MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val nameStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.5.sp, lineHeight = 22.sp)
    val previewStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 20.sp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = selectLabel)
            .padding(start = 10.dp, end = 12.dp, top = 11.dp, bottom = 11.dp)
            .semantics(mergeDescendants = true) { contentDescription = a11y; stateDescription = if (selected) selectedDesc else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Avatar(
                    c.title, item.photoUri, size = 46.dp, isGroup = c.isGroup,
                    icon = if (item.isBusiness) item.group?.let(CategoryUi::groupIcon) ?: CategoryUi.default else null,
                    neutral = item.isBusiness,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // Name and time share the first line; the preview gets the full width below.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title,
                        style = nameStyle,
                        color = strong,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (c.pinned) Icon(Icons.Filled.PushPin, null, Modifier.padding(start = 5.dp).size(14.dp), tint = quiet)
                    if (c.muted) Icon(Icons.Outlined.NotificationsOff, null, Modifier.padding(start = 5.dp).size(14.dp), tint = quiet)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    time, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = if (unread) MaterialTheme.colorScheme.primary else quiet,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            // A code goes straight under the name so it can be read or copied without opening the chat.
            item.otpCode?.let { code ->
                CodeChip(code, fresh = System.currentTimeMillis() - item.codeAt < CODE_LIKELY_VALID_MS, onCopy = { onCopyCode(code) })
                Spacer(Modifier.height(5.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                when {
                    c.hasFailed -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.padding(top = 2.dp, end = 5.dp).size(16.dp), tint = MaterialTheme.colorScheme.error)
                    !c.draft.isNullOrBlank() -> Icon(Icons.Outlined.Drafts, null, Modifier.padding(top = 2.dp, end = 5.dp).size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    c.snippetFromMe && c.snippetStatus in MessageStatus.OUTGOING_PENDING -> Icon(Icons.Outlined.Schedule, null, Modifier.padding(top = 2.dp, end = 5.dp).size(16.dp), tint = quiet)
                }
                Text(
                    preview,
                    style = previewStyle,
                    // Two lines, so the start of a long bank or order message is actually readable.
                    maxLines = if (item.otpCode != null) 1 else 2, overflow = TextOverflow.Ellipsis,
                    color = if (unread) strong else quiet,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

/** Most codes expire within half an hour; after that the chip stays but turns quiet. */
private const val CODE_LIKELY_VALID_MS = 30 * 60_000L

@Composable
private fun CodeChip(code: String, fresh: Boolean, onCopy: () -> Unit) {
    val copyDesc = stringResource(R.string.copy_code_n, code)
    val ink = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val fill = if (fresh) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        Modifier
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onCopy)
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = copyDesc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(code, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp), fontWeight = FontWeight.Bold, color = ink, letterSpacing = 2.sp)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp), tint = ink)
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
        navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, stringResource(R.string.clear_selection)) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        actions = {
            if (view == InboxView.BLOCKED) {
                TextButton(onClick = { vm.unblock() }) { Text(stringResource(R.string.unblock)) }
                return@TopAppBar
            }
            IconButton(onClick = { if (anyUnread) vm.markRead() else vm.markUnread() }) {
                Icon(if (anyUnread) Icons.Outlined.Drafts else Icons.Outlined.MarkEmailUnread, if (anyUnread) stringResource(R.string.mark_read) else stringResource(R.string.mark_unread))
            }
            val archived = view == InboxView.ARCHIVED
            IconButton(onClick = { vm.archive(archived = !archived) }) {
                Icon(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (archived) stringResource(R.string.unarchive) else stringResource(R.string.archive))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions)) }
                YarnMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    YarnMenuItem(stringResource(R.string.select_all), Icons.Outlined.SelectAll, { menu = false; vm.selectAll() })
                    YarnMenuItem(if (allPinned) stringResource(R.string.unpin) else stringResource(R.string.pin), Icons.Outlined.PushPin, { menu = false; vm.pin(!allPinned) })
                    YarnMenuItem(if (allStarred) stringResource(R.string.unstar) else stringResource(R.string.star), Icons.Outlined.StarOutline, { menu = false; vm.star(!allStarred) })
                    YarnMenuItem(if (allMuted) stringResource(R.string.unmute) else stringResource(R.string.mute), if (allMuted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, { menu = false; vm.mute(!allMuted) })
                    YarnMenuItem(stringResource(R.string.move_to_ellipsis), Icons.Outlined.DriveFileMove, { menu = false; onCategory() })
                    YarnMenuItem(stringResource(R.string.share_as_text), Icons.Outlined.Share, { menu = false; onShare() })
                    YarnMenuDivider()
                    if (view == InboxView.SPAM) {
                        YarnMenuItem(stringResource(R.string.not_spam), Icons.Outlined.GppGood, { menu = false; vm.spam(false) })
                    } else {
                        YarnMenuItem(stringResource(R.string.report_spam), Icons.Outlined.Report, { menu = false; vm.spam(true) })
                    }
                    YarnMenuItem(stringResource(R.string.block), Icons.Outlined.Block, { menu = false; vm.block() }, destructive = true)
                }
            }
        },
    )
}

@Composable
private fun StatusBanners(status: InboxStatus, onSetDefault: () -> Unit) {
    AnimatedVisibility(!status.isDefault, enter = androidx.compose.animation.fadeIn(app.yarn.ui.theme.Motion.fade(240)) + androidx.compose.animation.expandVertically(app.yarn.ui.theme.Motion.size), exit = androidx.compose.animation.fadeOut(app.yarn.ui.theme.Motion.fade(180)) + androidx.compose.animation.shrinkVertically(app.yarn.ui.theme.Motion.size)) {
        Banner(
            icon = Icons.Outlined.ErrorOutline,
            text = stringResource(R.string.banner_not_default),
            action = stringResource(R.string.set_default), onAction = onSetDefault, emphasis = true,
        )
    }
    AnimatedVisibility(status.waiting > 0 || (status.airplaneMode && status.isDefault), enter = androidx.compose.animation.fadeIn(app.yarn.ui.theme.Motion.fade(240)) + androidx.compose.animation.expandVertically(app.yarn.ui.theme.Motion.size), exit = androidx.compose.animation.fadeOut(app.yarn.ui.theme.Motion.fade(180)) + androidx.compose.animation.shrinkVertically(app.yarn.ui.theme.Motion.size)) {
        Banner(
            icon = Icons.Outlined.SignalCellularConnectedNoInternet0Bar,
            text = when {
                status.waiting > 0 && status.airplaneMode -> stringResource(R.string.airplane_waiting, status.waiting)
                status.waiting > 0 -> stringResource(R.string.waiting_signal, status.waiting)
                else -> stringResource(R.string.airplane_queue)
            },
        )
    }
    AnimatedVisibility(status.sync.running, enter = androidx.compose.animation.fadeIn(app.yarn.ui.theme.Motion.fade(240)) + androidx.compose.animation.expandVertically(app.yarn.ui.theme.Motion.size), exit = androidx.compose.animation.fadeOut(app.yarn.ui.theme.Motion.fade(180)) + androidx.compose.animation.shrinkVertically(app.yarn.ui.theme.Motion.size)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(stringResource(R.string.organising, status.sync.imported), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp).clip(CircleShape))
        }
    }
}

/** Live bars for sorting, re-sorting and Drive backup / restore, so long jobs are never a mystery. */
@Composable
internal fun WorkBanners(job: app.yarn.work.JobProgress?, backup: app.yarn.backup.BackupState, importing: Boolean) {
    AnimatedVisibility(job != null && !importing, enter = androidx.compose.animation.fadeIn(app.yarn.ui.theme.Motion.fade(240)) + androidx.compose.animation.expandVertically(app.yarn.ui.theme.Motion.size), exit = androidx.compose.animation.fadeOut(app.yarn.ui.theme.Motion.fade(180)) + androidx.compose.animation.shrinkVertically(app.yarn.ui.theme.Motion.size)) {
        job?.let { app.yarn.ui.common.JobProgressBar(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
    }
    AnimatedVisibility(backup is app.yarn.backup.BackupState.Running, enter = androidx.compose.animation.fadeIn(app.yarn.ui.theme.Motion.fade(240)) + androidx.compose.animation.expandVertically(app.yarn.ui.theme.Motion.size), exit = androidx.compose.animation.fadeOut(app.yarn.ui.theme.Motion.fade(180)) + androidx.compose.animation.shrinkVertically(app.yarn.ui.theme.Motion.size)) {
        val b = backup as? app.yarn.backup.BackupState.Running ?: return@AnimatedVisibility
        val fmt = java.text.NumberFormat.getIntegerInstance()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row {
                Text(b.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (b.total > 0) Text(
                    if (b.percent) "${b.done}%" else stringResource(R.string.job_count, fmt.format(b.done), fmt.format(b.total)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val bar = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(CircleShape)
            if (b.total > 0) LinearProgressIndicator(progress = { b.done.toFloat() / b.total }, modifier = bar) else LinearProgressIndicator(bar)
        }
    }
}

/** "Yarn cleared 1,240 old codes…" once after setup, and a gentle clean-up suggestion when junk piles up. */
@Composable
internal fun CleanBanners(report: Int, junk: Int, onClean: () -> Unit, onDismissHint: () -> Unit, onDismissReport: () -> Unit) {
    val fmt = java.text.NumberFormat.getIntegerInstance()
    AnimatedVisibility(report > 0, enter = androidx.compose.animation.fadeIn(app.yarn.ui.theme.Motion.fade(240)) + androidx.compose.animation.expandVertically(app.yarn.ui.theme.Motion.size), exit = androidx.compose.animation.fadeOut(app.yarn.ui.theme.Motion.fade(180)) + androidx.compose.animation.shrinkVertically(app.yarn.ui.theme.Motion.size)) {
        Banner(
            icon = Icons.Outlined.CleaningServices,
            text = stringResource(R.string.auto_clean_report, fmt.format(report)),
            action = stringResource(R.string.ok), onAction = onDismissReport,
        )
    }
    AnimatedVisibility(report <= 0 && junk >= CLEAN_HINT_MIN, enter = androidx.compose.animation.fadeIn(app.yarn.ui.theme.Motion.fade(240)) + androidx.compose.animation.expandVertically(app.yarn.ui.theme.Motion.size), exit = androidx.compose.animation.fadeOut(app.yarn.ui.theme.Motion.fade(180)) + androidx.compose.animation.shrinkVertically(app.yarn.ui.theme.Motion.size)) {
        Banner(
            icon = Icons.Outlined.CleaningServices,
            text = stringResource(R.string.clean_hint, fmt.format(junk)),
            action = stringResource(R.string.clean_up), onAction = onClean, onDismiss = onDismissHint,
        )
    }
}

private const val CLEAN_HINT_MIN = 100

@Composable
private fun Banner(
    icon: ImageVector,
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    emphasis: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (emphasis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.dismiss), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyInbox(view: InboxView, importing: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 96.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (view == InboxView.ALL) YarnLogo(size = 56.dp) else Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    importing -> stringResource(R.string.empty_importing)
                    view == InboxView.ARCHIVED -> stringResource(R.string.empty_archived)
                    view == InboxView.SPAM -> stringResource(R.string.empty_spam)
                    view == InboxView.BLOCKED -> stringResource(R.string.empty_blocked)
                    view == InboxView.ALL -> stringResource(R.string.empty_all)
                    else -> stringResource(R.string.all_caught_up)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (view) {
                    InboxView.SPAM -> stringResource(R.string.empty_spam_text)
                    InboxView.ALL -> stringResource(R.string.empty_all_text)
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Quiet total at the very end of the list, e.g. "48 conversations · 2,316 messages". */
@Composable
internal fun InboxCount(list: List<InboxItem>) {
    val conversations = list.size
    val messages = list.sumOf { it.conversation.messageCount }
    val fmt = java.text.NumberFormat.getIntegerInstance()
    Text(
        pluralStringResource(R.plurals.count_conversations, conversations, fmt.format(conversations)) + " · " +
            pluralStringResource(R.plurals.count_messages, messages, fmt.format(messages)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
    )
}

/** Soft band behind the inbox title and chips; the conversation sheet sits on top of it. */
@Composable
fun inboxHeaderColor(): Color =
    if (app.yarn.ui.theme.LocalDarkTheme.current) Color(0xFF2B2C30) else Color(0xFFEDF1F8)
