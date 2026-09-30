package app.yarn.mms

/**
 * Parses MMS PDUs (M-Notification.ind, M-Retrieve.conf, M-Send.conf, M-Delivery.ind, M-Read-Orig.ind).
 * Unknown or vendor headers are skipped with generic WSP value rules so that one odd
 * header never makes an otherwise valid message unreadable.
 */
object PduParser {

    fun parse(bytes: ByteArray): Pdu {
        val r = PduReader(bytes)
        val h = parseHeaders(r)
        val type = h.messageType ?: throw PduParseException("Missing X-Mms-Message-Type")
        return when (type) {
            MessageType.NOTIFICATION_IND -> Pdu.NotificationInd(
                transactionId = h.transactionId,
                contentLocation = h.contentLocation ?: throw PduParseException("Notification without content location"),
                from = h.from?.let(MmsAddress::clean),
                subject = h.subject,
                messageSize = h.messageSize,
                expiry = h.expiry,
                messageClass = h.messageClass,
                mmsVersion = h.version,
            )
            MessageType.RETRIEVE_CONF -> {
                val ct = h.contentType ?: ContentType(ContentTypes.TEXT_PLAIN)
                val parts = if (!r.hasMore()) emptyList() else if (ContentTypes.isMultipart(ct.mimeType)) {
                    parseMultipart(r)
                } else {
                    listOf(PduPart(ct.mimeType, ct.charset, ct.name, ct.fileName, null, null, r.readBytes(r.remaining)))
                }
                Pdu.RetrieveConf(
                    transactionId = h.transactionId,
                    messageId = h.messageId,
                    from = h.from?.let(MmsAddress::clean),
                    to = h.to.map(MmsAddress::clean),
                    cc = h.cc.map(MmsAddress::clean),
                    subject = h.subject,
                    dateEpochSeconds = h.date,
                    contentType = ct,
                    parts = parts,
                    retrieveStatus = h.retrieveStatus,
                    retrieveText = h.retrieveText,
                    messageClass = h.messageClass,
                    deliveryReportRequested = h.deliveryReport == MmsValues.YES,
                    readReportRequested = h.readReport == MmsValues.YES,
                )
            }
            MessageType.SEND_CONF -> Pdu.SendConf(h.transactionId, h.responseStatus, h.responseText, h.messageId)
            MessageType.DELIVERY_IND -> Pdu.DeliveryInd(h.messageId, h.to.map(MmsAddress::clean), h.date, h.status)
            MessageType.READ_ORIG_IND -> Pdu.ReadOrigInd(h.messageId, h.from?.let(MmsAddress::clean), h.date, h.readStatus)
            else -> Pdu.Other(type, h.transactionId)
        }
    }

    private class Headers {
        var messageType: Int? = null
        var transactionId: String? = null
        var version: Int? = null
        var contentLocation: String? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var subject: String? = null
        var messageSize: Long? = null
        var expiry: MmsTime? = null
        var messageClass: String? = null
        var messageId: String? = null
        var date: Long? = null
        var contentType: ContentType? = null
        var retrieveStatus: Int? = null
        var retrieveText: String? = null
        var responseStatus: Int? = null
        var responseText: String? = null
        var status: Int? = null
        var readStatus: Int? = null
        var deliveryReport: Int? = null
        var readReport: Int? = null
    }

    private fun parseHeaders(r: PduReader): Headers {
        val h = Headers()
        while (r.hasMore()) {
            val field = r.peek()
            if (field < 0x80) {
                // Application-header: Token-text Application-specific-value.
                r.readText(); if (r.hasMore()) r.readText()
                continue
            }
            r.readByte()
            when (field) {
                Header.MESSAGE_TYPE -> h.messageType = r.readByte()
                Header.TRANSACTION_ID -> h.transactionId = r.readText()
                Header.MMS_VERSION -> h.version = r.readShortInteger()
                Header.CONTENT_LOCATION -> h.contentLocation = r.readText()
                Header.FROM -> h.from = readFrom(r)
                Header.TO -> h.to += r.readEncodedString()
                Header.CC -> h.cc += r.readEncodedString()
                Header.BCC -> r.readEncodedString()
                Header.SUBJECT -> h.subject = r.readEncodedString().takeIf { it.isNotBlank() }
                Header.MESSAGE_SIZE -> h.messageSize = r.readLongInteger()
                Header.EXPIRY -> h.expiry = readTime(r)
                Header.DELIVERY_TIME -> readTime(r)
                Header.MESSAGE_CLASS -> h.messageClass = readMessageClass(r)
                Header.MESSAGE_ID -> h.messageId = r.readText()
                Header.DATE -> h.date = r.readLongInteger()
                Header.RETRIEVE_STATUS -> h.retrieveStatus = r.readByte()
                Header.RETRIEVE_TEXT -> h.retrieveText = r.readEncodedString()
                Header.RESPONSE_STATUS -> h.responseStatus = r.readByte()
                Header.RESPONSE_TEXT -> h.responseText = r.readEncodedString()
                Header.STATUS -> h.status = r.readByte()
                Header.READ_STATUS -> h.readStatus = r.readByte()
                Header.DELIVERY_REPORT -> h.deliveryReport = r.readByte()
                Header.READ_REPORT -> h.readReport = r.readByte()
                Header.CONTENT_TYPE -> {
                    h.contentType = readContentType(r)
                    // Content-Type is always the last header; the body follows.
                    return h
                }
                else -> r.skipValue()
            }
        }
        return h
    }

    private fun readFrom(r: PduReader): String? {
        val len = r.readValueLength()
        val sub = r.sub(len)
        if (!sub.hasMore()) return null
        return when (sub.readByte()) {
            0x80 -> if (sub.hasMore()) sub.readEncodedString() else null // Address-present-token
            else -> null // Insert-address-token: the MMSC fills in the sender.
        }
    }

    private fun readTime(r: PduReader): MmsTime? {
        val len = r.readValueLength()
        val sub = r.sub(len)
        if (!sub.hasMore()) return null
        val token = sub.readByte()
        val value = sub.readIntegerValue()
        return MmsTime(value, relative = token == 0x81)
    }

    private fun readMessageClass(r: PduReader): String {
        val b = r.peek()
        if (b >= 0x80) {
            r.readByte()
            return when (b) {
                MmsValues.CLASS_PERSONAL -> "personal"
                MmsValues.CLASS_ADVERTISEMENT -> "advertisement"
                MmsValues.CLASS_INFORMATIONAL -> "informational"
                MmsValues.CLASS_AUTO -> "auto"
                else -> "unknown"
            }
        }
        return r.readText()
    }

    internal fun readContentType(r: PduReader): ContentType {
        val first = r.peek()
        if (first >= 0x80) {
            val code = r.readShortInteger()
            return ContentType(ContentTypes.fromCode(code) ?: "application/octet-stream")
        }
        if (first >= 0x20) return ContentType(r.readText())

        val len = r.readValueLength()
        val sub = r.sub(len)
        val mime = if (sub.peek() >= 0x80) {
            ContentTypes.fromCode(sub.readShortInteger()) ?: "application/octet-stream"
        } else if (sub.peek() >= 0x20 || sub.peek() == Wsp.QUOTE) {
            sub.readText()
        } else {
            // Well-known-media encoded as long integer (rare).
            ContentTypes.fromCode(sub.readLongInteger().toInt()) ?: "application/octet-stream"
        }
        var ct = ContentType(mime)
        while (sub.hasMore()) ct = readParameter(sub, ct)
        return ct
    }

    private fun readParameter(r: PduReader, ct: ContentType): ContentType {
        val b = r.peek()
        if (b >= 0x80) {
            val token = r.readShortInteger()
            return when (token) {
                0x01 -> ct.copy(charset = readCharset(r))
                0x05, 0x17 -> ct.copy(name = readTextValue(r))
                0x06, 0x18 -> ct.copy(fileName = readTextValue(r))
                0x0A, 0x19 -> ct.copy(start = readTextValue(r))
                0x09 -> ct.copy(type = readTypeValue(r))
                0x03 -> { r.readIntegerValue(); ct }
                else -> { r.skipValue(); ct }
            }
        }
        // Untyped-parameter: Token-text Untyped-value
        val name = r.readText().lowercase()
        val value = readUntypedValue(r)
        return when (name) {
            "charset" -> ct.copy(charset = value.toIntOrNull() ?: charsetFromName(value))
            "name" -> ct.copy(name = value)
            "filename" -> ct.copy(fileName = value)
            "start" -> ct.copy(start = value)
            "type" -> ct.copy(type = value)
            else -> ct
        }
    }

    private fun readCharset(r: PduReader): Int? {
        val b = r.peek()
        return if (b in 0x20..0x7F && b != Wsp.QUOTE) charsetFromName(r.readText()) else r.readIntegerValue().toInt()
    }

    private fun readTextValue(r: PduReader): String {
        val b = r.peek()
        if (b == 0) { r.readByte(); return "" }
        if (b == Wsp.STRING_QUOTE) return r.readQuotedString()
        if (b < 0x20) return r.readEncodedString()
        return String(r.readTextBytes(), Charsets.UTF_8)
    }

    private fun readTypeValue(r: PduReader): String {
        val b = r.peek()
        return if (b >= 0x80) ContentTypes.fromCode(r.readShortInteger()) ?: "" else readTextValue(r)
    }

    private fun readUntypedValue(r: PduReader): String {
        val b = r.peek()
        return when {
            b == 0 -> { r.readByte(); "" }
            b >= 0x80 -> r.readShortInteger().toString()
            b <= Wsp.SHORT_LENGTH_MAX -> r.readLongInteger().toString()
            b == Wsp.STRING_QUOTE -> r.readQuotedString()
            else -> r.readText()
        }
    }

    private fun charsetFromName(name: String): Int? = when (name.lowercase()) {
        "utf-8", "utf8" -> MmsCharsets.MIB_UTF8
        "us-ascii", "ascii" -> MmsCharsets.MIB_US_ASCII
        "iso-8859-1", "latin1" -> MmsCharsets.MIB_ISO_8859_1
        "utf-16" -> MmsCharsets.MIB_UTF16
        "iso-10646-ucs-2", "ucs-2" -> MmsCharsets.MIB_UCS2
        "shift_jis" -> MmsCharsets.MIB_SHIFT_JIS
        "gb2312" -> MmsCharsets.MIB_GB2312
        "big5" -> MmsCharsets.MIB_BIG5
        else -> null
    }

    internal fun parseMultipart(r: PduReader): List<PduPart> {
        val count = r.readUintvar().toInt()
        if (count < 0 || count > 1000) throw PduParseException("Unreasonable part count $count")
        val parts = ArrayList<PduPart>(count)
        repeat(count) {
            val headersLen = r.readUintvar().toInt()
            val dataLen = r.readUintvar().toInt()
            val headers = r.sub(headersLen)
            val ct = readContentType(headers)
            var contentId: String? = null
            var contentLocation: String? = null
            var dispositionFile: String? = null
            while (headers.hasMore()) {
                val field = headers.peek()
                if (field >= 0x80) {
                    headers.readByte()
                    when (field and 0x7F) {
                        0x0E -> contentLocation = headers.readText()
                        0x40 -> contentId = headers.readQuotedString().trim('<', '>')
                        0x2E, 0x45 -> dispositionFile = readDisposition(headers)
                        else -> headers.skipValue()
                    }
                } else {
                    val name = headers.readText()
                    val value = if (headers.hasMore()) headers.readText() else ""
                    when (name.lowercase()) {
                        "content-id" -> contentId = value.trim('"', '<', '>')
                        "content-location" -> contentLocation = value
                    }
                }
            }
            val data = r.readBytes(dataLen)
            val nested = ContentTypes.isMultipart(ct.mimeType)
            if (nested) {
                // multipart/alternative inside related: flatten, preferring whatever is inside.
                runCatching { parts += parseMultipart(PduReader(data)) }
            } else {
                parts += PduPart(ct.mimeType, ct.charset, ct.name, ct.fileName ?: dispositionFile, contentId, contentLocation, data)
            }
        }
        return parts
    }

    private fun readDisposition(r: PduReader): String? {
        val len = r.readValueLength()
        val sub = r.sub(len)
        if (!sub.hasMore()) return null
        if (sub.peek() >= 0x80) sub.readByte() else sub.readText()
        var ct = ContentType("")
        while (sub.hasMore()) ct = runCatching { readParameter(sub, ct) }.getOrElse { return ct.fileName ?: ct.name }
        return ct.fileName ?: ct.name
    }
}
