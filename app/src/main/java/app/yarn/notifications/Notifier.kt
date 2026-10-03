package app.yarn.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.yarn.MainActivity
import app.yarn.R
import app.yarn.data.contacts.ContactsRepository
import app.yarn.data.db.ConversationEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.SettingsRepository
import app.yarn.intelligence.Category
import app.yarn.telephony.PhoneNumbers
import app.yarn.telephony.SimManager

class Notifier(
    private val context: Context,
    private val db: YarnDatabase,
    private val contacts: ContactsRepository,
    private val settings: SettingsRepository,
    private val sims: SimManager,
) {
    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CH_MESSAGES, context.getString(R.string.ch_messages), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.ch_messages_desc)
            },
            NotificationChannel(CH_OTP, context.getString(R.string.ch_otp), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.ch_otp_desc)
            },
            NotificationChannel(CH_QUIET, context.getString(R.string.ch_quiet), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.ch_quiet_desc)
            },
            NotificationChannel(CH_SPAM, context.getString(R.string.ch_spam), NotificationManager.IMPORTANCE_MIN).apply {
                description = context.getString(R.string.ch_spam_desc)
            },
            NotificationChannel(CH_FAILED, context.getString(R.string.ch_failed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.ch_failed_desc)
            },
            NotificationChannel(CH_FLASH, context.getString(R.string.ch_flash), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.ch_flash_desc)
            },
            NotificationChannel(CH_BACKGROUND, context.getString(R.string.ch_background), NotificationManager.IMPORTANCE_MIN).apply {
                description = context.getString(R.string.ch_background_desc)
            },
        )
        manager.createNotificationChannels(channels)
    }

    private fun canPost() = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Every post goes through here; the permission is checked right before notifying. */
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: android.app.Notification) {
        if (!canPost()) return
        try {
            nm.notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun openConversationIntent(conversationId: Long, messageId: Long? = null): PendingIntent {
        val uri = Uri.parse("yarn://conversation/$conversationId" + (messageId?.let { "?message=$it" } ?: ""))
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, conversationId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(action: String, conversationId: Long, extras: Intent.() -> Unit = {}, mutable: Boolean = false): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("yarn://action/$action/$conversationId"))
            .putExtra(NotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
            .apply(extras)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (action.hashCode() * 31 + conversationId).toInt(), intent, flags)
    }

    private fun avatar(photoUri: String?, name: String): IconCompat {
        val bitmap: Bitmap? = photoUri?.let { uri ->
            runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
        return if (bitmap != null) IconCompat.createWithAdaptiveBitmap(bitmap) else IconCompat.createWithBitmap(Avatars.letterBitmap(name, 128))
    }

    private fun person(address: String, iso: String): Person {
        val contact = contacts.lookup(address)
        val name = contact?.name ?: PhoneNumbers.format(address, iso)
        return Person.Builder()
            .setName(name)
            .setKey(PhoneNumbers.matchKey(address))
            .setIcon(avatar(contact?.photoUri, name))
            .apply { contact?.lookupUri?.let { setUri(it.toString()) } }
            .setImportant(contact?.starred == true)
            .build()
    }

    /** Posts or refreshes the notification for a conversation with all its unread messages. */
    suspend fun notifyConversation(conversationId: Long, alert: Boolean = true) {
        if (!canPost()) return
        val conversation = db.conversations().get(conversationId) ?: return
        val unread = db.messages().unread(conversationId).takeLast(8)
        if (unread.isEmpty()) { cancel(conversationId); return }
        val s = settings.current()
        if (conversation.muted || conversation.blocked) return
        val latest = unread.last()
        val category = Category.fromName(latest.category) ?: Category.PERSONAL
        val channel = when {
            conversation.spam -> if (s.notifySpam) CH_SPAM else return
            category == Category.PROMOTIONS -> if (s.notifyPromotions) CH_QUIET else return
            category == Category.OTP -> CH_OTP
            category == Category.UPDATES || category == Category.SHOPPING -> if (latest.important) CH_MESSAGES else CH_QUIET
            else -> CH_MESSAGES
        }
        val iso = PhoneNumbers.countryIso(context)
        val me = Person.Builder().setName(context.getString(R.string.you)).setKey("me").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if (conversation.isGroup) conversation.title else null)
            .setGroupConversation(conversation.isGroup)
        val people = HashMap<String, Person>()
        for (m in unread) {
            val text = when {
                !s.showNotificationPreviews -> context.getString(R.string.shortcut_new_message)
                m.body.isNotBlank() -> m.body
                m.hasAttachments -> context.getString(R.string.notif_attachment)
                else -> context.getString(R.string.mms_title)
            }
            val p = people.getOrPut(m.address) { person(m.address, iso) }
            style.addMessage(NotificationCompat.MessagingStyle.Message(text, m.date, p))
        }
        val shortcutId = ensureShortcut(conversation, people[latest.address])

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openConversationIntent(conversationId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))
            .setGroup(GROUP_MESSAGES)
            .setWhen(latest.date)
            .setNumber(unread.size)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.shortcut_new_message))
                    .setContentText(context.getString(R.string.n_unread, unread.size))
                    .build(),
            )
            .setDeleteIntent(actionIntent(NotificationActionReceiver.ACTION_DISMISSED, conversationId))
        // Dual SIM: say which SIM the message (or code) arrived on, e.g. "SIM 2 · Airtel".
        if (sims.isMultiSim) sims.current().firstOrNull { it.subId == latest.subId }?.let { builder.setSubText(app.yarn.ui.common.simLabel(context, it)) }

        val markRead = NotificationCompat.Action.Builder(R.drawable.ic_done, context.getString(R.string.mark_read_short), actionIntent(NotificationActionReceiver.ACTION_MARK_READ, conversationId))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        // Codes get the same two actions as other alerts (Mark read, Delete); Android adds its own
        // "Copy" shortcut for one-time codes, so Yarn doesn't duplicate it.
        run {
            if (latest.body.isNotBlank() && !conversation.spam && conversation.addresses.all { PhoneNumbers.isDialable(it) || PhoneNumbers.isEmail(it) }) {
                val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY).setLabel(context.getString(R.string.reply)).build()
                builder.addAction(
                    NotificationCompat.Action.Builder(R.drawable.ic_reply, context.getString(R.string.reply), actionIntent(NotificationActionReceiver.ACTION_REPLY, conversationId, mutable = true))
                        .addRemoteInput(remoteInput)
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                        .setShowsUserInterface(false)
                        .build(),
                )
            }
            builder.addAction(markRead)
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_delete, context.getString(R.string.delete),
                    actionIntent(NotificationActionReceiver.ACTION_DELETE, conversationId, {
                        putExtra(NotificationActionReceiver.EXTRA_MESSAGE_IDS, unread.map { it.id }.toLongArray())
                    }),
                ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_DELETE).setShowsUserInterface(false).build(),
            )
        }
        post(conversationId.toInt(), builder.build())
    }

    private fun ensureShortcut(conversation: ConversationEntity, person: Person?): String {
        val id = "conversation_${conversation.id}"
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("yarn://conversation/${conversation.id}"), context, MainActivity::class.java)
            val shortcut = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(conversation.title.take(24).ifBlank { context.getString(R.string.conversation) })
                .setLongLived(true)
                .setIntent(intent)
                .setLocusId(LocusIdCompat(id))
                .setCategories(setOf("app.yarn.category.CONVERSATION"))
                .setIcon(person?.icon ?: IconCompat.createWithBitmap(Avatars.letterBitmap(conversation.title, 128)))
                .apply { person?.let { setPerson(it) } }
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
        return id
    }

    fun cancel(conversationId: Long) = nm.cancel(conversationId.toInt())

    fun notifyFailed(message: MessageEntity, conversationTitle: String, reason: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_FAILED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.not_sent_to, conversationTitle))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$reason\n\n“${message.body.take(120)}”"))
            .setContentIntent(openConversationIntent(message.conversationId, message.id))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_retry, context.getString(R.string.retry),
                actionIntent(NotificationActionReceiver.ACTION_RETRY, message.conversationId, { putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, message.id) }),
            )
            .build()
        post(FAILED_BASE + message.id.toInt(), n)
    }

    fun cancelFailed(messageId: Long) = nm.cancel(FAILED_BASE + messageId.toInt())

    fun notifyMmsPending(message: MessageEntity, sender: String, reason: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_FAILED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.mms_from, sender))
            .setContentText(reason)
            .setContentIntent(openConversationIntent(message.conversationId, message.id))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_download, context.getString(R.string.download),
                actionIntent(NotificationActionReceiver.ACTION_DOWNLOAD_MMS, message.conversationId, { putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, message.id) }),
            )
            .build()
        post(MMS_BASE + message.id.toInt(), n)
    }

    fun cancelMmsPending(messageId: Long) = nm.cancel(MMS_BASE + messageId.toInt())

    /** Class 0 "flash" SMS: displayed immediately, never stored (3GPP TS 23.038). */
    fun notifyFlash(address: String, body: String) {
        if (!canPost()) return
        val iso = PhoneNumbers.countryIso(context)
        val name = contacts.lookup(address)?.name ?: PhoneNumbers.format(address, iso)
        val n = NotificationCompat.Builder(context, CH_FLASH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.flash_from, name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()
        post(FLASH_BASE + (address + body).hashCode() % 1000, n)
    }

    fun backgroundNotification(text: String) = NotificationCompat.Builder(context, CH_BACKGROUND)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(text)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .build()

    companion object {
        const val CH_MESSAGES = "messages"
        const val CH_OTP = "otp"
        const val CH_QUIET = "quiet"
        const val CH_SPAM = "spam"
        const val CH_FAILED = "failed"
        const val CH_FLASH = "flash"
        const val CH_BACKGROUND = "background"
        const val GROUP_MESSAGES = "app.yarn.MESSAGES"
        private const val FAILED_BASE = 1_000_000
        private const val MMS_BASE = 2_000_000
        private const val FLASH_BASE = 3_000_000
        const val BACKGROUND_ID = 4_000_001
    }
}
