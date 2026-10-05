package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Banks and card issuers (American Express above all) send every kind of message. Being from a bank
 * makes a message a TRANSACTION only when money moved or is owed; everything else is filed by what
 * it says: notices are UPDATES, selling is OFFERS, codes are OTP.
 */
class BankSenderTest {
    private val analyzer = MessageAnalyzer()
    private val O = CategoryGroup.OTP
    private val T = CategoryGroup.TRANSACTIONS
    private val U = CategoryGroup.UPDATES
    private val F = CategoryGroup.OFFERS
    private fun c(from: String, text: String, vararg want: CategoryGroup) = Triple(from, text, want.toSet())

    private val cases = listOf(
        // ---- American Express: plain updates ------------------------------------------------------
        c("JM-AMEXIN-S", "Your new American Express Card is on its way and should reach you in 5-7 working days.", U),
        c("JM-AMEXIN-S", "Your American Express Card ending 31005 has been activated. Welcome to American Express!", U),
        c("JM-AMEXIN-S", "Your American Express Card will expire in 03/27. A renewal card will be sent to your registered address.", U),
        c("JM-AMEXIN-S", "Your American Express account statement is now available online. Log in to view.", U),
        c("JM-AMEXIN-S", "We have updated your email address as requested. If you did not request this, please call us.", U),
        c("JM-AMEXIN-S", "Your request for a replacement Card has been received. Reference number 12345678.", U),
        c("JM-AMEXIN-S", "Important update: Changes to the terms and conditions of your American Express Card will be effective from 1 Nov 2026.", U),
        c("JM-AMEXIN-S", "Your Amex app was logged in on a new device on 05 Oct 2026. Not you? Call 1800-419-2122.", U),
        c("JM-AMEXIN-S", "Thank you for contacting American Express. Your service request SR123456 has been resolved.", U),
        c("JM-AMEXIN-S", "Your American Express Card is now enabled for contactless payments.", U),
        c("JM-AMEXIN-S", "Your supplementary Card for Priya Mittal has been approved and will be dispatched shortly.", U),
        c("JM-AMEXIN-S", "Your Membership Rewards points summary for September is available on the Amex app.", U, F),
        c("JM-AMEXIN-S", "Your American Express Card has been temporarily blocked for your security. Please call us to unblock.", U),
        c("JM-AMEXIN-S", "Your Amex Card PIN has been reset successfully.", U),
        c("JM-AMEXIN-S", "Your auto-pay mandate for your American Express Card has been registered successfully.", U, T),
        c("JM-AMEXIN-S", "AMEX never asks for OTP, Card details or sensitive information. Please do not share these with anyone. To read in another language, visit https://go.amex/QwzsKh", U),
        // ---- American Express: money -> Transactions ----------------------------------------------
        c("JM-AMEXIN-S", "Alert: You've spent INR 1,450.00 on your AMEX card ** 31005 at SWIGGY on 5 October 2026 at 01:12 PM IST.", T),
        c("JM-AMEXIN-S", "We have received your payment of INR 23,450.00 towards your American Express Card ending 31005. Thank you.", T),
        c("JM-AMEXIN-S", "Your American Express Card statement: Total due INR 23,450.00, minimum due INR 1,200.00, due date 25 Oct 2026.", T),
        c("JM-AMEXIN-S", "A refund of INR 2,100.00 from MAKEMYTRIP has been credited to your American Express Card ending 31005.", T),
        c("JM-AMEXIN-S", "Your annual fee of INR 4,500 plus GST has been charged to your American Express Card ending 31005.", T),
        c("JM-AMEXIN-S", "Transaction declined: INR 18,000 at CROMA on your Amex Card ending 31005 due to suspected fraud. Call us if this was you.", T),
        // ---- American Express: offers & codes -----------------------------------------------------
        c("JM-AMEXIN-S", "Enjoy 20% off at Taj Hotels with your American Express Card this festive season. T&C apply.", F),
        c("JM-AMEXIN-S", "Refer a friend to American Express and earn 10,000 Membership Rewards points. Refer now.", F),
        c("JM-AMEXIN-S", "Upgrade to the American Express Platinum Card and enjoy unlimited lounge access. Know more.", F),
        c("JM-AMEXIN-T", "Your American Express One Time Password is 913042. It is valid for 10 minutes.", O),

        // ---- Other banks: notices that are not transactions ---------------------------------------
        c("VM-HDFCBK-S", "Your HDFC Bank Credit Card has been dispatched via Blue Dart AWB 81234567890.", U),
        c("AD-ICICIB-S", "Your ICICI Bank account statement for September 2026 has been emailed to you.", U),
        c("JD-AXISBK-S", "Your Axis Bank Debit Card will expire on 10/26. A new card will be sent to your registered address.", U),
        c("VK-KOTAKB-S", "Your service request SR98765 for address change has been completed.", U),
        c("AX-SBIINB-S", "Your SBI branch has been changed to Connaught Place (00691) as requested.", U),
        c("JM-HDFCBK-S", "Revised schedule of charges for savings accounts effective 1 Nov 2026. Details on hdfc.bank.in", U),
        c("VM-ICICIB-S", "Your mobile number has been updated for ICICI Bank account XX1234.", U),
        c("AD-YESBNK-S", "Your YES BANK credit card has been delivered. Generate your PIN on YES Mobile.", U),
        c("JD-IDFCFB-S", "Your feedback matters! Rate your recent branch visit at IDFC FIRST Bank.", U, F),
        c("VK-SBICRD-S", "Your SBI Card is now enabled for international transactions as requested.", U),
        c("AX-HSBCIN-S", "HSBC: Your Internet Banking password was changed on 05 Oct 2026.", U),
        c("JM-SCBANK-S", "Your StanChart account will be upgraded to Priority banking from 1 Nov.", U),
        c("VM-AXISBK-S", "Your Axis Bank NetBanking user ID has been unlocked.", U),
        c("AD-KOTAKB-S", "Your Kotak FD XX4455 has matured and been renewed for 1 year.", U, T),
        c("JD-HDFCBK-S", "Your UPI ID karan@hdfcbank has been created successfully.", U),
        c("VK-ICICIB-S", "Your debit card is temporarily blocked after 3 wrong PIN attempts. It will be unblocked in 24 hours.", U),
        c("AX-PAYTMB-S", "Your Paytm Payments Bank account KYC is due. Complete video KYC in the app.", U),
        c("JM-INDUSB-S", "IndusInd Bank: Your cheque no 000456 has been cleared.", T, U),
        c("VM-CANBNK-S", "Canara Bank will remain closed on 12 Oct on account of Dussehra.", U),
        c("AD-BOBTXN-S", "Your Bank of Baroda locker rent is due. Visit your branch to renew.", T, U),
        // ---- Other banks: money ---------------------------------------------------------------------
        c("VM-HDFCBK-S", "Rs.1,200.00 debited from HDFC Bank A/c XX2626 to VPA blinkit@hdfc on 05-10-26. Ref 627712345690.", T),
        c("AD-ICICIB-S", "INR 3,450.00 spent on ICICI Bank Card XX1001 on 05-Oct-26 at AMAZON. Avl Lmt INR 1,20,000.", T),
        c("JD-AXISBK-S", "Your Axis Bank Credit Card bill of Rs 12,340 is due on 15-Oct-26.", T),
        c("VK-KOTAKB-S", "Rs 25,000 credited to your Kotak a/c XX9876 from ACME CORP salary.", T),
        c("AX-SBICRD-S", "Late payment charges of Rs 500 levied on your SBI Card ending 95.", T),
        // ---- Other banks: offers --------------------------------------------------------------------
        c("VM-HDFCBK-S", "Diwali offer: Get 10% cashback up to Rs 1,500 on electronics with HDFC Bank Credit Cards. T&C", F),
        c("AD-ICICIB-S", "Your Amazon Pay ICICI Credit Card is ready to apply. Earn 5% unlimited cashback. Apply now.", F),
        c("JD-AXISBK-S", "Axis Bank Magnus: Earn 35,000 bonus EDGE points on joining. Apply today.", F),
        c("VK-KOTAKB-S", "Invest in Kotak Mutual Fund NFO from 5-19 Oct. Start a SIP with Rs 500. Invest now.", F),
        c("AX-SBIINB-S", "Earn higher returns with SBI Amrit Kalash FD at 7.6% p.a. Limited period offer.", F),
        c("JM-HDFCBK-S", "Get a gold loan at just 9% p.a. with instant disbursal. Visit your nearest HDFC Bank branch.", F),
        c("VM-ICICIB-S", "Insure your family with ICICI Pru term plan starting Rs 490/month. Get a quote now.", F),
        // ---- Codes from banks ------------------------------------------------------------------------
        c("AD-SBIINB-S", "OTP 773910 for transaction of Rs 5,000 to RAHUL on SBI YONO. Do not share. -SBI", O),
        c("VM-ICICIB-S", "Your one time password for ICICI Bank card XX1001 transaction at FLIPKART is 228341.", O),
    )

    @Test
    fun banksAreFiledByWhatTheySay() {
        val misses = cases.mapNotNull { (from, text, want) ->
            val a = analyzer.analyze(MessageInput(text, SenderInfo(from), 1_790_000_000_000L))
            if (a.category.group in want) null else "$from: got ${a.category} want $want — ${a.classification.reasons} — “${text.take(80)}”"
        }
        assertWithMessage("Misfiled ${misses.size}/${cases.size}:\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
