package app.yarn.notifications

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.RemoteInput
import app.yarn.receivers.async

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1)
        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
                if (text.isNullOrEmpty()) return
                async(context) {
                    val sub = db.conversations().get(conversationId)?.preferredSubId ?: -1
                    sender.queue(conversationId, text, emptyList(), sub)
                    conversations.markRead(listOf(conversationId))
                }
            }
            ACTION_MARK_READ -> async(context) { conversations.markRead(listOf(conversationId)) }
            ACTION_COPY_OTP -> {
                val code = intent.getStringExtra(EXTRA_TEXT) ?: return
                copySensitive(context, code)
                confirmCopy(context)
                async(context) { conversations.markRead(listOf(conversationId)) }
            }
            ACTION_COPY_DELETE_OTP -> {
                val code = intent.getStringExtra(EXTRA_TEXT) ?: return
                val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1)
                copySensitive(context, code)
                confirmCopy(context)
                async(context) {
                    conversations.markRead(listOf(conversationId))
                    if (messageId > 0) conversations.deleteMessages(listOf(messageId))
                    db.conversations().deleteIfEmpty(conversationId)
                    notifier.cancel(conversationId)
                }
            }
            ACTION_DELETE -> {
                val ids = intent.getLongArrayExtra(EXTRA_MESSAGE_IDS)?.toList().orEmpty()
                async(context) {
                    conversations.markRead(listOf(conversationId))
                    if (ids.isNotEmpty()) conversations.deleteMessages(ids)
                    db.conversations().deleteIfEmpty(conversationId)
                    notifier.cancel(conversationId)
                }
            }
            ACTION_RETRY -> {
                val id = intent.getLongExtra(EXTRA_MESSAGE_ID, -1)
                async(context) { sender.retry(id) }
            }
            ACTION_DOWNLOAD_MMS -> {
                val id = intent.getLongExtra(EXTRA_MESSAGE_ID, -1)
                async(context) { incoming.download(id) }
            }
            ACTION_DISMISSED -> Unit
        }
    }

    companion object {
        const val ACTION_REPLY = "app.yarn.action.REPLY"
        const val ACTION_MARK_READ = "app.yarn.action.MARK_READ"
        const val ACTION_COPY_OTP = "app.yarn.action.COPY_OTP"
        const val ACTION_RETRY = "app.yarn.action.RETRY"
        const val ACTION_DOWNLOAD_MMS = "app.yarn.action.DOWNLOAD_MMS"
        const val ACTION_DISMISSED = "app.yarn.action.DISMISSED"
        const val ACTION_COPY_DELETE_OTP = "app.yarn.action.COPY_DELETE_OTP"
        const val ACTION_DELETE = "app.yarn.action.DELETE"
        const val EXTRA_MESSAGE_IDS = "message_ids"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_TEXT = "text"
        const val KEY_REPLY = "reply"

        /** Android 13+ shows its own clipboard confirmation; older versions get a toast. */
        private fun confirmCopy(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                android.widget.Toast.makeText(context, "Code copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        /** Copies a code and flags it sensitive so it is hidden from clipboard previews (Android 13+). */
        fun copySensitive(context: Context, text: String) {
            val cm = context.getSystemService(ClipboardManager::class.java) ?: return
            val clip = ClipData.newPlainText("code", text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            } else {
                clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            }
            cm.setPrimaryClip(clip)
        }
    }
}
