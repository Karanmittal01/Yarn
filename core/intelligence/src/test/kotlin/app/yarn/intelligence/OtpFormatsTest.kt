package app.yarn.intelligence

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * One-time codes come in endless shapes. Every message here carries a code to type in, so it must
 * land in OTP with the right code pulled out, whoever sends it (police, government, banks, shops).
 */
class OtpFormatsTest {
    private val analyzer = MessageAnalyzer()

    private data class Case(val from: String, val text: String, val code: String)

    private val cases = listOf(
        // The user's example: no space after the colon, from a government sender.
        Case("VM-DELTRF-G", "Your OTP for check Notice details is:769966 . Delhi Traffic Police", "769966"),
        Case("AD-DELTRF-S", "Your OTP for check Notice details is:769966 . Delhi Traffic Police", "769966"),
        Case("JD-PARIVH-G", "OTP:482910 for your Vahan application. Do not share it with anyone. -MoRTH", "482910"),
        Case("VM-UIDAI-G", "Your Aadhaar OTP is-551203. Valid for 10 minutes. -UIDAI", "551203"),
        Case("AX-UIDAI-S", "OTP for Aadhaar (XX1234) is 778812 (valid for 10 mins). To update Aadhaar, upload documents on myAadhaar portal. -UIDAI", "778812"),
        Case("VK-EPFOHO-G", "Your OTP for EPFO login is 335921. Do not share. EPFO", "335921"),
        Case("JM-INCTAX-G", "Dear user, 908112 is the OTP to verify your mobile on e-Filing portal. -ITD", "908112"),
        Case("VM-NSDLTN-S", "OTP for PAN application is 6672. Valid for 30 min. NSDL", "6672"),
        Case("BP-DIGILK-G", "Your DigiLocker verification code is 183920.", "183920"),
        Case("AD-IRCTCI-S", "OTP:1234 for IRCTC user ID ravi_k. Valid for 10 mins.", "1234"),
        Case("VM-SBIINB-T", "OTP for online purchase of Rs. 1,250.00 at AMAZON thru State Bank Debit Card ending 1234 is 448812. Do not share this with anyone.", "448812"),
        Case("JD-HDFCBK-T", "6702 is OTP for Txn of INR 500.00 at Swiggy on HDFC Bank card ending 7788. OTPs are SECRET. DO NOT disclose it to anyone.", "6702"),
        Case("AX-ICICIT-T", "Dear Customer, OTP is 338120 for txn of INR 2,999.00 at FLIPKART on ICICI Bank Credit Card XX9001. Valid till 10:20.", "338120"),
        Case("VM-AXISBK-T", "OTP-993011 for Axis Bank txn of Rs 450 at ZOMATO. Valid for 3 mins. Never share OTP.", "993011"),
        Case("JK-AMEXIN-T", "Your Amex SafeKey One-Time Password for INR 1,099.00 at NETFLIX is 317391. Valid for 10 mins.", "317391"),
        Case("VK-KOTAKB-T", "OTP: 662190 for login to Kotak Mobile Banking. Do not share.", "662190"),
        Case("AD-PAYTMB-S", "<#> 482113 is your Paytm verification code. Never share it. hEx7aKq2", "482113"),
        Case("JM-GPAYIN-S", "G-552199 is your Google verification code.", "552199"),
        Case("AX-WHATSP-S", "Your WhatsApp code: 123-456. Don't share this code with others.", "123456"),
        Case("VM-AMAZON-S", "123456 is your Amazon OTP. Do not share it with anyone.", "123456"),
        Case("JD-FLPKRT-S", "Flipkart: Your OTP is 4821. Do not share it with anyone.", "4821"),
        Case("AD-SWIGGY-S", "Your Swiggy login OTP is 5512. Valid for 5 minutes.", "5512"),
        Case("VM-ZOMATO-S", "Use 7712 as your OTP to log in to Zomato.", "7712"),
        Case("BZ-BLUDRT-S", "Your Blue Dart Secure Delivery Code is 692079 and its valid for next 30 minutes.", "692079"),
        Case("VM-DLHVRY-S", "Share OTP 3381 with the Delhivery agent to receive your package AWB 123456789012.", "3381"),
        Case("AD-OLACAB-S", "Hello there! Your order is assigned to the Ola delivery partner. Please share the OTP 4410 at the time of delivery.", "4410"),
        Case("JX-RMATIC-S", "09 Oct 20:00 Logout Check in successful. Please use OTP 9896 to check out from the cab.", "9896"),
        Case("VM-UBERIN-S", "Your Uber code is 8812. Never share this code.", "8812"),
        Case("AD-JIOINF-S", "Your One Time Password (OTP) for MyJio login is 902113. Valid for 10 minutes.", "902113"),
        Case("VM-AIRTEL-S", "OTP for Airtel Thanks app is 7710. Please do not share.", "7710"),
        Case("JM-MYAMEX-S", "Your Amex verification code is 318902. If you didn't request it, call us.", "318902"),
        Case("VK-LICIND-S", "Your OTP for LIC policy login is: 559021. LIC of India", "559021"),
        Case("AD-CDSLIN-S", "CDSL: OTP for eDIS authorisation is 778123. Valid for 15 mins.", "778123"),
        Case("VM-ZERODH-S", "Your Zerodha Kite login OTP is 449120.", "449120"),
        Case("JD-PHONPE-S", "PhonePe: 551098 is your verification code.", "551098"),
        Case("AX-CRED-S", "Your CRED OTP is 0912. Do not share it.", "0912"),
        Case("AD-NPCI-S", "UPI PIN set OTP: 772910. Don't share with anyone. -NPCI", "772910"),
        Case("VM-DIGIYA-S", "OTP:551209 Digi Yatra registration. Valid 10 min.", "551209"),
        Case("JD-PASPRT-G", "Passport Seva OTP is 881204. Do not share this with anyone.", "881204"),
        Case("VM-HDFCBK-S", "आपका ओटीपी 551903 है। इसे किसी के साथ साझा न करें। -HDFC Bank", "551903"),
        Case("AD-SBIINB-S", "ओटीपी: 330912 SBI YONO लॉगिन के लिए। किसी को न बताएं।", "330912"),
        Case("VM-DELJAL-G", "Delhi Jal Board: OTP for bill payment is:44812", "44812"),
        Case("AD-BSESRJ-S", "Your OTP is-7781 for BSES bill payment.", "7781"),
        Case("JD-MCDDEL-G", "Dear Citizen, your OTP for property tax login is 993120. MCD", "993120"),
    )

    @Test
    fun everyCodeLandsInOtp() {
        val misses = cases.mapNotNull { c ->
            val a = analyzer.analyze(MessageInput(c.text, SenderInfo(c.from), 1_790_000_000_000L))
            val code = a.entities.firstOrNull { it.type == EntityType.OTP }?.value
            if (a.category.group == CategoryGroup.OTP && code == c.code) null
            else "${c.from}: got ${a.category} code=$code want OTP code=${c.code} — ${a.classification.reasons} — “${c.text.take(70)}”"
        }
        assertWithMessage("Missed ${misses.size}/${cases.size}:\n" + misses.joinToString("\n")).that(misses).isEmpty()
    }
}
