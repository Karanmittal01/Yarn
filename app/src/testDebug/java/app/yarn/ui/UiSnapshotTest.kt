package app.yarn.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.layout.size
import app.yarn.data.db.ConversationEntity
import app.yarn.data.prefs.ThemeMode
import app.yarn.data.repo.InboxGroup
import app.yarn.ui.inbox.ConversationRow
import app.yarn.ui.inbox.InboxItem
import app.yarn.ui.settings.NavRow
import app.yarn.ui.settings.settingsHubItems
import app.yarn.ui.settings.smartFeatureItems
import app.yarn.ui.settings.backupItems
import app.yarn.ui.settings.SettingsGroup
import app.yarn.ui.theme.YarnTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders key UI pieces to PNGs in build/ui-snapshots for visual review (no device needed). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class UiSnapshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val now = System.currentTimeMillis()
    private fun conv(id: Long, title: String, snippet: String, cat: String, unread: Int, addr: String, pinned: Boolean = false) =
        ConversationEntity(id = id, addresses = listOf(addr), displayName = title, snippet = snippet, category = cat, unreadCount = unread, lastMessageAt = now - id * 600_000, messageCount = 3, pinned = pinned)

    private fun item(id: Long, title: String, snippet: String, cat: String, unread: Int, addr: String, business: Boolean, group: InboxGroup, otp: String? = null, pinned: Boolean = false) =
        InboxItem(conv(id, title, snippet, cat, unread, addr, pinned), null, business, group, otp)

    private val inbox = listOf(
        item(1, "Priya Sharma", "Are we still on for dinner tonight?", "PERSONAL", 2, "+919812345678", false, InboxGroup.PERSONAL, pinned = true),
        item(2, "SBI Card", "423378 is the OTP for Trxn. of INR 375.00 at AMAZON with your SBI Card", "OTP", 1, "AD-SBICRD-T", true, InboxGroup.OTP, "423378"),
        item(3, "HDFC Bank", "Rs.2,500.00 debited from A/c XX1234 to VPA swiggy@icici", "BANKING", 1, "VM-HDFCBK-S", true, InboxGroup.TRANSACTIONS),
        item(4, "Zepto", "Thank you for ordering from Zepto. Your order is about to be delivered.", "SHOPPING", 0, "JM-ZEPTON-S", true, InboxGroup.SHOPPING),
        item(5, "Swiggy", "Your Swiggy order from Behrouz Biryani is on the way!", "SHOPPING", 0, "AD-SWIGGY-S", true, InboxGroup.SHOPPING),
        item(6, "Mom", "Call me when you're free", "PERSONAL", 0, "+919800000002", false, InboxGroup.PERSONAL),
        item(7, "Zepto", "Sale is LIVE! Get flat 50% off on fruits & veggies. Order now", "PROMOTIONS", 0, "JM-ZEPTON-P", true, InboxGroup.OFFERS),
        item(8, "IRCTC", "PNR 4521367890: Train 12951 Coach B2 Berth 34 confirmed.", "TRAVEL", 0, "AD-IRCTCI-S", true, InboxGroup.UPDATES),
        item(9, "CDSL", "Credit in a/c *89040843 through IPO/FPO for 107 shares", "BANKING", 0, "JX-CDSLTX-S", true, InboxGroup.TRANSACTIONS),
    )
    private val chips = listOf(app.yarn.ui.inbox.InboxChip("All", app.yarn.data.repo.InboxFilter(), unread = 4)) +
        listOf(InboxGroup.PERSONAL to 2, InboxGroup.OTP to 1, InboxGroup.TRANSACTIONS to 1, InboxGroup.SHOPPING to 0, InboxGroup.UPDATES to 0, InboxGroup.OFFERS to 0)
            .map { (g, n) -> app.yarn.ui.inbox.InboxChip(g.label, g.filter, g, n) }

    private fun shot(dark: Boolean, name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { YarnTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/ui-snapshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @androidx.compose.runtime.Composable
    private fun InboxMock() {
        val header = app.yarn.ui.inbox.inboxHeaderColor()
        androidx.compose.material3.Scaffold(
            containerColor = header,
            topBar = {
                Column(Modifier.background(header)) {
                    app.yarn.ui.inbox.InboxHeader("Messages", {}, 0, true, {}, {})
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) { items(chips.size) { i -> app.yarn.ui.inbox.GroupChip(chips[i], selected = i == 0) {} } }
                }
            },
            floatingActionButton = {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = {}, icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Edit, null, Modifier.size(22.dp)) },
                    text = { androidx.compose.material3.Text("Start chat", style = MaterialTheme.typography.titleMedium) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                )
            },
        ) { p ->
            androidx.compose.foundation.layout.Box(
                Modifier.padding(top = p.calculateTopPadding()).fillMaxSize()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp)) {
                    items(inbox.size) { i ->
                        app.yarn.ui.inbox.SwipeableRow(true, app.yarn.data.prefs.SwipeAction.ARCHIVE, app.yarn.data.prefs.SwipeAction.DELETE, false, {}) {
                            ConversationRow(inbox[i], false, false, {}, {})
                        }
                    }
                    item { app.yarn.ui.inbox.InboxCount(inbox) }
                }
            }
        }
    }

    @Test fun inboxLight() = shot(false, "1_inbox_light") { InboxMock() }
    @Test fun inboxDark() = shot(true, "2_inbox_dark") { InboxMock() }
    @Test fun settingsLight() = shot(false, "3_settings_light") {
        app.yarn.ui.settings.SettingsScaffold("Settings", {}) { settingsHubItems(app.yarn.data.prefs.AppSettings(), true, {}, {}) }
    }
    @Test fun settingsDark() = shot(true, "4_settings_dark") {
        app.yarn.ui.settings.SettingsScaffold("Settings", {}) { settingsHubItems(app.yarn.data.prefs.AppSettings(), true, {}, {}) }
    }
    @Test fun smartLight() = shot(false, "5_smart_features_light") {
        app.yarn.ui.settings.SettingsScaffold("Smart features", {}) { smartFeatureItems(app.yarn.data.prefs.AppSettings(), {}, {}, {}) }
    }
    @Test fun selectionLight() = shot(false, "6_selected_row_light") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 24.dp)) {
            inbox.take(4).forEachIndexed { i, it -> ConversationRow(it, i == 3, false, {}, {}) }
        }
    }
    @Test fun backupSignedOut() = shot(false, "7_backup_signed_out") {
        app.yarn.ui.settings.SettingsScaffold("Backup", {}) {
            backupItems(app.yarn.data.prefs.AppSettings(), null, app.yarn.backup.BackupState.Idle, false, null, false, {}, {}, {}, {}, {}, {})
        }
    }
    @Test fun backupSignedIn() = shot(true, "8_backup_signed_in_dark") {
        app.yarn.ui.settings.SettingsScaffold("Backup", {}) {
            backupItems(
                app.yarn.data.prefs.AppSettings(googleAccount = "you@gmail.com", lastBackupAt = now - 3_600_000, lastBackupBytes = 48_000_000),
                null, app.yarn.backup.BackupState.Done("Backed up 12,306 messages to Google Drive"), false, null, false, {}, {}, {}, {}, {}, {},
            )
        }
    }
    private fun animatedShot(dark: Boolean, name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        // Infinite animations never go idle, so drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { YarnTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() } }
        compose.mainClock.advanceTimeBy(1_500)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/ui-snapshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun defaultPromptLight() = animatedShot(false, "9_default_prompt_light") { app.yarn.ui.inbox.DefaultAppPromptContent({}, {}) }
    @Test fun defaultPromptDark() = animatedShot(true, "10_default_prompt_dark") { app.yarn.ui.inbox.DefaultAppPromptContent({}, {}) }
}
