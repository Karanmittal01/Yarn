package app.yarn.ui.settings

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
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { p ->
        content(Modifier.padding(p).fillMaxSize())
    }
}

@Composable
fun BlockedScreen(vm: SettingsViewModel, onBack: () -> Unit, onBlockedConversations: () -> Unit) {
    val rules by vm.c.db.blocks().observe().collectAsState(initial = emptyList())
    var add by remember { mutableStateOf<String?>(null) }
    SimpleScaffold("Blocked numbers & filters", onBack) { modifier ->
        LazyColumn(modifier) {
            item {
                Text(
                    "Messages from blocked numbers are dropped. Messages containing a filtered word go to Spam without a notification. " +
                        "When Yarn is your default SMS app, blocked numbers are also added to Android's system block list.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(onClick = { add = BlockRuleEntity.NUMBER }) { Text("Block a number") }
                    OutlinedButton(onClick = { add = BlockRuleEntity.KEYWORD }, modifier = Modifier.padding(start = 8.dp)) { Text("Add word filter") }
                }
            }
            item { ClickRow("Blocked conversations", "View or unblock") { onBlockedConversations() } }
            if (rules.isEmpty()) item { ListItem(headlineContent = { Text("No blocked numbers or filters") }) }
            items(rules, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(r.value) },
                    supportingContent = { Text(if (r.type == BlockRuleEntity.NUMBER) "Blocked number" else "Word filter") },
                    trailingContent = {
                        IconButton(onClick = {
                            vm.launch { if (r.type == BlockRuleEntity.NUMBER) conversations.unblockNumber(r.value) else conversations.removeRule(r.id) }
                        }) { Icon(Icons.Outlined.Delete, "Remove ${r.value}") }
                    },
                )
            }
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
    val state by vm.c.backup.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var includeMedia by remember { mutableStateOf(true) }
    var passFor by remember { mutableStateOf<Pair<Boolean, Uri>?>(null) } // (isExport, uri)
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { passFor = true to it } }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { passFor = false to it } }
    val running = state is BackupState.Running

    SimpleScaffold("Backup & restore", onBack) { modifier ->
        Column(modifier.padding(16.dp)) {
            Text(
                "Backups are encrypted with a passphrase only you know (AES-256). Save them to your phone, an SD card or any cloud drive app — " +
                    "then restore on a new phone to move your history. Yarn has no servers and cannot recover a lost passphrase.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp).clickable { includeMedia = !includeMedia }) {
                Checkbox(includeMedia, { includeMedia = it })
                Text("Include photos and attachments")
            }
            Button(
                onClick = { createDoc.launch("yarn-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.yarnbak") },
                enabled = !running, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Back up now") }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("*/*")) }, enabled = !running, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Restore from backup") }
            when (val s = state) {
                is BackupState.Running -> {
                    Text("${s.label}… ${if (s.total > 0) "${s.done} / ${s.total}" else s.done.toString()}", modifier = Modifier.padding(top = 16.dp))
                    if (s.total > 0) LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                is BackupState.Done -> Text(s.message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
                is BackupState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                BackupState.Idle -> Unit
            }
        }
    }

    passFor?.let { (export, uri) ->
        var pass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        val valid = pass.length >= 8 && (!export || pass == confirm)
        AlertDialog(
            onDismissRequest = { passFor = null },
            title = { Text(if (export) "Choose a passphrase" else "Enter passphrase") },
            text = {
                Column {
                    OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase (8+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (export) OutlinedTextField(
                        confirm, { confirm = it }, label = { Text("Repeat passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.padding(top = 8.dp),
                        isError = confirm.isNotEmpty() && confirm != pass,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    val chars = pass.toCharArray()
                    passFor = null
                    vm.launch {
                        if (export) backup.export(uri, chars, includeMedia)
                        else { backup.restore(uri, chars); ReanalyzeWorker.enqueue(context) }
                    }
                }) { Text(if (export) "Back up" else "Restore") }
            },
            dismissButton = { TextButton(onClick = { passFor = null }) { Text("Cancel") } },
        )
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
