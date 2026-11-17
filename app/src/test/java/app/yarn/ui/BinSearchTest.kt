package app.yarn.ui

import app.yarn.data.db.BinAttachment
import app.yarn.data.db.BinEntity
import app.yarn.ui.bin.BinSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinSearchTest {
    private fun bin(id: Long, title: String, address: String, body: String, file: String? = null) = BinEntity(
        id = id, conversationId = id, addresses = listOf(address), title = title, kind = 0, address = address, body = body,
        date = 0, outgoing = false, status = 0, category = "PERSONAL",
        attachments = file?.let { listOf(BinAttachment("application/pdf", "file:///x", it)) }.orEmpty(), deletedAt = 0,
    )

    private val items = listOf(
        bin(1, "Karan Mittal", "+919812345678", "Reached home, call you in 10"),
        bin(2, "HDFC Bank", "VM-HDFCBK-S", "Rs.2,500.00 debited from A/c XX1234 to VPA swiggy@icici"),
        bin(3, "Mom", "+919800000002", "दवाई लेना मत भूलना", file = "prescription.pdf"),
    )

    @Test fun blankQueryKeepsEverything() {
        assertEquals(items, BinSearch.filter(items, "   "))
    }

    @Test fun findsByNameMessageAndSenderIdIgnoringCase() {
        assertEquals(listOf(2L), BinSearch.filter(items, "hdfc").map { it.id })
        assertEquals(listOf(2L), BinSearch.filter(items, "SWIGGY").map { it.id })
        assertEquals(listOf(2L), BinSearch.filter(items, "hdfcbk").map { it.id })
        assertEquals(listOf(1L), BinSearch.filter(items, "karan").map { it.id })
    }

    @Test fun everyWordMustMatch() {
        assertEquals(listOf(1L), BinSearch.filter(items, "home call").map { it.id })
        assertTrue(BinSearch.filter(items, "home swiggy").isEmpty())
    }

    @Test fun numbersMatchHoweverTheyWereWritten() {
        assertEquals(listOf(1L), BinSearch.filter(items, "98123 45678").map { it.id })
        assertEquals(listOf(1L), BinSearch.filter(items, "9812345678").map { it.id })
        assertEquals(listOf(1L), BinSearch.filter(items, "+91-98123").map { it.id })
    }

    @Test fun findsHindiAndAttachmentNames() {
        assertEquals(listOf(3L), BinSearch.filter(items, "दवाई").map { it.id })
        assertEquals(listOf(3L), BinSearch.filter(items, "prescription").map { it.id })
    }

    @Test fun snippetStartsNearALateMatch() {
        val body = "Hello from Amazon! We have processed your request and the amount has been refunded to your original payment method."
        val s = BinSearch.snippet(body, "refunded")
        assertTrue(s.startsWith("…"))
        assertTrue(s.contains("refunded"))
        assertEquals("Short message", BinSearch.snippet("Short message", "message"))
        assertFalse(BinSearch.snippet(body, "hello").startsWith("…"))
    }
}
