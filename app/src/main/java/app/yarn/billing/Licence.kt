package app.yarn.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import app.yarn.BuildConfig
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Yarn's trial and subscription, through Google Play Billing. There is no Yarn server: Google Play
 * is asked on every start, and the answer is kept on the phone so Yarn also works offline.
 */
class Licence(private val context: Context, private val scope: CoroutineScope) : PurchasesUpdatedListener {

    /** The yearly price as Google Play sells it here, e.g. "₹199". */
    data class Price(val formatted: String, val micros: Long, val currency: String) {
        /** The regular price, struck through while the early-bird price is lower. */
        val regular: String? get() = Plan.regularPrice(currency, micros)?.let { "₹" + it / 1_000_000 }
    }

    enum class Result { STARTED, UNAVAILABLE, FAILED }
    enum class Event { FAILED }

    private val prefs = context.getSharedPreferences("licence", Context.MODE_PRIVATE)
    private val _access = MutableStateFlow(if (BuildConfig.DEBUG) Access.Unlocked() else compute(cachedOwned()))
    val access: StateFlow<Access> = _access.asStateFlow()
    private val _price = MutableStateFlow(cachedPrice())
    val price: StateFlow<Price?> = _price.asStateFlow()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val lock = Mutex()
    private var details: ProductDetails? = null
    private var lifetime: ProductDetails? = null
    private var offerToken: String? = null
    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    fun start() {
        // Builds from Android Studio aren't from Google Play, so they can't be bought; they stay open.
        if (BuildConfig.DEBUG) return
        if (!prefs.contains(K_TRIAL)) prefs.edit { putLong(K_TRIAL, now()) }
        tick()
        scope.launch { refresh() }
        // The trial counts down in days and ends at a set time, even while Yarn stays open.
        scope.launch {
            while (isActive) {
                delay(10 * 60_000L)
                tick()
            }
        }
    }

    /** Asks Google Play what this account owns. Returns false when Play couldn't be reached. */
    suspend fun refresh(): Boolean {
        if (BuildConfig.DEBUG) return true
        return lock.withLock {
            if (!connect()) return@withLock false.also { tick() }
            loadProduct()
            val subs = query(ProductType.SUBS)
            val inApp = query(ProductType.INAPP)
            if (subs == null || inApp == null) return@withLock false.also { tick() }
            apply(subs + inApp)
            true
        }
    }

    /** Opens Google Play's payment sheet for the yearly plan. */
    suspend fun buy(activity: Activity): Result {
        if (BuildConfig.DEBUG) return Result.UNAVAILABLE
        val ready = lock.withLock { connect() && loadProduct() }
        val d = details
        val token = offerToken
        if (!ready || d == null || token == null) return Result.UNAVAILABLE
        return launch(activity, d, token)
    }

    /**
     * Redeems a tester's code inside Google's payment sheet for the lifetime unlock (its payment
     * method menu has "Redeem code"). The purchase comes straight back to Yarn, so it unlocks at once,
     * with no browser and no waiting for the Play Store to catch up.
     */
    suspend fun redeem(activity: Activity): Result {
        if (BuildConfig.DEBUG) return Result.UNAVAILABLE
        val ready = lock.withLock { connect() && loadLifetime() }
        val d = lifetime
        if (!ready || d == null) return Result.UNAVAILABLE
        return launch(activity, d, d.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken)
    }

    private suspend fun launch(activity: Activity, product: ProductDetails, offerToken: String?): Result {
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        if (offerToken != null) item.setOfferToken(offerToken)
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item.build())).build()
        val r = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, params) }
        return when (r.responseCode) {
            BillingResponseCode.OK -> Result.STARTED
            BillingResponseCode.ITEM_ALREADY_OWNED -> Result.STARTED.also { refresh() }
            else -> Result.FAILED
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK, BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh() }
            BillingResponseCode.USER_CANCELED -> Unit
            else -> _events.tryEmit(Event.FAILED)
        }
    }

    /** Recomputes from what's stored: the trial's days left, or its end. */
    fun tick() {
        if (!BuildConfig.DEBUG) _access.value = compute(cachedOwned())
    }

    private fun apply(purchases: List<Purchase>) {
        val owned = Plan.owned(purchases.map { it.toPlay() })
        prefs.edit {
            putBoolean(K_UNLOCKED, owned.unlocked)
            putBoolean(K_LIFETIME, owned.lifetime)
            putBoolean(K_PENDING, owned.pending)
        }
        _access.value = compute(owned)
        owned.toAcknowledge.forEach { token ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { }
        }
    }

    private fun compute(owned: Owned): Access = Plan.access(prefs.getLong(K_TRIAL, now()), now(), owned)

    private fun cachedOwned() = Owned(
        unlocked = prefs.getBoolean(K_UNLOCKED, false),
        lifetime = prefs.getBoolean(K_LIFETIME, false),
        pending = prefs.getBoolean(K_PENDING, false),
    )

    private fun cachedPrice(): Price? {
        val formatted = prefs.getString(K_PRICE, null) ?: return null
        return Price(formatted, prefs.getLong(K_PRICE_MICROS, 0), prefs.getString(K_CURRENCY, null).orEmpty())
    }

    /** The phone's clock, but never earlier than Yarn has already seen: turning it back doesn't extend the trial. */
    private fun now(): Long {
        val t = System.currentTimeMillis()
        val seen = prefs.getLong(K_SEEN, 0)
        if (t > seen) prefs.edit { putLong(K_SEEN, t) }
        return maxOf(t, seen)
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
        } ?: false
    }

    /** Loads the yearly plan and its price once. */
    private suspend fun loadProduct(): Boolean {
        if (details != null && offerToken != null) return true
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(Plan.SUBSCRIPTION).setProductType(ProductType.SUBS).build()))
            .build()
        val found = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<ProductDetails?> { cont ->
                client.queryProductDetailsAsync(params) { result, list ->
                    if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) list.productDetailsList.firstOrNull() else null)
                }
            }
        } ?: return false
        // The plain yearly plan: no special offers, so everyone sees the same price.
        val offer = found.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == Plan.BASE_PLAN }
            .let { offers -> offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() } ?: return false
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return false
        details = found
        offerToken = offer.offerToken
        val price = Price(phase.formattedPrice, phase.priceAmountMicros, phase.priceCurrencyCode)
        _price.value = price
        prefs.edit {
            putString(K_PRICE, price.formatted)
            putLong(K_PRICE_MICROS, price.micros)
            putString(K_CURRENCY, price.currency)
        }
        return true
    }

    /** The testers' lifetime unlock, only ever opened to redeem a code. */
    private suspend fun loadLifetime(): Boolean {
        if (lifetime != null) return true
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(Plan.LIFETIME).setProductType(ProductType.INAPP).build()))
            .build()
        lifetime = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<ProductDetails?> { cont ->
                client.queryProductDetailsAsync(params) { result, list ->
                    if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) list.productDetailsList.firstOrNull() else null)
                }
            }
        }
        return lifetime != null
    }

    private suspend fun query(type: String): List<Purchase>? = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine<List<Purchase>?> { cont ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, list ->
                if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) list else null)
            }
        }
    }

    private fun Purchase.toPlay() = PlayPurchase(
        products = products,
        state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PlayPurchase.State.PURCHASED
            Purchase.PurchaseState.PENDING -> PlayPurchase.State.PENDING
            else -> PlayPurchase.State.OTHER
        },
        acknowledged = isAcknowledged,
        token = purchaseToken,
    )

    companion object {
        private const val K_TRIAL = "trial_start"
        private const val K_SEEN = "clock_seen"
        private const val K_UNLOCKED = "unlocked"
        private const val K_LIFETIME = "lifetime"
        private const val K_PENDING = "pending"
        private const val K_PRICE = "price"
        private const val K_PRICE_MICROS = "price_micros"
        private const val K_CURRENCY = "price_currency"

        /** Google Play's page for managing or cancelling the subscription. */
        fun manageIntent(context: Context): Intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions?sku=${Plan.SUBSCRIPTION}&package=${context.packageName}"),
        )

        /** Redeeming a promo code: in the Play Store app, where codes for Yarn's products work. */
        fun redeemIntent(): Intent = redeemOnWeb().setPackage("com.android.vending")

        /** The same page in a browser, for phones without the Play Store app. */
        fun redeemOnWeb(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem"))
    }
}
