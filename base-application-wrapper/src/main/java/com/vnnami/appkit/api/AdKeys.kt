package com.vnnami.appkit.api

/**
 * Ad settings base-application-wrapper and the AAR read back from the app, through `AppKit.adKeys`.
 *
 * Two kinds of member live here:
 *  - **Config-key names** — strings the AAR looks up in `default_ads_config.json` / Firebase Remote
 *    Config for its own screens. The AAR expects these exact names, so they almost never change.
 *  - **Dev unit-id overrides** — a real AdMob unit id that short-circuits the lookup for a whole
 *    slot type, used to verify placement before the app owns any unit.
 *
 * This interface holds NO placement keys. Where an app puts its own banners, interstitials and
 * natives is per-app, so those are declared in the app module and passed in as plain strings —
 * see [AppBanner], [rememberInterstitial].
 *
 * An app implements it once and keeps its own placement keys in the same object, so there is a
 * single file to read when asking "what ads does this app have?":
 * ```
 * object AppAdKeys : AdKeys {
 *     // Overrides only where this app differs from the defaults below.
 *     override val devBannerUnitId = "ca-app-pub-3940256099942544/9214589741"  // dev only, blank to ship
 *
 *     // This app's own placements — plain strings, passed to the slots.
 *     const val BANNER_HOME  = "banner_home"
 *     const val INTER_BACK   = "inter_back_home"
 *     const val NATIVE_LIST  = "native_list"
 * }
 * ```
 * ```
 * object AppHost : AppKitHost { override val adKeys = AppAdKeys /* ... */ }
 * ```
 * How a key becomes an ad — every name here and every placement key above goes the same route:
 * ```
 * "banner_home" -> assets/default_ads_config.json -> Firebase Remote Config -> AdMob unit id
 * ```
 * A name missing from `default_ads_config.json`, or mapped to `""`, resolves to no unit id and the
 * slot draws nothing. That is the intended way to switch a placement off — not deleting the slot.
 *
 * Not every member below is wired: `openResume`, `nativeLanguage`, `privacyPolicyUrl` and
 * `termsOfUseUrl` are declared for completeness but nothing in the wrapper, the app or the AAR
 * reads them today. In particular the AAR's resume ad follows [openSplash], not [openResume].
 */
interface AdKeys {

    // ── Config-key names the base-application AAR resolves for itself ──────────────────────────
    val openSplash: String get() = "open_splash"                // App Open, over the AAR splash at boot
    val openResume: String get() = "open_all"                   // App Open, on return from background
    val interstitialInterval: String get() = "inter_splash"     // NOT an ad: gap the AAR keeps between inters
    val nativeLanguage: String get() = "native_language"        // Native on the wrapper's Language screen
    val privacyPolicyUrl: String get() = "privacy_policy"       // NOT an ad: URL on AAR IAP/Language screens
    val termsOfUseUrl: String get() = "term_of_use"             // NOT an ad: URL on those same two screens

    // ── Dev overrides ─────────────────────────────────────────────────────────────────────────
    // A non-blank value WINS over default_ads_config.json AND over Remote Config, for every slot of
    // that type. Google's test units: open /9257395921 · inter /1033173712 · banner /9214589741 ·
    // native /2247696110 · reward /5224354917, all under publisher ca-app-pub-3940256099942544.
    // BaseLibApplication also feeds devInterstitialUnitId to the AAR where it expects
    // interstitialInterval, so a unit id there silently stops interstitials from showing — put the
    // interstitial test id in default_ads_config.json instead.
    // >>> ALL FIVE MUST BE BLANK IN ANYTHING YOU SHIP. <<<
    val devOpenAdUnitId: String get() = ""                      // forces every App Open slot
    val devInterstitialUnitId: String get() = ""                // forces every interstitial — see note above
    val devBannerUnitId: String get() = ""                      // forces every banner slot
    val devNativeUnitId: String get() = ""                      // forces every native slot
    val devRewardedUnitId: String get() = ""                    // forces every rewarded slot

    companion object {
        val Default: AdKeys = object : AdKeys {}            // all defaults, for a host overriding nothing
    }
}
