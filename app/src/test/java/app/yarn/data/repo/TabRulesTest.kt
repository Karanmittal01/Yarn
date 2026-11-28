package app.yarn.data.repo

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import app.yarn.data.db.ConversationEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.CustomTab
import app.yarn.intelligence.CategoryGroup
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** All aside, every received message is in exactly one tab, and the user's own tabs take theirs out of the rest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TabRulesTest {
    private lateinit var db: YarnDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), YarnDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private val bank = CustomTab("bank", "Bank & payments", kind = CategoryGroup.TRANSACTIONS)
    private val salary = CustomTab("salary", "Salary", words = listOf("salary", "it's"))
    private val family = CustomTab("family", "Family", chats = listOf(31L))

    private var pid = 1L
    private suspend fun msg(conv: Long, body: String, category: String, date: Long, read: Boolean = true) =
        db.messages().insert(MessageEntity(conversationId = conv, providerId = pid++, address = "a$conv", body = body, date = date, outgoing = false, status = MessageStatus.RECEIVED, category = category, read = read))

    private suspend fun seed() {
        db.conversations().upsert(ConversationEntity(id = 30, addresses = listOf("VM-HDFCBK-S"), displayName = "HDFC Bank", category = "BANKING", messageCount = 4))
        msg(30, "OTP 482913 for your transaction", "OTP", 1_000, read = false)
        msg(30, "Rs.2,500 debited from A/c XX1234", "BANKING", 2_000, read = false)
        msg(30, "Your new debit card has been dispatched", "UPDATES", 3_000)
        msg(30, "Salary of Rs.85,000 credited to A/c XX1234", "BANKING", 4_000)
        db.conversations().upsert(ConversationEntity(id = 31, addresses = listOf("+919800000002"), displayName = "Mom", category = "PERSONAL", messageCount = 2))
        msg(31, "Beta, did your salary come?", "PERSONAL", 5_000)
        msg(31, "Call me when you're free", "PERSONAL", 6_000, read = false)
        db.conversations().upsert(ConversationEntity(id = 32, addresses = listOf("AX-AMAZON-S"), displayName = "Amazon", category = "SHOPPING", messageCount = 1))
        msg(32, "Your order has been shipped", "SHOPPING", 7_000)
        db.conversations().upsert(ConversationEntity(id = 33, addresses = listOf("+919811111111"), displayName = "Rahul", category = "PERSONAL", messageCount = 1))
        msg(33, "Movie tonight?", "PERSONAL", 8_000)
    }

    /** Ids of received messages a tab lists. */
    private fun ids(condition: String): Set<Long> {
        val out = HashSet<Long>()
        db.openHelper.readableDatabase.query("SELECT m.id FROM messages m JOIN conversations k ON k.id = m.conversationId WHERE m.outgoing = 0 AND $condition").use { c ->
            while (c.moveToNext()) out += c.getLong(0)
        }
        return out
    }

    private fun all(): Set<Long> = ids("1")

    private fun rows(filter: InboxFilter, tabs: List<CustomTab>, keep: String? = null) =
        db.conversations().observeSections(SimpleSQLiteQuery(sectionQuery(TabRules.condition(filter, tabs), keep)))

    @Test fun everyMessageIsInExactlyOneTab() = runTest {
        seed()
        for (tabs in listOf(emptyList(), listOf(bank), listOf(salary, family), listOf(family, salary), listOf(bank, salary, family))) {
            val lists = InboxGroup.entries.map { it.name to ids(TabRules.condition(it.filter, tabs)) } + tabs.map { it.name to ids(TabRules.condition(it.filter, tabs)) }
            val seen = HashMap<Long, String>()
            for ((tab, set) in lists) for (id in set) {
                assertWithMessage("message $id is in both ${seen[id]} and $tab (own tabs: ${tabs.map { it.name }})").that(seen.containsKey(id)).isFalse()
                seen[id] = tab
            }
            assertWithMessage("every message has a tab (own tabs: ${tabs.map { it.name }})").that(seen.keys).isEqualTo(all())
        }
    }

    @Test fun yourOwnTabsTakeTheirMessagesOutOfTheOthers() = runTest {
        seed()
        val tabs = listOf(bank, salary, family)
        // Bank & payments comes first, so it has both bank messages, the salary credit included…
        assertThat(ids(TabRules.condition(bank.filter, tabs))).hasSize(2)
        assertThat(rows(bank.filter, tabs).first().map { it.sectionSnippet }).containsExactly("Salary of Rs.85,000 credited to A/c XX1234")
        // …and Salary gets Mom's message about it.
        assertThat(rows(salary.filter, tabs).first().map { it.sectionSnippet }).containsExactly("Beta, did your salary come?")
        // Put Salary first and the credit goes there instead: the first tab in the row that wants a message gets it.
        val salaryFirst = listOf(salary, bank, family)
        assertThat(rows(salary.filter, salaryFirst).first().map { it.sectionSnippet }).containsExactly("Salary of Rs.85,000 credited to A/c XX1234", "Beta, did your salary come?")
        assertThat(rows(bank.filter, salaryFirst).first().map { it.sectionSnippet }).containsExactly("Rs.2,500 debited from A/c XX1234")
        // Updates keeps the card notice and the order; the debit left it.
        assertThat(rows(InboxGroup.UPDATES.filter, tabs).first().map { it.sectionSnippet }).containsExactly("Your new debit card has been dispatched", "Your order has been shipped")
        // OTP keeps the code: no tab of one's own wanted it.
        assertThat(rows(InboxGroup.OTP.filter, tabs).first().map { it.sectionSnippet }).containsExactly("OTP 482913 for your transaction")
        // Mom's chat is in Family (picked), without the message Salary took; Personal keeps only Rahul.
        val familyRows = rows(family.filter, tabs, "c.id IN (31)").first()
        assertThat(familyRows.map { it.conversation.id to it.sectionSnippet }).containsExactly(31L to "Call me when you're free")
        val personal = rows(InboxGroup.PERSONAL.filter, tabs, "c.category IN ('PERSONAL') AND c.id NOT IN (31)").first()
        assertThat(personal.map { it.conversation.id }).containsExactly(33L)
        // Taking a tab away gives its messages back: without Bank & payments the debit is in Updates again.
        val debit = ids("m.body LIKE 'Rs.2,500%'")
        assertThat(ids(TabRules.condition(InboxGroup.UPDATES.filter, tabs))).containsNoneIn(debit)
        assertThat(ids(TabRules.condition(InboxGroup.UPDATES.filter, listOf(salary, family)))).containsAtLeastElementsIn(debit)
    }

    @Test fun tabCountsFollowTheSameRule() = runTest {
        seed()
        val tabs = listOf(bank, salary, family)
        val counts = db.conversations().observeTabCounts(SimpleSQLiteQuery(TabRules.countsQuery(InboxGroup.entries, tabs)!!)).first().associateBy { it.tab }
        assertThat(counts.getValue("bank").chats).isEqualTo(1)
        assertThat(counts.getValue("bank").unread).isEqualTo(1)
        assertThat(counts.getValue("OTP").unread).isEqualTo(1)
        assertThat(counts.getValue("family").unread).isEqualTo(1)
        assertThat(counts.getValue("PERSONAL").chats).isEqualTo(1)
        // Updates: the card notice and the order, both read.
        assertThat(counts.getValue("UPDATES").chats).isEqualTo(2)
        assertThat(counts.getValue("UPDATES").unread).isEqualTo(0)
    }

    @Test fun wordsWithQuotesAndWildcardsAreSafe() = runTest {
        seed()
        msg(33, "50% off, it's 100_000 points", "PROMOTIONS", 9_000)
        assertThat(ids(TabRules.condition(CustomTab("w", "W", words = listOf("it's")).filter, emptyList())).size).isEqualTo(1)
        assertThat(ids(TabRules.condition(CustomTab("p", "P", words = listOf("50%")).filter, emptyList())).size).isEqualTo(1)
        assertThat(ids(TabRules.condition(CustomTab("u", "U", words = listOf("0_0")).filter, emptyList())).size).isEqualTo(1)
        assertThat(ids(TabRules.condition(CustomTab("n", "N", words = listOf("%")).filter, emptyList())).size).isEqualTo(1)
    }
}
