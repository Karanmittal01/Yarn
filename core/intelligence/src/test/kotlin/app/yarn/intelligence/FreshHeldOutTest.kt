package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Messages written after the rules were tuned, never used to tune them: an honest accuracy check. */
class FreshHeldOutTest {
    private val analyzer = MessageAnalyzer()
    private val P = CategoryGroup.PERSONAL
    private val O = CategoryGroup.OTP
    private val T = CategoryGroup.TRANSACTIONS
    private val S = CategoryGroup.SHOPPING
    private val U = CategoryGroup.UPDATES
    private val F = CategoryGroup.OFFERS
    private fun c(from: String, text: String, vararg want: CategoryGroup) = Triple(from, text, want.toSet())

    private val cases = listOf(
        c("VM-UNIONB-S", "Union Bank never asks for your account details over phone. Don't fall for such calls.", U),
        c("AD-BOIIND-S", "Beware of loan apps asking for advance fees. Bank of India does not charge any fee before loan approval.", U),
        c("JD-HSBCIN-S", "HSBC will never ask you to transfer money to a 'safe account'. Hang up on such calls.", U),
        c("VK-ICICIB-S", "Get up to Rs 2,000 Amazon voucher on your ICICI Bank Credit Card spends of Rs 50,000 this month. Register now.", F),
        c("AX-HDFCBK-S", "Your HDFC Bank PIXEL Credit Card offers 5% cashback on Swiggy and Zomato. Apply now in 2 mins.", F),
        c("JM-KOTAKB-S", "Earn 4x reward points on your Kotak Credit Card this festive season. T&C apply.", F),
        c("VM-SBIINB-S", "Pre-approved Home Loan top-up of Rs 15 lakh is available for you. Visit YONO to avail.", F),
        c("AD-AXISBK-S", "Rs.1,250.00 debited from A/c no. XX4321 on 05-10-26 for UPI/ZEPTO. Bal Rs.12,340.00", T),
        c("JD-ICICIB-S", "INR 50,000.00 credited to your A/c XX123 on 05-Oct-26 by IMPS from SHARMA. Avl Bal INR 1,02,345", T),
        c("VK-HDFCBK-S", "Your HDFC Bank Credit Card XX2774 payment of Rs 45,994 is due tomorrow. Pay now to avoid late charges.", T),
        c("AX-SBICRD-S", "Payment of Rs 5,000 received for your SBI Card XX95. Available limit Rs 1,45,000.", T),
        c("JM-AMEXIN-S", "Your Amex statement is ready. Total amount due INR 23,450, minimum due INR 1,200, due by 25 Oct.", T),
        c("VM-KOTAKB-S", "Your Kotak debit card XX9876 has been hotlisted as requested. Request a new card from the app.", U, T),
        c("AD-YESBNK-S", "Your YES BANK mobile banking was activated on a new device on 05-Oct-26 10:22. Not you? Call 18001200.", U),
        c("JD-AXISBK-S", "Your Axis Bank FD of Rs 1,00,000 has been booked successfully for 1 year at 7% p.a.", T, U),
        c("VK-SBIINB-S", "Your OTP for SBI YONO registration is 448120. Valid for 5 minutes. Do not share.", O),
        c("AX-ZOMATO-T", "8812 is your OTP for Zomato. Valid for 5 minutes.", O),
        c("JM-PAYTMB-S", "You've received Rs 2,500 from Amit Kumar in your Paytm Payments Bank account.", T),
        c("VM-PHONPE-S", "Rs 1,499 paid to Myntra via PhonePe. Txn ID T2610051234.", T),
        c("AD-FLPKRT-S", "Your Flipkart order for Nike shoes has been delivered. Hope you love it!", S),
        c("JD-AMAZON-S", "Arriving today: Your Amazon package with Philips trimmer. Track your package.", S),
        c("VK-SWIGGY-S", "Your Instamart order is packed and will be with you in 9 mins.", S),
        c("AX-MYNTRA-S", "Your return request for order 12345 has been approved. Pickup tomorrow.", S),
        c("JM-ZEPTON-S", "Free delivery on all orders today only! Order fresh vegetables on Zepto now.", F),
        c("VM-SWIGGY-S", "Weekend special: Flat Rs 100 off on orders above Rs 299. Use code WKND100.", F),
        c("AD-AIRTEL-S", "Upgrade to Airtel Xstream Fiber and get 3 months free. Book now.", F),
        c("JD-JIOINF-S", "Your daily data quota of 1.5GB is exhausted. Recharge with data booster.", U, F),
        c("VK-IRCTCI-S", "PNR 1234567890 waitlist status changed to CNF. Coach B3 Berth 22.", CategoryGroup.TRAVEL),
        c("AX-INDIGO-S", "Your boarding pass for 6E 2134 is ready. Gate 14, boarding at 18:10.", CategoryGroup.TRAVEL),
        c("JM-UIDAI-G", "Your Aadhaar biometric lock has been enabled successfully.", U),
        c("VM-ITDEPT-G", "Your PAN has been linked with Aadhaar successfully.", U),
        c("AD-MAXLIF-S", "Your Max Life policy 123456789 premium of Rs 18,000 is due on 20-Oct-26.", T),
        c("JD-STARHL-S", "Your Star Health claim of Rs 42,000 has been settled to your bank account.", T, U),
        c("VK-TATAPW-S", "Electricity supply will be disrupted in Sector 15 on 07-Oct 10AM-1PM for maintenance.", U),
        c("AX-HDFCBK-P", "Get FD rates up to 7.4% p.a. Book an FD on HDFC Bank MobileBanking now.", F),
        c("+919811120001", "Haan main aa raha hoon, 10 min mein pahunch jaunga", P),
        c("+919811120002", "Thanks for the gift! Loved it", P),
        c("+919811120003", "Kal office mein meeting hai 11 baje", P),
        c("+919811120004", "Did you pay the rent this month?", P),
        c("+919811120005", "Sent you the photos on WhatsApp", P),
    )

    @Test
    fun heldOut() {
        val misses = cases.mapNotNull { (from, text, want) ->
            val a = analyzer.analyze(MessageInput(text, SenderInfo(from, isContact = from.startsWith("+")), 1_790_000_000_000L))
            if (a.category.group in want) null else "$from: got ${a.category} want $want — ${a.classification.reasons} — “${text.take(80)}”"
        }
        assertWithMessage("Misfiled ${misses.size}/${cases.size}:\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
