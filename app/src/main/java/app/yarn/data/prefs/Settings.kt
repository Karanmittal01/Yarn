package app.yarn.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.yarn.intelligence.SpamSensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SwipeAction { NONE, ARCHIVE, DELETE, READ, PIN }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val deliveryReports: Boolean = false,
    val groupMms: Boolean = true,
    val autoDownloadMms: Boolean = true,
    val autoDownloadMmsRoaming: Boolean = false,
    val sendDelaySeconds: Int = 0,
    val showNotificationPreviews: Boolean = true,
    val notifyPromotions: Boolean = false,
    val notifySpam: Boolean = false,
    val otpCopyAction: Boolean = true,
    val aiEnabled: Boolean = true,
    val autoCategorize: Boolean = true,
    val spamFilter: Boolean = true,
    val spamSensitivity: SpamSensitivity = SpamSensitivity.MEDIUM,
    val smartReplies: Boolean = true,
    val mlEntityExtraction: Boolean = true,
    val learningEnabled: Boolean = true,
    val translateTarget: String = Locale.getDefault().language,
    val localLlmPath: String? = null,
    val selfHostedAiEnabled: Boolean = false,
    val selfHostedAiUrl: String = "",
    val selfHostedAiModel: String = "",
    val appLock: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val secureScreen: Boolean = false,
    val otpAutoDeleteHours: Int = 0,
    val spamAutoDeleteDays: Int = 30,
    val swipeStart: SwipeAction = SwipeAction.ARCHIVE,
    val swipeEnd: SwipeAction = SwipeAction.READ,
    val dayFirstDates: Boolean = Locale.getDefault().country !in setOf("US", "CA", "PH", "BZ", "FM"),
    val initialImportDone: Boolean = false,
    /** Google account used for Drive backups, or null when backup is off. */
    val googleAccount: String? = null,
    val autoBackup: Boolean = true,
    val backupMedia: Boolean = true,
    val lastBackupAt: Long = 0,
    val lastBackupBytes: Long = 0,
    /** Version of the sorting rules the stored categories were computed with. */
    val rulesVersion: Int = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val deliveryReports = booleanPreferencesKey("delivery_reports")
        val groupMms = booleanPreferencesKey("group_mms")
        val autoDownloadMms = booleanPreferencesKey("auto_download_mms")
        val autoDownloadMmsRoaming = booleanPreferencesKey("auto_download_mms_roaming")
        val sendDelay = intPreferencesKey("send_delay")
        val previews = booleanPreferencesKey("notification_previews")
        val notifyPromotions = booleanPreferencesKey("notify_promotions")
        val notifySpam = booleanPreferencesKey("notify_spam")
        val otpCopy = booleanPreferencesKey("otp_copy")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val autoCategorize = booleanPreferencesKey("auto_categorize")
        val spamFilter = booleanPreferencesKey("spam_filter")
        val spamSensitivity = stringPreferencesKey("spam_sensitivity")
        val smartReplies = booleanPreferencesKey("smart_replies")
        val mlEntities = booleanPreferencesKey("ml_entities")
        val learning = booleanPreferencesKey("learning")
        val translateTarget = stringPreferencesKey("translate_target")
        val localLlmPath = stringPreferencesKey("local_llm_path")
        val selfHostedEnabled = booleanPreferencesKey("self_hosted_ai")
        val selfHostedUrl = stringPreferencesKey("self_hosted_ai_url")
        val selfHostedModel = stringPreferencesKey("self_hosted_ai_model")
        val appLock = booleanPreferencesKey("app_lock")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val secureScreen = booleanPreferencesKey("secure_screen")
        val otpAutoDelete = intPreferencesKey("otp_auto_delete_hours")
        val spamAutoDelete = intPreferencesKey("spam_auto_delete_days")
        val swipeStart = stringPreferencesKey("swipe_start")
        val swipeEnd = stringPreferencesKey("swipe_end")
        val dayFirst = booleanPreferencesKey("day_first")
        val importDone = booleanPreferencesKey("initial_import_done")
        val googleAccount = stringPreferencesKey("google_account")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val backupMedia = booleanPreferencesKey("backup_media")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val lastBackupBytes = longPreferencesKey("last_backup_bytes")
        val rulesVersion = intPreferencesKey("rules_version")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { readFrom(it, AppSettings()) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val old = readFrom(p, AppSettings())
            val s = transform(old)
            p[K.onboardingDone] = s.onboardingDone
            p[K.theme] = s.theme.name
            p[K.dynamicColor] = s.dynamicColor
            p[K.deliveryReports] = s.deliveryReports
            p[K.groupMms] = s.groupMms
            p[K.autoDownloadMms] = s.autoDownloadMms
            p[K.autoDownloadMmsRoaming] = s.autoDownloadMmsRoaming
            p[K.sendDelay] = s.sendDelaySeconds
            p[K.previews] = s.showNotificationPreviews
            p[K.notifyPromotions] = s.notifyPromotions
            p[K.notifySpam] = s.notifySpam
            p[K.otpCopy] = s.otpCopyAction
            p[K.aiEnabled] = s.aiEnabled
            p[K.autoCategorize] = s.autoCategorize
            p[K.spamFilter] = s.spamFilter
            p[K.spamSensitivity] = s.spamSensitivity.name
            p[K.smartReplies] = s.smartReplies
            p[K.mlEntities] = s.mlEntityExtraction
            p[K.learning] = s.learningEnabled
            p[K.translateTarget] = s.translateTarget
            if (s.localLlmPath != null) p[K.localLlmPath] = s.localLlmPath else p.remove(K.localLlmPath)
            p[K.selfHostedEnabled] = s.selfHostedAiEnabled
            p[K.selfHostedUrl] = s.selfHostedAiUrl
            p[K.selfHostedModel] = s.selfHostedAiModel
            p[K.appLock] = s.appLock
            p[K.lockTimeout] = s.lockTimeoutSeconds
            p[K.secureScreen] = s.secureScreen
            p[K.otpAutoDelete] = s.otpAutoDeleteHours
            p[K.spamAutoDelete] = s.spamAutoDeleteDays
            p[K.swipeStart] = s.swipeStart.name
            p[K.swipeEnd] = s.swipeEnd.name
            p[K.dayFirst] = s.dayFirstDates
            p[K.importDone] = s.initialImportDone
            if (s.googleAccount != null) p[K.googleAccount] = s.googleAccount else p.remove(K.googleAccount)
            p[K.autoBackup] = s.autoBackup
            p[K.backupMedia] = s.backupMedia
            p[K.lastBackupAt] = s.lastBackupAt
            p[K.lastBackupBytes] = s.lastBackupBytes
            p[K.rulesVersion] = s.rulesVersion
        }
    }

    private fun readFrom(p: Preferences, d: AppSettings): AppSettings = AppSettings(
        onboardingDone = p[K.onboardingDone] ?: d.onboardingDone,
        theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
        dynamicColor = p[K.dynamicColor] ?: d.dynamicColor,
        deliveryReports = p[K.deliveryReports] ?: d.deliveryReports,
        groupMms = p[K.groupMms] ?: d.groupMms,
        autoDownloadMms = p[K.autoDownloadMms] ?: d.autoDownloadMms,
        autoDownloadMmsRoaming = p[K.autoDownloadMmsRoaming] ?: d.autoDownloadMmsRoaming,
        sendDelaySeconds = p[K.sendDelay] ?: d.sendDelaySeconds,
        showNotificationPreviews = p[K.previews] ?: d.showNotificationPreviews,
        notifyPromotions = p[K.notifyPromotions] ?: d.notifyPromotions,
        notifySpam = p[K.notifySpam] ?: d.notifySpam,
        otpCopyAction = p[K.otpCopy] ?: d.otpCopyAction,
        aiEnabled = p[K.aiEnabled] ?: d.aiEnabled,
        autoCategorize = p[K.autoCategorize] ?: d.autoCategorize,
        spamFilter = p[K.spamFilter] ?: d.spamFilter,
        spamSensitivity = p[K.spamSensitivity]?.let { runCatching { SpamSensitivity.valueOf(it) }.getOrNull() } ?: d.spamSensitivity,
        smartReplies = p[K.smartReplies] ?: d.smartReplies,
        mlEntityExtraction = p[K.mlEntities] ?: d.mlEntityExtraction,
        learningEnabled = p[K.learning] ?: d.learningEnabled,
        translateTarget = p[K.translateTarget] ?: d.translateTarget,
        localLlmPath = p[K.localLlmPath],
        selfHostedAiEnabled = p[K.selfHostedEnabled] ?: d.selfHostedAiEnabled,
        selfHostedAiUrl = p[K.selfHostedUrl] ?: d.selfHostedAiUrl,
        selfHostedAiModel = p[K.selfHostedModel] ?: d.selfHostedAiModel,
        appLock = p[K.appLock] ?: d.appLock,
        lockTimeoutSeconds = p[K.lockTimeout] ?: d.lockTimeoutSeconds,
        secureScreen = p[K.secureScreen] ?: d.secureScreen,
        otpAutoDeleteHours = p[K.otpAutoDelete] ?: d.otpAutoDeleteHours,
        spamAutoDeleteDays = p[K.spamAutoDelete] ?: d.spamAutoDeleteDays,
        swipeStart = p[K.swipeStart]?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() } ?: d.swipeStart,
        swipeEnd = p[K.swipeEnd]?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() } ?: d.swipeEnd,
        dayFirstDates = p[K.dayFirst] ?: d.dayFirstDates,
        initialImportDone = p[K.importDone] ?: d.initialImportDone,
        googleAccount = p[K.googleAccount],
        autoBackup = p[K.autoBackup] ?: d.autoBackup,
        backupMedia = p[K.backupMedia] ?: d.backupMedia,
        lastBackupAt = p[K.lastBackupAt] ?: d.lastBackupAt,
        lastBackupBytes = p[K.lastBackupBytes] ?: d.lastBackupBytes,
        rulesVersion = p[K.rulesVersion] ?: d.rulesVersion,
    )
}
