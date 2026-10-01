package app.yarn.receivers

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import app.yarn.AppContainer
import app.yarn.YarnApplication
import app.yarn.messaging.MessageSender
import app.yarn.work.SendWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal val Context.container: AppContainer get() = (applicationContext as YarnApplication).container

/** Runs [block] off the main thread while keeping the broadcast alive (max ~9s). */
internal fun BroadcastReceiver.async(context: Context, block: suspend AppContainer.() -> Unit) {
    val pending = goAsync()
    val c = context.container
    c.scope.launch {
        try {
            withTimeoutOrNull(9_000) { c.block() }
        } catch (e: Exception) {
            Log.e("YarnReceiver", "Receiver work failed", e)
        } finally {
            pending.finish()
        }
    }
}

private fun Intent.subId(): Int = getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, getIntExtra("subscription", -1))

/** SMS_DELIVER: only delivered to the default SMS app, which must store the message. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val subId = intent.subId()
        async(context) { incoming.onSms(messages, subId) }
    }
}

/** WAP_PUSH_DELIVER: MMS notifications and delivery reports. */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type != "application/vnd.wap.mms-message") return
        val data = intent.getByteArrayExtra("data") ?: return
        val subId = intent.subId()
        async(context) { incoming.onWapPush(data, subId) }
    }
}

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(MessageSender.EXTRA_MESSAGE_ID, -1)
        val attempt = intent.getIntExtra(MessageSender.EXTRA_ATTEMPT, -1)
        val code = resultCode
        val error = intent.getIntExtra("errorCode", 0)
        async(context) { sender.onSmsPartResult(id, attempt, code, error) }
    }
}

class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(MessageSender.EXTRA_MESSAGE_ID, -1)
        val attempt = intent.getIntExtra(MessageSender.EXTRA_ATTEMPT, -1)
        val providerId = intent.getLongExtra(MessageSender.EXTRA_PROVIDER_ID, -1)
        val pdu = intent.getByteArrayExtra("pdu")
        val format = intent.getStringExtra("format")
        async(context) { sender.onSmsDelivered(id, attempt, providerId, pdu, format) }
    }
}

class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(MessageSender.EXTRA_MESSAGE_ID, -1)
        val attempt = intent.getIntExtra(MessageSender.EXTRA_ATTEMPT, -1)
        val file = intent.getStringExtra(MessageSender.EXTRA_FILE)
        val data = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
        val code = resultCode
        async(context) { sender.onMmsSent(id, attempt, code, data, file) }
    }
}

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(MessageSender.EXTRA_MESSAGE_ID, -1)
        val attempt = intent.getIntExtra(MessageSender.EXTRA_ATTEMPT, -1)
        val file = intent.getStringExtra(MessageSender.EXTRA_FILE)
        val code = resultCode
        async(context) { incoming.onDownloaded(id, attempt, code, file) }
    }
}

/** Fires at the time of the earliest scheduled message. */
class ScheduledSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SendWorker.enqueue(context)
    }
}

/** Re-arms alarms and resumes the queue after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> async(context) {
                sender.scheduleAlarm()
                SendWorker.enqueue(context)
                incoming.resumePendingDownloads()
            }
        }
    }
}

/**
 * Required by the SMS role: lets the dialer's "respond via message" send without UI
 * (e.g. declining a call with a quick text).
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != "android.intent.action.RESPOND_VIA_MESSAGE") { stopSelf(startId); return START_NOT_STICKY }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        val recipients = intent.data?.schemeSpecificPart?.substringBefore('?')?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }
        if (text == null || recipients.isNullOrEmpty()) { stopSelf(startId); return START_NOT_STICKY }
        val c = container
        c.scope.launch {
            try {
                val conversationId = c.sender.conversationFor(recipients, c.conversations)
                val sub = c.db.conversations().get(conversationId)?.preferredSubId ?: -1
                c.sender.queue(conversationId, text, emptyList(), sub)
            } catch (e: Exception) {
                Log.e("HeadlessSmsSend", "Failed to queue quick response", e)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
}
