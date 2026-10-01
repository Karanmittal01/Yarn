package app.yarn.data.repo

import android.content.Context
import android.provider.Telephony
import android.util.Log
import app.yarn.data.db.AttachmentEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageKind
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.YarnDatabase
import app.yarn.telephony.ProviderMms
import app.yarn.telephony.ProviderSms
import app.yarn.telephony.TelephonyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncProgress(val running: Boolean = false, val imported: Int = 0)

/**
 * Mirrors the system SMS/MMS store into our encrypted database. The first run imports the full
 * history; later runs are incremental (by provider row id) and cheap. Messages are inserted
 * first and analysed in a background pass so the inbox appears quickly even with 100k messages.
 */
class TelephonySync(
    private val context: Context,
    private val db: YarnDatabase,
    private val store: TelephonyStore,
    private val conversations: ConversationRepository,
    private val providerLock: Mutex,
) {
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()
    private val syncLock = Mutex()
    private val cursors = context.getSharedPreferences("sync_cursors", Context.MODE_PRIVATE)

    /** Forget import cursors, e.g. after restoring a backup into the system store. */
    fun resetCursors() = cursors.edit().clear().apply()

    suspend fun sync(): Int = syncLock.withLock {
        _progress.value = SyncProgress(true, 0)
        var imported = 0
        try {
            providerLock.withLock {
                val recipients = store.threadRecipients()
                val touched = HashSet<Long>()
                val buffer = ArrayList<MessageEntity>(BATCH)
                val mmsAttachments = HashMap<Long, List<AttachmentEntity>>()

                suspend fun flush() {
                    if (buffer.isEmpty()) return
                    buffer.map { it.conversationId }.distinct().forEach { tid ->
                        conversations.ensure(tid, recipients[tid] ?: buffer.first { it.conversationId == tid }.address.split(','))
                    }
                    val ids = db.messages().insertAll(buffer)
                    val attachments = ArrayList<AttachmentEntity>()
                    ids.forEachIndexed { i, id ->
                        if (id <= 0) return@forEachIndexed
                        val m = buffer[i]
                        if (m.kind == MessageKind.MMS) {
                            mmsAttachments[m.providerId]?.let { list -> attachments += list.map { it.copy(messageId = id) } }
                        }
                        imported++
                    }
                    if (attachments.isNotEmpty()) db.messages().insertAttachments(attachments)
                    touched += buffer.map { it.conversationId }
                    buffer.clear()
                    mmsAttachments.clear()
                    _progress.value = SyncProgress(true, imported)
                }

                // Cursors are tracked separately from our own inserts so history is never skipped
                // when new messages arrive before the first import finishes.
                var lastSms = cursors.getLong(KEY_SMS, 0L)
                store.forEachSms(lastSms) { row ->
                    buffer += row.toEntity()
                    lastSms = maxOf(lastSms, row.id)
                    if (buffer.size >= BATCH) { flush(); cursors.edit().putLong(KEY_SMS, lastSms).apply() }
                }
                flush()
                cursors.edit().putLong(KEY_SMS, lastSms).apply()

                var lastMms = cursors.getLong(KEY_MMS, 0L)
                val mmsRows = ArrayList<ProviderMms>()
                store.forEachMms(lastMms) { mmsRows += it }
                for (row in mmsRows) {
                    lastMms = maxOf(lastMms, row.id)
                    val entity = row.toEntity() ?: continue
                    mmsAttachments[row.id] = row.parts
                        .filter { !it.contentType.startsWith("text/") && !it.contentType.equals("application/smil", true) }
                        .map { AttachmentEntity(messageId = 0, mimeType = it.contentType, uri = TelephonyStore.partUri(it.id).toString(), fileName = it.name) }
                    buffer += entity
                    if (buffer.size >= BATCH) flush()
                }
                flush()
                cursors.edit().putLong(KEY_MMS, lastMms).apply()
                touched.forEach { db.conversations().refresh(it) }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read messages", e)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        } finally {
            _progress.value = SyncProgress(false, imported)
        }
        imported
    }

    /**
     * Drops local rows whose provider copy was deleted by another app (e.g. a cleaner or restore
     * tool). Only runs when we can read the provider, and never touches unsent local messages.
     */
    suspend fun reconcileDeletions() = providerLock.withLock {
        val sms = store.existingSmsIds()
        val mms = store.existingMmsIds()
        if (sms.isEmpty() && mms.isEmpty()) return@withLock // unreadable provider: don't wipe anything
        val goneSms = db.messages().providerIds(MessageKind.SMS).filter { it !in sms }
        val goneMms = db.messages().providerIds(MessageKind.MMS).filter { it !in mms }
        val ids = (goneSms.mapNotNull { db.messages().byProviderId(MessageKind.SMS, it) } + goneMms.mapNotNull { db.messages().byProviderId(MessageKind.MMS, it) })
            .filter { !it.isPendingOutgoing }
        if (ids.isNotEmpty()) {
            db.messages().delete(ids.map { it.id })
            ids.map { it.conversationId }.distinct().forEach { db.conversations().refresh(it) }
            db.conversations().deleteEmpty()
        }
    }

    private fun ProviderSms.toEntity(): MessageEntity {
        val outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX
        val status = when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
            Telephony.Sms.MESSAGE_TYPE_SENT -> if (this.status == Telephony.Sms.STATUS_COMPLETE) MessageStatus.DELIVERED else MessageStatus.SENT
            else -> MessageStatus.FAILED // outbox/queued/failed rows from other apps can't be resumed safely
        }
        return MessageEntity(
            conversationId = threadId, kind = MessageKind.SMS, providerId = id, address = address, body = body,
            date = date, dateSent = dateSent, outgoing = outgoing, status = status, errorCode = errorCode, subId = subId,
            read = read || outgoing, seen = seen || outgoing, analyzed = false,
        )
    }

    private fun ProviderMms.toEntity(): MessageEntity? {
        val outgoing = box != Telephony.Mms.MESSAGE_BOX_INBOX
        val isNotification = messageType == TelephonyStore.MESSAGE_TYPE_NOTIFICATION_IND
        val text = parts.filter { it.contentType.startsWith("text/plain", true) }.mapNotNull { it.text }.joinToString("\n").trim()
        val address = if (outgoing) to.joinToString(",") else from ?: to.firstOrNull() ?: return null
        val status = when {
            isNotification -> MessageStatus.PENDING_DOWNLOAD
            !outgoing -> MessageStatus.RECEIVED
            box == Telephony.Mms.MESSAGE_BOX_SENT -> MessageStatus.SENT
            else -> MessageStatus.FAILED
        }
        val hasMedia = parts.any { !it.contentType.startsWith("text/") && !it.contentType.equals("application/smil", true) }
        return MessageEntity(
            conversationId = threadId, kind = MessageKind.MMS, providerId = id, address = address, body = text, subject = subject,
            date = dateMillis, outgoing = outgoing, status = status, subId = subId, read = read || outgoing, seen = read || outgoing,
            hasAttachments = hasMedia, mmsContentLocation = contentLocation, mmsTransactionId = transactionId, mmsMessageId = messageId,
            mmsExpiry = expirySeconds, mmsSize = size, analyzed = false, sendAsMms = outgoing,
        )
    }

    companion object {
        private const val TAG = "TelephonySync"
        private const val BATCH = 400
        private const val KEY_SMS = "sms"
        private const val KEY_MMS = "mms"
    }
}
