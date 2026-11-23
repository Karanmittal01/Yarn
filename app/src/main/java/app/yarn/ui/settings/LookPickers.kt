package app.yarn.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yarn.R
import app.yarn.ui.theme.Accent
import app.yarn.ui.theme.LocalDarkTheme
import app.yarn.ui.theme.Wallpaper
import app.yarn.ui.theme.chatWallpaper

/**
 * Yarn Pro's accent colours: a row of swatches. On Yarn Free they're shown with a PRO tag, and
 * choosing one opens Yarn Pro instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(selected: Accent, pro: Boolean, onPick: (Accent) -> Unit) {
    SettingsGroup {
        PickerTitle(stringResource(R.string.accent_colour), stringResource(selected.label), pro)
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Accent.entries.forEach { a ->
                val on = a == selected
                val label = stringResource(a.label)
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .selectable(selected = on, role = Role.RadioButton) { onPick(a) }
                        .padding(2.dp)
                        .border(if (on) 2.dp else 0.dp, if (on) a.swatch else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(a.bubble.first, a.bubble.second)))
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
    }
}

/** Yarn Pro's chat wallpapers: small previews of a chat on each. */
@Composable
fun WallpaperPicker(selected: Wallpaper, pro: Boolean, onPick: (Wallpaper) -> Unit) {
    SettingsGroup {
        PickerTitle(stringResource(R.string.chat_wallpaper), stringResource(selected.label), pro)
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Wallpaper.entries) { w ->
                val on = w == selected
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(14.dp))
                            .border(
                                if (on) 2.dp else 1.dp,
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp),
                            )
                            .selectable(selected = on, role = Role.RadioButton) { onPick(w) },
                    ) {
                        Box(Modifier.fillMaxSize().chatWallpaper(w))
                        // Two tiny bubbles: theirs and yours.
                        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Bottom) {
                            Box(Modifier.width(30.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (LocalDarkTheme.current) Color(0xFF303236) else Color.White))
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.align(Alignment.End).width(26.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(app.yarn.ui.theme.bubbleBrush()))
                        }
                        if (on) Box(
                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                    Text(
                        stringResource(w.label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp).width(64.dp), maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerTitle(title: String, value: String, pro: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp))
        if (!pro) {
            Spacer(Modifier.width(8.dp))
            app.yarn.ui.paywall.ProBadge()
        }
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
    }
}
