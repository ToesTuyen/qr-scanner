package com.vnnami.appkit.api

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * The single callback surface an app implements to configure the wrapper.
 *
 * Everything the wrapper needs to know about the host app arrives through here, so the app never
 * overrides wrapper internals one by one. Implement it once, return it from
 * [com.vnnami.appkit.api.BaseLibApplication.host], and the wrapper feeds it to the
 * base-application AAR.
 *
 * Only the first three members have no default — a minimal host is three lines:
 * ```
 * object AppHost : AppKitHost {
 *     override val appNameRes = R.string.app_name
 *     override val splashIconRes = R.drawable.ic_splash
 *     override val homeActivity = MainActivity::class.java
 * }
 * ```
 * A fuller one, with this app's ad keys, KeyVault on, and the locale mirrored into app state:
 * ```
 * object AppHost : AppKitHost {
 *     override val appNameRes = R.string.app_name
 *     override val splashIconRes = R.drawable.ic_splash
 *     override val homeActivity = MainActivity::class.java
 *     override val adKeys = AppAdKeys
 *     override val keyVaultAppId = "tuyennd1"
 *     override fun onLanguageApplied(languageTag: String) {
 *         Logger.d("Language applied", languageTag)
 *     }
 * }
 * ```
 * Adding a knob here — rather than overriding something in the wrapper — is what keeps this module
 * identical across clones.
 */
interface AppKitHost {

    /** Launcher label. Lives in the app module so the AAR splash shows the app's own name. */
    @get:StringRes
    val appNameRes: Int

    /** Icon the AAR splash renders. */
    @get:DrawableRes
    val splashIconRes: Int

    /** Where the AAR splash goes when it finishes. */
    val homeActivity: Class<out Activity>

    /** Placement keys this app resolves through Firebase Remote Config. */
    val adKeys: AdKeys
        get() = AdKeys.Default

    /**
     * Controls whether the legacy AAR boot sequence runs.
     *
     * Keep [AppKitBootstrapMode.STANDARD] for a normal app with its own Firebase project. Use
     * [AppKitBootstrapMode.LOCAL_ONLY] only for an explicitly offline POC that has no active
     * dependency on AAR-owned ads, Remote Config, AppsFlyer, notifications or billing.
     */
    val bootstrapMode: AppKitBootstrapMode
        get() = AppKitBootstrapMode.STANDARD

    /**
     * App id in the KeyVault admin, e.g. "tuyennd1". Blank (the default) turns KeyVault off, and
     * the app behaves exactly as if it were not integrated.
     *
     * Turning it on also needs `KV_CONTENT_KEY` in the root local.properties and the encrypted blob
     * at `app/src/main/res/raw/keyvault_defaults.json`. Nothing else, and no app Gradle changes.
     */
    val keyVaultAppId: String
        get() = ""

    /** CDN origin that serves the encrypted key blob. Override only to point at another vault. */
    val keyVaultCdnBase: String
        get() = "https://media-keyvault-config.goldenboat.us"

    /**
     * Fired after the user applies a language on the wrapper's Language screen.
     * [languageTag] is BCP-47 ("vi", "zh-TW"). Mirror it into the app's own locale handling.
     */
    fun onLanguageApplied(languageTag: String) {}
}
