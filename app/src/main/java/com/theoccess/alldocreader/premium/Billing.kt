package com.theoccess.alldocreader.premium

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.theoccess.alldocreader.data.Prefs
import java.text.NumberFormat
import java.util.Currency
import kotlin.math.roundToInt

/**
 * Google Play Billing for Premium.
 *
 * Play Console setup (Monetize → Subscriptions): recommended is one subscription with product id
 * [PRODUCT_ID] and two base plans — [PLAN_YEARLY] (auto-renewing, 1 year, with a 7-day free-trial
 * offer for new customers) and [PLAN_MONTHLY] (auto-renewing, 1 month). Two separate
 * subscriptions (e.g. "premium_yearly" / "premium_monthly", see [PRODUCT_IDS]) work too: plans are
 * recognised by their billing period (1 year / 1 month), so base plan ids can be anything. Prices shown in the app come from
 * Google Play in the user's own currency; until they load, the fallback texts in strings.xml are shown.
 */
object Billing : PurchasesUpdatedListener {

    private const val TAG = "Billing"
    const val PRODUCT_ID = "premium"
    const val PLAN_YEARLY = "yearly"
    const val PLAN_MONTHLY = "monthly"

    /** Subscription product ids looked up in Google Play (put your own ids here if they differ). */
    val PRODUCT_IDS = listOf(PRODUCT_ID, "premium_yearly", "premium_monthly", "yearly", "monthly", "premium_subscription")

    /** Why the last purchase could not start (shown on test builds so setup problems are obvious). */
    var lastError: String? = null
        private set

    enum class Plan { YEARLY, MONTHLY }

    enum class Result { SUCCESS, CANCELED, FAILED }

    /** What a plan costs, ready for display. */
    data class Price(
        val formatted: String,      // "Rs 5,600.00"
        val perDay: String,         // "Rs 15.34"
        val micros: Long,
        val currency: String,
        val trialDays: Int,         // 0 = no free trial offered
        val offerToken: String
    )

    private val main = Handler(Looper.getMainLooper())
    private var client: BillingClient? = null
    private val planDetails = HashMap<Plan, ProductDetails>()
    private var packageName = ""
    private var productsLoaded = false
    private var pending: ((Result) -> Unit)? = null

    private val _premium = MutableLiveData(false)
    val premium: LiveData<Boolean> = _premium

    private val _prices = MutableLiveData<Map<Plan, Price>>(emptyMap())
    val prices: LiveData<Map<Plan, Price>> = _prices

    val isPremium: Boolean get() = _premium.value == true

    /** Called from App.onCreate: connects, loads prices and re-checks the subscription. */
    fun init(context: Context) {
        _premium.value = Prefs.isPremium
        packageName = context.packageName
        if (client != null) return
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect { ok -> if (ok) { loadProducts(); refreshPurchases(null) } }
    }

    private val waiting = ArrayList<(Boolean) -> Unit>()

    /** Connects once; calls made while connecting wait for the same result. */
    private fun connect(done: (Boolean) -> Unit) {
        val c = client ?: return done(false)
        if (c.isReady) return done(true)
        waiting += done
        if (waiting.size > 1) return
        val finish = { ok: Boolean ->
            val list = ArrayList(waiting)
            waiting.clear()
            list.forEach { it(ok) }
        }
        try {
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        lastError = "Google Play Billing is not available on this device (code ${result.responseCode}${result.debugMessage.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}). " +
                            "Make sure the Play Store app is installed, updated and signed in."
                        Log.w(TAG, lastError!!)
                    }
                    main.post { finish(result.responseCode == BillingClient.BillingResponseCode.OK) }
                }
                override fun onBillingServiceDisconnected() {
                    main.post { if (waiting.isNotEmpty()) finish(false) }
                }
            })
        } catch (e: Exception) {
            finish(false)
        }
    }

    private fun loadProducts(done: (() -> Unit)? = null) {
        val c = client ?: return done?.invoke() ?: Unit
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            PRODUCT_IDS.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
        ).build()
        c.queryProductDetailsAsync(params) { result, list ->
            main.post {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    productsLoaded = true
                    _prices.value = buildPrices(list)
                    if (planDetails.isEmpty()) {
                        lastError = "Google Play returned no subscription for ids ${PRODUCT_IDS.joinToString()}. " +
                            "Create and ACTIVATE the subscription in Play Console, and install this app from a Google Play " +
                            "testing track (internal testing) with the same package name ($packageName) and a signed build."
                        Log.w(TAG, lastError!!)
                    }
                } else {
                    lastError = "Google Play could not load the subscription (code ${result.responseCode}): ${result.debugMessage}"
                    Log.w(TAG, lastError!!)
                }
                done?.invoke()
            }
        }
    }

    /** Recognises the yearly / monthly plan of every returned subscription by its billing period. */
    private fun buildPrices(products: List<ProductDetails>): Map<Plan, Price> {
        planDetails.clear()
        val out = HashMap<Plan, Price>()
        val candidates = products.flatMap { d -> d.subscriptionOfferDetails.orEmpty().map { d to it } }
        for ((plan, days) in listOf(Plan.YEARLY to 365.0, Plan.MONTHLY to 30.0)) {
            val forPlan = candidates.filter { (_, o) ->
                val paid = o.pricingPhases.pricingPhaseList.lastOrNull { it.priceAmountMicros > 0 }
                val period = paid?.billingPeriod.orEmpty()
                val byId = o.basePlanId.lowercase()
                when (plan) {
                    Plan.YEARLY -> period == "P1Y" || period == "P12M" || byId.contains("year") || byId.contains("annual")
                    Plan.MONTHLY -> period == "P1M" || byId.contains("month")
                }
            }
            if (forPlan.isEmpty()) continue
            // Play only returns offers the user is eligible for: prefer the one with a free trial
            val (product, chosen) = forPlan.firstOrNull { (_, o) -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
                ?: forPlan.firstOrNull { (_, o) -> o.offerId == null } ?: forPlan.first()
            val phases = chosen.pricingPhases.pricingPhaseList
            val paid = phases.lastOrNull { it.priceAmountMicros > 0 } ?: continue
            val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
            planDetails[plan] = product
            out[plan] = Price(
                formatted = paid.formattedPrice,
                perDay = format(paid.priceAmountMicros / 1_000_000.0 / days, paid.priceCurrencyCode),
                micros = paid.priceAmountMicros,
                currency = paid.priceCurrencyCode,
                trialDays = trial?.let { periodDays(it.billingPeriod) } ?: 0,
                offerToken = chosen.offerToken
            )
        }
        return out
    }

    /** "P7D" → 7, "P1W" → 7, "P1M" → 30 … */
    private fun periodDays(iso: String): Int {
        val m = Regex("P(\\d+)([DWMY])").find(iso) ?: return 0
        val n = m.groupValues[1].toInt()
        return when (m.groupValues[2]) { "D" -> n; "W" -> n * 7; "M" -> n * 30; else -> n * 365 }
    }

    private fun format(amount: Double, currency: String): String = try {
        NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency) }.format(amount)
    } catch (e: Exception) {
        String.format("%.2f %s", amount, currency)
    }

    /** "Save 67%": yearly compared with 12 months of the monthly plan. */
    fun savePercent(prices: Map<Plan, Price>): Int? {
        val y = prices[Plan.YEARLY] ?: return null
        val m = prices[Plan.MONTHLY] ?: return null
        if (y.currency != m.currency || m.micros <= 0) return null
        return ((1.0 - y.micros.toDouble() / (m.micros * 12.0)) * 100).roundToInt().takeIf { it > 0 }
    }

    /** Opens Google Play's purchase sheet (payment method / card, confirm). */
    fun buy(activity: Activity, plan: Plan, done: (Result) -> Unit) {
        connect { ok ->
            if (!ok) return@connect done(Result.FAILED)
            val launch = launch@{
                val d = planDetails[plan]
                val price = _prices.value?.get(plan)
                if (d == null || price == null) {
                    if (productsLoaded && planDetails.isNotEmpty()) lastError = "Google Play has no ${plan.name.lowercase()} plan for this subscription."
                    return@launch done(Result.FAILED)
                }
                val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(d)
                            .setOfferToken(price.offerToken)
                            .build()
                    )
                ).build()
                pending = done
                val r = client?.launchBillingFlow(activity, params)
                if (r == null || r.responseCode != BillingClient.BillingResponseCode.OK) {
                    pending = null
                    if (r != null && r.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                        lastError = "Google Play refused to open the payment sheet (code ${r.responseCode}): ${r.debugMessage}"
                        Log.w(TAG, lastError!!)
                    }
                    done(if (r?.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) Result.CANCELED else Result.FAILED)
                }
            }
            lastError = null
            if (planDetails[plan] == null) loadProducts { launch() } else launch()
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        main.post {
            val cb = pending
            pending = null
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    handle(purchases.orEmpty())
                    cb?.invoke(if (isPremium) Result.SUCCESS else Result.FAILED)
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases { cb?.invoke(if (it) Result.SUCCESS else Result.FAILED) }
                BillingClient.BillingResponseCode.USER_CANCELED -> cb?.invoke(Result.CANCELED)
                else -> {
                    lastError = "Purchase not completed (code ${result.responseCode}): ${result.debugMessage}"
                    cb?.invoke(Result.FAILED)
                }
            }
        }
    }

    /** "Restore": asks Google Play for an active subscription on this account. */
    fun refreshPurchases(done: ((Boolean) -> Unit)?) {
        connect { ok ->
            val c = client
            if (!ok || c == null) return@connect done?.invoke(isPremium) ?: Unit
            c.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            ) { result, list ->
                main.post {
                    val active = list.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && isOurs(it) }
                    if (result.responseCode == BillingClient.BillingResponseCode.OK && !active) setPremium(false)
                    handle(list)
                    done?.invoke(isPremium)
                }
            }
        }
    }

    private fun isOurs(p: Purchase) =
        p.purchaseState == Purchase.PurchaseState.PURCHASED &&
            p.products.any { id -> id in PRODUCT_IDS || planDetails.values.any { it.productId == id } }

    private fun handle(purchases: List<Purchase>) {
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && isOurs(it) }.forEach { p ->
            setPremium(true)
            if (!p.isAcknowledged) {
                client?.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                ) { }
            }
        }
    }

    /** Debug builds only: the test payment sheet's "Subscribe (test)" turns Premium on. */
    fun setTestPremium() {
        if (com.theoccess.alldocreader.BuildConfig.DEBUG) setPremium(true)
    }

    private fun setPremium(on: Boolean) {
        Prefs.isPremium = on
        _premium.value = on
    }
}
