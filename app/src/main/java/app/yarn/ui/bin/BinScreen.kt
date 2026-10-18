package app.yarn.ui.bin

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yarn.AppContainer
import app.yarn.R
import app.yarn.data.db.BinEntity
import app.yarn.data.db.MessageKind
import app.yarn.data.prefs.AppSettings
import app.yarn.data.prefs.BIN_NEVER_EMPTY
import app.yarn.data.prefs.BIN_OFF
import app.yarn.ui.common.Avatar
import app.yarn.ui.common.ConfirmDialog
import app.yarn.ui.common.Format
import app.yarn.ui.common.YarnMenu
import app.yarn.ui.common.YarnMenuItem
import app.yarn.ui.settings.settingsPageColor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BinViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<BinEntity>?> = c.bin.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    fun toggle(id: Long) { _selection.value = _selection.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { _selection.value = emptySet() }

    /** App scope, so leaving the screen can't stop a restore half-way. */
    fun restore(ids: Collection<Long>) {
        clearSelection()
        c.scope.launch {
            val n = c.conversations.restoreFromBin(ids)
            _events.tryEmit(c.app.resources.getQuantityString(R.plurals.bin_restored_n, n, n))
        }
    }

    fun restoreAll() = restore(items.value.orEmpty().map { it.id })

    fun deleteForever(ids: Collection<Long>) {
        clearSelection()
        c.scope.launch { c.bin.deleteForever(ids) }
    }

    fun empty() {
        clearSelection()
        c.scope.launch { c.bin.empty() }
    }
}

@Composable
fun BinScreen(vm: BinViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = selection.isNotEmpty()) { vm.clearSelection() }
    BinContent(
        items = items, binDays = settings.binDays, selection = selection, snackbar = snackbar,
        onToggle = vm::toggle, onRestore = vm::restore, onDeleteForever = vm::deleteForever,
        onRestoreAll = vm::restoreAll, onEmpty = vm::empty, onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinContent(
    items: List<BinEntity>?,
    binDays: Int,
    selection: Set<Long>,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onToggle: (Long) -> Unit,
    onRestore: (Collection<Long>) -> Unit,
    onDeleteForever: (Collection<Long>) -> Unit,
    onRestoreAll: () -> Unit,
    onEmpty: () -> Unit,
    onBack: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val page = settingsPageColor()
    var menu by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmForever by remember { mutableStateOf<Set<Long>?>(null) }
    val list = items.orEmpty()
    Scaffold(
        containerColor = page,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (selection.isEmpty()) stringResource(R.string.recycle_bin) else "${selection.size}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page),
                actions = {
                    if (list.isNotEmpty() && selection.isEmpty()) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
                            YarnMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                YarnMenuItem(stringResource(R.string.restore_all), Icons.Outlined.RestoreFromTrash, { menu = false; onRestoreAll() })
                                YarnMenuItem(stringResource(R.string.empty_bin), Icons.Outlined.DeleteForever, { menu = false; confirmEmpty = true })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selection.isNotEmpty()) {
                Surface(color = page) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = { confirmForever = selection }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp)) {
                            Icon(Icons.Outlined.DeleteForever, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.delete_forever))
                        }
                        Button(onClick = { onRestore(selection) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp)) {
                            Icon(Icons.Outlined.RestoreFromTrash, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.restore))
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    when {
                        binDays == BIN_OFF -> stringResource(R.string.bin_off_note)
                        binDays == BIN_NEVER_EMPTY -> stringResource(R.string.bin_keeps_forever)
                        else -> pluralStringResource(R.plurals.bin_keeps_days, binDays, binDays)
                    } + " " + stringResource(R.string.bin_mms_note),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            if (items != null && list.isEmpty()) {
                item { EmptyBin() }
            }
            items(list, key = { it.id }) { b ->
                BinRow(b, selected = b.id in selection, now = now) { onToggle(b.id) }
            }
        }
    }
    if (confirmEmpty) {
        ConfirmDialog(
            stringResource(R.string.empty_bin_q), pluralStringResource(R.plurals.empty_bin_text, list.size, list.size), stringResource(R.string.empty_bin),
            onConfirm = onEmpty, onDismiss = { confirmEmpty = false }, destructive = true,
        )
    }
    confirmForever?.let { ids ->
        ConfirmDialog(
            pluralStringResource(R.plurals.delete_forever_q, ids.size, ids.size), stringResource(R.string.delete_forever_text), stringResource(R.string.delete_forever),
            onConfirm = { onDeleteForever(ids) }, onDismiss = { confirmForever = null }, destructive = true,
        )
    }
}

@Composable
private fun EmptyBin() {
    Column(Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.bin_empty_state), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.bin_empty_state_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BinRow(b: BinEntity, selected: Boolean, now: Long, onToggle: () -> Unit) {
    val context = LocalContext.current
    val preview = when {
        b.body.isNotBlank() -> b.body
        b.attachments.isNotEmpty() || b.kind == MessageKind.MMS -> stringResource(R.string.attachment)
        else -> ""
    }
    val deleted = DateUtils.getRelativeTimeSpanString(b.deletedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onToggle, onLongClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Avatar(b.title, null, size = 44.dp, isGroup = b.addresses.size > 1, neutral = b.address.any { it.isLetter() })
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(Format.listTime(context, b.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (b.attachments.isNotEmpty()) Icon(Icons.Outlined.Image, null, Modifier.padding(end = 4.dp).size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (b.outgoing) stringResource(R.string.you_prefix, preview) else preview,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.deleted_when, deleted),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** The line delete dialogs end with: where deleted messages go, and for how long. */
@Composable
fun binNote(binDays: Int): String = when {
    binDays == BIN_OFF -> stringResource(R.string.final_delete_note)
    binDays == BIN_NEVER_EMPTY -> stringResource(R.string.to_bin_forever)
    else -> pluralStringResource(R.plurals.to_bin_note, binDays, binDays)
}
