package app.yarn.intelligence

import java.text.Normalizer

object TextNormalizer {
    private val ZERO_WIDTH = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]")
    private val WHITESPACE = Regex("\\s+")
    private val TOKEN = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")
    private val URL_LIKE = Regex("(?i)\\b(?:https?://|www\\.)\\S+")
    private val NUMBER = Regex("^\\d+$")
    private val WEEKDAYS = setOf("mon", "monday", "tue", "tues", "tuesday", "wed", "wednesday", "thu", "thur", "thurs", "thursday", "fri", "friday", "sat", "saturday", "sun", "sunday")
    private val MONTHS = setOf("jan", "january", "feb", "february", "mar", "march", "apr", "april", "may", "jun", "june", "jul", "july", "aug", "august", "sep", "sept", "september", "oct", "october", "nov", "november", "dec", "december")

    /** Common Cyrillic/Greek homoglyphs of Latin letters used to evade filters. */
    private val HOMOGLYPHS: Map<Char, Char> = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', 'і' to 'i',
        'ј' to 'j', 'ѕ' to 's', 'ԁ' to 'd', 'ɡ' to 'g', 'һ' to 'h', 'ⅼ' to 'l', 'ο' to 'o', 'α' to 'a',
        'ν' to 'v', 'τ' to 't', 'κ' to 'k', 'ι' to 'i', 'А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K',
        'М' to 'M', 'Н' to 'H', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X', 'Ү' to 'Y',
    )

    fun containsZeroWidth(text: String): Boolean = ZERO_WIDTH.containsMatchIn(text)

    /** Counts words that mix Latin with lookalike non-Latin letters (e.g. "Pаypal" with Cyrillic а). */
    fun mixedScriptWordCount(text: String): Int = text.split(WHITESPACE).count { word ->
        val hasLatin = word.any { it in 'a'..'z' || it in 'A'..'Z' }
        hasLatin && word.any { it in HOMOGLYPHS }
    }

    /** NFKC, strip zero-width, map homoglyphs, lowercase, collapse whitespace. */
    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(ZERO_WIDTH, "")
        val mapped = buildString(nfkc.length) { nfkc.forEach { append(HOMOGLYPHS[it] ?: it) } }
        return mapped.lowercase().replace(WHITESPACE, " ").trim()
    }

    /**
     * Tokens for statistical learning. Numbers become shape tokens (so "OTP 482913" and
     * "OTP 110022" look alike) and URLs collapse to a single token plus their host.
     */
    fun tokens(text: String): List<String> {
        val norm = normalize(text)
        val out = ArrayList<String>()
        val withoutUrls = URL_LIKE.replace(norm) { m ->
            out += "<url>"
            UrlTools.host(m.value)?.let { out += "host:$it" }
            " "
        }
        TOKEN.findAll(withoutUrls).forEach { m ->
            val t = m.value
            out += when {
                NUMBER.matches(t) -> "<num${minOf(t.length, 9)}>"
                t in WEEKDAYS -> "<dow>"
                t in MONTHS -> "<month>"
                t.any { it.isDigit() } && t.any { it.isLetter() } -> "<alnum>"
                else -> t
            }
        }
        return out
    }

    /** Word shingles used for near-duplicate detection of templated messages. */
    fun shingles(text: String, n: Int = 2): Set<String> {
        val t = tokens(text)
        if (t.size < n) return t.toSet()
        return (0..t.size - n).mapTo(HashSet()) { t.subList(it, it + n).joinToString(" ") }
    }

    fun jaccard(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        val inter = a.count { it in b }
        return inter.toFloat() / (a.size + b.size - inter)
    }
}

object SenderAnalyzer {
    private val SHORT_CODE = Regex("^\\d{3,8}$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val SUFFIX = Regex("(?i)-([PSTG])$")

    fun kindOf(address: String, isContact: Boolean): SenderKind {
        if (isContact) return SenderKind.CONTACT
        val a = address.trim()
        if (EMAIL.matches(a)) return SenderKind.EMAIL
        val digits = a.filter { it.isDigit() }
        val hasLetters = a.any { it.isLetter() }
        if (hasLetters) return SenderKind.ALPHANUMERIC
        if (SHORT_CODE.matches(a.removePrefix("+"))) return SenderKind.SHORT_CODE
        return if (digits.length >= 7) SenderKind.PHONE_NUMBER else SenderKind.SHORT_CODE
    }

    fun commercialSuffix(address: String): CommercialSuffix? {
        val m = SUFFIX.find(address.trim()) ?: return null
        if (!address.any { it.isLetter() && it.uppercaseChar() !in "PSTG" }) return null
        return when (m.groupValues[1].uppercase()) {
            "P" -> CommercialSuffix.PROMOTIONAL
            "S" -> CommercialSuffix.SERVICE
            "T" -> CommercialSuffix.TRANSACTIONAL
            "G" -> CommercialSuffix.GOVERNMENT
            else -> null
        }
    }

    /** "VM-HDFCBK-S" -> "HDFCBK": the brand part of an Indian-style header, else the address. */
    fun brandToken(address: String): String {
        val parts = address.trim().split('-').filter { it.isNotBlank() }
        return when {
            parts.size >= 3 && parts.last().length == 1 -> parts[parts.size - 2]
            parts.size == 2 && parts[0].length == 2 -> parts[1]
            else -> address.trim()
        }.uppercase()
    }
}
