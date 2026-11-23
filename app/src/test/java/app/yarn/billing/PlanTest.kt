package app.yarn.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlanTest {
    private val start = 1_790_000_000_000L
    private val day = Plan.DAY_MS
    private val none = Owned()

    @Test
    fun theProTrialCountsDownThirtyDays() {
        assertThat(Plan.access(start, start, none)).isEqualTo(Access.Trial(30))
        assertThat(Plan.access(start, start + 1, none)).isEqualTo(Access.Trial(30))
        assertThat(Plan.access(start, start + day, none)).isEqualTo(Access.Trial(29))
        assertThat(Plan.access(start, start + 29 * day + 3_600_000, none)).isEqualTo(Access.Trial(1))
        assertThat(Plan.access(start, start + 30 * day - 1, none)).isEqualTo(Access.Trial(1))
        assertThat(Plan.access(start, start + day, none).isPro).isTrue()
    }

    @Test
    fun afterTheTrialYarnStaysFree() {
        assertThat(Plan.access(start, start + 30 * day, none)).isEqualTo(Access.Free())
        assertThat(Plan.access(start, start + 400 * day, none).isPro).isFalse()
        assertThat(Plan.access(start, start + 40 * day, Owned(pending = true))).isEqualTo(Access.Free(pending = true))
        assertThat(Plan.access(start, start + 40 * day, Owned(unlocked = true))).isEqualTo(Access.Pro())
        assertThat(Plan.access(start, start + 40 * day, Owned(unlocked = true, lifetime = true))).isEqualTo(Access.Pro(lifetime = true))
    }

    @Test
    fun buyingDuringTheTrialUnlocksAtOnce() {
        assertThat(Plan.access(start, start + day, Owned(unlocked = true))).isEqualTo(Access.Pro())
        assertThat(Plan.access(start, start + day, Owned(pending = true))).isEqualTo(Access.Trial(29, pending = true))
    }

    @Test
    fun theEarliestRecordedStartWins() {
        assertThat(Plan.earliest(start, start - day, null)).isEqualTo(start - day)
        assertThat(Plan.earliest(null, start)).isEqualTo(start)
        assertThat(Plan.earliest(null, 0L, null)).isNull()
        // Reinstalling keeps the trial's first day: a later local start doesn't restart it.
        val kept = start
        val reinstalled = start + 50 * day
        assertThat(Plan.access(Plan.earliest(reinstalled, kept)!!, reinstalled, none)).isEqualTo(Access.Free())
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
        assertThat(Plan.regularPrice("INR", 199_000_000, Plan.REGULAR_YEARLY_INR_MICROS)).isEqualTo(499_000_000)
        assertThat(Plan.regularPrice("INR", 499_000_000, Plan.REGULAR_YEARLY_INR_MICROS)).isNull()
        assertThat(Plan.regularPrice("INR", 599_000_000, Plan.REGULAR_YEARLY_INR_MICROS)).isNull()
        assertThat(Plan.regularPrice("USD", 2_990_000, Plan.REGULAR_YEARLY_INR_MICROS)).isNull()
        assertThat(Licence.Price("₹199", 199_000_000, "INR").regular).isEqualTo("₹499")
        assertThat(Licence.Price("₹499", 499_000_000, "INR").regular).isNull()
        assertThat(Licence.Price("₹999", 999_000_000, "INR", ProPlan.LIFETIME).regular).isEqualTo("₹2,999")
        assertThat(Licence.Price("₹2,999", 2_999_000_000, "INR", ProPlan.LIFETIME).regular).isNull()
    }
}
