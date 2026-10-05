package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The rule people expect: a message is filed by WHAT IT IS, not by who sent it.
 *  - OTP: carries a one-time / verification code.
 *  - TRANSACTIONS: money moved or is owed — debits, credits, payments, wallet and gift-card balances,
 *    refunds, cashback credited, bills — whether the sender is a bank, Zomato, Swiggy or Amazon.
 *  - SHOPPING: an order's journey — placed, packed, shipped, out for delivery, delivered, failed
 *    delivery, return pickup.
 *  - OFFERS: advertising of any kind. A -P sender header is always an offer.
 *  - UPDATES: travel, rides, appointments, government, telecom and app service notices.
 *  - PERSONAL: people.
 * Written separately from the rules, with real-world wording, to measure rather than memorise.
 */
class MessageTypeCorpusTest {
    private val analyzer = MessageAnalyzer()

    private data class Case(val from: String, val text: String, val want: Set<CategoryGroup>)

    private val P = CategoryGroup.PERSONAL
    private val O = CategoryGroup.OTP
    private val T = CategoryGroup.TRANSACTIONS
    private val S = CategoryGroup.SHOPPING
    private val U = CategoryGroup.UPDATES
    private val F = CategoryGroup.OFFERS

    private fun c(from: String, text: String, vararg want: CategoryGroup) = Case(from, text, want.toSet())

    private val cases = listOf(
        // ---- From the user's own inbox ------------------------------------------------------------
        c("JD-ZOMATO-S", "Payment of Rs. 185.69 from Zomato Money is successful for your order #7654321. Available balance: Rs. 314.31", T),
        c("JD-ZOMATO-S", "Payment of Rs. 89.69 from Zomato Money is successful. Zomato Money balance: Rs. 500.00", T),
        c("JX-SWIGGY-S", "Your Swiggy Money is credited with a Gift card worth Rs. 500. Use it on your next order.", T),
        c("59039465", "A gift card has ben added to your Uber account. Your new balance is Rs 250.", T),
        c("JK-QWKCLR-S", "Dear Customer, your Amazon Pay Gift Card of Rs 1000 has been added to your Amazon Pay balance.", T),
        c("JK-MYNTRA-S", "Hi Karan! We were unable to deliver your order today. We will attempt delivery again tomorrow.", S),
        c("VK-SWIGGY-S", "Rs. 73.00 debited from your Swiggy Money wallet for order #123456789. Balance: Rs. 427.00", T),
        c("VM-SWIGGY-S", "Rs. 79.00 debited from your Swiggy Money for Instamart order. Avl balance Rs 348", T),
        c("JM-HSBCIN-S", "HSBC: Rs 280.0 spent on your HSBC Credit Card XX1234 at ZOMATO on 01-10-26. Avl limit Rs 1,23,456.", T),
        c("JX-VZY-T", "Dear Vzy user, your OTP is 345612. Do not share it with anyone.", O),

        // ---- Merchant wallets, refunds, cashback, gift cards -> Transactions ------------------------
        c("AX-AMAZON-S", "Refund of Rs 1,499.00 for your Amazon order 405-1234567 has been credited to your Amazon Pay balance.", T),
        c("VM-FLPKRT-S", "Rs. 2,349 has been refunded to your HDFC Bank card ending 1234 for your cancelled Flipkart order.", T),
        c("AD-ZEPTON-S", "Rs 45 cashback has been credited to your Zepto Cash wallet.", T),
        c("JM-PAYTMB-S", "Rs 120 paid to Swiggy from Paytm Wallet. Wallet balance Rs 880.", T),
        c("AD-PHONPE-S", "Paid Rs.349 to Zomato via PhonePe UPI. UPI Ref 512345678901.", T),
        c("VM-GPAYIN-S", "You paid Rs 210 to Blinkit using Google Pay. UPI transaction ID 612345678901.", T),
        c("JX-AMZPAY-S", "Your Amazon Pay balance has been credited with Rs 50 cashback for your recharge.", T),
        c("VK-ZOMATO-S", "Your refund of Rs 249 for order #889912 has been processed to your original payment method.", T),
        c("AD-MYNTRA-S", "Myntra Credit of Rs 300 has been added to your account against your return.", T),
        c("JK-OLAMNY-S", "Rs 150 debited from your Ola Money wallet for your ride. Balance Rs 50.", T),
        c("VM-UBERIN-S", "Rs 312 has been charged to your card for your Uber trip on 3 Oct.", T),
        c("AD-IRCTCI-S", "Refund of Rs 1,240 for cancelled PNR 4521367890 has been initiated to your bank account.", T),
        c("JD-BLNKIT-S", "Rs 30 has been credited to your Blinkit wallet as compensation for the delay.", T),
        c("VK-DOMINO-S", "Payment of Rs 549 received for your Domino's order. Thank you!", T, S),

        // ---- Orders and deliveries -> Shopping -----------------------------------------------------
        c("JD-ZOMATO-S", "Your order from Biryani Blues is being prepared. Track it on the app.", S),
        c("VK-SWIGGY-S", "Your Instamart order has been delivered. Hope you enjoy it!", S),
        c("AD-ZEPTON-S", "Your Zepto order is on its way and will reach you in 6 minutes.", S),
        c("AX-AMAZON-S", "Your Amazon order of Apple AirPods Pro has been dispatched. Expected delivery: Fri, 4 Oct.", S),
        c("VM-FLPKRT-S", "Your Flipkart order is out for delivery. Our delivery agent will call you before arriving.", S),
        c("JK-MEESHO-S", "Your Meesho order #MSH12345 has been packed and will be shipped soon.", S),
        c("AD-AJIOAP-S", "Return pickup for your AJIO order FN1234567 is scheduled for tomorrow between 10 AM and 6 PM.", S),
        c("VM-NYKAAF-S", "Your Nykaa order has been delivered. Rate your purchase and help others decide.", S),
        c("JX-BIGBSK-S", "Your bigbasket order BB98765 will be delivered tomorrow between 7 and 9 AM.", S),
        c("AD-DLHVRY-S", "Delhivery: Shipment AWB 21345678901 for your Myntra order will be delivered today.", S),
        c("VK-EKARTL-S", "Your Ekart shipment could not be delivered as you were unavailable. We will try again tomorrow.", S),
        c("JM-BLUDRT-S", "Blue Dart: Your shipment 81234567890 is in transit and will be delivered by 5 Oct.", S),
        c("AD-PHRMSY-S", "Your PharmEasy order has been dispatched and will reach you tomorrow.", S),
        c("JD-1MGTAT-S", "Your Tata 1mg order has been placed successfully. Order ID: 1MG998877.", S),
        c("VM-DOMINO-S", "Your Domino's order is being prepared and will be delivered in 30 mins.", S),
        c("JX-JIOMRT-S", "Your JioMart order has been shipped. Track your order: jiomart.com/t", S),
        c("AD-LENSKT-S", "Your Lenskart order is ready for pickup at our Koramangala store.", S),
        c("VK-ZOMATO-S", "Order delivered! Enjoy your meal from Behrouz Biryani.", S),
        c("AX-FIRSTC-S", "Your FirstCry order has been cancelled as requested.", S),

        // ---- Offers: always for -P, and when -S messages are really ads ----------------------------
        c("JD-ZOMATO-P", "Your favourite restaurant is offering 50% OFF today. Order now!", F),
        c("AD-SWIGGY-P", "Free delivery on all orders above Rs 99 this weekend. Order now on Swiggy!", F),
        c("VM-AMAZON-P", "Prime Day is here! Up to 70% off on electronics. Shop now: amzn.in/prime", F),
        c("JK-FLPKRT-P", "Your order could be FREE! Shop today and win back your order value. T&C", F),
        c("VM-HDFCBK-P", "Your HDFC Bank Credit Card limit can be increased to Rs 5,00,000. Apply now.", F),
        c("AD-ICICIB-P", "Get a personal loan of up to Rs 25 Lakh at 10.5% p.a. Apply in 2 mins.", F),
        c("JX-JIOINF-P", "Recharge now with Rs 299 and get 2GB/day + unlimited calls for 28 days.", F),
        c("AD-AIRTEL-P", "Your OTT pack is ready! Get Netflix with Airtel Black. Upgrade now.", F),
        c("VM-PAYTMB-P", "Pay your electricity bill on Paytm and get Rs 100 cashback. Pay now!", F),
        c("JD-MYNTRA-S", "Your wishlist items are now on sale! Up to 60% off. Shop before they're gone.", F),
        c("VK-ZEPTON-S", "Get flat Rs 100 off on your next order above Rs 499. Use code ZEPTO100.", F),
        c("AD-SWIGGY-S", "Hungry? Your favourite dishes are just a tap away. Get 40% off up to Rs 80.", F),
        c("JX-AMAZON-S", "Deals you will love: up to 40% off on home & kitchen. Shop now.", F),
        c("VM-BLNKIT-S", "New on Blinkit: Get iPhone 16 delivered in 10 minutes! Order now.", F),
        c("AD-KOTAKB-S", "Dear Customer, you are pre-approved for a Kotak Credit Card with lifetime free benefits. Apply now: kotak.com/cc", F),
        c("JM-SBICRD-S", "Convert your recent spend of Rs 15,000 into easy EMIs at low interest. Click to convert: sbicard.com/emi", F, T),
        c("AD-BAJAJF-S", "Your pre-approved loan of Rs 3,00,000 is ready. Get it in your account instantly. T&C apply.", F),
        c("VM-MMTRIP-S", "Fly to Goa from Rs 2,999! Use code MMTGOA. Book now on MakeMyTrip.", F),
        c("JD-NYKAAF-S", "Your cart is waiting! Complete your order now and get 15% off.", F),
        c("AD-DOMINO-S", "Weekend treat! Buy 1 Get 1 free on medium pizzas. Order now.", F),
        c("VM-FLPKRT-P", "Your order is shipped. Meanwhile, check out Big Billion Days deals!", F),
        c("AX-AJIOAP-P", "Thank you for shopping with us. Here's 20% off on your next purchase.", F),
        c("VM-CROMAR-S", "Diwali Dhamaka: Up to Rs 10,000 instant discount on TVs. Visit your nearest Croma store.", F),

        // ---- OTPs ----------------------------------------------------------------------------------
        c("VM-SBIINB-T", "OTP for your SBI internet banking login is 774411. Do not share this with anyone.", O),
        c("AD-HDFCBK-T", "487263 is OTP for txn of INR 1,200.00 at ZOMATO on HDFC Bank Card XX1234. Valid till 21:30. Do not share OTP.", O),
        c("JX-ZOMATO-T", "Your Zomato verification code is 9182. Don't share it with anyone.", O),
        c("VK-SWIGGY-T", "Use 3344 as your OTP to log in to Swiggy.", O),
        c("AD-AMAZON-T", "Your one-time password for Amazon is 551200. Do not share it.", O),
        c("JD-FLPKRT-T", "Your Flipkart delivery OTP is 4512. Share it with the delivery agent only after you receive your order.", O),
        c("VM-PHONPE-T", "Use OTP 908877 to link your bank account to PhonePe. Never share it.", O),
        c("AX-IRCTCI-T", "Your IRCTC OTP for booking ticket is 337721. Valid for 10 minutes.", O),
        c("JK-UIDAI-G", "Your Aadhaar OTP is 615243 for e-KYC. Do not share it with anyone. -UIDAI", O),
        c("AD-ZERODH-T", "Your Zerodha Kite login OTP is 612347. It is valid for 5 minutes.", O),
        c("VM-GOOGLE", "G-731942 is your Google verification code.", O),
        c("WHATSAPP", "Your WhatsApp code: 412-598. Don't share this code with others.", O),
        c("VM-MSFTOT-T", "Use 5532 as Microsoft account security code.", O),
        c("AD-CREDIN-T", "CRED: 908172 is your login OTP. Valid for 10 mins.", O),
        c("JX-ICICIT-T", "Dear Customer, OTP to complete your transaction of INR 2,999 at MYNTRA is 456789. Do not share it with anyone.", O),

        // ---- Bank and money notices -> Transactions --------------------------------------------------
        c("VM-SBIINB-S", "Dear SBI User, your A/c X1234 debited by Rs 500.00 on 01Oct26 transfer to RAHUL Ref No 612345678901.", T),
        c("JX-ICICIT-S", "ICICI Bank Acct XX123 credited with Rs 45,000.00 on 01-Oct-26; NEFT from ACME PVT LTD. Avl Bal Rs 1,02,340.", T),
        c("AD-AXISBK-S", "Spent INR 2,340 on Axis Bank Card no. XX1234 at SWIGGY on 01-10-26. Avl Lmt INR 87,660.", T),
        c("VM-KOTAKB-S", "Sent Rs.250.00 from Kotak Bank AC X1234 to zepto@ybl on 01-10-26. UPI Ref 612345678901.", T),
        c("JD-IDFCFB-S", "Your IDFC FIRST Bank credit card bill of Rs 18,320 is due on 05-Oct-26. Min due Rs 920.", T),
        c("AD-HDFCBK-S", "EMI of Rs 4,200 for your HDFC Bank loan has been debited from A/c XX1234.", T),
        c("VK-TATAPW-S", "Your Tata Power bill of Rs 1,860 for Sep-26 is due on 10-Oct-26.", T),
        c("JX-AIRTEL-S", "Your Airtel postpaid bill of Rs 599 is due on 08-Oct. Pay at airtel.in/pay", T),
        c("AD-JIOINF-S", "Recharge of Rs 349 is successful for Jio number 98XXXXXX12. Validity till 29-Oct-26.", T),
        c("VM-LICIND-S", "Premium of Rs 12,500 received for LIC policy 123456789. Thank you.", T),
        c("JM-GROWWS-S", "SIP of Rs 2,000 in Parag Parikh Flexi Cap Fund has been processed. Units will be allotted at NAV of 01-Oct.", T),
        c("AD-CDSLTX-S", "CDSL: Debit in a/c *89040843 for 10 shares of INFY on 01-10-26.", T),
        c("VK-PAYTMB-S", "Rs 65 deducted from your Paytm FASTag for toll at Kherki Daula. Balance Rs 435.", T),
        c("JD-HDFCBK-S", "Your HDFC Bank Debit Card XX1234 has been blocked as per your request.", T, U),
        c("VM-ICICIB-S", "Your ICICI Bank Credit Card XX5678 statement for Sep-26 is now available. Total due Rs 9,870.", T),
        c("AD-SBIINB-S", "Dear Customer, interest of Rs 412 has been credited to your SBI savings account XX1234.", T),

        // ---- Updates --------------------------------------------------------------------------------
        c("AD-IRCTCI-S", "PNR 2345678901 Train 12627 Dt 05-10-26 Coach S5 Seat 34 confirmed. Have a happy journey.", U),
        c("VM-INDIGO-S", "Your IndiGo flight 6E 512 from BLR to DEL on 05 Oct is on time. Gate closes 25 mins before departure.", U),
        c("JD-OLACAB-S", "Your Ola driver Ravi is arriving in 3 mins in a white Dzire KA05AB1234.", U),
        c("AD-RAPIDO-S", "Your Rapido captain Suresh is on the way. Bike KA01XY9876.", U),
        c("VM-APOLLO-S", "Your appointment with Dr. Rao at Apollo Hospital is confirmed for 5 Oct, 11:00 AM.", U),
        c("JK-UIDAI-G", "Your Aadhaar has been successfully updated. Download your updated Aadhaar from myaadhaar.uidai.gov.in", U),
        c("VM-EPFOHO-G", "Your PF passbook has been updated for Sep-26.", U),
        c("AD-JIOINF-S", "You have used 90% of your daily data. Speed will be reduced after 100%.", U),
        c("VM-AIRTEL-S", "Your Airtel number will be on DND from tomorrow as requested.", U),
        c("JD-BMSHOW-S", "Your tickets for Stree 3 at PVR Phoenix on 5 Oct, 7:30 PM are confirmed. Booking ID BMS1234.", U, S),
        c("AD-URBNCO-S", "Your Urban Company professional Anil will arrive at 4 PM for AC servicing.", U, S),
        c("VM-GOOGLE", "Your Google Account was signed in on a new Windows device. If this was you, no action is needed.", U),
        c("JX-DIGILK-G", "Your driving licence has been added to your DigiLocker.", U),
        c("VM-PARIVH-G", "Your vehicle registration KA01AB1234 insurance expires on 10-Oct-26.", U, T),

        // ---- People ---------------------------------------------------------------------------------
        c("+919811112222", "Order kar diya maine, 8 baje tak aa jayega", P),
        c("+919811113333", "Paid you 500 for the movie tickets", P),
        c("+919811114444", "Bhai mera OTP aaya hoga kya? Check karke batana", P),
        c("+919811115555", "Sale lagi hai Zara mein, chalein kal?", P),
        c("+919811116666", "Mom, I've reached the office. Will call at lunch.", P),
    )

    @Test
    fun filedByWhatTheMessageIs() {
        val misses = cases.mapNotNull { (from, text, want) ->
            val contact = from.startsWith("+")
            val a = analyzer.analyze(MessageInput(text, SenderInfo(from, isContact = contact), 1_790_000_000_000L))
            val g = a.category.group
            if (g in want) null else "$from: got ${a.category} ($g) want $want — ${a.classification.reasons} — “${text.take(80)}”"
        }
        assertWithMessage("Misfiled ${misses.size}/${cases.size}:\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
