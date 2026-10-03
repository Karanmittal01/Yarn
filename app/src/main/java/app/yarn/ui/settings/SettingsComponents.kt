package app.yarn.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yarn.AppContainer
import app.yarn.data.prefs.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import app.yarn.R

class SettingsViewModel(val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(transform) }
    fun launch(block: suspend AppContainer.() -> Unit) = viewModelScope.launch { c.block() }
}

@Composable
fun SectionHeader(text: String) = Text(
    text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 32.dp, top = 18.dp, bottom = 6.dp).semantics { heading() },
)

/** Background of a grouped-settings card: white on the light grey page, raised in dark mode. */
@Composable
fun settingsCardColor(): Color =
    if (app.yarn.ui.theme.LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest

/** Page colour behind the grouped cards. */
@Composable
fun settingsPageColor(): Color =
    if (app.yarn.ui.theme.LocalDarkTheme.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer

/** A rounded card grouping related rows; rows are separated by hairline dividers. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(settingsCardColor())
            .padding(vertical = 2.dp),
        content = content,
    )
}

/** Small rounded-square icon tile in a solid colour, as on premium settings screens. */
@Composable
fun IconTile(icon: ImageVector, tint: Color) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Shared compact row layout used by every settings row. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, tint)
            Spacer(Modifier.width(14.dp))
        } else if (leading != null) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor.copy(alpha = alpha))
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun Chevron() = Icon(
    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp),
)

@Composable
private fun ValueText(value: String) = Text(
    value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp),
)

/** Navigation row with an icon tile and an optional short value, used on the settings hub. */
@Composable
fun NavRow(icon: ImageVector, title: String, value: String?, tint: Color, onClick: () -> Unit) {
    SettingsRow(
        title, Modifier.clickable(onClick = onClick), icon = icon, tint = tint,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) ValueText(value)
                Chevron()
            }
        },
    )
}

@Composable
fun SwitchRow(title: String, summary: String? = null, checked: Boolean, enabled: Boolean = true, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title,
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        summary = summary, icon = icon, tint = tint, enabled = enabled,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.scale(0.85f)) },
    )
}

@Composable
fun ClickRow(title: String, summary: String? = null, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary, destructive: Boolean = false, onClick: () -> Unit) {
    SettingsRow(
        title, Modifier.clickable(onClick = onClick), summary = summary, icon = icon, tint = tint,
        titleColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        trailing = if (destructive) null else ({ Chevron() }),
    )
}

/** Plain information row inside a group. */
@Composable
fun InfoRow(title: String, summary: String? = null) = SettingsRow(title, summary = summary)

@Composable
fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsRow(
        title, Modifier.clickable { open = true }, icon = icon, tint = tint,
        trailing = { options.firstOrNull { it.first == selected }?.second?.let { ValueText(it.substringBefore(" —")) } },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value); open = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Text(label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
