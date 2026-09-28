package com.vnnami.appkit.internal.billing

import android.app.Activity
import android.content.Context
import android.os.CountDownTimer
import android.util.Log
import com.android.billingclient.api.ProductDetails
import com.vnnami.appkit.api.Logger

import java.util.concurrent.CopyOnWriteArraySet
import androidx.annotation.Keep
import com.brian.base_iap.iapLib.BillingProcessor
import com.brian.base_iap.iapLib.PurchaseInfo
import com.brian.base_iap.iapLib.SkuDetails
import kotlin.jvm.javaClass
import kotlin.let
import kotlin.run


@Keep
/**
 * Thin layer over the AAR's `base_iap` billing code, which ships R8-obfuscated.
 *
 * `@Keep` because the AAR reaches some of this by reflection; renaming it breaks purchases in a
 * release build only, where it is hardest to notice.
 */
internal object IAPUtils {
    val TAG = this.javaClass.simpleName
    private val listeners = CopyOnWriteArraySet<BillingProcessor.IBillingHandler>()
    private var appContext: Context? = null

    /**
     * Fan-out to every registered listener on the next main-thread tick.
     * The `CountDownTimer(0, 0)` defers to the next loop so callbacks never fire
     * inline on the SDK's thread; the snapshot copy avoids ConcurrentModification.
     */
    private fun dispatch(action: (BillingProcessor.IBillingHandler) -> Unit) {
        object : CountDownTimer(0, 0) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                for (listener in ArrayList(listeners)) action(listener)
            }
        }.start()
    }

    /** Internal handler that dispatches to *all* registered listeners. */
    private val internalHandler = object : BillingProcessor.IBillingHandler {
        override fun onBillingInitialized() {
            Log.i(TAG, "Billing initialized successfully")
            billingInitialized = true
            // Sync owned purchases from Google to ensure premium state is correct after app restart.
            appContext?.let { ctx -> syncPremiumFromGoogleAsync(ctx) { /* no-op */ } }
            dispatch { it.onBillingInitialized() }
        }
        override fun onProductPurchased(productId: String, details: PurchaseInfo?) {
            Log.i(TAG, "Product purchased: $productId, Details: $details")
            // Immediately refresh premium state after purchase.
            appContext?.let { ctx -> syncPremiumFromGoogleAsync(ctx) { /* no-op */ } }
            dispatch { it.onProductPurchased(productId, details) }
        }
        override fun onBillingError(errorCode: Int, error: Throwable?) {
            Log.e(TAG, "Billing error code: $errorCode, Error: ${error?.message}")
            dispatch { it.onBillingError(errorCode, error) }
        }
        override fun onPurchaseHistoryRestored() {
            Log.i(TAG, "Purchase history restored")
            dispatch { it.onPurchaseHistoryRestored() }
        }
    }

    var KEY_PREMIUM = "release_premium_access"
    var KEY_PREMIUM_YEARLY_PLAN = "release-yearly-plan"
    var KEY_PREMIUM_MONTHLY_PLAN = "release-monthly-plan"
    var KEY_PREMIUM_WEEKLY_PLAN = "release-weekly-plan"

    private var bp: BillingProcessor? = null

    private var billingInitialized = false

    fun init(context: Context, licenseKey: String) {
        if (bp != null) return  // already initialized
        appContext = context.applicationContext

        bp = BillingProcessor.newBillingProcessor(context, licenseKey, internalHandler)
        bp?.initialize()
    }

    fun initAndRegister(context: Context, licenseKey: String, handler: BillingProcessor.IBillingHandler) {
        registerListener(handler)
        init(context, licenseKey)
    }

    fun registerListener(handler: BillingProcessor.IBillingHandler) {
        if(!listeners.contains(handler)) {
            listeners.add(handler)
        }
        // if billing was already up, immediately notify them
        if (billingInitialized) {
            handler.onBillingInitialized()
            // also you might want to restore their view of purchases:
//            handler.onPurchaseHistoryRestored()
        }
    }

    fun unregisterListener(handler: BillingProcessor.IBillingHandler) {
        listeners.remove(handler)
    }

    fun callSubscription(activity: Activity, sku: String, baseIdPlan: String) {
        if (!billingInitialized) {
            Logger.e("IAPUtils callSubscription: billing NOT initialized — cannot launch (sku=$sku, basePlan=$baseIdPlan)")
            return
        }
        val isSubsUpdateSupported: Boolean = bp?.isSubscriptionUpdateSupported() == true
        Logger.d("IAPUtils callSubscription: sku=$sku, basePlan=$baseIdPlan, subsUpdateSupported=$isSubsUpdateSupported")
        if (isSubsUpdateSupported) {
            bp?.subscribeV7(activity, sku, baseIdPlan)
        } else {
            Logger.e("IAPUtils callSubscription: subscription update NOT supported — flow not launched")
        }
    }

    /** Launches Play's INAPP one-time purchase flow for a managed product (the
     *  credit packs: credit_1..credit_5). Returns true when BillingProcessor
     *  accepts the request — success/failure of the actual purchase arrives via
     *  the IBillingHandler.onProductPurchased callback (or onBillingError). */
    fun callPurchase(activity: Activity, productId: String): Boolean {
        if (!billingInitialized) {
            Logger.e("IAPUtils callPurchase: billing NOT initialized — cannot launch (productId=$productId)")
            return false
        }
        Logger.d("IAPUtils callPurchase: productId=$productId")
        return bp?.purchase(activity, productId) ?: false
    }

    /** Consumes a one-time managed product (credit pack) so it becomes purchasable AGAIN.
     *  BillingProcessor auto-acknowledges purchases but never consumes them, so a consumable
     *  pack stays "owned" forever and a repeat purchase fails with ITEM_ALREADY_OWNED.
     *  Must be called AFTER the backend has granted the credits. */
    fun consumePurchase(productId: String) {
        if (!billingInitialized) {
            Logger.e("IAPUtils consumePurchase: billing NOT initialized — cannot consume (productId=$productId)")
            return
        }
        val processor = bp
        if (processor == null) {
            Logger.w("IAPUtils consumePurchase: BillingProcessor null — '$productId' not consumed")
            return
        }
        Logger.d("IAPUtils consumePurchase: productId=$productId")
        processor.consumePurchaseAsync(productId, object : BillingProcessor.IPurchasesResponseListener {
            override fun onPurchasesSuccess() {
                Logger.d("IAPUtils consumePurchase: consumed '$productId' — repurchasable again")
            }
            override fun onPurchasesError() {
                Logger.e("IAPUtils consumePurchase: FAILED to consume '$productId' — repurchase will hit ITEM_ALREADY_OWNED")
            }
        })
    }

    @JvmStatic
    fun isPremium(): Boolean {
        val isPremium  = bp?.isSubscribed(KEY_PREMIUM)  == true
        Log.i(TAG, "isPremium: $isPremium")
        return isPremium;
    }

    /**
     * Prefer this check in UI / ads code, because BillingProcessor might not be fully ready yet.
     * We'll keep a cached premium flag that is updated whenever billing sync succeeds.
     */
    @JvmStatic
    fun isPremiumOrCached(context: Context): Boolean {
        return try {
            isPremium() || PremiumProvider.cached(context)
        } catch (_: Exception) {
            PremiumProvider.cached(context)
        }
    }

    /**
     * Sync subscriptions from Google and update cached premium flag.
     * This is safe to call multiple times; it will no-op if BillingProcessor isn't ready.
     */
    fun syncPremiumFromGoogleAsync(context: Context, result: (Boolean) -> Unit) {
        loadOwnedPurchasesFromGoogleAsync { success ->
            val premiumNow = if (success) isPremium() else PremiumProvider.cached(context)
            PremiumProvider.set(context, premiumNow)
            result.invoke(premiumNow)
        }
    }

    fun loadOwnedPurchasesFromGoogleAsync(result : (Boolean) -> Unit) {
        bp?.loadOwnedSubFromGoogleAsyncV7(object : BillingProcessor.IPurchasesResponseListener {
            override fun onPurchasesSuccess() {
                Log.i(TAG, "Owned purchases loaded successfully")
                //     updatePremiumState()
                result.invoke(true)
            }

            override fun onPurchasesError() {
                Log.e(TAG, "Failed to load owned purchases")
                // Handle error if needed
                result.invoke(false)
            }
        }) ?: run {
            Log.w(TAG, "BillingProcessor is not initialized")
            result.invoke(false)
        }
    }

    fun getPurchaseListingDetails(productId: String, skuDetails: (List<SkuDetails?>?) -> Unit) {
        if (billingInitialized) {
            bp?.getPurchaseListingDetailsAsync(productId, object : BillingProcessor.ISkuDetailsResponseListener {
                override fun onSkuDetailsResponse(products: List<SkuDetails?>?) {
                    Log.d(TAG, "Purchase listing details for ${productId}: $skuDetails")
                    // Assuming one SkuDetails object per product
                    skuDetails.invoke(products)
                }

                override fun onSkuDetailsError(error: String?) {
                    Log.e(TAG, "Error getting purchase listing details: $error")
                    skuDetails.invoke(null)
                }

            })
        } else {
            Log.w(TAG, "BillingProcessor is not initialized")
        }
    }

    fun isSubscribed(productId: String): Boolean {
        return bp?.isSubscribed(productId) == true
    }

    // Function to get listing details for a single subscription
    fun getSubscriptionListingDetails(subscriptionId: String, skuDetails: (List<ProductDetails?>?) -> Unit) {
        if (billingInitialized) {
            Logger.d("IAPUtils getSubscriptionListingDetails: querying '$subscriptionId'")
            bp?.getSubscriptionListingDetailsAsyncV7(subscriptionId, object : BillingProcessor.ISkuDetailsResponseListenerV7 {
                override fun onSkuDetailsResponse(products: List<ProductDetails?>?) {
                    Logger.d("IAPUtils getSubscriptionListingDetails('$subscriptionId') -> ${products?.size ?: 0} product(s): ${products?.mapNotNull { it?.productId }}")
                    skuDetails.invoke(products)
                }

                override fun onSkuDetailsError(error: String?) {
                    Logger.e("IAPUtils getSubscriptionListingDetails('$subscriptionId') ERROR: $error")
                    skuDetails.invoke(null)
                }

            })
        } else {
            Logger.e("IAPUtils getSubscriptionListingDetails('$subscriptionId'): BillingProcessor NOT initialized — không query được productID")
        }
    }

    // Function to query listing details for multiple product IDs
    fun getPurchaseListingDetails(productIds: ArrayList<String>) {
        if (billingInitialized) {
            bp?.getPurchaseListingDetailsAsync(productIds, object : BillingProcessor.ISkuDetailsResponseListener {
                override fun onSkuDetailsResponse(products: List<SkuDetails?>?) {
                    Log.i(TAG, "Purchase listing details for products: \$skuDetails")
                }

                override fun onSkuDetailsError(error: String?) {
                    Log.e(TAG, "Error getting purchase listing details: $error")
                }

            })
        } else {
            Log.w(TAG, "BillingProcessor is not initialized")
        }
    }

    // Function to query listing details for multiple subscription IDs
    fun getSubscriptionListingDetails(subscriptionIds: ArrayList<String>) {
        if (billingInitialized) {
            bp?.getPurchaseListingDetailsAsync(subscriptionIds, object : BillingProcessor.ISkuDetailsResponseListener {
                override fun onSkuDetailsResponse(products: List<SkuDetails?>?) {
                    Log.i(TAG, "Subscription listing details for subscriptions: \$skuDetails")
                }

                override fun onSkuDetailsError(error: String?) {
                    Log.e(TAG, "Error getting subscription listing details: $error")
                }

            })
        } else {
            Log.w(TAG, "BillingProcessor is not initialized")
        }
    }

    // Function to get purchase info details for a product
    fun getPurchaseInfoDetails(productId: String): PurchaseInfo? {
        return bp?.getPurchaseInfo(productId)
    }

    // Function to get purchase info details for a subscription
    fun getSubscriptionPurchaseInfoDetails(subscriptionId: String): PurchaseInfo? {
        return bp?.getSubscriptionPurchaseInfo(subscriptionId)
    }

    // Note: base_iap's BillingProcessor.listOwnedProducts/Subscriptions are not
    // part of the AAR's public Kotlin-visible surface (R8 minified / signature
    // mismatch). We rely on onProductPurchased + onPurchaseHistoryRestored
    // callbacks for fresh purchase events instead of polling owned lists.

    // Additional functions such as handle canceled subscriptions can be implemented by checking the autoRenewing flag
    fun isSubscriptionCanceled(subscriptionId: String): Boolean {
        val purchaseInfo = getSubscriptionPurchaseInfoDetails(subscriptionId)
        if (purchaseInfo != null) {
            return purchaseInfo.purchaseData.autoRenewing.not()
        }
        Log.w(TAG, "No purchase info for subscriptionId: \$subscriptionId")
        return false
    }

    fun destroy() {
        bp?.release()
        bp = null
        billingInitialized = false
        listeners.clear()
        Log.i(TAG, "IAPUtils destroyed")
    }

}
