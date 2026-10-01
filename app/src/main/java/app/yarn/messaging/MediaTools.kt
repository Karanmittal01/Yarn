package app.yarn.messaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class AttachmentDraft(
    /** file:// URI inside app storage (copied immediately so picker grants can expire). */
    val uri: Uri,
    val mimeType: String,
    val fileName: String,
    val size: Long,
)

object MediaTools {
    /** Directory for outgoing attachments; excluded from Auto Backup. */
    fun outboxDir(context: Context) = File(context.noBackupFilesDir, "outbox").apply { mkdirs() }

    fun mmsDir(context: Context) = File(context.cacheDir, "mms").apply { mkdirs() }

    /** Copies a picked content URI into private storage and returns a draft attachment. */
    fun importAttachment(context: Context, source: Uri): AttachmentDraft? {
        val resolver = context.contentResolver
        val mime = resolver.getType(source) ?: guessMime(source.lastPathSegment.orEmpty())
        var name = "attachment"
        resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        if (!name.contains('.') && ext != null) name = "$name.$ext"
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val target = File(outboxDir(context), "${UUID.randomUUID()}_$safe")
        return runCatching {
            resolver.openInputStream(source)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
            AttachmentDraft(Uri.fromFile(target), mime, safe, target.length())
        }.getOrNull()
    }

    fun guessMime(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    /**
     * Downscales and re-encodes an image until it fits [maxBytes] and the carrier's dimension
     * limits. Returns JPEG bytes (PNG/GIF are converted; animated GIFs lose animation only if
     * they would not fit otherwise).
     */
    fun compressImage(context: Context, uri: Uri, mime: String, maxBytes: Int, maxWidth: Int, maxHeight: Int): Pair<ByteArray, String>? {
        val original = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
        if (original.size <= maxBytes && mime != "image/heic" && mime != "image/heif") return original to mime
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(original.let { java.nio.ByteBuffer.wrap(it) })) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1f, maxWidth.toFloat() / info.size.width, maxHeight.toFloat() / info.size.height)
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }.getOrNull() ?: return null
        var current = bitmap
        var quality = 85
        repeat(12) {
            val out = ByteArrayOutputStream()
            current.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= maxBytes) return out.toByteArray() to "image/jpeg"
            if (quality > 45) {
                quality -= 15
            } else {
                current = Bitmap.createScaledBitmap(current, (current.width * 0.75f).toInt().coerceAtLeast(1), (current.height * 0.75f).toInt().coerceAtLeast(1), true)
                quality = 75
            }
        }
        return null
    }
}
