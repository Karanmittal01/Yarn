package app.yarn.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yarn.R
import app.yarn.data.contacts.ContactInfo
import app.yarn.telephony.SimInfo
import app.yarn.ui.common.Avatar
import app.yarn.ui.theme.LocalDarkTheme

/** The ring around the profile picture, in Yarn's colours. */
private val ring = Brush.sweepGradient(listOf(Color(0xFF3D7BFF), Color(0xFF8A5CFF), Color(0xFF00B4D8), Color(0xFF3D7BFF)))

/** Your picture in the top-right corner; opens [AccountSheet]. */
@Composable
fun ProfileButton(me: ContactInfo?, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val label = stringResource(R.string.account_and_more)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size + 6.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            Avatar(me?.name?.takeIf { it.isNotBlank() } ?: "Y", me?.photoUri, size = size)
        }
    }
}

/**
 * Everything that used to hide behind the ⋮ menu, laid out like Google Messages' account page:
 * who you are (your card and SIM numbers), then folders, then app-wide actions.
 */
@Composable
fun AccountSheet(
    me: ContactInfo?,
    sims: List<SimInfo>,
    hasUnread: Boolean,
    onMarkAllRead: () -> Unit,
    onNavigate: (InboxDestination) -> Unit,
    onFeedback: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { shown.targetState = true }
    fun close(then: () -> Unit = {}) { onDismiss(); then() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AnimatedVisibility(shown, enter = app.yarn.ui.theme.Motion.rise, exit = app.yarn.ui.theme.Motion.sink) {
            AccountSheetContent(
                me, sims, hasUnread,
                onMarkAllRead = { close(onMarkAllRead) },
                onNavigate = { d -> close { onNavigate(d) } },
                onFeedback = { close(onFeedback) },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
fun AccountSheetContent(
    me: ContactInfo?,
    sims: List<SimInfo>,
    hasUnread: Boolean,
    onMarkAllRead: () -> Unit,
    onNavigate: (InboxDestination) -> Unit,
    onFeedback: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    // The same page and cards as Settings, so the two screens read as one design.
    val page = app.yarn.ui.settings.settingsPageColor()
    val card = app.yarn.ui.settings.settingsCardColor()
    // A Surface gives the page the theme's text colour; a bare dialog would fall back to black.
    androidx.compose.material3.Surface(color = page, contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close), Modifier.size(26.dp)) }
        }
        // Who you are.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(card).padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(78.dp).border(3.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
                Avatar(me?.name?.takeIf { it.isNotBlank() } ?: "Y", me?.photoUri, size = 68.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    me?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.you),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (sims.isEmpty()) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                sims.forEach { sim ->
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (sims.size > 1) app.yarn.ui.common.SimBadge(sim, Modifier.padding(end = 6.dp), height = 14.dp)
                        Text(
                            listOfNotNull(sim.number?.takeIf { it.isNotBlank() }, sim.carrier).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SheetGroup(card) {
            SheetItem(Icons.Outlined.StarOutline, stringResource(R.string.starred)) { onNavigate(InboxDestination.Starred) }
            SheetItem(Icons.Outlined.Archive, stringResource(R.string.archived)) { onNavigate(InboxDestination.Archived) }
            SheetItem(Icons.Outlined.DeleteOutline, stringResource(R.string.recycle_bin)) { onNavigate(InboxDestination.Bin) }
            SheetItem(Icons.Outlined.GppMaybe, stringResource(R.string.verdict_spam)) { onNavigate(InboxDestination.Spam) }
            SheetItem(Icons.Outlined.Block, stringResource(R.string.blocked)) { onNavigate(InboxDestination.Blocked) }
            SheetItem(Icons.Outlined.DoneAll, stringResource(R.string.mark_all_read), enabled = hasUnread, last = true, onClick = onMarkAllRead)
        }
        Spacer(Modifier.height(12.dp))
        SheetGroup(card) {
            SheetItem(Icons.Outlined.CleaningServices, stringResource(R.string.clean_up)) { onNavigate(InboxDestination.Cleanup) }
            SheetItem(Icons.Outlined.Settings, stringResource(R.string.settings)) { onNavigate(InboxDestination.Settings) }
            SheetItem(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help_feedback), last = true, onClick = onFeedback)
        }
        Text(
            stringResource(R.string.private_on_phone),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
    }
}

@Composable
private fun SheetGroup(color: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color).padding(vertical = 6.dp)) {
        content()
    }
}

@Composable
private fun SheetItem(icon: ImageVector, title: String, enabled: Boolean = true, last: Boolean = false, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = ink)
            Spacer(Modifier.width(20.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), color = ink)
        }
        // No dividers: like the Settings cards, rows sit on one clean card.
    }
}
