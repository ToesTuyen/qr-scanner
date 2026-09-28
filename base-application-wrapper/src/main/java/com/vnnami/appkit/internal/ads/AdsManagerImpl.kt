package com.vnnami.appkit.internal.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Consumer
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardItem
import com.nlbn.ads.banner.BannerPlugin
import com.nlbn.ads.callback.AdCallback
import com.nlbn.ads.callback.NativeCallback
import com.nlbn.ads.callback.RewardCallback
import com.nlbn.ads.util.Admob
import com.vnnami.appkit.api.AppKit
import com.vnnami.appkit.api.Logger
import com.vnnami.appkit.internal.billing.PremiumProvider
import com.vnnami.appkit.internal.ads.RemoteConfigProvider
import com.brian.base_application.R as AdR
import com.vnnami.appkit.api.BannerSize
import com.vnnami.appkit.api.NativeSize
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [AdsManager] implementation — orchestrates ad loading for the whole app.
 *
 * ## Design decisions
 * - Named **Manager** (not Repository) because it touches both data (Premium state,
 *   Remote Config) and View concerns (inflating layouts, setting visibility).
 * - Premium state is delegated to [PremiumProvider] (single source of truth).
 * - `_isPremiumFlow` allows coroutine-based screens to react to premium changes.
 * - Call [refreshPremiumStatus] right after a successful purchase to keep the flow in sync.
 *
 * ## Singleton
 * Use [getInstance] — safe for multi-thread access.
 */
internal class AdsManagerImpl private constructor(private val context: Context) {

    /** Public [BannerSize] carries no AAR types; the translation to the AAR's own preset is here. */
    private val BannerSize.pluginType: BannerPlugin.BannerType
        get() = when (this) {
            BannerSize.ADAPTIVE -> BannerPlugin.BannerType.Adaptive
            BannerSize.STANDARD -> BannerPlugin.BannerType.Standard
            BannerSize.COLLAPSIBLE_BOTTOM -> BannerPlugin.BannerType.CollapsibleBottom
        }

    private val NativeSize.shimmerLayoutRes: Int
        get() = when (this) {
            NativeSize.SMALL -> AdR.layout.ads_native_shimer_small
            NativeSize.NORMAL -> AdR.layout.ads_native_loading
            NativeSize.DEFAULT, NativeSize.EXTENDED -> AdR.layout.ads_native_bot_loading
        }

    private val NativeSize.adLayoutRes: Int
        get() = when (this) {
            NativeSize.SMALL -> AdR.layout.ads_native_bot_no_media_short
            NativeSize.NORMAL -> AdR.layout.ads_native_bot_no_media
            NativeSize.DEFAULT, NativeSize.EXTENDED -> AdR.layout.ads_native_bot
        }


    companion object {
        @Volatile private var instance: AdsManagerImpl? = null

        @JvmStatic
        fun getInstance(context: Context): AdsManagerImpl =
            instance ?: synchronized(this) {
                instance ?: AdsManagerImpl(context.applicationContext).also { instance = it }
            }
    }

    // ── Premium state ─────────────────────────────────────────────────────────

    private val _isPremiumFlow = MutableStateFlow(PremiumProvider.isPremium(context))

    // Cache the inverted StateFlow once — `_isPremiumFlow.map { !it }` returned a
    // *new* cold Flow on every call, which means every Composable using
    // collectAsState() would re-collect from scratch on each re-subscription.
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private val _shouldShowAdsFlow: StateFlow<Boolean> =
        _isPremiumFlow.map { !it }.stateIn(
            scope = GlobalScope,
            started = SharingStarted.Eagerly,
            initialValue = !_isPremiumFlow.value,
        )

    /** Observe whether ads should be shown (false = premium, skip ads). */
    fun shouldShowAds(): Flow<Boolean> = _shouldShowAdsFlow

    /** Call after a successful IAP purchase to push the updated state downstream. */
    fun refreshPremiumStatus() {
        PremiumProvider.refresh(context) { isPremium ->
            _isPremiumFlow.value = isPremium
        }
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    // IDs inside layout_banner (from base_application AAR)
    private val ID_BANNER_CONTAINER = AdR.id.banner_container
    private val ID_SHIMMER          = AdR.id.shimmer_container_banner

    /**
     * Load a banner using explicit [container]/[shimmer] views already in your layout.
     * Prefer [showBanner] for the simpler single-slot pattern.
     */
    fun loadBannerPlugin(
        activity:  Activity,
        container: ViewGroup,
        shimmer:   ViewGroup,
        adUnitKey: String,
        size:      BannerSize
    ) {
        if (PremiumProvider.isPremium(activity)) {
            container.visibility = View.GONE
            shimmer.visibility   = View.GONE
            return
        }
        val adId = resolveAdId(adUnitKey, AppKit.adKeys.devBannerUnitId)
        if (adId.isEmpty()) {
            Logger.d("AdsManager: Banner skipped — empty adId for key=$adUnitKey")
            container.visibility = View.GONE
            shimmer.visibility   = View.GONE
            return
        }
        Logger.d("AdsManager: loadBannerPlugin", "adId=$adId, size=$size")
        val config = BannerPlugin.Config().apply {
            defaultAdUnitId           = adId
            defaultRefreshRateSec     = 30
            defaultCBFetchIntervalSec = 30
            defaultBannerType         = size.pluginType
        }
        Admob.getInstance().loadBannerPlugin(activity, container, shimmer, config)
    }

    /**
     * Simpler banner loader — inflates [AdR.layout.layout_banner] internally, injects it into
     * the FrameLayout [container] slot, then starts loading.
     *
     * ## Usage (XML + Kotlin)
     * ```xml
     * <FrameLayout android:id="@+id/bannerAdLayout"
     *     android:layout_width="match_parent"
     *     android:layout_height="wrap_content"
     *     android:visibility="gone" />
     * ```
     * ```kotlin
     * adsRepository.showBanner(activity, binding.bannerAdLayout)
     * ```
     * Premium users are handled automatically — container stays GONE.
     */
    fun showBanner(
        activity:  Activity,
        container: ViewGroup,
        adKey:     String,
        size:      BannerSize
    ) {
        loadBannerIntoSlot(activity, container, adKey, size)
    }

    /**
     * Core banner-loading implementation (previously AdBannerHelper.load).
     * Inflates layout_banner, wires inner views, and calls [Admob.loadBannerPlugin].
     */
    private fun loadBannerIntoSlot(
        activity:  Activity,
        container: ViewGroup?,
        adKey:     String = "",
        size:      BannerSize = BannerSize.ADAPTIVE
    ) {
        if (container == null) return

        if (PremiumProvider.isPremium(activity)) {
            container.visibility = View.GONE
            Logger.d("AdsManager: Banner skipped — Premium user")
            return
        }

        // Resolve adId BEFORE touching any views — hide and bail if key is unconfigured
        val adId = resolveAdId(adKey, AppKit.adKeys.devBannerUnitId)
        if (adId.isEmpty()) {
            Logger.d("AdsManager: Banner skipped — empty adId for key=$adKey")
            container.visibility = View.GONE
            return
        }

        Logger.d("AdsManager: showBanner", "adKey=$adKey, size=$size, adId=$adId")

        // Inflate layout_banner and extract its inner sub-views
        val adView       = LayoutInflater.from(activity).inflate(AdR.layout.layout_banner, null)
        val bannerInner  = adView.findViewById<ViewGroup>(ID_BANNER_CONTAINER)
        val shimmerInner = adView.findViewById<ViewGroup>(ID_SHIMMER)

        if (bannerInner == null || shimmerInner == null) {
            Logger.e("AdsManager: banner_container or shimmer_container_banner not found in layout_banner")
            container.visibility = View.GONE
            return
        }

        // Inject inflated view into the slot and make everything visible
        container.removeAllViews()
        container.addView(adView)
        container.visibility   = View.VISIBLE
        bannerInner.visibility = View.VISIBLE   // starts GONE in AAR XML

        val config = BannerPlugin.Config().apply {
            defaultAdUnitId           = adId
            defaultRefreshRateSec     = 30
            defaultCBFetchIntervalSec = 30
            defaultBannerType         = size.pluginType
        }
        Admob.getInstance().loadBannerPlugin(activity, bannerInner, shimmerInner, config)
    }

    // ── Native ────────────────────────────────────────────────────────────────

    fun loadNativeAdNoMedia(
        activity:  Activity,
        container: ViewGroup,
        adUnitKey: String,
        size:      NativeSize = NativeSize.DEFAULT,
        onAvailabilityChanged: (Boolean) -> Unit = {},
    ) {
        // Compose list items must remain 0x0 until there is a real ad. Keeping this container
        // VISIBLE for a shimmer/no-fill reserves an empty card or spacer in the surrounding list.
        onAvailabilityChanged(false)
        container.visibility = View.GONE
        if (PremiumProvider.isPremium(activity)) {
            return
        }

        val adId = resolveAdId(adUnitKey, AppKit.adKeys.devNativeUnitId)
        Logger.d("AdsManager: loadNativeAdNoMedia", "key=$adUnitKey, size=$size, adId=$adId")
        if (adId.isEmpty()) {
            return
        }

        // Preload the placeholder while the parent stays collapsed. It is ready if a caller later
        // chooses to expose loading state, but it cannot leave a blank slot in today's lists.
        container.safeSwap(activity, size.shimmerLayoutRes)

        Admob.getInstance().loadNativeAd(activity, adId, object : NativeCallback() {
            override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                super.onNativeAdLoaded(nativeAd)
                try {
                    val adView = LayoutInflater.from(activity)
                        .inflate(size.adLayoutRes, null) as NativeAdView
                    container.safeSwap(adView)
                    Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
                    container.visibility = View.VISIBLE
                    onAvailabilityChanged(true)
                } catch (e: Exception) {
                    Logger.e("AdsManager: native ad inflate failed: ${e.message}")
                    container.visibility = View.GONE
                    onAvailabilityChanged(false)
                }
            }

            override fun onAdFailedToLoad() {
                super.onAdFailedToLoad()
                container.visibility = View.GONE
                onAvailabilityChanged(false)
            }
        })
    }

    /**
     * Load a native ad for in-list injection (RecyclerView).
     * [onShimmerReady] fires immediately with shimmer placeholder.
     * [onAdLoaded] fires on main thread when real ad is ready.
     * [onAdFailed] fires if loading fails or user is premium.
     */
    fun loadNativeAdForList(
        activity:       Activity,
        adUnitKey:      String,
        size:           NativeSize = NativeSize.SMALL,
        onShimmerReady: (View) -> Unit,
        onAdLoaded:     (View) -> Unit,
        onAdFailed:     () -> Unit
    ) = loadNativeAdForListInternal(activity, adUnitKey, size, onShimmerReady, onAdLoaded, onAdFailed, 0L)

    /**
     * Core in-list native loader. [Admob.loadNativeAd] (nlbn) has no built-in timeout,
     * so when [timeoutMs] > 0 we arm a Handler that fires [onAdFailed] if neither
     * success nor failure arrives in time — prevents callers (e.g. onboarding) from
     * blocking forever when the SDK never calls back. An [AtomicBoolean] guarantees
     * exactly one of {loaded, failed, timeout} wins.
     */
    private fun loadNativeAdForListInternal(
        activity:       Activity,
        adUnitKey:      String,
        size:           NativeSize = NativeSize.SMALL,
        onShimmerReady: (View) -> Unit,
        onAdLoaded:     (View) -> Unit,
        onAdFailed:     () -> Unit,
        timeoutMs:      Long
    ) {
        if (PremiumProvider.isPremium(activity)) { onAdFailed(); return }

        val shimmer = LayoutInflater.from(activity).inflate(size.shimmerLayoutRes, null)
        onShimmerReady(shimmer)

        val adId = resolveAdId(adUnitKey, AppKit.adKeys.devNativeUnitId)
        Logger.d("AdsManager: loadNativeAdForList", "key=$adUnitKey, size=$size, adId=$adId, timeoutMs=$timeoutMs")
        if (adId.isEmpty()) {
            onAdFailed()
            return
        }

        val done = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable: Runnable? = if (timeoutMs > 0) Runnable {
            if (done.compareAndSet(false, true)) {
                Logger.d("AdsManager: native timeout ${timeoutMs}ms — key=$adUnitKey")
                onAdFailed()
            }
        } else null
        timeoutRunnable?.let { handler.postDelayed(it, timeoutMs) }

        Admob.getInstance().loadNativeAd(activity, adId, object : NativeCallback() {
            override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                super.onNativeAdLoaded(nativeAd)
                if (!done.compareAndSet(false, true)) return
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                try {
                    val adView = LayoutInflater.from(activity)
                        .inflate(size.adLayoutRes, null) as NativeAdView
                    Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
                    onAdLoaded(adView)
                } catch (e: Exception) {
                    Logger.e("AdsManager: loadNativeAdForList failed: ${e.message}")
                    onAdFailed()
                }
            }

            override fun onAdFailedToLoad() {
                super.onAdFailedToLoad()
                if (!done.compareAndSet(false, true)) return
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                onAdFailed()
            }
        })
    }

    // ── Interstitial ──────────────────────────────────────────────────────────

    fun showInterstitial(
        activity: Activity,
        adUnitKey: String,
        onComplete: () -> Unit,
        onShowAd: () -> Unit = {},
    ) {
        if (PremiumProvider.isPremium(activity)) {
            Logger.d("AdsRepo: Interstitial skipped — Premium")
            onShowAd()
            onComplete()
            return
        }

        onShowAd()
        val adId = resolveAdId(adUnitKey, AppKit.adKeys.devInterstitialUnitId)
        if (adId.isEmpty()) {
            Logger.d("AdsRepo: Missing ad ID for $adUnitKey")
            onComplete()
            return
        }
        Logger.d("AdsRepo: showInterstitial", "adId=$adUnitKey, adId=$adId")

        Admob.getInstance().loadAndShowInter(activity, adId, 8_000, 100, object : AdCallback() {
            override fun onNextAction() { super.onNextAction(); runWhenResumed(activity, onComplete) }
            override fun onAdFailedToLoad(e: LoadAdError?) {
                super.onAdFailedToLoad(e)
                Logger.e("AdsRepo: Interstitial failed — ${e?.message}")
            }
            override fun onAdClosed() { super.onAdClosed() }
        })
    }

    /**
     * Chạy [action] khi host Activity đã RESUMED (khi inter đóng / lỗi, host mới resume lại). Tránh chạy
     * callback nav ngay lúc Activity còn đang chuyển trạng thái → gây double-navigation / phải back 2 lần.
     * Nếu Activity đã resumed thì chạy ngay; nếu không phải LifecycleOwner cũng chạy ngay (fallback).
     */
    private fun runWhenResumed(activity: Activity, action: () -> Unit) {
        val lc =
            (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle ?: run { action(); return }
        if (lc.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            action(); return
        }
        lc.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                lc.removeObserver(this); action()
            }
        })
    }

    // ── Java-friendly overloads ────────────────────────────────────────────────
    // The interface methods take Kotlin function types (`() -> Unit`); these wrappers
    // accept Runnable/Consumer so View-based (Java) screens can call them cleanly:
    //   mgr.showInterstitial(this, <your key>, () -> next());

    /** Java-friendly interstitial: only an onComplete [Runnable]. */
    fun showInterstitial(activity: Activity, adUnitKey: String, onComplete: Runnable) {
        showInterstitial(activity, adUnitKey, onShowAd = {}, onComplete = { onComplete.run() })
    }

    /** Java-friendly in-list native loader using [Consumer]/[Runnable] callbacks. */
    fun loadNativeAdForList(
        activity:       Activity,
        adUnitKey:      String,
        size:           NativeSize = NativeSize.SMALL,
        onShimmerReady: Consumer<View>,
        onAdLoaded:     Consumer<View>,
        onAdFailed:     Runnable
    ) = loadNativeAdForListInternal(
        activity, adUnitKey, size,
        { onShimmerReady.accept(it) }, { onAdLoaded.accept(it) }, { onAdFailed.run() }, 0L
    )

    /** Java-friendly in-list native loader with a [timeoutMs] safety net (onAdFailed if exceeded). */
    fun loadNativeAdForList(
        activity:       Activity,
        adUnitKey:      String,
        size:           NativeSize = NativeSize.SMALL,
        onShimmerReady: Consumer<View>,
        onAdLoaded:     Consumer<View>,
        onAdFailed:     Runnable,
        timeoutMs:      Long
    ) = loadNativeAdForListInternal(
        activity, adUnitKey, size,
        { onShimmerReady.accept(it) }, { onAdLoaded.accept(it) }, { onAdFailed.run() }, timeoutMs
    )

    // ── Rewarded ────────────────────────────────────────────────────────────────

    fun showRewarded(
        activity: Activity,
        adUnitKey: String,
        onRewardEarned: () -> Unit,
        onShowAd: () -> Unit = {},
        onAdClosed: () -> Unit = {},
        onAdFailedToShow: () -> Unit = {},
    ) {
        if (PremiumProvider.isPremium(activity)) {
            Logger.d("AdsManager: Rewarded skipped — Premium")
            onShowAd()
            onRewardEarned()   // premium ⇒ no ad needed, grant the reward
            return
        }
        onShowAd()
        val adId = resolveAdId(adUnitKey, AppKit.adKeys.devRewardedUnitId)
        if (adId.isEmpty()) {
            Logger.d("AdsManager: Rewarded skipped — empty adId for key=$adUnitKey")
            onAdFailedToShow()
            return
        }
        Logger.d("AdsManager: showRewarded", "key=$adUnitKey, adId=$adId")
        var earned = false
        Admob.getInstance().loadAndShowReward(activity, adId, 100, 10_000, object : RewardCallback {
            override fun onEarnedReward(rewardItem: RewardItem) { earned = true }
            override fun onAdClosed() { if (earned) onRewardEarned() else onAdClosed() }
            override fun onAdFailedToShow(errorCode: Int) {
                Logger.e("AdsManager: Rewarded failed to show — code=$errorCode")
                onAdFailedToShow()
            }
        })
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun resolveAdId(key: String, bannerMain: String): String =
        bannerMain.takeIf { it.isNotBlank() }?.trim()
            ?: RemoteConfigProvider.get().getAdsConfigValue(key)?.trim() ?: ""
}

// ── ViewGroup extension helpers ───────────────────────────────────────────────

/** Replace all children with a freshly-inflated [layoutRes]. */
private fun ViewGroup.safeSwap(activity: Activity, layoutRes: Int) {
    try {
        removeAllViews()
        addView(LayoutInflater.from(activity).inflate(layoutRes, null))
    } catch (e: Exception) {
        Logger.e("safeSwap inflate failed: ${e.message}")
    }
}

/** Replace all children with [view]. */
private fun ViewGroup.safeSwap(view: View) {
    removeAllViews()
    addView(view)
}
