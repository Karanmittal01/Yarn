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
import app.yarn.data.db.ConversationEntity
import app.yarn.data.prefs.ThemeMode
import app.yarn.data.repo.InboxGroup
import app.yarn.ui.inbox.ConversationRow
import app.yarn.ui.inbox.InboxItem
import app.yarn.ui.settings.NavRow
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

    private fun render(dark: Boolean, name: String) {
        compose.setContent {
            YarnTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
                    ConversationRow(InboxItem(conv(1, "Priya Sharma", "Are we still on for dinner tonight?", "PERSONAL", 2, "+919812345678", pinned = true), null, false, InboxGroup.PERSONAL, null), false, false, {}, {})
                    ConversationRow(InboxItem(conv(2, "SBI", "123190 is OTP for online purchase of Rs. 50000.00 at DREAMPLUG", "OTP", 1, "AD-SBIOTP-S"), null, true, InboxGroup.TRANSACTIONS, "123190"), false, false, {}, {})
                    ConversationRow(InboxItem(conv(3, "HDFC Bank", "Amt Deducted! Rs.35000 from your HDFC Bank A/c XX2626 for NEFT txn", "BANKING", 0, "AD-HDFCBK-S"), null, true, InboxGroup.TRANSACTIONS, null), true, false, {}, {})
                    ConversationRow(InboxItem(conv(4, "Amazon", "Your package will be delivered today by 9 PM", "DELIVERY", 0, "AX-AMAZON-S"), null, true, InboxGroup.UPDATES, null), false, false, {}, {})
                    SettingsGroup {
                        NavRow(androidx.compose.material.icons.Icons.Outlined.Palette, "Appearance", "System theme · swipe gestures", androidx.compose.ui.graphics.Color(0xFF7C4DFF)) {}
                        NavRow(androidx.compose.material.icons.Icons.Outlined.Palette, "Notifications", "Previews on", androidx.compose.ui.graphics.Color(0xFFE5533D)) {}
                    }
                }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/ui-snapshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun light() = render(false, "inbox_light")
    @Test fun dark() = render(true, "inbox_dark")
}
