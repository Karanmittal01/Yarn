package app.yarn.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlanTest {
    private val start = 1_790_000_000_000L
    private val day = Plan.DAY_MS
    private val none = Owned()

    @Test
    fun theTrialCountsDownFiveDays() {
        assertThat(Plan.access(start, start, none)).isEqualTo(Access.Trial(5))
        assertThat(Plan.access(start, start + 1, none)).isEqualTo(Access.Trial(5))
        assertThat(Plan.access(start, start + day, none)).isEqualTo(Access.Trial(4))
        assertThat(Plan.access(start, start + 4 * day + 3_600_000, none)).isEqualTo(Access.Trial(1))
        assertThat(Plan.access(start, start + 5 * day - 1, none)).isEqualTo(Access.Trial(1))
    }

    @Test
    fun afterTheTrialYarnNeedsTheSubscription() {
        assertThat(Plan.access(start, start + 5 * day, none)).isEqualTo(Access.Expired)
        assertThat(Plan.access(start, start + 40 * day, none).usable).isFalse()
        assertThat(Plan.access(start, start + 40 * day, Owned(pending = true))).isEqualTo(Access.Pending)
        assertThat(Plan.access(start, start + 40 * day, Owned(unlocked = true))).isEqualTo(Access.Unlocked())
        assertThat(Plan.access(start, start + 40 * day, Owned(unlocked = true, lifetime = true))).isEqualTo(Access.Unlocked(lifetime = true))
    }

    @Test
    fun buyingDuringTheTrialUnlocksAtOnce() {
        assertThat(Plan.access(start, start + day, Owned(unlocked = true))).isEqualTo(Access.Unlocked())
        assertThat(Plan.access(start, start + day, Owned(pending = true))).isEqualTo(Access.Trial(4, pending = true))
    }

    private fun purchase(product: String, state: PlayPurchase.State, acknowledged: Boolean = true, token: String = product) =
        PlayPurchase(listOf(product), state, acknowledged, token)

    @Test
    fun aPaidSubscriptionUnlocksAndIsAcknowledged() {
        val owned = Plan.owned(listOf(purchase(Plan.SUBSCRIPTION, PlayPurchase.State.PURCHASED, acknowledged = false, token = "t1")))
        assertThat(owned.unlocked).isTrue()
        assertThat(owned.lifetime).isFalse()
        assertThat(owned.toAcknowledge).containsExactly("t1")
    }

    @Test
    fun aTestersCodeUnlocksForLife() {
        val owned = Plan.owned(listOf(purchase(Plan.LIFETIME, PlayPurchase.State.PURCHASED)))
        assertThat(owned.unlocked).isTrue()
        assertThat(owned.lifetime).isTrue()
        assertThat(owned.toAcknowledge).isEmpty()
    }

    @Test
    fun aPendingUpiPaymentDoesNotUnlockYet() {
        val owned = Plan.owned(listOf(purchase(Plan.SUBSCRIPTION, PlayPurchase.State.PENDING, acknowledged = false)))
        assertThat(owned.unlocked).isFalse()
        assertThat(owned.pending).isTrue()
        assertThat(owned.toAcknowledge).isEmpty()
    }

    @Test
    fun otherProductsAreIgnored() {
        assertThat(Plan.owned(listOf(purchase("something_else", PlayPurchase.State.PURCHASED)))).isEqualTo(Owned())
        assertThat(Plan.owned(emptyList())).isEqualTo(Owned())
    }

    @Test
    fun theEarlyBirdPriceIsStruckAgainstTheRegularOne() {
        assertThat(Plan.regularPrice("INR", 199_000_000)).isEqualTo(499_000_000)
        assertThat(Plan.regularPrice("INR", 499_000_000)).isNull()
        assertThat(Plan.regularPrice("INR", 599_000_000)).isNull()
        assertThat(Plan.regularPrice("USD", 2_990_000)).isNull()
        assertThat(Licence.Price("₹199", 199_000_000, "INR").regular).isEqualTo("₹499")
        assertThat(Licence.Price("₹499", 499_000_000, "INR").regular).isNull()
    }
}
