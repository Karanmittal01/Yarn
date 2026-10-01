package app.yarn.messaging

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.content.FileProvider
import app.yarn.ai.IntelligenceEngine
import app.yarn.data.db.AttachmentEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageKind
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.SettingsRepository
import app.yarn.mms.OutgoingPart
import app.yarn.mms.Pdu
import app.yarn.mms.PduComposer
import app.yarn.mms.PduParser
import app.yarn.mms.SendRequest
import app.yarn.notifications.Notifier
import app.yarn.receivers.MmsSentReceiver
import app.yarn.receivers.ScheduledSendReceiver
import app.yarn.receivers.SmsDeliveredReceiver
import app.yarn.receivers.SmsSentReceiver
import app.yarn.telephony.PartToStore
import app.yarn.telephony.PhoneNumbers
import app.yarn.telephony.SimManager
import app.yarn.telephony.TelephonyStore
import app.yarn.work.SendWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/**
 * Durable outgoing queue. Every message is persisted before any radio work, so nothing is lost
 * to process death, airplane mode or reboots. Sending is idempotent per attempt: callbacks carry
 * the attempt number and stale ones are ignored.
 */
class MessageSender(
    private val context: Context,
    private val db: YarnDatabase,
    private val store: TelephonyStore,
    private val sims: SimManager,
    private val settings: SettingsRepository,
    private val engine: IntelligenceEngine,
    private val notifier: Notifier,
    private val providerLock: Mutex,
    private val scope: CoroutineScope,
) {
    private val callbackLock = Mutex()

    /** Creates (or reuses) the OS thread for these recipients and our conversation row. */
    suspend fun conversationFor(addresses: List<String>, conversations: ConversationWriter): Long {
        val iso = PhoneNumbers.countryIso(context)
        val normalized = addresses.map { PhoneNumbers.normalize(it, iso) }.filter { it.isNotBlank() }.distinct()
        require(normalized.isNotEmpty()) { "No recipients" }
        val threadId = providerLock.withLock { store.threadIdFor(normalized.toSet()) }
        conversations.ensure(threadId, normalized)
        return threadId
    }

    /**
     * Queues a message. Returns the new message id. Text-only messages to one recipient go as
     * SMS; attachments, subjects and group conversations (when group MMS is on) go as MMS.
     */
    suspend fun queue(
        conversationId: Long,
        text: String,
        attachments: List<AttachmentDraft>,
        subId: Int,
        scheduledAt: Long? = null,
        subject: String? = null,
    ): Long {
        val conversation = db.conversations().get(conversationId) ?: error("Unknown conversation $conversationId")
        val s = settings.current()
        val resolvedSub = sims.resolveSubId(subId)
        val config = sims.mmsConfig(resolvedSub)
        val recipients = conversation.addresses
        val needsMms = attachments.isNotEmpty() || !subject.isNullOrBlank() ||
            (recipients.size > 1 && s.groupMms && config.groupMmsEnabled) ||
            recipients.any { PhoneNumbers.isEmail(it) } ||
            (config.smsToMmsTextThreshold > 0 && SmsMessage.calculateLength(text, false)[0] > config.smsToMmsTextThreshold)
        val now = System.currentTimeMillis()
        val status = when {
            scheduledAt != null && scheduledAt > now -> MessageStatus.SCHEDULED
            s.sendDelaySeconds > 0 -> MessageStatus.DELAYED
            else -> MessageStatus.QUEUED
        }
        val message = MessageEntity(
            conversationId = conversationId,
            kind = if (needsMms) MessageKind.MMS else MessageKind.SMS,
            address = recipients.joinToString(","),
            body = text,
            subject = subject,
            date = if (status == MessageStatus.SCHEDULED) scheduledAt!! else now,
            outgoing = true,
            status = status,
            scheduledAt = scheduledAt ?: 0,
            nextAttemptAt = if (status == MessageStatus.DELAYED) now + s.sendDelaySeconds * 1000L else 0,
            subId = resolvedSub,
            hasAttachments = attachments.isNotEmpty(),
            deliveryReport = s.deliveryReports && config.deliveryReportsSupported,
            sendAsMms = needsMms,
            category = conversation.category,
        )
        val id = db.messages().insert(message)
        if (attachments.isNotEmpty()) {
            db.messages().insertAttachments(attachments.map { AttachmentEntity(messageId = id, mimeType = it.mimeType, uri = it.uri.toString(), fileName = it.fileName, size = it.size) })
        }
        engine.analyzeAndStore(id)
        db.conversations().setDraft(conversationId, null, now)
        if (conversation.archived) db.conversations().setArchived(listOf(conversationId), false)
        db.conversations().refresh(conversationId)
        kick(status, message.nextAttemptAt, scheduledAt)
        return id
    }

    private suspend fun kick(status: Int, nextAttemptAt: Long, scheduledAt: Long?) {
        when (status) {
            MessageStatus.SCHEDULED -> scheduleAlarm()
            MessageStatus.DELAYED -> {
                // In-process timer for the undo window, with WorkManager as a durable fallback.
                scope.launch { delay(nextAttemptAt - System.currentTimeMillis()); SendWorker.enqueue(context) }
                scope.launch { reschedule() }
            }
            else -> SendWorker.enqueue(context)
        }
    }

    /** Undo/cancel a message that hasn't reached the radio yet. Returns it for re-editing. */
    suspend fun cancel(messageId: Long): MessageEntity? {
        val m = db.messages().get(messageId) ?: return null
        if (m.status !in listOf(MessageStatus.DELAYED, MessageStatus.SCHEDULED, MessageStatus.QUEUED, MessageStatus.WAITING_FOR_SERVICE, MessageStatus.FAILED)) return null
        providerLock.withLock {
            m.providerId?.let { if (m.kind == MessageKind.SMS) store.deleteSms(it) else store.deleteMms(it) }
            m.extraProviderIds?.split(',')?.mapNotNull { it.toLongOrNull() }?.forEach { store.deleteSms(it) }
        }
        db.messages().delete(listOf(messageId))
        db.conversations().refresh(m.conversationId)
        notifier.cancelFailed(messageId)
        scheduleAlarm()
        return m
    }

    suspend fun retry(messageId: Long) {
        val m = db.messages().get(messageId) ?: return
        if (!m.outgoing) return
        db.messages().update(m.copy(status = MessageStatus.QUEUED, attempts = 0, nextAttemptAt = 0, errorCode = 0))
        db.conversations().refresh(m.conversationId)
        notifier.cancelFailed(messageId)
        SendWorker.enqueue(context)
    }

    /** Sends everything that is due. Returns the next time the queue needs attention. */
    suspend fun processQueue(): Long? {
        recoverStuck()
        val due = db.messages().dueOutgoing(System.currentTimeMillis())
        for (m in due) {
            try {
                if (m.sendAsMms) sendMms(m) else sendSms(m)
            } catch (e: Exception) {
                Log.e(TAG, "Send failed for ${m.id}", e)
                finishAttempt(m.id, m.attempts + 1, SendErrors.Action.RETRY, -1, e.message ?: "Unexpected error", isMms = m.sendAsMms)
            }
        }
        scheduleAlarm()
        return db.messages().nextDueTime()
    }

    /** Messages stuck in SENDING (no radio callback, e.g. process killed) become failed so the user decides. */
    private suspend fun recoverStuck() {
        for (m in db.messages().stuckSending(System.currentTimeMillis() - 10 * 60_000L)) {
            if (System.currentTimeMillis() - maxOf(m.nextAttemptAt, m.date) < 10 * 60_000L) continue
            db.messages().update(m.copy(status = MessageStatus.FAILED, errorCode = -2))
            db.conversations().refresh(m.conversationId)
            notifier.notifyFailed(m, conversationTitle(m.conversationId), "Sending status unknown. Tap to check and retry.")
        }
    }

    private suspend fun conversationTitle(id: Long) = db.conversations().get(id)?.title ?: "conversation"

    // ---- SMS ----------------------------------------------------------------------------------

    private suspend fun sendSms(m: MessageEntity) {
        val recipients = m.address.split(',').filter { it.isNotBlank() }
        val subId = sims.resolveSubId(m.subId)
        val sms = sims.smsManager(subId)
        val parts = sms.divideMessage(m.body.ifEmpty { " " })
        val attempt = m.attempts + 1
        val now = System.currentTimeMillis()

        val providerIds = providerLock.withLock {
            val existing = listOfNotNull(m.providerId) + m.extraProviderIds.orEmpty().split(',').mapNotNull { it.toLongOrNull() }
            if (existing.size == recipients.size) {
                existing.forEach { store.updateSmsType(it, Telephony.Sms.MESSAGE_TYPE_OUTBOX) }
                existing
            } else {
                recipients.map { r -> store.insertOutgoingSms(r, m.body, now, subId, m.conversationId, m.deliveryReport) ?: -1L }
            }
        }
        db.messages().update(
            m.copy(
                status = MessageStatus.SENDING, attempts = attempt, subId = subId, date = if (m.status == MessageStatus.SCHEDULED || m.status == MessageStatus.DELAYED) now else m.date,
                providerId = providerIds.firstOrNull()?.takeIf { it > 0 }, extraProviderIds = providerIds.drop(1).filter { it > 0 }.joinToString(",").ifEmpty { null },
                partsTotal = parts.size * recipients.size, partsSent = 0, partsFailed = 0, partsDelivered = 0, nextAttemptAt = now,
            ),
        )
        db.conversations().refresh(m.conversationId)

        var failedImmediately = 0
        var lastError = 0
        recipients.forEachIndexed { r, recipient ->
            val sent = ArrayList<PendingIntent>()
            val delivered = ArrayList<PendingIntent>()
            parts.indices.forEach { p ->
                sent += smsIntent(SmsSentReceiver::class.java, m.id, attempt, r, p, providerIds.getOrNull(r) ?: -1)
                if (m.deliveryReport) delivered += smsIntent(SmsDeliveredReceiver::class.java, m.id, attempt, r, p, providerIds.getOrNull(r) ?: -1)
            }
            try {
                sms.sendMultipartTextMessage(recipient, null, parts, sent, if (m.deliveryReport) delivered else null)
            } catch (e: Exception) {
                Log.w(TAG, "sendMultipartTextMessage threw for message ${m.id}", e)
                failedImmediately += parts.size
                lastError = SmsManager.RESULT_ERROR_GENERIC_FAILURE
            }
        }
        if (failedImmediately > 0) {
            repeat(failedImmediately) { onSmsPartResult(m.id, attempt, lastError, -1) }
        }
    }

    private fun smsIntent(cls: Class<*>, messageId: Long, attempt: Int, recipient: Int, part: Int, providerId: Long): PendingIntent {
        val intent = Intent(context, cls)
            .setData(Uri.parse("yarn://sms/${cls.simpleName}/$messageId/$attempt/$recipient/$part"))
            .putExtra(EXTRA_MESSAGE_ID, messageId)
            .putExtra(EXTRA_ATTEMPT, attempt)
            .putExtra(EXTRA_PROVIDER_ID, providerId)
        // Mutable: the telephony stack attaches error codes / status-report PDUs as extras.
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    suspend fun onSmsPartResult(messageId: Long, attempt: Int, resultCode: Int, errorCode: Int) = callbackLock.withLock {
        val m = db.messages().get(messageId) ?: return@withLock
        if (m.status != MessageStatus.SENDING || m.attempts != attempt) return@withLock
        val ok = resultCode == Activity.RESULT_OK
        val updated = m.copy(
            partsSent = m.partsSent + if (ok) 1 else 0,
            partsFailed = m.partsFailed + if (ok) 0 else 1,
            errorCode = if (ok) m.errorCode else resultCode,
        )
        db.messages().update(updated)
        if (updated.partsSent + updated.partsFailed < updated.partsTotal) return@withLock
        if (updated.partsFailed == 0) {
            finishSuccess(updated)
        } else {
            val code = updated.errorCode
            finishAttempt(messageId, attempt, SendErrors.smsAction(code), code, SendErrors.smsMessage(code), isMms = false)
        }
    }

    suspend fun onSmsDelivered(messageId: Long, attempt: Int, providerId: Long, pdu: ByteArray?, format: String?) = callbackLock.withLock {
        val m = db.messages().get(messageId) ?: return@withLock
        if (m.attempts != attempt) return@withLock
        val status = runCatching { SmsMessage.createFromPdu(pdu, format).status }.getOrDefault(0)
        // TP-Status: 0x00-0x1F delivered, 0x20-0x3F still trying, >= 0x40 permanent failure.
        when {
            status < 0x20 -> {
                val delivered = m.partsDelivered + 1
                val done = delivered >= m.partsTotal
                db.messages().update(m.copy(partsDelivered = delivered, status = if (done && m.status == MessageStatus.SENT) MessageStatus.DELIVERED else m.status))
                if (done && providerId > 0) providerLock.withLock { store.updateSmsDeliveryStatus(providerId, Telephony.Sms.STATUS_COMPLETE) }
            }
            status >= 0x40 -> {
                if (providerId > 0) providerLock.withLock { store.updateSmsDeliveryStatus(providerId, Telephony.Sms.STATUS_FAILED) }
                db.messages().update(m.copy(status = MessageStatus.FAILED, errorCode = status))
                notifier.notifyFailed(m, conversationTitle(m.conversationId), "The network reported the message was not delivered.")
            }
        }
        db.conversations().refresh(m.conversationId)
    }

    private suspend fun finishSuccess(m: MessageEntity) {
        val sentAt = System.currentTimeMillis()
        db.messages().update(m.copy(status = MessageStatus.SENT, dateSent = sentAt, errorCode = 0))
        providerLock.withLock {
            if (m.kind == MessageKind.SMS) {
                (listOfNotNull(m.providerId) + m.extraProviderIds.orEmpty().split(',').mapNotNull { it.toLongOrNull() })
                    .forEach { store.updateSmsType(it, Telephony.Sms.MESSAGE_TYPE_SENT) }
            }
        }
        db.conversations().refresh(m.conversationId)
        notifier.cancelFailed(m.id)
    }

    private suspend fun finishAttempt(messageId: Long, attempt: Int, action: SendErrors.Action, code: Int, reason: String, isMms: Boolean) {
        val m = db.messages().get(messageId) ?: return
        val now = System.currentTimeMillis()
        val waitedTooLong = now - m.date > SendErrors.MAX_WAIT_FOR_SERVICE_MS
        val (status, next) = when {
            action == SendErrors.Action.WAIT_FOR_SERVICE && !waitedTooLong -> MessageStatus.WAITING_FOR_SERVICE to now + SendErrors.backoffMillis(attempt).coerceAtMost(15 * 60_000L)
            action == SendErrors.Action.RETRY && attempt < SendErrors.MAX_ATTEMPTS -> MessageStatus.QUEUED to now + SendErrors.backoffMillis(attempt)
            else -> MessageStatus.FAILED to 0L
        }
        // Waiting for signal doesn't burn retry budget.
        val attempts = if (status == MessageStatus.WAITING_FOR_SERVICE) (attempt - 1).coerceAtLeast(0) else attempt
        db.messages().update(m.copy(status = status, nextAttemptAt = next, errorCode = code, attempts = attempts))
        if (status == MessageStatus.FAILED) {
            providerLock.withLock {
                if (!isMms) {
                    (listOfNotNull(m.providerId) + m.extraProviderIds.orEmpty().split(',').mapNotNull { it.toLongOrNull() })
                        .forEach { store.updateSmsType(it, Telephony.Sms.MESSAGE_TYPE_FAILED, code) }
                } else {
                    m.providerId?.let { store.updateMmsBox(it, Telephony.Mms.MESSAGE_BOX_FAILED) }
                }
            }
            notifier.notifyFailed(m, conversationTitle(m.conversationId), reason)
        } else {
            reschedule()
        }
        db.conversations().refresh(m.conversationId)
    }

    /** Arms the delayed queue run for the earliest pending retry/undo deadline. */
    suspend fun reschedule() {
        val next = db.messages().nextDueTime() ?: return
        SendWorker.enqueue(context, delayMillis = (next - System.currentTimeMillis()).coerceAtLeast(0))
    }

    // ---- MMS ----------------------------------------------------------------------------------

    private suspend fun sendMms(m: MessageEntity) {
        val subId = sims.resolveSubId(m.subId)
        val config = sims.mmsConfig(subId)
        val attempt = m.attempts + 1
        if (!config.mmsEnabled) {
            db.messages().update(m.copy(attempts = attempt, status = MessageStatus.SENDING))
            finishAttempt(m.id, attempt, SendErrors.Action.FAIL, SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER, SendErrors.mmsMessage(SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER), true)
            return
        }
        val recipients = m.address.split(',').filter { it.isNotBlank() }
        val attachments = db.messages().attachments(m.id)
        val textBytes = m.body.toByteArray().size
        val budget = (config.maxMessageSize - 8 * 1024 - textBytes).coerceAtLeast(16 * 1024)
        val perAttachment = if (attachments.isEmpty()) budget else budget / attachments.size

        val parts = ArrayList<OutgoingPart>()
        attachments.forEachIndexed { i, a ->
            val uri = Uri.parse(a.uri)
            val name = (a.fileName ?: "part$i").let { if (parts.any { p -> p.fileName == it }) "${i}_$it" else it }
            val (bytes, type) = if (a.mimeType.startsWith("image/") && a.mimeType != "image/gif") {
                MediaTools.compressImage(context, uri, a.mimeType, perAttachment, config.maxImageWidth, config.maxImageHeight)
                    ?: run {
                        failTooLarge(m, attempt, config.maxMessageSize); return
                    }
            } else {
                val data = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?: run { finishAttempt(m.id, attempt, SendErrors.Action.FAIL, -3, "An attachment is no longer available.", true); return }
                if (data.size > perAttachment) { failTooLarge(m, attempt, config.maxMessageSize); return }
                data to a.mimeType
            }
            val finalName = if (type == "image/jpeg" && !name.endsWith(".jpg", true) && !name.endsWith(".jpeg", true)) name.substringBeforeLast('.') + ".jpg" else name
            parts += OutgoingPart(type, bytes, finalName)
        }
        if (m.body.isNotBlank()) parts += OutgoingPart.text(m.body)

        val now = System.currentTimeMillis()
        val pdu = PduComposer.sendReq(
            SendRequest(
                recipients = recipients, parts = parts, transactionId = "T" + UUID.randomUUID().toString().replace("-", "").take(20),
                subject = m.subject, dateEpochSeconds = now / 1000, deliveryReport = m.deliveryReport,
            ),
        )
        val providerId = providerLock.withLock {
            m.providerId?.also { store.updateMmsBox(it, Telephony.Mms.MESSAGE_BOX_OUTBOX) } ?: store.persistMms(
                box = Telephony.Mms.MESSAGE_BOX_OUTBOX, threadId = m.conversationId, from = null, recipients = recipients,
                subject = m.subject, dateMillis = now, parts = parts.map { PartToStore(it.contentType, it.data, it.fileName, it.contentId, null) },
                subId = subId, read = true, deliveryReport = m.deliveryReport,
            )?.id
        }
        val file = File(MediaTools.mmsDir(context), "send-${m.id}-$attempt.pdu").apply { writeBytes(pdu) }
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(context, MmsSentReceiver::class.java)
            .setData(Uri.parse("yarn://mms/sent/${m.id}/$attempt"))
            .putExtra(EXTRA_MESSAGE_ID, m.id)
            .putExtra(EXTRA_ATTEMPT, attempt)
            .putExtra(EXTRA_FILE, file.absolutePath)
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        db.messages().update(
            m.copy(
                status = MessageStatus.SENDING, attempts = attempt, subId = subId, providerId = providerId, partsTotal = 1,
                partsSent = 0, partsFailed = 0, nextAttemptAt = now,
                date = if (m.status == MessageStatus.SCHEDULED || m.status == MessageStatus.DELAYED) now else m.date,
            ),
        )
        db.conversations().refresh(m.conversationId)
        try {
            sims.smsManager(subId).sendMultimediaMessage(context, contentUri, null, null, pi)
        } catch (e: Exception) {
            Log.w(TAG, "sendMultimediaMessage threw", e)
            file.delete()
            finishAttempt(m.id, attempt, SendErrors.Action.RETRY, SmsManager.MMS_ERROR_UNSPECIFIED, SendErrors.mmsMessage(SmsManager.MMS_ERROR_UNSPECIFIED), true)
        }
    }

    private suspend fun failTooLarge(m: MessageEntity, attempt: Int, limit: Int) {
        db.messages().update(m.copy(attempts = attempt, status = MessageStatus.SENDING))
        finishAttempt(m.id, attempt, SendErrors.Action.FAIL, SendErrors.MMS_TOO_LARGE, "Attachment is larger than your carrier's ${limit / 1024} KB MMS limit.", true)
    }

    suspend fun onMmsSent(messageId: Long, attempt: Int, resultCode: Int, response: ByteArray?, filePath: String?) = callbackLock.withLock {
        filePath?.let { File(it).delete() }
        val m = db.messages().get(messageId) ?: return@withLock
        if (m.status != MessageStatus.SENDING || m.attempts != attempt) return@withLock
        if (resultCode == Activity.RESULT_OK) {
            val conf = response?.let { runCatching { PduParser.parse(it) as? Pdu.SendConf }.getOrNull() }
            if (conf != null && !conf.isSuccess) {
                val status = conf.responseStatus ?: 0
                // 0xC0-0xDF transient, 0xE0-0xFF permanent (OMA-TS-MMS-ENC 7.3.48).
                val action = if (status in 0xC0..0xDF) SendErrors.Action.RETRY else SendErrors.Action.FAIL
                finishAttempt(messageId, attempt, action, status, conf.responseText ?: "The carrier rejected the message.", true)
                return@withLock
            }
            db.messages().update(m.copy(status = MessageStatus.SENT, dateSent = System.currentTimeMillis(), mmsMessageId = conf?.messageId, errorCode = 0))
            providerLock.withLock { m.providerId?.let { store.updateMmsBox(it, Telephony.Mms.MESSAGE_BOX_SENT, conf?.messageId) } }
            // Our copies of the attachments are no longer needed once they are in the system store.
            db.conversations().refresh(m.conversationId)
            notifier.cancelFailed(m.id)
        } else {
            finishAttempt(messageId, attempt, SendErrors.mmsAction(resultCode), resultCode, SendErrors.mmsMessage(resultCode), true)
        }
    }

    suspend fun onMmsDeliveryReport(mmsMessageId: String?, delivered: Boolean) {
        val mid = mmsMessageId ?: return
        val m = db.messages().byMmsMessageId(mid) ?: return
        if (delivered && m.status == MessageStatus.SENT) {
            db.messages().update(m.copy(status = MessageStatus.DELIVERED))
            db.conversations().refresh(m.conversationId)
        }
    }

    // ---- scheduling ---------------------------------------------------------------------------

    /** Arms one alarm for the earliest scheduled message. */
    suspend fun scheduleAlarm() {
        val next = db.messages().scheduled().minOfOrNull { it.scheduledAt }
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, ScheduledSendReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (next == null) { am.cancel(pi); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    interface ConversationWriter {
        suspend fun ensure(threadId: Long, addresses: List<String>)
    }

    companion object {
        private const val TAG = "MessageSender"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_PROVIDER_ID = "provider_id"
        const val EXTRA_FILE = "file"
    }
}
