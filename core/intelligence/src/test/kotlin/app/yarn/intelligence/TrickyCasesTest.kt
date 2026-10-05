package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Third, unseen batch: look-alikes that trip naive rules (codes in ads, money in ads, mixed messages). */
class TrickyCasesTest {
    private val analyzer = MessageAnalyzer()
    private val P = CategoryGroup.PERSONAL; private val O = CategoryGroup.OTP; private val T = CategoryGroup.TRANSACTIONS
    private val S = CategoryGroup.SHOPPING; private val U = CategoryGroup.UPDATES; private val F = CategoryGroup.OFFERS

    private val cases: List<Triple<String, String, Set<CategoryGroup?>>> = listOf(
        // Promo codes and prices are not OTPs
        Triple("AD-SWIGGY-S", "Use code SWIGGY50 to get 50% off up to Rs 100 on your next 2 orders.", setOf(F)),
        Triple("VM-ZOMATO-S", "Code TRYNEW gets you Rs 125 off. Valid on orders above Rs 159 till midnight.", setOf(F)),
        Triple("JX-MYNTRA-S", "Flat Rs 500 off on Rs 2499. Code: MYNTRA500. Valid till Sunday.", setOf(F)),
        Triple("AD-JIOINF-S", "Get 5G unlimited with Rs 399 plan. Recharge before 30 Oct.", setOf(F)),
        // Money mentioned inside ads stays an offer
        Triple("VM-HDFCBK-S", "Get up to Rs 5000 cashback on electronics with HDFC Bank Credit Cards this festive season. T&C apply.", setOf(F)),
        Triple("AD-PAYTMB-S", "Win cashback up to Rs 1000 on your first UPI payment with Paytm. Pay now!", setOf(F)),
        Triple("JD-AMZPAY-S", "Add money to Amazon Pay balance and get 5% back. Offer valid today only.", setOf(F)),
        // Mixed: order first vs money first
        Triple("VK-ZEPTON-S", "Your order #Z7788 is confirmed! Rs 412 paid via UPI. Arriving in 9 mins.", setOf(S)),
        Triple("AD-SWIGGY-S", "Rs 412 paid successfully for your Swiggy order #77881. Your food is being prepared.", setOf(T)),
        Triple("VM-AMAZON-S", "Your Amazon order has been delivered. Pay Rs 899 cash or UPI to the delivery agent.", setOf(S)),
        Triple("JX-FLPKRT-S", "Refund of Rs 799 for your Flipkart order has been processed and will reach your bank in 3-5 days.", setOf(T)),
        // Promotion from -P always wins, even with transactional-sounding words
        Triple("VM-SBIINB-P", "Your SBI account is eligible for a pre-approved car loan. Rs 8 Lakh credited instantly. Apply now.", setOf(F)),
        Triple("AD-SWIGGY-P", "Your order is waiting! Rs 0 delivery fee today only.", setOf(F)),
        Triple("JK-AIRTEL-P", "Your recharge is due. Recharge with Rs 299 & get Disney+ Hotstar free.", setOf(F)),
        // OTPs in unusual wording
        Triple("AX-PAYTMB-T", "Paytm: 4421 is the code to verify your mobile number. Never share it with anyone.", setOf(O)),
        Triple("VM-KOTAKB-T", "OTP is 778812 to authenticate txn of Rs 15,000 to RAHUL via IMPS. Valid 5 mins. Do not share.", setOf(O)),
        Triple("AD-OLACAB-T", "Your ride OTP is 4432. Share it with your driver to start the ride.", setOf(O)),
        Triple("JD-BLNKIT-T", "Blinkit: Your delivery PIN is 5521. Share only after receiving your order.", setOf(O)),
        Triple("VM-NETFLX", "Your Netflix verification code is 223344.", setOf(O)),
        // Bank alerts with OTP warnings in the footer stay Transactions
        Triple("AD-AXISBK-S", "INR 999 debited from A/c XX1234 to VPA netflix@icici. Not you? Call 18604195555. Never share your OTP/PIN.", setOf(T)),
        Triple("VM-ICICIT-S", "Rs 1,500 credited to your ICICI Bank A/c XX99 from AMIT via UPI. Do not share OTP with anyone.", setOf(T)),
        // Scams from phone numbers go to spam (group null), never to Transactions or OTP
        Triple("+919876500011", "Dear customer your SBI YONO account will be blocked today. Update PAN immediately: sbi-kyc-update.in", setOf(null)),
        Triple("+918877665544", "Congratulations! You have won Rs 25,00,000 in KBC lottery. Call now to claim your prize.", setOf(null)),
        // Service notices
        Triple("VM-AIRTEL-S", "Your Airtel Xstream Fiber will be under maintenance tomorrow 2-4 AM.", setOf(U)),
        Triple("AD-HDFCBK-S", "Dear Customer, HDFC Bank NetBanking will be unavailable on 5 Oct from 1 AM to 4 AM due to maintenance.", setOf(T, U)),
        Triple("JX-IRCTCI-S", "Your train 12951 is running late by 45 mins. Expected arrival at New Delhi 09:15.", setOf(CategoryGroup.TRAVEL)),
        Triple("VM-GOIBIB-S", "Your hotel booking at Taj Fort Aguada, Goa for 12-14 Oct is confirmed. Booking ID GO12345.", setOf(CategoryGroup.TRAVEL)),
        // People
        Triple("+919811117777", "Zomato se order karte hain aaj?", setOf(P)),
        Triple("+919811118888", "Can you send me the OTP that came on your phone?", setOf(P, null)),
        Triple("+919811119999", "Bro 2000 transfer kar de, kal wapas", setOf(P)),
    )

    @Test
    fun trickyLookAlikes() {
        val misses = cases.mapNotNull { (from, text, want) ->
            val a = analyzer.analyze(MessageInput(text, SenderInfo(from, isContact = from.startsWith("+9198111")), 1_790_000_000_000L))
            val g = a.category.group
            if (g in want) null else "$from: got ${a.category} ($g) want $want — ${a.classification.reasons} — “${text.take(80)}”"
        }
        assertWithMessage("Misfiled ${misses.size}/${cases.size}:\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
