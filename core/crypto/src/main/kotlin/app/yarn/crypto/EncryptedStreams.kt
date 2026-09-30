package app.yarn.crypto

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BadPassphraseException : IOException("Wrong passphrase or corrupted backup")
class CorruptBackupException(message: String) : IOException(message)

/**
 * Passphrase-encrypted streaming container used for user backups.
 *
 * Layout: header | chunk*
 *  header = MAGIC(8) | version(1) | iterations(4) | salt(16) | noncePrefix(8) | chunkSize(4)
 *  chunk  = length(4) | AES-256-GCM(ciphertext+tag)
 *
 * Each chunk uses nonce = noncePrefix || counter and authenticates header || counter || finalFlag
 * as associated data, so reordering, truncation and header tampering are all detected while
 * memory use stays bounded regardless of backup size.
 */
object EncryptedStreams {
    private val MAGIC = "YARNBK01".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    const val DEFAULT_ITERATIONS = 310_000
    private const val SALT_LEN = 16
    private const val PREFIX_LEN = 8
    private const val TAG_BITS = 128
    const val DEFAULT_CHUNK = 64 * 1024
    private const val HEADER_LEN = 8 + 1 + 4 + SALT_LEN + PREFIX_LEN + 4

    fun encrypt(
        passphrase: CharArray,
        sink: OutputStream,
        iterations: Int = DEFAULT_ITERATIONS,
        chunkSize: Int = DEFAULT_CHUNK,
        random: SecureRandom = SecureRandom(),
    ): OutputStream {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty" }
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val prefix = ByteArray(PREFIX_LEN).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC).put(VERSION.toByte()).putInt(iterations).put(salt).put(prefix).putInt(chunkSize)
            .array()
        sink.write(header)
        val key = deriveKey(passphrase, salt, iterations)
        return ChunkEncryptingStream(sink, key, header, prefix, chunkSize)
    }

    fun decrypt(passphrase: CharArray, source: InputStream): InputStream {
        val din = DataInputStream(source)
        val header = ByteArray(HEADER_LEN)
        try {
            din.readFully(header)
        } catch (e: EOFException) {
            throw CorruptBackupException("Not a Yarn backup (too short)")
        }
        val buf = ByteBuffer.wrap(header)
        val magic = ByteArray(8).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) throw CorruptBackupException("Not a Yarn backup")
        val version = buf.get().toInt()
        if (version != VERSION) throw CorruptBackupException("Unsupported backup version $version")
        val iterations = buf.getInt()
        if (iterations !in 10_000..10_000_000) throw CorruptBackupException("Invalid KDF parameters")
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val prefix = ByteArray(PREFIX_LEN).also { buf.get(it) }
        val chunkSize = buf.getInt()
        if (chunkSize !in 1024..(16 * 1024 * 1024)) throw CorruptBackupException("Invalid chunk size")
        val key = deriveKey(passphrase, salt, iterations)
        return ChunkDecryptingStream(din, key, header, prefix, chunkSize)
    }

    internal fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    internal fun nonce(prefix: ByteArray, counter: Int): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).array()

    internal fun aad(header: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + 5).put(header).putInt(counter).put(if (last) 1 else 0).array()

    private class ChunkEncryptingStream(
        private val sink: OutputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val prefix: ByteArray,
        chunkSize: Int,
    ) : OutputStream() {
        private val buffer = ByteArray(chunkSize)
        private var filled = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) {
            if (filled == buffer.size) flushChunk(last = false)
            buffer[filled++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off
            var remaining = len
            while (remaining > 0) {
                if (filled == buffer.size) flushChunk(last = false)
                val n = minOf(remaining, buffer.size - filled)
                System.arraycopy(b, o, buffer, filled, n)
                filled += n; o += n; remaining -= n
            }
        }

        private fun flushChunk(last: Boolean) {
            if (counter == Int.MAX_VALUE) throw IOException("Backup too large")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter)))
            cipher.updateAAD(aad(header, counter, last))
            val ct = cipher.doFinal(buffer, 0, filled)
            sink.write(ByteBuffer.allocate(4).putInt(ct.size).array())
            sink.write(ct)
            counter++
            filled = 0
        }

        override fun flush() = sink.flush()

        override fun close() {
            if (closed) return
            closed = true
            flushChunk(last = true)
            sink.flush()
            sink.close()
        }
    }

    private class ChunkDecryptingStream(
        private val source: DataInputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val prefix: ByteArray,
        private val chunkSize: Int,
    ) : InputStream() {
        private var current = ByteArray(0)
        private var pos = 0
        private var counter = 0
        private var finished = false
        private var pending: ByteArray? = null

        private fun readRawChunk(): ByteArray? {
            val len = try {
                source.readInt()
            } catch (e: EOFException) {
                return null
            }
            if (len < TAG_BITS / 8 || len > chunkSize + TAG_BITS / 8) throw CorruptBackupException("Invalid chunk length")
            val data = ByteArray(len)
            try {
                source.readFully(data)
            } catch (e: EOFException) {
                throw CorruptBackupException("Backup is truncated")
            }
            return data
        }

        /** Decrypts the next chunk; we look ahead one chunk to know whether this is the final one. */
        private fun nextChunk(): Boolean {
            if (finished) return false
            val raw = pending ?: readRawChunk() ?: throw CorruptBackupException("Backup is truncated")
            pending = readRawChunk()
            val last = pending == null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter)))
            cipher.updateAAD(aad(header, counter, last))
            current = try {
                cipher.doFinal(raw)
            } catch (e: AEADBadTagException) {
                if (counter == 0) throw BadPassphraseException()
                throw CorruptBackupException("Backup failed integrity check at chunk $counter")
            } catch (e: GeneralSecurityException) {
                throw CorruptBackupException("Backup decryption failed: ${e.message}")
            }
            pos = 0
            counter++
            if (last) finished = true
            return true
        }

        override fun read(): Int {
            while (pos >= current.size) if (!nextChunk()) return -1
            return current[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= current.size) if (!nextChunk()) return -1
            val n = minOf(len, current.size - pos)
            System.arraycopy(current, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = source.close()
    }
}
