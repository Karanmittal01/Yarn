package app.yarn.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yarn.data.prefs.AppSettings
import app.yarn.data.prefs.sortingOn
import app.yarn.ui.common.BrandLogos
import app.yarn.ui.common.Feedback
import app.yarn.ui.common.LogoRequests
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
        val text = LogoRequests.text("Apollo Pharmacy", "cp-aplphr-s")
        assertThat(text).startsWith("Yarn logo request: Apollo Pharmacy (CP-APLPHR-S).")
        assertThat(text).endsWith("Yarn ${app.yarn.BuildConfig.VERSION_CODE}")
        // Plain ASCII, so it goes as one ordinary text.
        assertThat(text.all { it.code < 128 }).isTrue()
        assertThat(text.length).isAtMost(160)
        assertThat(LogoRequests.text("APLPHR", "CP-APLPHR-S")).startsWith("Yarn logo request: CP-APLPHR-S.")
    }

    @Test
    fun aSenderIsRequestedOnceWhateverItsPrefixOrSuffix() {
        assertThat(LogoRequests.key("CP-APLPHR-S")).isEqualTo("APLPHR")
        assertThat(LogoRequests.key("jd-aplphr")).isEqualTo("APLPHR")
        assertThat(LogoRequests.key("APLPHR")).isEqualTo("APLPHR")
    }

    @Test
    fun onlyLetterSenderIdsWithoutALogoCanBeRequested() {
        assertThat(BrandLogos.isMissing(context, "VM-NEWBRD-S")).isTrue()
        assertThat(BrandLogos.isMissing(context, "CP-APLPHR-S")).isFalse()
        assertThat(BrandLogos.isMissing(context, "59039149")).isFalse()
        assertThat(BrandLogos.isMissing(context, "+919812345678")).isFalse()
        assertThat(BrandLogos.isMissing(context, null)).isFalse()
    }

    @Test
    fun logosAreHiddenWithoutPro() {
        try {
            BrandLogos.enabled = false
            assertThat(BrandLogos.uri(context, "CP-APLPHR-S")).isNull()
            assertThat(BrandLogos.find(context, "CP-APLPHR-S")).isEqualTo("logo:apollopharmacy")
        } finally {
            BrandLogos.enabled = true
        }
        assertThat(BrandLogos.uri(context, "CP-APLPHR-S")).isEqualTo("logo:apollopharmacy")
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
