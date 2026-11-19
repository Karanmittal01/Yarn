package app.yarn.intelligence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The on-device model may only re-file a message under a group its own words point to. */
class SecondOpinionTest {
    private val analyzer = MessageAnalyzer()
    private fun may(from: String, text: String, group: CategoryGroup) =
        analyzer.mayFileAs(MessageInput(text, SenderInfo(from), 1_790_000_000_000L), group)

    @Test
    fun anApprovalIsNeverAnOffer() {
        val text = "Congratulations! We approved your HDFC Bank Credit Card (App No. D26K12345678A0B1). Use password ABCD0101 to view your application in your email"
        assertThat(may("VD-HDFCBK-S", text, CategoryGroup.OFFERS)).isFalse()
        assertThat(may("VD-HDFCBK-S", text, CategoryGroup.UPDATES)).isTrue()
    }

    @Test
    fun aCabPickupIsNeverShopping() {
        val text = "SS-HR-6786 assigned for your pickup has started. You can track vehicle using mobile app.\n- RM"
        assertThat(may("AX-RMATIC-S", text, CategoryGroup.SHOPPING)).isFalse()
        assertThat(may("AX-RMATIC-S", text, CategoryGroup.TRAVEL)).isTrue()
    }

    @Test
    fun anAdvertMayStillBeAnOffer() {
        assertThat(may("VM-HDFCBK-S", "Enjoy 10% off on Myntra with your HDFC Bank Credit Card this weekend. T&C apply.", CategoryGroup.OFFERS)).isTrue()
    }

    @Test
    fun whenTheWordsSayNothingTheModelDecides() {
        // Only the sender is known: nothing in the text points anywhere.
        for (g in listOf(CategoryGroup.OFFERS, CategoryGroup.UPDATES, CategoryGroup.SHOPPING)) {
            assertThat(may("VM-ZOMATO-S", "Your Zomato experience matters to us.", g)).isTrue()
        }
    }
}
