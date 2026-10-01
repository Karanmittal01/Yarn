package app.yarn.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yarn.BuildConfig
import app.yarn.data.prefs.SwipeAction
import app.yarn.data.prefs.ThemeMode
import app.yarn.telephony.DefaultSmsApp
import app.yarn.ui.common.container

enum class SettingsPage { AI, BLOCKED, BACKUP }

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val isDefault by vm.c.isDefaultSmsApp.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sims = vm.c.sims.current()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { context.container.refreshPlatformState() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Default SMS app") },
                    supportingContent = { Text(if (isDefault) "Yarn is your default messaging app" else "Required to send and receive messages") },
                    trailingContent = {
                        if (!isDefault) TextButton(onClick = { DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) } }) { Text("Set default") }
                    },
                )
            }
            item { SectionHeader("Appearance") }
            item {
                ChoiceRow("Theme", listOf(ThemeMode.SYSTEM to "System default", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), s.theme) { v -> vm.update { it.copy(theme = v) } }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
                SwitchRow("Dynamic colour", "Match your wallpaper colours", s.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
            }
            item {
                ChoiceRow("Swipe right", swipeOptions, s.swipeStart) { v -> vm.update { it.copy(swipeStart = v) } }
            }
            item {
                ChoiceRow("Swipe left", swipeOptions, s.swipeEnd) { v -> vm.update { it.copy(swipeEnd = v) } }
            }

            item { SectionHeader("Notifications") }
            item { SwitchRow("Show message previews", "Turn off to show only “New message”", s.showNotificationPreviews) { v -> vm.update { it.copy(showNotificationPreviews = v) } } }
            item { SwitchRow("Copy-code button for OTPs", "Adds “Copy 123456” to verification code notifications", s.otpCopyAction) { v -> vm.update { it.copy(otpCopyAction = v) } } }
            item { SwitchRow("Notify for promotions", "Promotions arrive silently when on", s.notifyPromotions) { v -> vm.update { it.copy(notifyPromotions = v) } } }
            item { SwitchRow("Notify for suspected spam", null, s.notifySpam) { v -> vm.update { it.copy(notifySpam = v) } } }
            item {
                ClickRow("System notification settings", "Sounds, vibration and per-category channels") {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }

            item { SectionHeader("Sending") }
            item {
                ChoiceRow("Undo send", listOf(0 to "Off", 3 to "3 seconds", 5 to "5 seconds", 10 to "10 seconds"), s.sendDelaySeconds) { v -> vm.update { it.copy(sendDelaySeconds = v) } }
            }
            item { SwitchRow("Delivery reports", "Ask the network to confirm delivery (carrier dependent)", s.deliveryReports) { v -> vm.update { it.copy(deliveryReports = v) } } }
            item { SwitchRow("Group messaging (MMS)", "Send one group message everyone can reply to. Off sends individual texts.", s.groupMms) { v -> vm.update { it.copy(groupMms = v) } } }
            item { SwitchRow("Auto-download MMS", "Multimedia messages need mobile data", s.autoDownloadMms) { v -> vm.update { it.copy(autoDownloadMms = v) } } }
            item { SwitchRow("Auto-download while roaming", "May incur roaming charges", s.autoDownloadMmsRoaming, enabled = s.autoDownloadMms) { v -> vm.update { it.copy(autoDownloadMmsRoaming = v) } } }
            if (sims.isNotEmpty()) item {
                ListItem(headlineContent = { Text("SIM cards") }, supportingContent = { Text(sims.joinToString("\n") { it.label + (it.number?.let { n -> " · $n" } ?: "") }) })
            }
            item {
                ClickRow("Exact scheduled sending", "Allow alarms so scheduled messages go out on time") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }

            item { SectionHeader("Smart features") }
            item { ClickRow("AI & learning", "On-device categories, spam protection, summaries, replies and what Yarn has learned") { onOpen(SettingsPage.AI) } }

            item { SectionHeader("Privacy & security") }
            item { SwitchRow("App lock", "Require fingerprint, face or screen lock to open Yarn", s.appLock) { v -> vm.update { it.copy(appLock = v) } } }
            if (s.appLock) item {
                ChoiceRow("Lock after", listOf(0 to "Immediately", 60 to "1 minute", 300 to "5 minutes", 1800 to "30 minutes"), s.lockTimeoutSeconds) { v -> vm.update { it.copy(lockTimeoutSeconds = v) } }
            }
            item { SwitchRow("Block screenshots", "Also hides Yarn's content in Recents", s.secureScreen) { v -> vm.update { it.copy(secureScreen = v) } } }
            item {
                ChoiceRow("Auto-delete old OTP messages", listOf(0 to "Never", 24 to "After 1 day", 168 to "After 7 days", 720 to "After 30 days"), s.otpAutoDeleteHours) { v -> vm.update { it.copy(otpAutoDeleteHours = v) } }
            }
            item {
                ChoiceRow("Auto-delete spam", listOf(0 to "Never", 7 to "After 7 days", 30 to "After 30 days", 90 to "After 90 days"), s.spamAutoDeleteDays) { v -> vm.update { it.copy(spamAutoDeleteDays = v) } }
            }
            item { ClickRow("Blocked numbers & filters", null) { onOpen(SettingsPage.BLOCKED) } }
            item { ClickRow("Backup & restore", "Encrypted, saved wherever you choose") { onOpen(SettingsPage.BACKUP) } }

            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("Yarn ${BuildConfig.VERSION_NAME}") },
                    supportingContent = {
                        Text(
                            "Messages are stored on your phone in an encrypted database. Core messaging never uses the internet. " +
                                "RCS isn't available to third-party apps on Android, so Yarn uses SMS and MMS.",
                        )
                    },
                )
            }
        }
    }
}

private val swipeOptions = listOf(
    SwipeAction.NONE to "Nothing", SwipeAction.ARCHIVE to "Archive", SwipeAction.DELETE to "Delete",
    SwipeAction.READ to "Read / unread", SwipeAction.PIN to "Pin",
)
