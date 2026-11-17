package app.yarn.ui.conversation

import app.yarn.data.db.ConversationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessagesKeyTest {
    private val chat = ConversationEntity(
        id = 1, addresses = listOf("+919812345678"), snippet = "Are you coming?", lastMessageAt = 1_000, messageCount = 4, unreadCount = 1,
    )

    @Test fun savingTheDraftIsNotAChange() {
        // Typing saves the draft every pause, which moves lastMessageAt; smart replies and scrolling must not react.
        val typing = chat.copy(draft = "Yes, on my w", draftUpdatedAt = 5_000, lastMessageAt = 5_000)
        assertEquals(chat.messagesKey(), typing.messagesKey())
        assertEquals(chat.messagesKey(), typing.copy(draft = "Yes, on my way", draftUpdatedAt = 6_000, lastMessageAt = 6_000).messagesKey())
    }

    @Test fun messagesAreChanges() {
        assertNotEquals(chat.messagesKey(), chat.copy(messageCount = 5, snippet = "Hello?", lastMessageAt = 7_000).messagesKey())
        assertNotEquals(chat.messagesKey(), chat.copy(unreadCount = 0).messagesKey())
        assertNotEquals(chat.messagesKey(), chat.copy(snippet = "Yes", snippetFromMe = true, messageCount = 5).messagesKey())
    }
}
