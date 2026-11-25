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
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * Imports what's new in the system store. The store is read without holding [providerLock] and
     * each batch is written under it, so an arriving message, a send or a delivery report waits for
     * one batch at most, never for a whole first import of years of messages.
     */
    suspend fun sync(): Int = syncLock.withLock {
        _progress.value = SyncProgress(true, 0)
        var imported = 0
        try {
            val recipients = store.threadRecipients()
            val touched = HashSet<Long>()

            /** Writes one batch (under the lock). Rows deleted since they were read are skipped. */
            suspend fun write(entities: List<MessageEntity>, attachments: Map<Long, List<AttachmentEntity>>) {
                if (entities.isEmpty()) return
                val ids = db.messages().insertAll(entities)
                val rows = ArrayList<AttachmentEntity>()
                ids.forEachIndexed { i, id ->
                    if (id <= 0) return@forEachIndexed
                    val m = entities[i]
                    if (m.kind == MessageKind.MMS) attachments[m.providerId]?.let { list -> rows += list.map { it.copy(messageId = id) } }
                    imported++
                }
                if (rows.isNotEmpty()) db.messages().insertAttachments(rows)
                touched += entities.map { it.conversationId }
                _progress.value = SyncProgress(true, imported)
            }

            suspend fun ensureConversations(entities: List<MessageEntity>) {
                entities.map { it.conversationId }.distinct().forEach { tid ->
                    conversations.ensure(tid, recipients[tid] ?: entities.first { it.conversationId == tid }.address.split(','))
                }
            }

            // Cursors are tracked separately from our own inserts so history is never skipped
            // when new messages arrive before the first import finishes.
            var lastSms = cursors.getLong(KEY_SMS, 0L)
            val smsRows = ArrayList<ProviderSms>(BATCH)
            suspend fun flushSms() {
                if (smsRows.isEmpty()) return
                providerLock.withLock {
                    val alive = store.stillThere(Telephony.Sms.CONTENT_URI, smsRows.map { it.id })
                    val entities = ArrayList<MessageEntity>(smsRows.size)
                    for (row in smsRows) {
                        if (row.id !in alive) continue
                        if (row.type != Telephony.Sms.MESSAGE_TYPE_INBOX &&
                            db.messages().byProviderId(MessageKind.SMS, row.id) == null &&
                            db.messages().ownSendNear(row.threadId, row.body, row.date - DUPLICATE_WINDOW_MS, row.date + DUPLICATE_WINDOW_MS, row.id) != null
                        ) {
                            // The phone saved its own copy of a message Yarn sent: drop it, keep ours.
                            store.deleteSms(row.id)
                            continue
                        }
                        entities += row.toEntity()
                    }
                    ensureConversations(entities)
                    write(entities, emptyMap())
                }
                lastSms = maxOf(lastSms, smsRows.maxOf { it.id })
                cursors.edit().putLong(KEY_SMS, lastSms).apply()
                smsRows.clear()
            }
            store.forEachSms(lastSms) { row ->
                smsRows += row
                if (smsRows.size >= BATCH) flushSms()
            }
            flushSms()

            var lastMms = cursors.getLong(KEY_MMS, 0L)
            val mmsRows = ArrayList<ProviderMms>()
            store.forEachMms(lastMms) { mmsRows += it }
            for (batch in mmsRows.chunked(BATCH)) {
                providerLock.withLock {
                    val alive = store.stillThere(Telephony.Mms.CONTENT_URI, batch.map { it.id })
                    val entities = ArrayList<MessageEntity>(batch.size)
                    val attachments = HashMap<Long, List<AttachmentEntity>>()
                    for (row in batch) {
                        if (row.id !in alive) continue
                        val entity = row.toEntity() ?: continue
                        attachments[row.id] = row.parts
                            .filter { !it.contentType.startsWith("text/") && !it.contentType.equals("application/smil", true) }
                            .map { AttachmentEntity(messageId = 0, mimeType = it.contentType, uri = TelephonyStore.partUri(it.id).toString(), fileName = it.name) }
                        entities += entity
                    }
                    ensureConversations(entities)
                    write(entities, attachments)
                }
                lastMms = maxOf(lastMms, batch.maxOf { it.id })
                cursors.edit().putLong(KEY_MMS, lastMms).apply()
            }
            // Clean up copies imported before this check existed.
            val dups = db.messages().duplicatesOfOwnSends()
            if (dups.isNotEmpty()) providerLock.withLock {
                for (dup in dups) {
                    dup.providerId?.let { store.deleteSms(it) }
                    db.messages().delete(listOf(dup.id))
                    touched += dup.conversationId
                }
            }
            touched.forEach { db.conversations().refresh(it) }
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
     * Our own rows are listed first, so a message that arrives while the store is being read is
     * never mistaken for a deleted one; only the deleting is done under [providerLock].
     */
    suspend fun reconcileDeletions() {
        val knownSms = db.messages().providerIds(MessageKind.SMS)
        val knownMms = db.messages().providerIds(MessageKind.MMS)
        val sms = store.existingSmsIds()
        val mms = store.existingMmsIds()
        if (sms.isEmpty() && mms.isEmpty()) return // unreadable provider: don't wipe anything
        val goneSms = knownSms.filter { it !in sms }
        val goneMms = knownMms.filter { it !in mms }
        if (goneSms.isEmpty() && goneMms.isEmpty()) return
        providerLock.withLock {
            // Read again under the lock: only what is still missing goes.
            val stillGoneSms = goneSms.toSet() - store.stillThere(Telephony.Sms.CONTENT_URI, goneSms)
            val stillGoneMms = goneMms.toSet() - store.stillThere(Telephony.Mms.CONTENT_URI, goneMms)
            val ids = (stillGoneSms.mapNotNull { db.messages().byProviderId(MessageKind.SMS, it) } + stillGoneMms.mapNotNull { db.messages().byProviderId(MessageKind.MMS, it) })
                .filter { !it.isPendingOutgoing }
            if (ids.isNotEmpty()) {
                db.messages().delete(ids.map { it.id })
                ids.map { it.conversationId }.distinct().forEach { db.conversations().refresh(it) }
                db.conversations().deleteEmpty()
            }
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
        private const val DUPLICATE_WINDOW_MS = 180_000L
    }
}

/**
 * Runs [block] holding the lock if it can be had within [waitMs]; otherwise runs it anyway. For
 * work that must never be lost or stall behind a long job, like storing an arriving message: the
 * lock only orders it with an import, and the database's unique key stops duplicates either way.
 */
suspend fun <T> Mutex.withLockOrAfter(waitMs: Long, block: suspend () -> T): T {
    val locked = withTimeoutOrNull(waitMs) { lock() } != null
    try {
        return block()
    } finally {
        if (locked) unlock()
    }
}
