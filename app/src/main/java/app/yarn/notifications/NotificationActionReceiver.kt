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
                async(context) { conversations.markRead(listOf(conversationId)) }
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
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_TEXT = "text"
        const val KEY_REPLY = "reply"

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
