package app.yarn.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import app.yarn.data.db.AttachmentEntity
import app.yarn.data.db.BinAttachment
import app.yarn.data.db.BinEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.BIN_OFF
import app.yarn.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

/**
 * Keeps a copy of everything the user deletes, so mistakes can be undone. Deleted messages leave
 * the system message store straight away (other apps stop showing them); the bin holds the only
 * copy, attachments included, until it is emptied by hand or after [app.yarn.data.prefs.AppSettings.binDays].
 * Restoring lives in [ConversationRepository.restoreFromBin], which can write to the system store.
 */
class RecycleBin(private val context: Context, private val db: YarnDatabase, private val settings: SettingsRepository) {
    val items: Flow<List<BinEntity>> = db.bin().observe()
    val count: Flow<Int> = db.bin().count()

    private val dir: File get() = File(context.filesDir, "bin").apply { mkdirs() }

    /** Copies [messages] into the bin; returns their bin ids, or nothing when the bin is turned off. */
    suspend fun keep(messages: List<MessageEntity>, now: Long = System.currentTimeMillis()): List<Long> {
        if (messages.isEmpty() || settings.current().binDays == BIN_OFF) return emptyList()
        val chats = db.conversations().getAll(messages.map { it.conversationId }.distinct()).associateBy { it.id }
        val ids = ArrayList<Long>(messages.size)
        for (chunk in messages.chunked(CHUNK)) {
            val files = db.messages().attachmentsFor(chunk.map { it.id }).groupBy { it.messageId }
            val rows = chunk.map { m ->
                val chat = chats[m.conversationId]
                BinEntity(
                    conversationId = m.conversationId,
                    addresses = chat?.addresses?.takeIf { it.isNotEmpty() } ?: m.address.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    title = chat?.title ?: m.address,
                    kind = m.kind, address = m.address, body = m.body, subject = m.subject,
                    date = m.date, dateSent = m.dateSent, outgoing = m.outgoing, status = m.status, subId = m.subId,
                    category = m.category, starred = m.starred,
                    attachments = files[m.id].orEmpty().mapNotNull(::copyOut),
                    deletedAt = now,
                )
            }
            ids += db.bin().insertAll(rows)
        }
        return ids
    }

    /** Copies an attachment out of the system store (or Yarn's own files) into the bin folder. */
    private fun copyOut(a: AttachmentEntity): BinAttachment? = try {
        val target = File(dir, UUID.randomUUID().toString() + (a.fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= 5 }?.let { ".$it" } ?: ""))
        val uri = Uri.parse(a.uri)
        val input = if (uri.scheme == "file") uri.path?.let { File(it).inputStream() } else context.contentResolver.openInputStream(uri)
        input?.use { src -> target.outputStream().use { src.copyTo(it) } } ?: error("unreadable")
        BinAttachment(a.mimeType, Uri.fromFile(target).toString(), a.fileName, target.length())
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't keep an attachment in the bin", e)
        null
    }

    /** Deletes these for good, files included. */
    suspend fun deleteForever(ids: Collection<Long>) = drop(db.bin().getAll(ids))

    suspend fun empty() = drop(db.bin().all())

    /** Empties out what has been in the bin longer than the user's chosen number of days. */
    suspend fun emptyExpired(now: Long = System.currentTimeMillis()): Int {
        val days = settings.current().binDays
        if (days <= 0) return 0
        val old = db.bin().olderThan(now - days * DAY_MS)
        drop(old)
        return old.size
    }

    private suspend fun drop(items: List<BinEntity>) {
        items.flatMap { it.attachments }.forEach { a -> Uri.parse(a.uri).takeIf { it.scheme == "file" }?.path?.let { File(it).delete() } }
        items.map { it.id }.chunked(CHUNK).forEach { db.bin().delete(it) }
    }

    /** Called once a binned message is back in a chat: its files now belong to the restored message. */
    internal fun attachmentsFor(item: BinEntity, messageId: Long): List<AttachmentEntity> =
        item.attachments.map { AttachmentEntity(messageId = messageId, mimeType = it.mimeType, uri = it.uri, fileName = it.fileName, size = it.size) }

    companion object {
        private const val TAG = "RecycleBin"
        private const val CHUNK = 400
        const val DAY_MS = 86_400_000L
    }
}
