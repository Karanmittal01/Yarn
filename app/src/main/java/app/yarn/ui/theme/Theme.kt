package app.yarn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yarn.data.prefs.ThemeMode

/** Brand colours taken from the Yarn logo. */
object Brand {
    val Sky = Color(0xFF2FB3FC)
    val Blue = Color(0xFF1466F0)
    val Deep = Color(0xFF0A2DA8)
    val Cyan = Color(0xFF7EEBFB)

    /** Gradient used for the user's own message bubbles and accents. */
    val bubble = Brush.linearGradient(listOf(Color(0xFF2B86FF), Color(0xFF1252E0)))
    val logo = Brush.linearGradient(listOf(Sky, Blue, Deep))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1466F0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF00174D),
    secondary = Color(0xFF4F5D79),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E9F6),
    onSecondaryContainer = Color(0xFF0B1A33),
    tertiary = Color(0xFF0B8FCB),
    tertiaryContainer = Color(0xFFD3F0FF),
    onTertiaryContainer = Color(0xFF00283A),
    error = Color(0xFFD0342C),
    errorContainer = Color(0xFFFFE2DE),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF151A24),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF151A24),
    onSurfaceVariant = Color(0xFF5B6476),
    surfaceVariant = Color(0xFFE2E6EF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F9),
    surfaceContainer = Color(0xFFECEFF6),
    surfaceContainerHigh = Color(0xFFE6E9F2),
    surfaceContainerHighest = Color(0xFFDFE3EE),
    outline = Color(0xFF8A93A6),
    outlineVariant = Color(0xFFD7DCE7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB2FF),
    onPrimary = Color(0xFF00296B),
    primaryContainer = Color(0xFF1A4FC4),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFB9C4DD),
    onSecondary = Color(0xFF223047),
    secondaryContainer = Color(0xFF2A3346),
    onSecondaryContainer = Color(0xFFDDE4F5),
    tertiary = Color(0xFF7DD3FC),
    tertiaryContainer = Color(0xFF0D4A66),
    onTertiaryContainer = Color(0xFFCDEFFF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1714),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1017),
    onBackground = Color(0xFFE4E7EF),
    surface = Color(0xFF0D1017),
    onSurface = Color(0xFFE4E7EF),
    onSurfaceVariant = Color(0xFF9AA3B5),
    surfaceVariant = Color(0xFF2C3241),
    surfaceContainerLowest = Color(0xFF080A10),
    surfaceContainerLow = Color(0xFF131720),
    surfaceContainer = Color(0xFF171B25),
    surfaceContainerHigh = Color(0xFF1E232F),
    surfaceContainerHighest = Color(0xFF262C39),
    outline = Color(0xFF6E778A),
    outlineVariant = Color(0xFF2C3241),
)

private val YarnShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val YarnTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun YarnTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, shapes = YarnShapes, typography = YarnTypography, content = content)
    }
}
