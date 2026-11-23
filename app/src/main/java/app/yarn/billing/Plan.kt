package app.yarn.billing

/**
 * Yarn is free; Yarn Pro adds the rest. Everyone gets Pro free for the first 30 days (a "reverse
 * trial"), then keeps the free app unless they buy Pro: yearly, or once for life. Testers hold a
 * lifetime unlock given through Play promo codes. Pure rules, so they can be tested without Play.
 */
object Plan {
    const val TRIAL_DAYS = 30
    const val DAY_MS = 86_400_000L

    /** The yearly subscription and its base plan, as set up in Play Console. */
    const val SUBSCRIPTION = "yarn"
    const val BASE_PLAN = "yearly"

    /** Pro for life: bought once, or given free with a promo code. */
    const val LIFETIME = "yarn_lifetime"

    /** Regular prices in India. While Play charges less, the early-bird price is shown against them. */
    const val REGULAR_YEARLY_INR_MICROS = 499_000_000L
    const val REGULAR_LIFETIME_INR_MICROS = 2_999_000_000L

    fun trialEnds(trialStart: Long): Long = trialStart + TRIAL_DAYS * DAY_MS

    fun access(trialStart: Long, now: Long, owned: Owned): Access {
        val end = trialEnds(trialStart)
        return when {
            owned.unlocked -> Access.Pro(owned.lifetime)
            now < end -> Access.Trial(((end - now + DAY_MS - 1) / DAY_MS).toInt().coerceIn(1, TRIAL_DAYS), owned.pending)
            else -> Access.Free(owned.pending)
        }
    }

    /**
     * The trial began at the earliest start any record remembers: this phone, Google's Block Store
     * (kept across reinstalls) or the user's Google Drive. Reinstalling never starts a new trial.
     */
    fun earliest(vararg starts: Long?): Long? = starts.filterNotNull().filter { it > 0 }.minOrNull()

    /** What the purchases Google Play reports add up to. */
    fun owned(purchases: List<PlayPurchase>): Owned {
        val ours = purchases.filter { p -> p.products.any { it == SUBSCRIPTION || it == LIFETIME } }
        val paid = ours.filter { it.state == PlayPurchase.State.PURCHASED }
        return Owned(
            unlocked = paid.isNotEmpty(),
            lifetime = paid.any { LIFETIME in it.products },
            pending = ours.any { it.state == PlayPurchase.State.PENDING },
            // Play refunds anything not acknowledged within three days.
            toAcknowledge = paid.filter { !it.acknowledged }.map { it.token },
        )
    }

    /** The regular price struck through next to Play's, or null once Play charges that much (or outside India). */
    fun regularPrice(currency: String?, micros: Long?, regular: Long): Long? =
        if (currency == "INR" && micros != null && micros in 1 until regular) regular else null
}

/** What someone has right now. */
sealed interface Access {
    /** The first 30 days: all of Pro, free. [daysLeft] counts today. A payment may already be on its way. */
    data class Trial(val daysLeft: Int, val pending: Boolean = false) : Access

    /** Bought yearly or for life, or holding a tester's lifetime code. */
    data class Pro(val lifetime: Boolean = false) : Access

    /** After the trial: the free app. A payment may be on its way (UPI can take a while). */
    data class Free(val pending: Boolean = false) : Access

    val isPro: Boolean get() = this is Trial || this is Pro
}

/** The two ways to buy Pro. */
enum class ProPlan { YEARLY, LIFETIME }

data class Owned(
    val unlocked: Boolean = false,
    val lifetime: Boolean = false,
    val pending: Boolean = false,
    val toAcknowledge: List<String> = emptyList(),
)

/** A purchase as Google Play reports it, reduced to what decides access. */
data class PlayPurchase(val products: List<String>, val state: State, val acknowledged: Boolean, val token: String) {
    enum class State { PURCHASED, PENDING, OTHER }
}
