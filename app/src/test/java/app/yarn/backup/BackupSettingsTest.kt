package app.yarn.backup

import app.yarn.data.prefs.AppSettings
import app.yarn.data.prefs.NotifyMode
import app.yarn.data.prefs.SwipeAction
import app.yarn.data.prefs.ThemeMode
import app.yarn.intelligence.CategoryGroup
import app.yarn.intelligence.SpamSensitivity
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class BackupSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val mine = AppSettings(
        theme = ThemeMode.DARK, dynamicColor = true, textScale = 1.15f, sendDelaySeconds = 5,
        notifyTabs = mapOf(CategoryGroup.OFFERS to NotifyMode.OFF, CategoryGroup.OTP to NotifyMode.SOUND),
        aiEnabled = true, autoCategorize = false, spamSensitivity = SpamSensitivity.HIGH, smartReplies = false,
        translateTarget = "hi", onlineTranslation = false, appLock = true, lockTimeoutSeconds = 300, secureScreen = true,
        binDays = 30, otpAutoDeleteMinutes = 240, promoAutoDeleteDays = 14, swipeStart = SwipeAction.DELETE, swipeEnd = SwipeAction.PIN,
        backupMedia = false, profileName = "Asha", googleAccount = "old@example.com", lastBackupAt = 123, initialImportDone = true,
    )

    @Test
    fun everySettingComesBackOnANewPhone() {
        val carried = json.decodeFromString(BackupSettings.serializer(), json.encodeToString(BackupSettings.serializer(), BackupSettings.of(mine, "hi")))
        val newPhone = AppSettings(googleAccount = "new@example.com")
        val restored = carried.applyTo(newPhone)
        assertThat(carried.language).isEqualTo("hi")
        assertThat(restored.theme).isEqualTo(ThemeMode.DARK)
        assertThat(restored.dynamicColor).isTrue()
        assertThat(restored.textScale).isEqualTo(1.15f)
        assertThat(restored.sendDelaySeconds).isEqualTo(5)
        assertThat(restored.notifyTabs).isEqualTo(mine.notifyTabs)
        assertThat(restored.autoCategorize).isFalse()
        assertThat(restored.spamSensitivity).isEqualTo(SpamSensitivity.HIGH)
        assertThat(restored.smartReplies).isFalse()
        assertThat(restored.translateTarget).isEqualTo("hi")
        assertThat(restored.onlineTranslation).isFalse()
        assertThat(restored.appLock).isTrue()
        assertThat(restored.lockTimeoutSeconds).isEqualTo(300)
        assertThat(restored.secureScreen).isTrue()
        assertThat(restored.binDays).isEqualTo(30)
        assertThat(restored.otpAutoDeleteMinutes).isEqualTo(240)
        assertThat(restored.promoAutoDeleteDays).isEqualTo(14)
        assertThat(restored.swipeStart).isEqualTo(SwipeAction.DELETE)
        assertThat(restored.swipeEnd).isEqualTo(SwipeAction.PIN)
        assertThat(restored.backupMedia).isFalse()
    }

    @Test
    fun theNewPhonesOwnStateStays() {
        val restored = BackupSettings.of(mine, "").applyTo(AppSettings(googleAccount = "new@example.com", initialImportDone = false))
        assertThat(restored.googleAccount).isEqualTo("new@example.com")
        assertThat(restored.lastBackupAt).isEqualTo(0)
        assertThat(restored.initialImportDone).isFalse()
        // Name and picture travel separately, with the photo file.
        assertThat(restored.profileName).isEmpty()
    }

    @Test
    fun anOlderOrNewerBackupRestoresWhatItHas() {
        val partial = json.decodeFromString(BackupSettings.serializer(), """{"theme":"LIGHT","swipeEnd":"SOMETHING_NEW","futureSetting":true}""")
        val restored = partial.applyTo(mine)
        assertThat(restored.theme).isEqualTo(ThemeMode.LIGHT)
        assertThat(restored.swipeEnd).isEqualTo(SwipeAction.PIN)
        assertThat(restored.appLock).isTrue()
    }

    @Test
    fun aVersionOneBackupStillReads() {
        val v1 = """{"version":1,"createdAt":1,"messageCount":2,"conversations":[{"addresses":["+919800000000"],"customTitle":null,"pinned":true,
            "archived":false,"muted":false,"starred":false,"category":"PERSONAL","categoryLocked":false}],"corrections":[],"rules":[],"blocks":[]}"""
        val meta = json.decodeFromString(BackupMeta.serializer(), v1)
        assertThat(meta.settings).isNull()
        assertThat(meta.bin).isEmpty()
        assertThat(meta.profilePhoto).isNull()
        assertThat(meta.conversations.single().draft).isNull()
        assertThat(meta.conversations.single().preferredSubId).isEqualTo(-1)
    }
}
