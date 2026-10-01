package app.yarn.ui.settings

import kotlinx.coroutines.launch
import app.yarn.ui.common.ConfirmDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import app.yarn.work.GoogleBackupWorker
import app.yarn.backup.ConsentNeededException
import app.yarn.backup.DriveBackup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Block
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.backup.BackupState
import app.yarn.data.db.BlockRuleEntity
import app.yarn.telephony.PhoneNumbers
import app.yarn.ui.common.Format
import app.yarn.work.ReanalyzeWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SimpleScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    val scroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { p ->
        content(Modifier.padding(p).fillMaxSize())
    }
}

@Composable
fun BlockedScreen(vm: SettingsViewModel, onBack: () -> Unit, onBlockedConversations: () -> Unit) {
    val rules by vm.c.db.blocks().observe().collectAsState(initial = emptyList())
    var add by remember { mutableStateOf<String?>(null) }
    SettingsScaffold("Blocked", onBack) {
        item {
            SettingsGroup {
                ClickRow("Block a number", icon = Icons.Outlined.Block, tint = androidx.compose.ui.graphics.Color(0xFFF2453D)) { add = BlockRuleEntity.NUMBER }
                ClickRow("Filter a word", icon = Icons.Outlined.FilterAlt, tint = androidx.compose.ui.graphics.Color(0xFFFF9500)) { add = BlockRuleEntity.KEYWORD }
                ClickRow("Blocked conversations", icon = Icons.Outlined.Forum, tint = androidx.compose.ui.graphics.Color(0xFF8E8E93)) { onBlockedConversations() }
            }
        }
        if (rules.isNotEmpty()) {
            item { SectionHeader("Your list") }
            item {
                SettingsGroup {
                    rules.forEach { r ->
                        SettingsRow(
                            r.value,
                            summary = if (r.type == BlockRuleEntity.NUMBER) "Number" else "Word filter",
                            trailing = {
                                IconButton(onClick = {
                                    vm.launch { if (r.type == BlockRuleEntity.NUMBER) conversations.unblockNumber(r.value) else conversations.removeRule(r.id) }
                                }) { Icon(Icons.Outlined.Close, "Remove ${r.value}", Modifier.size(20.dp)) }
                            },
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Blocked numbers are dropped silently. Messages with a filtered word go to Spam without notifying you.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
        }
    }
    add?.let { type ->
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = null },
            title = { Text(if (type == BlockRuleEntity.NUMBER) "Block a number" else "Filter messages containing") },
            text = {
                OutlinedTextField(
                    value, { value = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (type == BlockRuleEntity.NUMBER) KeyboardType.Phone else KeyboardType.Text),
                    placeholder = { Text(if (type == BlockRuleEntity.NUMBER) "+1 555 0100 or sender ID" else "e.g. lottery") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = value.trim()
                    if (v.isNotEmpty()) vm.launch { if (type == BlockRuleEntity.NUMBER) conversations.blockNumbers(listOf(v)) else conversations.addKeywordFilter(v) }
                    add = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { add = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun BackupScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val c = vm.c
    val s by vm.settings.collectAsStateWithLifecycle()
    val state by c.backup.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var latest by remember { mutableStateOf<DriveBackup?>(null) }
    var checking by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<DriveBackup?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, suspend (String) -> Unit>?>(null) }
    val running = state is BackupState.Running || checking

    /** Runs [action] with a Drive token for [email]; shows Google's one-time "Allow" screen when needed. */
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending
        pending = null
        if (p == null) { checking = false; return@rememberLauncherForActivityResult }
        if (result.resultCode != android.app.Activity.RESULT_OK) { checking = false; error = "Access wasn't allowed."; return@rememberLauncherForActivityResult }
        scope.launch {
            runCatching { p.second(c.drive.token(p.first)) }.onFailure { error = it.message ?: "Google sign-in failed." }
            checking = false
        }
    }

    fun runWithGoogle(email: String, action: suspend (String) -> Unit) {
        error = null
        checking = true
        scope.launch {
            try {
                val token = c.drive.token(email)
                try {
                    action(token)
                } catch (e: java.io.IOException) {
                    // A cached token can expire; refresh once and retry.
                    if (e.message?.contains("expired") != true) throw e
                    c.drive.invalidate(token)
                    action(c.drive.token(email))
                }
            } catch (e: ConsentNeededException) {
                pending = email to action
                consent.launch(e.intent)
                return@launch
            } catch (e: Exception) {
                error = e.message ?: "Couldn't reach Google"
            }
            checking = false
        }
    }

    suspend fun refreshLatest(token: String) { latest = c.drive.latest(token) }

    fun connect(email: String) = runWithGoogle(email) { token ->
        c.settings.update { it.copy(googleAccount = email) }
        GoogleBackupWorker.schedule(context, s.autoBackup)
        refreshLatest(token)
        // Coming from another phone: offer to bring the messages back straight away.
        latest?.let { confirmRestore = it }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == android.app.Activity.RESULT_OK && email != null) connect(email)
    }

    fun withAccount(action: suspend (String) -> Unit) {
        val email = s.googleAccount ?: run { picker.launch(c.drive.accountPickerIntent()); return }
        runWithGoogle(email, action)
    }

    LaunchedEffect(s.googleAccount) {
        val email = s.googleAccount ?: return@LaunchedEffect
        runCatching { refreshLatest(c.drive.token(email)) }
    }

    SettingsScaffold("Backup", onBack) {
        backupItems(
            s, latest, state, checking, error, running,
            onSignIn = { error = null; picker.launch(c.drive.accountPickerIntent()) },
            onBackupNow = {
                withAccount { token ->
                    c.backup.backupToGoogle(c.drive, token, s.backupMedia)?.let { (bytes, _) ->
                        c.settings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupBytes = bytes) }
                    }
                    refreshLatest(token)
                }
            },
            onAutoBackup = { v ->
                vm.update { it.copy(autoBackup = v) }
                GoogleBackupWorker.schedule(context, v)
            },
            onMedia = { v -> vm.update { it.copy(backupMedia = v) } },
            onRestore = {
                withAccount { token ->
                    refreshLatest(token)
                    if (latest == null) error = "No backup found in this Google account." else confirmRestore = latest
                }
            },
            onTurnOff = { confirmOff = true },
        )
    }

    confirmRestore?.let { b ->
        ConfirmDialog(
            "Restore messages?",
            "Found a backup from ${Format.listTime(context, b.modifiedAt)}" +
                (if (b.messageCount > 0) " with ${java.text.NumberFormat.getIntegerInstance().format(b.messageCount)} messages" else "") +
                ". Messages already on this phone are kept; duplicates are skipped.",
            "Restore",
            onConfirm = {
                withAccount { token ->
                    c.backup.restoreFromGoogle(c.drive, token, b)
                    ReanalyzeWorker.enqueue(context)
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
    if (confirmOff) {
        ConfirmDialog(
            "Turn off Google backup?", "Yarn stops backing up. Your existing backup stays in Google Drive so you can restore it later.", "Turn off",
            onConfirm = {
                vm.update { it.copy(googleAccount = null) }
                GoogleBackupWorker.schedule(context, false)
                latest = null
            },
            onDismiss = { confirmOff = false }, destructive = true,
        )
    }
}

/** Backup page rows, split out so they can be rendered in screenshot tests. */
internal fun androidx.compose.foundation.lazy.LazyListScope.backupItems(
    s: app.yarn.data.prefs.AppSettings,
    latest: DriveBackup?,
    state: BackupState,
    checking: Boolean,
    error: String?,
    running: Boolean,
    onSignIn: () -> Unit,
    onBackupNow: () -> Unit,
    onAutoBackup: (Boolean) -> Unit,
    onMedia: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onTurnOff: () -> Unit,
) {
    if (s.googleAccount == null) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(settingsCardColor())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color(0xFF1466F0)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CloudUpload, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Back up to Google Drive", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Keep your messages safe and bring them to a new phone. Free — it uses your Google account's storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onSignIn,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Sign in with Google") }
            }
        }
    } else {
        item {
            SettingsGroup {
                SettingsRow(
                    s.googleAccount.orEmpty(),
                    summary = when {
                        s.lastBackupAt > 0 -> "Last backup ${Format.listTime(LocalContext.current, s.lastBackupAt)} · ${android.text.format.Formatter.formatShortFileSize(LocalContext.current, s.lastBackupBytes)}"
                        latest != null -> "Last backup ${Format.listTime(LocalContext.current, latest.modifiedAt)} · ${android.text.format.Formatter.formatShortFileSize(LocalContext.current, latest.sizeBytes)}"
                        else -> "Not backed up yet"
                    },
                    icon = Icons.Outlined.CloudDone, tint = androidx.compose.ui.graphics.Color(0xFF1466F0),
                )
            }
        }
        item {
            Button(
                onClick = onBackupNow,
                enabled = !running,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(48.dp),
            ) { Text("Back up now") }
        }
        item { BackupProgress(state, checking) }
        item { SectionHeader("Settings") }
        item {
            SettingsGroup {
                SwitchRow("Back up daily", "On Wi-Fi while charging", s.autoBackup, icon = Icons.Outlined.Schedule, tint = androidx.compose.ui.graphics.Color(0xFF34C759)) { v -> onAutoBackup(v) }
                SwitchRow("Include photos & videos", checked = s.backupMedia, icon = Icons.Outlined.Image, tint = androidx.compose.ui.graphics.Color(0xFFFF9500)) { v -> onMedia(v) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            SettingsGroup {
                ClickRow("Restore from Google Drive", icon = Icons.Outlined.CloudDownload, tint = androidx.compose.ui.graphics.Color(0xFF0FA3B1)) { onRestore() }
                ClickRow("Turn off Google backup", destructive = true) { onTurnOff() }
            }
        }
    }
    error?.let { e ->
        item {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
            }
        }
    }
    item {
        Text(
            "Backups are encrypted and kept in a private Yarn folder in your Google Drive that only Yarn can open. " +
                "They count towards your Google storage (15 GB free). Yarn has no servers of its own.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun BackupProgress(state: BackupState, checking: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        when (state) {
            is BackupState.Running -> {
                Text(
                    state.label + if (state.total > 0) " · ${state.done} / ${state.total}" else if (state.done > 0) " · ${state.done}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.total > 0) LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            is BackupState.Done -> Text(state.message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            is BackupState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            BackupState.Idle -> if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun StarredScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpen: (Long, Long) -> Unit) {
    val starred by vm.c.db.messages().observeStarred().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val iso = remember { PhoneNumbers.countryIso(context) }
    SimpleScaffold("Starred messages", onBack) { modifier ->
        LazyColumn(modifier) {
            if (starred.isEmpty()) item { Text("Long-press a message and choose Star to keep it here.", Modifier.padding(24.dp)) }
            items(starred, key = { it.message.id }) { s ->
                val m = s.message
                ListItem(
                    overlineContent = {
                        Text((if (m.outgoing) "You" else vm.c.contacts.lookup(m.address)?.name ?: PhoneNumbers.format(m.address, iso)) + " · " + Format.listTime(context, m.date))
                    },
                    headlineContent = { Text(m.body.ifBlank { "Attachment" }, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { onOpen(m.conversationId, m.id) },
                )
            }
        }
    }
}
