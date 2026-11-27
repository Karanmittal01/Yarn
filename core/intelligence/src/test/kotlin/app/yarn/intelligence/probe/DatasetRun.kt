package app.yarn.intelligence.probe

import app.yarn.intelligence.*
import java.io.File
import org.junit.Test

/**
 * Runs an outside dataset (one "sender TAB text" per line) through the analyzer and writes what Yarn
 * decided for each: tab, category, verdict, score, High verdict and reasons. Not a check; does
 * nothing unless asked:
 *
 *     DATASET=in.tsv DATASET_OUT=out.tsv ./gradlew :core:intelligence:test --tests "*DatasetRun"
 */
class DatasetRun {
    private val CONTACT_NAMES = setOf("MOM", "DAD", "SOCIETY", "SCHOOL", "OFFICE", "LANDLORD")

    @Test fun run() {
        val input = System.getenv("DATASET") ?: return
        val out = File(System.getenv("DATASET_OUT")!!)
        val a = MessageAnalyzer()
        val strict = MessageAnalyzer(AnalyzerConfig(spamSensitivity = SpamSensitivity.HIGH))
        out.bufferedWriter().use { w ->
            File(input).readLines().forEach { line ->
                val (given, raw) = line.split('\t', limit = 2)
                val text = raw.replace("\\n", "\n")
                // "c:Mom" is a saved contact, as is a sender shown as a name ("MOM", "Priya Verma").
                val sender = given.removePrefix("c:")
                val contact = given.startsWith("c:") || sender in CONTACT_NAMES || Regex("[A-Z][a-z]+ [A-Z][a-z]+").matches(sender)
                val m = MessageInput(text, SenderInfo(sender, isContact = contact), 1_790_000_000_000L)
                val r = a.analyze(m)
                val h = strict.analyze(m)
                val tab = if (r.spam.verdict.isFiltered) "SPAM" else r.category.group?.name ?: "SPAM"
                w.write(listOf(sender, raw, tab, r.category.name, r.spam.verdict.name, "%.2f".format(r.spam.score),
                    if (h.spam.verdict.isFiltered) "SPAM" else "-", r.classification.reasons.joinToString("; "), r.spam.reasons.joinToString("; ")).joinToString("\t"))
                w.newLine()
            }
        }
    }
}
