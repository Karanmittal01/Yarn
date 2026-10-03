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
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import app.yarn.R

enum class SettingsPage(@androidx.annotation.StringRes val title: Int) {
    APPEARANCE(R.string.appearance),
    NOTIFICATIONS(R.string.notifications),
    MESSAGING(R.string.messaging),
    AI(R.string.smart_features),
    PRIVACY(R.string.privacy_security),
    CLEANUP(R.string.clean_up),
    BLOCKED(R.string.blocked_filters),
    BACKUP(R.string.backup_restore),
    ABOUT(R.string.about_yarn),
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
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

    SettingsScaffold(stringResource(R.string.settings), onBack) {
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
                    if (isDefault) stringResource(R.string.hub_default) else stringResource(R.string.hub_not_default),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isDefault) FilledTonalButton(onClick = onSetDefault) { Text(stringResource(R.string.set_short)) }
        }
    }
    item { Spacer(Modifier.height(10.dp)) }
    item {
        SettingsGroup {
            NavRow(Icons.Outlined.Palette, stringResource(R.string.appearance), themeLabel(s.theme), Color(0xFF7C5CFF)) { onOpen(SettingsPage.APPEARANCE) }
            NavRow(Icons.Outlined.Notifications, stringResource(R.string.notifications), null, Color(0xFFF2453D)) { onOpen(SettingsPage.NOTIFICATIONS) }
            NavRow(Icons.Outlined.Sms, stringResource(R.string.messaging), null, Color(0xFF34C759)) { onOpen(SettingsPage.MESSAGING) }
        }
    }
    item { Spacer(Modifier.height(10.dp)) }
    item {
        SettingsGroup {
            NavRow(Icons.Outlined.AutoAwesome, stringResource(R.string.smart_features), if (s.aiEnabled) stringResource(R.string.on) else stringResource(R.string.off), Color(0xFF1466F0)) { onOpen(SettingsPage.AI) }
            NavRow(Icons.Outlined.Lock, stringResource(R.string.privacy_security), null, Color(0xFF0FA3B1)) { onOpen(SettingsPage.PRIVACY) }
            NavRow(Icons.Outlined.CleaningServices, stringResource(R.string.clean_up), if (app.yarn.data.repo.Cleaner.isAutoCleanOn(s)) stringResource(R.string.auto) else null, Color(0xFF00A86B)) { onOpen(SettingsPage.CLEANUP) }
            NavRow(Icons.Outlined.Block, stringResource(R.string.blocked), null, Color(0xFF8E8E93)) { onOpen(SettingsPage.BLOCKED) }
        }
    }
    item { Spacer(Modifier.height(10.dp)) }
    item {
        SettingsGroup {
            NavRow(Icons.Outlined.Backup, stringResource(R.string.backup_restore), null, Color(0xFFFF9500)) { onOpen(SettingsPage.BACKUP) }
            NavRow(Icons.Outlined.Info, stringResource(R.string.about), BuildConfig.VERSION_NAME, Color(0xFF636366)) { onOpen(SettingsPage.ABOUT) }
        }
    }
}

@Composable
private fun themeLabel(t: ThemeMode) = stringResource(
    when (t) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun swipeOptions() = listOf(
    SwipeAction.NONE to stringResource(R.string.swipe_nothing), SwipeAction.ARCHIVE to stringResource(R.string.archive),
    SwipeAction.DELETE to stringResource(R.string.delete), SwipeAction.READ to stringResource(R.string.swipe_read_unread),
    SwipeAction.PIN to stringResource(R.string.pin),
)

@Composable
fun AppearanceScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.appearance), onBack) {
        item { SectionHeader(stringResource(R.string.language)) }
        item {
            SettingsGroup {
                val options = listOf("" to stringResource(R.string.phone_language)) + app.yarn.i18n.AppLanguage.supported.map { it to app.yarn.i18n.AppLanguage.nativeName(it) }
                ChoiceRow(stringResource(R.string.app_language), options, app.yarn.i18n.AppLanguage.selected(context), icon = Icons.Outlined.Language, tint = Color(0xFF1A73E8)) { tag ->
                    (context as? android.app.Activity)?.let { app.yarn.i18n.AppLanguage.select(it, tag) }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.theme)) }
        item {
            SettingsGroup {
                ChoiceRow(stringResource(R.string.theme), listOf(ThemeMode.SYSTEM to stringResource(R.string.system_default), ThemeMode.LIGHT to stringResource(R.string.theme_light), ThemeMode.DARK to stringResource(R.string.theme_dark)), s.theme) { v -> vm.update { it.copy(theme = v) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(stringResource(R.string.wallpaper_colours), stringResource(R.string.wallpaper_colours_sub), s.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.text_size)) }
        item { TextSizeCard(s.textScale) { v -> vm.update { it.copy(textScale = v) } } }
        item { SectionHeader(stringResource(R.string.inbox_gestures)) }
        item {
            SettingsGroup {
                ChoiceRow(stringResource(R.string.swipe_right), swipeOptions(), s.swipeStart) { v -> vm.update { it.copy(swipeStart = v) } }
                ChoiceRow(stringResource(R.string.swipe_left), swipeOptions(), s.swipeEnd) { v -> vm.update { it.copy(swipeEnd = v) } }
            }
        }
    }
}

@Composable
fun NotificationsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.notifications), onBack) {
        item { SectionHeader(stringResource(R.string.content)) }
        item {
            SettingsGroup {
                SwitchRow(stringResource(R.string.show_previews), stringResource(R.string.show_previews_sub), s.showNotificationPreviews) { v -> vm.update { it.copy(showNotificationPreviews = v) } }
            }
        }
        item { SectionHeader(stringResource(R.string.which_notify)) }
        item {
            SettingsGroup {
                SwitchRow(stringResource(R.string.offers_promotions), stringResource(R.string.delivered_silently), s.notifyPromotions) { v -> vm.update { it.copy(notifyPromotions = v) } }
                SwitchRow(stringResource(R.string.suspected_spam), null, s.notifySpam) { v -> vm.update { it.copy(notifySpam = v) } }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            SettingsGroup {
                ClickRow(stringResource(R.string.sounds_vibration), stringResource(R.string.open_android_notif)) {
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
    SettingsScaffold(stringResource(R.string.messaging), onBack) {
        item { SectionHeader(stringResource(R.string.sending)) }
        item {
            SettingsGroup {
                ChoiceRow(stringResource(R.string.undo_send), listOf(0 to stringResource(R.string.off), 3 to pluralStringResource(R.plurals.n_seconds, 3, 3), 5 to pluralStringResource(R.plurals.n_seconds, 5, 5), 10 to pluralStringResource(R.plurals.n_seconds, 10, 10)), s.sendDelaySeconds) { v -> vm.update { it.copy(sendDelaySeconds = v) } }
                SwitchRow(stringResource(R.string.delivery_reports), stringResource(R.string.delivery_reports_sub), s.deliveryReports) { v -> vm.update { it.copy(deliveryReports = v) } }
                SwitchRow(stringResource(R.string.group_messaging), stringResource(R.string.group_messaging_sub), s.groupMms) { v -> vm.update { it.copy(groupMms = v) } }
                ClickRow(stringResource(R.string.exact_alarms), stringResource(R.string.exact_alarms_sub)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.mms_section)) }
        item {
            SettingsGroup {
                SwitchRow(stringResource(R.string.auto_download), stringResource(R.string.auto_download_sub), s.autoDownloadMms) { v -> vm.update { it.copy(autoDownloadMms = v) } }
                SwitchRow(stringResource(R.string.auto_download_roaming), stringResource(R.string.may_cost_extra), s.autoDownloadMmsRoaming, enabled = s.autoDownloadMms) { v -> vm.update { it.copy(autoDownloadMmsRoaming = v) } }
            }
        }
        if (sims.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sim_cards)) }
            item {
                SettingsGroup {
                    val defaultSub = vm.c.sims.defaultSmsSubId()
                    sims.forEach { sim ->
                        SettingsRow(
                            app.yarn.ui.common.simLabel(sim),
                            summary = listOfNotNull(sim.number, if (sim.subId == defaultSub && sims.size > 1) stringResource(R.string.default_for_texts) else null)
                                .joinToString(" · ").ifEmpty { null },
                            leading = { app.yarn.ui.common.SimBadge(sim, height = 24.dp) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.privacy_security), onBack) {
        item { SectionHeader(stringResource(R.string.protection)) }
        item {
            SettingsGroup {
                SwitchRow(stringResource(R.string.app_lock), stringResource(R.string.app_lock_sub), s.appLock) { v -> vm.update { it.copy(appLock = v) } }
                if (s.appLock) {
                    ChoiceRow(stringResource(R.string.lock_after), listOf(0 to stringResource(R.string.immediately), 60 to pluralStringResource(R.plurals.n_minutes, 1, 1), 300 to pluralStringResource(R.plurals.n_minutes, 5, 5), 1800 to pluralStringResource(R.plurals.n_minutes, 30, 30)), s.lockTimeoutSeconds) { v -> vm.update { it.copy(lockTimeoutSeconds = v) } }
                }
                SwitchRow(stringResource(R.string.block_screenshots), stringResource(R.string.block_screenshots_sub), s.secureScreen) { v -> vm.update { it.copy(secureScreen = v) } }
            }
        }
        item {
            Text(
                stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.about), onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                YarnLogo(size = 72.dp)
                Spacer(Modifier.height(4.dp))
                Text("Yarn", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.version_n, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsGroup {
                InfoRow(stringResource(R.string.private_by_design), stringResource(R.string.private_by_design_sub))
                InfoRow(stringResource(R.string.sms_mms), stringResource(R.string.sms_mms_sub))
            }
        }
    }
}

private val TEXT_SIZES = listOf(0.85f to R.string.size_small, 0.92f to R.string.size_compact, 1f to R.string.size_default, 1.1f to R.string.size_large, 1.2f to R.string.size_larger, 1.3f to R.string.size_largest)

/** Slider for Yarn's own text size, with a live preview of an inbox row. */
@Composable
fun TextSizeCard(value: Float, onChange: (Float) -> Unit) {
    val index = TEXT_SIZES.indexOfFirst { kotlin.math.abs(it.first - value) < 0.01f }.takeIf { it >= 0 } ?: 2
    var pos by androidx.compose.runtime.remember(index) { androidx.compose.runtime.mutableFloatStateOf(index.toFloat()) }
    val current = TEXT_SIZES[pos.toInt().coerceIn(0, TEXT_SIZES.lastIndex)]
    val base = androidx.compose.ui.platform.LocalDensity.current
    val sizeName = stringResource(current.second)
    val sizeDesc = stringResource(R.string.text_size_desc, sizeName)
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
                app.yarn.ui.common.Avatar(stringResource(R.string.preview_name), null, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.preview_name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.preview_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    .androidxSemantics(sizeDesc),
            )
            Text("A", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            sizeName + if (current.first == 1f) " · " + stringResource(R.string.same_as_phone) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

private fun Modifier.androidxSemantics(label: String) = this.then(
    androidx.compose.ui.Modifier.semantics { contentDescription = label },
)
