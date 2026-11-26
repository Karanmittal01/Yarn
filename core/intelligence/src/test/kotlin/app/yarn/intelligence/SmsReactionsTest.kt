package app.yarn.intelligence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsReactionsTest {
    @Test
    fun iPhoneTapbacks() {
        assertThat(SmsReactions.parse("Loved “see you at 8”")).isEqualTo(SmsReactions.Reaction("❤️", "see you at 8"))
        assertThat(SmsReactions.parse("Liked “Ok done”")!!.emoji).isEqualTo("👍")
        assertThat(SmsReactions.parse("Disliked “Meeting moved to 4”")!!.emoji).isEqualTo("👎")
        assertThat(SmsReactions.parse("Laughed at “bhai 😂 kya scene hai”")).isEqualTo(SmsReactions.Reaction("😂", "bhai 😂 kya scene hai"))
        assertThat(SmsReactions.parse("Emphasized “Call me when you reach”")!!.emoji).isEqualTo("‼️")
        assertThat(SmsReactions.parse("Questioned “Dinner at 9?”")!!.emoji).isEqualTo("❓")
        // A quote that itself holds quotes, and one over several lines.
        assertThat(SmsReactions.parse("Loved “He said “yes”!”")!!.quote).isEqualTo("He said “yes”!")
        assertThat(SmsReactions.parse("Liked “Line one\nLine two”")!!.quote).isEqualTo("Line one\nLine two")
    }

    @Test
    fun photosAndCustomEmoji() {
        assertThat(SmsReactions.parse("Loved an image")).isEqualTo(SmsReactions.Reaction("❤️", null))
        assertThat(SmsReactions.parse("Laughed at a video")!!.media).isTrue()
        assertThat(SmsReactions.parse("Reacted 🔥 to “new bike!”")).isEqualTo(SmsReactions.Reaction("🔥", "new bike!"))
        assertThat(SmsReactions.parse("Reacted 👍🏽 to an image")).isEqualTo(SmsReactions.Reaction("👍🏽", null))
        // Straight quotes, as some Android apps send them.
        assertThat(SmsReactions.parse("Liked \"see you\"")!!.quote).isEqualTo("see you")
    }

    @Test
    fun takingAReactionBack() {
        assertThat(SmsReactions.parse("Removed a heart from “see you at 8”")).isEqualTo(SmsReactions.Reaction("❤️", "see you at 8", removed = true))
        assertThat(SmsReactions.parse("Removed a like from an image")).isEqualTo(SmsReactions.Reaction("👍", null, removed = true))
        assertThat(SmsReactions.parse("Removed a question mark from “Dinner?”")!!.emoji).isEqualTo("❓")
        assertThat(SmsReactions.parse("Removed 🔥 from “new bike!”")).isEqualTo(SmsReactions.Reaction("🔥", "new bike!", removed = true))
    }

    @Test
    fun ordinaryMessagesAreNotReactions() {
        for (text in listOf(
            "Loved “the movie” last night!", "Liked the food there", "I liked “Animal” more than Jawan", "Reacted badly to the medicine",
            "Removed the file from “Downloads”", "Removed from “Family” group", "Loved it", "Questioned by police today",
            "Liked", "Your order was liked by 3 people", "Loved the movie", "Liked the photo", "Laughed at the video",
        )) assertThat(SmsReactions.parse(text)).isNull()
    }

    @Test
    fun matchingTheMessage() {
        assertThat(SmsReactions.matches("see you at 8", "see you at 8")).isTrue()
        assertThat(SmsReactions.matches("Don’t be late", "Don't  be late")).isTrue()
        assertThat(SmsReactions.matches("Reached the station, waiting near gate…", "Reached the station, waiting near gate 3 with the bags")).isTrue()
        assertThat(SmsReactions.matches("ok", "ok sure")).isFalse()
        assertThat(SmsReactions.matches("see you at 8", "see you at 9")).isFalse()
    }
}
