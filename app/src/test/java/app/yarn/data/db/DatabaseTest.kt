package app.yarn.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real Room schema and SQL (without SQLCipher, which needs a device). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DatabaseTest {
    private lateinit var db: YarnDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), YarnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.conversations().upsert(ConversationEntity(id = 1, addresses = listOf("+15550100"), displayName = "Alex"))
        db.messages().insert(MessageEntity(conversationId = 1, providerId = 10, address = "+15550100", body = "Dinner at Luigi's tomorrow?", date = 1_000, outgoing = false, status = MessageStatus.RECEIVED, read = false, category = "PERSONAL", priority = 70))
        db.messages().insert(MessageEntity(conversationId = 1, address = "+15550100", body = "Sure, see you there", date = 2_000, outgoing = true, status = MessageStatus.QUEUED))
    }

    @Test
    fun refreshComputesAggregates() = runTest {
        seed()
        db.conversations().refresh(1)
        val c = db.conversations().get(1)!!
        assertThat(c.snippet).isEqualTo("Sure, see you there")
        assertThat(c.snippetFromMe).isTrue()
        assertThat(c.unreadCount).isEqualTo(1)
        assertThat(c.messageCount).isEqualTo(2)
        assertThat(c.importance).isEqualTo(70)
        assertThat(c.hasOutgoing).isTrue()
    }

    @Test
    fun repliedConversationsAreNeverAutoSpam() = runTest {
        seed()
        db.messages().insert(MessageEntity(conversationId = 1, address = "+15550100", body = "win a prize", date = 3_000, outgoing = false, status = MessageStatus.RECEIVED, spamVerdict = "SCAM"))
        db.conversations().refresh(1)
        assertThat(db.conversations().get(1)!!.spam).isFalse()
    }

    @Test
    fun fullTextSearchFindsPrefixes() = runTest {
        seed()
        val results = db.messages().search("lui*", 10)
        assertThat(results.map { it.message.body }).containsExactly("Dinner at Luigi's tomorrow?")
        assertThat(results.single().conversationTitle).isEqualTo("Alex")
        assertThat(results.single().snippet).contains("\u0002")
    }

    @Test
    fun queueQueriesRespectTiming() = runTest {
        seed()
        db.messages().insert(MessageEntity(conversationId = 1, address = "+15550100", body = "later", date = 5_000, outgoing = true, status = MessageStatus.SCHEDULED, scheduledAt = 50_000))
        db.messages().insert(MessageEntity(conversationId = 1, address = "+15550100", body = "retry", date = 6_000, outgoing = true, status = MessageStatus.WAITING_FOR_SERVICE, nextAttemptAt = 20_000))
        assertThat(db.messages().dueOutgoing(10_000).map { it.body }).containsExactly("Sure, see you there")
        assertThat(db.messages().dueOutgoing(30_000).map { it.body }).containsExactly("Sure, see you there", "retry")
        assertThat(db.messages().nextDueTime()).isEqualTo(0) // the queued message is due immediately
    }

    @Test
    fun providerIdsAreUnique() = runTest {
        seed()
        val dup = db.messages().insert(MessageEntity(conversationId = 1, providerId = 10, address = "+15550100", body = "dup", date = 1, outgoing = false, status = MessageStatus.RECEIVED))
        assertThat(dup).isEqualTo(-1)
    }

    @Test
    fun deletingConversationCascades() = runTest {
        seed()
        db.conversations().delete(listOf(1))
        assertThat(db.messages().recent(1, 10)).isEmpty()
    }

    @Test
    fun junkQueryKeepsWhatMatters() = runTest {
        val old = 1_000L
        fun conv(id: Long, vararg mods: (ConversationEntity) -> ConversationEntity) =
            mods.fold(ConversationEntity(id = id, addresses = listOf("AX-SHOP$id"), displayName = "Shop $id")) { c, m -> m(c) }
        db.conversations().upsert(conv(10))
        db.conversations().upsert(conv(11, { it.copy(pinned = true) }))
        db.conversations().upsert(conv(12, { it.copy(hasOutgoing = true) }))
        db.conversations().upsert(conv(13, { it.copy(categoryLocked = true, category = "PERSONAL") }))
        var pid = 100L
        suspend fun msg(conv: Long, category: String, date: Long = old, starred: Boolean = false) =
            db.messages().insert(MessageEntity(conversationId = conv, providerId = pid++, address = "AX-SHOP$conv", body = "x", date = date, outgoing = false, status = MessageStatus.RECEIVED, category = category, starred = starred))
        val junk = msg(10, "PROMOTIONS")
        msg(10, "PROMOTIONS", starred = true) // starred: kept
        msg(10, "PROMOTIONS", date = 9_000) // too recent: kept
        msg(10, "BANKING") // not junk
        msg(11, "PROMOTIONS") // pinned chat
        msg(12, "PROMOTIONS") // replied-to chat
        msg(13, "PROMOTIONS") // filed as personal
        assertThat(db.messages().junkIds(listOf("PROMOTIONS"), before = 5_000)).containsExactly(junk)
    }
}
