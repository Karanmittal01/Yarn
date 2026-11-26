package app.yarn.messaging

import org.json.JSONArray
import org.json.JSONObject

/**
 * RCS business chats ("chatbots") can reach the phone's message store as an MMS whose content is
 * a rich card: JSON with a title, a description and buttons, rather than plain text. Yarn shows
 * the card's words as the message instead of an empty "Attachment".
 */
object RichCards {
    /** A part that holds a card or its buttons, not a photo or file to show. */
    fun isCard(contentType: String): Boolean {
        val ct = contentType.lowercase()
        return "json" in ct || "botmessage" in ct || "botsuggestion" in ct
    }

    /** The words on a card: titles, descriptions and text, in order, without buttons' data or links. */
    fun text(contentType: String, raw: String?): String? {
        if (raw.isNullOrBlank() || !isCard(contentType)) return null
        val out = LinkedHashSet<String>()
        val root = raw.trim()
        runCatching {
            when {
                root.startsWith("{") -> collect(JSONObject(root), out, inButtons = false)
                root.startsWith("[") -> collect(JSONArray(root), out, inButtons = false)
            }
        }
        return out.joinToString("\n").take(MAX).takeIf { it.isNotBlank() }
    }

    private fun collect(value: Any?, out: MutableSet<String>, inButtons: Boolean) {
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                val k = key.lowercase()
                if (k in SKIP) return@forEach
                val v = value.opt(key)
                when {
                    v is String && k in WORDS && !inButtons -> v.trim().takeIf { it.isNotEmpty() && !it.startsWith("http") }?.let(out::add)
                    else -> collect(v, out, inButtons || k in BUTTONS)
                }
            }
            is JSONArray -> for (i in 0 until value.length()) collect(value.opt(i), out, inButtons)
        }
    }

    private val WORDS = setOf("title", "description", "text", "body", "message")
    private val BUTTONS = setOf("suggestions", "suggestion", "reply", "action")
    private val SKIP = setOf("postback", "postbackdata", "data", "url", "fileurl", "thumbnailurl", "media", "openurl", "dialphonenumber")
    private const val MAX = 2_000
}
