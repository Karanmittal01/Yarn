package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * More than a thousand messages modelled on what Indian phones actually receive (see
 * tools/corpus/make_sms_corpus.py), each with the tab it belongs in. Every message must land in
 * one of its tabs, and only rows marked X may be filtered as spam.
 */
class SmsCorpusTest {
    private val analyzer = MessageAnalyzer()

    private data class Row(val sender: String, val want: Set<Char>, val text: String)

    private val rows: List<Row> = javaClass.classLoader!!.getResourceAsStream("sms-corpus.tsv")!!.bufferedReader().readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val (sender, want, text) = line.split('\t', limit = 3)
            Row(sender, want.split('|').map { it.single() }.toSet(), text.replace("\\n", "\n"))
        }

    private fun tab(a: Analysis): Char = if (a.spam.verdict.isFiltered) 'X' else when (a.category.group) {
        CategoryGroup.PERSONAL -> 'P'
        CategoryGroup.OTP -> 'O'
        CategoryGroup.TRANSACTIONS -> 'T'
        CategoryGroup.SHOPPING -> 'S'
        CategoryGroup.TRAVEL -> 'V'
        CategoryGroup.UPDATES -> 'U'
        CategoryGroup.OFFERS -> 'F'
        null -> 'X'
    }

    private fun analyze(r: Row, with: MessageAnalyzer = analyzer): Analysis {
        val contact = r.sender.startsWith("c:")
        return with.analyze(MessageInput(r.text, SenderInfo(r.sender.removePrefix("c:"), isContact = contact), 1_790_000_000_000L))
    }

    /** People who pick High spam filtering still never lose a real message. */
    @Test
    fun highSensitivityNeverFiltersARealMessage() {
        val strict = MessageAnalyzer(AnalyzerConfig(spamSensitivity = SpamSensitivity.HIGH))
        val filtered = rows.filter { 'X' !in it.want }.mapNotNull { r ->
            val a = analyze(r, strict)
            if (!a.spam.verdict.isFiltered) null
            else "${r.sender}: ${a.spam.verdict} ${"%.2f".format(a.spam.score)} ${a.spam.reasons} — “${r.text.take(110).replace('\n', ' ')}”"
        }
        assertWithMessage("${filtered.size} real messages filtered on High:\n" + filtered.joinToString("\n")).that(filtered).isEmpty()
    }

    /** Low filters only what it's surest of, and that still covers the large majority of scams. */
    @Test
    fun lowSensitivityStillCatchesScams() {
        val lenient = MessageAnalyzer(AnalyzerConfig(spamSensitivity = SpamSensitivity.LOW))
        val scams = rows.filter { it.want == setOf('X') }
        val missed = scams.mapNotNull { r ->
            val a = analyze(r, lenient)
            if (a.spam.verdict.isFiltered) null else "${"%.2f".format(a.spam.score)} ${a.spam.reasons} — “${r.text.take(100)}”"
        }
        assertWithMessage("scams missed on Low: ${missed.size} of ${scams.size}\n" + missed.joinToString("\n")).that(missed.size * 5).isAtMost(scams.size)
    }

    @Test
    fun everyMessageLandsInItsTab() {
        assertWithMessage("corpus size").that(rows.size).isAtLeast(1000)
        val misses = rows.mapNotNull { r ->
            val a = analyze(r)
            val got = tab(a)
            if (got in r.want) null else {
                val why = if (got == 'X' || 'X' in r.want) a.spam.reasons + "score=${"%.2f".format(a.spam.score)}" else a.classification.reasons
                "${r.sender}: got $got (${a.category}) want ${r.want.joinToString("|")} — $why — “${r.text.take(110).replace('\n', ' ')}”"
            }
        }
        val spamMisses = misses.count { " got X " in it }
        assertWithMessage("Misfiled ${misses.size}/${rows.size} ($spamMisses wrongly filtered as spam):\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
