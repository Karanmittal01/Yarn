package app.yarn.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryTest {
    @Test fun oneRecipientIsDeliveredOnItsReport() {
        val n = Delivery.recipients("+919812345678")
        assertEquals(1, n)
        assertFalse(Delivery.complete(0, n))
        assertTrue(Delivery.complete(Delivery.record(0, 0, n), n))
    }

    @Test fun aGroupNeedsEveryRecipient() {
        val n = Delivery.recipients("+91981,+91982,+91983")
        var d = Delivery.record(0, 2, n)
        d = Delivery.record(d, 0, n)
        assertFalse(Delivery.complete(d, n))
        d = Delivery.record(d, 1, n)
        assertTrue(Delivery.complete(d, n))
    }

    @Test fun aRepeatedReportCountsOnce() {
        // Some carriers send the same report twice; it mustn't stand in for another recipient's.
        val n = 2
        val d = Delivery.record(Delivery.record(0, 0, n), 0, n)
        assertFalse(Delivery.complete(d, n))
    }

    @Test fun reportsFromBeforeThisUpdateStillCount() {
        // Older in-flight messages carry no recipient in their report.
        assertTrue(Delivery.complete(Delivery.record(0, -1, 1), 1))
    }

    @Test fun bigGroupsFallBackToCounting() {
        val n = 40
        var d = 0
        repeat(39) { d = Delivery.record(d, it, n) }
        assertFalse(Delivery.complete(d, n))
        assertTrue(Delivery.complete(Delivery.record(d, 39, n), n))
    }

    @Test fun statusReportCodes() {
        assertEquals(Delivery.Report.DELIVERED, Delivery.report(0x00))
        assertEquals(Delivery.Report.DELIVERED, Delivery.report(0x01)) // forwarded, couldn't confirm
        assertEquals(Delivery.Report.PENDING, Delivery.report(0x20)) // network still trying (phone off)
        assertEquals(Delivery.Report.PENDING, Delivery.report(0x3F))
        assertEquals(Delivery.Report.FAILED, Delivery.report(0x40))
        assertEquals(Delivery.Report.FAILED, Delivery.report(0x65))
    }
}
