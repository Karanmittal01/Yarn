package app.yarn.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Message lifecycle. Stored as Int; values are persisted and must never be renumbered. */
object MessageStatus {
    const val RECEIVED = 0
    const val QUEUED = 1
    const val SENDING = 2
    const val SENT = 3
    const val DELIVERED = 4
    const val FAILED = 5
    const val SCHEDULED = 6
    const val PENDING_DOWNLOAD = 7
    const val DOWNLOADING = 8
    const val DOWNLOAD_FAILED = 9
    const val WAITING_FOR_SERVICE = 10
    const val EXPIRED = 11
    /** Held for the user's "undo send" window. */
    const val DELAYED = 12

    val OUTGOING_PENDING = listOf(QUEUED, SENDING, SCHEDULED, WAITING_FOR_SERVICE, DELAYED)
}

object MessageKind {
    const val SMS = 0
    const val MMS = 1
}

@Entity(
    tableName = "conversations",
    indices = [Index("archived", "pinned", "lastMessageAt"), Index("category"), Index("spam")],
)
data class ConversationEntity(
    /** Telephony provider thread id; shared with the OS so other apps agree on threading. */
    @PrimaryKey val id: Long,
    /** Participant addresses as the provider knows them (excluding the user). */
    val addresses: List<String>,
    /** Resolved contact names joined with ", " — cached for fast list rendering and search. */
    val displayName: String = "",
    /** User-chosen group name; overrides [displayName]. */
    val customTitle: String? = null,
    val snippet: String = "",
    val snippetFromMe: Boolean = false,
    val snippetStatus: Int = MessageStatus.RECEIVED,
    val lastMessageAt: Long = 0,
    val unreadCount: Int = 0,
    val messageCount: Int = 0,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0,
    val starred: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    val blocked: Boolean = false,
    /** Latest inbox category (a [app.yarn.intelligence.Category] name). */
    val category: String = "PERSONAL",
    /** True if the user pinned the category; automatic re-categorisation is then skipped. */
    val categoryLocked: Boolean = false,
    val spam: Boolean = false,
    /** Highest priority among unread messages (0..100). */
    val importance: Int = 0,
    val hasFailed: Boolean = false,
    val draft: String? = null,
    val draftUpdatedAt: Long = 0,
    /** Preferred SIM subscription id for replies, or -1 for the system default. */
    val preferredSubId: Int = -1,
    val hasBusinessSender: Boolean = false,
    val hasOutgoing: Boolean = false,
    /** SIM subscription of the latest message, so dual-SIM inboxes can show which SIM it used. */
    @ColumnInfo(defaultValue = "-1") val lastSubId: Int = -1,
) {
    val isGroup: Boolean get() = addresses.size > 1
    val title: String get() = customTitle?.takeIf { it.isNotBlank() } ?: displayName.ifBlank { addresses.joinToString(", ") }
}

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(ConversationEntity::class, ["id"], ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index("conversationId", "date"),
        Index(value = ["kind", "providerId"], unique = true),
        Index("status"),
        Index("starred"),
        Index("category"),
        Index("mmsContentLocation"),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val kind: Int = MessageKind.SMS,
    /** Row id in content://sms or content://mms; null until written to the provider. */
    val providerId: Long? = null,
    /** Additional provider rows (broadcast SMS to several recipients). Comma separated. */
    val extraProviderIds: String? = null,
    /** Sender for incoming; recipients (comma separated) for outgoing. */
    val address: String,
    val body: String = "",
    val subject: String? = null,
    val date: Long,
    val dateSent: Long = 0,
    val outgoing: Boolean,
    val status: Int,
    val errorCode: Int = 0,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val scheduledAt: Long = 0,
    val partsTotal: Int = 0,
    val partsSent: Int = 0,
    val partsFailed: Int = 0,
    val partsDelivered: Int = 0,
    val subId: Int = -1,
    val read: Boolean = true,
    val seen: Boolean = true,
    val starred: Boolean = false,
    val hasAttachments: Boolean = false,
    val category: String = "PERSONAL",
    val categorySource: String = "RULES",
    val categoryConfidence: Float = 0f,
    val categoryReasons: String = "",
    val spamVerdict: String = "CLEAN",
    val spamScore: Float = 0f,
    val spamReasons: String = "",
    val riskyUrls: String = "",
    val priority: Int = 0,
    val important: Boolean = false,
    val analyzed: Boolean = false,
    val mmsContentLocation: String? = null,
    val mmsTransactionId: String? = null,
    val mmsMessageId: String? = null,
    val mmsExpiry: Long = 0,
    val mmsSize: Long = 0,
    val deliveryReport: Boolean = false,
    val sendAsMms: Boolean = false,
) {
    val isPendingOutgoing: Boolean get() = outgoing && status in MessageStatus.OUTGOING_PENDING
}

/** Full-text index kept in sync with messages.body by Room-generated triggers. */
@Fts4(contentEntity = MessageEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "messages_fts")
data class MessageFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val body: String,
    val subject: String?,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(MessageEntity::class, ["id"], ["messageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val mimeType: String,
    /** content://mms/part/N for provider-backed parts, or a file:// URI in app storage. */
    val uri: String,
    val fileName: String? = null,
    val size: Long = 0,
)

@Entity(
    tableName = "message_entities",
    foreignKeys = [ForeignKey(MessageEntity::class, ["id"], ["messageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("messageId"), Index("type")],
)
data class ExtractedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val type: String,
    val text: String,
    val value: String,
    val start: Int,
    val end: Int,
    val attributes: Map<String, String> = emptyMap(),
    /** "rules" or "mlkit". */
    val source: String = "rules",
)

@Entity(tableName = "corrections", indices = [Index("createdAt")])
data class CorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long?,
    val text: String,
    val senderAddress: String,
    val senderKind: String,
    val fromCategory: String?,
    val category: String?,
    val isSpam: Boolean?,
    val createdAt: Long,
)

@Entity(tableName = "sender_rules")
data class SenderRuleEntity(
    /** [app.yarn.intelligence.LearningState.normalizeAddress] of the sender. */
    @PrimaryKey val address: String,
    val displayAddress: String,
    val category: String? = null,
    /** true = trusted (never spam), false = always spam, null = no preference. */
    val trusted: Boolean? = null,
    val updatedAt: Long,
)

@Entity(tableName = "block_rules", indices = [Index(value = ["type", "value"], unique = true)])
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** NUMBER (normalised digits/alphanumeric id) or KEYWORD. */
    val type: String,
    val value: String,
    val createdAt: Long,
) {
    companion object {
        const val NUMBER = "NUMBER"
        const val KEYWORD = "KEYWORD"
    }
}

data class MessageWithAttachments(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId") val attachments: List<AttachmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "messageId") val entities: List<ExtractedEntity>,
)

data class MessageSearchResult(
    @Embedded val message: MessageEntity,
    val conversationTitle: String,
    val snippet: String,
)
