package app.yarn.messaging

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.yarn.data.db.ConversationEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.YarnDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reactions that arrive as texts become emoji on their message; nothing unmatched disappears. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReactionsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: YarnDatabase
    private val friend = "+919812345678"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, YarnDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private suspend fun add(body: String, outgoing: Boolean, minute: Long, attachments: Boolean = false): Long =
        db.messages().insert(
            MessageEntity(
                conversationId = 1, address = friend, body = body, date = minute * 60_000, outgoing = outgoing,
                status = if (outgoing) MessageStatus.DELIVERED else MessageStatus.RECEIVED, hasAttachments = attachments,
            ),
        )

    private suspend fun state() = Reactions.resolve(db.messages(), 1, db.messages().reactionTexts(1).first())

    @Test
    fun reactionsLandOnTheirMessages() = runTest {
        db.conversations().upsert(ConversationEntity(id = 1, addresses = listOf(friend), displayName = "Karan"))
        val plan = add("Perfect, see you at 7", outgoing = true, minute = 1)
        val tickets = add("I'll book the tickets 🎬", outgoing = true, minute = 2)
        val photo = add("", outgoing = true, minute = 3, attachments = true)
        val popcorn = add("You're the best!! Popcorn on me 🍿", outgoing = false, minute = 4)
        val liked = add("Liked “Perfect, see you at 7”", outgoing = false, minute = 5)
        val loved = add("Loved “I'll book the tickets 🎬”", outgoing = false, minute = 6)
        val lovedPhoto = add("Loved an image", outgoing = false, minute = 7)
        // From you, sent earlier by Google Messages: it shows as your reaction.
        val mine = add("Laughed at “You're the best!! Popcorn on me 🍿”", outgoing = true, minute = 8)
        // A changed mind: the heart on the tickets becomes a laugh, and the like is taken back.
        val laughed = add("Reacted 😂 to “I'll book the tickets 🎬”", outgoing = false, minute = 9)
        val removed = add("Removed a like from “Perfect, see you at 7”", outgoing = false, minute = 10)
        // Nothing to attach it to: it stays a normal message.
        val orphan = add("Loved “a message Yarn never saw”", outgoing = false, minute = 11)

        val s = state()
        assertThat(s.byMessage[plan]).isNull()
        assertThat(s.byMessage[tickets]).containsExactly(Reactions.Shown("😂", friend))
        assertThat(s.byMessage[photo]).containsExactly(Reactions.Shown("❤️", friend))
        assertThat(s.byMessage[popcorn]).containsExactly(Reactions.Shown("😂", null))
        assertThat(s.hidden).containsExactly(liked, loved, lovedPhoto, mine, laughed, removed)
        assertThat(s.hidden).doesNotContain(orphan)
    }

    @Test
    fun aReactionNeverPointsForward() = runTest {
        db.conversations().upsert(ConversationEntity(id = 1, addresses = listOf(friend), displayName = "Karan"))
        // The same words sent again after the reaction: the reaction belongs to the earlier one.
        val first = add("ok", outgoing = true, minute = 1)
        add("Liked “ok”", outgoing = false, minute = 2)
        add("ok", outgoing = true, minute = 3)
        assertThat(state().byMessage.keys).containsExactly(first)
    }

    @Test
    fun describedForTheInboxAndNotifications() {
        assertThat(Reactions.describe(context, "Loved “see you at 8”")).isEqualTo("Reacted ❤️ to “see you at 8”")
        assertThat(Reactions.describe(context, "Laughed at an image")).isEqualTo("Reacted 😂 to an attachment")
        assertThat(Reactions.describe(context, "Removed a heart from “see you at 8”")).isEqualTo("Removed ❤️ from “see you at 8”")
        assertThat(Reactions.describe(context, "Loved the movie")).isNull()
    }
}
