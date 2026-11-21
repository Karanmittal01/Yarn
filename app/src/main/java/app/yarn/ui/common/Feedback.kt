package app.yarn.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yarn.BuildConfig
import app.yarn.R

/**
 * Feedback and logo requests go straight to the developer, on WhatsApp or by email. The number
 * and address are set in res/values/support.xml; a blank one hides that choice.
 */
object Feedback {
    /** Something ready to send: a subject (for email), the text, and Yarn's error log when it helps. */
    data class Note(val title: String, val subject: String, val text: String, val log: String? = null)

    fun whatsAppNumber(context: Context): String? =
        context.getString(R.string.support_whatsapp).filter(Char::isDigit).takeIf { it.length in 10..15 }

    fun emailAddress(context: Context): String? =
        context.getString(R.string.support_email).trim().takeIf { '@' in it && ' ' !in it }

    fun device(): String =
        "Yarn ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"

    fun feedback(context: Context, log: String?): Note = Note(
        title = context.getString(R.string.feedback_title),
        subject = context.getString(R.string.feedback_subject),
        text = context.getString(R.string.feedback_body) + "\n\n\n— " + device(),
        log = log?.takeIf { it.isNotBlank() },
    )

    /** Only the sender's name and ID: never the messages. */
    fun logoRequest(context: Context, name: String, senderId: String): Note = Note(
        title = context.getString(R.string.logo_request),
        subject = context.getString(R.string.logo_request_subject, name),
        text = context.getString(R.string.logo_request_text, name, senderId.uppercase()) + "\n\n— " + device(),
    )

    fun whatsApp(context: Context, note: Note): Boolean {
        val number = whatsAppNumber(context) ?: return false
        return open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=" + Uri.encode(note.text))))
    }

    fun email(context: Context, note: Note): Boolean {
        val to = emailAddress(context) ?: return false
        val body = note.text + (note.log?.let { "\n\n---\n$it" } ?: "")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, note.subject)
            .putExtra(Intent.EXTRA_TEXT, body.take(60_000))
        return open(context, intent)
    }

    /** Any app the person likes: the fallback when no number or address is set. */
    fun share(context: Context, note: Note): Boolean {
        val to = emailAddress(context)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, note.subject)
            .putExtra(Intent.EXTRA_TEXT, (note.text + (note.log?.let { "\n\n---\n$it" } ?: "")).take(60_000))
            .apply { if (to != null) putExtra(Intent.EXTRA_EMAIL, arrayOf(to)) }
        return open(context, Intent.createChooser(send, note.title))
    }

    private fun open(context: Context, intent: Intent) = runCatching { context.startActivity(intent) }.isSuccess
}

/** Asks how to send [note]: WhatsApp, email, or any other app. */
@Composable
fun FeedbackChooser(note: Feedback.Note, subtitle: String? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val whatsApp = Feedback.whatsAppNumber(context) != null
    val email = Feedback.emailAddress(context) != null
    if (!whatsApp && !email) {
        // Nowhere set up to send it directly: straight to the share sheet.
        LaunchedEffect(note) { Feedback.share(context, note); onDismiss() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.title) },
        text = {
            Column {
                Text(subtitle ?: stringResource(R.string.feedback_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                if (whatsApp) Choice(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.feedback_whatsapp)) { Feedback.whatsApp(context, note); onDismiss() }
                if (email) Choice(Icons.Outlined.Email, stringResource(R.string.feedback_email)) { Feedback.email(context, note); onDismiss() }
                Choice(Icons.Outlined.Share, stringResource(R.string.feedback_other)) { Feedback.share(context, note); onDismiss() }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Choice(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
