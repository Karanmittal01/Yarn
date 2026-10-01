package app.yarn.ui.conversation

import android.telephony.SmsMessage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.yarn.ai.RewriteStyle
import app.yarn.messaging.AttachmentDraft
import app.yarn.telephony.SimInfo
import coil3.compose.AsyncImage
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Composer(
    text: String,
    onText: (String) -> Unit,
    attachments: List<AttachmentDraft>,
    onAddAttachments: (List<android.net.Uri>) -> Unit,
    onRemoveAttachment: (AttachmentDraft) -> Unit,
    sims: List<SimInfo>,
    subId: Int,
    onSim: (Int) -> Unit,
    smartReplies: List<String>,
    onSmartReply: (String) -> Unit,
    onSend: () -> Unit,
    onSchedule: (Long) -> Unit,
    onRewrite: (RewriteStyle) -> Unit,
    isGroupMms: Boolean,
) {
    var attachMenu by remember { mutableStateOf(false) }
    var simMenu by remember { mutableStateOf(false) }
    var aiMenu by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf(false) }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { if (it.isNotEmpty()) onAddAttachments(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) onAddAttachments(it) }
    val canSend = text.isNotBlank() || attachments.isNotEmpty()

    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            if (smartReplies.isNotEmpty() && text.isEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(smartReplies) { r -> SuggestionChip(onClick = { onSmartReply(r) }, label = { Text(r) }) }
                }
            }
            if (attachments.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attachments) { a ->
                        Box {
                            if (a.mimeType.startsWith("image/") || a.mimeType.startsWith("video/")) {
                                AsyncImage(a.uri, a.fileName, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                            } else {
                                InputChip(selected = false, onClick = {}, label = { Text(a.fileName) }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) })
                            }
                            IconButton(onClick = { onRemoveAttachment(a) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) {
                                    Icon(Icons.Outlined.Close, "Remove ${a.fileName}", tint = Color.White, modifier = Modifier.padding(2.dp).size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(onClick = { attachMenu = true }) { Icon(Icons.Outlined.AddCircleOutline, "Attach") }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Photos & videos") }, leadingIcon = { Icon(Icons.Outlined.Image, null) },
                            onClick = { attachMenu = false; pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        )
                        DropdownMenuItem(
                            text = { Text("File") }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                            onClick = { attachMenu = false; pickFiles.launch(arrayOf("*/*")) },
                        )
                    }
                }
                TextField(
                    value = text,
                    onValueChange = onText,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    placeholder = { Text(if (attachments.isNotEmpty() || isGroupMms) "MMS message" else "Text message") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = if (text.isNotBlank()) ({
                        Box {
                            IconButton(onClick = { aiMenu = true }) { Icon(Icons.Outlined.AutoAwesome, "Rewrite with AI") }
                            DropdownMenu(expanded = aiMenu, onDismissRequest = { aiMenu = false }) {
                                listOf(
                                    RewriteStyle.REPHRASE to "Rephrase", RewriteStyle.SHORTEN to "Shorten", RewriteStyle.ELABORATE to "Elaborate",
                                    RewriteStyle.FRIENDLY to "Friendlier", RewriteStyle.PROFESSIONAL to "More professional", RewriteStyle.EMOJIFY to "Add emoji",
                                ).forEach { (s, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { aiMenu = false; onRewrite(s) }) }
                            }
                        }
                    }) else null,
                    supportingText = segmentInfo(text, attachments.isNotEmpty() || isGroupMms)?.let { info -> { Text(info) } },
                )
                if (sims.size > 1) {
                    Box {
                        val sim = sims.firstOrNull { it.subId == subId }
                        IconButton(onClick = { simMenu = true }, modifier = Modifier.semantics { contentDescription = "Send using ${sim?.label ?: "default SIM"}" }) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.SimCard, null)
                                Text("${(sim?.slotIndex ?: 0) + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        DropdownMenu(expanded = simMenu, onDismissRequest = { simMenu = false }) {
                            sims.forEach { s ->
                                DropdownMenuItem(text = { Text(s.label + (s.number?.let { "  $it" } ?: "")) }, onClick = { simMenu = false; onSim(s.subId) })
                            }
                        }
                    }
                }
                val sendColor = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                Surface(
                    shape = CircleShape,
                    color = sendColor,
                    modifier = Modifier
                        .padding(bottom = 10.dp, start = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(enabled = canSend, onClick = onSend, onLongClick = { schedule = true }, onClickLabel = "Send", onLongClickLabel = "Schedule message")
                        .semantics { contentDescription = "Send. Long press to schedule" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (schedule) ScheduleDialog(onDismiss = { schedule = false }, onPick = { schedule = false; onSchedule(it) })
}

/** "2/160 · 1 SMS" style counter, shown once the message gets long or uses Unicode. */
private fun segmentInfo(text: String, mms: Boolean): String? {
    if (mms || text.length < 60) return null
    val r = SmsMessage.calculateLength(text, false)
    val parts = r[0]
    val remaining = r[2]
    return if (parts > 1) "$remaining left · $parts messages" else "$remaining left"
}

@Composable
fun ScheduleDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<Long?>(null) }
    if (!custom) {
        val now = Calendar.getInstance()
        fun at(daysAhead: Int, hour: Int) = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAhead); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val options = buildList {
            if (now.get(Calendar.HOUR_OF_DAY) < 17) add("Later today, 6:00 PM" to at(0, 18))
            add("Tomorrow, 9:00 AM" to at(1, 9))
            add("Tomorrow, 6:00 PM" to at(1, 18))
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Schedule message") },
            text = {
                Column {
                    options.forEach { (label, t) -> TextButton(onClick = { onPick(t) }, modifier = Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) } }
                    TextButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) { Text("Pick date & time…", Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        return
    }
    if (pickedDate == null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { pickedDate = state.selectedDateMillis ?: System.currentTimeMillis() }) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(state) }
    } else {
        val time = rememberTimePickerState(initialHour = 9, initialMinute = 0)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Time") },
            text = { TimePicker(time) },
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker returns UTC midnight; convert to the local calendar date.
                    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedDate!! }
                    val local = Calendar.getInstance().apply {
                        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), time.hour, time.minute, 0)
                    }
                    onPick(local.timeInMillis.coerceAtLeast(System.currentTimeMillis() + 60_000))
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

@Composable
fun RewriteSheet(state: RewriteUi, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    if (state == RewriteUi.Hidden) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Rewrite", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            when (state) {
                RewriteUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Rewriting on-device…")
                }
                is RewriteUi.Error -> Text(state.message)
                is RewriteUi.Ready -> {
                    state.options.forEach { option ->
                        OutlinedCard(onClick = { onApply(option) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(option, Modifier.padding(14.dp))
                        }
                    }
                    Text("By ${state.engine.label}. Review before sending.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                }
                RewriteUi.Hidden -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SummarySheet(state: SummaryUi, onDismiss: () -> Unit) {
    if (state == SummaryUi.Hidden) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Summary", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            when (state) {
                SummaryUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Summarising on-device…")
                }
                is SummaryUi.Ready -> {
                    val s = state.summary.summary
                    if (s.bullets.isEmpty() && s.keyDetails.isEmpty()) Text("Not enough conversation to summarise yet.")
                    s.bullets.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 3.dp)) }
                    if (s.keyDetails.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Key details", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.padding(top = 4.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(s.keyDetails) { AssistChip(onClick = {}, label = { Text(it) }) } }
                        }
                    }
                    s.awaitingReply?.let {
                        Spacer(Modifier.height(12.dp))
                        Text("Waiting on you", style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("By ${state.summary.engine.label}. Summaries can miss details.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 16.dp))
                }
                SummaryUi.Hidden -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
