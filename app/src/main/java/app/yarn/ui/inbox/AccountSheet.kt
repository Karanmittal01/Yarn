package app.yarn.ui.inbox

import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yarn.data.prefs.AppSettings
import app.yarn.ui.settings.SettingsPage
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            MyAvatar(me?.name, me?.photoUri, size)
        }
    }
}

/** Your picture, or your initial, or a plain person outline before you've added a name. */
@Composable
internal fun MyAvatar(name: String?, photo: String?, size: androidx.compose.ui.unit.Dp) {
    val n = name?.takeIf { it.isNotBlank() }
    Avatar(n ?: "Me", photo, size = size, icon = if (n == null) Icons.Outlined.Person else null)
}

/**
 * Your profile and everything else, as one screen: your card (tap to change your name or picture), your
 * folders, then Yarn's settings. Every option opens on top of it, so Back always comes back here.
 */
@Composable
fun ProfileScreen(
    me: ContactInfo?,
    sims: List<SimInfo>,
    hasUnread: Boolean,
    settings: AppSettings,
    onMarkAllRead: () -> Unit,
    onNavigate: (InboxDestination) -> Unit,
    onFeedback: () -> Unit,
    onSaveProfile: (name: String, photo: android.graphics.Bitmap?, removePhoto: Boolean) -> Unit,
    onClose: () -> Unit,
    plan: app.yarn.billing.Access? = null,
) {
    var editing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = editing) { editing = false }
    androidx.compose.material3.Surface(color = app.yarn.ui.settings.settingsPageColor(), contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedContent(
            editing,
            // Editing slides in like any other page of this menu.
            transitionSpec = {
                if (targetState) app.yarn.ui.theme.Motion.pushEnter togetherWith app.yarn.ui.theme.Motion.pushExit
                else app.yarn.ui.theme.Motion.popEnter togetherWith app.yarn.ui.theme.Motion.popExit
            },
            label = "profilePage",
        ) { edit ->
            if (edit) {
                ProfileEditor(
                    name = me?.name.orEmpty(), photo = me?.photoUri,
                    onSave = { name, photo, remove -> onSaveProfile(name, photo, remove); editing = false },
                    onBack = { editing = false },
                )
            } else {
                AccountSheetContent(
                    me, sims, hasUnread, settings,
                    onMarkAllRead = onMarkAllRead,
                    onNavigate = onNavigate,
                    onFeedback = onFeedback,
                    onEditProfile = { editing = true },
                    onDismiss = onClose,
                    plan = plan,
                )
            }
        }
    }
}

@Composable
fun AccountSheetContent(
    me: ContactInfo?,
    sims: List<SimInfo>,
    hasUnread: Boolean,
    settings: AppSettings,
    onMarkAllRead: () -> Unit,
    onNavigate: (InboxDestination) -> Unit,
    onFeedback: () -> Unit,
    onEditProfile: () -> Unit,
    onDismiss: () -> Unit,
    plan: app.yarn.billing.Access? = null,
) {
    val card = app.yarn.ui.settings.settingsCardColor()
    val name = me?.name?.takeIf { it.isNotBlank() }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close), Modifier.size(24.dp)) }
        }
        // Who you are: compact, and a tap away from changing.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(card)
                .clickable(onClickLabel = stringResource(R.string.edit_profile), onClick = onEditProfile)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(50.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
                MyAvatar(name, me?.photoUri, 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name ?: stringResource(R.string.add_your_name),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.Medium,
                    color = if (name != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                sims.forEach { sim ->
                    Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (sims.size > 1) app.yarn.ui.common.SimBadge(sim, Modifier.padding(end = 5.dp), height = 11.dp)
                        Text(
                            listOfNotNull(sim.number?.takeIf { it.isNotBlank() }, sim.carrier).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        // Yarn's plan: the trial's days left, or the subscription.
        val planRow: Pair<String, String>? = when (plan) {
            is app.yarn.billing.Access.Trial -> stringResource(R.string.plan_row_trial) to
                androidx.compose.ui.res.pluralStringResource(R.plurals.plan_days_left, plan.daysLeft, plan.daysLeft)
            is app.yarn.billing.Access.Unlocked -> stringResource(R.string.plan_row_title) to
                stringResource(if (plan.lifetime) R.string.plan_lifetime else R.string.plan_active)
            else -> null
        }
        planRow?.let { (title, value) ->
            SheetGroup(card) { SheetItem(Icons.Outlined.WorkspacePremium, title, value = value) { onNavigate(InboxDestination.Plan) } }
            Spacer(Modifier.height(8.dp))
        }
        SheetGroup(card) {
            SheetItem(Icons.Outlined.StarOutline, stringResource(R.string.starred)) { onNavigate(InboxDestination.Starred) }
            SheetItem(Icons.Outlined.Archive, stringResource(R.string.archived)) { onNavigate(InboxDestination.Archived) }
            SheetItem(Icons.Outlined.DeleteOutline, stringResource(R.string.recycle_bin)) { onNavigate(InboxDestination.Bin) }
            SheetItem(Icons.Outlined.GppMaybe, stringResource(R.string.verdict_spam)) { onNavigate(InboxDestination.Spam) }
            SheetItem(Icons.Outlined.Block, stringResource(R.string.blocked)) { onNavigate(InboxDestination.Blocked) }
            SheetItem(Icons.Outlined.DoneAll, stringResource(R.string.mark_all_read), enabled = hasUnread, onClick = onMarkAllRead)
        }
        Spacer(Modifier.height(8.dp))
        // Settings live right here; each row opens its own page.
        fun open(page: SettingsPage) = onNavigate(InboxDestination.Setting(page))
        SheetGroup(card) {
            SheetItem(Icons.Outlined.Palette, stringResource(R.string.appearance), value = app.yarn.ui.settings.themeLabel(settings.theme)) { open(SettingsPage.APPEARANCE) }
            SheetItem(Icons.Outlined.Notifications, stringResource(R.string.notifications)) { open(SettingsPage.NOTIFICATIONS) }
            SheetItem(Icons.Outlined.Sms, stringResource(R.string.messaging)) { open(SettingsPage.MESSAGING) }
            SheetItem(Icons.Outlined.AutoAwesome, stringResource(R.string.smart_features), value = stringResource(if (settings.aiEnabled) R.string.on else R.string.off)) { open(SettingsPage.AI) }
            SheetItem(Icons.Outlined.Lock, stringResource(R.string.privacy_security)) { open(SettingsPage.PRIVACY) }
            SheetItem(
                Icons.Outlined.CleaningServices, stringResource(R.string.clean_up),
                value = if (app.yarn.data.repo.Cleaner.isAutoCleanOn(settings)) stringResource(R.string.auto) else null,
            ) { open(SettingsPage.CLEANUP) }
        }
        Spacer(Modifier.height(8.dp))
        SheetGroup(card) {
            SheetItem(Icons.Outlined.Backup, stringResource(R.string.backup_restore)) { open(SettingsPage.BACKUP) }
            SheetItem(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help_feedback), onClick = onFeedback)
            SheetItem(Icons.Outlined.Info, stringResource(R.string.about), value = app.yarn.BuildConfig.VERSION_NAME) { open(SettingsPage.ABOUT) }
        }
        Text(
            stringResource(R.string.private_on_phone),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SheetGroup(color: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(color).padding(vertical = 4.dp)) {
        content()
    }
}

@Composable
private fun SheetItem(icon: ImageVector, title: String, enabled: Boolean = true, value: String? = null, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        // Menu-like rows: about 46dp, still an easy tap.
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(enabled = enabled, onClick = onClick).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = ink)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp), color = ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    }
}
