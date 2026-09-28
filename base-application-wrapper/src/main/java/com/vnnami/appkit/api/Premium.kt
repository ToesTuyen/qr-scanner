package com.vnnami.appkit.api

import android.content.Context

/**
 * Premium entitlement — whether the user HAS it. Selling it is [PaywallApi]'s job.
 *
 * Reached as `AppKit.premium`.
 *
 * ```
 * // Read it — cheap, backed by prefs, safe on the main thread.
 * if (AppKit.premium.isPremium(context)) exportInHd() else AppKit.paywall.open(context)
 *
 * // After coming back from a purchase (e.g. in onResume), re-read Play and refresh the UI.
 * AppKit.premium.refresh(context) { isPremium ->
 *     AppKit.ads.refreshPremiumState(context)
 * }
 * ```
 * [set] is the write path for an entitlement your own backend verified. Do not call it to "unlock
 * for testing" in anything you ship — it is the same flag the ad slots and the paywall read.
 */
interface PremiumApi {
    fun isPremium(context: Context): Boolean

    /** Re-reads Google Play. Call after returning from a purchase. */
    fun refresh(context: Context, onResult: (Boolean) -> Unit = {})

    /** Write path for a backend-verified entitlement. */
    fun set(context: Context, isPremium: Boolean)
}
