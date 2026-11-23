package app.yarn.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.yarn.intelligence.SenderNames

/**
 * Logos of well-known Indian businesses, shipped inside the app (assets/logos) so nothing is looked up
 * online and no one learns who messages you. A sender gets a logo only when its registered SMS sender ID
 * matches a known brand, so a look-alike number never borrows a bank's logo.
 */
object BrandLogos {
    /** Marks a "photo" that is a bundled logo rather than a contact picture. */
    const val SCHEME = "logo:"
    private const val DIR = "logos"

    @Volatile private var available: Set<String>? = null
    private val cache = LruCache<String, Bitmap>(48)

    private fun names(context: Context): Set<String> = available ?: synchronized(this) {
        available ?: runCatching { context.assets.list(DIR)?.map { it.substringBeforeLast('.') }?.toSet() }
            .getOrNull().orEmpty().also { available = it }
    }

    /** Logos are part of Yarn Pro; without it, businesses show their initial like anyone else. */
    @Volatile var enabled: Boolean = true

    /** "logo:hdfcbank" for a business sender with a bundled logo, else null. */
    fun uri(context: Context, address: String?): String? = if (enabled) find(context, address) else null

    /** The bundled logo for [address], whether or not logos are shown. */
    fun find(context: Context, address: String?): String? {
        if (address.isNullOrBlank()) return null
        val key = SenderNames.brandKey(address) ?: return null
        return if (key in names(context)) SCHEME + key else null
    }

    /**
     * A business sender ID (letters, like "CP-APLPHR-S") with no logo in Yarn yet: one its user can
     * ask for. Number-only senders are skipped, since many brands share them.
     */
    fun isMissing(context: Context, address: String?): Boolean =
        !address.isNullOrBlank() && '@' !in address && address.any { it.isLetter() } && address.none { it == ' ' } &&
            SenderNames.isBusiness(address) && find(context, address) == null

    fun isLogo(uri: String?): Boolean = uri?.startsWith(SCHEME) == true

    /** The logo as a square bitmap (decoded once, then cached), or null if [uri] isn't a logo. */
    fun bitmap(context: Context, uri: String): Bitmap? {
        if (!isLogo(uri)) return null
        val key = uri.removePrefix(SCHEME)
        cache.get(key)?.let { return it }
        val bmp = runCatching { context.assets.open("$DIR/$key.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        cache.put(key, bmp)
        return bmp
    }
}
