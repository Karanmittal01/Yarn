package app.yarn.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.yarn.R

/**
 * Yarn Pro's accent colours. Each sets the buttons, switches and tabs, and the gradient of your
 * own message bubbles, tuned separately for light and dark so text on them stays easy to read.
 */
enum class Accent(
    @StringRes val label: Int,
    val bubble: Pair<Color, Color>,
    private val light: Tones,
    private val dark: Tones,
) {
    YARN(R.string.accent_yarn, Color(0xFF2B86FF) to Color(0xFF1252E0),
        Tones(0xFF1466F0, 0xFFFFFFFF, 0xFFDCE6FF, 0xFF00174D), Tones(0xFF8FB2FF, 0xFF00296B, 0xFF1A4FC4, 0xFFDCE6FF)),
    TEAL(R.string.accent_teal, Color(0xFF0E9C8D) to Color(0xFF00695F),
        Tones(0xFF00796B, 0xFFFFFFFF, 0xFFC8F1EB, 0xFF00201C), Tones(0xFF6FD8C9, 0xFF003731, 0xFF005C53, 0xFFC8F1EB)),
    GREEN(R.string.accent_green, Color(0xFF3E9A42) to Color(0xFF256B29),
        Tones(0xFF2E7D32, 0xFFFFFFFF, 0xFFD3F2D4, 0xFF002106), Tones(0xFF8EDB91, 0xFF00390A, 0xFF1F6A25, 0xFFD3F2D4)),
    VIOLET(R.string.accent_violet, Color(0xFF8063EC) to Color(0xFF5634B8),
        Tones(0xFF6745D2, 0xFFFFFFFF, 0xFFE7DEFF, 0xFF22005D), Tones(0xFFC5B3FF, 0xFF3A1A8A, 0xFF5634B8, 0xFFE7DEFF)),
    PINK(R.string.accent_pink, Color(0xFFD63A73) to Color(0xFFA0104A),
        Tones(0xFFB8185A, 0xFFFFFFFF, 0xFFFFD9E3, 0xFF3E001A), Tones(0xFFFFB0C8, 0xFF5E0B2E, 0xFF8E0F43, 0xFFFFD9E3)),
    ORANGE(R.string.accent_orange, Color(0xFFD9620B) to Color(0xFFAE4400),
        Tones(0xFFB84D00, 0xFFFFFFFF, 0xFFFFDBC9, 0xFF351000), Tones(0xFFFFB68C, 0xFF552000, 0xFF8A3A00, 0xFFFFDBC9)),
    RED(R.string.accent_red, Color(0xFFD63A35) to Color(0xFFA51C1C),
        Tones(0xFFC0282A, 0xFFFFFFFF, 0xFFFFDAD5, 0xFF410001), Tones(0xFFFFB4A9, 0xFF690003, 0xFF931B1B, 0xFFFFDAD5)),
    GRAPHITE(R.string.accent_graphite, Color(0xFF5B7380) to Color(0xFF37474F),
        Tones(0xFF455A64, 0xFFFFFFFF, 0xFFDCE3E9, 0xFF0F1D24), Tones(0xFFB4C4CE, 0xFF1F333C, 0xFF3A4C55, 0xFFDCE3E9));

    /** The colour shown in the picker. */
    val swatch: Color get() = light.primary

    fun applyTo(scheme: ColorScheme, dark: Boolean): ColorScheme {
        val t = if (dark) this.dark else light
        return scheme.copy(primary = t.primary, onPrimary = t.onPrimary, primaryContainer = t.container, onPrimaryContainer = t.onContainer)
    }

    class Tones(primary: Long, onPrimary: Long, container: Long, onContainer: Long) {
        val primary = Color(primary)
        val onPrimary = Color(onPrimary)
        val container = Color(container)
        val onContainer = Color(onContainer)
    }

    companion object {
        fun of(name: String?): Accent = entries.firstOrNull { it.name == name } ?: YARN
    }
}

/** Yarn Pro's chat wallpapers: soft washes, and one with a faint pattern of dots. */
enum class Wallpaper(@StringRes val label: Int, private val light: List<Long>, private val dark: List<Long>, val dots: Boolean = false) {
    DEFAULT(R.string.wallpaper_default, listOf(0xFFEFF4FE, 0xFFF7F8FD, 0xFFFBF8FE), listOf(0xFF161A23, 0xFF101217, 0xFF0D0C12)),
    PLAIN(R.string.wallpaper_plain, listOf(0xFFFFFFFF, 0xFFFFFFFF), listOf(0xFF000000, 0xFF000000)),
    SKY(R.string.wallpaper_sky, listOf(0xFFD8ECFF, 0xFFEAF5FF, 0xFFF4FAFF), listOf(0xFF0E2233, 0xFF0B1823, 0xFF091119)),
    MINT(R.string.wallpaper_mint, listOf(0xFFD9F4E8, 0xFFEAF8F1, 0xFFF3FBF8), listOf(0xFF0F241D, 0xFF0C1915, 0xFF0A1412)),
    SUNSET(R.string.wallpaper_sunset, listOf(0xFFFFE3D3, 0xFFFFEEE6, 0xFFFCE6EE), listOf(0xFF2A1A16, 0xFF1D1214, 0xFF1E1020)),
    LAVENDER(R.string.wallpaper_lavender, listOf(0xFFEBE3FF, 0xFFF4F0FF, 0xFFFBEFFA), listOf(0xFF1D1830, 0xFF141221, 0xFF180F1E)),
    DOTS(R.string.wallpaper_dots, listOf(0xFFEFF4FE, 0xFFF7F8FD, 0xFFFBF8FE), listOf(0xFF161A23, 0xFF101217, 0xFF0D0C12), dots = true);

    fun brush(dark: Boolean): Brush = Brush.verticalGradient((if (dark) this.dark else light).map { Color(it) })

    companion object {
        fun of(name: String?): Wallpaper = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

val LocalAccent = staticCompositionLocalOf { Accent.YARN }
val LocalWallpaper = staticCompositionLocalOf { Wallpaper.DEFAULT }

/** The gradient of your own message bubbles, in the chosen accent. */
@Composable
fun bubbleBrush(): Brush = LocalAccent.current.bubble.let { (top, bottom) -> Brush.linearGradient(listOf(top, bottom)) }

/** The chosen wallpaper behind a conversation. */
@Composable
fun Modifier.chatWallpaper(wallpaper: Wallpaper = LocalWallpaper.current): Modifier {
    val dark = LocalDarkTheme.current
    val base = background(wallpaper.brush(dark))
    if (!wallpaper.dots) return base
    val dot = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.10f else 0.08f)
    return base.drawBehind {
        val step = 22.dp.toPx()
        val r = 1.6.dp.toPx()
        var y = step / 2
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) step / 2 else step
            while (x < size.width) {
                drawCircle(dot, r, Offset(x, y))
                x += step
            }
            y += step * 0.87f
            row++
        }
    }
}
