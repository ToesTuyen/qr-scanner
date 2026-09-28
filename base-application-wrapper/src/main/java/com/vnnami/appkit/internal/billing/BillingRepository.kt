package com.vnnami.appkit.internal.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.brian.base_iap.iapLib.BillingProcessor
import com.brian.base_iap.iapLib.PurchaseInfo
// Use the wrapper's IAPUtils (com.vnnami.appkit.internal.billing.IAPUtils), not the AAR's
// (com.brian.base_iap.utils.IAPUtils). The wrapper mirrors the AAR's public surface
// AND adds INAPP one-time purchase support (callPurchase) which the AAR omits.
import com.vnnami.appkit.internal.billing.IAPUtils
import com.vnnami.appkit.BuildConfig
import com.vnnami.appkit.R
import com.vnnami.appkit.api.Logger
import com.vnnami.appkit.internal.ads.RemoteConfigProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
/**
 * Play Billing for the Compose paywall: resolves subscription and credit-pack prices into
 * StateFlows, launches purchase flows, and republishes what the user already owns.
 *
 * A `@Singleton`, but [init] is written to survive being called more than once — several callers
 * legitimately want to be sure the connection is up.
 */
internal class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class RestoreScope { ALL, ONE_TIME, SUBSCRIPTION }

    // Fallback prices shown until BillingClient queries Play Store and overrides them.
    // Target US tiers: yearly $199.99 / monthly $29.99 / weekly $9.99.
    // BillingClient replaces these placeholders with localized Play prices.
    private val _yearlyPrice = MutableStateFlow("$199.99")
    val yearlyPrice: StateFlow<String> = _yearlyPrice.asStateFlow()

    private val _monthlyPrice = MutableStateFlow("$29.99")
    val monthlyPrice: StateFlow<String> = _monthlyPrice.asStateFlow()

    private val _weeklyPrice = MutableStateFlow("$9.99")
    val weeklyPrice: StateFlow<String> = _weeklyPrice.asStateFlow()

    private val _monthlyEquivalent = MutableStateFlow("")
    val monthlyEquivalent: StateFlow<String> = _monthlyEquivalent.asStateFlow()


    // Credit-pack (one-time INAPP) prices now come 100% from Google Play — NO backend /sku.
    // Map: productId -> Play-localized price text (SkuDetails.priceText, e.g. "395.000 ₫").
    // Empty until loadCreditPackPrices() resolves; the shop maps credit amounts onto these.
    private val _creditPackPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val creditPackPrices: StateFlow<Map<String, String>> = _creditPackPrices.asStateFlow()
    /** One-time managed product ids — MUST match Play Console INAPP product ids + backend skus.py. */
    private val creditPackSkus = listOf("credit_1", "credit_2", "credit_3", "credit_4", "credit_5")

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // Emits only after the host has verified the Play purchase with its backend and granted
    // the matching entitlement. UI can collect this to dismiss the paywall safely.
    private val _purchaseSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val purchaseSuccess: SharedFlow<Unit> = _purchaseSuccess.asSharedFlow()

    // Emits the (productId, purchaseToken) tuple right after Play confirms a successful
    // purchase. Host app observes this and POSTs to backend (e.g. /subs/android or
    // /quota/android) to convert Play-side payment into server-side entitlement.
    // Without this hand-off, the server NEVER hears about the purchase from the client —
    // RTDN alone can't grant credits because it doesn't carry our DEVICECODE.
    data class PurchaseEvent(val productId: String, val purchaseToken: String)
    // replay MATTERS: the purchase completes inside the library's own IapActivity, so the host's
    // collector may not be subscribed at emit time (its Activity can be destroyed while the paywall
    // is in front). A replay-less SharedFlow drops the emission outright — tryEmit still reports
    // success — and the paid token is then only recoverable on the NEXT process start via
    // republishOwnedPurchases. Replaying is safe because the backend de-duplicates by
    // UNIQUE(purchase_token) and grants nothing on a repeat.
    private val _purchaseEvent = MutableSharedFlow<PurchaseEvent>(replay = 8, extraBufferCapacity = 8)
    val purchaseEvent: SharedFlow<PurchaseEvent> = _purchaseEvent.asSharedFlow()

    // BillingProcessor's callback, direct queryPurchasesAsync, ON_START and ON_RESUME can all
    // discover the same receipt within milliseconds. Backend grants are idempotent, but sending
    // every duplicate still consumes the IAP/IP rate limit and can starve the one NEW credit
    // receipt behind a wall of 429s. One token identifies one Play transaction regardless of
    // which callback found it, so publish it at most once per short recovery window.
    private val recentlyPublishedTokens = ConcurrentHashMap<String, Long>()

    private fun publishPurchaseEvent(productId: String, purchaseToken: String, source: String) {
        if (productId.isBlank() || purchaseToken.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        val shouldPublish = AtomicBoolean(false)
        recentlyPublishedTokens.compute(purchaseToken) { _, previous ->
            if (previous == null || now - previous >= PURCHASE_EVENT_COOLDOWN_MS) {
                shouldPublish.set(true)
                now
            } else {
                previous
            }
        }
        if (shouldPublish.get()) {
            Logger.d("IAP publish receipt: source=$source productId=$productId token=${purchaseToken.take(12)}...")
            _purchaseEvent.tryEmit(PurchaseEvent(productId, purchaseToken))
        } else {
            Logger.d("IAP duplicate receipt suppressed: source=$source productId=$productId")
        }
    }

    private val billingHandler = object : BillingProcessor.IBillingHandler {
        override fun onBillingInitialized() {
            Logger.d("IAP billing initialized — syncing owned purchases + loading prices")
            IAPUtils.loadOwnedPurchasesFromGoogleAsync {
                syncFromGoogle()
                loadSubscriptionPrices()
                loadCreditPackPrices()
                // Boot-time recovery: if a previous /subs/android call failed (server
                // down, perm not yet replicated, app killed before POST), the Play
                // side still records the purchase. Re-publish every owned token so
                // the host re-runs the backend hand-off — backend de-dupes via
                // UNIQUE(purchase_token) so this is safe to repeat.
                republishOwnedPurchases(knownSkus)
            }
        }

        override fun onProductPurchased(productId: String, details: PurchaseInfo?) {
            val token = details?.purchaseData?.purchaseToken
            Logger.d("IAP purchased: productId=$productId, token=${token?.take(12) ?: "<null>"}...")
            syncFromGoogle()
            if (!token.isNullOrBlank()) {
                publishPurchaseEvent(productId, token, "BillingProcessor callback")
            } else {
                Logger.e("IAP purchased but purchaseToken is null/blank — backend won't be notified")
            }
        }

        override fun onBillingError(errorCode: Int, error: Throwable?) {
            Logger.e("IAP billing error: code=$errorCode, msg=${error?.message}")
        }

        override fun onPurchaseHistoryRestored() {
            Logger.d("IAP purchase history restored — re-syncing + loading prices")
            syncFromGoogle()
            loadSubscriptionPrices()
            loadCreditPackPrices()
            // Re-claim every owned purchase: Play tells us which tokens are still alive,
            // we forward each to the host so it can POST to backend in case the original
            // /subs/android call failed (network drop, server 5xx, perm not yet granted).
            // This is the recovery path for the exact case we're debugging right now.
            republishOwnedPurchases(knownSkus)
        }
    }

    /** Iterates Play's currently-owned purchases and re-emits each as a PurchaseEvent so
     *  the host re-runs its backend hand-off. Safe to call repeatedly — the backend's
     *  Purchase table has UNIQUE(purchase_token) so duplicate claims are idempotent. */
    /** Re-publishes purchase events for SKUs the host knows about (passed via [skuHints])
     *  — used by Restore + boot recovery. Without bp.listOwned*() exposed, we probe each
     *  hinted SKU via the wrapped getPurchaseInfo* helpers and emit a PurchaseEvent only
     *  for those Play actually owns. Safe to call repeatedly — backend de-dupes via
     *  UNIQUE(purchase_token). */
    private fun republishOwnedPurchases(skuHints: List<String>) {
        try {
            for (sku in skuHints) {
                val info = IAPUtils.getSubscriptionPurchaseInfoDetails(sku)
                    ?: IAPUtils.getPurchaseInfoDetails(sku)
                val token = info?.purchaseData?.purchaseToken ?: continue
                Logger.d("IAP republish owned: sku=$sku token=${token.take(12)}...")
                publishPurchaseEvent(sku, token, "BillingProcessor cache")
            }
        } catch (e: Exception) {
            Logger.e("IAP republishOwnedPurchases failed: ${e.message}")
        }
    }

    /** Public emit hook for callers (e.g. PurchaseObserver) that complete the
     *  backend-grant half of a purchase and need to dismiss the paywall — Play's
     *  onProductPurchased callback is unreliable (we've seen Play return without
     *  ever firing it in production), so the observer pushes its own success
     *  signal here once the backend returns a verified entitlement (subscription or credits). */
    fun emitPurchaseSuccess() { _purchaseSuccess.tryEmit(Unit) }

    /** Launches Play's INAPP one-time purchase flow for a managed product
     *  (credit_1..5). Same fire-and-forget shape as subscribe() — outcome
     *  flows back via BillingProcessor.onProductPurchased → PurchaseEvent. */
    fun buyOneTime(activity: Activity, productId: String) {
        Logger.d("IAP buyOneTime: launching Play INAPP flow — productId='$productId'")
        IAPUtils.callPurchase(activity, productId)
    }

    /** Consumes a one-time managed product (credit_1..5) by its exact Play token so the user
     *  can buy the SAME pack again. Using BillingProcessor's SKU cache here is unsafe: the
     *  recovery path exists precisely because that cache can be stale. Call this only AFTER
     *  the backend has granted the credits — consuming earlier would burn a paid receipt. */
    fun consumeOneTime(productId: String, purchaseToken: String) {
        if (purchaseToken.isBlank()) {
            Logger.e("IAP consumeOneTime: blank token for productId='$productId'")
            return
        }
        val client = ensureDirectClient()
        val consume = {
            val params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            client.consumeAsync(params) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Logger.d("IAP consumeOneTime: consumed '$productId' — repurchasable again")
                } else {
                    Logger.e(
                        "IAP consumeOneTime failed: productId='$productId' " +
                            "code=${result.responseCode} msg=${result.debugMessage}"
                    )
                }
            }
        }
        if (client.isReady) {
            consume()
        } else {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        consume()
                    } else {
                        Logger.e(
                            "IAP consume connection failed: productId='$productId' " +
                                "code=${billingResult.responseCode} msg=${billingResult.debugMessage}"
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Logger.e("IAP consume BillingClient disconnected: productId='$productId'")
                }
            })
        }
    }

    /** Known SKU universe for this app — keep aligned with backend skus.py.
     *  Listed here because base_iap's BillingProcessor doesn't expose listOwned*()
     *  publicly, so we have to probe each candidate SKU explicitly.
     *
     *  IMPORTANT: probe by the ACTUAL Play Console product id, not by our internal
     *  tier names. Play has a single sub product `release_premium_access`
     *  (= IAPUtils.KEY_PREMIUM) — Play returns null for "yearly_pro" etc. because
     *  no such product exists upstream, even though the user owns the umbrella
     *  sub. Missing this caused restore-on-reinstall to silently no-op:
     *  PremiumProvider sees isSubscribed=true (via bp.isSubscribed) but
     *  republishOwnedPurchases couldn't locate the token to forward to backend. */
    private val knownSkus = listOf(
        IAPUtils.KEY_PREMIUM,         // sub: "release_premium_access" — owns the 3 base plans
        "yearly_pro", "monthly_pro", "weekly_pro",  // kept as harmless fallbacks (no-op upstream)
        "credit_1", "credit_2", "credit_3", "credit_4", "credit_5",
        // Spelling the packs carried before the rename: a receipt bought under one of
        // these is still owned upstream and has to be re-claimed on reinstall.
        "credit_01", "credit_02", "credit_03", "credit_04", "credit_05",
    )

    /** Explicit re-publish hook for the Restore button. */
    fun republishOwned() = republishOwnedPurchases(knownSkus)

    // @Singleton, but callers (e.g. IapViewModel.init) may invoke init() repeatedly — register only once.
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        try {
            val licenseKey = context.getString(R.string.public_license_key)
            Logger.d("IAP BillingRepository.init: registering billing processor (KEY_PREMIUM='${IAPUtils.KEY_PREMIUM}')")
            IAPUtils.initAndRegister(context, licenseKey, billingHandler)
        } catch (e: Exception) {
            initialized.set(false)
            Logger.e("IAP BillingRepository.init failed: ${e.message}")
        }
    }

    private fun syncFromGoogle() {
        com.vnnami.appkit.internal.billing.IAPUtils.syncPremiumFromGoogleAsync(context) { premium ->
            _isPremium.value = premium
        }
    }

    // Base-plan ids live here (the billing layer) so the ViewModel can stay in domain terms.
    // All three plans now read Firebase Remote Config first (keys below), then fall back
    // to the wrapper constants. Lets ops swap base plans on the live app without a release
    // — e.g. promo plans, region-specific yearly, A/B test sidegrade rules — without
    // touching code. Empty / missing RC value → constant fallback.
    //
    // Required RC keys (String):
    //   iap_base_plan_weekly  → e.g. "release-weekly-plan"
    //   iap_base_plan_monthly → e.g. "release-monthly-plan"
    //   iap_base_plan_yearly  → e.g. "release-yearly-plan"
    //
    // Legacy key `iap_base_plan` (used to drive only yearly) is still honored as a
    // second-tier fallback so an existing Firebase setup keeps working until ops
    // migrates.
    private fun rcString(key: String): String =
        runCatching {
            com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance().getString(key)
        }.getOrNull().orEmpty()

    /** Normalises a Remote Config base-plan value. RC may legitimately store a SHORTHAND
     *  ("yearly", "year", "annual") instead of the full Play Console basePlanId
     *  ("release-yearly-plan"). Accept both, plus empty → fall back to the constant
     *  (which IS Play Console's actual id). Anything else is assumed to already be a
     *  valid full basePlanId and is passed through unchanged. */
    private fun resolveBasePlanId(rcValue: String, shorthands: Set<String>, fallback: String): String {
        val normalised = rcValue.trim().lowercase()
        return when {
            normalised.isEmpty() -> fallback
            normalised in shorthands -> fallback
            else -> rcValue.trim()
        }
    }

    val weeklyBasePlanId: String
        get() = resolveBasePlanId(
            rcString("iap_base_plan_weekly"),
            shorthands = setOf("weekly", "week"),
            fallback = IAPUtils.KEY_PREMIUM_WEEKLY_PLAN,
        )

    val monthlyBasePlanId: String
        get() = resolveBasePlanId(
            rcString("iap_base_plan_monthly"),
            shorthands = setOf("monthly", "month"),
            fallback = IAPUtils.KEY_PREMIUM_MONTHLY_PLAN,
        )

    val yearlyBasePlanId: String
        get() = resolveBasePlanId(
            rcString("iap_base_plan_yearly"),
            shorthands = setOf("yearly", "year", "annual"),
            fallback = IAPUtils.KEY_PREMIUM_YEARLY_PLAN,
        )

    // Accepts a plain Activity (the underlying IAPUtils.callSubscription only needs an
    // Activity) so callers hosted in a non-Fragment ComponentActivity (e.g. a Compose-only
    // MainActivity) can launch the Play billing flow directly.
    //
    // Tier-aware routing:
    //   - Fresh subscriber (no active sub) → base_iap's wrapper subscribeV7 (no oldToken).
    //   - Existing subscriber upgrading/changing tier → direct BillingClient with
    //     subscriptionUpdateParams.oldPurchaseToken. Without this, base_iap's
    //     subscribeV7 silently fails (Play returns "Item already owned" or similar)
    //     for sidegrades like weekly → monthly. Yearly upgrade happened to work in
    //     some cases because of price-tier rules, but the canonical way is to pass
    //     the old token + a replacement mode.
    fun subscribe(activity: Activity, basePlanId: String) {
        val oldToken = currentActiveSubscriptionToken()
        if (oldToken != null) {
            Logger.d("IAP subscribe (upgrade): basePlanId='$basePlanId', oldToken=${oldToken.take(8)}...")
            subscribeUpgradeDirect(activity, basePlanId, oldToken)
        } else {
            Logger.d("IAP subscribe (new): launching Play billing flow — sku='${IAPUtils.KEY_PREMIUM}', basePlanId='$basePlanId'")
            IAPUtils.callSubscription(activity, IAPUtils.KEY_PREMIUM, basePlanId)
        }
    }

    /** Returns the active subscription's purchase token if Play's cache reports one
     *  for our premium product, null otherwise. base_iap caches the owned-purchases
     *  result after `loadOwnedSubFromGoogleAsyncV7` — we rely on the boot-time call
     *  in `onBillingInitialized` to keep that cache warm. */
    private fun currentActiveSubscriptionToken(): String? {
        return runCatching {
            IAPUtils.getSubscriptionPurchaseInfoDetails(IAPUtils.KEY_PREMIUM)
                ?.purchaseData?.purchaseToken?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Lazily-created direct BillingClient used for sub upgrades and receipt recovery.
     *  base_iap's wrapper neither exposes `setSubscriptionUpdateParams(oldPurchaseToken)`
     *  nor reliably refreshes one-time INAPP purchases in the current process, so both
     *  operations use the official client directly. Keep a single instance to avoid
     *  repeated handshakes, and route every PURCHASED result through [_purchaseEvent]
     *  so the host verifies it with its backend before signalling [_purchaseSuccess]. */
    @Volatile private var directClient: BillingClient? = null

    private fun ensureDirectClient(): BillingClient {
        directClient?.let { return it }
        val client = BillingClient.newBuilder(context)
            .setListener(PurchasesUpdatedListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        publishDirectPurchase(purchase, "purchase update")
                    }
                } else {
                    Logger.e("IAP direct onPurchasesUpdated: code=${result.responseCode} msg=${result.debugMessage}")
                }
            })
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
        directClient = client
        return client
    }

    /** Publish only completed purchases. PENDING receipts must not be sent to the backend as
     *  paid, and a Purchase can technically contain more than one product id. */
    private fun publishDirectPurchase(purchase: Purchase, source: String) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            Logger.d("IAP $source: ignoring non-purchased state=${purchase.purchaseState}")
            return
        }
        val token = purchase.purchaseToken
        if (token.isBlank()) return
        for (productId in purchase.products) {
            Logger.d("IAP $source: productId=$productId, token=${token.take(12)}...")
            publishPurchaseEvent(productId, token, source)
        }
    }

    private fun subscribeUpgradeDirect(
        activity: Activity,
        newBasePlanId: String,
        oldPurchaseToken: String,
    ) {
        val client = ensureDirectClient()
        val launch = { _: Unit ->
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(IAPUtils.KEY_PREMIUM)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            val queryParams = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()
            // Billing 8 hands back a QueryProductDetailsResult here, not the bare List that 7.x used.
            client.queryProductDetailsAsync(queryParams) { _, result ->
                val details = result.productDetailsList.firstOrNull() ?: run {
                    Logger.e("IAP upgrade: no ProductDetails for ${IAPUtils.KEY_PREMIUM}")
                    return@queryProductDetailsAsync
                }
                val offer = details.subscriptionOfferDetails?.firstOrNull { it.basePlanId == newBasePlanId }
                    ?: run {
                        Logger.e("IAP upgrade: basePlanId='$newBasePlanId' not in offers; available=${details.subscriptionOfferDetails?.map { it.basePlanId }}")
                        return@queryProductDetailsAsync
                    }
                val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offer.offerToken)
                    .build()
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .setSubscriptionUpdateParams(
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(oldPurchaseToken)
                            // WITHOUT_PRORATION — change tier immediately, no proration
                            // math, normal cycle billing kicks in at next renewal. The
                            // safer-for-everything mode: CHARGE_PRORATED_PRICE is rejected
                            // for downgrades + sidegrades (weekly→monthly is a per-unit-time
                            // DOWNGRADE because monthly $29.99/30d ≈ $1/d vs weekly
                            // $9.99/7d ≈ $1.43/d), and we hit
                            // "Requested replacement mode is not supported" in prod.
                            // WITHOUT_PRORATION accepts every up/side/down combo.
                            .setSubscriptionReplacementMode(
                                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITHOUT_PRORATION
                            )
                            .build()
                    )
                    .build()
                Logger.d("IAP upgrade: launching flow basePlanId=$newBasePlanId")
                client.launchBillingFlow(activity, flowParams)
            }
        }
        if (client.isReady) {
            launch(Unit)
        } else {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        launch(Unit)
                    } else {
                        Logger.e("IAP upgrade connection failed: code=${billingResult.responseCode} msg=${billingResult.debugMessage}")
                    }
                }
                override fun onBillingServiceDisconnected() {
                    Logger.e("IAP upgrade BillingClient disconnected")
                }
            })
        }
    }

    private val restoreMutex = Mutex()

    /**
     * Query Play's current ownership directly and wait for both product types to finish.
     *
     * The AAR's `loadOwnedSubFromGoogleAsyncV7` only refreshes subscriptions. Its INAPP cache
     * therefore stayed stale after a credit purchase whose callback was missed, until a cold
     * BillingProcessor initialization (force-stop/reopen) populated it again. Directly querying
     * INAPP fixes that gap; querying SUBS here as well gives Restore one deterministic source.
     */
    private fun queryOwnedPurchasesDirect(
        scope: RestoreScope,
        onComplete: (Boolean) -> Unit,
    ) {
        val client = ensureDirectClient()
        val queryBoth = {
            val productTypes = when (scope) {
                RestoreScope.ALL -> listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)
                RestoreScope.ONE_TIME -> listOf(BillingClient.ProductType.INAPP)
                RestoreScope.SUBSCRIPTION -> listOf(BillingClient.ProductType.SUBS)
            }
            val remaining = AtomicInteger(productTypes.size)
            val allSucceeded = AtomicBoolean(true)

            fun query(productType: String) {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(productType)
                    .build()
                client.queryPurchasesAsync(params) { result, purchases ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Logger.d("IAP restore direct: type=$productType owned=${purchases.size}")
                        // PicMove has exactly one subscription product. License-test accounts can
                        // nevertheless return several historical SUBS tokens; forwarding all of
                        // them repeatedly produced 400s and exhausted the shared backend IP limit.
                        // The newest PURCHASED token is the only candidate for the current plan.
                        val receipts = if (productType == BillingClient.ProductType.SUBS) {
                            listOfNotNull(
                                purchases
                                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                                    .maxByOrNull { it.purchaseTime }
                            )
                        } else {
                            purchases
                        }
                        receipts.forEach { publishDirectPurchase(it, "restore $productType") }
                    } else {
                        allSucceeded.set(false)
                        Logger.e(
                            "IAP restore direct failed: type=$productType " +
                                "code=${result.responseCode} msg=${result.debugMessage}"
                        )
                    }
                    if (remaining.decrementAndGet() == 0) onComplete(allSucceeded.get())
                }
            }

            productTypes.forEach(::query)
        }

        if (client.isReady) {
            queryBoth()
        } else {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryBoth()
                    } else {
                        Logger.e(
                            "IAP restore connection failed: code=${billingResult.responseCode} " +
                                "msg=${billingResult.debugMessage}"
                        )
                        onComplete(false)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Logger.e("IAP restore BillingClient disconnected")
                }
            })
        }
    }

    /** Explicit Restore/recovery operation. Returns only after Play has answered both the
     *  one-time-product and subscription ownership queries. Concurrent lifecycle/toolbar
     *  restores are serialized so BillingClient is never connected or queried twice at once. */
    suspend fun restore(scope: RestoreScope = RestoreScope.ALL): Boolean = restoreMutex.withLock {
        val directSucceeded = suspendCancellableCoroutine { continuation ->
            queryOwnedPurchasesDirect(scope) { success ->
                if (continuation.isActive) continuation.resume(success)
            }
        }

        // Keep the AAR's subscription cache and PremiumProvider-compatible state warm too.
        // This callback is not used for receipt recovery: the direct queries above are the
        // authoritative, awaited path and include INAPP purchases.
        IAPUtils.loadOwnedPurchasesFromGoogleAsync {
            syncFromGoogle()
        }
        directSucceeded
    }

    private companion object {
        const val PURCHASE_EVENT_COOLDOWN_MS = 60_000L
    }

    private fun loadSubscriptionPrices() {
        Logger.d("IAP loadSubscriptionPrices: querying subscription product '${IAPUtils.KEY_PREMIUM}'")

        IAPUtils.getSubscriptionListingDetails(IAPUtils.KEY_PREMIUM) { products ->
            if (products.isNullOrEmpty()) {
                Logger.e("IAP loadSubscriptionPrices: NO products for '${IAPUtils.KEY_PREMIUM}' — CHƯA load được productID cho màn IAP (kiểm tra Play Console / tài khoản test / KEY_PREMIUM)")
                return@getSubscriptionListingDetails
            }
            Logger.d("IAP loadSubscriptionPrices: loaded ${products.size} product(s): ${products.mapNotNull { it?.productId }}")

            // ── RAW dump of every package the LocalIAP screen receives (debug) ──
            // ProductDetails.toString() carries the original Play Billing JSON; the per-offer /
            // per-phase lines below break it down (basePlanId, offerId/token/tags, and each
            // pricing phase: price, micros, currency, billing period, cycle count, recurrence).
            if (BuildConfig.DEBUG) {
                products.forEachIndexed { pi, pd ->
                    if (pd == null) return@forEachIndexed
                    Logger.d("IAP RAW product[$pi] id='${pd.productId}' type='${pd.productType}' title='${pd.title}' name='${pd.name}' desc='${pd.description}'")
                    Logger.d("IAP RAW product[$pi] toString=$pd")
                    pd.subscriptionOfferDetails?.forEachIndexed { oi, off ->
                        Logger.d("IAP RAW   offer[$oi] basePlanId='${off.basePlanId}' offerId='${off.offerId}' tags=${off.offerTags} token='${off.offerToken.take(16)}…'")
                        off.pricingPhases.pricingPhaseList.forEachIndexed { phi, ph ->
                            Logger.d(
                                "IAP RAW     phase[$phi] price='${ph.formattedPrice}' micros=${ph.priceAmountMicros} " +
                                    "currency='${ph.priceCurrencyCode}' period='${ph.billingPeriod}' " +
                                    "cycles=${ph.billingCycleCount} recurrence=${ph.recurrenceMode}",
                            )
                        }
                    } ?: Logger.d("IAP RAW   product[$pi] has NO subscriptionOfferDetails")
                }
            }

            val productDetails = products.firstOrNull() ?: return@getSubscriptionListingDetails
            val offers = productDetails.subscriptionOfferDetails ?: return@getSubscriptionListingDetails

            val yearlyBaseId = yearlyBasePlanId
            val monthlyBaseId = monthlyBasePlanId
            val weeklyBaseId = weeklyBasePlanId
            Logger.d("IAP loadSubscriptionPrices: product='${productDetails.productId}', ${offers.size} offer(s); expected basePlanIds -> yearly='$yearlyBaseId', monthly='$monthlyBaseId', weekly='$weeklyBaseId'")

            offers.forEach { offer ->
                Logger.d("IAP offer: basePlanId='${offer.basePlanId}' price='${offer.firstPaidPhase()?.formattedPrice}'")
                when (offer.basePlanId) {
                    yearlyBaseId -> updateYearlyPrice(offer)
                    monthlyBaseId -> offer.firstPaidPhase()?.let { _monthlyPrice.value = it.formattedPrice }
                    weeklyBaseId -> offer.firstPaidPhase()?.let { _weeklyPrice.value = it.formattedPrice }
                }
            }

            // Fallback if yearly price wasn't updated by baseId match — compare against
            // the literal fallback so we only retry while still on the placeholder value.
            if (_yearlyPrice.value == "$199.99") {
                offers.find { it.basePlanId == IAPUtils.KEY_PREMIUM_YEARLY_PLAN || it.basePlanId == "release-yearly-plan" }
                    ?.let { updateYearlyPrice(it) }
            }
            Logger.d("IAP prices resolved -> yearly='${_yearlyPrice.value}', monthly='${_monthlyPrice.value}', weekly='${_weeklyPrice.value}', monthlyEquivalent='${_monthlyEquivalent.value}'")
        }
    }

    /** Query Play for each one-time credit-pack INAPP product and publish its localized price
     *  into [creditPackPrices]. This REPLACES the backend /sku price — the shop now shows the
     *  exact Play Console price (e.g. VND). Each SKU is queried singly (the AAR's multi-id
     *  variant has no result callback). A SKU that Play doesn't return (not Active on Play
     *  Console, or app not in a matching test track) is logged and simply omitted. */
    private fun loadCreditPackPrices() {
        Logger.d("IAP loadCreditPackPrices: querying ${creditPackSkus.size} INAPP product(s): $creditPackSkus")
        creditPackSkus.forEach { productId ->
            IAPUtils.getPurchaseListingDetails(productId) { list ->
                val sku = list?.firstOrNull()
                if (sku == null) {
                    Logger.e("IAP loadCreditPackPrices: NO details for '$productId' — sản phẩm INAPP chưa Active trên Play Console, hoặc app chưa ở track test phù hợp")
                    return@getPurchaseListingDetails
                }
                if (BuildConfig.DEBUG) {
                    Logger.d("IAP RAW inapp '$productId' priceText='${sku.priceText}' currency='${sku.currency}' priceLong=${sku.priceLong} priceValue=${sku.priceValue} title='${sku.title}'")
                }
                _creditPackPrices.update { it + (productId to sku.priceText) }
            }
        }
    }

    /** First pricing phase that actually costs money (skips free-trial / intro phases). */
    private fun ProductDetails.SubscriptionOfferDetails.firstPaidPhase() =
        pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros > 0 }

    // Yearly is the only plan that also derives the "per month" equivalent, so it keeps a dedicated helper.
    private fun updateYearlyPrice(offer: ProductDetails.SubscriptionOfferDetails) {
        offer.firstPaidPhase()?.let { phase ->
            _yearlyPrice.value = phase.formattedPrice
            _monthlyEquivalent.value = calculateMonthlyPriceFromYearly(phase.priceAmountMicros)
            Logger.d("VN_NAMI", "Yearly Price Updated: ${phase.formattedPrice}")
        }
    }

    private fun calculateMonthlyPriceFromYearly(yearlyPriceMicros: Long): String {
        val monthlyPriceMicros = yearlyPriceMicros / 12
        val monthlyPrice = monthlyPriceMicros / 1_000_000.0
        return String.format("%.2f", monthlyPrice)
    }

    fun destroy() {
        IAPUtils.unregisterListener(billingHandler)
        directClient?.endConnection()
        directClient = null
    }
}
