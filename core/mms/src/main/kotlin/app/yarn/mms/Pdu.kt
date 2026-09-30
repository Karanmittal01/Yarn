package app.yarn.mms

/** X-Mms-Message-Type values. */
object MessageType {
    const val SEND_REQ = 0x80
    const val SEND_CONF = 0x81
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85
    const val DELIVERY_IND = 0x86
    const val READ_REC_IND = 0x87
    const val READ_ORIG_IND = 0x88
}

/** Header field assigned numbers (with the high bit set, as they appear on the wire). */
internal object Header {
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val DELIVERY_REPORT = 0x86
    const val DELIVERY_TIME = 0x87
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_ID = 0x8B
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val READ_REPORT = 0x90
    const val REPORT_ALLOWED = 0x91
    const val RESPONSE_STATUS = 0x92
    const val RESPONSE_TEXT = 0x93
    const val SENDER_VISIBILITY = 0x94
    const val STATUS = 0x95
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98
    const val RETRIEVE_STATUS = 0x99
    const val RETRIEVE_TEXT = 0x9A
    const val READ_STATUS = 0x9B
}

object MmsValues {
    const val VERSION_1_2 = 0x12
    const val YES = 0x80
    const val NO = 0x81
    const val PRIORITY_NORMAL = 0x81
    const val CLASS_PERSONAL = 0x80
    const val CLASS_ADVERTISEMENT = 0x81
    const val CLASS_INFORMATIONAL = 0x82
    const val CLASS_AUTO = 0x83

    const val RESPONSE_OK = 0x80
    const val STATUS_EXPIRED = 0x80
    const val STATUS_RETRIEVED = 0x81
    const val STATUS_REJECTED = 0x82
    const val STATUS_DEFERRED = 0x83
    const val STATUS_UNRECOGNISED = 0x84
    const val STATUS_INDETERMINATE = 0x85
    const val STATUS_FORWARDED = 0x86
    const val STATUS_UNREACHABLE = 0x87

    const val READ_STATUS_READ = 0x80
    const val RETRIEVE_OK = 0x80
}

data class ContentType(
    val mimeType: String,
    val charset: Int? = null,
    val name: String? = null,
    val fileName: String? = null,
    val start: String? = null,
    val type: String? = null,
)

class PduPart(
    val contentType: String,
    val charset: Int?,
    val name: String?,
    val fileName: String?,
    val contentId: String?,
    val contentLocation: String?,
    val data: ByteArray,
) {
    val isText: Boolean get() = ContentTypes.isText(contentType) && !ContentTypes.isSmil(contentType)
    val isSmil: Boolean get() = ContentTypes.isSmil(contentType)

    fun text(): String = PduReader.decode(data, charset ?: MmsCharsets.MIB_UTF8)

    /** Best display name for the part. */
    val displayName: String? get() = fileName ?: name ?: contentLocation

    override fun toString(): String = "PduPart($contentType, ${data.size} bytes, name=$displayName, cid=$contentId)"
}

/** Relative or absolute time as carried by X-Mms-Expiry / Delivery-Time. */
data class MmsTime(val value: Long, val relative: Boolean) {
    fun toEpochSeconds(nowEpochSeconds: Long): Long = if (relative) nowEpochSeconds + value else value
}

sealed class Pdu {
    abstract val messageType: Int
    abstract val transactionId: String?

    data class NotificationInd(
        override val transactionId: String?,
        val contentLocation: String,
        val from: String?,
        val subject: String?,
        val messageSize: Long?,
        val expiry: MmsTime?,
        val messageClass: String?,
        val mmsVersion: Int?,
    ) : Pdu() {
        override val messageType = MessageType.NOTIFICATION_IND
    }

    class RetrieveConf(
        override val transactionId: String?,
        val messageId: String?,
        val from: String?,
        val to: List<String>,
        val cc: List<String>,
        val subject: String?,
        val dateEpochSeconds: Long?,
        val contentType: ContentType,
        val parts: List<PduPart>,
        val retrieveStatus: Int?,
        val retrieveText: String?,
        val messageClass: String?,
        val deliveryReportRequested: Boolean,
        val readReportRequested: Boolean,
    ) : Pdu() {
        override val messageType = MessageType.RETRIEVE_CONF
        val isSuccess: Boolean get() = retrieveStatus == null || retrieveStatus == MmsValues.RETRIEVE_OK
    }

    data class SendConf(
        override val transactionId: String?,
        val responseStatus: Int?,
        val responseText: String?,
        val messageId: String?,
    ) : Pdu() {
        override val messageType = MessageType.SEND_CONF
        val isSuccess: Boolean get() = responseStatus == MmsValues.RESPONSE_OK
    }

    data class DeliveryInd(
        val messageId: String?,
        val to: List<String>,
        val dateEpochSeconds: Long?,
        val status: Int?,
    ) : Pdu() {
        override val messageType = MessageType.DELIVERY_IND
        override val transactionId: String? = null
        val delivered: Boolean get() = status == MmsValues.STATUS_RETRIEVED
    }

    data class ReadOrigInd(
        val messageId: String?,
        val from: String?,
        val dateEpochSeconds: Long?,
        val readStatus: Int?,
    ) : Pdu() {
        override val messageType = MessageType.READ_ORIG_IND
        override val transactionId: String? = null
    }

    data class Other(override val messageType: Int, override val transactionId: String?) : Pdu()
}

object MmsAddress {
    private val TYPE_SUFFIX = Regex("/TYPE=[^/]*$", RegexOption.IGNORE_CASE)

    /** Strips the "/TYPE=PLMN" style suffix used on the wire. */
    fun clean(raw: String): String = raw.trim().replace(TYPE_SUFFIX, "")

    /** Encodes an address for To/Cc headers. */
    fun encode(address: String): String {
        val trimmed = address.trim()
        if (trimmed.contains('@')) return trimmed
        if (TYPE_SUFFIX.containsMatchIn(trimmed)) return trimmed
        val digits = trimmed.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        return "$digits/TYPE=PLMN"
    }
}
