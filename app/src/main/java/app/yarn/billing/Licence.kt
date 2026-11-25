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
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Yarn Pro, through Google Play Billing: a 30-day reverse trial, then yearly or lifetime. There is
 * no Yarn server. Google Play is asked on every start and the answer is kept on the phone, so Yarn
 * also works offline. The trial's start is remembered on the phone, in Google's Block Store (which
 * survives reinstalling) and, once backup is on, in the user's own Google Drive.
 */
class Licence(private val context: Context, private val scope: CoroutineScope) : PurchasesUpdatedListener {

    /** A price as Google Play sells it here, e.g. "₹199". */
    data class Price(val formatted: String, val micros: Long, val currency: String, val plan: ProPlan = ProPlan.YEARLY) {
        /** The regular price, struck through while the early-bird price is lower. */
        val regular: String?
            get() = Plan.regularPrice(currency, micros, if (plan == ProPlan.LIFETIME) Plan.REGULAR_LIFETIME_INR_MICROS else Plan.REGULAR_YEARLY_INR_MICROS)
                ?.let { "₹" + java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(it / 1_000_000) }
    }

    data class Prices(val yearly: Price? = null, val lifetime: Price? = null)

    enum class Result { STARTED, UNAVAILABLE, FAILED }
    enum class Event { FAILED }

    private val prefs = context.getSharedPreferences("licence", Context.MODE_PRIVATE)
    private val _access = MutableStateFlow<Access>(if (BuildConfig.DEBUG) Access.Pro() else compute(cachedOwned()))
    val access: StateFlow<Access> = _access.asStateFlow()
    private val _prices = MutableStateFlow(cachedPrices())
    val prices: StateFlow<Prices> = _prices.asStateFlow()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /** Set when something asks for Pro (a locked tab or feature): the app shows the Pro page. */
    private val _askPro = MutableStateFlow(false)
    val askPro: StateFlow<Boolean> = _askPro.asStateFlow()

    private val lock = Mutex()
    private var yearly: ProductDetails? = null
    private var yearlyOffer: String? = null
    private var lifetime: ProductDetails? = null
    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    /** Pro right now: during the trial or once bought. */
    val isPro: Boolean get() = _access.value.isPro

    /** Opens the Pro page, e.g. from a locked feature. */
    fun requestPro() { _askPro.value = true }
    fun proShown() { _askPro.value = false }

    /** The card that welcomes someone to their Pro trial is shown until they close it. */
    val welcomeSeen: Boolean get() = prefs.getBoolean(K_WELCOME_SEEN, false)
    fun markWelcomeSeen() = prefs.edit { putBoolean(K_WELCOME_SEEN, true) }

    /** The page that says Pro ended is shown once, the first time Yarn opens on the free plan. */
    val downgradeSeen: Boolean get() = prefs.getBoolean(K_DOWNGRADE_SEEN, false)
    fun markDowngradeSeen() = prefs.edit { putBoolean(K_DOWNGRADE_SEEN, true) }

    fun start() {
        // Builds from Android Studio aren't from Google Play, so they can't be bought; they stay Pro.
        if (BuildConfig.DEBUG) return
        tick()
        scope.launch {
            anchorTrial()
            refresh()
        }
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
            loadProducts()
            val subs = query(ProductType.SUBS)
            val inApp = query(ProductType.INAPP)
            if (subs == null || inApp == null) return@withLock false.also { tick() }
            apply(subs + inApp)
            true
        }
    }

    /** Opens Google Play's payment sheet for [plan]. */
    suspend fun buy(activity: Activity, plan: ProPlan): Result {
        if (BuildConfig.DEBUG) return Result.UNAVAILABLE
        val ready = lock.withLock { connect() && loadProducts() }
        if (!ready) return Result.UNAVAILABLE
        return when (plan) {
            ProPlan.YEARLY -> {
                val d = yearly ?: return Result.UNAVAILABLE
                launch(activity, d, yearlyOffer ?: return Result.UNAVAILABLE)
            }
            ProPlan.LIFETIME -> {
                val d = lifetime ?: return Result.UNAVAILABLE
                launch(activity, d, d.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken)
            }
        }
    }

    /**
     * Redeems a tester's code inside Google's payment sheet for the lifetime unlock (its payment
     * method menu has "Redeem code"). The purchase comes straight back to Yarn, so Pro unlocks at once.
     */
    suspend fun redeem(activity: Activity): Result = buy(activity, ProPlan.LIFETIME)

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

    // ---- The trial's start, remembered in three places ---------------------------------------

    /** Brings in the start Block Store kept (it survives reinstalling) and records it everywhere. */
    private suspend fun anchorTrial() {
        val kept = withTimeoutOrNull(10_000) { runCatching { readBlockStore() }.getOrNull() }
        val start = Plan.earliest(storedTrialStart(), kept) ?: now()
        setTrialStart(start)
        if (kept != start) runCatching { writeBlockStore(start) }
    }

    /** Called with a Google Drive token (setup, backup): the account's own record of the trial. */
    suspend fun anchorTrialInDrive(drive: app.yarn.backup.GoogleDrive, token: String) {
        if (BuildConfig.DEBUG) return
        val remote = runCatching { drive.trialStart(token) }.getOrNull()
        val start = Plan.earliest(storedTrialStart(), remote) ?: now()
        if (start != storedTrialStart()) {
            setTrialStart(start)
            runCatching { writeBlockStore(start) }
        }
        if (remote != start) runCatching { drive.saveTrialStart(token, start) }
    }

    private fun storedTrialStart(): Long? = prefs.getLong(K_TRIAL, 0).takeIf { it > 0 }

    private fun setTrialStart(start: Long) {
        prefs.edit { putLong(K_TRIAL, start) }
        tick()
    }

    private suspend fun readBlockStore(): Long? {
        val response = Blockstore.getClient(context)
            .retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(BLOCKSTORE_KEY)).build())
            .await()
        return response.blockstoreDataMap[BLOCKSTORE_KEY]?.bytes?.decodeToString()?.trim()?.toLongOrNull()
    }

    private suspend fun writeBlockStore(start: Long) {
        Blockstore.getClient(context).storeBytes(
            StoreBytesData.Builder().setKey(BLOCKSTORE_KEY).setBytes(start.toString().toByteArray()).setShouldBackupToCloud(true).build(),
        ).await()
    }

    // ---- Purchases ----------------------------------------------------------------------------

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

    // Until the trial's start is known for sure, it counts from now: Pro, never locked out by mistake.
    private fun compute(owned: Owned): Access = Plan.access(storedTrialStart() ?: now(), now(), owned)

    private fun cachedOwned() = Owned(
        unlocked = prefs.getBoolean(K_UNLOCKED, false),
        lifetime = prefs.getBoolean(K_LIFETIME, false),
        pending = prefs.getBoolean(K_PENDING, false),
    )

    private fun cachedPrices(): Prices {
        fun read(prefix: String, plan: ProPlan): Price? {
            val formatted = prefs.getString("${prefix}_price", null) ?: return null
            return Price(formatted, prefs.getLong("${prefix}_micros", 0), prefs.getString("${prefix}_currency", null).orEmpty(), plan)
        }
        return Prices(read("yearly", ProPlan.YEARLY), read("lifetime", ProPlan.LIFETIME))
    }

    private fun savePrice(prefix: String, price: Price) = prefs.edit {
        putString("${prefix}_price", price.formatted)
        putLong("${prefix}_micros", price.micros)
        putString("${prefix}_currency", price.currency)
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

    /** Loads both plans and their prices; true when the yearly plan (the main one) is ready. */
    private suspend fun loadProducts(): Boolean {
        if (yearly == null || yearlyOffer == null) {
            queryProduct(Plan.SUBSCRIPTION, ProductType.SUBS)?.let { found ->
                // The plain yearly plan: no special offers, so everyone sees the same price.
                val offer = found.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == Plan.BASE_PLAN }
                    .let { offers -> offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() }
                val phase = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()
                if (offer != null && phase != null) {
                    yearly = found
                    yearlyOffer = offer.offerToken
                    val price = Price(phase.formattedPrice, phase.priceAmountMicros, phase.priceCurrencyCode, ProPlan.YEARLY)
                    _prices.value = _prices.value.copy(yearly = price)
                    savePrice("yearly", price)
                }
            }
        }
        if (lifetime == null) {
            queryProduct(Plan.LIFETIME, ProductType.INAPP)?.let { found ->
                val offer = found.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: found.oneTimePurchaseOfferDetails
                lifetime = found
                if (offer != null) {
                    val price = Price(offer.formattedPrice, offer.priceAmountMicros, offer.priceCurrencyCode, ProPlan.LIFETIME)
                    _prices.value = _prices.value.copy(lifetime = price)
                    savePrice("lifetime", price)
                }
            }
        }
        return yearly != null && yearlyOffer != null
    }

    private suspend fun queryProduct(id: String, type: String): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()))
            .build()
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<ProductDetails?> { cont ->
                client.queryProductDetailsAsync(params) { result, list ->
                    if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) list.productDetailsList.firstOrNull() else null)
                }
            }
        }
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
        /** A new key for the 30-day Pro trial: everyone, existing users included, starts it fresh. */
        private const val K_TRIAL = "pro_trial_start"
        private const val BLOCKSTORE_KEY = "yarn_pro_trial_start"
        private const val K_SEEN = "clock_seen"
        private const val K_UNLOCKED = "unlocked"
        private const val K_LIFETIME = "lifetime"
        private const val K_PENDING = "pending"
        private const val K_DOWNGRADE_SEEN = "downgrade_seen"
        private const val K_WELCOME_SEEN = "pro_welcome_seen"

        /** Google Play's page for managing or cancelling the subscription. */
        fun manageIntent(context: Context): Intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions?sku=${Plan.SUBSCRIPTION}&package=${context.packageName}"),
        )

        /** Redeeming a promo code in the Play Store app. */
        fun redeemIntent(): Intent = redeemOnWeb().setPackage("com.android.vending")

        /** The same page in a browser, for phones without the Play Store app. */
        fun redeemOnWeb(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem"))
    }
}
