package app.yarn.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import app.yarn.intelligence.SpamVerdict
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String, val unread: Int)

data class CategoryTotal(val category: String, val total: Int, val unread: Int)

@Dao
abstract class ConversationDao {
    @RawQuery(observedEntities = [ConversationEntity::class])
    abstract fun observe(query: SupportSQLiteQuery): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    abstract suspend fun get(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    abstract fun observe(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id IN (:ids)")
    abstract suspend fun getAll(ids: Collection<Long>): List<ConversationEntity>

    @Query("SELECT * FROM conversations")
    abstract suspend fun all(): List<ConversationEntity>

    @Query("SELECT id FROM conversations")
    abstract suspend fun allIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(c: ConversationEntity): Long

    @Upsert
    abstract suspend fun upsert(c: ConversationEntity)

    @Update
    abstract suspend fun update(c: ConversationEntity)

    @Query("UPDATE conversations SET pinned = :pinned, pinnedAt = :at WHERE id IN (:ids)")
    abstract suspend fun setPinned(ids: Collection<Long>, pinned: Boolean, at: Long)

    @Query("UPDATE conversations SET archived = :archived WHERE id IN (:ids)")
    abstract suspend fun setArchived(ids: Collection<Long>, archived: Boolean)

    @Query("UPDATE conversations SET muted = :muted WHERE id IN (:ids)")
    abstract suspend fun setMuted(ids: Collection<Long>, muted: Boolean)

    @Query("UPDATE conversations SET starred = :starred WHERE id IN (:ids)")
    abstract suspend fun setStarred(ids: Collection<Long>, starred: Boolean)

    @Query("UPDATE conversations SET blocked = :blocked WHERE id IN (:ids)")
    abstract suspend fun setBlocked(ids: Collection<Long>, blocked: Boolean)

    @Query("UPDATE conversations SET spam = :spam WHERE id IN (:ids)")
    abstract suspend fun setSpam(ids: Collection<Long>, spam: Boolean)

    @Query("UPDATE conversations SET category = :category, categoryLocked = :locked WHERE id IN (:ids)")
    abstract suspend fun setCategory(ids: Collection<Long>, category: String, locked: Boolean)

    @Query("UPDATE conversations SET draft = :draft, draftUpdatedAt = :at WHERE id = :id")
    abstract suspend fun setDraft(id: Long, draft: String?, at: Long)

    @Query("UPDATE conversations SET preferredSubId = :subId WHERE id = :id")
    abstract suspend fun setPreferredSub(id: Long, subId: Int)

    @Query("UPDATE conversations SET customTitle = :title WHERE id = :id")
    abstract suspend fun setCustomTitle(id: Long, title: String?)

    @Query("UPDATE conversations SET displayName = :name WHERE id = :id")
    abstract suspend fun setDisplayName(id: Long, name: String)

    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    abstract suspend fun delete(ids: Collection<Long>)

    @Query("DELETE FROM conversations WHERE messageCount = 0 AND (draft IS NULL OR draft = '')")
    abstract suspend fun deleteEmpty()

    @Query("DELETE FROM conversations WHERE id = :id AND messageCount = 0 AND (draft IS NULL OR draft = '')")
    abstract suspend fun deleteIfEmpty(id: Long)

    @Query("SELECT category, SUM(unreadCount) AS unread FROM conversations WHERE archived = 0 AND spam = 0 AND blocked = 0 GROUP BY category")
    abstract fun unreadByCategory(): Flow<List<CategoryCount>>

    @Query(
        "SELECT category, COUNT(*) AS total, SUM(CASE WHEN unreadCount > 0 THEN 1 ELSE 0 END) AS unread FROM conversations " +
            "WHERE archived = 0 AND spam = 0 AND blocked = 0 AND messageCount > 0 GROUP BY category",
    )
    abstract fun totalsByCategory(): Flow<List<CategoryTotal>>

    @Query("SELECT COUNT(*) FROM conversations WHERE archived = 0 AND spam = 0 AND blocked = 0 AND unreadCount > 0 AND importance >= 65")
    abstract fun importantUnread(): Flow<Int>

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations WHERE spam = 1 AND blocked = 0")
    abstract fun unreadSpam(): Flow<Int>

    @Query("SELECT * FROM conversations WHERE spam = 0 AND blocked = 0 AND archived = 0 ORDER BY lastMessageAt DESC LIMIT :limit")
    abstract suspend fun recent(limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE displayName LIKE :like OR customTitle LIKE :like OR addresses LIKE :like ORDER BY lastMessageAt DESC LIMIT 30")
    abstract suspend fun searchByName(like: String): List<ConversationEntity>

    // ---- aggregate refresh ------------------------------------------------------------------

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY date DESC, id DESC LIMIT 1")
    protected abstract suspend fun latestMessage(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :id AND outgoing = 0 ORDER BY date DESC, id DESC LIMIT 1")
    protected abstract suspend fun latestIncoming(id: Long): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :id")
    protected abstract suspend fun messageCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :id AND outgoing = 0 AND read = 0")
    protected abstract suspend fun unreadCount(id: Long): Int

    @Query("SELECT COALESCE(MAX(priority), 0) FROM messages WHERE conversationId = :id AND outgoing = 0 AND read = 0")
    protected abstract suspend fun maxUnreadPriority(id: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE conversationId = :id AND outgoing = 1 AND status IN (5, 9))")
    protected abstract suspend fun hasFailedMessages(id: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE conversationId = :id AND outgoing = 1)")
    protected abstract suspend fun hasOutgoingMessages(id: Long): Boolean

    /** Recomputes denormalised list fields after any message change in the conversation. */
    @Transaction
    open suspend fun refresh(id: Long) {
        val c = get(id) ?: return
        val latest = latestMessage(id)
        val incoming = latestIncoming(id)
        val hasOutgoing = hasOutgoingMessages(id)
        val snippet = when {
            latest == null -> ""
            latest.body.isNotBlank() -> latest.body.take(200)
            latest.status == MessageStatus.PENDING_DOWNLOAD || latest.status == MessageStatus.DOWNLOAD_FAILED -> "Multimedia message"
            latest.hasAttachments -> "Attachment"
            else -> ""
        }
        val category = if (c.categoryLocked) c.category else incoming?.category ?: c.category
        // Once the user has replied in a thread it is a real conversation, not spam.
        val spam = !hasOutgoing && incoming != null &&
            (incoming.spamVerdict == SpamVerdict.SPAM.name || incoming.spamVerdict == SpamVerdict.SCAM.name)
        update(
            c.copy(
                snippet = snippet,
                snippetFromMe = latest?.outgoing ?: false,
                snippetStatus = latest?.status ?: MessageStatus.RECEIVED,
                lastMessageAt = maxOf(latest?.date ?: 0, if (!c.draft.isNullOrBlank()) c.draftUpdatedAt else 0),
                messageCount = messageCount(id),
                unreadCount = unreadCount(id),
                importance = maxUnreadPriority(id),
                hasFailed = hasFailedMessages(id),
                category = category,
                // A user decision (locked category) always overrides automatic spam detection.
                spam = if (c.categoryLocked) c.category == "SPAM" else spam,
                hasOutgoing = hasOutgoing,
                lastSubId = latest?.subId ?: -1,
            ),
        )
    }
}

@Dao
abstract class MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY date DESC, id DESC")
    abstract fun paging(conversationId: Long): PagingSource<Int, MessageWithAttachments>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY date DESC, id DESC LIMIT :limit")
    abstract suspend fun recent(conversationId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND read = 0 AND outgoing = 0 ORDER BY date ASC")
    abstract suspend fun unread(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    abstract suspend fun get(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    abstract suspend fun getAll(ids: Collection<Long>): List<MessageEntity>

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :id")
    abstract suspend fun getFull(id: Long): MessageWithAttachments?

    @Transaction
    @Query("SELECT * FROM messages WHERE id IN (:ids) ORDER BY date ASC")
    abstract suspend fun getFull(ids: Collection<Long>): List<MessageWithAttachments>

    @Query("SELECT * FROM messages WHERE id = :id")
    abstract fun observe(id: Long): Flow<MessageEntity?>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND date > :date")
    abstract suspend fun countNewerThan(conversationId: Long, date: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(m: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(m: List<MessageEntity>): List<Long>

    @Update
    abstract suspend fun update(m: MessageEntity)

    @Query("SELECT * FROM messages WHERE kind = :kind AND providerId = :providerId")
    abstract suspend fun byProviderId(kind: Int, providerId: Long): MessageEntity?

    /** A message Yarn itself sent (it has send attempts) with this text, near this time, in this thread. */
    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND outgoing = 1 AND attempts > 0 AND kind = 0 " +
            "AND body = :body AND date BETWEEN :from AND :to AND (providerId IS NULL OR providerId != :providerId) LIMIT 1",
    )
    abstract suspend fun ownSendNear(conversationId: Long, body: String, from: Long, to: Long, providerId: Long): MessageEntity?

    /**
     * Imported copies of SMS that Yarn sent itself. Some phones (notably Samsung) save their own
     * copy of every sent SMS on top of ours; those must not show up as a second message.
     */
    @Query(
        "SELECT d.* FROM messages d WHERE d.kind = 0 AND d.outgoing = 1 AND d.attempts = 0 AND d.providerId IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM messages o WHERE o.conversationId = d.conversationId AND o.outgoing = 1 AND o.attempts > 0 " +
            "AND o.kind = 0 AND o.id != d.id AND o.body = d.body AND ABS(o.date - d.date) < 180000)",
    )
    abstract suspend fun duplicatesOfOwnSends(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE mmsContentLocation = :location LIMIT 1")
    abstract suspend fun byContentLocation(location: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE mmsMessageId = :mid AND outgoing = 1 LIMIT 1")
    abstract suspend fun byMmsMessageId(mid: String): MessageEntity?

    @Query("SELECT MAX(providerId) FROM messages WHERE kind = :kind")
    abstract suspend fun maxProviderId(kind: Int): Long?

    @Query("SELECT providerId FROM messages WHERE kind = :kind AND providerId IS NOT NULL")
    abstract suspend fun providerIds(kind: Int): List<Long>

    @Query("UPDATE messages SET read = 1, seen = 1 WHERE conversationId IN (:conversationIds) AND read = 0")
    abstract suspend fun markRead(conversationIds: Collection<Long>)

    @Query("SELECT providerId FROM messages WHERE conversationId IN (:conversationIds) AND read = 0 AND kind = :kind AND providerId IS NOT NULL")
    abstract suspend fun unreadProviderIds(conversationIds: Collection<Long>, kind: Int): List<Long>

    @Query("UPDATE messages SET read = 0 WHERE id = (SELECT id FROM messages WHERE conversationId = :conversationId AND outgoing = 0 ORDER BY date DESC LIMIT 1)")
    abstract suspend fun markLatestUnread(conversationId: Long)

    @Query("UPDATE messages SET starred = :starred WHERE id IN (:ids)")
    abstract suspend fun setStarred(ids: Collection<Long>, starred: Boolean)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    abstract suspend fun delete(ids: Collection<Long>)

    @Query("SELECT * FROM messages WHERE conversationId IN (:conversationIds)")
    abstract suspend fun inConversations(conversationIds: Collection<Long>): List<MessageEntity>

    @Query("UPDATE messages SET conversationId = :to WHERE id = :id")
    abstract suspend fun moveTo(id: Long, to: Long)

    @Query(
        "SELECT * FROM messages WHERE outgoing = 1 AND status IN (1, 10) AND nextAttemptAt <= :now " +
            "UNION SELECT * FROM messages WHERE outgoing = 1 AND status = 6 AND scheduledAt <= :now " +
            "UNION SELECT * FROM messages WHERE outgoing = 1 AND status = 12 AND nextAttemptAt <= :now ORDER BY date ASC",
    )
    abstract suspend fun dueOutgoing(now: Long): List<MessageEntity>

    @Query(
        "SELECT MIN(t) FROM (SELECT nextAttemptAt AS t FROM messages WHERE outgoing = 1 AND status IN (1, 10, 12) " +
            "UNION ALL SELECT scheduledAt AS t FROM messages WHERE outgoing = 1 AND status = 6)",
    )
    abstract suspend fun nextDueTime(): Long?

    @Query("SELECT * FROM messages WHERE outgoing = 1 AND status = 6 ORDER BY scheduledAt ASC")
    abstract fun observeScheduled(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE outgoing = 1 AND status = 6")
    abstract suspend fun scheduled(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE outgoing = 1 AND status = 10")
    abstract fun waitingForService(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE outgoing = 1 AND status = 5")
    abstract fun failedCount(): Flow<Int>

    @Query("SELECT * FROM messages WHERE outgoing = 1 AND status = 2 AND date < :before")
    abstract suspend fun stuckSending(before: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status IN (7, 9) AND outgoing = 0")
    abstract suspend fun pendingDownloads(): List<MessageEntity>

    @Transaction
    @Query("SELECT * FROM messages WHERE starred = 1 ORDER BY date DESC")
    abstract fun observeStarred(): Flow<List<MessageWithAttachments>>

    /**
     * Clean-up candidates: received messages in [categories] older than [before]. Starred messages,
     * pinned or starred chats, chats the user has replied to and chats they filed as personal are
     * never touched.
     */
    @Query(
        "SELECT m.id FROM messages m JOIN conversations c ON c.id = m.conversationId " +
            "WHERE m.category IN (:categories) AND m.outgoing = 0 AND m.starred = 0 AND m.date < :before " +
            "AND c.pinned = 0 AND c.starred = 0 AND c.hasOutgoing = 0 AND c.spam = 0 " +
            "AND NOT (c.categoryLocked = 1 AND c.category = 'PERSONAL')",
    )
    abstract suspend fun junkIds(categories: List<String>, before: Long): List<Long>

    @Query("SELECT m.id FROM messages m JOIN conversations c ON c.id = m.conversationId WHERE c.spam = 1 AND m.starred = 0 AND m.date < :before")
    abstract suspend fun spamOlderThan(before: Long): List<Long>

    @Query(
        "SELECT * FROM messages WHERE outgoing = 0 AND analyzed = 1 AND categorySource = 'RULES' AND categoryConfidence < :threshold " +
            "AND date > :since AND body != '' AND spamVerdict NOT IN ('SPAM', 'SCAM') ORDER BY date DESC LIMIT :limit",
    )
    abstract suspend fun uncertain(threshold: Float, since: Long, limit: Int): List<MessageEntity>

    /** Lets automatic decisions be recomputed after the sorting rules change; user choices stay. */
    @Query("UPDATE messages SET categorySource = 'RULES' WHERE categorySource = 'LLM'")
    abstract suspend fun resetModelCategories()

    @Query("SELECT id FROM conversations WHERE unreadCount > 0 AND blocked = 0 AND spam = 0")
    abstract suspend fun unreadConversationIds(): List<Long>

    @Query("SELECT COUNT(*) FROM messages WHERE analyzed = 0")
    abstract suspend fun unanalyzedCount(): Int

    @Query("SELECT COUNT(*) FROM messages")
    abstract suspend fun count(): Int

    @Query("SELECT * FROM messages WHERE analyzed = 0 ORDER BY date DESC LIMIT :limit")
    abstract suspend fun unanalyzed(limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND outgoing = 1")
    abstract suspend fun outgoingCount(conversationId: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND outgoing = 0")
    abstract suspend fun incomingCount(conversationId: Long): Int

    @Query(
        "SELECT m.*, CASE WHEN c.customTitle IS NOT NULL AND c.customTitle != '' THEN c.customTitle ELSE c.displayName END AS conversationTitle, " +
            "snippet(messages_fts, char(2), char(3), '…', -1, 14) AS snippet " +
            "FROM messages_fts JOIN messages m ON m.id = messages_fts.rowid JOIN conversations c ON c.id = m.conversationId " +
            "WHERE messages_fts MATCH :match AND c.blocked = 0 ORDER BY m.date DESC LIMIT :limit",
    )
    abstract suspend fun search(match: String, limit: Int): List<MessageSearchResult>

    @Query("SELECT * FROM messages ORDER BY id ASC LIMIT :limit OFFSET :offset")
    abstract suspend fun page(limit: Int, offset: Int): List<MessageEntity>

    // ---- attachments & entities -------------------------------------------------------------

    @Insert
    abstract suspend fun insertAttachments(a: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    abstract suspend fun attachments(messageId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    abstract suspend fun deleteAttachments(messageId: Long)

    @Query("SELECT * FROM attachments WHERE messageId IN (:messageIds)")
    abstract suspend fun attachmentsFor(messageIds: Collection<Long>): List<AttachmentEntity>

    @Insert
    abstract suspend fun insertEntities(e: List<ExtractedEntity>)

    @Query("DELETE FROM message_entities WHERE messageId = :messageId AND source = :source")
    abstract suspend fun deleteEntities(messageId: Long, source: String)

    @Query("SELECT * FROM message_entities WHERE messageId = :messageId")
    abstract suspend fun entities(messageId: Long): List<ExtractedEntity>

    @Transaction
    open suspend fun replaceEntities(messageId: Long, source: String, entities: List<ExtractedEntity>) {
        deleteEntities(messageId, source)
        if (entities.isNotEmpty()) insertEntities(entities)
    }
}

@Dao
interface LearningDao {
    @Insert
    suspend fun insertCorrection(c: CorrectionEntity): Long

    @Query("SELECT * FROM corrections ORDER BY createdAt DESC")
    fun observeCorrections(): Flow<List<CorrectionEntity>>

    @Query("SELECT * FROM corrections ORDER BY createdAt ASC")
    suspend fun corrections(): List<CorrectionEntity>

    @Query("DELETE FROM corrections WHERE id = :id")
    suspend fun deleteCorrection(id: Long)

    @Query("DELETE FROM corrections")
    suspend fun clearCorrections()

    @Upsert
    suspend fun upsertRule(r: SenderRuleEntity)

    @Query("SELECT * FROM sender_rules WHERE address = :address")
    suspend fun rule(address: String): SenderRuleEntity?

    @Query("SELECT * FROM sender_rules ORDER BY updatedAt DESC")
    fun observeRules(): Flow<List<SenderRuleEntity>>

    @Query("SELECT * FROM sender_rules")
    suspend fun rules(): List<SenderRuleEntity>

    @Query("DELETE FROM sender_rules WHERE address = :address")
    suspend fun deleteRule(address: String)

    @Query("DELETE FROM sender_rules")
    suspend fun clearRules()
}

@Dao
interface BlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(r: BlockRuleEntity): Long

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM block_rules WHERE type = :type AND value = :value")
    suspend fun delete(type: String, value: String)

    @Query("SELECT * FROM block_rules ORDER BY createdAt DESC")
    fun observe(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules")
    suspend fun all(): List<BlockRuleEntity>
}
