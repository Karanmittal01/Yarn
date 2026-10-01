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
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1E),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceVariant = Color(0xFFE1E2E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F9),
    surfaceContainer = Color(0xFFF0F1F4),
    surfaceContainerHigh = Color(0xFFE9EBEF),
    surfaceContainerHighest = Color(0xFFE2E4E9),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFD5D7DD),
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
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF0E0F11),
    onSurface = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFFC6C8CE),
    surfaceVariant = Color(0xFF2E3036),
    surfaceContainerLowest = Color(0xFF08090A),
    surfaceContainerLow = Color(0xFF16171A),
    surfaceContainer = Color(0xFF1B1C20),
    surfaceContainerHigh = Color(0xFF25272B),
    surfaceContainerHighest = Color(0xFF303236),
    outline = Color(0xFF8E9199),
    outlineVariant = Color(0xFF3A3C42),
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
