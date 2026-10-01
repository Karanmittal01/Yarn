package app.yarn.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
        item(2, "SBI", "123190 is OTP for online purchase of Rs. 50000.00 at DREAMPLUG", "OTP", 1, "AD-SBIOTP-S", true, InboxGroup.TRANSACTIONS, "123190"),
        item(3, "Rahul", "Sent you the photos from Goa 📸", "PERSONAL", 0, "+919800000001", false, InboxGroup.PERSONAL),
        item(4, "HDFC Bank", "Amt Deducted! Rs.35000 from your HDFC Bank A/c XX2626 for NEFT txn", "BANKING", 0, "AD-HDFCBK-S", true, InboxGroup.TRANSACTIONS),
        item(5, "Amazon", "Your package will be delivered today by 9 PM", "DELIVERY", 0, "AX-AMAZON-S", true, InboxGroup.UPDATES),
        item(6, "Mom", "Call me when you're free", "PERSONAL", 0, "+919800000002", false, InboxGroup.PERSONAL),
        item(7, "Zepto", "Flat 50% off on your next 3 orders. Use code ZEP50", "PROMOTIONS", 0, "JM-ZEPTON-S", true, InboxGroup.OFFERS),
        item(8, "Jio", "Your plan expires in 2 days. Recharge now to continue enjoying benefits.", "UPDATES", 0, "JK-JIOINF-S", true, InboxGroup.UPDATES),
        item(9, "CDSL", "Credit in a/c *89040843 through IPO/FPO for 107 shares", "BANKING", 0, "JX-CDSLTX-S", true, InboxGroup.TRANSACTIONS),
    )
    private val chips = listOf(
        app.yarn.ui.inbox.InboxChip("All", app.yarn.data.repo.InboxFilter(), unread = 3),
        app.yarn.ui.inbox.InboxChip("Personal", app.yarn.data.repo.InboxFilter(categories = setOf(app.yarn.intelligence.Category.PERSONAL)), unread = 2),
        app.yarn.ui.inbox.InboxChip("Transactions", app.yarn.data.repo.InboxFilter(categories = setOf(app.yarn.intelligence.Category.BANKING)), unread = 1),
        app.yarn.ui.inbox.InboxChip("Updates", app.yarn.data.repo.InboxFilter(categories = setOf(app.yarn.intelligence.Category.UPDATES))),
        app.yarn.ui.inbox.InboxChip("Offers", app.yarn.data.repo.InboxFilter(categories = setOf(app.yarn.intelligence.Category.PROMOTIONS))),
    )

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
        androidx.compose.material3.Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = { app.yarn.ui.inbox.InboxHeader("Messages", {}, 0, true, {}, {}) },
            floatingActionButton = {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = {}, icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Edit, null, Modifier.size(20.dp)) },
                    text = { androidx.compose.material3.Text("Start chat", style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                )
            },
        ) { p ->
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = p) {
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) { items(chips.size) { i -> app.yarn.ui.inbox.GroupChip(chips[i], selected = i == 0) {} } }
                }
                items(inbox.size) { i ->
                    app.yarn.ui.inbox.SwipeableRow(true, app.yarn.data.prefs.SwipeAction.ARCHIVE, app.yarn.data.prefs.SwipeAction.DELETE, false, {}) {
                        ConversationRow(inbox[i], false, false, {}, {})
                    }
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
}
