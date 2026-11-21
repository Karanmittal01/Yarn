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
            "AD-APWELL-S" to "Apollo", "JD-SMYTTN-P" to "Smytten", "VM-MYTNEU-P" to "Tata Neu", "AD-TATNEU-S" to "Tata Neu", "JD-MAXMZE-S" to "Maximize",
            "VM-BIGBKT-S" to "bigbasket", "AD-MYSBIC-S" to "SBI Card", "JD-SHPFAS-S" to "Shipfast", "VM-HOIYTC-S" to "Hoi", "VK-RMATIC-S" to "Routematic", "AD-ADHAAR-G" to "UIDAI (Aadhaar)", "VM-QWKCLR-S" to "Qwikcilver", "JD-PAYRWD-S" to "Pay with Rewards",
        )
        for ((address, name) in expected) assertThat(SenderNames.pretty(address)).isEqualTo(name)
    }

    @Test fun logoKeysComeFromTheBrandName() {
        assertThat(SenderNames.brandKey("VM-HDFCBK-S")).isEqualTo("hdfcbank")
        assertThat(SenderNames.brandKey("JD-SWGTNG-S")).isEqualTo("swiggytoing")
        assertThat(SenderNames.brandKey("VM-ACMEIN-T")).isNull()
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
}
