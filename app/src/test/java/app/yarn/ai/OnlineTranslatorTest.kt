package app.yarn.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnlineTranslatorTest {
    @Test
    fun readsSentencesAndDetectedLanguage() {
        val body = """[[["Shall we meet tomorrow evening? ","Kal shaam ko milte hain?",null,null,10],["Let's go to a movie","Movie dekhne chalenge",null,null,10]],null,"hi",null,null,null,null,[]]"""
        val r = OnlineTranslator.parse(body)!!
        assertThat(r.text).isEqualTo("Shall we meet tomorrow evening? Let's go to a movie")
        assertThat(r.source).isEqualTo("hi")
    }

    @Test
    fun skipsTransliterationRowsAndBadInput() {
        // Google sometimes adds a row with only the romanised reading; it carries no translation.
        val body = """[[["Hello","नमस्ते",null,null,1],[null,null,"Namaste"]],null,"hi"]"""
        assertThat(OnlineTranslator.parse(body)!!.text).isEqualTo("Hello")
        assertThat(OnlineTranslator.parse("<html>Sorry...</html>")).isNull()
        assertThat(OnlineTranslator.parse("[[],null,\"en\"]")).isNull()
    }
}
