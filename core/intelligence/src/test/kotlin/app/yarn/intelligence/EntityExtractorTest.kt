package app.yarn.intelligence

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class EntityExtractorTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2025, 3, 10, 9, 0).toInstant(zone).toEpochMilli()
    private val ex = EntityExtractor(dayFirst = true, zone = zone)

    private fun of(text: String, type: EntityType) = ex.extract(text, now).filter { it.type == type }

    @Test
    fun otpFormats() {
        assertThat(of("482913 is your OTP for login. Do not share it with anyone.", EntityType.OTP).single().value).isEqualTo("482913")
        assertThat(of("Your verification code is: 7710", EntityType.OTP).single().value).isEqualTo("7710")
        assertThat(of("G-552199 is your Google verification code.", EntityType.OTP).single().value).isEqualTo("552199")
        assertThat(of("Use code 123-456 to sign in", EntityType.OTP).single().value).isEqualTo("123456")
        val withExpiry = of("OTP 90210 for txn of Rs 5,000.00 on card XX1234. Valid for 10 mins.", EntityType.OTP).single()
        assertThat(withExpiry.value).isEqualTo("90210")
        assertThat(withExpiry.attributes["expiresInMinutes"]).isEqualTo("10")
    }

    @Test
    fun noOtpWithoutContext() {
        assertThat(of("Meet me at gate 1234 at 5pm", EntityType.OTP)).isEmpty()
        assertThat(of("Rs 5000 debited from a/c XX1234", EntityType.OTP)).isEmpty()
    }

    @Test
    fun amountsWithDirection() {
        val a = of("Rs.1,25,000.50 debited from A/c XX4321 on 05-Mar-25. Avl Bal INR 12,000", EntityType.AMOUNT)
        assertThat(a.map { it.value }).containsExactly("INR 125000.50", "INR 12000").inOrder()
        assertThat(a.first().attributes["direction"]).isEqualTo("debit")
        assertThat(of("You received $45.99 from Alex", EntityType.AMOUNT).single().attributes["direction"]).isEqualTo("credit")
        assertThat(of("Total 300 EUR due tomorrow", EntityType.AMOUNT).single().value).isEqualTo("EUR 300")
    }

    @Test
    fun linksEmailsAndUpi() {
        val e = ex.extract("Pay via pay.example.com/x?a=1, mail help@example.com or UPI shop@okaxis. See www.test.org.", now)
        assertThat(e.filter { it.type == EntityType.URL }.map { it.value }).containsExactly("https://pay.example.com/x?a=1", "https://www.test.org")
        assertThat(e.filter { it.type == EntityType.EMAIL }.map { it.value }).containsExactly("help@example.com")
        assertThat(e.filter { it.type == EntityType.UPI_ID }.map { it.value }).containsExactly("shop@okaxis")
        assertThat(of("e.g. this is fine. Rs.500 only", EntityType.URL)).isEmpty()
    }

    @Test
    fun trackingAndBookings() {
        val ups = of("Your UPS package 1Z999AA10123456784 is out for delivery", EntityType.TRACKING_NUMBER).single()
        assertThat(ups.attributes["carrier"]).isEqualTo("UPS")
        assertThat(ups.attributes["trackUrl"]).contains("1Z999AA10123456784")
        assertThat(of("Shipped via Delhivery. Tracking ID: 1234567890123", EntityType.TRACKING_NUMBER).single().attributes["carrier"]).isEqualTo("Delhivery")
        assertThat(of("PNR: X7K9QZ for flight 6E 2134 departs 14:35", EntityType.BOOKING_REF).single().value).isEqualTo("X7K9QZ")
        assertThat(of("PNR: X7K9QZ for flight 6E 2134 departs 14:35", EntityType.FLIGHT).single().value).isEqualTo("6E2134")
        assertThat(of("Your order #OD12345678 has shipped", EntityType.ORDER_ID).single().value).isEqualTo("OD12345678")
    }

    @Test
    fun datesAndTimes() {
        val d = of("Dinner on 14 March at 7:30 pm?", EntityType.DATE_TIME).single()
        assertThat(d.value).isEqualTo("2025-03-14T19:30")
        assertThat(of("Due date 05/04/2025", EntityType.DATE_TIME).single().value).isEqualTo("2025-04-05")
        assertThat(EntityExtractor(dayFirst = false, zone = zone).extract("Due 05/04/2025", now).single { it.type == EntityType.DATE_TIME }.value).isEqualTo("2025-05-04")
        assertThat(of("See you tomorrow at 10am", EntityType.DATE_TIME).single().value).isEqualTo("2025-03-11T10:00")
        assertThat(of("Appointment on Jan 5", EntityType.DATE_TIME).single().value).isEqualTo("2026-01-05")
        assertThat(of("We're open 24/7", EntityType.DATE_TIME)).isEmpty()
    }

    @Test
    fun phonesAndAddresses() {
        assertThat(of("Call me on +1 (415) 555-0132 later", EntityType.PHONE).single().value).isEqualTo("+14155550132")
        assertThat(of("Deliver to 1600 Amphitheatre Parkway, Mountain View, CA 94043", EntityType.ADDRESS)).hasSize(1)
    }

    @Test
    fun referenceNumbersAreNotPhoneNumbers() {
        val text = "HSBC: Rs 80.0 spent on your HSBC Credit Card ending 9578 at CRED on 01 Oct 2026 through UPI: 664017641271. Trxn. not done by you? Call 18002673456."
        val phones = EntityExtractor().extract(text, 0L).filter { it.type == EntityType.PHONE }.map { it.value }
        com.google.common.truth.Truth.assertThat(phones).containsExactly("18002673456")
        val ref = EntityExtractor().extract("Paid Rs 500. UPI Ref No 512345678901. Call +914065118002 to report.", 0L)
            .filter { it.type == EntityType.PHONE }.map { it.value }
        com.google.common.truth.Truth.assertThat(ref).containsExactly("+914065118002")
    }
}
