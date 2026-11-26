package app.yarn.intelligence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A tester's ICICI Bank alerts were filed as spam; real look-alikes must still be caught. */
class BankSpamTest {
    private val analyzer = MessageAnalyzer()
    private fun verdict(address: String, text: String) =
        analyzer.analyze(MessageInput(text, SenderInfo(address), 1_790_000_000_000L, true, false), PriorityContext()).spam.verdict

    @Test
    fun aBanksOwnAlertsAndLinksAreNotSpam() {
        assertThat(verdict("JD-ICICIT-S", "Rs 5,656.92 spent on ICICI Bank Card XX4001 on 26-Nov-26 at PAY*ATRIA CONVERGENCE. Avl Lmt: Rs 2,94,343.08. If not you, call 1800 2662/SMS BLOCK 4001 to 9215676766"))
            .isEqualTo(SpamVerdict.CLEAN)
        // The tester's message, word for word: its EMI link on icici.co used to read as a fake ICICI site.
        assertThat(verdict("JD-ICICIT-S", "Rs 5,656.92 spent on ICICI Bank Card XX4001 on 26-Nov-26 at PAY*ATRIA CONVE. Avl Lmt: Rs 2,96,489.08. To dispute, call 18002662/SMS BLOCK 4001 to 9215676766. To convert this txn to EMI give a missed call on 9924667667. Know more about EMI conversion at https://icici.co/ICICIT/a/LEd4nPjg"))
            .isEqualTo(SpamVerdict.CLEAN)
        // ICICI's own short domain isn't an imitation of ICICI when ICICI sends it.
        assertThat(verdict("VM-ICICIB-S", "Important update regarding changes to the features and charges of your ICICI Bank Credit Card. Click https://icici.co/T7jK2 to know more."))
            .isEqualTo(SpamVerdict.CLEAN)
        assertThat(verdict("AX-ICICIT-T", "Your ICICI Bank Credit Card XXXX4001 has a total due of Rs 12,345.00. Pay now: https://icicibank.co/pay. Ignore if paid."))
            .isEqualTo(SpamVerdict.CLEAN)
        // Even urgent wording from the bank's registered ID stays out of Spam.
        assertThat(verdict("VM-HDFCBK-S", "URGENT: your HDFC Bank card will be blocked today. Update KYC immediately at https://hdfcbk.io/kyc"))
            .isNotIn(listOf(SpamVerdict.SPAM, SpamVerdict.SCAM))
    }

    @Test
    fun aRegisteredSendersShortLinkIsNotSuspicious() {
        assertThat(verdict("VM-LOOKSL-S", "Dear MANAV MAM thank you for going paperless, please click on the link below to view your bill https://bit.ly/lks123"))
            .isEqualTo(SpamVerdict.CLEAN)
    }

    @Test
    fun lookAlikesAreStillCaught() {
        // A phone number pretending to be the bank, with a fake domain.
        assertThat(verdict("+917012345678", "Dear ICICI customer, your account KYC expired and will be blocked today. Update now: https://icici-kyc-update.in/login"))
            .isIn(listOf(SpamVerdict.SPAM, SpamVerdict.SCAM))
        // Some other business's header linking to a domain that imitates ICICI.
        assertThat(verdict("VM-KYCUPD-S", "Your ICICI Bank account KYC expires today and the account will be blocked. Update now: https://icici.co.kyc-verify.top/x"))
            .isIn(listOf(SpamVerdict.SPAM, SpamVerdict.SCAM))
    }
}
