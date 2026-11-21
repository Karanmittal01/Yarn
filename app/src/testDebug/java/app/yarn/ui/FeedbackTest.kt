package app.yarn.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yarn.data.prefs.AppSettings
import app.yarn.data.prefs.sortingOn
import app.yarn.ui.common.Feedback
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FeedbackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aLogoRequestCarriesOnlyTheSendersNameAndId() {
        val note = Feedback.logoRequest(context, "MYTNEU", "vm-mytneu-p")
        assertThat(note.text).contains("MYTNEU")
        assertThat(note.text).contains("VM-MYTNEU-P")
        assertThat(note.subject).contains("MYTNEU")
        assertThat(note.log).isNull()
    }

    @Test
    fun feedbackSaysWhichYarnAndPhone() {
        val note = Feedback.feedback(context, "crash log")
        assertThat(note.text).contains("Yarn ")
        assertThat(note.text).contains("Android ")
        assertThat(note.log).isEqualTo("crash log")
        assertThat(Feedback.feedback(context, " ").log).isNull()
    }

    @Test
    fun feedbackGoesToTheDevelopersWhatsAppAndEmail() {
        assertThat(Feedback.whatsAppNumber(context)).isEqualTo("919999422626")
        assertThat(Feedback.emailAddress(context)).isEqualTo("karanmittal2626@gmail.com")
    }

    @Test
    fun aNewInstallHasEverySmartFeatureOn() {
        val s = AppSettings()
        assertThat(s.aiEnabled).isTrue()
        assertThat(s.autoCategorize).isTrue()
        assertThat(s.sortingOn).isTrue()
        assertThat(s.smartReplies).isTrue()
        assertThat(s.mlEntityExtraction).isTrue()
        assertThat(s.learningEnabled).isTrue()
        assertThat(s.onlineTranslation).isTrue()
        assertThat(s.spamSensitivity).isEqualTo(app.yarn.intelligence.SpamSensitivity.MEDIUM)
    }

    @Test
    fun sortingNeedsBothSwitches() {
        assertThat(AppSettings(autoCategorize = false).sortingOn).isFalse()
        assertThat(AppSettings(aiEnabled = false).sortingOn).isFalse()
    }
}
