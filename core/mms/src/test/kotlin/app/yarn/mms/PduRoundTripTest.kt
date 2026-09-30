package app.yarn.mms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PduRoundTripTest {

    @Test
    fun uintvarEncodingMatchesSpecExamples() {
        assertThat(PduWriter.encodeUintvar(0).toList()).containsExactly(0x00.toByte())
        assertThat(PduWriter.encodeUintvar(0x7F).toList()).containsExactly(0x7F.toByte())
        assertThat(PduWriter.encodeUintvar(0x80).toList()).containsExactly(0x81.toByte(), 0x00.toByte()).inOrder()
        assertThat(PduWriter.encodeUintvar(0x3FFF).toList()).containsExactly(0xFF.toByte(), 0x7F.toByte()).inOrder()
        val r = PduReader(PduWriter.encodeUintvar(123456789))
        assertThat(r.readUintvar()).isEqualTo(123456789)
    }

    @Test
    fun sendReqRoundTripsThroughParser() {
        val image = ByteArray(5000) { (it % 251).toByte() }
        val req = SendRequest(
            recipients = listOf("+1 (555) 010-2000", "friend@example.com"),
            parts = listOf(
                OutgoingPart("image/jpeg", image, "photo.jpg"),
                OutgoingPart.text("Hello 👋 world — ünïcödé"),
            ),
            transactionId = "T12345",
            subject = "Trip photos ✈",
            dateEpochSeconds = 1_700_000_000,
            deliveryReport = true,
        )
        val bytes = PduComposer.sendReq(req)
        assertThat(bytes[0].toInt() and 0xFF).isEqualTo(0x8C)
        assertThat(bytes[1].toInt() and 0xFF).isEqualTo(MessageType.SEND_REQ)

        // Re-label as retrieve-conf, which shares the header/body layout, and parse it back.
        bytes[1] = MessageType.RETRIEVE_CONF.toByte()
        val pdu = PduParser.parse(bytes) as Pdu.RetrieveConf
        assertThat(pdu.transactionId).isEqualTo("T12345")
        assertThat(pdu.to).containsExactly("+15550102000", "friend@example.com").inOrder()
        assertThat(pdu.subject).isEqualTo("Trip photos ✈")
        assertThat(pdu.dateEpochSeconds).isEqualTo(1_700_000_000)
        assertThat(pdu.deliveryReportRequested).isTrue()
        assertThat(pdu.contentType.mimeType).isEqualTo(ContentTypes.MULTIPART_RELATED)
        assertThat(pdu.contentType.start).isEqualTo("<smil>")
        assertThat(pdu.contentType.type).isEqualTo(ContentTypes.APP_SMIL)
        assertThat(pdu.parts).hasSize(3)
        assertThat(pdu.parts[0].isSmil).isTrue()
        assertThat(pdu.parts[0].text()).contains("cid:photo.jpg")
        assertThat(pdu.parts[1].contentType).isEqualTo("image/jpeg")
        assertThat(pdu.parts[1].data).isEqualTo(image)
        assertThat(pdu.parts[1].contentId).isEqualTo("photo.jpg")
        assertThat(pdu.parts[1].name).isEqualTo("photo.jpg")
        assertThat(pdu.parts[2].isText).isTrue()
        assertThat(pdu.parts[2].text()).isEqualTo("Hello 👋 world — ünïcödé")
    }

    @Test
    fun parsesNotificationInd() {
        val w = PduWriter()
            .byte(0x8C).byte(MessageType.NOTIFICATION_IND)
            .byte(0x98).text("tx-1")
            .byte(0x8D).shortInteger(0x12)
            .byte(0x89)
        val from = PduWriter().byte(0x80).encodedString("+15550001111/TYPE=PLMN").toByteArray()
        w.valueLength(from.size).bytes(from)
        w.byte(0x8A).byte(0x80)
        w.byte(0x8E).longInteger(48_000)
        val expiry = PduWriter().byte(0x81).longInteger(604800).toByteArray()
        w.byte(0x88).valueLength(expiry.size).bytes(expiry)
        w.byte(0x83).text("http://mmsc.example.com/abc?id=1")

        val pdu = PduParser.parse(w.toByteArray()) as Pdu.NotificationInd
        assertThat(pdu.transactionId).isEqualTo("tx-1")
        assertThat(pdu.from).isEqualTo("+15550001111")
        assertThat(pdu.messageSize).isEqualTo(48_000)
        assertThat(pdu.messageClass).isEqualTo("personal")
        assertThat(pdu.expiry).isEqualTo(MmsTime(604800, relative = true))
        assertThat(pdu.contentLocation).isEqualTo("http://mmsc.example.com/abc?id=1")
    }

    @Test
    fun unknownHeadersAreSkipped() {
        val w = PduWriter()
            .byte(0x8C).byte(MessageType.SEND_CONF)
            .byte(0x98).text("tx-2")
            .byte(0x8D).shortInteger(0x12)
            .byte(0xB7).text("vendor-app-id") // Applic-ID, not modelled
            .byte(0xC5).valueLength(3).byte(1).byte(2).byte(3) // unassigned header with length value
            .byte(0x92).byte(0x80)
            .byte(0x8B).text("msg-99")
        val pdu = PduParser.parse(w.toByteArray()) as Pdu.SendConf
        assertThat(pdu.isSuccess).isTrue()
        assertThat(pdu.messageId).isEqualTo("msg-99")
    }

    @Test
    fun parsesSinglePartRetrieveConf() {
        val w = PduWriter()
            .byte(0x8C).byte(MessageType.RETRIEVE_CONF)
            .byte(0x8D).shortInteger(0x12)
            .byte(0x85).longInteger(1_600_000_000)
        val from = PduWriter().byte(0x80).encodedString("+447700900123/TYPE=PLMN").toByteArray()
        w.byte(0x89).valueLength(from.size).bytes(from)
        w.byte(0x84).shortInteger(0x03) // text/plain
        w.bytes("plain body".toByteArray())
        val pdu = PduParser.parse(w.toByteArray()) as Pdu.RetrieveConf
        assertThat(pdu.from).isEqualTo("+447700900123")
        assertThat(pdu.parts.single().text()).isEqualTo("plain body")
    }

    @Test
    fun ackAndNotifyRespAreWellFormed() {
        val ack = PduComposer.acknowledgeInd("tx-9")
        assertThat(PduParser.parse(ack)).isEqualTo(Pdu.Other(MessageType.ACKNOWLEDGE_IND, "tx-9"))
        val nr = PduComposer.notifyRespInd("tx-10", MmsValues.STATUS_DEFERRED)
        assertThat(PduParser.parse(nr)).isEqualTo(Pdu.Other(MessageType.NOTIFYRESP_IND, "tx-10"))
    }

    @Test(expected = PduParseException::class)
    fun truncatedPduFailsCleanly() {
        val bytes = PduComposer.sendReq(
            SendRequest(listOf("123"), listOf(OutgoingPart.text("hi")), "t"),
        )
        bytes[1] = MessageType.RETRIEVE_CONF.toByte()
        PduParser.parse(bytes.copyOf(bytes.size - 3))
    }
}
