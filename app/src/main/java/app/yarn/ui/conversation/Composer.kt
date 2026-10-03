package app.yarn.ui.conversation

import android.telephony.SmsMessage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.unit.sp
import app.yarn.ai.RewriteStyle
import app.yarn.messaging.AttachmentDraft
import app.yarn.telephony.SimInfo
import coil3.compose.AsyncImage
import java.util.Calendar
import androidx.compose.ui.res.stringResource
import app.yarn.R

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

    Surface(color = MaterialTheme.colorScheme.surface) {
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
                            val removeDesc = stringResource(R.string.remove_n, a.fileName)
                            if (a.mimeType.startsWith("image/") || a.mimeType.startsWith("video/")) {
                                AsyncImage(a.uri, a.fileName, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                            } else {
                                InputChip(selected = false, onClick = {}, label = { Text(a.fileName) }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) })
                            }
                            IconButton(onClick = { onRemoveAttachment(a) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) {
                                    Icon(Icons.Outlined.Close, removeDesc, tint = Color.White, modifier = Modifier.padding(2.dp).size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            val sim = sims.firstOrNull { it.subId == subId } ?: sims.firstOrNull()
            if (sims.size > 1 && sim != null) {
                // Dual SIM: always say which SIM this message will go from; one tap to switch.
                val changeSim = stringResource(R.string.change_sim)
                val sendingDesc = stringResource(R.string.sending_from_desc, app.yarn.ui.common.simLabel(sim))
                Box(Modifier.padding(start = 14.dp, top = 2.dp)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = changeSim) { simMenu = true }
                            .background(app.yarn.ui.common.simColor(sim).copy(alpha = 0.12f))
                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            .semantics { contentDescription = sendingDesc },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.sending_from), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        app.yarn.ui.common.SimTag(sim, badgeHeight = 14.dp, fontSize = 13.sp)
                        Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp), tint = app.yarn.ui.common.simColor(sim))
                    }
                    app.yarn.ui.common.YarnMenu(expanded = simMenu, onDismissRequest = { simMenu = false }) {
                        sims.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(app.yarn.ui.common.simLabel(s), style = MaterialTheme.typography.bodyLarge)
                                        s.number?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                },
                                leadingIcon = { app.yarn.ui.common.SimBadge(s, height = 22.dp) },
                                trailingIcon = { if (s.subId == sim.subId) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { simMenu = false; onSim(s.subId) },
                                modifier = Modifier.heightIn(min = 56.dp),
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(onClick = { attachMenu = true }) { Icon(Icons.Outlined.AddCircleOutline, stringResource(R.string.attach)) }
                    app.yarn.ui.common.YarnMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.photos_videos)) }, leadingIcon = { Icon(Icons.Outlined.Image, null) },
                            onClick = { attachMenu = false; pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file)) }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                            onClick = { attachMenu = false; pickFiles.launch(arrayOf("*/*")) },
                        )
                    }
                }
                TextField(
                    value = text,
                    onValueChange = onText,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    placeholder = { Text(if (attachments.isNotEmpty() || isGroupMms) stringResource(R.string.hint_mms) else stringResource(R.string.hint_text)) },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    trailingIcon = if (text.isNotBlank()) ({
                        Box {
                            IconButton(onClick = { aiMenu = true }) { Icon(Icons.Outlined.AutoAwesome, stringResource(R.string.rewrite_ai)) }
                            app.yarn.ui.common.YarnMenu(expanded = aiMenu, onDismissRequest = { aiMenu = false }) {
                                listOf(
                                    RewriteStyle.REPHRASE to R.string.rw_rephrase, RewriteStyle.SHORTEN to R.string.rw_shorten, RewriteStyle.ELABORATE to R.string.rw_elaborate,
                                    RewriteStyle.FRIENDLY to R.string.rw_friendly, RewriteStyle.PROFESSIONAL to R.string.rw_professional, RewriteStyle.EMOJIFY to R.string.rw_emoji,
                                ).forEach { (s, label) -> DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { aiMenu = false; onRewrite(s) }) }
                            }
                        }
                    }) else null,
                    supportingText = segmentInfo(LocalContext.current, text, attachments.isNotEmpty() || isGroupMms)?.let { info -> { Text(info) } },
                )
                val sendLabel = stringResource(R.string.send)
                val scheduleLabel = stringResource(R.string.schedule_message)
                val sendDesc = stringResource(R.string.send_desc)
                val sendColor = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                Surface(
                    shape = CircleShape,
                    color = sendColor,
                    modifier = Modifier
                        .padding(bottom = 10.dp, start = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(enabled = canSend, onClick = onSend, onLongClick = { schedule = true }, onClickLabel = sendLabel, onLongClickLabel = scheduleLabel)
                        .semantics { contentDescription = sendDesc },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (sims.size > 1 && sim != null) {
                            app.yarn.ui.common.SimBadge(sim, Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 7.dp), height = 13.dp)
                        }
                    }
                }
            }
        }
    }
    if (schedule) ScheduleDialog(onDismiss = { schedule = false }, onPick = { schedule = false; onSchedule(it) })
}

/** "2/160 · 1 SMS" style counter, shown once the message gets long or uses Unicode. */
private fun segmentInfo(context: android.content.Context, text: String, mms: Boolean): String? {
    if (mms || text.length < 60) return null
    val r = SmsMessage.calculateLength(text, false)
    val parts = r[0]
    val remaining = r[2]
    return if (parts > 1) context.getString(R.string.chars_left_parts, remaining, parts) else context.getString(R.string.chars_left, remaining)
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
        val context = LocalContext.current
        fun timeOf(t: Long) = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(t))
        val options = buildList {
            if (now.get(Calendar.HOUR_OF_DAY) < 17) at(0, 18).let { add(context.getString(R.string.later_today_at, timeOf(it)) to it) }
            at(1, 9).let { add(context.getString(R.string.tomorrow_at, timeOf(it)) to it) }
            at(1, 18).let { add(context.getString(R.string.tomorrow_at, timeOf(it)) to it) }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.schedule_message)) },
            text = {
                Column {
                    options.forEach { (label, t) -> TextButton(onClick = { onPick(t) }, modifier = Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) } }
                    TextButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pick_date_time), Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }
    if (pickedDate == null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { pickedDate = state.selectedDateMillis ?: System.currentTimeMillis() }) { Text(stringResource(R.string.next)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    } else {
        val time = rememberTimePickerState(initialHour = 9, initialMinute = 0)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.time)) },
            text = { TimePicker(time) },
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker returns UTC midnight; convert to the local calendar date.
                    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedDate!! }
                    val local = Calendar.getInstance().apply {
                        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), time.hour, time.minute, 0)
                    }
                    onPick(local.timeInMillis.coerceAtLeast(System.currentTimeMillis() + 60_000))
                }) { Text(stringResource(R.string.schedule)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun RewriteSheet(state: RewriteUi, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    if (state == RewriteUi.Hidden) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.rewrite), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            when (state) {
                RewriteUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.rewriting))
                }
                is RewriteUi.Error -> Text(stringResource(state.message))
                is RewriteUi.Ready -> {
                    state.options.forEach { option ->
                        OutlinedCard(onClick = { onApply(option) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(option, Modifier.padding(14.dp))
                        }
                    }
                    Text(stringResource(R.string.by_engine_review, stringResource(state.engine.label)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
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
                Text(stringResource(R.string.summary), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            when (state) {
                SummaryUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.summarising))
                }
                is SummaryUi.Ready -> {
                    val s = state.summary.summary
                    if (s.bullets.isEmpty() && s.keyDetails.isEmpty()) Text(stringResource(R.string.summary_empty))
                    s.bullets.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 3.dp)) }
                    if (s.keyDetails.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.key_details), style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.padding(top = 4.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(s.keyDetails) { AssistChip(onClick = {}, label = { Text(it) }) } }
                        }
                    }
                    s.awaitingReply?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.waiting_on_you), style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.by_engine_summary, stringResource(state.summary.engine.label)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 16.dp))
                }
                SummaryUi.Hidden -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
