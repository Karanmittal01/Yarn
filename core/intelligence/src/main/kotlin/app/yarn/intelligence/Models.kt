package app.yarn.intelligence

/** Inbox categories. Order is the default display order. */
enum class Category {
    PERSONAL, WORK, OTP, BANKING, PAYMENTS, BILLS, DELIVERY, SHOPPING, TRAVEL, UPDATES, PROMOTIONS, SPAM;

    val isTransactional: Boolean get() = this == OTP || this == BANKING || this == PAYMENTS || this == BILLS

    companion object {
        fun fromName(name: String?): Category? = entries.firstOrNull { it.name == name }
    }
}

/** How a sender address looks; a strong prior for both categorisation and phishing detection. */
enum class SenderKind {
    /** Saved in the user's contacts. */
    CONTACT,
    /** A regular (long) phone number not in contacts. */
    PHONE_NUMBER,
    /** 3-8 digit short code, typically businesses. */
    SHORT_CODE,
    /** Alphanumeric sender ID such as "AMAZON" or "VM-HDFCBK-S". */
    ALPHANUMERIC,
    /** Email address (e.g. SMS gateways, iMessage-to-SMS bridges). */
    EMAIL,
}

/** Header suffix mandated for commercial SMS in India (TRAI): -P, -S, -T, -G. */
enum class CommercialSuffix { PROMOTIONAL, SERVICE, TRANSACTIONAL, GOVERNMENT }

data class SenderInfo(
    val address: String,
    val isContact: Boolean = false,
    val contactName: String? = null,
    val isStarredContact: Boolean = false,
) {
    val kind: SenderKind by lazy { SenderAnalyzer.kindOf(address, isContact) }
    val commercialSuffix: CommercialSuffix? by lazy { SenderAnalyzer.commercialSuffix(address) }
    val isBusinessLike: Boolean get() = kind == SenderKind.SHORT_CODE || kind == SenderKind.ALPHANUMERIC
}

data class MessageInput(
    val text: String,
    val sender: SenderInfo,
    val timestampMillis: Long,
    val isIncoming: Boolean = true,
    val hasAttachments: Boolean = false,
)

enum class EntityType {
    OTP, AMOUNT, DATE_TIME, URL, EMAIL, PHONE, TRACKING_NUMBER, ORDER_ID, BOOKING_REF,
    FLIGHT, ADDRESS, UPI_ID, ACCOUNT_REF,
}

data class Entity(
    val type: EntityType,
    /** Exact matched text. */
    val text: String,
    /** Normalised value (digits for OTP, ISO-8601 for dates, canonical URL...). */
    val value: String,
    val start: Int,
    val end: Int,
    /** Optional attributes: currency, carrier, direction, expiry minutes... */
    val attributes: Map<String, String> = emptyMap(),
    val confidence: Float = 1f,
)

enum class SpamVerdict {
    /** Nothing suspicious. */
    CLEAN,
    /** Legit bulk marketing. */
    PROMOTIONAL,
    /** Unwanted/junk. */
    SPAM,
    /** Likely fraud: phishing link, impersonation, credential harvesting. */
    SCAM;

    val isFiltered: Boolean get() = this == SPAM || this == SCAM
}

data class SpamAssessment(
    val verdict: SpamVerdict,
    /** 0..1 */
    val score: Float,
    val reasons: List<String>,
    /** URLs judged dangerous; the UI warns before opening them. */
    val riskyUrls: Set<String> = emptySet(),
)

data class Classification(
    val category: Category,
    /** 0..1 */
    val confidence: Float,
    /** Human-readable explanation shown in "Why this category?". */
    val reasons: List<String>,
    val source: Source,
) {
    enum class Source { RULES, LEARNED, SENDER_RULE, SIMILAR_CORRECTION }
}

data class Analysis(
    val classification: Classification,
    val spam: SpamAssessment,
    val entities: List<Entity>,
    /** 0..100 */
    val priority: Int,
    val isImportant: Boolean,
) {
    val otp: Entity? get() = entities.firstOrNull { it.type == EntityType.OTP }
    val category: Category get() = if (spam.verdict.isFiltered) Category.SPAM else classification.category
}
