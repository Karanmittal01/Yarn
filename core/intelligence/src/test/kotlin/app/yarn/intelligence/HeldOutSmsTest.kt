package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Messages written after the rules were tuned, to check they generalise rather than memorise. */
class HeldOutSmsTest {
    private val analyzer = MessageAnalyzer()
    private val G = CategoryGroup.entries.associateBy { it.name }

    private val cases = listOf(
        Triple("VM-YESBNK-T", "Your OTP for Yes Bank NetBanking login is 336721. It is valid for 5 mins.", "OTP"),
        Triple("AD-MYNTRA-T", "Use 4419 as your Myntra login OTP. Do not share it with anyone.", "OTP"),
        Triple("JX-CRED-S", "571036 is your CRED verification code. Code is valid for 10 minutes.", "OTP"),
        Triple("VK-UBERIN-T", "Your Uber code: 8835. Never share this code.", "OTP"),
        Triple("TX-DIGILK-G", "Your DigiLocker OTP is 902817. Valid for 10 minutes.", "OTP"),
        Triple("VM-BOBTXN-S", "Rs.15000.00 transferred from A/c ...6789 to:IMPS/P2A/512345678901/RAVI. Total Bal:Rs.84500.00CR. Avlbl Amt:Rs.84500.00 -Bank of Baroda", "TRANSACTIONS"),
        Triple("AD-PNBSMS-S", "Your A/c XXXXXXXX4521 is credited by Rs.25,000.00 on 01-04-2025 (NEFT-SALARY). Aval Bal Rs.1,02,340.50 -PNB", "TRANSACTIONS"),
        Triple("JD-AMEXIN-S", "Alert: You've spent INR 4,560.00 on your AMEX card ** 12345 at MAKEMYTRIP on 4 April 2025.", "TRANSACTIONS"),
        Triple("VM-GROWWS-S", "Your order to buy 10 shares of TATAMOTORS at Rs 652.30 has been executed. - Groww", "TRANSACTIONS"),
        Triple("AX-PHONPE-S", "Received Rs.500 from Amit Sharma in your Bank of Baroda a/c XX6789 via PhonePe. UPI Ref 412345678912", "TRANSACTIONS"),
        Triple("VK-TATAPW-S", "Dear consumer, bill of Rs 2,315.00 for CA No 60001234567 is due on 18-Apr-25. Pay now: tatapower.com", "TRANSACTIONS"),
        Triple("JK-AXISBK-P", "Get up to Rs 1,500 off on flights with Axis Bank Credit Cards on MakeMyTrip this weekend! T&C apply", "OFFERS"),
        Triple("VM-AJIOAP-P", "AJIO Big Bold Sale: 50-90% off on 5 lakh+ styles. Starts midnight! Shop: ajio.me/s", "OFFERS"),
        Triple("AD-SWIGGY-S", "Free delivery on your next 3 Instamart orders above Rs 199! Valid till Sunday. Order now.", "OFFERS"),
        Triple("JD-ZOMATO-P", "Your favourite biryani is now 40% off! Order before 11 PM tonight.", "OFFERS"),
        Triple("BZ-UPSTOX-P", "Open a free demat account in 10 mins and get Rs 0 brokerage on delivery trades. Open now: upstox.com", "OFFERS"),
        Triple("VM-VIINDI-P", "Hero Unlimited plan at Rs 365 with unlimited calls & data delight. Recharge now on Vi App!", "OFFERS"),
        Triple("AD-BLINKT-S", "Your order is on the way! Delivery partner Rahul will reach you in 6 minutes.", "SHOPPING"),
        Triple("VK-AMAZON-S", "Return pickup for your Amazon order 404-1234567-1234567 is scheduled for tomorrow. Please keep the item ready.", "SHOPPING"),
        Triple("JM-FLPKRT-S", "Your Flipkart order of Samsung Galaxy M35 is out for delivery. It will be delivered today by 9 PM.", "SHOPPING"),
        Triple("AD-1MGTAT-S", "Your Tata 1mg order 1MG12345 has been packed and will be shipped shortly.", "SHOPPING"),
        Triple("VM-EKARTL-S", "Your Ekart shipment FMPP1234567890 will be delivered today. Delivery executive: Sunil, 98xxxxxx12", "SHOPPING"),
        Triple("JX-ZEPTON-S", "Order delivered! Thank you for shopping with Zepto. Rate your delivery experience.", "SHOPPING"),
        Triple("AD-REDBUS-S", "Ticket confirmed: Bangalore to Chennai, 12 Apr, 22:30. Seat 14L. Bus: SRS Travels. PNR TQ1234567", "UPDATES"),
        Triple("VM-AIRIND-S", "Your Air India flight AI 505 BLR-DEL on 12 Apr is delayed. New departure 21:40.", "UPDATES"),
        Triple("JD-EPFOHO-G", "Your claim for EPF withdrawal has been settled. Amount will reach your bank account in 3 working days.", "UPDATES"),
        Triple("AD-PRACTO-S", "Reminder: Your appointment with Dr. Mehta is tomorrow at 11:00 AM at Practo Care Clinic.", "UPDATES"),
        Triple("+919811112222", "Reached home, call you in 10 mins", "PERSONAL"),
        Triple("+919811113333", "Kal office aa raha hai kya?", "PERSONAL"),
        Triple("+919811114444", "Happy birthday bhai! Party kab de raha hai?", "PERSONAL"),
    )

    @Test
    fun heldOutMessagesLandInTheExpectedGroup() {
        val misses = cases.mapNotNull { (from, text, want) ->
            val contact = from.startsWith("+")
            val a = analyzer.analyze(MessageInput(text, SenderInfo(from, isContact = contact), 1_743_500_000_000L))
            if (a.category.group == G[want]) null else "$from: got ${a.category} want $want — ${a.classification.reasons} — “${text.take(70)}”"
        }
        assertWithMessage("Held-out misses ${misses.size}/${cases.size}:\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
