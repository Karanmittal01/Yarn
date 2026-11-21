package app.yarn.billing

/**
 * Yarn is paid: a free trial, then a yearly subscription. People who helped test it hold a
 * lifetime unlock, given only through Play promo codes. Pure rules, so they can be tested
 * without Google Play.
 */
object Plan {
    const val TRIAL_DAYS = 5
    const val DAY_MS = 86_400_000L

    /** The subscription and its yearly base plan, as set up in Play Console. */
    const val SUBSCRIPTION = "yarn"
    const val BASE_PLAN = "yearly"

    /** A one-time product that never shows in the app: testers get it free with a promo code. */
    const val LIFETIME = "yarn_lifetime"

    /** The regular yearly price in India. While Play charges less, the early-bird price shows it struck through. */
    const val REGULAR_INR_MICROS = 499_000_000L

    fun trialEnds(trialStart: Long): Long = trialStart + TRIAL_DAYS * DAY_MS

    fun access(trialStart: Long, now: Long, owned: Owned): Access {
        val end = trialEnds(trialStart)
        return when {
            owned.unlocked -> Access.Unlocked(owned.lifetime)
            now < end -> Access.Trial(((end - now + DAY_MS - 1) / DAY_MS).toInt().coerceIn(1, TRIAL_DAYS), owned.pending)
            owned.pending -> Access.Pending
            else -> Access.Expired
        }
    }

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

    /** The early-bird price struck through against, or null once Play's price reaches it (or outside India). */
    fun regularPrice(currency: String?, micros: Long?): Long? =
        if (currency == "INR" && micros != null && micros in 1 until REGULAR_INR_MICROS) REGULAR_INR_MICROS else null
}

/** What someone may do with Yarn right now. */
sealed interface Access {
    /** The free trial is running; [daysLeft] counts today. A payment may already be on its way. */
    data class Trial(val daysLeft: Int, val pending: Boolean = false) : Access

    /** Subscribed, or holding the testers' lifetime unlock. */
    data class Unlocked(val lifetime: Boolean = false) : Access

    /** The trial is over and Google Play is still confirming a payment (UPI can take a while). */
    data object Pending : Access

    /** The trial is over and nothing was bought. */
    data object Expired : Access

    val usable: Boolean get() = this is Trial || this is Unlocked
}

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
