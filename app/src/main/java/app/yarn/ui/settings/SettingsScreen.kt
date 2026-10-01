package app.yarn.ui.settings

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.BuildConfig
import app.yarn.data.prefs.SwipeAction
import app.yarn.data.prefs.ThemeMode
import app.yarn.telephony.DefaultSmsApp
import app.yarn.ui.common.YarnLogo
import app.yarn.ui.common.container

enum class SettingsPage(val title: String) {
    APPEARANCE("Appearance"),
    NOTIFICATIONS("Notifications"),
    MESSAGING("Messaging"),
    AI("Smart features"),
    PRIVACY("Privacy & security"),
    BLOCKED("Blocked & filters"),
    BACKUP("Backup & restore"),
    ABOUT("About Yarn"),
}

/** Collapsing large-title scaffold shared by every settings page. */
@Composable
fun SettingsScaffold(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = settingsPageColor(),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = settingsPageColor(), scrolledContainerColor = settingsPageColor()),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), content = content)
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val isDefault by vm.c.isDefaultSmsApp.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { context.container.refreshPlatformState() }

    SettingsScaffold("Settings", onBack) {
        settingsHubItems(s, isDefault, onSetDefault = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) } }, onOpen = onOpen)
    }
}

/** The settings hub rows, split out so they can be rendered in previews and screenshot tests. */
internal fun androidx.compose.foundation.lazy.LazyListScope.settingsHubItems(
    s: app.yarn.data.prefs.AppSettings,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    item {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(settingsCardColor())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YarnLogo(size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Yarn", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isDefault) "Default messaging app" else "Not your default SMS app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isDefault) FilledTonalButton(onClick = onSetDefault) { Text("Set") }
        }
    }
    item { Spacer(Modifier.height(10.dp)) }
    item {
        SettingsGroup {
            NavRow(Icons.Outlined.Palette, "Appearance", themeLabel(s.theme), Color(0xFF7C5CFF)) { onOpen(SettingsPage.APPEARANCE) }
            NavRow(Icons.Outlined.Notifications, "Notifications", null, Color(0xFFF2453D)) { onOpen(SettingsPage.NOTIFICATIONS) }
            NavRow(Icons.Outlined.Sms, "Messaging", null, Color(0xFF34C759)) { onOpen(SettingsPage.MESSAGING) }
        }
    }
    item { Spacer(Modifier.height(10.dp)) }
    item {
        SettingsGroup {
            NavRow(Icons.Outlined.AutoAwesome, "Smart features", if (s.aiEnabled) "On" else "Off", Color(0xFF1466F0)) { onOpen(SettingsPage.AI) }
            NavRow(Icons.Outlined.Lock, "Privacy & security", null, Color(0xFF0FA3B1)) { onOpen(SettingsPage.PRIVACY) }
            NavRow(Icons.Outlined.Block, "Blocked", null, Color(0xFF8E8E93)) { onOpen(SettingsPage.BLOCKED) }
        }
    }
    item { Spacer(Modifier.height(10.dp)) }
    item {
        SettingsGroup {
            NavRow(Icons.Outlined.Backup, "Backup & restore", null, Color(0xFFFF9500)) { onOpen(SettingsPage.BACKUP) }
            NavRow(Icons.Outlined.Info, "About", BuildConfig.VERSION_NAME, Color(0xFF636366)) { onOpen(SettingsPage.ABOUT) }
        }
    }
}

private fun themeLabel(t: ThemeMode) = when (t) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private val swipeOptions = listOf(
    SwipeAction.NONE to "Nothing", SwipeAction.ARCHIVE to "Archive", SwipeAction.DELETE to "Delete",
    SwipeAction.READ to "Read / unread", SwipeAction.PIN to "Pin",
)

@Composable
fun AppearanceScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsScaffold("Appearance", onBack) {
        item { SectionHeader("Theme") }
        item {
            SettingsGroup {
                ChoiceRow("Theme", listOf(ThemeMode.SYSTEM to "System default", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), s.theme) { v -> vm.update { it.copy(theme = v) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Wallpaper colours", "Use your wallpaper's colours instead of Yarn blue", s.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
                }
            }
        }
        item { SectionHeader("Text size") }
        item { TextSizeCard(s.textScale) { v -> vm.update { it.copy(textScale = v) } } }
        item { SectionHeader("Inbox gestures") }
        item {
            SettingsGroup {
                ChoiceRow("Swipe right", swipeOptions, s.swipeStart) { v -> vm.update { it.copy(swipeStart = v) } }
                ChoiceRow("Swipe left", swipeOptions, s.swipeEnd) { v -> vm.update { it.copy(swipeEnd = v) } }
            }
        }
    }
}

@Composable
fun NotificationsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScaffold("Notifications", onBack) {
        item { SectionHeader("Content") }
        item {
            SettingsGroup {
                SwitchRow("Show message previews", "Off shows only “New message”", s.showNotificationPreviews) { v -> vm.update { it.copy(showNotificationPreviews = v) } }
                SwitchRow("Copy & delete for codes", "One tap copies the code and deletes the message", s.otpCopyAction) { v -> vm.update { it.copy(otpCopyAction = v) } }
            }
        }
        item { SectionHeader("Which messages notify") }
        item {
            SettingsGroup {
                SwitchRow("Offers & promotions", "Delivered silently when on", s.notifyPromotions) { v -> vm.update { it.copy(notifyPromotions = v) } }
                SwitchRow("Suspected spam", null, s.notifySpam) { v -> vm.update { it.copy(notifySpam = v) } }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            SettingsGroup {
                ClickRow("Sounds & vibration", "Open Android notification settings") {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
        }
    }
}

@Composable
fun MessagingScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sims = vm.c.sims.current()
    SettingsScaffold("Messaging", onBack) {
        item { SectionHeader("Sending") }
        item {
            SettingsGroup {
                ChoiceRow("Undo send", listOf(0 to "Off", 3 to "3 seconds", 5 to "5 seconds", 10 to "10 seconds"), s.sendDelaySeconds) { v -> vm.update { it.copy(sendDelaySeconds = v) } }
                SwitchRow("Delivery reports", "Ask the network to confirm delivery", s.deliveryReports) { v -> vm.update { it.copy(deliveryReports = v) } }
                SwitchRow("Group messaging", "One group thread everyone can reply to (MMS)", s.groupMms) { v -> vm.update { it.copy(groupMms = v) } }
                ClickRow("On-time scheduled messages", "Allow exact alarms for scheduled sends") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        }
        item { SectionHeader("Multimedia (MMS)") }
        item {
            SettingsGroup {
                SwitchRow("Auto-download", "MMS uses mobile data", s.autoDownloadMms) { v -> vm.update { it.copy(autoDownloadMms = v) } }
                SwitchRow("Auto-download while roaming", "May cost extra", s.autoDownloadMmsRoaming, enabled = s.autoDownloadMms) { v -> vm.update { it.copy(autoDownloadMmsRoaming = v) } }
            }
        }
        if (sims.isNotEmpty()) {
            item { SectionHeader("SIM cards") }
            item {
                SettingsGroup {
                    sims.forEach { sim ->
                        InfoRow(sim.label, sim.number)
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsScaffold("Privacy & security", onBack) {
        item { SectionHeader("Protection") }
        item {
            SettingsGroup {
                SwitchRow("App lock", "Fingerprint, face or screen lock to open Yarn", s.appLock) { v -> vm.update { it.copy(appLock = v) } }
                if (s.appLock) {
                    ChoiceRow("Lock after", listOf(0 to "Immediately", 60 to "1 minute", 300 to "5 minutes", 1800 to "30 minutes"), s.lockTimeoutSeconds) { v -> vm.update { it.copy(lockTimeoutSeconds = v) } }
                }
                SwitchRow("Block screenshots", "Also hides Yarn in Recents", s.secureScreen) { v -> vm.update { it.copy(secureScreen = v) } }
            }
        }
        item { SectionHeader("Clean-up") }
        item {
            SettingsGroup {
                ChoiceRow("Delete old OTP messages", listOf(0 to "Never", 24 to "After 1 day", 168 to "After 7 days", 720 to "After 30 days"), s.otpAutoDeleteHours) { v -> vm.update { it.copy(otpAutoDeleteHours = v) } }
                ChoiceRow("Delete old spam", listOf(0 to "Never", 7 to "After 7 days", 30 to "After 30 days", 90 to "After 90 days"), s.spamAutoDeleteDays) { v -> vm.update { it.copy(spamAutoDeleteDays = v) } }
            }
        }
        item {
            Text(
                "Your messages are stored in an encrypted database whose key never leaves this phone. Yarn has no servers and no analytics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold("About", onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                YarnLogo(size = 72.dp)
                Spacer(Modifier.height(4.dp))
                Text("Yarn", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsGroup {
                InfoRow("Private by design", "Messages and AI stay on your phone. Core messaging never uses the internet.")
                InfoRow("SMS & MMS", "Android doesn't offer RCS to third-party apps, so Yarn uses SMS and MMS.")
            }
        }
    }
}

private val TEXT_SIZES = listOf(0.85f to "Small", 0.92f to "Compact", 1f to "Default", 1.1f to "Large", 1.2f to "Larger", 1.3f to "Largest")

/** Slider for Yarn's own text size, with a live preview of an inbox row. */
@Composable
fun TextSizeCard(value: Float, onChange: (Float) -> Unit) {
    val index = TEXT_SIZES.indexOfFirst { kotlin.math.abs(it.first - value) < 0.01f }.takeIf { it >= 0 } ?: 2
    var pos by androidx.compose.runtime.remember(index) { androidx.compose.runtime.mutableFloatStateOf(index.toFloat()) }
    val current = TEXT_SIZES[pos.toInt().coerceIn(0, TEXT_SIZES.lastIndex)]
    val base = androidx.compose.ui.platform.LocalDensity.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(settingsCardColor())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // Preview at the size being chosen (relative to the size already applied).
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, base.fontScale / value * current.first),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                app.yarn.ui.common.Avatar("Priya", null, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Priya Sharma", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Are we still on for dinner tonight?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Slider(
                value = pos,
                onValueChange = { pos = it },
                onValueChangeFinished = { onChange(TEXT_SIZES[pos.toInt().coerceIn(0, TEXT_SIZES.lastIndex)].first) },
                valueRange = 0f..TEXT_SIZES.lastIndex.toFloat(),
                steps = TEXT_SIZES.size - 2,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    .androidxSemantics("Text size, ${current.second}"),
            )
            Text("A", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            current.second + if (current.first == 1f) " · same as your phone" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

private fun Modifier.androidxSemantics(label: String) = this.then(
    androidx.compose.ui.Modifier.semantics { contentDescription = label },
)
