package com.vnnami.appkit.api

import com.vnnami.appkit.internal.AdsApiImpl
import com.vnnami.appkit.internal.BillingApiImpl
import com.vnnami.appkit.internal.KeysApiImpl
import com.vnnami.appkit.internal.LanguageApiImpl
import com.vnnami.appkit.internal.PaywallApiImpl
import com.vnnami.appkit.internal.PremiumApiImpl

/**
 * The wrapper's public façade — the one object an app calls.
 *
 * Rule of the module: **the app imports `com.vnnami.appkit.api` and nothing else.** Everything
 * under `com.vnnami.appkit.internal` is `internal`, so a wrong call is a compile error rather than
 * something to catch in review. If a feature is missing here, add it here; do not reach past it.
 *
 * Each property below is documented in its own file — [AdsApi] in `Ads.kt`, [PremiumApi] in
 * `Premium.kt`, and so on — together with the Composables for that same feature.
 *
 * ### Wiring a new app
 * Three small files in the app module, then everything else is a call on this object:
 * ```
 * // 1. appkit/AppAdKeys.kt — placement keys + any AdKeys override
 * object AppAdKeys : AdKeys {
 *     const val BANNER_HOME = "banner_home"
 * }
 *
 * // 2. appkit/AppHost.kt — the one callback surface, see AppKitHost
 * object AppHost : AppKitHost {
 *     override val appNameRes = R.string.app_name
 *     override val splashIconRes = R.drawable.ic_splash
 *     override val homeActivity = MainActivity::class.java
 *     override val adKeys = AppAdKeys
 * }
 *
 * // 3. MyApplication.kt — android:name in the manifest
 * @HiltAndroidApp
 * class MyApplication : BaseLibApplication() {
 *     override val host = AppHost
 * }
 * ```
 * From then on:
 * ```
 * AppKit.billing.start(context)                       // once, early
 * AppKit.premium.isPremium(context)                   // entitlement
 * AppKit.paywall.gate(context) { exportInHd() }       // sell it
 * AppBanner(AppAdKeys.BANNER_HOME)                    // a slot, in Compose
 * ```
 */
object AppKit {

    @Volatile
    private var hostRef: AppKitHost? = null

    internal fun bind(host: AppKitHost) { hostRef = host }

    /** Set once by [BaseLibApplication]; reading it before that is a programming error. */
    val host: AppKitHost
        get() = hostRef ?: error(
            "AppKit is not initialised. The Application class must extend " +
                "com.vnnami.appkit.api.BaseLibApplication and return a host.",
        )

    /** Shorthand for `host.adKeys`. */
    val adKeys: AdKeys get() = host.adKeys

    /** Ads that are not Compose. See `Ads.kt`. */
    val ads: AdsApi get() = AdsApiImpl

    /** Premium entitlement. See `Premium.kt`. */
    val premium: PremiumApi get() = PremiumApiImpl

    /** Opening a paywall. See `Paywall.kt`. */
    val paywall: PaywallApi get() = PaywallApiImpl

    /** Google Play Billing. See `Billing.kt`. */
    val billing: BillingApi get() = BillingApiImpl

    /** In-app language. See `Language.kt`. */
    val language: LanguageApi get() = LanguageApiImpl

    /** API keys from KeyVault. See `Keys.kt`. */
    val keys: KeysApi get() = KeysApiImpl
}
