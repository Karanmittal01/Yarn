package app.yarn.ui.common

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yarn.AppContainer
import app.yarn.YarnApplication
import app.yarn.data.repo.InboxGroup
import app.yarn.intelligence.Category
import app.yarn.notifications.Avatars
import coil3.compose.AsyncImage
import java.util.Date

val Context.container: AppContainer get() = (applicationContext as YarnApplication).container

/** Creates a ViewModel with access to the app container. */
@Composable
inline fun <reified VM : ViewModel> yarnViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.container
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = create(container) as T
        },
    )
}

/**
 * Tonal avatar: soft tinted circle with a coloured initial (or icon for businesses and groups).
 * Contact photos are shown as-is.
 */
@Composable
fun Avatar(
    name: String,
    photoUri: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    isGroup: Boolean = false,
    icon: ImageVector? = null,
) {
    val accent = Color(Avatars.colorFor(name))
    val dark = app.yarn.ui.theme.LocalDarkTheme.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (photoUri != null) Color.Transparent else if (dark) accent.mix(Color.White, 0.45f) else accent.mix(Color.White, 0.78f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // Solid pastel circle with a deep-toned glyph: high contrast in both themes, like Google Messages.
        val tint = if (dark) accent.mix(Color.Black, 0.62f) else accent.mix(Color.Black, 0.35f)
        when {
            photoUri != null -> AsyncImage(model = photoUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            isGroup -> androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Groups, null, tint = tint, modifier = Modifier.size(size * 0.5f))
            icon != null -> androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.46f))
            else -> Text(Avatars.initial(name), color = tint, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
        }
    }
}

private fun Color.mix(other: Color, t: Float): Color = Color(red + (other.red - red) * t, green + (other.green - green) * t, blue + (other.blue - blue) * t, 1f)

object CategoryUi {
    fun label(c: Category): String = when (c) {
        Category.PERSONAL -> "Personal"
        Category.WORK -> "Work"
        Category.OTP -> "OTP"
        Category.BANKING -> "Banking"
        Category.PAYMENTS -> "Payments"
        Category.BILLS -> "Bills"
        Category.DELIVERY -> "Deliveries"
        Category.SHOPPING -> "Shopping"
        Category.TRAVEL -> "Travel"
        Category.UPDATES -> "Updates"
        Category.PROMOTIONS -> "Promotions"
        Category.SPAM -> "Spam"
    }

    fun icon(c: Category): ImageVector = when (c) {
        Category.PERSONAL -> Icons.Outlined.Person
        Category.WORK -> Icons.Outlined.Work
        Category.OTP -> Icons.Outlined.Password
        Category.BANKING -> Icons.Outlined.AccountBalance
        Category.PAYMENTS -> Icons.Outlined.Payments
        Category.BILLS -> Icons.Outlined.ReceiptLong
        Category.DELIVERY -> Icons.Outlined.LocalShipping
        Category.SHOPPING -> Icons.Outlined.ShoppingBag
        Category.TRAVEL -> Icons.Outlined.Flight
        Category.UPDATES -> Icons.Outlined.Info
        Category.PROMOTIONS -> Icons.Outlined.Campaign
        Category.SPAM -> Icons.Outlined.Block
    }

    val default: ImageVector get() = Icons.AutoMirrored.Outlined.Message

    fun groupIcon(g: InboxGroup): ImageVector = when (g) {
        InboxGroup.PERSONAL -> Icons.Outlined.Person
        InboxGroup.TRANSACTIONS -> Icons.Outlined.AccountBalance
        InboxGroup.UPDATES -> Icons.Outlined.Inventory2
        InboxGroup.OFFERS -> Icons.Outlined.LocalOffer
    }
}

object Format {
    /** Compact list timestamp: time today, weekday this week, date otherwise. */
    fun listTime(context: Context, millis: Long): String {
        val now = System.currentTimeMillis()
        return when {
            DateUtils.isToday(millis) -> DateFormat.getTimeFormat(context).format(Date(millis))
            now - millis < 6 * DateUtils.DAY_IN_MILLIS -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
            else -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or (if (isThisYear(millis)) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_NUMERIC_DATE))
        }
    }

    fun time(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

    fun dayHeader(context: Context, millis: Long): String = when {
        DateUtils.isToday(millis) -> "Today"
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or (if (isThisYear(millis)) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR))
    }

    fun dateTime(context: Context, millis: Long): String =
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR)

    fun sameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun isThisYear(millis: Long): Boolean {
        val c = java.util.Calendar.getInstance()
        val y = c.get(java.util.Calendar.YEAR)
        c.timeInMillis = millis
        return c.get(java.util.Calendar.YEAR) == y
    }

    fun size(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Group picker used for corrections; lets the user make the choice sticky for the sender. */
@Composable
fun CategoryPickerDialog(current: Category?, onPick: (Category, Boolean) -> Unit, onDismiss: () -> Unit) {
    var always by remember { mutableStateOf(true) }
    val currentGroup = InboxGroup.of(current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to") },
        text = {
            Column {
                InboxGroup.entries.forEach { g ->
                    val selected = g == currentGroup
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { onPick(g.representative, always); onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(CategoryUi.groupIcon(g), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(g.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (selected) Text("Current", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.padding(top = 8.dp).clickable { always = !always }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = always, onCheckedChange = { always = it })
                    Text("Always do this for this sender", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The Yarn app icon, drawn from the same vector layers as the launcher icon. */
@Composable
fun YarnLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.3f))
            .clearAndSetSemantics { },
    ) {
        // The adaptive-icon artwork is designed for a 108dp canvas with a 72dp safe zone; scale it to fill.
        val zoom = 1.5f
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(app.yarn.R.drawable.ic_launcher_background), null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Crop,
        )
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(app.yarn.R.drawable.ic_launcher_foreground), null,
            modifier = Modifier.size(size).graphicsLayer(scaleX = zoom, scaleY = zoom),
        )
    }
}
