package app.yarn.data.repo

import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.AppSettings
import app.yarn.data.prefs.SettingsRepository

/** Kinds of clutter the junk cleaner can clear, with how old a message must be to count. */
enum class JunkKind(val categories: List<String>, val minAgeMs: Long, val selectedByDefault: Boolean) {
    /** Codes stop working within minutes, so anything older than 10 minutes is clutter. */
    CODES(listOf("OTP"), 10 * 60_000L, true),
    OFFERS(listOf("PROMOTIONS"), 7 * DAY, true),
    SPAM(emptyList(), 0, true),
    /** Order and delivery updates, once the parcel has long arrived. Off by default. */
    ORDERS(listOf("SHOPPING", "DELIVERY"), 30 * DAY, false),
}

private const val DAY = 86_400_000L

/**
 * Finds and clears junk: old one-time codes, expired offers, spam and stale order updates.
 * Starred messages, pinned or starred chats and chats with replies are always kept.
 */
class Cleaner(
    private val db: YarnDatabase,
    private val conversations: ConversationRepository,
    private val settings: SettingsRepository,
) {
    suspend fun scan(now: Long = System.currentTimeMillis()): Map<JunkKind, List<Long>> =
        JunkKind.entries.associateWith { ids(it, now - it.minAgeMs) }

    private suspend fun ids(kind: JunkKind, before: Long): List<Long> =
        if (kind == JunkKind.SPAM) db.messages().spamOlderThan(before) else db.messages().junkIds(kind.categories, before)

    suspend fun clean(ids: Collection<Long>, onProgress: (Int) -> Unit = {}): Int {
        if (ids.isEmpty()) return 0
        conversations.deleteMessages(ids.distinct(), onProgress)
        return ids.size
    }

    /** Applies the user's auto-clean choices; returns how many messages were removed. */
    suspend fun autoClean(settingsNow: AppSettings? = null, now: Long = System.currentTimeMillis()): Int {
        val s = settingsNow ?: settings.current()
        val ids = buildSet {
            if (s.otpAutoDeleteMinutes > 0) addAll(ids(JunkKind.CODES, now - s.otpAutoDeleteMinutes * 60_000L))
            if (s.promoAutoDeleteDays > 0) addAll(ids(JunkKind.OFFERS, now - s.promoAutoDeleteDays * DAY))
            if (s.spamAutoDeleteDays > 0) addAll(ids(JunkKind.SPAM, now - s.spamAutoDeleteDays * DAY))
        }
        val removed = clean(ids)
        // The first clean-up after setup is announced once in the inbox.
        if (removed > 0) settings.update { if (it.autoCleanReport >= 0) it.copy(autoCleanReport = it.autoCleanReport + removed) else it }
        return removed
    }

    companion object {
        fun isAutoCleanOn(s: AppSettings) = s.otpAutoDeleteMinutes > 0 || s.promoAutoDeleteDays > 0
    }
}
