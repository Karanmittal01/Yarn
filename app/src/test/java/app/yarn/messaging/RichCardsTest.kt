package app.yarn.messaging

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RichCardsTest {
    private val card = """
        {"message":{"generalPurposeCard":{"layout":{"cardOrientation":"VERTICAL"},"content":{
          "media":{"mediaUrl":"https://example.com/banner.jpg","height":"MEDIUM_HEIGHT"},
          "title":"Admissions open for 2027",
          "description":"Apply for the PGDM programme by 15 December.",
          "suggestions":[{"action":{"text":"Apply now","postback":{"data":"apply"},"openUrlAction":{"url":"https://example.com"}}},
                         {"reply":{"text":"Call us","postback":{"data":"call"}}}]}}}}
    """.trimIndent()

    @Test
    fun aCardShowsItsWordsNotItsButtonsOrLinks() {
        val text = RichCards.text("application/vnd.gsma.botmessage.v1.0+json", card)
        assertThat(text).isEqualTo("Admissions open for 2027\nApply for the PGDM programme by 15 December.")
    }

    @Test
    fun onlyCardsAreRead() {
        assertThat(RichCards.isCard("application/vnd.gsma.botsuggestion.v1.0+json")).isTrue()
        assertThat(RichCards.isCard("image/jpeg")).isFalse()
        assertThat(RichCards.text("image/jpeg", card)).isNull()
        assertThat(RichCards.text("application/json", "not json")).isNull()
        assertThat(RichCards.text("application/json", """{"text":"Your bill of Rs 349 is due on 30 Nov"}""")).isEqualTo("Your bill of Rs 349 is due on 30 Nov")
    }
}
