package app.yarn.mms

/** WSP well-known content types (WAP-230-WSP Appendix A, Table 40). Index == assigned number. */
object ContentTypes {
    const val TEXT_PLAIN = "text/plain"
    const val APP_SMIL = "application/smil"
    const val MULTIPART_MIXED = "application/vnd.wap.multipart.mixed"
    const val MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    const val MULTIPART_ALTERNATIVE = "application/vnd.wap.multipart.alternative"

    private val WELL_KNOWN = arrayOf(
        "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml",
        "text/x-vCalendar", "text/x-vCard", "text/vnd.wap.wml", "text/vnd.wap.wmlscript",
        "text/vnd.wap.wta-event", "multipart/*", "multipart/mixed", "multipart/form-data",
        "multipart/byterantes", "multipart/alternative", "application/*", "application/java-vm",
        "application/x-www-form-urlencoded", "application/x-hdmlc", "application/vnd.wap.wmlc",
        "application/vnd.wap.wmlscriptc", "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof",
        "application/vnd.wap.wtls-ca-certificate", "application/vnd.wap.wtls-user-certificate",
        "application/x-x509-ca-cert", "application/x-x509-user-cert", "image/*", "image/gif",
        "image/jpeg", "image/tiff", "image/png", "image/vnd.wap.wbmp",
        "application/vnd.wap.multipart.*", "application/vnd.wap.multipart.mixed",
        "application/vnd.wap.multipart.form-data", "application/vnd.wap.multipart.byteranges",
        "application/vnd.wap.multipart.alternative", "application/xml", "text/xml",
        "application/vnd.wap.wbxml", "application/x-x968-cross-cert", "application/x-x968-ca-cert",
        "application/x-x968-user-cert", "text/vnd.wap.si", "application/vnd.wap.sic",
        "text/vnd.wap.sl", "application/vnd.wap.slc", "text/vnd.wap.co", "application/vnd.wap.coc",
        "application/vnd.wap.multipart.related", "application/vnd.wap.sia",
        "text/vnd.wap.connectivity-xml", "application/vnd.wap.connectivity-wbxml",
        "application/pkcs7-mime", "application/vnd.wap.hashed-certificate",
        "application/vnd.wap.signed-certificate", "application/vnd.wap.cert-response",
        "application/xhtml+xml", "application/wml+xml", "text/css",
        "application/vnd.wap.mms-message", "application/vnd.wap.rollover-certificate",
        "application/vnd.wap.locc+wbxml", "application/vnd.wap.loc+xml",
        "application/vnd.syncml.dm+wbxml", "application/vnd.syncml.dm+xml",
        "application/vnd.syncml.notification", "application/vnd.wap.xhtml+xml",
        "application/vnd.wv.csp.cir", "application/vnd.oma.dd+xml", "application/vnd.oma.drm.message",
        "application/vnd.oma.drm.content", "application/vnd.oma.drm.rights+xml",
        "application/vnd.oma.drm.rights+wbxml", "application/vnd.wv.csp+xml",
        "application/vnd.wv.csp+wbxml", "application/vnd.syncml.ds.notification", "audio/*",
        "video/*", "application/vnd.oma.dd2+xml", "application/mikey", "application/vnd.oma.dcd",
        "application/vnd.oma.dcdc",
    )

    fun fromCode(code: Int): String? = WELL_KNOWN.getOrNull(code)

    fun codeOf(type: String): Int? = WELL_KNOWN.indexOfFirst { it.equals(type, ignoreCase = true) }.takeIf { it >= 0 }

    fun isMultipart(type: String?): Boolean =
        type != null && (type.startsWith("application/vnd.wap.multipart", true) || type.startsWith("multipart/", true))

    fun isText(type: String?): Boolean = type != null && type.startsWith("text/", ignoreCase = true)
    fun isSmil(type: String?): Boolean = type.equals(APP_SMIL, ignoreCase = true)
}
