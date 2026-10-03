package app.yarn.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yarn.telephony.SimInfo
import app.yarn.ui.theme.LocalDarkTheme
import androidx.compose.ui.res.stringResource
import app.yarn.R

/**
 * Each SIM slot keeps one colour everywhere in Yarn (inbox, chat, composer, notifications), so on
 * dual-SIM phones it's obvious at a glance which number a message came to or will go from.
 */
object SimColors {
    private val light = listOf(Color(0xFF1A73E8), Color(0xFFE8710A), Color(0xFF188038), Color(0xFF9334E6))
    private val dark = listOf(Color(0xFF8AB4F8), Color(0xFFFCAD70), Color(0xFF81C995), Color(0xFFD7AEFB))

    fun of(slot: Int, darkTheme: Boolean): Color = (if (darkTheme) dark else light)[(slot - 1).mod(light.size)]

    /** For notifications, which can't follow the app's theme. */
    fun argb(slot: Int): Int {
        val c = light[(slot - 1).mod(light.size)]
        return android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    }
}

/** A SIM card: rounded rectangle with the top-right corner clipped, like the real thing. */
private val SimShape = GenericShape { size, _ ->
    val cut = size.width * 0.34f
    val r = size.width * 0.16f
    val rounded = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
    val notch = Path().apply {
        moveTo(size.width - cut, 0f)
        lineTo(size.width + 1f, 0f)
        lineTo(size.width + 1f, cut)
        close()
    }
    addPath(Path().apply { op(rounded, notch, androidx.compose.ui.graphics.PathOperation.Difference) })
}

@Composable
fun simColor(sim: SimInfo): Color = SimColors.of(sim.slot, LocalDarkTheme.current)

/** "SIM 1 · Jio" in Yarn's language. */
fun simLabel(context: android.content.Context, sim: SimInfo): String = context.getString(R.string.sim_label, sim.slot, sim.carrier)

@Composable
fun simLabel(sim: SimInfo): String = stringResource(R.string.sim_label, sim.slot, sim.carrier)

/** Small coloured SIM card with the slot number inside. */
@Composable
fun SimBadge(sim: SimInfo, modifier: Modifier = Modifier, height: Dp = 16.dp, fill: Color? = null, ink: Color? = null) {
    val label = simLabel(sim)
    val color = fill ?: simColor(sim)
    val onColor = ink ?: if (LocalDarkTheme.current) Color(0xFF0B0B0C) else Color.White
    Box(
        modifier.width(height * 0.78f).height(height).background(color, SimShape).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            sim.slot.toString(),
            style = TextStyle(fontSize = (height.value * 0.62f).sp, lineHeight = (height.value * 0.62f).sp, fontWeight = FontWeight.Bold, color = onColor),
        )
    }
}

/** SIM badge followed by the network name, e.g. [1] Jio. */
@Composable
fun SimTag(
    sim: SimInfo,
    modifier: Modifier = Modifier,
    badgeHeight: Dp = 14.dp,
    fontSize: TextUnit = 12.sp,
    tinted: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SimBadge(sim, height = badgeHeight)
        Text(
            sim.carrier,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize, lineHeight = fontSize * 1.2f),
            fontWeight = FontWeight.Medium,
            color = if (tinted) simColor(sim) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
