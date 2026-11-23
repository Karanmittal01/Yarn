package app.yarn.backup

import app.yarn.data.prefs.AppSettings
import app.yarn.data.prefs.NotifyMode
import app.yarn.intelligence.CategoryGroup
import kotlinx.serialization.Serializable

/**
 * Yarn's settings as they travel in a backup. Every field is optional: a backup from an older or
 * newer Yarn restores what it has and leaves the rest as it is. Things that belong to one phone
 * (the backup account, a downloaded model's path, what's already been imported) stay out.
 */
@Serializable
data class BackupSettings(
    val language: String? = null,
    val theme: String? = null,
    val dynamicColor: Boolean? = null,
    val accent: String? = null,
    val chatWallpaper: String? = null,
    val textScale: Float? = null,
    val deliveryReports: Boolean? = null,
    val groupMms: Boolean? = null,
    val autoDownloadMms: Boolean? = null,
    val autoDownloadMmsRoaming: Boolean? = null,
    val sendDelaySeconds: Int? = null,
    val showNotificationPreviews: Boolean? = null,
    val notifyTabs: Map<String, String>? = null,
    val notifySpam: Boolean? = null,
    val otpCopyAction: Boolean? = null,
    val aiEnabled: Boolean? = null,
    val autoCategorize: Boolean? = null,
    val spamSensitivity: String? = null,
    val smartReplies: Boolean? = null,
    val mlEntityExtraction: Boolean? = null,
    val learningEnabled: Boolean? = null,
    val translateTarget: String? = null,
    val onlineTranslation: Boolean? = null,
    val selfHostedAiEnabled: Boolean? = null,
    val selfHostedAiUrl: String? = null,
    val selfHostedAiModel: String? = null,
    val appLock: Boolean? = null,
    val lockTimeoutSeconds: Int? = null,
    val secureScreen: Boolean? = null,
    val binDays: Int? = null,
    val otpAutoDeleteMinutes: Int? = null,
    val spamAutoDeleteDays: Int? = null,
    val promoAutoDeleteDays: Int? = null,
    val swipeStart: String? = null,
    val swipeEnd: String? = null,
    val dayFirstDates: Boolean? = null,
    val autoBackup: Boolean? = null,
    val backupMedia: Boolean? = null,
) {
    fun applyTo(s: AppSettings): AppSettings = s.copy(
        theme = enumOr(theme, s.theme),
        dynamicColor = dynamicColor ?: s.dynamicColor,
        accent = accent ?: s.accent,
        chatWallpaper = chatWallpaper ?: s.chatWallpaper,
        textScale = textScale ?: s.textScale,
        deliveryReports = deliveryReports ?: s.deliveryReports,
        groupMms = groupMms ?: s.groupMms,
        autoDownloadMms = autoDownloadMms ?: s.autoDownloadMms,
        autoDownloadMmsRoaming = autoDownloadMmsRoaming ?: s.autoDownloadMmsRoaming,
        sendDelaySeconds = sendDelaySeconds ?: s.sendDelaySeconds,
        showNotificationPreviews = showNotificationPreviews ?: s.showNotificationPreviews,
        notifyTabs = notifyTabs?.mapNotNull { (group, mode) ->
            val g = enumOrNull<CategoryGroup>(group)
            val m = enumOrNull<NotifyMode>(mode)
            if (g != null && m != null) g to m else null
        }?.toMap()?.takeIf { it.isNotEmpty() } ?: s.notifyTabs,
        notifySpam = notifySpam ?: s.notifySpam,
        otpCopyAction = otpCopyAction ?: s.otpCopyAction,
        aiEnabled = aiEnabled ?: s.aiEnabled,
        autoCategorize = autoCategorize ?: s.autoCategorize,
        spamSensitivity = enumOr(spamSensitivity, s.spamSensitivity),
        smartReplies = smartReplies ?: s.smartReplies,
        mlEntityExtraction = mlEntityExtraction ?: s.mlEntityExtraction,
        learningEnabled = learningEnabled ?: s.learningEnabled,
        translateTarget = translateTarget ?: s.translateTarget,
        onlineTranslation = onlineTranslation ?: s.onlineTranslation,
        selfHostedAiEnabled = selfHostedAiEnabled ?: s.selfHostedAiEnabled,
        selfHostedAiUrl = selfHostedAiUrl ?: s.selfHostedAiUrl,
        selfHostedAiModel = selfHostedAiModel ?: s.selfHostedAiModel,
        appLock = appLock ?: s.appLock,
        lockTimeoutSeconds = lockTimeoutSeconds ?: s.lockTimeoutSeconds,
        secureScreen = secureScreen ?: s.secureScreen,
        binDays = binDays ?: s.binDays,
        otpAutoDeleteMinutes = otpAutoDeleteMinutes ?: s.otpAutoDeleteMinutes,
        spamAutoDeleteDays = spamAutoDeleteDays ?: s.spamAutoDeleteDays,
        promoAutoDeleteDays = promoAutoDeleteDays ?: s.promoAutoDeleteDays,
        swipeStart = enumOr(swipeStart, s.swipeStart),
        swipeEnd = enumOr(swipeEnd, s.swipeEnd),
        dayFirstDates = dayFirstDates ?: s.dayFirstDates,
        autoBackup = autoBackup ?: s.autoBackup,
        backupMedia = backupMedia ?: s.backupMedia,
    )

    companion object {
        fun of(s: AppSettings, language: String): BackupSettings = BackupSettings(
            language = language,
            theme = s.theme.name,
            dynamicColor = s.dynamicColor,
            accent = s.accent,
            chatWallpaper = s.chatWallpaper,
            textScale = s.textScale,
            deliveryReports = s.deliveryReports,
            groupMms = s.groupMms,
            autoDownloadMms = s.autoDownloadMms,
            autoDownloadMmsRoaming = s.autoDownloadMmsRoaming,
            sendDelaySeconds = s.sendDelaySeconds,
            showNotificationPreviews = s.showNotificationPreviews,
            notifyTabs = s.notifyTabs.map { (g, m) -> g.name to m.name }.toMap(),
            notifySpam = s.notifySpam,
            otpCopyAction = s.otpCopyAction,
            aiEnabled = s.aiEnabled,
            autoCategorize = s.autoCategorize,
            spamSensitivity = s.spamSensitivity.name,
            smartReplies = s.smartReplies,
            mlEntityExtraction = s.mlEntityExtraction,
            learningEnabled = s.learningEnabled,
            translateTarget = s.translateTarget,
            onlineTranslation = s.onlineTranslation,
            selfHostedAiEnabled = s.selfHostedAiEnabled,
            selfHostedAiUrl = s.selfHostedAiUrl,
            selfHostedAiModel = s.selfHostedAiModel,
            appLock = s.appLock,
            lockTimeoutSeconds = s.lockTimeoutSeconds,
            secureScreen = s.secureScreen,
            binDays = s.binDays,
            otpAutoDeleteMinutes = s.otpAutoDeleteMinutes,
            spamAutoDeleteDays = s.spamAutoDeleteDays,
            promoAutoDeleteDays = s.promoAutoDeleteDays,
            swipeStart = s.swipeStart.name,
            swipeEnd = s.swipeEnd.name,
            dayFirstDates = s.dayFirstDates,
            autoBackup = s.autoBackup,
            backupMedia = s.backupMedia,
        )
    }
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E = enumOrNull<E>(name) ?: fallback

/** Something deleted, kept in the recycle bin, carried along so it can still be restored. */
@Serializable
data class BackupBin(
    val addresses: List<String>, val title: String, val kind: Int, val address: String, val body: String, val subject: String? = null,
    val date: Long, val dateSent: Long = 0, val outgoing: Boolean, val status: Int, val subId: Int = -1, val category: String,
    val starred: Boolean = false, val deletedAt: Long, val media: List<BackupMedia> = emptyList(),
)
