package app.yarn.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class EncryptedStreamsTest {
    private val pass = "correct horse battery staple".toCharArray()

    private fun encrypt(data: ByteArray, chunk: Int = 1024): ByteArray {
        val out = ByteArrayOutputStream()
        EncryptedStreams.encrypt(pass, out, iterations = 10_000, chunkSize = chunk).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decrypt(bytes: ByteArray, p: CharArray = pass): ByteArray =
        EncryptedStreams.decrypt(p, ByteArrayInputStream(bytes)).use { it.readBytes() }

    @Test
    fun roundTripsAcrossChunkBoundaries() {
        for (size in listOf(0, 1, 1023, 1024, 1025, 10_000)) {
            val data = Random(size).nextBytes(size)
            assertThat(decrypt(encrypt(data))).isEqualTo(data)
        }
    }

    @Test
    fun wrongPassphraseIsReported() {
        val enc = encrypt("secret".toByteArray())
        assertThrows(BadPassphraseException::class.java) { decrypt(enc, "nope".toCharArray()) }
    }

    @Test
    fun truncationIsDetected() {
        val enc = encrypt(Random(1).nextBytes(5000))
        // Drop the final chunk entirely: the previous chunk was sealed as non-final.
        val lastChunkLen = 4 + (5000 % 1024) + 16
        assertThrows(CorruptBackupException::class.java) { decrypt(enc.copyOf(enc.size - lastChunkLen)) }
    }

    @Test
    fun tamperingIsDetected() {
        val enc = encrypt(Random(2).nextBytes(3000))
        enc[enc.size - 20] = (enc[enc.size - 20].toInt() xor 1).toByte()
        assertThrows(CorruptBackupException::class.java) { decrypt(enc) }
    }

    @Test
    fun rejectsForeignFiles() {
        assertThrows(CorruptBackupException::class.java) { decrypt(ByteArray(100)) }
    }
}
