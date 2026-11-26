package app.yarn.intelligence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SenderNamesTest {
    @Test fun namesSendersFromARealInbox() {
        val expected = mapOf(
            "VM-TRAIND-G" to "TRAI", "JD-SWGTNG-S" to "Swiggy Toing", "AX-XPBEES-S" to "XpressBees", "VM-SLCBNK-S" to "slice",
            "JD-PHEASY-S" to "PharmEasy", "AD-MGCPNP-S" to "magicpin", "VM-FRCHBT-S" to "Freecharge", "JM-MYCLIQ-S" to "Tata CLiQ",
            "AD-TALUXE-P" to "Tata CLiQ Luxury", "VK-QKWLLT-S" to "QuikWallet", "JD-COUPLX-S" to "Coupl", "AX-GYFTRR-S" to "GyFTR",
            "VM-WOOHOO-S" to "Woohoo", "AD-HSQUAR-S" to "Hudle", "JD-PLAYO-S" to "Playo", "VM-LPHILP-P" to "Louis Philippe",
            "AD-APWELL-S" to "Apollo", "JD-SMYTTN-P" to "Smytten", "VM-MYTNEU-P" to "Tata Neu", "AD-TATNEU-S" to "Tata Neu", "JD-MAXMZE-S" to "Maximize", "JD-HMFINE-S" to "MFine", "CP-APLPHR-S" to "Apollo Pharmacy",
            "VM-BIGBKT-S" to "bigbasket", "AD-MYSBIC-S" to "SBI Card", "JD-SHPFAS-S" to "Shipfast", "VM-HOIYTC-S" to "Hoi", "VK-RMATIC-S" to "Routematic", "AD-ADHAAR-G" to "UIDAI (Aadhaar)", "VM-QWKCLR-S" to "Qwikcilver", "JD-PAYRWD-S" to "Pay with Rewards",
            // Logo requests from testers, exactly as they arrived.
            "AX-CKYCR-S" to "CKYC (CERSAI)", "AD-VAAHAN-S" to "Parivahan", "VD-NESLIU-S" to "NeSL", "TX-EAWIGN-S" to "Awign", "TX-MAWIGN-P" to "Awign",
        )
        for ((address, name) in expected) assertThat(SenderNames.pretty(address)).isEqualTo(name)
    }

    @Test fun logoKeysComeFromTheBrandName() {
        assertThat(SenderNames.brandKey("VM-HDFCBK-S")).isEqualTo("hdfcbank")
        assertThat(SenderNames.brandKey("JD-SWGTNG-S")).isEqualTo("swiggytoing")
        assertThat(SenderNames.brandKey("VM-ACMEIN-T")).isNull()
        assertThat(SenderNames.brandKey("AX-CKYCR-S")).isEqualTo("ckyccersai")
        assertThat(SenderNames.brandKey("VD-NESLIU-S")).isEqualTo("nesl")
        assertThat(SenderNames.brandKey("TX-EAWIGN-S")).isEqualTo("awign")
        assertThat(SenderNames.brandKey("AD-VAAHAN-S")).isEqualTo("parivahan")
    }

    @Test fun jiosCodeNumberIsJio() {
        assertThat(SenderNames.pretty("620021")).isEqualTo("Jio")
        assertThat(SenderNames.brandKey("620021")).isEqualTo("jio")
        // As it reaches the phone: with the carrier's prefix and suffix.
        assertThat(SenderNames.pretty("JK-620021-P")).isEqualTo("Jio") // exactly as on the user's phone
        assertThat(SenderNames.brandKey("JK-620021-P")).isEqualTo("jio")
        assertThat(SenderNames.pretty("JM-620021-S")).isEqualTo("Jio")
        assertThat(SenderNames.brandKey("VK-620021")).isEqualTo("jio")
        assertThat(SenderNames.pretty("JM-620022-S")).isEqualTo("620022")
        assertThat(SenderNames.brandKey("+91620021")).isNull()
    }

    @Test fun applesCodeNumberIsApple() {
        assertThat(SenderNames.pretty("57575711")).isEqualTo("Apple")
        assertThat(SenderNames.brandKey("57575711")).isEqualTo("apple")
    }

    @Test fun ordinaryNumbersNeverBorrowABrand() {
        assertThat(SenderNames.pretty("+919812345678")).isNull()
        assertThat(SenderNames.brandKey("+9157575711")).isNull()
        assertThat(SenderNames.brandKey("9812345678")).isNull()
        assertThat(SenderNames.pretty("hello@example.com")).isNull()
    }

    @Test fun unknownSendersKeepTheirCode() {
        assertThat(SenderNames.pretty("VM-RPCARD-S")).isEqualTo("RPCARD")
        assertThat(SenderNames.pretty("VM-SHPWSE-S")).isEqualTo("Shopwise")
    }

    /** How a business chat's agent ID reaches the message store: Base32, lower case, no padding. */
    private fun encode(id: String): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in id.toByteArray()) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) { bits -= 5; out.append(alphabet[(buffer shr bits) and 31]) }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    @Test fun businessChatsAreNamedAfterTheirBusiness() {
        // The tester's inbox showed "m5xw6z3mmvpxaylzl5yg..." and "njuw643qnbsxezk7ob4wu...".
        assertThat(encode("google_pay_x")).startsWith("m5xw6z3mmvpxaylzl5")
        assertThat(encode("jiosphere_x")).startsWith("njuw643qnbsxezk7")
        // What the tester's inbox showed, cut short by the screen, still reads as the business.
        assertThat(SenderNames.pretty("m5xw6z3mmvpxaylzl5yg")).isEqualTo("Google Pay")
        assertThat(SenderNames.pretty("njuw643qnbsxezk7ob4wu")).startsWith("JioSphere")
        val gpay = encode("google_pay_9f3a1c_agent")
        assertThat(SenderNames.rcsAgent(gpay)).isEqualTo("google_pay_9f3a1c_agent")
        assertThat(SenderNames.pretty(gpay)).isEqualTo("Google Pay")
        assertThat(SenderNames.brandKey(gpay)).isEqualTo("googlepay")
        assertThat(SenderNames.isBusiness(gpay)).isTrue()
        assertThat(SenderNames.pretty(encode("jiosphere_rbm_agent"))).isEqualTo("JioSphere")
        assertThat(SenderNames.pretty(encode("woxen_university_7k2p") + "@rbm.goog")).isEqualTo("Woxen University")
    }

    @Test fun ordinarySendersAreNotMistakenForBusinessChats() {
        for (a in listOf("VM-HDFCBK-S", "+919812345678", "59039149", "notificationsalerts@example.com", "abcdefghijklmnopqrstu")) {
            assertThat(SenderNames.rcsAgent(a)).isNull()
        }
    }
}
