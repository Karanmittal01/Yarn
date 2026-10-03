package app.yarn.telephony

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import app.yarn.mms.MmsCharsets
import app.yarn.mms.PduPart

data class ProviderSms(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val dateSent: Long,
    val type: Int,
    val read: Boolean,
    val seen: Boolean,
    val status: Int,
    val subId: Int,
    val errorCode: Int,
)

data class ProviderPart(val id: Long, val contentType: String, val text: String?, val name: String?)

data class ProviderMms(
    val id: Long,
    val threadId: Long,
    val dateMillis: Long,
    val box: Int,
    val read: Boolean,
    val subject: String?,
    val subId: Int,
    val messageType: Int,
    val contentLocation: String?,
    val transactionId: String?,
    val messageId: String?,
    val expirySeconds: Long,
    val size: Long,
    val from: String?,
    val to: List<String>,
    val parts: List<ProviderPart>,
)

/** A part to persist into the provider for an MMS we sent or downloaded. */
class PartToStore(val contentType: String, val data: ByteArray, val name: String?, val contentId: String?, val charset: Int?) {
    val isText get() = contentType.startsWith("text/", true) && !contentType.equals("application/smil", true)
    val isSmil get() = contentType.equals("application/smil", true)

    companion object {
        fun from(p: PduPart) = PartToStore(p.contentType, p.data, p.displayName, p.contentId, p.charset)
    }
}

/**
 * Access to the platform SMS/MMS store (content://sms, content://mms). As the default SMS app we
 * are responsible for writing every message there so the OS, backups and other apps stay correct.
 */
class TelephonyStore(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun threadIdFor(addresses: Set<String>): Long = Telephony.Threads.getOrCreateThreadId(context, addresses)

    /** thread id -> recipient addresses, from the canonical address table. */
    fun threadRecipients(): Map<Long, List<String>> {
        val canonical = HashMap<Long, String>()
        query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"))?.use { c ->
            while (c.moveToNext()) canonical[c.getLong(0)] = c.getString(1).orEmpty()
        }
        val result = HashMap<Long, List<String>>()
        query(
            Uri.parse("content://mms-sms/conversations?simple=true"),
            arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS),
        )?.use { c ->
            while (c.moveToNext()) {
                val ids = c.getString(1).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }
                result[c.getLong(0)] = ids.mapNotNull { canonical[it] }.filter { it.isNotBlank() }
            }
        }
        return result
    }

    /** Streams SMS rows newer than [afterId]; inline so callers may suspend per row. */
    inline fun forEachSms(afterId: Long, block: (ProviderSms) -> Unit) {
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE, Telephony.Sms.READ, Telephony.Sms.SEEN, Telephony.Sms.STATUS,
            Telephony.Sms.SUBSCRIPTION_ID, Telephony.Sms.ERROR_CODE,
        )
        query(Telephony.Sms.CONTENT_URI, projection, "${Telephony.Sms._ID} > ?", arrayOf(afterId.toString()), "${Telephony.Sms._ID} ASC")?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(6)
                if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                block(
                    ProviderSms(
                        id = c.getLong(0), threadId = c.getLong(1), address = c.getString(2).orEmpty(), body = c.getString(3).orEmpty(),
                        date = c.getLong(4), dateSent = c.getLong(5), type = type, read = c.getInt(7) != 0, seen = c.getInt(8) != 0,
                        status = c.getInt(9), subId = c.getIntOrNull(10) ?: -1, errorCode = c.getIntOrNull(11) ?: 0,
                    ),
                )
            }
        }
    }

    fun forEachMms(afterId: Long, block: (ProviderMms) -> Unit) {
        val parts = HashMap<Long, MutableList<ProviderPart>>()
        query(
            Uri.parse("content://mms/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.MSG_ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT, Telephony.Mms.Part.NAME, Telephony.Mms.Part.FILENAME, Telephony.Mms.Part.CONTENT_LOCATION),
            "${Telephony.Mms.Part.MSG_ID} > ?", arrayOf(afterId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(2).orEmpty()
                val name = c.getString(5) ?: c.getString(4) ?: c.getString(6)
                parts.getOrPut(c.getLong(1)) { mutableListOf() } += ProviderPart(c.getLong(0), ct, c.getString(3), name)
            }
        }
        val projection = arrayOf(
            Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ,
            Telephony.Mms.SUBJECT, Telephony.Mms.SUBJECT_CHARSET, Telephony.Mms.SUBSCRIPTION_ID, Telephony.Mms.MESSAGE_TYPE,
            Telephony.Mms.CONTENT_LOCATION, Telephony.Mms.TRANSACTION_ID, Telephony.Mms.MESSAGE_ID, Telephony.Mms.EXPIRY,
            Telephony.Mms.MESSAGE_SIZE,
        )
        query(Telephony.Mms.CONTENT_URI, projection, "${Telephony.Mms._ID} > ?", arrayOf(afterId.toString()), "${Telephony.Mms._ID} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val box = c.getInt(3)
                if (box == Telephony.Mms.MESSAGE_BOX_DRAFTS) continue
                val subject = c.getString(5)?.let { decodeSubject(it, c.getIntOrNull(6)) }
                val (from, to) = addresses(id)
                block(
                    ProviderMms(
                        id = id, threadId = c.getLong(1), dateMillis = c.getLong(2) * 1000, box = box, read = c.getInt(4) != 0,
                        subject = subject, subId = c.getIntOrNull(7) ?: -1, messageType = c.getInt(8), contentLocation = c.getString(9),
                        transactionId = c.getString(10), messageId = c.getString(11), expirySeconds = c.getLongOrNull(12) ?: 0,
                        size = c.getLongOrNull(13) ?: 0, from = from, to = to, parts = parts[id].orEmpty(),
                    ),
                )
            }
        }
    }

    private fun decodeSubject(raw: String, charset: Int?): String {
        if (charset == null || charset == MmsCharsets.MIB_UTF8 || charset == 0) return raw
        // The provider stores subjects as ISO-8859-1 bytes of the original encoding.
        val cs = MmsCharsets.forMib(charset) ?: return raw
        return runCatching { String(raw.toByteArray(Charsets.ISO_8859_1), cs) }.getOrDefault(raw)
    }

    private fun addresses(mmsId: Long): Pair<String?, List<String>> {
        var from: String? = null
        val to = mutableListOf<String>()
        query(Uri.parse("content://mms/$mmsId/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE))?.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(0) ?: continue
                if (addr == INSERT_ADDRESS_TOKEN) continue
                when (c.getInt(1)) {
                    PDU_FROM -> from = addr
                    PDU_TO, PDU_CC -> to += addr
                }
            }
        }
        return from to to
    }

    // ---- SMS writes -------------------------------------------------------------------------

    fun insertIncomingSms(address: String, body: String, date: Long, dateSent: Long, subId: Int, read: Boolean): Pair<Long, Long>? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, dateSent)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        val uri = safeInsert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        val threadId = query(uri, arrayOf(Telephony.Sms.THREAD_ID))?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return null
        return id to threadId
    }

    fun insertOutgoingSms(address: String, body: String, date: Long, subId: Int, threadId: Long, deliveryReport: Boolean): Long? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, date)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            put(Telephony.Sms.STATUS, if (deliveryReport) Telephony.Sms.STATUS_PENDING else Telephony.Sms.STATUS_NONE)
        }
        return safeInsert(Telephony.Sms.CONTENT_URI, values)?.let(ContentUris::parseId)
    }

    fun updateSmsType(id: Long, type: Int, errorCode: Int = 0) {
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, type)
            if (errorCode != 0) put(Telephony.Sms.ERROR_CODE, errorCode)
            if (type == Telephony.Sms.MESSAGE_TYPE_SENT) put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
        }
        safeUpdate(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), values)
    }

    fun updateSmsDeliveryStatus(id: Long, status: Int) {
        safeUpdate(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), ContentValues().apply { put(Telephony.Sms.STATUS, status) })
    }

    fun markRead(smsIds: Collection<Long>, mmsIds: Collection<Long>) {
        val values = ContentValues().apply { put("read", 1); put("seen", 1) }
        smsIds.chunked(500).forEach { chunk ->
            safeUpdate(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms._ID} IN (${chunk.joinToString(",")})")
        }
        mmsIds.chunked(500).forEach { chunk ->
            safeUpdate(Telephony.Mms.CONTENT_URI, values, "${Telephony.Mms._ID} IN (${chunk.joinToString(",")})")
        }
    }

    fun markUnread(kind: Int, id: Long) {
        val uri = if (kind == 0) Telephony.Sms.CONTENT_URI else Telephony.Mms.CONTENT_URI
        safeUpdate(ContentUris.withAppendedId(uri, id), ContentValues().apply { put("read", 0) })
    }

    fun deleteSms(id: Long) = safeDelete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id))

    /** Deletes many SMS rows in one provider call (ids are numbers, so the IN list is safe). */
    fun deleteSms(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val ok = runCatching {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "_id IN (${ids.joinToString(",")})", null)
        }.isSuccess
        if (!ok) ids.forEach { deleteSms(it) }
    }
    fun deleteMms(id: Long) = safeDelete(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id))
    fun deleteThread(threadId: Long) = safeDelete(ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId))

    fun existingSmsIds(): Set<Long> = ids(Telephony.Sms.CONTENT_URI)
    fun existingMmsIds(): Set<Long> = ids(Telephony.Mms.CONTENT_URI)

    private fun ids(uri: Uri): Set<Long> {
        val out = HashSet<Long>()
        query(uri, arrayOf("_id"))?.use { c -> while (c.moveToNext()) out += c.getLong(0) }
        return out
    }

    // ---- MMS writes -------------------------------------------------------------------------

    data class StoredMms(val id: Long, val partUris: List<Pair<PartToStore, Uri>>)

    fun persistMms(
        box: Int,
        threadId: Long,
        from: String?,
        recipients: List<String>,
        subject: String?,
        dateMillis: Long,
        parts: List<PartToStore>,
        subId: Int,
        read: Boolean,
        messageId: String? = null,
        transactionId: String? = null,
        contentLocation: String? = null,
        deliveryReport: Boolean = false,
    ): StoredMms? {
        val incoming = box == Telephony.Mms.MESSAGE_BOX_INBOX
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, dateMillis / 1000)
            put(Telephony.Mms.DATE_SENT, dateMillis / 1000)
            put(Telephony.Mms.MESSAGE_BOX, box)
            put(Telephony.Mms.READ, if (read) 1 else 0)
            put(Telephony.Mms.SEEN, if (read) 1 else 0)
            subject?.let { put(Telephony.Mms.SUBJECT, it); put(Telephony.Mms.SUBJECT_CHARSET, MmsCharsets.MIB_UTF8) }
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.MESSAGE_TYPE, if (incoming) MESSAGE_TYPE_RETRIEVE_CONF else MESSAGE_TYPE_SEND_REQ)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            put(Telephony.Mms.PRIORITY, 0x81)
            put(Telephony.Mms.DELIVERY_REPORT, if (deliveryReport) 0x80 else 0x81)
            put(Telephony.Mms.READ_REPORT, 0x81)
            put(Telephony.Mms.SUBSCRIPTION_ID, subId)
            put(Telephony.Mms.TEXT_ONLY, if (parts.all { it.isText || it.isSmil }) 1 else 0)
            put(Telephony.Mms.MESSAGE_SIZE, parts.sumOf { it.data.size })
            messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            contentLocation?.let { put(Telephony.Mms.CONTENT_LOCATION, it) }
        }
        val uri = safeInsert(Telephony.Mms.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        val partUris = ArrayList<Pair<PartToStore, Uri>>()
        val partBase = Uri.parse("content://mms/$id/part")
        parts.forEachIndexed { index, p ->
            val pv = ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, id)
                put(Telephony.Mms.Part.SEQ, if (p.isSmil) -1 else index)
                put(Telephony.Mms.Part.CONTENT_TYPE, p.contentType)
                p.name?.let { put(Telephony.Mms.Part.NAME, it); put(Telephony.Mms.Part.FILENAME, it); put(Telephony.Mms.Part.CONTENT_LOCATION, it) }
                p.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, "<$it>") }
                if (p.isText || p.isSmil) {
                    val cs = p.charset ?: MmsCharsets.MIB_UTF8
                    put(Telephony.Mms.Part.CHARSET, MmsCharsets.MIB_UTF8)
                    put(Telephony.Mms.Part.TEXT, String(p.data, MmsCharsets.forMib(cs) ?: Charsets.UTF_8))
                }
            }
            val partUri = safeInsert(partBase, pv) ?: return@forEachIndexed
            if (!p.isText && !p.isSmil) {
                runCatching { resolver.openOutputStream(partUri)?.use { it.write(p.data) } }
                    .onFailure { Log.w(TAG, "Failed writing MMS part data", it) }
            }
            partUris += p to partUri
        }
        val addrUri = Uri.parse("content://mms/$id/addr")
        fun addAddr(address: String, type: Int) {
            safeInsert(addrUri, ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, type)
                put(Telephony.Mms.Addr.CHARSET, MmsCharsets.MIB_UTF8)
                put(Telephony.Mms.Addr.MSG_ID, id)
            })
        }
        addAddr(from ?: INSERT_ADDRESS_TOKEN, PDU_FROM)
        recipients.forEach { addAddr(it, PDU_TO) }
        return StoredMms(id, partUris)
    }

    fun updateMmsBox(id: Long, box: Int, messageId: String? = null) {
        safeUpdate(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id), ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, box)
            messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
        })
    }

    // ---- helpers ----------------------------------------------------------------------------

    @PublishedApi
    internal fun query(uri: Uri, projection: Array<String>, selection: String? = null, args: Array<String>? = null, sort: String? = null): Cursor? =
        try {
            resolver.query(uri, projection, selection, args, sort)
        } catch (e: Exception) {
            Log.w(TAG, "Query failed for $uri", e)
            null
        }

    private fun safeInsert(uri: Uri, values: ContentValues): Uri? = try {
        resolver.insert(uri, values)
    } catch (e: Exception) {
        Log.w(TAG, "Insert failed for $uri", e)
        null
    }

    private fun safeUpdate(uri: Uri, values: ContentValues, where: String? = null): Int = try {
        resolver.update(uri, values, where, null)
    } catch (e: Exception) {
        Log.w(TAG, "Update failed for $uri", e)
        0
    }

    private fun safeDelete(uri: Uri): Int = try {
        resolver.delete(uri, null, null)
    } catch (e: Exception) {
        Log.w(TAG, "Delete failed for $uri", e)
        0
    }

    @PublishedApi
    internal fun Cursor.getIntOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)
    private fun Cursor.getLongOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)

    companion object {
        private const val TAG = "TelephonyStore"
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"
        const val PDU_FROM = 0x89
        const val PDU_TO = 0x97
        const val PDU_CC = 0x82
        const val MESSAGE_TYPE_SEND_REQ = 0x80
        const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82
        const val MESSAGE_TYPE_RETRIEVE_CONF = 0x84

        fun partUri(partId: Long): Uri = Uri.parse("content://mms/part/$partId")
    }
}
