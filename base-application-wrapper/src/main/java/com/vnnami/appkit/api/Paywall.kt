package com.vnnami.appkit.api

import android.content.Context
import androidx.compose.runtime.Composable
import com.vnnami.appkit.internal.ads.rememberPremiumGate as internalPremiumGate
import com.vnnami.appkit.internal.iap.LocalIapRoute

/**
 * There are TWO paywalls in this project and they are not interchangeable. Pick explicitly.
 *
 * | | [Base] | [Local] |
 * |---|---|---|
 * | Lives in | the base-application AAR (binary) | this wrapper, `internal/iap/LocalIapScreen.kt` |
 * | Built with | Views, AAR's own resources | Compose |
 * | Prices from | the AAR's own billing code | [BillingApi] / BillingRepository |
 * | Restyle | not possible — it is compiled in | edit the wrapper |
 * | Copy / plans | fixed by the AAR | `values/string.xml` of the wrapper |
 * | Inside the app's NavHost | no, it is an Activity | yes, via [LocalPaywallRoute] |
 *
 * [Base] is the default because it needs no maintenance. Switch to [Local] when the app wants a
 * paywall it controls, and then own the screen.
 */
enum class Paywall {
    /** The prebuilt paywall Activity inside the base-application AAR. */
    Base,

    /** The wrapper's own Compose paywall. Same screen as [LocalPaywallRoute], hosted in an Activity. */
    Local,
}

/**
 * Opening a paywall. Whether the user is already entitled is [PremiumApi]'s job.
 *
 * Reached as `AppKit.paywall`.
 *
 * ```
 * // Straight to the AAR paywall.
 * AppKit.paywall.open(context)
 *
 * // Gate a feature: runs for premium users, otherwise sells and does NOT run.
 * AppKit.paywall.gate(context) { exportInHd() }
 *
 * // The wrapper's own Compose paywall instead.
 * AppKit.paywall.gate(context, Paywall.Local) { exportInHd() }
 * ```
 * In Compose, prefer [rememberPremiumGate] — same gate, with the house interstitial in front:
 * ```
 * val gate = rememberPremiumGate(onNavigateToPremium = { navController.navigate("premium") })
 * Button(onClick = { gate(AppAdKeys.INTER_OPEN_FEATURE) { exportInHd() } })
 * ```
 * And to host [Paywall.Local] as a NavHost destination:
 * ```
 * composable("premium") { LocalPaywallRoute(onBack = { navController.popBackStack() }) }
 * ```
 */
interface PaywallApi {
    /** Launches [paywall] as its own Activity. No-op when the user is already premium. */
    fun open(context: Context, paywall: Paywall = Paywall.Base)

    /**
     * Runs [onPremium] when the user is entitled; otherwise opens [paywall] and does NOT run it —
     * the feature stays gated until the purchase completes. Returns whether [onPremium] ran.
     */
    fun gate(context: Context, paywall: Paywall = Paywall.Base, onPremium: () -> Unit): Boolean
}

/**
 * Compose form of [PaywallApi.gate] with an ad in front: returns `gate(adKey) { premiumAction() }`,
 * which runs the action for premium users and otherwise shows an interstitial before sending the
 * user to [onNavigateToPremium]. The action does NOT run for a non-premium user.
 */
@Composable
fun rememberPremiumGate(onNavigateToPremium: () -> Unit): (String, () -> Unit) -> Unit =
    internalPremiumGate(onNavigateToPremium)

/**
 * [Paywall.Local] as a destination inside the app's NavHost.
 *
 * There is no route form of [Paywall.Base] — that one lives in the AAR as an Activity and can only
 * be launched with `AppKit.paywall.open(context, Paywall.Base)`.
 */
@Composable
fun LocalPaywallRoute(onBack: () -> Unit) = LocalIapRoute(onNavigateBack = onBack)
