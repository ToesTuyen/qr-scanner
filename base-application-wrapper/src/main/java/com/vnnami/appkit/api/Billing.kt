package com.vnnami.appkit.api

import android.content.Context

/**
 * Google Play Billing, as far as an app needs to touch it. Prices and purchase flows are driven by
 * whichever paywall is shown — see [Paywall].
 *
 * Reached as `AppKit.billing`.
 *
 * ```
 * class MainActivity : ComponentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         AppKit.billing.start(this)   // once; connecting later means the paywall opens priceless
 *     }
 * }
 * ```
 * The Play products themselves are not configured here — the AAR resolves them from the license key
 * in `base-application-wrapper/res/values/ads_id.xml` and the product ids the AAR was built with.
 *
 * The dependency is pinned to Billing **8.0.0** in the module's build.gradle.kts, matching what the
 * AAR was compiled against: its `BillingProcessor` implements `ProductDetailsResponseListener` with
 * the 8.x signature only, so on 7.x the product-details callback never fires and the paywall shows
 * no prices. Do not lower it.
 */
interface BillingApi {
    /** Starts the billing connection. Call once, early (e.g. the first Activity's onCreate). */
    fun start(context: Context)
}
