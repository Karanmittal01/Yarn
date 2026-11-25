package app.yarn.backup

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import app.yarn.crypto.EncryptedStreams
import app.yarn.data.db.BlockRuleEntity
import app.yarn.data.db.CorrectionEntity
import app.yarn.data.db.MessageKind
import app.yarn.data.db.SenderRuleEntity
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.SettingsRepository
import app.yarn.data.repo.TelephonySync
import app.yarn.telephony.DefaultSmsApp
import app.yarn.telephony.PartToStore
import app.yarn.telephony.TelephonyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import app.yarn.R

@Serializable
data class BackupMessage(
    val kind: Int, val threadAddresses: List<String>, val address: String, val body: String, val subject: String? = null,
    val date: Long, val outgoing: Boolean, val read: Boolean, val starred: Boolean, val subId: Int,
    val media: List<BackupMedia> = emptyList(),
)

@Serializable
data class BackupMedia(val entry: String, val mimeType: String, val name: String?)

@Serializable
data class BackupConversation(
    val addresses: List<String>, val customTitle: String?, val pinned: Boolean, val archived: Boolean, val muted: Boolean,
    val starred: Boolean, val category: String, val categoryLocked: Boolean,
    /** An unsent message, and the SIM chosen for replies in this chat. */
    val draft: String? = null, val draftUpdatedAt: Long = 0, val preferredSubId: Int = -1,
)

@Serializable
data class BackupCorrection(val text: String, val senderAddress: String, val senderKind: String, val category: String?, val isSpam: Boolean?, val createdAt: Long)

@Serializable
data class BackupRule(val address: String, val displayAddress: String, val category: String?, val trusted: Boolean?)

@Serializable
data class BackupBlock(val type: String, val value: String)

@Serializable
data class BackupMeta(
    val version: Int = 2,
    val createdAt: Long,
    val messageCount: Int,
    val conversations: List<BackupConversation>,
    val corrections: List<BackupCorrection>,
    val rules: List<BackupRule>,
    val blocks: List<BackupBlock>,
    /** Everything below arrived in version 2; older backups simply don't have it. */
    val settings: BackupSettings? = null,
    val profileName: String? = null,
    /** The zip entry holding your profile picture. */
    val profilePhoto: String? = null,
    val bin: List<BackupBin> = emptyList(),
)

sealed interface BackupState {
    data object Idle : BackupState
    /** [percent] = true when [done]/[total] is 0..100 rather than a message count. */
    data class Running(val label: String, val done: Int, val total: Int, val percent: Boolean = false) : BackupState
    data class Done(val message: String) : BackupState
    data class Failed(val message: String) : BackupState
}

/**
 * Passphrase-encrypted backups (AES-256-GCM, PBKDF2-SHA256) written to any location the user picks
 * with the system file picker — local storage, SD card, or a cloud drive app. Restoring on another
 * phone is how history moves between devices without any Yarn server.
 */
class BackupManager(
    private val context: Context,
    private val db: YarnDatabase,
    private val store: TelephonyStore,
    private val sync: TelephonySync,
    private val providerLock: Mutex,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    suspend fun export(target: Uri, passphrase: CharArray, includeMedia: Boolean) = withContext(Dispatchers.IO) {
        try {
            val done = write(target, passphrase, includeMedia)
            _state.value = BackupState.Done(context.resources.getQuantityString(R.plurals.backed_up_n, done, done))
        } catch (e: Exception) {
            _state.value = BackupState.Failed(e.message ?: context.getString(R.string.backup_failed))
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Encrypts a backup and uploads it to the user's Google Drive. Returns the uploaded size and
     * message count, or null when it failed (the reason is in [state]).
     */
    suspend fun backupToGoogle(drive: GoogleDrive, token: String, includeMedia: Boolean): Pair<Long, Int>? = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "drive-backup.tmp")
        var key: CharArray? = null
        try {
            _state.value = BackupState.Running(context.getString(R.string.bk_preparing), 0, 0)
            key = drive.backupKey(token)
            val count = write(Uri.fromFile(tmp), key, includeMedia)
            val uploading = context.getString(R.string.bk_uploading)
            _state.value = BackupState.Running(uploading, 0, 100, percent = true)
            drive.upload(token, tmp, count) { done, total ->
                if (total > 0) _state.value = BackupState.Running(uploading, (done * 100 / total).toInt(), 100, percent = true)
            }
            _state.value = BackupState.Done(context.resources.getQuantityString(R.plurals.backed_up_drive_n, count, count))
            tmp.length() to count
        } catch (e: Exception) {
            _state.value = BackupState.Failed(e.message ?: context.getString(R.string.backup_failed))
            null
        } finally {
            key?.fill('\u0000')
            tmp.delete()
        }
    }

    /** Downloads the latest Google Drive backup and restores it. */
    suspend fun restoreFromGoogle(drive: GoogleDrive, token: String, backup: DriveBackup) = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "drive-restore.tmp")
        try {
            val downloading = context.getString(R.string.bk_downloading)
            _state.value = BackupState.Running(downloading, 0, 100, percent = true)
            val key = drive.backupKey(token)
            drive.download(token, backup, tmp) { done, total ->
                if (total > 0) _state.value = BackupState.Running(downloading, (done * 100 / total).toInt(), 100, percent = true)
            }
            restore(Uri.fromFile(tmp), key, backup.messageCount)
        } catch (e: Exception) {
            _state.value = BackupState.Failed(e.message ?: context.getString(R.string.restore_failed))
        } finally {
            tmp.delete()
        }
    }

    private suspend fun write(target: Uri, passphrase: CharArray, includeMedia: Boolean): Int {
        run {
            val conversations = db.conversations().all()
            val byId = conversations.associateBy { it.id }
            val total = conversations.sumOf { it.messageCount }
            var done = 0
            context.contentResolver.openOutputStream(target, "wt").use { raw ->
                requireNotNull(raw) { "Cannot write to the chosen location" }
                ZipOutputStream(EncryptedStreams.encrypt(passphrase, raw)).use { zip ->
                    zip.putNextEntry(ZipEntry("messages.jsonl"))
                    val mediaQueue = ArrayList<Pair<String, String>>()
                    var offset = 0
                    while (true) {
                        val page = db.messages().page(500, offset)
                        if (page.isEmpty()) break
                        offset += page.size
                        val attachments = db.messages().attachmentsFor(page.map { it.id }).groupBy { it.messageId }
                        for (m in page) {
                            if (m.isPendingOutgoing) continue
                            val media = if (includeMedia) attachments[m.id].orEmpty().map { a ->
                                val entry = "media/${a.id}"
                                mediaQueue += entry to a.uri
                                BackupMedia(entry, a.mimeType, a.fileName)
                            } else emptyList()
                            val line = json.encodeToString(
                                BackupMessage.serializer(),
                                BackupMessage(
                                    m.kind, byId[m.conversationId]?.addresses.orEmpty(), m.address, m.body, m.subject, m.date,
                                    m.outgoing, m.read, m.starred, m.subId, media,
                                ),
                            )
                            zip.write(line.toByteArray()); zip.write('\n'.code)
                            done++
                        }
                        _state.value = BackupState.Running(context.getString(R.string.bk_messages), done, total)
                    }
                    zip.closeEntry()
                    for ((entry, uri) in mediaQueue) {
                        val input = runCatching { context.contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull() ?: continue
                        input.use { zip.putNextEntry(ZipEntry(entry)); it.copyTo(zip); zip.closeEntry() }
                    }
                    // You: your name, your picture and every setting.
                    val s = settings.current()
                    val photo = s.profilePhoto.takeIf { it.isNotBlank() }?.let { uri ->
                        val bytes = runCatching { readUri(uri) }.getOrNull() ?: return@let null
                        zip.putNextEntry(ZipEntry(PROFILE_PHOTO)); zip.write(bytes); zip.closeEntry()
                        PROFILE_PHOTO
                    }
                    // The recycle bin, so deleted messages can still be brought back on the new phone.
                    var binFile = 0
                    val bin = db.bin().all().map { b ->
                        val files = if (!includeMedia) emptyList() else b.attachments.mapNotNull { a ->
                            val bytes = runCatching { readUri(a.uri) }.getOrNull() ?: return@mapNotNull null
                            val entry = "bin/${binFile++}"
                            zip.putNextEntry(ZipEntry(entry)); zip.write(bytes); zip.closeEntry()
                            BackupMedia(entry, a.mimeType, a.fileName)
                        }
                        BackupBin(
                            b.addresses, b.title, b.kind, b.address, b.body, b.subject, b.date, b.dateSent, b.outgoing, b.status, b.subId,
                            b.category, b.starred, b.deletedAt, files,
                        )
                    }
                    val meta = BackupMeta(
                        createdAt = System.currentTimeMillis(), messageCount = done,
                        conversations = conversations.map {
                            BackupConversation(
                                it.addresses, it.customTitle, it.pinned, it.archived, it.muted, it.starred, it.category, it.categoryLocked,
                                it.draft, it.draftUpdatedAt, it.preferredSubId,
                            )
                        },
                        corrections = db.learning().corrections().map { BackupCorrection(it.text, it.senderAddress, it.senderKind, it.category, it.isSpam, it.createdAt) },
                        rules = db.learning().rules().map { BackupRule(it.address, it.displayAddress, it.category, it.trusted) },
                        blocks = db.blocks().all().map { BackupBlock(it.type, it.value) },
                        settings = BackupSettings.of(s, app.yarn.i18n.AppLanguage.selected(context)),
                        profileName = s.profileName,
                        profilePhoto = photo,
                        bin = bin,
                    )
                    zip.putNextEntry(ZipEntry("meta.json"))
                    zip.write(json.encodeToString(BackupMeta.serializer(), meta).toByteArray())
                    zip.closeEntry()
                }
            }
            return done
        }
    }

    /**
     * Restores messages into the system store (requires being the default SMS app), skipping
     * duplicates, then re-imports and re-applies conversation settings and learned preferences.
     */
    suspend fun restore(source: Uri, passphrase: CharArray, expectedMessages: Int = 0) = withContext(Dispatchers.IO) {
        if (!DefaultSmsApp.isDefault(context)) {
            _state.value = BackupState.Failed(context.getString(R.string.restore_needs_default))
            return@withContext
        }
        try {
            var restored = 0
            var skipped = 0
            var meta: BackupMeta? = null
            val pendingMms = ArrayList<BackupMessage>()
            // Photos and videos wait on disk, not in memory: a backup with years of media would not fit.
            val media = MediaFiles(File(context.cacheDir, "restore-media"))
            context.contentResolver.openInputStream(source).use { raw ->
                requireNotNull(raw) { "Cannot read the chosen file" }
                val zip = ZipInputStream(EncryptedStreams.decrypt(passphrase, raw))
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == "messages.jsonl" -> {
                            val reader = BufferedReader(InputStreamReader(zip))
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isBlank()) continue
                                val m = json.decodeFromString(BackupMessage.serializer(), line)
                                if (m.kind == MessageKind.MMS) { pendingMms += m; continue }
                                if (restoreSms(m)) restored++ else skipped++
                                if ((restored + skipped) % 50 == 0) _state.value = BackupState.Running(context.getString(R.string.bk_restoring), restored + skipped, maxOf(expectedMessages, restored + skipped))
                            }
                        }
                        entry.name == "meta.json" -> meta = json.decodeFromString(BackupMeta.serializer(), zip.readBytes().decodeToString())
                        entry.name.startsWith("media/") || entry.name.startsWith("bin/") || entry.name == PROFILE_PHOTO -> media.put(entry.name, zip)
                    }
                }
            }
            try {
                for (m in pendingMms) if (restoreMms(m, media)) restored++ else skipped++
                _state.value = BackupState.Running(context.getString(R.string.bk_organising), 0, 0)
                sync.sync()
                meta?.let { applyMeta(it, media) }
            } finally {
                media.clear()
            }
            _state.value = BackupState.Done(context.resources.getQuantityString(R.plurals.restored_n, restored, restored) + if (skipped > 0) context.getString(R.string.already_present_n, skipped) else "")
        } catch (e: app.yarn.crypto.BadPassphraseException) {
            _state.value = BackupState.Failed(context.getString(R.string.wrong_passphrase))
        } catch (e: Exception) {
            _state.value = BackupState.Failed(e.message ?: context.getString(R.string.restore_failed))
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private suspend fun restoreSms(m: BackupMessage): Boolean = providerLock.withLock {
        if (smsExists(m)) return@withLock false
        val threadId = store.threadIdFor(m.threadAddresses.ifEmpty { listOf(m.address) }.toSet())
        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.ADDRESS, m.address.substringBefore(','))
            put(Telephony.Sms.BODY, m.body)
            put(Telephony.Sms.DATE, m.date)
            put(Telephony.Sms.DATE_SENT, m.date)
            put(Telephony.Sms.READ, if (m.read) 1 else 0)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, if (m.outgoing) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.SUBSCRIPTION_ID, m.subId)
        }
        runCatching { context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) }.getOrNull() != null
    }

    private fun smsExists(m: BackupMessage): Boolean = runCatching {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.DATE} = ? AND ${Telephony.Sms.BODY} = ?", arrayOf(m.date.toString(), m.body), null,
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    private suspend fun restoreMms(m: BackupMessage, media: MediaFiles): Boolean = providerLock.withLock {
        val exists = runCatching {
            context.contentResolver.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID), "${Telephony.Mms.DATE} = ?", arrayOf((m.date / 1000).toString()), null)
                ?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
        if (exists) return@withLock false
        val participants = m.threadAddresses.ifEmpty { listOf(m.address) }
        val threadId = store.threadIdFor(participants.toSet())
        val parts = ArrayList<PartToStore>()
        m.media.forEach { md -> media[md.entry]?.let { parts += PartToStore(md.mimeType, it, md.name, md.name, null) } }
        if (m.body.isNotBlank()) parts += PartToStore("text/plain", m.body.toByteArray(), "text.txt", "text", 106)
        if (parts.isEmpty()) return@withLock false
        store.persistMms(
            box = if (m.outgoing) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_INBOX,
            threadId = threadId, from = if (m.outgoing) null else m.address,
            recipients = if (m.outgoing) participants else participants.filter { it != m.address },
            subject = m.subject, dateMillis = m.date, parts = parts, subId = m.subId, read = m.read,
        ) != null
    }

    private suspend fun applyMeta(meta: BackupMeta, files: MediaFiles) {
        val current = db.conversations().all()
        for (bc in meta.conversations) {
            val match = current.firstOrNull { it.addresses.toSet() == bc.addresses.toSet() } ?: continue
            // A draft written on this phone since is newer than the one in the backup: keep it.
            val keepDraft = !match.draft.isNullOrBlank() && match.draftUpdatedAt >= bc.draftUpdatedAt
            db.conversations().update(
                match.copy(
                    customTitle = bc.customTitle, pinned = bc.pinned, archived = bc.archived, muted = bc.muted, starred = bc.starred,
                    category = if (bc.categoryLocked) bc.category else match.category, categoryLocked = bc.categoryLocked,
                    draft = if (keepDraft) match.draft else bc.draft ?: match.draft,
                    draftUpdatedAt = if (keepDraft) match.draftUpdatedAt else maxOf(bc.draftUpdatedAt, match.draftUpdatedAt),
                    preferredSubId = if (bc.preferredSubId != -1) bc.preferredSubId else match.preferredSubId,
                ),
            )
            db.conversations().refresh(match.id)
        }
        applyYou(meta, files)
        restoreBin(meta.bin, files, current)
        val existing = db.learning().corrections().map { it.text to it.createdAt }.toSet()
        meta.corrections.filter { (it.text to it.createdAt) !in existing }.forEach {
            db.learning().insertCorrection(CorrectionEntity(messageId = null, text = it.text, senderAddress = it.senderAddress, senderKind = it.senderKind, fromCategory = null, category = it.category, isSpam = it.isSpam, createdAt = it.createdAt))
        }
        meta.rules.forEach { db.learning().upsertRule(SenderRuleEntity(it.address, it.displayAddress, it.category, it.trusted, System.currentTimeMillis())) }
        meta.blocks.forEach { db.blocks().insert(BlockRuleEntity(type = it.type, value = it.value, createdAt = System.currentTimeMillis())) }
    }

    /** Your settings, language, name and picture. The backup account and this phone's own state stay. */
    private suspend fun applyYou(meta: BackupMeta, files: MediaFiles) {
        val photo = meta.profilePhoto?.let { files[it] }
            ?.let { bytes -> runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
            ?.let { app.yarn.ui.inbox.ProfilePhoto.save(context, it) }
        settings.update { s ->
            val restored = meta.settings?.applyTo(s) ?: s
            restored.copy(
                profileName = meta.profileName?.takeIf { it.isNotBlank() } ?: s.profileName,
                profilePhoto = photo ?: s.profilePhoto,
            )
        }
        meta.settings?.language?.let { tag ->
            withContext(Dispatchers.Main) { runCatching { app.yarn.i18n.AppLanguage.restore(context, tag) } }
        }
    }

    /** Puts deleted messages back in the recycle bin, skipping any that are already there. */
    private suspend fun restoreBin(items: List<BackupBin>, files: MediaFiles, chats: List<app.yarn.data.db.ConversationEntity>) {
        if (items.isEmpty()) return
        val have = db.bin().all().map { Triple(it.address, it.date, it.body) }.toSet()
        val dir = File(context.filesDir, "bin").apply { mkdirs() }
        val rows = items.filter { Triple(it.address, it.date, it.body) !in have }.map { b ->
            val attachments = b.media.mapNotNull { m ->
                val source = files.file(m.entry) ?: return@mapNotNull null
                val target = File(dir, java.util.UUID.randomUUID().toString() + (m.name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= 5 }?.let { ".$it" } ?: ""))
                source.copyTo(target, overwrite = true)
                app.yarn.data.db.BinAttachment(m.mimeType, Uri.fromFile(target).toString(), m.name, target.length())
            }
            app.yarn.data.db.BinEntity(
                conversationId = chats.firstOrNull { it.addresses.toSet() == b.addresses.toSet() }?.id ?: 0,
                addresses = b.addresses, title = b.title, kind = b.kind, address = b.address, body = b.body, subject = b.subject,
                date = b.date, dateSent = b.dateSent, outgoing = b.outgoing, status = b.status, subId = b.subId,
                category = b.category, starred = b.starred, attachments = attachments, deletedAt = b.deletedAt,
            )
        }
        if (rows.isNotEmpty()) db.bin().insertAll(rows)
    }

    private fun readUri(uri: String): ByteArray? {
        val u = Uri.parse(uri)
        val input = if (u.scheme == "file") u.path?.let { File(it).inputStream() } else context.contentResolver.openInputStream(u)
        return input?.use { it.readBytes() }
    }

    fun reset() { _state.value = BackupState.Idle }

    companion object {
        private const val PROFILE_PHOTO = "profile/photo.jpg"
    }
}

/** A backup's photos and videos, kept on disk while restoring; each is read only when it's put back. */
private class MediaFiles(private val dir: File) {
    private val files = HashMap<String, File>()

    init {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    /** Copies the current zip entry to disk (the stream stays open for the next entry). */
    fun put(name: String, input: java.io.InputStream) {
        val f = File(dir, files.size.toString())
        f.outputStream().use { input.copyTo(it) }
        files[name] = f
    }

    operator fun get(name: String): ByteArray? = files[name]?.readBytes()
    fun file(name: String): File? = files[name]
    fun clear() { dir.deleteRecursively() }
}
