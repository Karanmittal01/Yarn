package app.yarn.ui.bin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import app.yarn.data.db.BinEntity

/**
 * Finds messages in the recycle bin. Every word typed must appear in the chat's name, the sender, the
 * message or an attachment's name; numbers also match however the phone number was written.
 */
object BinSearch {
    fun words(query: String): List<String> = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(b: BinEntity, query: String): Boolean {
        val words = words(query)
        if (words.isEmpty()) return true
        val text = buildString {
            append(b.title).append('\n').append(b.address).append('\n').append(b.body)
            b.subject?.let { append('\n').append(it) }
            b.attachments.forEach { a -> a.fileName?.let { append('\n').append(it) } }
        }.lowercase()
        val numbers = (b.addresses + b.address).joinToString(" ") { it.filter(Char::isDigit) }
        return words.all { w ->
            w in text || w.count(Char::isDigit) >= 3 && w.all { it.isDigit() || it in "+-() " } && w.filter(Char::isDigit) in numbers
        }
    }

    fun filter(items: List<BinEntity>, query: String): List<BinEntity> =
        if (words(query).isEmpty()) items else items.filter { matches(it, query) }

    /** The part of a long message worth showing: from just before the first match, so it's in view. */
    fun snippet(body: String, query: String): String {
        val first = words(query).mapNotNull { w -> body.indexOf(w, ignoreCase = true).takeIf { it >= 0 } }.minOrNull() ?: return body
        if (first < 60) return body
        val from = body.lastIndexOf(' ', first - 24).let { if (it < 0) first - 24 else it + 1 }.coerceAtLeast(0)
        return "…" + body.substring(from)
    }

    /** [text] with each searched word picked out. */
    fun highlight(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        val lower = text.lowercase()
        if (lower.length != text.length) return@buildAnnotatedString // rare letters whose lower case is longer
        for (w in words(query)) {
            var i = lower.indexOf(w)
            while (i >= 0) {
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold, background = color.copy(alpha = 0.14f)), i, i + w.length)
                i = lower.indexOf(w, i + w.length)
            }
        }
    }
}
