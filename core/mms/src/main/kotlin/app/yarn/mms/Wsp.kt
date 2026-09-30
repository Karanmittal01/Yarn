package app.yarn.mms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Primitive WAP-230-WSP encodings used by OMA MMS encapsulation (OMA-TS-MMS-ENC).
 */
internal object Wsp {
    const val QUOTE: Int = 0x7F
    const val LENGTH_QUOTE: Int = 0x1F
    const val STRING_QUOTE: Int = 0x22
    const val SHORT_LENGTH_MAX: Int = 30
}

class PduParseException(message: String) : Exception(message)

/** Sequential reader over a PDU byte array. */
internal class PduReader(private val data: ByteArray, start: Int = 0, private val end: Int = data.size) {
    var pos: Int = start
        private set

    val remaining: Int get() = end - pos
    fun hasMore(): Boolean = pos < end

    fun peek(): Int {
        if (pos >= end) throw PduParseException("Unexpected end of PDU at $pos")
        return data[pos].toInt() and 0xFF
    }

    fun readByte(): Int {
        val b = peek()
        pos++
        return b
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || pos + count > end) throw PduParseException("Length $count exceeds PDU bounds at $pos")
        val out = data.copyOfRange(pos, pos + count)
        pos += count
        return out
    }

    fun skip(count: Int) {
        if (count < 0 || pos + count > end) throw PduParseException("Skip $count exceeds PDU bounds at $pos")
        pos += count
    }

    fun sub(length: Int): PduReader {
        if (length < 0 || pos + length > end) throw PduParseException("Sub-reader of $length exceeds bounds at $pos")
        val r = PduReader(data, pos, pos + length)
        pos += length
        return r
    }

    fun readUintvar(): Long {
        var result = 0L
        var count = 0
        while (true) {
            val b = readByte()
            result = (result shl 7) or (b and 0x7F).toLong()
            count++
            if (b and 0x80 == 0) break
            if (count > 5) throw PduParseException("Uintvar too long")
        }
        return result
    }

    /** Value-length: short-length (0..30) or Length-quote followed by uintvar. */
    fun readValueLength(): Int {
        val first = readByte()
        return when {
            first <= Wsp.SHORT_LENGTH_MAX -> first
            first == Wsp.LENGTH_QUOTE -> readUintvar().toInt()
            else -> throw PduParseException("Invalid value-length octet 0x${first.toString(16)}")
        }
    }

    fun readShortInteger(): Int = readByte() and 0x7F

    fun readLongInteger(): Long {
        val len = readByte()
        if (len > 8) throw PduParseException("Long-integer length $len too large")
        var v = 0L
        repeat(len) { v = (v shl 8) or readByte().toLong() }
        return v
    }

    /** Integer-value: short-integer or long-integer. */
    fun readIntegerValue(): Long {
        val b = peek()
        return if (b and 0x80 != 0) readShortInteger().toLong() else readLongInteger()
    }

    /** Text-string, optionally prefixed with the quote octet. Returns raw bytes without terminator. */
    fun readTextBytes(): ByteArray {
        if (peek() == Wsp.QUOTE) readByte()
        val start = pos
        while (pos < end && data[pos].toInt() != 0) pos++
        val out = data.copyOfRange(start, pos)
        if (pos < end) pos++ // NUL terminator
        return out
    }

    fun readText(): String = String(readTextBytes(), Charsets.ISO_8859_1)

    /** Quoted-string: <"> TEXT NUL (the leading quote is optional in practice). */
    fun readQuotedString(): String {
        if (peek() == Wsp.STRING_QUOTE) readByte()
        return String(readTextBytes(), Charsets.UTF_8)
    }

    /** Encoded-string-value: Text-string | Value-length Char-set Text-string. */
    fun readEncodedString(): String {
        val first = peek()
        if (first < 0x20) {
            val length = readValueLength()
            val r = sub(length)
            val charsetMib = r.readIntegerValue().toInt()
            val bytes = r.readTextBytes()
            return decode(bytes, charsetMib)
        }
        return String(readTextBytes(), Charsets.UTF_8)
    }

    /**
     * Skips any well-formed WSP value using the generic first-octet rules:
     * 0..30 short length, 31 length-quote, 32..127 text, 128..255 single octet.
     */
    fun skipValue() {
        val b = peek()
        when {
            b <= Wsp.SHORT_LENGTH_MAX -> { readByte(); skip(b) }
            b == Wsp.LENGTH_QUOTE -> { readByte(); skip(readUintvar().toInt()) }
            b < 0x80 -> readTextBytes()
            else -> readByte()
        }
    }

    companion object {
        fun decode(bytes: ByteArray, mib: Int): String {
            val cs = MmsCharsets.forMib(mib) ?: Charsets.UTF_8
            return String(bytes, cs)
        }
    }
}

internal class PduWriter {
    private val out = ByteArrayOutputStream()

    fun toByteArray(): ByteArray = out.toByteArray()
    val size: Int get() = out.size()

    fun byte(v: Int): PduWriter = apply { out.write(v and 0xFF) }
    fun bytes(b: ByteArray): PduWriter = apply { out.write(b) }

    fun uintvar(value: Long): PduWriter = apply { bytes(encodeUintvar(value)) }

    fun shortInteger(v: Int): PduWriter {
        require(v in 0..127) { "Short-integer out of range: $v" }
        return byte(v or 0x80)
    }

    fun longInteger(v: Long): PduWriter = apply {
        require(v >= 0)
        var tmp = v
        val buf = ArrayList<Int>()
        do {
            buf.add((tmp and 0xFF).toInt()); tmp = tmp ushr 8
        } while (tmp != 0L)
        byte(buf.size)
        for (i in buf.indices.reversed()) byte(buf[i])
    }

    fun integerValue(v: Long): PduWriter = if (v in 0..127) shortInteger(v.toInt()) else longInteger(v)

    fun valueLength(length: Int): PduWriter = apply {
        if (length <= Wsp.SHORT_LENGTH_MAX) byte(length) else { byte(Wsp.LENGTH_QUOTE); uintvar(length.toLong()) }
    }

    fun text(value: String, charset: Charset = Charsets.ISO_8859_1): PduWriter = apply {
        val b = value.toByteArray(charset)
        if (b.isNotEmpty() && (b[0].toInt() and 0xFF) >= 0x80) byte(Wsp.QUOTE)
        bytes(b); byte(0)
    }

    fun quotedString(value: String): PduWriter = apply {
        byte(Wsp.STRING_QUOTE); bytes(value.toByteArray(Charsets.UTF_8)); byte(0)
    }

    /** Encoded-string-value; plain text when ASCII, else UTF-8 with explicit charset. */
    fun encodedString(value: String): PduWriter = apply {
        if (value.all { it.code in 0x20..0x7E }) {
            text(value)
        } else {
            val inner = PduWriter().shortInteger(MmsCharsets.MIB_UTF8).text(value, Charsets.UTF_8).toByteArray()
            valueLength(inner.size); bytes(inner)
        }
    }

    companion object {
        fun encodeUintvar(value: Long): ByteArray {
            require(value >= 0)
            val groups = ArrayList<Int>()
            var v = value
            groups.add((v and 0x7F).toInt())
            v = v ushr 7
            while (v != 0L) {
                groups.add(((v and 0x7F) or 0x80).toInt()); v = v ushr 7
            }
            return ByteArray(groups.size) { groups[groups.size - 1 - it].toByte() }
        }
    }
}

/** IANA MIBenum values used in MMS. */
object MmsCharsets {
    const val MIB_ANY = 0
    const val MIB_US_ASCII = 3
    const val MIB_ISO_8859_1 = 4
    const val MIB_SHIFT_JIS = 17
    const val MIB_UTF8 = 106
    const val MIB_UCS2 = 1000
    const val MIB_UTF16 = 1015
    const val MIB_GB2312 = 2025
    const val MIB_BIG5 = 2026

    fun forMib(mib: Int): Charset? {
        val name = when (mib) {
            MIB_ANY, MIB_UTF8 -> "UTF-8"
            MIB_US_ASCII -> "US-ASCII"
            MIB_ISO_8859_1 -> "ISO-8859-1"
            MIB_SHIFT_JIS -> "Shift_JIS"
            MIB_UCS2 -> "UTF-16BE"
            MIB_UTF16 -> "UTF-16"
            MIB_GB2312 -> "GB2312"
            MIB_BIG5 -> "Big5"
            else -> return null
        }
        return runCatching { Charset.forName(name) }.getOrNull()
    }
}
