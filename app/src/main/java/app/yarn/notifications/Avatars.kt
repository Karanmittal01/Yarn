package app.yarn.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** Deterministic letter avatars, shared by notifications and shortcuts. */
object Avatars {
    val PALETTE = intArrayOf(
        0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(), 0xFFAB47BC.toInt(),
        0xFF42A5F5.toInt(), 0xFF8D6E63.toInt(), 0xFFEC407A.toInt(), 0xFF66BB6A.toInt(),
    )

    fun colorFor(key: String): Int = PALETTE[(key.hashCode() and 0x7fffffff) % PALETTE.size]

    fun initial(name: String): String =
        name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isDigit()) "#" else it.toString() } ?: "#"

    fun letterBitmap(name: String, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFor(name) }
        c.drawCircle(size / 2f, size / 2f, size / 2f, bg)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.45f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = size / 2f - (tp.descent() + tp.ascent()) / 2
        c.drawText(initial(name), size / 2f, y, tp)
        return bmp
    }
}
