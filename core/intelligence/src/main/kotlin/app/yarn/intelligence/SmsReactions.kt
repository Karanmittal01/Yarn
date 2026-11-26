package app.yarn.intelligence

/**
 * Reactions that reach plain SMS as text. iPhones (and Google Messages, when it reacts to someone
 * on SMS) send them as `Loved “see you at 8”`, `Laughed at an image`, `Reacted 🔥 to “…”`, and take
 * them back with `Removed a heart from “…”`. Yarn shows them as an emoji on the message they're about.
 */
object SmsReactions {
    data class Reaction(
        val emoji: String,
        /** The text reacted to, as quoted; null when it was a photo, video or other attachment. */
        val quote: String?,
        /** Taking a reaction back. */
        val removed: Boolean = false,
    ) {
        val media: Boolean get() = quote == null
    }

    private val VERBS = mapOf(
        "liked" to "👍", "loved" to "❤️", "disliked" to "👎", "laughed at" to "😂",
        "emphasized" to "‼️", "emphasised" to "‼️", "questioned" to "❓",
    )
    private val NOUNS = mapOf(
        "like" to "👍", "heart" to "❤️", "love" to "❤️", "dislike" to "👎", "laugh" to "😂",
        "exclamation" to "‼️", "exclamation mark" to "‼️", "emphasis" to "‼️", "question mark" to "❓", "question" to "❓",
    )

    // Curly quotes from iPhones, straight ones from some Android apps; the text inside may hold quotes too.
    private const val QUOTED = "[“\"](.+)[”\"]"
    // Exactly as iPhones word them ("Loved an image", "Liked a movie"): a person writing "Loved the movie" isn't reacting.
    private const val MEDIA = "(?:an image|a photo|a picture|a video|a movie|a gif|a sticker|an attachment|an audio message|a voice message)"
    private val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val ADD = Regex("^(liked|loved|disliked|laughed at|emphasi[sz]ed|questioned) (?:$QUOTED|$MEDIA)$", OPTIONS)
    private val REACTED = Regex("^reacted (\\S{1,16}) to (?:$QUOTED|$MEDIA)$", OPTIONS)
    private val REMOVED = Regex("^removed (?:an? )?(.{1,20}?) from (?:$QUOTED|$MEDIA)$", OPTIONS)

    fun parse(body: String): Reaction? {
        val text = body.trim()
        if (text.length < 7 || text.length > 2_000) return null
        ADD.find(text)?.let { m ->
            val emoji = VERBS[m.groupValues[1].lowercase()] ?: return null
            return Reaction(emoji, m.groupValues[2].takeIf { it.isNotEmpty() })
        }
        REACTED.find(text)?.let { m ->
            val emoji = m.groupValues[1].takeIf(::isEmoji) ?: return null
            return Reaction(emoji, m.groupValues[2].takeIf { it.isNotEmpty() })
        }
        REMOVED.find(text)?.let { m ->
            val what = m.groupValues[1]
            val emoji = NOUNS[what.lowercase()] ?: what.takeIf(::isEmoji) ?: return null
            return Reaction(emoji, m.groupValues[2].takeIf { it.isNotEmpty() }, removed = true)
        }
        return null
    }

    /** The reactions Yarn offers: the six every iPhone and Google Messages understands. */
    val OFFERED = listOf("👍", "❤️", "😂", "‼️", "❓", "👎")

    private val VERB_FOR = mapOf("👍" to "Liked", "❤️" to "Loved", "😂" to "Laughed at", "‼️" to "Emphasized", "❓" to "Questioned", "👎" to "Disliked")
    private val REMOVAL_FOR = mapOf("👍" to "a like", "❤️" to "a heart", "😂" to "a laugh", "‼️" to "an exclamation", "❓" to "a question mark", "👎" to "a dislike")

    /** What a reaction says about an attachment: "an image", "a video" or "an attachment". */
    enum class Media(val words: String) { IMAGE("an image"), VIDEO("a video"), OTHER("an attachment") }

    /**
     * The text to send for a reaction, worded as iPhones word it so other phones show it as one:
     * `Loved “see you at 8”`, `Laughed at an image`, `Removed a heart from “see you at 8”`.
     * [quote] is the message's text; long ones are cut short with "…".
     */
    fun compose(emoji: String, quote: String?, media: Media = Media.OTHER, removed: Boolean = false): String {
        val what = quote?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
            "“" + (if (q.length > MAX_QUOTE) q.take(MAX_QUOTE - 20).trimEnd() + "…" else q) + "”"
        } ?: media.words
        return if (removed) {
            "Removed ${REMOVAL_FOR[emoji] ?: emoji} from $what"
        } else {
            VERB_FOR[emoji]?.let { "$it $what" } ?: "Reacted $emoji to $what"
        }
    }

    private const val MAX_QUOTE = 120

    /** Whether [body] is the message that [quote] reacts to. Long messages may be quoted cut short with "…". */
    fun matches(quote: String, body: String): Boolean {
        val q = normalize(quote)
        val b = normalize(body)
        if (q.isEmpty() || b.isEmpty()) return false
        if (q == b) return true
        val cut = q.removeSuffix("…").removeSuffix("...").trimEnd()
        return cut.length in 8 until q.length && b.startsWith(cut)
    }

    /** The first characters of [quote], for finding candidate messages before [matches] decides. */
    fun searchPrefix(quote: String): String = normalize(quote).removeSuffix("…").removeSuffix("...").take(24)

    private val SPACE = Regex("\\s+")
    private fun normalize(s: String) = s.replace('’', '\'').replace('‘', '\'').replace(SPACE, " ").trim()

    /** An emoji, with any skin tone or joiners: no letters or digits in it. */
    private fun isEmoji(s: String) = s.isNotEmpty() && s.none { it.isLetterOrDigit() } && s.any { Character.isSurrogate(it) || it.code in 0x2600..0x27BF || it.code in 0x2190..0x21FF || it == '‼' || it == '❓' }
}
