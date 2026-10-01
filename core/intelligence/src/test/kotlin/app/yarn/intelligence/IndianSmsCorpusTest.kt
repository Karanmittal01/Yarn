package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Realistic Indian SMS, modelled on what banks, shops, delivery apps, telcos and government
 * services actually send (DLT headers with -P/-S/-T/-G suffixes). Each must land in the group a
 * person would expect. This is the yardstick for categorisation changes.
 */
class IndianSmsCorpusTest {
    private val analyzer = MessageAnalyzer()

    private data class Case(val from: String, val text: String, val expected: Set<CategoryGroup>, val contact: Boolean = false)

    private fun c(from: String, text: String, vararg expected: CategoryGroup, contact: Boolean = false) = Case(from, text, expected.toSet(), contact)

    private val corpus = listOf(
        // ---- OTP: always its own group, whoever sends it ----------------------------------------
        c("AD-SBICRD-T", "423378 is the OTP for Trxn. of INR 375.00 at AMAZON with your SBI Card XX1234. OTP is valid for 10 mins. Pls do not share with anyone", CategoryGroup.OTP),
        c("VM-HDFCBK-T", "OTP is 482913 for txn of INR 2,500.00 at FLIPKART on HDFC Bank card ending 4521. Valid till 14:32. Do not share OTP for security reasons.", CategoryGroup.OTP),
        c("AX-AMAZON-T", "123456 is your Amazon OTP. Do not share it with anyone.", CategoryGroup.OTP),
        c("JM-ZEPTON-S", "Your delivery OTP for order #ZPT1234 is 4821. Share it with the delivery partner only after receiving your order.", CategoryGroup.OTP),
        c("VK-SWIGGY-T", "Swiggy login OTP: 739201. Valid for 5 minutes. Do not share with anyone. -Swiggy", CategoryGroup.OTP),
        c("CP-UIDAI-G", "OTP for Aadhaar (XX1234) is 552310 (valid for 10 mins). Do not share this OTP with anyone. -UIDAI", CategoryGroup.OTP),
        c("JK-PHONPE-S", "783920 is your PhonePe verification code. Do not share it with anyone. PhonePe never asks for it.", CategoryGroup.OTP),
        c("GOOGLE", "G-482913 is your Google verification code.", CategoryGroup.OTP),
        c("AD-IRCTCI-T", "Your OTP for IRCTC login is 662211. Valid for 10 minutes.", CategoryGroup.OTP),
        c("VM-ICICIT-T", "Dear Customer, 908172 is your One Time Password for ICICI Bank NetBanking login. Valid for 3 mins. Do not share.", CategoryGroup.OTP),
        c("BZ-PAYTMB-T", "Your Paytm login code is 5521. Don't share it with anyone.", CategoryGroup.OTP),

        // ---- Transactions: anything a bank, card, UPI app or investment service sends ----------
        c("VM-HDFCBK-S", "Rs.2,500.00 debited from A/c XX1234 on 05-03-25 to VPA swiggy@icici (UPI Ref No 512345678901). Not you? Call 18002586161 to report. Never share OTP.", CategoryGroup.TRANSACTIONS),
        c("AD-SBICRD-S", "Rs.375.00 spent on your SBI Credit Card ending 1234 at ZEPTO on 05/03/25. Trxn. not done by you? Report at https://sbicard.com/Dispute", CategoryGroup.TRANSACTIONS),
        c("JX-HSBCIN-S", "HSBC: Rs 90.0 spent on your HSBC Credit Card XX5678 at SWIGGY on 2025-03-05 at 18:11. Avl limit Rs 1,45,210.", CategoryGroup.TRANSACTIONS),
        c("VM-ICICIT-S", "Payment of Rs 50,000.00 has been received on your ICICI Bank Credit Card Account XX1234 on 05-Mar-25. Thank you.", CategoryGroup.TRANSACTIONS),
        c("AX-IDFCFB-S", "Monthly interest of INR 23.00 earned on your IDFC FIRST Bank Savings Account XXXX1234 has been credited on 31-MAR-25.", CategoryGroup.TRANSACTIONS),
        c("JX-CDSLTX-S", "CDSL: Credit in a/c *89040843 through IPO/FPO for 107-GERMAN GREEN STEEL AND POWER LIMITED on 05-03-25", CategoryGroup.TRANSACTIONS),
        c("VM-KOTAKB-S", "Your Kotak Credit Card statement for Mar-25: Total amt due Rs 12,345, Min amt due Rs 620. Due date 20-Apr-25. Pay via Kotak app.", CategoryGroup.TRANSACTIONS),
        c("BP-AXISBK-S", "INR 1,200.00 credited to A/c no. XX4321 on 05-03-25 by IMPS from RAHUL KUMAR. Avl Bal INR 45,320.11 - Axis Bank", CategoryGroup.TRANSACTIONS),
        c("JD-PAYTMB-S", "Paid Rs.120 to Chai Point from Paytm UPI. UPI Ref: 512398765432. Check balance on Paytm app.", CategoryGroup.TRANSACTIONS),
        c("VK-BESCOM-S", "Your electricity bill of Rs 1,240 for Feb-25 is due on 12-Mar-25. Pay via BESCOM app to avoid late fee.", CategoryGroup.TRANSACTIONS),
        c("VM-SBIMFS-S", "SIP of Rs 5,000 in SBI Bluechip Fund Folio 1234567 processed at NAV 85.42 on 05-Mar-25.", CategoryGroup.TRANSACTIONS),
        c("AD-ZERODH-S", "Funds of Rs 10,000.00 added to your Zerodha account AB1234 successfully.", CategoryGroup.TRANSACTIONS),
        c("JK-CREDIN-S", "Your payment of Rs 8,250 to HDFC Bank credit card XX9012 via CRED is successful.", CategoryGroup.TRANSACTIONS),

        // ---- Offers: marketing, even from banks and shopping apps -------------------------------
        c("VM-HDFCBK-P", "Pre-approved Personal Loan of up to Rs 10,00,000 at attractive rates! Apply now in 2 mins: hdfc.bank/pl T&C apply", CategoryGroup.OFFERS),
        c("AX-ICICIB-S", "Dear Customer, you are eligible for an ICICI Bank Credit Card with lifetime free benefits. Apply now: icicibank.com/cc T&C apply", CategoryGroup.OFFERS),
        c("JX-BAJAJF-P", "Get an instant loan up to Rs 5 Lakh with minimal documents. Click to apply: bjaj.in/xyz T&C", CategoryGroup.OFFERS),
        c("JM-ZEPTON-P", "Sale is LIVE! Get flat 50% off on fruits & veggies. Order now on Zepto: zep.to/sale", CategoryGroup.OFFERS),
        c("JM-ZEPTON-S", "Sale is live on Zepto! Biggest discounts of the season on your favourite brands. Hurry, order now!", CategoryGroup.OFFERS),
        c("AD-SWIGGY-P", "Craving biryani? Get 60% OFF up to Rs 120 on your next order. Use code TRYNEW. Order now!", CategoryGroup.OFFERS),
        c("VM-FLPKRT-P", "The Big Billion Days are here! Up to 80% off on top brands. Shop now: fkrt.it/xyz", CategoryGroup.OFFERS),
        c("AX-AMAZON-P", "Great Indian Festival is LIVE! Deals on smartphones starting at Rs 6,999. Shop now: amzn.in/deals", CategoryGroup.OFFERS),
        c("JX-MYNTRA-S", "End of Reason Sale ends tonight! Extra 10% off on 5000+ styles. Shop now: myntr.it/eors", CategoryGroup.OFFERS),
        c("VK-ZOMATO-S", "Hungry? Flat Rs 100 off on orders above Rs 299 today only. Order now on Zomato!", CategoryGroup.OFFERS),
        c("JD-BLNKIT-S", "Diwali deals are here! Free delivery on all orders above Rs 199. Order now on Blinkit.", CategoryGroup.OFFERS),
        c("VM-JIOINF-P", "Recharge with Rs 349 and get unlimited 5G data plus JioHotstar free. Recharge now: jio.com/r", CategoryGroup.OFFERS),
        c("AD-NYKAA-P", "Your cart misses you! Get an extra 15% off on beauty bestsellers. Hurry, offer ends at midnight.", CategoryGroup.OFFERS),

        // ---- Shopping: orders, food, groceries, deliveries, refunds -----------------------------
        c("JM-ZEPTON-S", "Dear Customer, Thank you for ordering from Zepto. Your order is about to be delivered. Amount paid Rs 349", CategoryGroup.SHOPPING),
        c("AX-AMAZON-S", "Shipped: Your Amazon package with boAt Airdopes 141 will be delivered Tue, 7 Mar. Track: amzn.in/d/abc", CategoryGroup.SHOPPING),
        c("VM-FLPKRT-S", "Delivered: Your Flipkart order for realme Narzo 70 was delivered today. Rate your experience: fkrt.it/r", CategoryGroup.SHOPPING),
        c("AD-SWIGGY-S", "Your Swiggy order from Behrouz Biryani is on the way! Track it live: swig.gy/t", CategoryGroup.SHOPPING),
        c("VK-ZOMATO-S", "Your Zomato order has been delivered. Enjoy your meal!", CategoryGroup.SHOPPING),
        c("JD-BLNKIT-S", "Your Blinkit order #12345 has been packed and will arrive in 8 minutes.", CategoryGroup.SHOPPING),
        c("AD-BIGBSK-S", "Your bigbasket order BB123456 is scheduled for delivery tomorrow between 7-9 AM.", CategoryGroup.SHOPPING),
        c("VM-DLHVRY-S", "Your shipment AWB 1234567890 from Myntra is out for delivery today. Delivery agent: Ramesh", CategoryGroup.SHOPPING),
        c("AX-MYNTRA-S", "Refund of Rs 1,299 for your return of order 1234567 has been initiated to your original payment method.", CategoryGroup.SHOPPING),
        c("VM-NYKAAF-S", "Your Nykaa order NYK1234567 has been dispatched and will reach you by 9 Mar.", CategoryGroup.SHOPPING),
        c("AD-JIOMRT-S", "Your JioMart order OD12345 is confirmed. We will notify you when it is shipped.", CategoryGroup.SHOPPING),
        c("VK-SWIGGY-S", "Rs 89 has been credited to your Swiggy Money as a refund for order #1234567.", CategoryGroup.SHOPPING),
        c("AX-XPRSBS-S", "Your XpressBees shipment 13579246801 is in transit and will be delivered by tomorrow.", CategoryGroup.SHOPPING),

        // ---- Updates: travel, government, telecom service notices, appointments ----------------
        c("AD-IRCTCI-S", "PNR 4521367890: Train 12951 Coach B2 Berth 34 confirmed. Departure 16:55 on 12-03-25.", CategoryGroup.UPDATES),
        c("VM-INDIGO-S", "Web check-in is open for your IndiGo flight 6E 2134 BLR-DEL on 12 Mar, 06:10. Check in: goindigo.in", CategoryGroup.UPDATES),
        c("AX-UBERIN-S", "Your Uber driver Ramesh is arriving in a white Swift DL1AB1234.", CategoryGroup.UPDATES),
        c("JD-UIDAI-G", "Your Aadhaar update request has been successfully processed. Download e-Aadhaar from myaadhaar.uidai.gov.in", CategoryGroup.UPDATES),
        c("AD-APOLLO-S", "Your appointment with Dr. Sharma is confirmed for 12 Mar at 10:30 AM at Apollo Clinic, Indiranagar.", CategoryGroup.UPDATES),
        c("AD-AIRTEL-S", "Your Airtel prepaid plan expires in 2 days. Recharge to continue enjoying uninterrupted services.", CategoryGroup.UPDATES, CategoryGroup.TRANSACTIONS),

        // ---- Harder cases ---------------------------------------------------------------------
        c("VM-AIRTEL-P", "Get 3 months of Apple Music free with Airtel Black. Explore now: i.airtel.in/black", CategoryGroup.OFFERS),
        c("AD-PHRMSY-S", "Your PharmEasy order PE1234567 has been delivered. Get well soon!", CategoryGroup.SHOPPING),
        c("VM-DOMINO-S", "Your Domino's order is out for delivery and will reach you in 15 mins.", CategoryGroup.SHOPPING),
        c("AD-DOMINO-P", "Buy 1 Get 1 free on all medium pizzas this weekend! Order now: dominos.co.in", CategoryGroup.OFFERS),
        c("AX-LENSKT-P", "Flat 50% off on eyeglasses this week. Visit your nearest Lenskart store or shop online.", CategoryGroup.OFFERS),
        c("VK-HDFCBK-S", "Your HDFC Bank Credit Card XX1234 has been blocked as requested. A new card will be dispatched in 7 days.", CategoryGroup.TRANSACTIONS),
        c("AD-SBIINB-S", "Dear Customer, periodic KYC update for your SBI account is due. Please visit your home branch with your documents.", CategoryGroup.TRANSACTIONS),
        c("JD-OLACAB-S", "Your Ola ride is confirmed. Driver Suresh, KA01AB1234, arriving in 4 mins.", CategoryGroup.UPDATES),
        c("VM-MMTRIP-P", "Flat 25% off on domestic flights! Use code MMTSALE. Book now on MakeMyTrip.", CategoryGroup.OFFERS),
        c("VM-BMSHOW-S", "Your BookMyShow booking for Pushpa 2 at PVR Forum on 12 Mar, 7:30 PM is confirmed. Booking ID ABC1234.", CategoryGroup.UPDATES, CategoryGroup.SHOPPING),
        c("AX-CANBNK-T", "Your a/c XX1234 debited for Rs.2000.00 on 05-03-25 for ATM withdrawal. -Canara Bank", CategoryGroup.TRANSACTIONS),
        c("VM-XYZABC-P", "Visit our showroom this weekend for the Diwali bonanza on sofas and beds!", CategoryGroup.OFFERS),
        c("AD-FRSHMN-S", "Your order #789123 has been delivered. Thanks for shopping with FreshMeat!", CategoryGroup.SHOPPING),
        c("VM-ITDEPT-G", "Your income tax refund of Rs 12,345 for AY 2024-25 has been credited to your bank account.", CategoryGroup.UPDATES, CategoryGroup.TRANSACTIONS),
        c("AD-LICIND-S", "Premium of Rs 25,000 for LIC policy 123456789 is due on 15-Mar-25. Pay online at licindia.in", CategoryGroup.TRANSACTIONS),
        c("JK-MEESHO-P", "Abhi order karein aur paayein 50% ki chhoot! Offer sirf aaj ke liye. Shop now: msho.in/s", CategoryGroup.OFFERS),
        c("AX-AMAZON-S", "Delivered: your package was delivered today. Get 10% cashback on your next order with Amazon Pay.", CategoryGroup.SHOPPING),
        c("VM-ICICIT-S", "INR 500.00 debited from A/c XX1234 on 05-Mar-25 to VPA zomato@hdfc. Get a pre-approved loan in 3 seconds on iMobile.", CategoryGroup.TRANSACTIONS),
        c("JD-KOTAKB-P", "Upgrade to Kotak 811 Super and earn 5% cashback on all spends. Open now: kotak.com/811", CategoryGroup.OFFERS),
        c("AD-TATACQ-S", "Your Tata CLiQ order 1234-5678 has been shipped via Delhivery. Track: tatacliq.com/t", CategoryGroup.SHOPPING),

        // ---- Personal -----------------------------------------------------------------------
        c("+919812345678", "hey are we still on for dinner tonight?", CategoryGroup.PERSONAL, contact = true),
        c("+919800000001", "Hi, this is Ravi from the society office. Water supply will be off tomorrow 10 to 2.", CategoryGroup.PERSONAL),
        c("+919812345670", "Bhai 5000 bhej de, kal wapas kar dunga", CategoryGroup.PERSONAL, contact = true),
        c("+919812345671", "Sent you 500 for the cab, check", CategoryGroup.PERSONAL, contact = true),
    )

    @Test
    fun everyMessageLandsInTheExpectedGroup() {
        val misses = corpus.mapNotNull { case ->
            val a = analyzer.analyze(MessageInput(case.text, SenderInfo(case.from, isContact = case.contact), 1_741_000_000_000L))
            val group = a.category.group
            if (group in case.expected) null
            else "${case.from}: got ${a.category} (${group}) want ${case.expected} — ${a.classification.reasons} — “${case.text.take(70)}”"
        }
        assertWithMessage("Misfiled ${misses.size}/${corpus.size}:\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
