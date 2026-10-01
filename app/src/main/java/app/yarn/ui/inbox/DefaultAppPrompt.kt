package app.yarn.ui.inbox

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yarn.ui.common.YarnLogo
import app.yarn.ui.theme.LocalDarkTheme

/**
 * Full-screen invitation to make Yarn the default SMS app. Android only lets the default app send
 * and receive, so this is shown whenever Yarn isn't default; "Not now" hides it until next launch.
 */
@Composable
fun DefaultAppPrompt(onSetDefault: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DefaultAppPromptContent(onSetDefault, onDismiss)
    }
}

@Composable
fun DefaultAppPromptContent(onSetDefault: () -> Unit, onDismiss: () -> Unit) {
    val dark = LocalDarkTheme.current
    val bg = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF0B1B3F), Color(0xFF0E0F11)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFDCE8FF), Color(0xFFFFFFFF)))
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(if (shown) 1f else 0f, tween(520, easing = FastOutSlowInEasing), label = "enter")

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Illustration(Modifier.graphicsLayer { alpha = enter; scaleX = 0.9f + 0.1f * enter; scaleY = 0.9f + 0.1f * enter })
            Spacer(Modifier.height(36.dp))
            Column(
                Modifier.graphicsLayer { alpha = enter; translationY = (1f - enter) * 40f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Make Yarn your SMS app",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android lets only your default SMS app send and receive messages.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Benefit(Icons.Outlined.Sort, "A calm inbox, sorted for you")
                Benefit(Icons.Outlined.GppGood, "Spam and scam protection")
                Benefit(Icons.Outlined.Lock, "Private: everything stays on your phone")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSetDefault,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("Set as default SMS app", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Not now", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The Yarn logo with message bubbles floating around it. */
@Composable
private fun Illustration(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "float")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(230.dp).clip(CircleShape).background(primary.copy(alpha = 0.10f)))
        Box(Modifier.size(170.dp).clip(CircleShape).background(primary.copy(alpha = 0.12f)))
        YarnLogo(size = 112.dp, modifier = Modifier.shadow(18.dp, RoundedCornerShape(34.dp)))
        Bubble(Icons.Outlined.Password, "482913", Color(0xFF7C5CFF), x = (-92).dp, y = (-96).dp, lift = a * 8f)
        Bubble(Icons.Outlined.AccountBalance, "₹2,500", Color(0xFF34C759), x = 96.dp, y = (-58).dp, lift = b * 10f)
        Bubble(Icons.Outlined.ShoppingBag, "Delivered", Color(0xFFFF9500), x = (-74).dp, y = 104.dp, lift = b * 7f)
        Dot(primary, x = 108.dp, y = 92.dp, size = 14.dp, lift = a * 6f)
        Dot(Color(0xFFFFC94D), x = (-124).dp, y = 12.dp, size = 10.dp, lift = b * 6f)
        Dot(Color(0xFF7EEBFB), x = 40.dp, y = (-128).dp, size = 8.dp, lift = a * 5f)
    }
}

@Composable
private fun Bubble(icon: ImageVector, label: String, tint: Color, x: Dp, y: Dp, lift: Float) {
    Row(
        Modifier
            .offset(x, y - lift.dp)
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Dot(color: Color, x: Dp, y: Dp, size: Dp, lift: Float) {
    Box(Modifier.offset(x, y - lift.dp).size(size).scale(1f).clip(CircleShape).background(color))
}
