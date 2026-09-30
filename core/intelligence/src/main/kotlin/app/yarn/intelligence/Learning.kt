package app.yarn.intelligence

import kotlin.math.exp
import kotlin.math.ln

/** One user correction. Exactly one of [category] or [isSpam] is set. */
data class Correction(
    val text: String,
    val senderAddress: String,
    val senderKind: SenderKind,
    val category: Category? = null,
    val isSpam: Boolean? = null,
    val timestampMillis: Long = 0,
)

/** Multinomial naive Bayes with Laplace smoothing. Tiny, fast, and fully explainable. */
class NaiveBayes<L : Any> {
    private val tokenCounts = HashMap<L, HashMap<String, Int>>()
    private val totalTokens = HashMap<L, Int>()
    private val docCounts = HashMap<L, Int>()
    private val vocabulary = HashSet<String>()
    var documents: Int = 0
        private set

    val labels: Set<L> get() = docCounts.keys

    fun train(tokens: List<String>, label: L) {
        documents++
        docCounts[label] = (docCounts[label] ?: 0) + 1
        val counts = tokenCounts.getOrPut(label) { HashMap() }
        for (t in tokens) {
            counts[t] = (counts[t] ?: 0) + 1
            totalTokens[label] = (totalTokens[label] ?: 0) + 1
            vocabulary += t
        }
    }

    /** Posterior probabilities over known labels, or empty if untrained. */
    fun predict(tokens: List<String>): Map<L, Float> {
        if (documents == 0) return emptyMap()
        val v = vocabulary.size.toDouble() + 1
        val logs = docCounts.keys.associateWith { label ->
            var lp = ln(docCounts.getValue(label).toDouble() / documents)
            val counts = tokenCounts[label].orEmpty()
            val total = (totalTokens[label] ?: 0).toDouble()
            for (t in tokens) lp += ln(((counts[t] ?: 0) + 1.0) / (total + v))
            lp
        }
        val max = logs.values.max()
        val exps = logs.mapValues { exp(it.value - max) }
        val sum = exps.values.sum()
        return exps.mapValues { (it.value / sum).toFloat() }
    }

    /** Tokens that most support [label] vs the rest — used to explain learned decisions. */
    fun topEvidence(tokens: List<String>, label: L, n: Int = 3): List<String> {
        val own = tokenCounts[label] ?: return emptyList()
        val ownTotal = (totalTokens[label] ?: 0) + vocabulary.size + 1.0
        val otherCounts = HashMap<String, Int>()
        var otherTotal = vocabulary.size + 1.0
        tokenCounts.forEach { (l, c) -> if (l != label) c.forEach { (t, k) -> otherCounts[t] = (otherCounts[t] ?: 0) + k; otherTotal += k } }
        return tokens.distinct()
            .filter { !it.startsWith("<") && !it.startsWith("sender:") }
            .map { t -> t to ln(((own[t] ?: 0) + 1) / ownTotal) - ln(((otherCounts[t] ?: 0) + 1) / otherTotal) }
            .filter { it.second > 0.5 }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }
}

/**
 * Everything the user has taught the assistant. Rebuilt from persisted corrections; training on
 * a few thousand examples takes milliseconds, so there is no opaque model file to manage and
 * "reset learning" is simply deleting the corrections.
 */
class LearningState(
    corrections: List<Correction> = emptyList(),
    /** Normalised sender address -> category the user pinned for that sender. */
    val senderCategories: Map<String, Category> = emptyMap(),
    /** Normalised sender address -> true (trusted, never spam) / false (always spam). */
    val senderTrust: Map<String, Boolean> = emptyMap(),
) {
    private val categoryModel = NaiveBayes<Category>()
    private val spamModel = NaiveBayes<Boolean>()
    private val memory: List<Pair<Correction, Set<String>>>

    val categoryExamples: Int get() = categoryModel.documents
    val spamExamples: Int get() = spamModel.documents

    init {
        // Newest corrections win when several conflict.
        val sorted = corrections.sortedBy { it.timestampMillis }
        for (c in sorted) {
            val tokens = features(c.text, c.senderKind, c.senderAddress)
            c.category?.let { categoryModel.train(tokens, it) }
            c.isSpam?.let { spamModel.train(tokens, it) }
        }
        memory = sorted.takeLast(MAX_MEMORY).map { it to TextNormalizer.shingles(it.text) }
    }

    fun categoryPosterior(input: MessageInput): Map<Category, Float> =
        if (categoryModel.labels.size < 2) emptyMap() else categoryModel.predict(features(input))

    fun spamPosterior(input: MessageInput): Float? =
        if (spamModel.labels.size < 2 || spamModel.documents < 6) null else spamModel.predict(features(input))[true]

    fun evidence(input: MessageInput, category: Category): List<String> = categoryModel.topEvidence(features(input), category)

    /** The most similar past correction, if the new message is clearly the same template. */
    fun similarCorrection(input: MessageInput, predicate: (Correction) -> Boolean): Pair<Correction, Float>? {
        val sh = TextNormalizer.shingles(input.text)
        if (sh.isEmpty()) return null
        val addr = normalizeAddress(input.sender.address)
        var best: Pair<Correction, Float>? = null
        for ((c, csh) in memory.asReversed()) {
            if (!predicate(c)) continue
            val sim = TextNormalizer.jaccard(sh, csh)
            val sameSender = normalizeAddress(c.senderAddress) == addr
            val threshold = if (sameSender) 0.5f else 0.8f
            if (sim >= threshold && (best == null || sim > best.second)) best = c to sim
        }
        return best
    }

    companion object {
        private const val MAX_MEMORY = 2000

        fun normalizeAddress(address: String): String {
            val a = address.trim()
            if (a.any { it.isLetter() }) return SenderAnalyzer.brandToken(a)
            val digits = a.filter { it.isDigit() }
            return if (digits.length > 10) digits.takeLast(10) else digits
        }

        fun features(text: String, kind: SenderKind, address: String): List<String> {
            val t = TextNormalizer.tokens(text).toMutableList()
            t += "sender:${kind.name.lowercase()}"
            if (kind == SenderKind.ALPHANUMERIC) t += "brand:${SenderAnalyzer.brandToken(address).lowercase()}"
            return t
        }

        fun features(input: MessageInput) = features(input.text, input.sender.kind, input.sender.address)
    }
}
