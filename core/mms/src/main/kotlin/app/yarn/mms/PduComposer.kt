package app.yarn.mms

/** A part to include in an outgoing M-Send.req. */
class OutgoingPart(
    val contentType: String,
    val data: ByteArray,
    val fileName: String,
    val contentId: String = fileName,
) {
    val isText: Boolean get() = ContentTypes.isText(contentType) && !ContentTypes.isSmil(contentType)

    companion object {
        fun text(text: String, fileName: String = "text.txt"): OutgoingPart =
            OutgoingPart(ContentTypes.TEXT_PLAIN, text.toByteArray(Charsets.UTF_8), fileName)
    }
}

data class SendRequest(
    val recipients: List<String>,
    val parts: List<OutgoingPart>,
    val transactionId: String,
    val subject: String? = null,
    val dateEpochSeconds: Long? = null,
    val deliveryReport: Boolean = false,
    val readReport: Boolean = false,
    val expirySeconds: Long = 7L * 24 * 60 * 60,
    val includeSmil: Boolean = true,
)

object PduComposer {

    fun sendReq(req: SendRequest): ByteArray {
        require(req.recipients.isNotEmpty()) { "At least one recipient is required" }
        require(req.parts.isNotEmpty()) { "At least one part is required" }
        val w = PduWriter()
        w.byte(Header.MESSAGE_TYPE).byte(MessageType.SEND_REQ)
        w.byte(Header.TRANSACTION_ID).text(req.transactionId)
        w.byte(Header.MMS_VERSION).shortInteger(MmsValues.VERSION_1_2)
        req.dateEpochSeconds?.let { w.byte(Header.DATE).longInteger(it) }
        // From: Insert-address-token, the MMSC fills in our number.
        w.byte(Header.FROM).valueLength(1).byte(0x81)
        req.recipients.forEach { w.byte(Header.TO).encodedString(MmsAddress.encode(it)) }
        req.subject?.takeIf { it.isNotBlank() }?.let { w.byte(Header.SUBJECT).encodedString(it) }
        w.byte(Header.MESSAGE_CLASS).byte(MmsValues.CLASS_PERSONAL)
        if (req.expirySeconds > 0) {
            val inner = PduWriter().byte(0x81).longInteger(req.expirySeconds).toByteArray()
            w.byte(Header.EXPIRY).valueLength(inner.size).bytes(inner)
        }
        w.byte(Header.PRIORITY).byte(MmsValues.PRIORITY_NORMAL)
        w.byte(Header.DELIVERY_REPORT).byte(if (req.deliveryReport) MmsValues.YES else MmsValues.NO)
        w.byte(Header.READ_REPORT).byte(if (req.readReport) MmsValues.YES else MmsValues.NO)

        val parts = if (req.includeSmil) listOf(Smil.part(req.parts)) + req.parts else req.parts

        // Content-Type: multipart/related; start=<smil>; type=application/smil (or multipart/mixed).
        w.byte(Header.CONTENT_TYPE)
        if (req.includeSmil) {
            val ct = PduWriter()
                .shortInteger(ContentTypes.codeOf(ContentTypes.MULTIPART_RELATED)!!)
                .byte(0x8A).text("<${Smil.CONTENT_ID}>")
                .byte(0x89).text(ContentTypes.APP_SMIL)
                .toByteArray()
            w.valueLength(ct.size).bytes(ct)
        } else {
            w.shortInteger(ContentTypes.codeOf(ContentTypes.MULTIPART_MIXED)!!)
        }
        writeMultipart(w, parts)
        return w.toByteArray()
    }

    /** M-NotifyResp.ind: tells the MMSC how we handled a notification (e.g. deferred). */
    fun notifyRespInd(transactionId: String, status: Int, reportAllowed: Boolean = false): ByteArray =
        PduWriter()
            .byte(Header.MESSAGE_TYPE).byte(MessageType.NOTIFYRESP_IND)
            .byte(Header.TRANSACTION_ID).text(transactionId)
            .byte(Header.MMS_VERSION).shortInteger(MmsValues.VERSION_1_2)
            .byte(Header.STATUS).byte(status)
            .byte(Header.REPORT_ALLOWED).byte(if (reportAllowed) MmsValues.YES else MmsValues.NO)
            .toByteArray()

    /** M-Acknowledge.ind: confirms a successful retrieval (immediate retrieval flow). */
    fun acknowledgeInd(transactionId: String, reportAllowed: Boolean = false): ByteArray =
        PduWriter()
            .byte(Header.MESSAGE_TYPE).byte(MessageType.ACKNOWLEDGE_IND)
            .byte(Header.TRANSACTION_ID).text(transactionId)
            .byte(Header.MMS_VERSION).shortInteger(MmsValues.VERSION_1_2)
            .byte(Header.REPORT_ALLOWED).byte(if (reportAllowed) MmsValues.YES else MmsValues.NO)
            .toByteArray()

    private fun writeMultipart(w: PduWriter, parts: List<OutgoingPart>) {
        w.uintvar(parts.size.toLong())
        for (part in parts) {
            val headers = PduWriter()
            val ct = PduWriter()
            val code = ContentTypes.codeOf(part.contentType)
            if (code != null) ct.shortInteger(code) else ct.text(part.contentType)
            if (part.isText || ContentTypes.isSmil(part.contentType)) ct.byte(0x81).shortInteger(MmsCharsets.MIB_UTF8)
            ct.byte(0x85).text(part.fileName, Charsets.UTF_8)
            val ctBytes = ct.toByteArray()
            headers.valueLength(ctBytes.size).bytes(ctBytes)
            headers.byte(0x80 or 0x0E).text(part.fileName, Charsets.UTF_8) // Content-Location
            headers.byte(0x80 or 0x40).quotedString("<${part.contentId}>") // Content-ID
            val headerBytes = headers.toByteArray()
            w.uintvar(headerBytes.size.toLong())
            w.uintvar(part.data.size.toLong())
            w.bytes(headerBytes)
            w.bytes(part.data)
        }
    }
}

/** Minimal SMIL presentation so recipients render media above text, one slide per media item. */
object Smil {
    const val CONTENT_ID = "smil"

    fun part(parts: List<OutgoingPart>): OutgoingPart {
        val hasText = parts.any { it.isText }
        val media = parts.filter { !it.isText }
        val sb = StringBuilder()
        sb.append("<smil><head><layout><root-layout/>")
        sb.append("<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>")
        if (hasText) sb.append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>")
        sb.append("</layout></head><body>")
        val texts = parts.filter { it.isText }
        val slides = maxOf(media.size, if (texts.isNotEmpty()) 1 else 0)
        for (i in 0 until slides) {
            sb.append("<par dur=\"5000ms\">")
            media.getOrNull(i)?.let { sb.append(mediaTag(it)) }
            if (i == 0) texts.forEach { sb.append("<text src=\"cid:${escape(it.contentId)}\" region=\"Text\"/>") }
            sb.append("</par>")
        }
        sb.append("</body></smil>")
        return OutgoingPart(ContentTypes.APP_SMIL, sb.toString().toByteArray(Charsets.UTF_8), "smil.xml", CONTENT_ID)
    }

    private fun mediaTag(p: OutgoingPart): String {
        val src = "cid:${escape(p.contentId)}"
        return when {
            p.contentType.startsWith("image/") -> "<img src=\"$src\" region=\"Image\"/>"
            p.contentType.startsWith("video/") -> "<video src=\"$src\" region=\"Image\"/>"
            p.contentType.startsWith("audio/") -> "<audio src=\"$src\"/>"
            else -> "<ref src=\"$src\"/>"
        }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
