package app.yarn.ui.conversation

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.yarn.data.db.AttachmentEntity
import app.yarn.data.db.ExtractedEntity
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.MessageWithAttachments
import app.yarn.intelligence.EntityType
import app.yarn.intelligence.SpamVerdict
import app.yarn.notifications.NotificationActionReceiver
import app.yarn.ui.common.Format
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

data class BubbleCallbacks(
    val onClick: (MessageWithAttachments) -> Unit,
    val onLongClick: (MessageWithAttachments) -> Unit,
    val onRetry: (Long) -> Unit,
    val onUndo: (Long) -> Unit,
    val onDownload: (Long) -> Unit,
    val onTranslate: (MessageWithAttachments) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    item: MessageWithAttachments,
    senderLabel: String?,
    selected: Boolean,
    highlighted: Boolean,
    translation: TranslationUi?,
    showInsights: Boolean,
    callbacks: BubbleCallbacks,
) {
    val m = item.message
    val context = LocalContext.current
    val outgoing = m.outgoing
    val colors = MaterialTheme.colorScheme
    val container = when {
        selected -> colors.tertiaryContainer
        m.status == MessageStatus.FAILED -> colors.errorContainer
        outgoing -> colors.primaryContainer
        else -> colors.surfaceContainerHighest
    }
    val content = when {
        selected -> colors.onTertiaryContainer
        m.status == MessageStatus.FAILED -> colors.onErrorContainer
        outgoing -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val risky = remember(m.riskyUrls) { m.riskyUrls.lines().filter { it.isNotBlank() }.toSet() }
    var pendingRiskyUrl by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(20.dp, 20.dp, if (outgoing) 6.dp else 20.dp, if (outgoing) 20.dp else 6.dp)
    val statusText = statusLabel(m.status, m.errorCode)
    val a11y = buildString {
        append(if (outgoing) "You" else senderLabel ?: "Message")
        append(": ")
        append(m.body.ifBlank { if (m.hasAttachments) "attachment" else "multimedia message" })
        append(". ${Format.time(context, m.date)}")
        statusText?.let { append(". $it") }
        if (m.starred) append(". Starred")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) colors.secondaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        if (senderLabel != null && !outgoing) {
            Text(senderLabel, style = MaterialTheme.typography.labelMedium, color = colors.primary, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp))
        }
        Surface(
            color = container,
            contentColor = content,
            shape = shape,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(shape)
                .combinedClickable(onClick = { callbacks.onClick(item) }, onLongClick = { callbacks.onLongClick(item) }, onLongClickLabel = "Select message")
                .semantics(mergeDescendants = false) { contentDescription = a11y },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                m.subject?.takeIf { it.isNotBlank() }?.let { Text(it, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp)) }
                if (item.attachments.isNotEmpty()) {
                    item.attachments.forEach { AttachmentView(it) }
                }
                if (m.status == MessageStatus.PENDING_DOWNLOAD || m.status == MessageStatus.DOWNLOADING || m.status == MessageStatus.DOWNLOAD_FAILED || m.status == MessageStatus.EXPIRED) {
                    MmsPlaceholder(m.status, m.mmsSize, m.mmsExpiry) { callbacks.onDownload(m.id) }
                }
                if (m.body.isNotBlank()) {
                    Text(
                        linkify(m.body, item.entities, risky, colors.primary) { url -> if (url in risky) pendingRiskyUrl = url else Actions.openUrl(context, url) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                when (translation) {
                    TranslationUi.Loading -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text("  Translating on-device…", style = MaterialTheme.typography.labelSmall)
                    }
                    is TranslationUi.Done -> Column(Modifier.padding(top = 6.dp)) {
                        Text(translation.text, style = MaterialTheme.typography.bodyLarge)
                        Text("Translated from ${translation.from} · tap Translate again for original", style = MaterialTheme.typography.labelSmall)
                    }
                    is TranslationUi.Error -> Text(translation.message, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    null -> Unit
                }
            }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.starred) Icon(Icons.Outlined.Star, null, Modifier.size(12.dp), tint = colors.tertiary)
            Text(Format.time(context, if (m.status == MessageStatus.SCHEDULED) m.scheduledAt else m.date), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            if (outgoing) StatusIcon(m.status)
            statusText?.let { Text("  $it", style = MaterialTheme.typography.labelSmall, color = if (m.status == MessageStatus.FAILED) colors.error else colors.onSurfaceVariant) }
            when (m.status) {
                MessageStatus.FAILED -> TextButton(onClick = { callbacks.onRetry(m.id) }) { Text("Retry") }
                MessageStatus.DELAYED -> UndoCountdown(m.nextAttemptAt) { callbacks.onUndo(m.id) }
                MessageStatus.SCHEDULED, MessageStatus.WAITING_FOR_SERVICE, MessageStatus.QUEUED -> TextButton(onClick = { callbacks.onUndo(m.id) }) { Text("Cancel") }
            }
        }
        if (showInsights && !outgoing) InsightChips(item, risky, onTranslate = { callbacks.onTranslate(item) }, onRisky = { pendingRiskyUrl = it })
    }

    pendingRiskyUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingRiskyUrl = null },
            icon = { Icon(Icons.Outlined.Warning, null, tint = colors.error) },
            title = { Text("This link looks dangerous") },
            text = {
                Column {
                    Text(url, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    m.spamReasons.lines().filter { it.isNotBlank() }.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.size(8.dp))
                    Text("Never enter passwords, card details or codes on a page opened from a message.", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { pendingRiskyUrl = null }) { Text("Stay safe") } },
            dismissButton = { TextButton(onClick = { pendingRiskyUrl = null; Actions.openUrl(context, url) }) { Text("Open anyway", color = colors.error) } },
        )
    }
}

@Composable
private fun UndoCountdown(deadline: Long, onUndo: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) {
        while (now < deadline) { delay(250); now = System.currentTimeMillis() }
    }
    val left = ((deadline - now) / 1000 + 1).coerceAtLeast(0)
    if (left > 0) TextButton(onClick = onUndo) { Text("Undo (${left}s)") }
}

fun statusLabel(status: Int, errorCode: Int): String? = when (status) {
    MessageStatus.QUEUED -> "Queued"
    MessageStatus.SENDING -> "Sending…"
    MessageStatus.DELIVERED -> "Delivered"
    MessageStatus.FAILED -> if (errorCode == -2) "Status unknown" else "Not sent"
    MessageStatus.SCHEDULED -> "Scheduled"
    MessageStatus.WAITING_FOR_SERVICE -> "Waiting for signal"
    MessageStatus.DELAYED -> "Sending soon"
    else -> null
}

@Composable
private fun StatusIcon(status: Int) {
    val icon = when (status) {
        MessageStatus.SENT -> Icons.Outlined.Done
        MessageStatus.DELIVERED -> Icons.Outlined.DoneAll
        MessageStatus.FAILED -> Icons.Outlined.ErrorOutline
        MessageStatus.QUEUED, MessageStatus.SENDING, MessageStatus.SCHEDULED, MessageStatus.WAITING_FOR_SERVICE, MessageStatus.DELAYED -> Icons.Outlined.Schedule
        else -> null
    } ?: return
    Icon(icon, null, Modifier.padding(start = 4.dp).size(14.dp), tint = if (status == MessageStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MmsPlaceholder(status: Int, size: Long, expiry: Long, onDownload: () -> Unit) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text("Multimedia message" + if (size > 0) " · ${Format.size(size)}" else "", fontWeight = FontWeight.Medium)
        when (status) {
            MessageStatus.DOWNLOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("  Downloading…")
            }
            MessageStatus.EXPIRED -> Text("This message expired on the carrier's server.")
            else -> {
                Text(if (status == MessageStatus.DOWNLOAD_FAILED) "Couldn't download. MMS needs mobile data." else "Not downloaded yet.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onDownload, modifier = Modifier.padding(top = 4.dp)) { Text("Download") }
            }
        }
        if (expiry > 0 && status != MessageStatus.EXPIRED) {
            val context = LocalContext.current
            Text("Available until ${Format.dateTime(context, expiry * 1000)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AttachmentView(a: AttachmentEntity) {
    val context = LocalContext.current
    val uri = Uri.parse(a.uri)
    when {
        a.mimeType.startsWith("image/") -> AsyncImage(
            model = uri, contentDescription = a.fileName ?: "Image", contentScale = ContentScale.Crop,
            modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp)).clickable { Actions.openAttachment(context, uri, a.mimeType) },
        )
        a.mimeType.startsWith("video/") -> Box(
            Modifier.padding(bottom = 6.dp).fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(14.dp)).clickable { Actions.openAttachment(context, uri, a.mimeType) },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(model = uri, contentDescription = a.fileName ?: "Video", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
            Icon(Icons.Outlined.PlayCircle, "Play video", Modifier.size(56.dp), tint = androidx.compose.ui.graphics.Color.White)
        }
        else -> AssistChip(
            onClick = { Actions.openAttachment(context, uri, a.mimeType) },
            label = { Text((a.fileName ?: a.mimeType) + if (a.size > 0) " · ${Format.size(a.size)}" else "") },
            leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
}

/** Turns URLs, phone numbers and emails found by the extractor into tappable links. */
private fun linkify(
    text: String,
    entities: List<ExtractedEntity>,
    risky: Set<String>,
    linkColor: androidx.compose.ui.graphics.Color,
    onUrl: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val normal = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    val danger = TextLinkStyles(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFD32F2F), textDecoration = TextDecoration.LineThrough))
    for (e in entities) {
        if (e.start < 0 || e.end > text.length || e.start >= e.end) continue
        when (e.type) {
            EntityType.URL.name -> addLink(
                LinkAnnotation.Clickable(e.value, if (e.value in risky) danger else normal) { onUrl(e.value) }, e.start, e.end,
            )
            EntityType.PHONE.name -> addLink(LinkAnnotation.Url("tel:${e.value}", normal), e.start, e.end)
            EntityType.EMAIL.name -> addLink(LinkAnnotation.Url("mailto:${e.value}", normal), e.start, e.end)
            EntityType.OTP.name -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), e.start, e.end)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InsightChips(item: MessageWithAttachments, risky: Set<String>, onTranslate: () -> Unit, onRisky: (String) -> Unit) {
    val context = LocalContext.current
    val m = item.message
    val entities = item.entities.distinctBy { it.type + it.value }
    val verdict = runCatching { SpamVerdict.valueOf(m.spamVerdict) }.getOrDefault(SpamVerdict.CLEAN)
    val chips = ArrayList<@Composable () -> Unit>()
    for (e in entities.take(6)) {
        when (e.type) {
            EntityType.OTP.name -> chips += {
                AssistChip(
                    onClick = {
                        NotificationActionReceiver.copySensitive(context, e.value)
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("Copy code ${e.value}") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
            EntityType.URL.name -> if (verdict != SpamVerdict.SCAM) chips += {
                val bad = e.value in risky
                AssistChip(
                    onClick = { if (bad) onRisky(e.value) else Actions.openUrl(context, e.value) },
                    label = { Text(if (bad) "Suspicious link" else "Open ${e.attributes["host"] ?: "link"}") },
                    leadingIcon = { Icon(if (bad) Icons.Outlined.Warning else Icons.Outlined.Link, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
            EntityType.DATE_TIME.name -> if (m.category != "OTP" && m.category != "BANKING") chips += {
                AssistChip(
                    onClick = { Actions.addToCalendar(context, e.value, m.body.take(60), m.body) },
                    label = { Text("Add “${e.text}” to calendar") },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
            EntityType.TRACKING_NUMBER.name -> chips += {
                val url = e.attributes["trackUrl"]
                AssistChip(
                    onClick = {
                        if (url != null) Actions.openUrl(context, url)
                        else { NotificationActionReceiver.copySensitive(context, e.value); Toast.makeText(context, "Tracking number copied", Toast.LENGTH_SHORT).show() }
                    },
                    label = { Text(if (url != null) "Track ${e.attributes["carrier"] ?: "package"}" else "Copy tracking ${e.value}") },
                    leadingIcon = { Icon(Icons.Outlined.LocalShipping, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
            EntityType.PHONE.name -> if (verdict == SpamVerdict.CLEAN || verdict == SpamVerdict.PROMOTIONAL) chips += {
                AssistChip(onClick = { Actions.dial(context, e.value) }, label = { Text("Call ${e.text}") }, leadingIcon = { Icon(Icons.Outlined.Call, null, Modifier.size(AssistChipDefaults.IconSize)) })
            }
            EntityType.ADDRESS.name -> chips += {
                AssistChip(onClick = { Actions.map(context, e.value) }, label = { Text("Directions") }, leadingIcon = { Icon(Icons.Outlined.Directions, null, Modifier.size(AssistChipDefaults.IconSize)) })
            }
            EntityType.EMAIL.name -> chips += {
                AssistChip(onClick = { Actions.email(context, e.value) }, label = { Text("Email") }, leadingIcon = { Icon(Icons.Outlined.Email, null, Modifier.size(AssistChipDefaults.IconSize)) })
            }
            EntityType.BOOKING_REF.name, EntityType.ORDER_ID.name, EntityType.UPI_ID.name -> chips += {
                AssistChip(
                    onClick = { NotificationActionReceiver.copySensitive(context, e.value); Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() },
                    label = { Text("Copy ${e.value}") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
    }
    val nonAscii = m.body.count { it.code > 127 } > m.body.length / 3
    if (m.body.length > 3 && nonAscii) chips += {
        AssistChip(onClick = onTranslate, label = { Text("Translate") }, leadingIcon = { Icon(Icons.Outlined.Translate, null, Modifier.size(AssistChipDefaults.IconSize)) })
    }
    if (chips.isEmpty()) return
    FlowRow(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { it() }
    }
}
