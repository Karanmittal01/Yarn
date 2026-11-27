package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * A tester's phone stopped at "Sorting your messages 0 of 449": one message that analysis can't
 * finish holds up the whole inbox. However long or odd a message, analysing it must end, quickly.
 */
class LongMessageTest {
    private val card = "🚗 Is your car insurance expiring soon? Renew with TATA AIG and get up to 75%* off. ✅ Zero paperwork ✅ Instant policy. Visit https://www.tataaig.com/motor?utm_source=rcs&utm_campaign=renewal Renew now | Call us | Know more"

    @Test fun anyMessageIsAnalysedQuickly() {
        val texts = mapOf(
            "repeated words and dots" to "Offer.".repeat(2_000),
            "repeated Rs." to "Rs.".repeat(5_000) + "100",
            "dotted line" to "Hello " + ".".repeat(20_000),
            "a long run of digits" to "9".repeat(20_000),
            "comma-grouped digits" to (1..3_000).joinToString(",") { "999" },
            "twenty business cards" to (1..20).joinToString("\n") { card },
            "one long word" to "x".repeat(50_000),
            "many short lines" to (1..2_000).joinToString("\n") { "• Item $it — Rs. $it /- only!!! Limited offer..." },
            "a long domain" to (1..3_000).joinToString(".") { "ab" } + ".com",
        )
        val analyzer = MessageAnalyzer()
        for ((name, text) in texts) for (sender in listOf("VM-HDFCBK-S", "+919876543210", "orqxiyk7mfuwox3dmfzf62loo@rbm.goog")) {
            val start = System.nanoTime()
            val result = runCatching { analyzer.analyze(MessageInput(text, SenderInfo(sender), 1_790_000_000_000L)) }
            val ms = (System.nanoTime() - start) / 1_000_000
            assertWithMessage("$name from $sender threw").that(result.exceptionOrNull()).isNull()
            assertWithMessage("$name from $sender took ${ms}ms").that(ms).isLessThan(1_500)
        }
    }
}
