package app.yarn.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.size
import app.yarn.data.db.ConversationEntity
import app.yarn.data.prefs.ThemeMode
import app.yarn.data.repo.InboxGroup
import app.yarn.ui.inbox.ConversationRow
import app.yarn.ui.inbox.InboxItem
import app.yarn.ui.settings.NavRow
import app.yarn.ui.settings.settingsHubItems
import app.yarn.ui.settings.smartFeatureItems
import app.yarn.ui.settings.notificationItems
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
            .map { (g, n) -> app.yarn.ui.inbox.InboxChip(g.label, g.filter, g, n) } +
        app.yarn.ui.inbox.InboxChip(app.yarn.R.string.verdict_spam, app.yarn.data.repo.InboxFilter(app.yarn.data.repo.InboxView.SPAM), unread = 3)

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
    private fun InboxMock(rows: List<InboxItem> = inbox, me: app.yarn.data.contacts.ContactInfo? = karan) {
        val header = app.yarn.ui.inbox.inboxHeaderColor()
        androidx.compose.material3.Scaffold(
            containerColor = header,
            topBar = {
                Column(Modifier.background(header)) {
                    app.yarn.ui.inbox.InboxHeader(androidx.compose.ui.res.stringResource(app.yarn.R.string.app_name), {}, me = me, onProfile = {})
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
                    items(rows.size) { i ->
                        app.yarn.ui.inbox.SwipeableRow(true, app.yarn.data.prefs.SwipeAction.ARCHIVE, app.yarn.data.prefs.SwipeAction.DELETE, false, {}) {
                            ConversationRow(rows[i], false, false, {}, {})
                        }
                    }
                    item { app.yarn.ui.inbox.InboxCount(rows) }
                }
            }
        }
    }

    // The OTP tab lists only codes; RMATIC's cab chat is here for its codes and in Travel for its pickups.
    private val otpTab = listOf(
        item(1, "RMATIC", "09 Oct 20:00 Logout Check in successful. Please use OTP 9896 to check out from the cab.", "OTP", 1, "JX-RMATIC-S", true, InboxGroup.OTP, "9896"),
        item(2, "Blue Dart", "Your Blue Dart Secure Delivery Code is 692079 and its valid for next 30 minutes.", "OTP", 0, "BZ-BLUDRT-S", true, InboxGroup.OTP, "692079"),
        item(6, "YEIDAP", "Your one time password for Allottee Login to YEIDA citizen charter portal is 486731.", "OTP", 0, "VM-YEIDAP-S", true, InboxGroup.OTP, "486731"),
        item(9, "Equitas Bank", "OTP 558210 for reward redemption of Amazon Pay E-Gift Card through Equitas Xchange.", "OTP", 0, "VM-EQUTAS-S", true, InboxGroup.OTP, "558210"),
        item(14, "PNB", "101073 is your OTP for purchase of INR 1,499.00 at FLIPKART", "OTP", 0, "AD-PNBSMS-S", true, InboxGroup.OTP, "101073"),
    )
    private val travelTab = listOf(
        item(3, "RMATIC", "Pickup Details for 09 Oct 13:30 ETA:11:37 Vehicle No HR55AB1234, Driver Ramesh", "TRAVEL", 0, "JX-RMATIC-S", true, InboxGroup.TRAVEL),
        item(8, "IRCTC", "PNR 4521367890: Train 12951 Coach B2 Berth 34 confirmed.", "TRAVEL", 0, "AD-IRCTCI-S", true, InboxGroup.TRAVEL),
        item(20, "Uber", "Your Uber trip receipt for Oct 8: Rs 312.40", "TRAVEL", 0, "VM-UBERIN-S", true, InboxGroup.TRAVEL),
    )

    @androidx.compose.runtime.Composable
    private fun TabMock(rows: List<InboxItem>, selected: InboxGroup, me: app.yarn.data.contacts.ContactInfo? = karan) {
        val header = app.yarn.ui.inbox.inboxHeaderColor()
        Column(Modifier.fillMaxSize().background(header)) {
            app.yarn.ui.inbox.InboxHeader(androidx.compose.ui.res.stringResource(app.yarn.R.string.app_name), {}, me = me, onProfile = {})
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = chips.indexOfFirst { it.group == selected }.coerceAtLeast(1) - 1),
            ) { items(chips.size) { i -> app.yarn.ui.inbox.GroupChip(chips[i], selected = chips[i].group == selected) {} } }
            Column(
                Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(top = 10.dp),
            ) { rows.forEach { ConversationRow(it, false, false, {}, {}) } }
        }
    }

    @Test fun otpTabDark() = shot(true, "33_otp_tab_only_codes_dark") { TabMock(otpTab, InboxGroup.OTP) }
    @Test fun travelTabCab() = shot(true, "34_travel_tab_same_cab_chat_dark") { TabMock(travelTab, InboxGroup.TRAVEL) }
    @Test fun otpTabLight() = shot(false, "35_otp_tab_only_codes_light") { TabMock(otpTab, InboxGroup.OTP) }
    @Test fun chatFromOtpTab() = shot(true, "36_chat_opened_from_otp_dark") {
        val noop = app.yarn.ui.conversation.BubbleCallbacks({}, {}, {}, {}, {}, { _, _ -> })
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 32.dp)) {
            app.yarn.ui.conversation.SectionBar(InboxGroup.OTP) {}
            app.yarn.ui.conversation.MessageBubble(msg(1, "09 Oct 09:58 Login Check in successful. Please use OTP 1992 to check in to the cab.", false, 1, 700), null, false, false, null, false, noop)
            app.yarn.ui.conversation.MessageBubble(msg(2, "09 Oct 20:00 Logout Check in successful. Please use OTP 9896 to check out from the cab.", false, 1, 20), null, false, false, null, false, noop)
        }
    }

    @Test fun spamTab() = shot(true, "44_spam_tab_dark") { SpamMock(karan) }

    @androidx.compose.runtime.Composable
    private fun SpamMock(me: app.yarn.data.contacts.ContactInfo?) {
        val header = app.yarn.ui.inbox.inboxHeaderColor()
        val spamRows = listOf(
            item(1, "AX-RMGAME", "Congratulations! You won Rs 25,000 cash prize. Claim now at bit.ly/claim-win before it expires", "SPAM", 1, "AX-RMGAME-P", true, InboxGroup.OFFERS),
            item(3, "+91 70123 45678", "Your electricity will be disconnected tonight at 9:30 pm. Call officer 7012345678 immediately", "SPAM", 1, "+917012345678", false, InboxGroup.PERSONAL),
            item(6, "VK-LOANPR", "Pre-approved loan of Rs 5 lakh in 5 minutes! No documents. Apply: loanfast.in", "SPAM", 1, "VK-LOANPR-P", true, InboxGroup.OFFERS),
            item(9, "+91 81234 56789", "Your parcel is on hold due to an incomplete address. Pay Rs 25 within 12 hours at parcel-redeliver.in", "SPAM", 0, "+918123456789", false, InboxGroup.PERSONAL),
            item(12, "VM-KYCUPD", "Dear customer, your bank account KYC expires today and the account will be blocked. Update now: kyc-verify.in", "SPAM", 0, "VM-KYCUPD-S", true, InboxGroup.UPDATES),
        )
        Column(Modifier.fillMaxSize().background(header)) {
            app.yarn.ui.inbox.InboxHeader(androidx.compose.ui.res.stringResource(app.yarn.R.string.app_name), {}, me = me, onProfile = {})
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = chips.size - 3),
            ) { items(chips.size) { i -> app.yarn.ui.inbox.GroupChip(chips[i], selected = i == chips.size - 1) {} } }
            Column(
                Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(top = 10.dp),
            ) { spamRows.forEach { ConversationRow(it, false, false, {}, {}) } }
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
    private val myNotify = app.yarn.data.prefs.AppSettings(
        notifyTabs = app.yarn.data.prefs.DEFAULT_NOTIFY + (app.yarn.intelligence.CategoryGroup.OFFERS to app.yarn.data.prefs.NotifyMode.SILENT),
    )
    @Test fun notificationsDark() = shot(true, "37_notifications_per_tab_dark") {
        app.yarn.ui.settings.SettingsScaffold("Notifications", {}) { notificationItems(myNotify, {}, {}) }
    }
    @Test @Config(qualifiers = "hi-w411dp-h891dp-xxhdpi") fun notificationsHindi() = shot(false, "38_notifications_per_tab_hindi") {
        app.yarn.ui.settings.SettingsScaffold("सूचनाएं", {}) { notificationItems(myNotify, {}, {}) }
    }
    private fun binned(id: Long, title: String, address: String, body: String, hoursAgo: Long, outgoing: Boolean = false, photo: Boolean = false) =
        app.yarn.data.db.BinEntity(
            id = id, conversationId = id, addresses = listOf(address), title = title, kind = if (photo) 1 else 0, address = address, body = body,
            date = now - (hoursAgo + 2) * 3_600_000, outgoing = outgoing, status = if (outgoing) 3 else 0, category = "PERSONAL",
            attachments = if (photo) listOf(app.yarn.data.db.BinAttachment("image/jpeg", "file:///x.jpg")) else emptyList(),
            deletedAt = now - hoursAgo * 3_600_000,
        )
    private val binItems = listOf(
        binned(1, "Karan Mittal", "+919812345678", "Reached home, call you in 10", 1),
        binned(2, "Mom", "+919800000002", "Don't forget the medicines", 3, photo = true),
        binned(3, "HDFC Bank", "VM-HDFCBK-S", "Rs.2,500.00 debited from A/c XX1234 to VPA swiggy@icici", 26),
        binned(4, "Karan Mittal", "+919812345678", "Sure, 7 works", 50, outgoing = true),
        binned(5, "DELTRF", "VM-DELTRF-G", "Your OTP for check Notice details is:769966 . Delhi Traffic Police", 120),
    )
    @Test fun binDark() = shot(true, "39_bin_dark") {
        app.yarn.ui.bin.BinContent(binItems, 7, emptySet(), onToggle = {}, onRestore = {}, onDeleteForever = {}, onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now)
    }
    @Test fun binSelected() = shot(false, "40_bin_selected_light") {
        app.yarn.ui.bin.BinContent(binItems, 7, setOf(2L, 4L), onToggle = {}, onRestore = {}, onDeleteForever = {}, onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now)
    }
    // A long message in the bin: the search shows the part that matches.
    private val binSearchItems = binItems + binned(6, "Amazon", "AX-AMAZON-S",
        "Hello from Amazon! We have refunded Rs 1,299 for your return of boAt Airdopes to your original payment method.", 300)
    @Test fun binSearch() = shot(true, "54_bin_search_dark") {
        val q = "refund"
        app.yarn.ui.bin.BinContent(app.yarn.ui.bin.BinSearch.filter(binSearchItems, q), 7, emptySet(), onToggle = {}, onRestore = {}, onDeleteForever = {},
            onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now, query = q, total = binSearchItems.size, onSelectAll = {})
    }
    @Test fun binSearchNumber() = shot(false, "55_bin_search_number_light") {
        val q = "98123"
        app.yarn.ui.bin.BinContent(app.yarn.ui.bin.BinSearch.filter(binSearchItems, q), 7, emptySet(), onToggle = {}, onRestore = {}, onDeleteForever = {},
            onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now, query = q, total = binSearchItems.size, onSelectAll = {})
    }
    @Test fun binSearchNothing() = shot(true, "56_bin_search_nothing_dark") {
        val q = "passport"
        app.yarn.ui.bin.BinContent(app.yarn.ui.bin.BinSearch.filter(binSearchItems, q), 7, emptySet(), onToggle = {}, onRestore = {}, onDeleteForever = {},
            onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now, query = q, total = binSearchItems.size, onSelectAll = {})
    }
    // Senders from a real bin, deleted before they had names: each shows its current name and logo.
    private val realBin = listOf(
        binned(11, "SNITCH", "VM-SNITCH-S", "1 item(s) from your SNITCH order 1234567 have been delivered.", 70),
        binned(12, "XPBEES", "AX-XPBEES-S", "Order:10000001 from Tata Cliq of Super You Protein Wafer Bars is out for delivery", 70),
        binned(13, "PHEASY", "JD-PHEASY-S", "<#> Use OTP 1111 to login to your account on PharmEasy.", 70),
        binned(14, "MGCPNP", "AD-MGCPNP-S", "<#> Your magicpin code for login is: 222222. Do not share it with anyone.", 30),
        binned(15, "FRCHBT", "VM-FRCHBT-S", "3333 is your OTP to login to your Freecharge Biz Account.", 30),
        binned(16, "QKWLLT", "VK-QKWLLT-S", "Your QuikWallet a/c debited for Rs.100.00 and credited to billpay@upi", 70),
        binned(17, "COUPLX", "JD-COUPLX-S", "Dear Customer, Rs.500.00 is credited into your COUPL Card.", 70),
        binned(18, "HSQUAR", "AD-HSQUAR-S", "<#> Hudle up! Your one-time password is 44444 to login to The Hudle App", 30),
        binned(19, "PLAYO", "JD-PLAYO-S", "OTP - 55555. Please enter this in your Playo app.", 30),
        binned(20, "57575711", "57575711", "Your Apple Account Code is: 666666. Don't share it with anyone.", 50),
        binned(21, "BIGBKT", "VM-BIGBKT-S", "Bigbasket login code: 777777. Valid for 3 mins.", 60),
        binned(22, "MYSBIC", "AD-MYSBIC-S", "E-statement of SBI Credit Card ending XX12 dated 01/11/2026 has been mailed.", 60),
        binned(23, "SHPFAS", "JD-SHPFAS-S", "Your order #CR100000_10000000000001 from Mecraaz is out for delivery! Track here", 60),
    )
    @Test @Config(qualifiers = "w411dp-h1420dp-xxhdpi") fun binRealSenders() = shot(true, "57_bin_named_senders_dark") {
        app.yarn.ui.bin.BinContent(realBin, 30, emptySet(), onToggle = {}, onRestore = {}, onDeleteForever = {}, onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now)
    }
    @Test fun binEmpty() = shot(false, "41_bin_empty") {
        app.yarn.ui.bin.BinContent(emptyList(), 7, emptySet(), onToggle = {}, onRestore = {}, onDeleteForever = {}, onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now)
    }
    @Test @Config(qualifiers = "hi-w411dp-h891dp-xxhdpi") fun binHindi() = shot(true, "42_hi_bin_dark") {
        app.yarn.ui.bin.BinContent(binItems, 7, emptySet(), onToggle = {}, onRestore = {}, onDeleteForever = {}, onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now)
    }
    @Test @Config(qualifiers = "w411dp-h1500dp-xxhdpi") fun cleanerWithBin() = shot(true, "43_cleanup_with_bin_dark") {
        app.yarn.ui.cleaner.CleanerContent(
            app.yarn.ui.cleaner.CleanerState(scanning = false, found = found), app.yarn.ui.cleaner.AutoClean(240, 30, 7, 7), false, false, true,
            {}, {}, {}, {}, {}, {}, binCount = 12,
        )
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

    private val karan = app.yarn.data.contacts.ContactInfo("Karan Mittal", null, null, false)

    /** A real-looking thread: runs of messages, a day break, an online translation and "Delivered". */
    @androidx.compose.runtime.Composable
    private fun RunsMock() {
        val noop = app.yarn.ui.conversation.BubbleCallbacks({}, {}, {}, {}, {}, { _, _ -> })
        val D = app.yarn.data.db.MessageStatus.DELIVERED
        val S = app.yarn.data.db.MessageStatus.SENT
        val thread = listOf(
            msg(1, "Hey! Are you coming to Diwali dinner on Saturday?", false, 1, 300),
            msg(2, "Mom is making chole bhature", false, 1, 299),
            msg(3, "Yes! Wouldn't miss it", true, 1, 290, S),
            msg(4, "What time should I come?", true, 1, 289, S),
            msg(5, "Kal shaam ko milte hain? Movie dekhne chalenge", false, 1, 62),
            msg(6, "7 baje theek rahega", false, 1, 61),
            msg(7, "Perfect, see you at 7", true, 1, 12, D),
            msg(8, "I'll book the tickets 🎬", true, 1, 11, D),
        ).map { it.message }
        Column(Modifier.fillMaxSize().background(app.yarn.ui.theme.chatBackdrop()).padding(top = 28.dp)) {
            thread.forEachIndexed { i, m ->
                val position = app.yarn.ui.conversation.runPosition(thread.getOrNull(i - 1), m, thread.getOrNull(i + 1))
                app.yarn.ui.conversation.MessageBubble(
                    app.yarn.data.db.MessageWithAttachments(m, emptyList(), emptyList()), null, false, false,
                    if (m.id == 5L) app.yarn.ui.conversation.TranslationUi.Done("Shall we meet tomorrow evening? Let's go to a movie", "hi-Latn", online = true) else null,
                    false, noop, translateTo = if (m.id == 6L) "en" else null, position = position, latestSent = m.id == 8L,
                )
            }
        }
    }
    @Test fun chatRunsDark() = shot(true, "45_chat_runs_delivered_dark") { RunsMock() }
    @Test fun chatRunsLight() = shot(false, "46_chat_runs_delivered_light") { RunsMock() }
    @Test fun accountSheetDark() = shot(true, "47_account_sheet_dark") {
        androidx.compose.material3.Surface(color = app.yarn.ui.settings.settingsPageColor(), contentColor = MaterialTheme.colorScheme.onSurface) { app.yarn.ui.inbox.AccountSheetContent(karan, listOf(jio, airtel), true, app.yarn.data.prefs.AppSettings(otpAutoDeleteMinutes = 240), {}, {}, {}, {}, {}) }
    }
    @Test fun accountSheetLight() = shot(false, "48_account_sheet_light") {
        androidx.compose.material3.Surface(color = app.yarn.ui.settings.settingsPageColor(), contentColor = MaterialTheme.colorScheme.onSurface) { app.yarn.ui.inbox.AccountSheetContent(null, listOf(jio), true, app.yarn.data.prefs.AppSettings(), {}, {}, {}, {}, {}) }
    }

    @Test fun profileEditorDark() = shot(true, "49_profile_editor_dark") {
        androidx.compose.material3.Surface(color = app.yarn.ui.settings.settingsPageColor(), contentColor = MaterialTheme.colorScheme.onSurface) {
            app.yarn.ui.inbox.ProfileEditor("Karan Mittal", null, onSave = { _, _, _ -> }, onBack = {})
        }
    }
    @Test fun photoCropper() = shot(true, "50_photo_cropper") {
        // A stand-in photo: a soft diagonal gradient with a bright disc where a face would be.
        val bmp = android.graphics.Bitmap.createBitmap(1200, 900, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawPaint(android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 1200f, 900f, 0xFF3D7BFF.toInt(), 0xFFFF9A62.toInt(), android.graphics.Shader.TileMode.CLAMP) })
        canvas.drawCircle(600f, 420f, 220f, android.graphics.Paint().apply { color = 0xFFF6D7B0.toInt(); isAntiAlias = true })
        app.yarn.ui.inbox.PhotoCropper(bmp, onCancel = {}, onDone = {})
    }

    // A typical inbox, with each sender's logo where its sender ID is a known brand.
    private fun withLogos(vararg rows: InboxItem): List<InboxItem> {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        return rows.map { it.copy(photoUri = app.yarn.ui.common.BrandLogos.uri(ctx, it.conversation.addresses.first())) }
    }
    private val logoInbox get() = withLogos(
        item(1, "TRAI", "स्पैम कॉल्स से परेशान है? TRAI DND ऐप पर स्पैम की शिकायत दर्ज करें।", "UPDATES", 0, "VM-TRAIND-G", true, InboxGroup.UPDATES),
        item(2, "Yes Bank", "Dear Customer, your request for Aadhaar Detail for YES BANK Customer ID 1234567 is received", "UPDATES", 0, "AX-YESBNK-S", true, InboxGroup.UPDATES),
        item(3, "HDFC Bank", "Reward Points Credited: 3000 To HDFC Bank Credit Card 1111 On 16/NOV/2026", "BANKING", 0, "VM-HDFCBK-S", true, InboxGroup.TRANSACTIONS),
        item(4, "HSBC", "HSBC: Rs 115.0 spent on your HSBC Credit Card ending 2222 at SWIGGY on 17 Nov 2026", "BANKING", 0, "JD-HSBCIN-S", true, InboxGroup.TRANSACTIONS),
        item(5, "Swiggy Toing", "Your payment for Toing order #100000000000001 was not completed. Amount will be refunded", "SHOPPING", 0, "VM-SWGTNG-S", true, InboxGroup.SHOPPING),
        item(6, "AU Bank", "Alert! INR 1.00 credited to AU Bank Credit Card XX3333 from Apollo Pharm on 16-11-2026", "BANKING", 0, "AD-AUBANK-S", true, InboxGroup.TRANSACTIONS),
        item(7, "SBI Card", "Dear Cardholder, with the Balance Transfer on EMI facility on your SBI Card", "PROMOTIONS", 0, "AD-SBICRD-P", true, InboxGroup.OFFERS),
        item(8, "SBI", "Dear Customer, transaction number 1234 for Rs 500 is successful", "BANKING", 0, "AD-SBIINB-S", true, InboxGroup.TRANSACTIONS),
        item(9, "Amazon", "Your order of boAt Airdopes has been delivered", "SHOPPING", 0, "AX-AMAZON-S", true, InboxGroup.SHOPPING),
        item(10, "IndiGo", "Web check-in is open for 6E 2134 BLR-DEL", "TRAVEL", 0, "JD-INDIGO-S", true, InboxGroup.TRAVEL),
    )
    private val moreLogos get() = withLogos(
        item(1, "Jio", "Your Jio number xxxxxx1234 recharge of Rs 349 is successful", "BILLS", 0, "JM-JIOINF-S", true, InboxGroup.UPDATES),
        item(2, "Blue Dart", "Your order with Blue Dart AWB# 12345678901 was delivered to SELF", "DELIVERY", 0, "BZ-BLUDRT-S", true, InboxGroup.SHOPPING),
        item(3, "Shopwise", "Congratulations, your Amazon Pay E-Gift Voucher is here", "PROMOTIONS", 0, "VM-SHPWSE-S", true, InboxGroup.OFFERS),
        item(4, "Union Bank", "Your A/c XX1234 is credited by Rs 5,000.00", "BANKING", 0, "AD-UNIONB-S", true, InboxGroup.TRANSACTIONS),
        item(5, "Bandhan Bank", "Rs 1,200 debited from A/c XX5678 via UPI", "BANKING", 0, "VM-BANDHN-S", true, InboxGroup.TRANSACTIONS),
        item(6, "CheQ", "Your credit card bill of Rs 3,357 has been paid via CheQ", "PAYMENTS", 0, "AD-CHEQAP-S", true, InboxGroup.TRANSACTIONS),
        item(7, "IndiGo", "Your boarding pass for 6E 2134 is ready", "TRAVEL", 0, "JD-INDIGO-S", true, InboxGroup.TRAVEL),
        item(8, "Zara", "Your Zara order has been shipped", "SHOPPING", 0, "AD-ZARAIN-S", true, InboxGroup.SHOPPING),
        item(9, "Duolingo", "Keep your streak going! 5 minutes a day", "PROMOTIONS", 0, "VM-DUOLNG-P", true, InboxGroup.OFFERS),
        item(10, "BMW", "Your BMW service is due next week", "UPDATES", 0, "AD-BMWIND-S", true, InboxGroup.UPDATES),
    )
    @Test fun inboxMoreLogos() = shot(true, "58_inbox_more_logos_dark") { InboxMock(moreLogos) }
    private val lastLogos get() = withLogos(
        item(1, "UCO Bank", "Your A/c XX1234 is debited by Rs 500.00", "BANKING", 0, "AD-UCOBNK-S", true, InboxGroup.TRANSACTIONS),
        item(2, "Karnataka Bank", "Rs 2,000 credited to your A/c XX5678", "BANKING", 0, "VM-KARBNK-S", true, InboxGroup.TRANSACTIONS),
        item(3, "BSNL", "Your BSNL bill of Rs 499 is generated", "BILLS", 0, "BZ-BSNLIN-S", true, InboxGroup.UPDATES),
        item(4, "DTDC", "Your shipment D12345678 is out for delivery", "DELIVERY", 0, "JD-DTDCIN-S", true, InboxGroup.SHOPPING),
        item(5, "HP", "Your HP laptop warranty is expiring soon", "UPDATES", 0, "AD-HPINDA-S", true, InboxGroup.UPDATES),
        item(6, "Lenovo", "Your Lenovo order has been shipped", "SHOPPING", 0, "VM-LENOVO-S", true, InboxGroup.SHOPPING),
        item(7, "Tata Motors", "Your Nexon service is due on 25 Nov", "UPDATES", 0, "AD-TATAMT-S", true, InboxGroup.UPDATES),
        item(8, "Kalyan Jewellers", "Gold rate today: Rs 7,250/g. Visit your nearest showroom", "PROMOTIONS", 0, "VM-KALYAN-P", true, InboxGroup.OFFERS),
        item(9, "MRF", "Your MRF tyre warranty is registered", "UPDATES", 0, "AD-MRFLTD-S", true, InboxGroup.UPDATES),
        item(10, "Subway", "Sub of the day: buy 1 get 1 free", "PROMOTIONS", 0, "VM-SUBWAY-P", true, InboxGroup.OFFERS),
    )
    @Test fun inboxLastLogos() = shot(true, "59_inbox_found_logos_dark") { InboxMock(lastLogos) }
    // The top of the user's inbox: Smytten and Jio's number-only sender now have logos.
    private val topOfInbox get() = withLogos(
        item(1, "Bank of Baroda", "Dear BOB UPI User: Your account is credited with INR 2.00", "BANKING", 0, "AD-BOBTXN-S", true, InboxGroup.TRANSACTIONS),
        item(2, "Smytten", "Flash Sale! Get Full-size trials from Foxtale, Novology & more at 4 trial points till midnight", "PROMOTIONS", 0, "JD-SMYTTN-P", true, InboxGroup.OFFERS),
        item(3, "Domino's", "<#>Your Domino's login OTP is 123456", "OTP", 0, "VM-DOMINO-S", true, InboxGroup.OTP, "123456"),
        item(4, "Jio", "क्या आप अपना Jio नंबर xxxxxx1234 चालू रखना चाहते हैं?", "UPDATES", 0, "JM-620021-S", true, InboxGroup.UPDATES),
        item(5, "Tata CLiQ", "Hi, your order of Fitspire YOR Daily Rose Falooda is confirmed", "SHOPPING", 0, "JM-MYCLIQ-S", true, InboxGroup.SHOPPING),
        item(6, "MobiKwik", "Your KYC is due for update by January 17, 2027", "UPDATES", 0, "AD-MOBIKW-S", true, InboxGroup.UPDATES),
    )
    @Test fun inboxTop() = shot(true, "60_inbox_smytten_jio_dark") { InboxMock(topOfInbox) }
    private val travelInbox get() = withLogos(
        item(1, "Routematic", "SS-HR-1234 assigned for your pickup has started. You can track vehicle using mobile app", "TRAVEL", 0, "VK-RMATIC-S", true, InboxGroup.TRAVEL),
        item(2, "Divya", "You: I miss him too.", "PERSONAL", 0, "+919800000003", false, InboxGroup.PERSONAL),
        item(3, "Hoi", "Your Booking ID for Encalm Lounge, T3 INT DEP, Delhi is: HOI0000001. Booking valid for 3 hours", "TRAVEL", 0, "VM-HOIYTC-S", true, InboxGroup.TRAVEL),
    )
    @Test fun inboxHoiRoutematic() = shot(true, "62_inbox_hoi_routematic_dark") { InboxMock(travelInbox) }
    private val rewardsInbox get() = withLogos(
        item(1, "Qwikcilver", "Dear Customer, Your OTP for Flipkart SuperCoins gift card is 123456", "OTP", 0, "VM-QWKCLR-S", true, InboxGroup.OTP, "123456"),
        item(2, "UIDAI (Aadhaar)", "123456 is OTP for Aadhaar (XX1234) (valid for 10 mins)", "OTP", 0, "AD-ADHAAR-G", true, InboxGroup.OTP, "654321"),
        item(3, "Pay with Rewards", "You have spent INR 174 using your Pay with Rewards account", "BANKING", 0, "JD-PAYRWD-S", true, InboxGroup.TRANSACTIONS),
    )
    @Test fun inboxRewards() = shot(true, "63_inbox_aadhaar_qwikcilver_rewards_dark") { InboxMock(rewardsInbox) }
    private val neuInbox get() = withLogos(
        item(1, "Tata Neu", "Asha, welcome to Select tier. Claim new benefits, made for you. Get NeuCard & earn up to 10% Tata NeuCoins.", "PROMOTIONS", 0, "VM-MYTNEU-P", true, InboxGroup.OFFERS),
        item(2, "American Express", "Dear Cardmember, your Amex Card ending 1234 has been cancelled as per your request.", "UPDATES", 0, "VM-AMEXIN-S", true, InboxGroup.UPDATES),
        item(3, "HDFC Bank", "Congratulations! We approved your HDFC Bank Credit Card (App No. D26K12345678A0B1).", "UPDATES", 0, "VD-HDFCBK-S", true, InboxGroup.UPDATES),
    )
    @Test fun inboxNeu() = shot(true, "64_inbox_tata_neu_dark") { InboxMock(neuInbox) }
    private val maximizeInbox get() = withLogos(
        item(1, "Maximize", "<#> 1234 is your OTP to login to your Maximize account.", "OTP", 0, "JD-MAXMZE-S", true, InboxGroup.OTP, "1234"),
        item(2, "Maximize", "Your gift card purchase details are: Amount: 152.00, GC#: 6000000000000000", "UPDATES", 0, "JD-MAXMZE-S", true, InboxGroup.UPDATES),
        item(3, "Kotak Bank", "Sent Rs.15.58 from Kotak Bank A/c X1234 to MAXIMIZE PAY SOLUTIO on 21-11-26.", "BANKING", 0, "AD-KOTAKB-S", true, InboxGroup.TRANSACTIONS),
    )
    @Test fun inboxMaximize() = shot(true, "65_inbox_maximize_dark") { InboxMock(maximizeInbox, storeMe) }

    // ---- Yarn's plan: a 5-day trial, then a yearly subscription ---------------------------------
    private val earlyBird = app.yarn.billing.Licence.Price("₹199", 199_000_000, "INR")
    @androidx.compose.runtime.Composable
    private fun Paywall(access: app.yarn.billing.Access, note: String? = null, back: Boolean = false) =
        app.yarn.ui.paywall.PaywallContent(access, earlyBird, false, note, {}, {}, {}, {}, {}, if (back) ({}) else null)
    @Test fun planExpired() = shot(false, "66_plan_trial_ended_light") { Paywall(app.yarn.billing.Access.Expired) }
    @Test fun planExpiredDark() = shot(true, "67_plan_trial_ended_dark") { Paywall(app.yarn.billing.Access.Expired) }
    @Test fun planTrial() = shot(true, "68_plan_trial_3_days_dark") { Paywall(app.yarn.billing.Access.Trial(3), back = true) }
    @Test fun planPending() = shot(false, "69_plan_payment_pending") { Paywall(app.yarn.billing.Access.Pending) }
    @Test fun planSubscribed() = shot(false, "70_plan_subscribed") { Paywall(app.yarn.billing.Access.Unlocked(), back = true) }
    @Test @Config(qualifiers = "hi-w411dp-h891dp-xxhdpi") fun planHindi() = shot(true, "71_plan_trial_ended_hindi") { Paywall(app.yarn.billing.Access.Expired) }
    @Test fun planProfileRow() = shot(false, "72_profile_trial_row") {
        androidx.compose.material3.Surface(color = app.yarn.ui.settings.settingsPageColor(), contentColor = MaterialTheme.colorScheme.onSurface) {
            app.yarn.ui.inbox.AccountSheetContent(storeMe, listOf(storeJio), true, app.yarn.data.prefs.AppSettings(), {}, {}, {}, {}, {}, plan = app.yarn.billing.Access.Trial(3))
        }
    }
    // Sorting off: no tabs, so notifications have no per-tab choices.
    @Test fun notificationsSortingOff() = shot(false, "74_notifications_sorting_off") {
        app.yarn.ui.settings.SettingsScaffold("Notifications", {}) { notificationItems(myNotify.copy(autoCategorize = false), {}, {}) }
    }
    @Test fun planBanner() = shot(true, "73_inbox_trial_banner_dark") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 24.dp)) {
            app.yarn.ui.inbox.TrialBanner(app.yarn.billing.Access.Trial(2)) {}
            storeInbox.take(4).forEach { ConversationRow(it, false, false, {}, {}) }
        }
    }

    // Notification icons: before (square tile) and after (cut round), with the phone's app badge in the corner.
    @Test fun notificationIcons() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val logos = listOf("woohoo", "hdfcbank", "swiggy", "jio").map { app.yarn.ui.common.BrandLogos.bitmap(ctx, "logo:$it")!! }
        // Corners of the round icon are clear; its middle is the logo.
        val round = app.yarn.notifications.Avatars.round(logos[0])
        org.junit.Assert.assertEquals(0, android.graphics.Color.alpha(round.getPixel(1, 1)))
        org.junit.Assert.assertEquals(255, android.graphics.Color.alpha(round.getPixel(round.width / 2, round.height / 2)))
        shot(true, "61_notification_icons_dark") {
            Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF2B2F38)).padding(24.dp)) {
                for ((label, cut) in listOf("Before" to false, "Now" to true)) {
                    androidx.compose.material3.Text(label, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
                        for (bmp in logos) {
                            androidx.compose.foundation.layout.Box(Modifier.size(72.dp)) {
                                val img = if (cut) app.yarn.notifications.Avatars.round(bmp) else bmp
                                androidx.compose.foundation.Image(img.asImageBitmap(), null, Modifier.size(64.dp))
                                app.yarn.ui.common.YarnLogo(30.dp, Modifier.align(androidx.compose.ui.Alignment.BottomEnd))
                            }
                        }
                    }
                }
            }
        }
    }
    @Test fun inboxLogosDark() = shot(true, "52_inbox_brand_logos_dark") { InboxMock(logoInbox) }
    @Test fun inboxLogosLight() = shot(false, "53_inbox_brand_logos_light") { InboxMock(logoInbox) }

    @Test fun chatLinksNoButtons() = shot(true, "51_chat_links_no_buttons_dark") {
        val noop = app.yarn.ui.conversation.BubbleCallbacks({}, {}, {}, {}, {}, { _, _ -> })
        // The real extractor finds the link, number and code, exactly as on the phone.
        val extractor = app.yarn.intelligence.EntityExtractor()
        fun analysed(id: Long, body: String, minutesAgo: Long) = msg(id, body, false, 1, minutesAgo).let { w ->
            w.copy(entities = with(app.yarn.ai.IntelligenceEngine) { extractor.extract(body, w.message.date).map { it.toRow(id, "rules") } })
        }
        Column(Modifier.fillMaxSize().background(app.yarn.ui.theme.chatBackdrop()).padding(top = 32.dp)) {
            app.yarn.ui.conversation.MessageBubble(
                analysed(1, "Dear Customer, your SBI Credit Card statement is ready. View it at https://sbicard.com/statement or call 1860 180 1290 for help. -SBI Card", 120),
                null, false, false, null, true, noop,
            )
            app.yarn.ui.conversation.MessageBubble(
                analysed(2, "Your OTP for login is 482913. Do not share it with anyone. -SBI", 5),
                null, false, false, null, true, noop,
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

    // ---- Play Store screenshots (tools/store/frame.py frames them) ----------------------------
    // Made-up people, numbers and codes only: nothing here comes from a real phone.
    private val storeMe = app.yarn.data.contacts.ContactInfo("Asha Verma", null, null, false)
    private val storeJio = app.yarn.telephony.SimInfo(1, 0, "Jio 4G", "Jio", "+91 98000 11111", 0)
    private val storeAirtel = app.yarn.telephony.SimInfo(2, 1, "airtel", "airtel", "+91 99000 22222", 0)
    private val storeInbox get() = withLogos(
        item(1, "Priya Sharma", "Are we still on for dinner tonight?", "PERSONAL", 2, "+919812345678", false, InboxGroup.PERSONAL, pinned = true),
        item(2, "SBI Card", "423378 is the OTP for Trxn. of INR 375.00 at AMAZON with your SBI Card", "OTP", 1, "AD-SBICRD-T", true, InboxGroup.OTP, "423378"),
        item(3, "HDFC Bank", "Rs.2,500.00 debited from A/c XX1234 to VPA swiggy@icici", "BANKING", 1, "VM-HDFCBK-S", true, InboxGroup.TRANSACTIONS),
        item(4, "Swiggy", "Your Swiggy order from Behrouz Biryani is on the way!", "SHOPPING", 0, "AD-SWIGGY-S", true, InboxGroup.SHOPPING),
        item(5, "IndiGo", "Web check-in is open for your flight 6E 2134 BLR-DEL", "TRAVEL", 0, "JD-INDIGO-S", true, InboxGroup.TRAVEL),
        item(6, "Mom", "Call me when you're free", "PERSONAL", 0, "+919800000002", false, InboxGroup.PERSONAL),
        item(7, "Amazon", "Your order of boAt Airdopes has been delivered", "SHOPPING", 0, "AX-AMAZON-S", true, InboxGroup.SHOPPING),
        item(8, "Jio", "Your Jio recharge of Rs 349 is successful. Enjoy unlimited 5G.", "BILLS", 0, "JM-JIOINF-S", true, InboxGroup.UPDATES),
        item(9, "IRCTC", "PNR 4521367890: Train 12951 Coach B2 Berth 34 confirmed.", "TRAVEL", 0, "AD-IRCTCI-S", true, InboxGroup.TRAVEL),
        item(10, "Zepto", "Sale is LIVE! Get flat 50% off on fruits & veggies. Order now", "PROMOTIONS", 0, "JM-ZEPTON-P", true, InboxGroup.OFFERS),
    )
    private val storeOtps get() = withLogos(
        item(1, "Amazon", "382915 is your Amazon OTP. Do not share it with anyone.", "OTP", 1, "AX-AMAZON-S", true, InboxGroup.OTP, "382915"),
        item(2, "Blue Dart", "Your Blue Dart Secure Delivery Code is 692079 and it's valid for the next 30 minutes.", "OTP", 1, "BZ-BLUDRT-S", true, InboxGroup.OTP, "692079"),
        item(3, "Swiggy", "Your Swiggy login OTP is 482913. Valid for 10 minutes.", "OTP", 0, "AD-SWIGGY-S", true, InboxGroup.OTP, "482913"),
        item(5, "PNB", "101073 is your OTP for purchase of INR 1,499.00 at FLIPKART", "OTP", 0, "AD-PNBSMS-S", true, InboxGroup.OTP, "101073"),
        item(8, "Uber", "Your Uber ride PIN is 4821. Share it with your driver at pickup.", "OTP", 0, "VM-UBERIN-S", true, InboxGroup.OTP, "4821"),
        item(12, "Zomato", "Your Zomato login code is 750264. Don't share it with anyone.", "OTP", 0, "JD-ZOMATO-S", true, InboxGroup.OTP, "750264"),
    )

    @androidx.compose.runtime.Composable
    private fun StoreChat() {
        val noop = app.yarn.ui.conversation.BubbleCallbacks({}, {}, {}, {}, {}, { _, _ -> })
        val D = app.yarn.data.db.MessageStatus.DELIVERED
        val thread = listOf(
            msg(1, "Hey! Are you coming to Diwali dinner on Saturday?", false, 1, 300),
            msg(2, "Mom is making chole bhature", false, 1, 299),
            msg(3, "Yes! Wouldn't miss it", true, 1, 290, D),
            msg(4, "Kal shaam ko milte hain? Movie dekhne chalenge", false, 1, 62),
            msg(5, "Perfect, see you at 7", true, 2, 12, D),
            msg(6, "I'll book the tickets 🎬", true, 2, 11, D),
        ).map { it.message }
        Column(Modifier.fillMaxSize().background(app.yarn.ui.theme.chatBackdrop())) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 30.dp, bottom = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(onClick = {}) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, null) }
                app.yarn.ui.common.Avatar("Priya Sharma", null, size = 36.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text("Priya Sharma", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.material3.Text("+91 98123 45678", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.IconButton(onClick = {}) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Call, null) }
                androidx.compose.material3.IconButton(onClick = {}) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.MoreVert, null) }
            }
            thread.forEachIndexed { i, m ->
                val position = app.yarn.ui.conversation.runPosition(thread.getOrNull(i - 1), m, thread.getOrNull(i + 1))
                app.yarn.ui.conversation.MessageBubble(
                    app.yarn.data.db.MessageWithAttachments(m, emptyList(), emptyList()), null, false, false,
                    if (m.id == 4L) app.yarn.ui.conversation.TranslationUi.Done("Shall we meet tomorrow evening? Let's go to a movie", "hi-Latn", online = true) else null,
                    true, noop, position = position, latestSent = m.id == 6L, sim = if (m.subId == 2) storeAirtel else storeJio,
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            app.yarn.ui.conversation.Composer(
                text = "", onText = {}, attachments = emptyList(), onAddAttachments = {}, onRemoveAttachment = {},
                sims = listOf(storeJio, storeAirtel), subId = 2, onSim = {}, smartReplies = listOf("See you then!", "Sounds good", "👍"), onSmartReply = {},
                onSend = {}, onSchedule = {}, onRewrite = {}, isGroupMms = false,
            )
        }
    }
    private fun storeBinned(id: Long, title: String, address: String, body: String, hoursAgo: Long, photo: Boolean = false) =
        binned(id, title, address, body, hoursAgo, photo = photo)
    private val storeBin = listOf(
        storeBinned(1, "Priya Sharma", "+919812345678", "Reached home, call you in 10", 1),
        storeBinned(2, "Mom", "+919800000002", "Don't forget the medicines", 3, photo = true),
        storeBinned(3, "HDFC Bank", "VM-HDFCBK-S", "Rs.2,500.00 debited from A/c XX1234 to VPA swiggy@icici", 26),
        storeBinned(4, "Amazon", "AX-AMAZON-S", "Hello from Amazon! We have refunded Rs 1,299 for your return of boAt Airdopes to your original payment method.", 50),
        storeBinned(5, "Myntra", "AD-MYNTRA-S", "Refund of Rs 899 for your returned Levi's jeans has been processed to your bank account.", 120),
        storeBinned(6, "IRCTC", "AD-IRCTCI-S", "PNR 4521367890: Train 12951 Coach B2 Berth 34 confirmed.", 200),
    )

    @Test fun store1Inbox() = shot(false, "store_1_inbox") { InboxMock(storeInbox, storeMe) }
    @Test fun store2Otp() = shot(true, "store_2_otp") { TabMock(storeOtps, InboxGroup.OTP, storeMe) }
    // Shorter screen so the reply bar fits inside the store frame.
    @Test @Config(qualifiers = "w411dp-h740dp-xxhdpi") fun store3Chat() = shot(false, "store_3_chat") { StoreChat() }
    @Test fun store4Spam() = shot(true, "store_4_spam") { SpamMock(storeMe) }
    @Test fun store5Profile() = shot(false, "store_5_profile") {
        androidx.compose.material3.Surface(color = app.yarn.ui.settings.settingsPageColor(), contentColor = MaterialTheme.colorScheme.onSurface) {
            app.yarn.ui.inbox.AccountSheetContent(storeMe, listOf(storeJio, storeAirtel), true, app.yarn.data.prefs.AppSettings(otpAutoDeleteMinutes = 240), {}, {}, {}, {}, {})
        }
    }
    @Test fun store6Bin() = shot(true, "store_6_bin") {
        val q = "refund"
        app.yarn.ui.bin.BinContent(app.yarn.ui.bin.BinSearch.filter(storeBin, q), 30, emptySet(), onToggle = {}, onRestore = {}, onDeleteForever = {},
            onRestoreAll = {}, onEmpty = {}, onBack = {}, now = now, query = q, total = storeBin.size, onSelectAll = {})
    }
    @Test fun store7Backup() = shot(false, "store_7_backup") {
        app.yarn.ui.settings.SettingsScaffold("Backup", {}) {
            backupItems(
                app.yarn.data.prefs.AppSettings(googleAccount = "you@gmail.com", lastBackupAt = now - 3_600_000, lastBackupBytes = 48_000_000),
                null, app.yarn.backup.BackupState.Done("Backed up 12,306 messages to Google Drive"), false, null, false, {}, {}, {}, {}, {}, {},
            )
        }
    }
    @Test fun store8Private() = shot(true, "store_8_private") {
        app.yarn.ui.settings.SettingsScaffold("Smart features", {}) { smartFeatureItems(app.yarn.data.prefs.AppSettings(), {}, {}, {}) }
    }
}
