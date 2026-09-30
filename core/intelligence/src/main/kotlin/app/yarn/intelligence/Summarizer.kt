package app.yarn.intelligence

import kotlin.math.ln
import kotlin.math.sqrt

data class SummaryMessage(
    val sender: String,
    val text: String,
    val timestampMillis: Long,
    val isFromMe: Boolean,
)

data class ConversationSummary(
    val bullets: List<String>,
    val keyDetails: List<String>,
    val awaitingReply: String?,
    /** "extractive" for this engine; the app sets "gemini-nano" / "local-llm" when those are used. */
    val method: String = "extractive",
)

/**
 * Extractive summariser: TextRank over TF-IDF sentence vectors with recency and information
 * bonuses. It never invents text, only selects it, so it is safe to run on any conversation.
 */
class ExtractiveSummarizer(private val extractor: EntityExtractor = EntityExtractor()) {

    private data class Sentence(val msgIndex: Int, val sender: String, val text: String, val tokens: List<String>)

    fun summarize(messages: List<SummaryMessage>, maxBullets: Int = 4): ConversationSummary {
        val msgs = messages.filter { it.text.isNotBlank() }.takeLast(300)
        if (msgs.isEmpty()) return ConversationSummary(emptyList(), emptyList(), null)

        val sentences = msgs.flatMapIndexed { i, m ->
            SENTENCE_SPLIT.split(m.text.trim()).map { it.trim() }.filter { it.length > 1 }.map { s ->
                Sentence(i, if (m.isFromMe) "You" else m.sender, s, contentTokens(s))
            }
        }.filter { it.tokens.size >= 3 && !FILLER.matches(it.text.lowercase()) }

        val bullets = if (sentences.isEmpty()) emptyList() else rank(sentences, msgs.size, maxBullets)
        val details = keyDetails(msgs)
        val awaiting = msgs.lastOrNull()?.takeIf { !it.isFromMe && it.text.trim().endsWith("?") }?.let { "${it.sender} asked: ${clip(it.text.trim(), 120)}" }
        return ConversationSummary(bullets, details, awaiting)
    }

    private fun rank(sentences: List<Sentence>, messageCount: Int, maxBullets: Int): List<String> {
        val df = HashMap<String, Int>()
        sentences.forEach { s -> s.tokens.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = sentences.size.toDouble()
        val vectors = sentences.map { s ->
            val tf = s.tokens.groupingBy { it }.eachCount()
            val v = tf.mapValues { (t, c) -> c * ln(1 + n / (df.getValue(t))) }
            val norm = sqrt(v.values.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
            v.mapValues { it.value / norm }
        }
        fun cos(a: Map<String, Double>, b: Map<String, Double>) = a.entries.sumOf { (k, x) -> x * (b[k] ?: 0.0) }

        val size = sentences.size
        val sim = Array(size) { i -> DoubleArray(size) { j -> if (i == j) 0.0 else cos(vectors[i], vectors[j]) } }
        val outSum = DoubleArray(size) { i -> sim[i].sum() }
        var rank = DoubleArray(size) { 1.0 / size }
        repeat(25) {
            rank = DoubleArray(size) { j ->
                var acc = 0.0
                for (i in 0 until size) if (outSum[i] > 0) acc += sim[i][j] / outSum[i] * rank[i]
                0.15 / size + 0.85 * acc
            }
        }
        val scored = sentences.indices.map { i ->
            val s = sentences[i]
            val recency = s.msgIndex.toDouble() / maxOf(1, messageCount - 1)
            var score = rank[i] * (1.0 + 0.6 * recency)
            if (INFO.containsMatchIn(s.text)) score *= 1.4
            if (s.text.endsWith("?")) score *= 1.15
            if (s.tokens.size > 40) score *= 0.8
            i to score
        }.sortedByDescending { it.second }

        val picked = ArrayList<Int>()
        for ((i, _) in scored) {
            if (picked.size >= maxBullets) break
            if (picked.any { cos(vectors[it], vectors[i]) > 0.6 }) continue
            picked += i
        }
        return picked.sorted().map { i -> "${sentences[i].sender}: ${clip(sentences[i].text, 160)}" }
    }

    private fun keyDetails(msgs: List<SummaryMessage>): List<String> {
        val seen = LinkedHashSet<String>()
        for (m in msgs.takeLast(100)) {
            for (e in extractor.extract(m.text, m.timestampMillis)) {
                val label = when (e.type) {
                    EntityType.AMOUNT -> "Amount ${e.text}"
                    EntityType.DATE_TIME -> "When: ${e.text}"
                    EntityType.BOOKING_REF -> "${e.attributes["label"]?.uppercase() ?: "Booking"} ${e.value}"
                    EntityType.TRACKING_NUMBER -> "Tracking ${e.value}"
                    EntityType.ORDER_ID -> "Order ${e.value}"
                    EntityType.FLIGHT -> "Flight ${e.value}"
                    EntityType.ADDRESS -> "Address: ${e.text}"
                    else -> null
                }
                if (label != null) seen += label
            }
        }
        return seen.toList().takeLast(6)
    }

    private fun contentTokens(s: String): List<String> =
        TextNormalizer.tokens(s).filter { it !in STOPWORDS && !it.startsWith("<") && it.length > 1 }

    private fun clip(s: String, max: Int): String = if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"

    companion object {
        private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+|\\n+")
        private val FILLER = Regex("^(ok|okay|k|kk|yes|yeah|yep|no|nope|sure|thanks|thank you|thx|ty|lol|haha+|hmm+|cool|nice|great|good|done|fine)[.! ]*$")
        private val INFO = Regex("(?i)(\\d|\\b(?:at|on|tomorrow|today|tonight|meet|address|pay|paid|send|bring|call|book|confirm|deadline|due)\\b)")
        private val STOPWORDS = setOf(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been", "to", "of", "in", "on",
            "for", "with", "at", "by", "from", "it", "this", "that", "i", "you", "he", "she", "we", "they", "me",
            "my", "your", "our", "their", "so", "just", "do", "did", "does", "have", "has", "had", "will", "would",
            "can", "could", "should", "not", "no", "yes", "ok", "okay", "im", "i'm", "u", "ur", "its", "it's",
            "what", "when", "where", "how", "if", "then", "there", "here", "about", "as", "up", "out", "get", "got",
        )
    }
}

/**
 * Lightweight English reply suggestions for when ML Kit Smart Reply is unavailable or declines.
 * Only fires on clear intents; returns nothing rather than guessing.
 */
object QuickReplies {
    private data class Intent(val pattern: Regex, val replies: List<String>)

    private fun i(regex: String, vararg replies: String) = Intent(Regex("(?i)$regex"), replies.toList())

    private val INTENTS = listOf(
        i("\\bwhere (are|r) (you|u)\\b", "On my way", "Almost there", "Running a bit late"),
        i("\\b(what time|when)\\b.*\\?", "Soon", "In 10 minutes", "Not sure yet"),
        i("\\bhow (are|r) (you|u)\\b", "I'm good, you?", "Doing well, thanks!", "Not bad"),
        i("\\b(are you free|can you|could you|will you|would you|can we|shall we|do you want|wanna|want to)\\b.*\\?", "Sure!", "Yes", "Sorry, I can't"),
        i("\\bcall me\\b", "Calling you now", "Will call you soon", "Can't talk now, text me?"),
        i("\\bhappy birthday\\b", "Thank you so much!", "Thanks! 😊"),
        i("\\bcongrat", "Thank you!", "Thanks so much!"),
        i("\\blove (you|u)\\b", "Love you too ❤️"),
        i("\\b(thank you|thanks|thx|ty)\\b", "You're welcome!", "Anytime!", "No problem"),
        i("\\b(sorry|my bad|apologi[sz]e)\\b", "No worries", "It's okay"),
        i("\\bgood morning\\b", "Good morning!", "Morning! ☀️"),
        i("\\bgood night\\b", "Good night!", "Sleep well"),
        i("\\b(i'm here|im here|reached|arrived|outside)\\b", "Coming!", "Be there in 5"),
        i("^(is|are|do|does|did|will|can|should|have|has|was|were)\\b.*\\?$", "Yes", "No", "Not sure"),
        i("^(hi|hello|hey|hii+|yo)[!. ]*$", "Hi!", "Hey! What's up?"),
        i("^(ok|okay|k|done|sure|alright)[!. ]*$", "👍", "Great"),
    )

    fun suggest(lastIncoming: String, category: Category): List<String> {
        if (category != Category.PERSONAL && category != Category.WORK) return emptyList()
        val text = lastIncoming.trim()
        if (text.isEmpty() || text.length > 300) return emptyList()
        val asciiRatio = text.count { it.code < 128 }.toFloat() / text.length
        if (asciiRatio < 0.85f) return emptyList()
        return INTENTS.firstOrNull { it.pattern.containsMatchIn(text) }?.replies?.take(3).orEmpty()
    }
}
