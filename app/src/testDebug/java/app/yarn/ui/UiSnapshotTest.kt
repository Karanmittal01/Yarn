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
        InboxItem(conv(id, title, snippet, cat, unread, addr, pinned), null, business, group, otp, codeAt = if (otp != null) now - id * 600_000 else 0)

    private val inbox = listOf(
        item(1, "Karan Mittal", "Are we still on for dinner tonight?", "PERSONAL", 2, "+919812345678", false, InboxGroup.PERSONAL, pinned = true),
        item(2, "SBI Card", "423378 is the OTP for Trxn. of INR 375.00 at AMAZON with your SBI Card", "OTP", 1, "AD-SBICRD-T", true, InboxGroup.OTP, "423378"),
        item(3, "HDFC Bank", "Rs.2,500.00 debited from A/c XX1234 to VPA swiggy@icici", "BANKING", 1, "VM-HDFCBK-S", true, InboxGroup.TRANSACTIONS),
        item(4, "Zepto", "Thank you for ordering from Zepto. Your order is about to be delivered.", "SHOPPING", 0, "JM-ZEPTON-S", true, InboxGroup.SHOPPING),
        item(5, "Swiggy", "Your Swiggy order from Behrouz Biryani is on the way!", "SHOPPING", 0, "AD-SWIGGY-S", true, InboxGroup.SHOPPING),
        item(6, "Mom", "Call me when you're free", "PERSONAL", 0, "+919800000002", false, InboxGroup.PERSONAL),
        item(7, "Zepto", "Sale is LIVE! Get flat 50% off on fruits & veggies. Order now", "PROMOTIONS", 0, "JM-ZEPTON-P", true, InboxGroup.OFFERS),
        item(8, "IRCTC", "PNR 4521367890: Train 12951 Coach B2 Berth 34 confirmed.", "TRAVEL", 0, "AD-IRCTCI-S", true, InboxGroup.TRAVEL),
        item(9, "CDSL", "Credit in a/c *89040843 through IPO/FPO for 107 shares", "BANKING", 0, "JX-CDSLTX-S", true, InboxGroup.TRANSACTIONS),
    )
    private val chips = listOf(app.yarn.ui.inbox.InboxChip(app.yarn.R.string.chip_all, app.yarn.data.repo.InboxFilter(), unread = 4)) +
        listOf(InboxGroup.PERSONAL to 2, InboxGroup.OTP to 1, InboxGroup.TRANSACTIONS to 1, InboxGroup.SHOPPING to 0, InboxGroup.TRAVEL to 1, InboxGroup.UPDATES to 0, InboxGroup.OFFERS to 0)
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
                    app.yarn.ui.inbox.InboxHeader(androidx.compose.ui.res.stringResource(app.yarn.R.string.messages), {}, 0, true, {}, {})
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) { items(chips.size) { i -> app.yarn.ui.inbox.GroupChip(chips[i], selected = i == 0) {} } }
                }
            },
            floatingActionButton = {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = {}, icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Edit, null, Modifier.size(22.dp)) },
                    text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(app.yarn.R.string.start_chat), style = MaterialTheme.typography.titleMedium) },
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

    private val otpTab = listOf(
        item(1, "RMATIC", "09 Oct 20:00 Logout Check in successful. Please use OTP 9896 to check out from the cab.", "TRAVEL", 1, "JX-RMATIC-S", true, InboxGroup.TRAVEL, "9896"),
        item(2, "Blue Dart", "Your Blue Dart Secure Delivery Code is 692079 and its valid for next 30 minutes.", "OTP", 0, "BZ-BLUDRT-S", true, InboxGroup.OTP, "692079"),
        item(6, "YEIDAP", "Your one time password for Allottee Login to YEIDA citizen charter portal is 486731.", "OTP", 0, "VM-YEIDAP-S", true, InboxGroup.OTP, "486731"),
        item(9, "RMATIC", "Pickup Details for 09 Oct 13:30 ETA:11:37 Vehicle No HR55AB1234", "TRAVEL", 0, "JX-RMATIC-T", true, InboxGroup.TRAVEL, "1992"),
        item(14, "PNB", "101073 is your OTP for purchase of INR 1,499.00 at FLIPKART", "OTP", 0, "AD-PNBSMS-S", true, InboxGroup.OTP, "101073"),
    )

    @Test fun inboxCodes() = shot(true, "33_inbox_codes_dark") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 12.dp)) {
            otpTab.forEach { ConversationRow(it, false, false, {}, {}) }
        }
    }
    @Test fun inboxCodesLight() = shot(false, "34_inbox_codes_light") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 12.dp)) {
            otpTab.forEach { ConversationRow(it, false, false, {}, {}) }
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
    private fun newChat(query: String) = shot(true, if (query.isEmpty()) "11_new_chat_empty" else "12_new_chat_typing") {
        app.yarn.ui.newchat.NewConversationContent(
            query = query, recipients = emptyList(),
            results = if (query.isEmpty()) emptyList() else listOf(
                app.yarn.data.contacts.ContactPhone(1, "Rahul Verma", "+91 98187 16240", "Mobile", null, false),
                app.yarn.data.contacts.ContactPhone(2, "Rahul Office", "+91 99100 22334", "Work", null, false),
            ),
            hasContacts = true, onQuery = {}, onAdd = { _, _ -> }, onRemove = {}, onStart = {}, onRequestContacts = {}, onBack = {}, autoFocus = false,
        )
    }

    @Test fun newChatEmpty() = newChat("")
    @Test fun newChatTyping() = newChat("9818716240")
    @Test fun inboxDarkLargeText() {
        // Like a phone set to a bigger font size.
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        shot(true, "13_inbox_dark_large_text") { InboxMock() }
    }
    @Test fun textSizeSetting() = shot(true, "14_text_size_setting") {
        app.yarn.ui.settings.SettingsScaffold("Appearance", {}) {
            item { app.yarn.ui.settings.SectionHeader("Text size") }
            item { app.yarn.ui.settings.TextSizeCard(1.1f) {} }
        }
    }

    // ---- dual SIM, translation, clean-up, onboarding ---------------------------------------

    private val jio = app.yarn.telephony.SimInfo(1, 0, "Jio 4G", "Jio", "+91 98187 16240", 0)
    private val airtel = app.yarn.telephony.SimInfo(2, 1, "airtel", "airtel", "+91 99100 22334", 0)

    private fun msg(id: Long, body: String, outgoing: Boolean, sub: Int, minutesAgo: Long, status: Int = app.yarn.data.db.MessageStatus.RECEIVED) =
        app.yarn.data.db.MessageWithAttachments(
            app.yarn.data.db.MessageEntity(id = id, conversationId = 1, address = "+919812345678", body = body, date = now - minutesAgo * 60_000, outgoing = outgoing, status = status, subId = sub),
            emptyList(), emptyList(),
        )

    @androidx.compose.runtime.Composable
    private fun ChatMock(translated: Boolean, hindiUi: Boolean = false) {
        val noop = app.yarn.ui.conversation.BubbleCallbacks({}, {}, {}, {}, {}, { _, _ -> })
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 32.dp)) {
            app.yarn.ui.conversation.MessageBubble(msg(1, "Kal shaam ko milte hain? Movie dekhne chalenge", false, 1, 50), null, false, false, null, true, noop, sim = jio)
            app.yarn.ui.conversation.MessageBubble(msg(2, "Haan pakka! 7 baje", true, 2, 45, app.yarn.data.db.MessageStatus.DELIVERED), null, false, false, null, true, noop, sim = airtel)
            if (hindiUi) app.yarn.ui.conversation.MessageBubble(
                msg(3, "Rs.2,500 has been debited from your account. If not done by you, call the bank immediately.", false, 1, 30), null, false, false,
                null, true, noop, translateTo = "hi", sim = jio,
            ) else app.yarn.ui.conversation.MessageBubble(
                msg(3, "आपके खाते से ₹2,500 की निकासी हुई है। यदि यह आपने नहीं किया है तो तुरंत बैंक से संपर्क करें।", false, 1, 30), null, false, false,
                if (translated) app.yarn.ui.conversation.TranslationUi.Done("₹2,500 has been withdrawn from your account. If you didn't do this, contact the bank immediately.", "hi") else null,
                true, noop, translateTo = "en", sim = jio,
            )
            app.yarn.ui.conversation.MessageBubble(
                msg(4, "Votre colis sera livré demain entre 9h et 12h.", false, 2, 10), null, false, false,
                app.yarn.ui.conversation.TranslationUi.NeedsDownload("fr"), true, noop, translateTo = "en", sim = airtel,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            app.yarn.ui.conversation.Composer(
                text = "", onText = {}, attachments = emptyList(), onAddAttachments = {}, onRemoveAttachment = {},
                sims = listOf(jio, airtel), subId = 2, onSim = {}, smartReplies = listOf("See you then!", "👍"), onSmartReply = {},
                onSend = {}, onSchedule = {}, onRewrite = {}, isGroupMms = false,
            )
        }
    }

    @Test fun chatDualSimLight() = shot(false, "15_chat_dual_sim_translate_light") { ChatMock(translated = false) }
    @Test fun chatDualSimDark() = shot(true, "16_chat_dual_sim_translated_dark") { ChatMock(translated = true) }

    @Test fun inboxDualSim() = shot(false, "17_inbox_dual_sim_clean_banner") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 24.dp)) {
            app.yarn.ui.inbox.CleanBanners(report = 0, junk = 1284, onClean = {}, onDismissHint = {}, onDismissReport = {})
            inbox.take(6).forEachIndexed { i, it -> ConversationRow(it.copy(sim = if (i % 2 == 0) jio else airtel), false, false, {}, {}) }
        }
    }

    private val found = mapOf(
        app.yarn.data.repo.JunkKind.CODES to 842, app.yarn.data.repo.JunkKind.OFFERS to 1210,
        app.yarn.data.repo.JunkKind.SPAM to 37, app.yarn.data.repo.JunkKind.ORDERS to 156,
    )

    @Test fun cleanerLight() = shot(false, "18_cleaner_light") {
        app.yarn.ui.cleaner.CleanerContent(app.yarn.ui.cleaner.CleanerState(scanning = false, found = found), app.yarn.ui.cleaner.AutoClean(10, 30, 7), true, false, true, {}, {}, {}, {}, {}, {})
    }
    @Test fun cleanerDoneDark() = shot(true, "19_cleaner_done_dark") {
        app.yarn.ui.cleaner.CleanerContent(
            app.yarn.ui.cleaner.CleanerState(scanning = false, found = found.mapValues { 0 }, cleaned = 2089),
            app.yarn.ui.cleaner.AutoClean(10, 30, 30), false, true, true, {}, {}, {}, {}, {}, {},
        )
    }

    private fun onboarding(step: Int, dark: Boolean, name: String) = shot(dark, name) {
        app.yarn.ui.onboarding.OnboardingContent(step, false, "", {}, app.yarn.ui.onboarding.AutoCleanChoice(), {}, {}, {}, {}, {})
    }

    @Test fun onboardingWelcome() = onboarding(0, false, "20_onboarding_welcome")
    @Test fun onboardingClean() = onboarding(3, true, "21_onboarding_auto_clean_dark")

    // ---- Hindi ---------------------------------------------------------------------------

    @Test @Config(qualifiers = "hi-w411dp-h891dp-xxhdpi") fun hindiInbox() = shot(false, "22_hi_inbox") { InboxMock() }
    @Test @Config(qualifiers = "hi-w411dp-h891dp-xxhdpi") fun hindiChat() = shot(true, "23_hi_chat_dark") { ChatMock(translated = false, hindiUi = true) }
    @Test @Config(qualifiers = "hi-w411dp-h891dp-xxhdpi") fun hindiSettings() = shot(false, "24_hi_settings") {
        app.yarn.ui.settings.SettingsScaffold(androidx.compose.ui.res.stringResource(app.yarn.R.string.settings), {}) { settingsHubItems(app.yarn.data.prefs.AppSettings(), true, {}, {}) }
    }
    @Test @Config(qualifiers = "hi-w411dp-h891dp-xxhdpi") fun hindiCleaner() = shot(false, "25_hi_cleaner") {
        app.yarn.ui.cleaner.CleanerContent(app.yarn.ui.cleaner.CleanerState(scanning = false, found = found), app.yarn.ui.cleaner.AutoClean(10, 30, 7), true, false, true, {}, {}, {}, {}, {}, {})
    }
    @Test @Config(qualifiers = "hi-w411dp-h891dp-xxhdpi") fun hindiOnboarding() = shot(false, "26_hi_onboarding") {
        app.yarn.ui.onboarding.OnboardingContent(0, false, "hi", {}, app.yarn.ui.onboarding.AutoCleanChoice(), {}, {}, {}, {}, {})
    }

    // ---- Clean up with Apply, re-sort, progress bars ---------------------------------------------
    @Test @Config(qualifiers = "w411dp-h1350dp-xxhdpi") fun cleanerUnsaved() = shot(true, "27_cleaner_unsaved_dark") {
        app.yarn.ui.cleaner.CleanerContent(app.yarn.ui.cleaner.CleanerState(scanning = false, found = found), app.yarn.ui.cleaner.AutoClean(10, 7, 7), true, false, true, {}, {}, {}, {}, {}, {})
    }
    @Test fun cleanerCleaning() = shot(false, "28_cleaner_cleaning") {
        app.yarn.ui.cleaner.CleanerContent(app.yarn.ui.cleaner.CleanerState(scanning = false, found = found, cleaning = true, done = 1240, total = 2089), app.yarn.ui.cleaner.AutoClean(10, 30, 30), false, true, true, {}, {}, {}, {}, {}, {})
    }
    @Test fun smartResort() = shot(true, "29_smart_features_resorting_dark") {
        app.yarn.ui.settings.SettingsScaffold("Smart features", {}) {
            smartFeatureItems(app.yarn.data.prefs.AppSettings(), {}, {}, {}, job = app.yarn.work.JobProgress(app.yarn.work.JobKind.RESORTING, 3120, 8450))
        }
    }
    @Test fun smartResortIdle() = shot(false, "30_smart_features_resort_button") {
        app.yarn.ui.settings.SettingsScaffold("Smart features", {}) { smartFeatureItems(app.yarn.data.prefs.AppSettings(), {}, {}, {}) }
    }
    @Test fun inboxProgress() = shot(false, "31_inbox_progress_bars") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 24.dp)) {
            app.yarn.ui.inbox.WorkBanners(app.yarn.work.JobProgress(app.yarn.work.JobKind.RESORTING, 3120, 8450), app.yarn.backup.BackupState.Running("Uploading to Google Drive", 45, 100, percent = true), importing = false)
            inbox.take(4).forEach { ConversationRow(it, false, false, {}, {}) }
        }
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Test fun travelTab() = shot(true, "32_travel_tab_dark") {
        val travel = listOf(
            item(21, "IRCTC", "PNR 4521367890: Train 12951 Coach B2 Berth 34 confirmed. Dep 16:55", "TRAVEL", 1, "AD-IRCTCI-S", true, InboxGroup.TRAVEL),
            item(22, "IndiGo", "Web check-in is open for your flight 6E 2134 DEL-BOM on 06 Oct.", "TRAVEL", 0, "JM-INDIGO-S", true, InboxGroup.TRAVEL),
            item(23, "Uber", "Your Uber driver Sunil has arrived. Look for a grey WagonR DL1CA1234.", "TRAVEL", 0, "VM-UBERIN-S", true, InboxGroup.TRAVEL),
            item(24, "MakeMyTrip", "Your hotel booking at Taj Fort Aguada is confirmed for 10-12 Oct.", "TRAVEL", 0, "VK-MKMTRP-S", true, InboxGroup.TRAVEL),
        )
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 24.dp)) {
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) { chips.forEach { c -> app.yarn.ui.inbox.GroupChip(c, selected = c.group == InboxGroup.TRAVEL) {} } }
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
            travel.forEach { ConversationRow(it, false, false, {}, {}) }
        }
    }
}
