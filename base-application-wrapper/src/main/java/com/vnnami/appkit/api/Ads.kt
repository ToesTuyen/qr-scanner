package com.vnnami.appkit.api

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vnnami.appkit.internal.ads.BannerAdView
import com.vnnami.appkit.internal.ads.NativeAdView
import com.vnnami.appkit.internal.ads.rememberShowInterstitial
import com.vnnami.appkit.internal.ads.rememberShowReward

/**
 * Everything public about ads: the non-Compose operations, the Composable slots, and the shape
 * presets. Placement keys are NOT here — they belong to the app, see [AdKeys].
 *
 * Reached as `AppKit.ads`.
 *
 * All four formats, as an app uses them:
 * ```
 * // Banner — place it, forget it. Zero height when premium or the key resolves to "".
 * AppBanner(AppAdKeys.BANNER_HOME, Modifier.align(Alignment.BottomCenter))
 *
 * // Native — between list rows; the flag lets you drop the spacing when no ad attached.
 * var hasAd by remember { mutableStateOf(false) }
 * AppNativeAd(AppAdKeys.NATIVE_LIST, size = NativeSize.SMALL) { hasAd = it }
 *
 * // Interstitial — as a navigation tax; the continuation always runs.
 * val showInter = rememberInterstitial()
 * Button(onClick = { showInter(AppAdKeys.INTER_BACK) { navController.popBackStack() } })
 *
 * // Rewarded — the lambda runs ONLY if the reward was earned.
 * val showReward = rememberRewarded()
 * Button(onClick = { showReward(AppAdKeys.REWARD_UNLOCK) { unlockTemplate() } })
 * ```
 * Outside Compose, use [showInterstitial] / [showRewarded] with an Activity. After a purchase
 * completes, call [refreshPremiumState] so every slot on screen stops drawing immediately.
 *
 * House placement rules this project follows: interstitials are a navigation tax (leaving a screen),
 * never on a core action; banners go everywhere except Settings and Language; a native goes after
 * the first list row, then every fourth.
 */
interface AdsApi {
    /** Re-reads entitlement so ad slots stop drawing as soon as the user becomes premium. */
    fun refreshPremiumState(context: Context)

    fun showInterstitial(activity: Activity, adKey: String, onComplete: () -> Unit)

    fun showRewarded(
        activity: Activity,
        adKey: String,
        onRewardEarned: () -> Unit,
        onDismissed: () -> Unit = {},
    )
}

/**
 * Banner slot. Measures zero and draws nothing when the user is premium or the key resolves to an
 * empty unit id, so it is safe to place unconditionally.
 */
@Composable
fun AppBanner(
    adKey: String,
    modifier: Modifier = Modifier,
    size: BannerSize = BannerSize.ADAPTIVE,
) = BannerAdView(adKey = adKey, modifier = modifier, size = size)

/**
 * Native ad, for injecting between rows of a vertical list.
 *
 * House placement rule: put one after the FIRST row, then one every 4 rows from there
 * (`index % 4 == 0`), and keep ~24dp of empty space above and below it — the rows on either side
 * are themselves tappable, so without that gap a mis-aimed tap lands on the ad. Use
 * [onAvailabilityChanged] to add that spacing only when an ad actually attached, otherwise a
 * premium user or an empty unit id leaves a visible hole in the list.
 */
@Composable
fun AppNativeAd(
    adKey: String,
    modifier: Modifier = Modifier,
    size: NativeSize = NativeSize.DEFAULT,
    onAvailabilityChanged: (Boolean) -> Unit = {},
) = NativeAdView(
    adKey = adKey,
    modifier = modifier,
    size = size,
    onAvailabilityChanged = onAvailabilityChanged,
)

/**
 * Returns `show(adKey) { next() }`. The continuation runs exactly once whether the ad showed,
 * failed or was skipped, so navigation never gets stranded behind a missing ad.
 */
@Composable
fun rememberInterstitial(): (String, () -> Unit) -> Unit = rememberShowInterstitial()

/** Returns `show(adKey) { grantReward() }`. The lambda runs ONLY when the reward is earned. */
@Composable
fun rememberRewarded(): (String, () -> Unit) -> Unit = rememberShowReward()

/**
 * Banner shapes. Deliberately payload-free so the public API never mentions a type from the
 * base-application AAR — the mapping to the AAR's own presets lives in the internal ads layer.
 */
enum class BannerSize { ADAPTIVE, STANDARD, COLLAPSIBLE_BOTTOM }

/**
 * Native-ad layout presets.
 *
 * | Preset   | Shape                                             |
 * |----------|---------------------------------------------------|
 * | SMALL    | compact, for injecting between rows of a list      |
 * | NORMAL   | compact, no media                                  |
 * | DEFAULT  | full width, bottom of screen                       |
 * | EXTENDED | same layouts as DEFAULT, kept as a semantic alias  |
 */
enum class NativeSize { SMALL, NORMAL, DEFAULT, EXTENDED }
