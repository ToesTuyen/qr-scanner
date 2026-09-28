package com.ivistatect.qrscanner.appkit

import com.vnnami.appkit.api.AdKeys

/**
 * Everything about ads for THIS app, in one object (the app's side of the wrapper seam) — the single
 * file to read when asking "what ads does this app have?".
 *
 * The placement call-sites are wired (see docs/AD_PLACEMENTS.md, docs/ADS_KEY_AUDIT.md) but the
 * default configuration is **production-safe no-op**: every key maps to `""` in
 * `assets/default_ads_config.json`, so each slot resolves to no unit id and draws nothing /
 * fires its continuation immediately — no request, no render, no crash. Real unit ids arrive only
 * through the project's own Firebase Remote Config later. The five dev unit ids stay blank so a
 * release never carries a test id (see the [AdKeys] dev-override note: BLANK IN ANYTHING SHIPPED).
 *
 * Placement keys are grouped by screen family, not one-per-screen:
 *  - Banners: [BANNER_HOME] (Scanner/History/Create home tabs), [BANNER_INNER] (Result/Created/Form).
 *  - Native (in-feed, no-media): [NATIVE_LIST] (History list — after row 1, then every 4th).
 *  - Interstitial (navigation-transition only, never a core action, never the default Scanner tab,
 *    never the ad-free Settings/Language surfaces): [INTER_TAB_HISTORY], [INTER_TAB_CREATE] (open a
 *    feature tab), [INTER_BACK_HOME] (back from a feature tab to the Scanner home).
 *
 * No rewarded key: the reference has no "watch ad → one free use" entitlement contract (its only
 * paywall is the subscription ProX screen), so per policy no rewarded placement is created.
 */
object AppAdKeys : AdKeys {
    // Dev overrides — all blank: QA may temporarily set a Google test id here to see a creative, but
    // every one MUST be "" before a release build (they win over config AND Remote Config).
    override val devOpenAdUnitId = ""
    override val devBannerUnitId = ""
    override val devNativeUnitId = ""
    override val devRewardedUnitId = ""
    override val devInterstitialUnitId = ""

    // ── Placement keys — plain strings passed to the public wrapper slots/callbacks. ──
    // Each resolves through default_ads_config.json (currently "" = ad-free) → Remote Config → unit id.
    const val BANNER_HOME = "banner_home"          // Scanner / History / Create home tabs
    const val BANNER_INNER = "banner_inner"        // Result / Created / Create-form inner screens
    const val NATIVE_LIST = "native_list"          // History list feed (after row 1, then every 4th)
    const val INTER_TAB_HISTORY = "inter_tab_history" // navigation → open History tab
    const val INTER_TAB_CREATE = "inter_tab_create"   // navigation → open Create tab
    const val INTER_BACK_HOME = "inter_back_home"     // navigation → back from a feature tab to Home
}
