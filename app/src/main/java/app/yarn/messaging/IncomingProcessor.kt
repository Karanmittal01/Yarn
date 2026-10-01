package app.yarn.messaging

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.content.FileProvider
import app.yarn.ai.IntelligenceEngine
import app.yarn.ai.IntelligenceEngine.Companion.toRow
import app.yarn.ai.MlEntityService
import app.yarn.data.db.AttachmentEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageKind
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.SettingsRepository
import app.yarn.data.repo.ConversationRepository
import app.yarn.mms.Pdu
import app.yarn.mms.PduComposer
import app.yarn.mms.PduParser
import app.yarn.notifications.Notifier
import app.yarn.receivers.MmsDownloadedReceiver
import app.yarn.telephony.PartToStore
import app.yarn.telephony.PhoneNumbers
import app.yarn.telephony.SimManager
import app.yarn.telephony.TelephonyStore
import app.yarn.work.MmsDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class IncomingProcessor(
    private val context: Context,
    private val db: YarnDatabase,
    private val store: TelephonyStore,
    private val sims: SimManager,
    private val settings: SettingsRepository,
    private val engine: IntelligenceEngine,
    private val mlEntities: MlEntityService,
    private val smartSorter: app.yarn.ai.SmartSorter,
    private val conversations: ConversationRepository,
    private val sender: MessageSender,
    private val notifier: Notifier,
    private val providerLock: Mutex,
    private val scope: CoroutineScope,
) {
    // ---- SMS ----------------------------------------------------------------------------------

    /** Handles one SMS_DELIVER broadcast (all PDUs belong to the same, possibly multipart, message). */
    suspend fun onSms(parts: Array<SmsMessage>, subId: Int) {
        if (parts.isEmpty()) return
        val first = parts.first()
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        if (body.isBlank() && (first.isMWISetMessage || first.isMWIClearMessage)) return // voicemail indicators
        if (first.messageClass == SmsMessage.MessageClass.CLASS_0) {
            notifier.notifyFlash(address, body)
            return
        }
        val block = conversations.blockDecision(address, body)
        if (block == ConversationRepository.Block.DROP) {
            Log.i(TAG, "Dropped SMS from blocked sender")
            return
        }
        val now = System.currentTimeMillis()
        val dateSent = first.timestampMillis.takeIf { it > 0 } ?: now
        // Provider row and our row are written under one lock so a concurrent import can't duplicate it.
        val (id, threadId) = providerLock.withLock {
            val inserted = store.insertIncomingSms(address, body, now, dateSent, subId, read = false)
            // Not the default SMS app (or provider unavailable): keep a local copy so nothing is lost.
            val threadId = inserted?.second ?: runCatching { store.threadIdFor(setOf(address)) }.getOrElse { -address.hashCode().toLong() }
            conversations.ensure(threadId, listOf(address))
            db.messages().insert(
                MessageEntity(
                    conversationId = threadId, kind = MessageKind.SMS, providerId = inserted?.first, address = address, body = body,
                    date = now, dateSent = dateSent, outgoing = false, status = MessageStatus.RECEIVED, subId = subId,
                    read = false, seen = false,
                ),
            ) to threadId
        }
        afterIncoming(id, threadId, forceSpam = block == ConversationRepository.Block.FILTER)
    }

    private suspend fun afterIncoming(messageId: Long, conversationId: Long, forceSpam: Boolean) {
        val conversation = db.conversations().get(conversationId)
        val ctx = conversations.priorityContext(conversationId)
        val updated = engine.analyzeAndStore(messageId, ctx) ?: return
        if (forceSpam) {
            db.messages().update(updated.copy(spamVerdict = "SPAM", spamReasons = "Matched one of your block filters"))
        }
        // Let Gemini Nano sort messages the rules weren't sure about, before notifying.
        kotlinx.coroutines.withTimeoutOrNull(3_000) { runCatching { smartSorter.sortOne(messageId) } }
        if (conversation != null && conversation.archived && !conversation.muted) {
            db.conversations().setArchived(listOf(conversationId), false)
        }
        db.conversations().refresh(conversationId)
        notifier.notifyConversation(conversationId)
        enrichWithMlKit(messageId)
    }

    /** Adds ML Kit entities (addresses, precise dates) in the background when the model exists. */
    fun enrichWithMlKit(messageId: Long) {
        scope.launch {
            val s = settings.current()
            if (!s.aiEnabled || !s.mlEntityExtraction) return@launch
            val m = db.messages().get(messageId) ?: return@launch
            if (m.body.isBlank()) return@launch
            val existing = db.messages().entities(messageId)
            val extra = mlEntities.extract(m.body, m.date).filter { e ->
                existing.none { it.start < e.end && e.start < it.end }
            }
            db.messages().replaceEntities(messageId, "mlkit", extra.map { it.toRow(messageId, "mlkit") })
        }
    }

    // ---- MMS ----------------------------------------------------------------------------------

    suspend fun onWapPush(pduBytes: ByteArray, subId: Int) {
        val pdu = runCatching { PduParser.parse(pduBytes) }.getOrElse {
            Log.w(TAG, "Unparseable WAP push", it); return
        }
        when (pdu) {
            is Pdu.NotificationInd -> onMmsNotification(pdu, subId)
            is Pdu.DeliveryInd -> sender.onMmsDeliveryReport(pdu.messageId, pdu.delivered)
            else -> Log.i(TAG, "Ignoring WAP push type ${pdu.messageType}")
        }
    }

    private suspend fun onMmsNotification(n: Pdu.NotificationInd, subId: Int) {
        if (db.messages().byContentLocation(n.contentLocation) != null) return // duplicate push
        val from = n.from ?: "Unknown sender"
        if (conversations.blockDecision(from, n.subject.orEmpty()) == ConversationRepository.Block.DROP) return
        val threadId = runCatching { providerLock.withLock { store.threadIdFor(setOf(from)) } }.getOrElse { -from.hashCode().toLong() }
        conversations.ensure(threadId, listOf(from))
        val now = System.currentTimeMillis()
        val id = db.messages().insert(
            MessageEntity(
                conversationId = threadId, kind = MessageKind.MMS, address = from, subject = n.subject, date = now,
                outgoing = false, status = MessageStatus.PENDING_DOWNLOAD, subId = subId, read = false, seen = false,
                mmsContentLocation = n.contentLocation, mmsTransactionId = n.transactionId,
                mmsExpiry = n.expiry?.toEpochSeconds(now / 1000) ?: 0, mmsSize = n.messageSize ?: 0, analyzed = true,
            ),
        )
        db.conversations().refresh(threadId)
        val s = settings.current()
        val roaming = sims.isRoaming(subId)
        if (s.autoDownloadMms && (!roaming || s.autoDownloadMmsRoaming)) {
            download(id)
        } else {
            val m = db.messages().get(id) ?: return
            notifier.notifyMmsPending(m, conversations.titleFor(threadId), if (roaming) "Not downloaded while roaming. Tap to download." else "Tap to download.")
        }
    }

    suspend fun download(messageId: Long) {
        val m = db.messages().get(messageId) ?: return
        val location = m.mmsContentLocation ?: return
        if (m.mmsExpiry > 0 && System.currentTimeMillis() / 1000 > m.mmsExpiry) {
            db.messages().update(m.copy(status = MessageStatus.EXPIRED))
            db.conversations().refresh(m.conversationId)
            return
        }
        val attempt = m.attempts + 1
        db.messages().update(m.copy(status = MessageStatus.DOWNLOADING, attempts = attempt))
        db.conversations().refresh(m.conversationId)
        notifier.cancelMmsPending(messageId)
        val file = File(MediaTools.mmsDir(context), "dl-$messageId-$attempt.pdu").apply { delete(); createNewFile() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(context, MmsDownloadedReceiver::class.java)
            .setData(Uri.parse("yarn://mms/downloaded/$messageId/$attempt"))
            .putExtra(MessageSender.EXTRA_MESSAGE_ID, messageId)
            .putExtra(MessageSender.EXTRA_ATTEMPT, attempt)
            .putExtra(MessageSender.EXTRA_FILE, file.absolutePath)
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        try {
            sims.smsManager(sims.resolveSubId(m.subId)).downloadMultimediaMessage(context, location, uri, null, pi)
        } catch (e: Exception) {
            Log.w(TAG, "downloadMultimediaMessage threw", e)
            onDownloadFailed(messageId, attempt, -1)
        }
    }

    suspend fun onDownloaded(messageId: Long, attempt: Int, resultCode: Int, filePath: String?) {
        val file = filePath?.let(::File)
        try {
            if (resultCode != Activity.RESULT_OK || file == null || file.length() == 0L) {
                onDownloadFailed(messageId, attempt, resultCode)
                return
            }
            val conf = runCatching { PduParser.parse(file.readBytes()) as? Pdu.RetrieveConf }.getOrNull()
            if (conf == null || !conf.isSuccess) {
                onDownloadFailed(messageId, attempt, conf?.retrieveStatus ?: -4)
                return
            }
            persistRetrieved(messageId, conf)
        } finally {
            file?.delete()
        }
    }

    private suspend fun persistRetrieved(messageId: Long, conf: Pdu.RetrieveConf) {
        val m = db.messages().get(messageId) ?: return
        val iso = PhoneNumbers.countryIso(context)
        val own = sims.ownNumbers()
        val from = conf.from ?: m.address
        val recipients = (conf.to + conf.cc)
            .filter { r -> own.none { PhoneNumbers.same(it, r, iso) } }
            .filter { r -> !PhoneNumbers.same(r, from, iso) }
        // With one "To" and no known own number, that address is us: treat it as a 1:1 message.
        val others = if (own.isEmpty() && conf.to.size == 1 && conf.cc.isEmpty()) emptyList() else recipients
        val participants = (listOf(from) + others).distinct()
        val date = conf.dateEpochSeconds?.times(1000) ?: m.date
        val parts = conf.parts.map(PartToStore::from)

        val text = conf.parts.filter { it.isText }.joinToString("\n") { it.text().trim() }.trim()
        val threadId = providerLock.withLock {
            val threadId = runCatching { store.threadIdFor(participants.toSet()) }.getOrDefault(m.conversationId)
            val saved = store.persistMms(
                box = Telephony.Mms.MESSAGE_BOX_INBOX, threadId = threadId, from = from, recipients = others,
                subject = conf.subject, dateMillis = date, parts = parts, subId = m.subId, read = false,
                messageId = conf.messageId, transactionId = conf.transactionId ?: m.mmsTransactionId, contentLocation = m.mmsContentLocation,
            )
            conversations.ensure(threadId, participants)
            val attachments = saved?.partUris?.filter { (p, _) -> !p.isText && !p.isSmil }?.map { (p, uri) ->
                AttachmentEntity(messageId = messageId, mimeType = p.contentType, uri = uri.toString(), fileName = p.name, size = p.data.size.toLong())
            } ?: conf.parts.filter { !it.isText && !it.isSmil }.mapIndexedNotNull { i, p ->
                // Provider write failed: keep the media in private storage so the user still sees it.
                val f = File(MediaTools.outboxDir(context), "in-$messageId-$i-${p.displayName ?: "part"}".replace(Regex("[^A-Za-z0-9._-]"), "_"))
                runCatching { f.writeBytes(p.data) }.getOrNull() ?: return@mapIndexedNotNull null
                AttachmentEntity(messageId = messageId, mimeType = p.contentType, uri = Uri.fromFile(f).toString(), fileName = p.displayName, size = p.data.size.toLong())
            }
            db.messages().deleteAttachments(messageId)
            if (attachments.isNotEmpty()) db.messages().insertAttachments(attachments)
            db.messages().update(
                m.copy(
                    conversationId = threadId, providerId = saved?.id, address = from, body = text, subject = conf.subject,
                    date = date, status = MessageStatus.RECEIVED, hasAttachments = attachments.isNotEmpty(), errorCode = 0,
                    mmsMessageId = conf.messageId, analyzed = false,
                ),
            )
            threadId
        }
        if (threadId != m.conversationId) {
            db.conversations().refresh(m.conversationId)
            db.conversations().deleteIfEmpty(m.conversationId)
        }
        acknowledge(conf.transactionId ?: m.mmsTransactionId, m.subId)
        afterIncoming(messageId, threadId, forceSpam = conversations.blockDecision(from, text) == ConversationRepository.Block.FILTER)
    }

    private suspend fun onDownloadFailed(messageId: Long, attempt: Int, code: Int) {
        val m = db.messages().get(messageId) ?: return
        val expired = m.mmsExpiry > 0 && System.currentTimeMillis() / 1000 > m.mmsExpiry
        val status = if (expired) MessageStatus.EXPIRED else MessageStatus.DOWNLOAD_FAILED
        db.messages().update(m.copy(status = status, errorCode = code))
        db.conversations().refresh(m.conversationId)
        if (!expired && attempt < 3 && SendErrors.mmsAction(code) != SendErrors.Action.FAIL) {
            MmsDownloadWorker.enqueue(context, messageId, SendErrors.backoffMillis(attempt + 1))
        } else if (!expired) {
            notifier.notifyMmsPending(m, conversations.titleFor(m.conversationId), SendErrors.mmsMessage(code))
        }
    }

    /** M-Acknowledge.ind so the MMSC stops re-sending the notification. Best effort. */
    private fun acknowledge(transactionId: String?, subId: Int) {
        val tx = transactionId ?: return
        runCatching {
            val file = File(MediaTools.mmsDir(context), "ack-$tx.pdu".replace(Regex("[^A-Za-z0-9._-]"), "_")).apply { writeBytes(PduComposer.acknowledgeInd(tx)) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            sims.smsManager(sims.resolveSubId(subId)).sendMultimediaMessage(context, uri, null, null, null)
        }.onFailure { Log.w(TAG, "Acknowledge failed", it) }
    }

    /** Restarts downloads that were interrupted (e.g. by a reboot). */
    suspend fun resumePendingDownloads() {
        val s = settings.current()
        if (!s.autoDownloadMms) return
        for (m in db.messages().pendingDownloads()) {
            if (m.status == MessageStatus.PENDING_DOWNLOAD && m.attempts == 0) download(m.id)
        }
    }

    companion object {
        private const val TAG = "IncomingProcessor"
    }
}
