package com.vnnami.appkit.internal.ads

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vnnami.appkit.api.BannerSize
import com.vnnami.appkit.api.NativeSize
import com.vnnami.appkit.internal.ads.AdsManagerImpl
import com.vnnami.appkit.internal.util.findActivity

/**
 * Hiển thị Banner Ad sử dụng Jetpack Compose.
 * Sẽ tự động ẩn đi nếu user là Premium hoặc Ad không load được.
 */
@Composable
internal fun BannerAdView(
    adKey: String,
    modifier: Modifier = Modifier,
    size: BannerSize = BannerSize.ADAPTIVE
) {
    val context = LocalContext.current
    val adsManager = remember { AdsManagerImpl.getInstance(context) }
    
    // Thu thập trạng thái showAds (true nếu không phải Premium)
    val shouldShowAds by adsManager.shouldShowAds().collectAsState(initial = true)

    if (shouldShowAds) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                    
                    val activity = ctx.findActivity()
                    if (activity != null) {
                        adsManager.showBanner(activity, this, adKey, size)
                    }
                }
            },
            update = { frameLayout ->
                // Chỉ gọi khởi tạo 1 lần ở factory để tránh việc recomposition làm tải lại quảng cáo liên tục
            }
        )
    }
}

/**
 * Returns a `showInter(adKey, onComplete)` function the UI can call from a
 * click handler. The helper resolves the host Activity from Compose context,
 * forwards to the singleton [AdsManagerImpl], and falls back to invoking
 * [onComplete] directly when no Activity is available (e.g. inside @Preview).
 *
 * ```
 * val showInter = rememberShowInterstitial()
 * Button(onClick = {
 *     showInter(AppAdKeys.INTER_OPEN_TOOL) { onOpenCreate() }
 * })
 * ```
 */
@Composable
internal fun rememberShowInterstitial(): (String, () -> Unit) -> Unit {
    val context = LocalContext.current
    val adsManager = remember(context) { AdsManagerImpl.getInstance(context) }
    return remember(context, adsManager) {
        { adKey: String, onComplete: () -> Unit ->
            val activity = context.findActivity()
            if (activity != null) {
                adsManager.showInterstitial(activity, adKey, onComplete = onComplete)
            } else {
                onComplete()
            }
        }
    }
}

/**
 * Returns a `showReward(adKey, onRewarded)` function for Compose click handlers. Shows a rewarded
 * ad via the singleton [AdsManagerImpl]; [onRewarded] runs ONLY when the reward is earned (a skipped
 * ad / failure does nothing). With no Activity (e.g. @Preview) it runs [onRewarded] directly.
 *
 * ```
 * val showReward = rememberShowReward()
 * Button(onClick = { showReward(AppAdKeys.REWARD_PLAY) { grantTrial() } })
 * ```
 */
@Composable
internal fun rememberShowReward(): (String, () -> Unit) -> Unit {
    val context = LocalContext.current
    val adsManager = remember(context) { AdsManagerImpl.getInstance(context) }
    return remember(context, adsManager) {
        { adKey: String, onRewarded: () -> Unit ->
            val activity = context.findActivity()
            if (activity != null) {
                adsManager.showRewarded(activity, adKey, onRewardEarned = onRewarded)
            } else {
                onRewarded()
            }
        }
    }
}

/**
 * Premium gate. Returned lambda `gate(adKey, onProceed)`:
 *  - If the user is **premium**, runs [onProceed] immediately (no ad, no IAP).
 *  - Otherwise shows an interstitial under [adKey]; once the ad is dismissed,
 *    routes the user to the IAP screen via [onNavigateToPremium].
 *    The original action does NOT run — the feature stays gated until the
 *    user actually subscribes.
 *
 * ```
 * val gate = rememberPremiumGate(onNavigateToPremium = onNavigateToPremium)
 * Button(onClick = {
 *     gate(AppAdKeys.INTER_OPEN_TOOL) { onOpenCreate() }
 * })
 * ```
 */
@Composable
internal fun rememberPremiumGate(
    onNavigateToPremium: () -> Unit,
): (String, () -> Unit) -> Unit {
    val context = LocalContext.current
    val adsManager = remember(context) { AdsManagerImpl.getInstance(context) }
    val showAds by adsManager.shouldShowAds().collectAsState(initial = true)
    val showInter = rememberShowInterstitial()
    return remember(showAds, showInter, onNavigateToPremium) {
        { adKey: String, onProceed: () -> Unit ->
            if (!showAds) {
                // shouldShowAds == false → user is premium → unlock the feature.
                onProceed()
            } else {
                showInter(adKey) {
                    onNavigateToPremium()
                }
            }
        }
    }
}

/**
 * Hiển thị Native Ad sử dụng Jetpack Compose.
 */
@Composable
internal fun NativeAdView(
    adKey: String,
    modifier: Modifier = Modifier,
    size: NativeSize = NativeSize.DEFAULT,
    onAvailabilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val adsManager = remember { AdsManagerImpl.getInstance(context) }
    var isAvailable by remember(adKey, size) { mutableStateOf(false) }
    val latestAvailabilityCallback by rememberUpdatedState(onAvailabilityChanged)

    val shouldShowAds by adsManager.shouldShowAds().collectAsState(initial = true)
    LaunchedEffect(shouldShowAds) {
        if (!shouldShowAds) {
            isAvailable = false
            latestAvailabilityCallback(false)
        }
    }

    if (shouldShowAds) {
        AndroidView(
            // Keep the loader composed so it can expand after success, but make it truly
            // unmeasurable until then. This collapses LazyRow cards, grid rows and guard spacers.
            modifier = if (isAvailable) {
                modifier.fillMaxWidth().wrapContentHeight()
            } else {
                Modifier.size(0.dp)
            },
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                    
                    val activity = ctx.findActivity()
                    if (activity != null) {
                        adsManager.loadNativeAdNoMedia(activity, this, adKey, size) { available ->
                            isAvailable = available
                            latestAvailabilityCallback(available)
                        }
                    } else {
                        latestAvailabilityCallback(false)
                    }
                }
            },
            update = { frameLayout ->
                // Chỉ gọi khởi tạo 1 lần ở factory
            }
        )
    }
}
