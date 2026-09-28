package com.vnnami.appkit.api

import android.app.Activity
import com.brian.base_application.BaseApplication
import com.nlbn.ads.util.Admob
import com.nlbn.ads.util.AppFlyer
import com.vnnami.appkit.BuildConfig
import com.vnnami.appkit.R
import com.goldenboat.keyvault.KeyVaultProvider
import com.vnnami.appkit.internal.billing.PremiumProvider
import com.vnnami.appkit.internal.ads.RemoteConfigProvider

/**
 * Application base every app extends.
 *
 * An app supplies ONE thing — an [AppKitHost] — and the wrapper feeds it to the base-application
 * AAR. The AAR's ~45 configuration hooks are implemented here and marked `final` on purpose: if an
 * app needs to change one, the knob belongs on [AppKitHost], not in an ad-hoc override. That is what
 * keeps this module from drifting per clone.
 *
 * ```
 * @HiltAndroidApp
 * class MyApplication : BaseLibApplication() {
 *     override val host = AppHost
 * }
 * ```
 * ```xml
 * <application android:name=".MyApplication" ... >
 * ```
 * `super.onCreate()` is what boots the AAR (Firebase, ads SDK, AppsFlyer, the splash), so an app
 * that overrides [onCreate] must call through. Two things run BEFORE it on purpose: [AppKit] is
 * bound so `AppKit.*` is usable from the first line of app code, and KeyVault decrypts its blob so
 * a key is in RAM before any screen asks for one.
 *
 * What the app still owns outside this class:
 *  - `app/src/main/assets/default_ads_config.json` — every key in [AdKeys] and every placement key
 *    the app declares, mapped to an AdMob unit id (empty string = that slot draws nothing).
 *  - `app/google-services.json` — this app's OWN Firebase project. Ad unit ids come from its Remote
 *    Config, so a leftover file from another app serves that app's live ad config.
 *  - `base-application-wrapper/res/values/ads_id.xml` — the one file inside this module a clone is
 *    meant to edit (AdMob app id, Facebook app id + client token, AppsFlyer id, Play license key).
 */
abstract class BaseLibApplication : BaseApplication() {

    /** Built once, before anything else touches the wrapper. */
    abstract val host: AppKitHost

    override fun onCreate() {
        AppKit.bind(host)
        // Central API-key store: decrypt the bundled blob with the content-key so a key is in RAM
        // before any screen asks. A host that leaves keyVaultAppId blank is skipped silently.
        KeyVaultProvider.init(
            this,
            host.keyVaultAppId,
            host.keyVaultCdnBase,
            BuildConfig.KV_CONTENT_KEY,
            readKeyVaultDefaults(),
        )
        if (host.bootstrapMode == AppKitBootstrapMode.LOCAL_ONLY) {
            Logger.d(
                "AppKit local-only bootstrap",
                "Skipped legacy AAR startup: Firebase Remote Config, ads SDK and AppsFlyer are disabled.",
            )
        } else {
            super.onCreate()
            if (BuildConfig.DEBUG) Admob.getInstance().setIntervalShowInterstitial(8)
        }
    }

    /**
     * The encrypted blob the app ships at `res/raw/keyvault_defaults.json`. Resolved by name so the
     * wrapper never needs the app's generated R class.
     */
    private fun readKeyVaultDefaults(): String = runCatching {
        val id = resources.getIdentifier("keyvault_defaults", "raw", packageName)
        if (id == 0) "" else resources.openRawResource(id).bufferedReader().use { it.readText() }
    }.getOrDefault("")

    // ── Fed from AppKitHost ────────────────────────────────────────────────────────────────
    final override fun getAppNameRes(): Int = host.appNameRes
    final override fun getIconSplashRes(): Int = host.splashIconRes
    final override fun getHomeActivity(): Class<out Activity> = host.homeActivity

    final override fun initAppFlyerId() {
        AppFlyer.getInstance().initAppFlyer(this, getString(R.string.app_flyer_id), true, false, true)
    }

    final override fun notifyLanguageSaved(languageCode: String) {
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(languageCode),
        )
        host.onLanguageApplied(languageCode.replace("-r", "-"))
    }

    final override fun getResumeAdId(): String =
        host.adKeys.devOpenAdUnitId.takeIf { it.isNotBlank() }?.trim()
            ?: RemoteConfigProvider.get().getAdsConfigValue(host.adKeys.openSplash)?.trim().orEmpty()

    final override fun getKeyRemoteIntervalShowInterstitial(): String =
        host.adKeys.devInterstitialUnitId.takeIf { it.isNotBlank() }?.trim()
            ?: RemoteConfigProvider.get().getAdsConfigValue(host.adKeys.interstitialInterval)?.trim().orEmpty()

    // ── Fixed wrapper policy ───────────────────────────────────────────────────────────────
    final override fun getListTestDeviceId(): List<String> = emptyList()
    final override fun isForceShowFullAdsTest(): Boolean = true
    final override fun isPurchased(): Boolean = PremiumProvider.isPremium(applicationContext)
    final override fun enableAdsResume(): Boolean = !PremiumProvider.isPremium(applicationContext)
    final override fun hasForegroundServicePermission(): Boolean = true
    final override fun setupKoin() = Unit

    final override fun iapPremiumKey(): String = defaultIapPremiumKey()
    final override fun iapPremiumWeeklyKey(): String = defaultIapPremiumWeeklyKey()
    final override fun iapPremiumMonthlyKey(): String = defaultIapPremiumMonthlyKey()
    final override fun iapPremiumYearlyKey(): String = defaultIapPremiumYearlyKey()
    final override fun iapPublicKey(): String = getString(R.string.public_license_key)

    // ── Assets the AAR renders. These still come from the wrapper's own res; a clone that wants
    //    its own artwork replaces the drawables, it does not override these methods. ──────────
    final override fun getSplashLoadingRes(): Int = R.raw.splash_loading
    final override fun getNotificationImages() = intArrayOf(
        R.drawable.icon_noti_1, R.drawable.icon_noti_2, R.drawable.icon_noti_3,
        R.drawable.icon_noti_4, R.drawable.icon_noti_5,
    )
    final override fun getNotificationIconRes() = R.drawable.icon_noti_1
    final override fun getNotificationChannelPrefix() = "AppKit"
    final override fun getNewFileNotiContentRes() = R.string.new_file_content_notification
    final override fun getScreenshotNotiTitleRes() = R.string.notification_description_11
    final override fun getRecentDocumentsTitleRes() = R.string.your_recent_documents
    final override fun getOpenTextRes() = R.string.open
    final override fun getScanDocumentRes() = R.string.clean_files
    final override fun getWidgetButtonBackgroundRes() = R.drawable.btn_background_primary
    final override fun getDailyCallOpenAppContentRes() = R.string.daily_call_open_app_content
    final override fun getCheckNowTextRes() = R.string.check_now
    final override fun getDocumentPreviewRes() = R.drawable.img_document_preview
    final override fun getFullScreenNoti1Res() = R.string.full_screen_noti
    final override fun getFullScreenNoti2Res() = R.string.full_screen_noti2
    final override fun getNotificationTitles2ArrayRes() = R.array.notification_title2
    final override fun getNotificationMessages2ArrayRes() = R.array.notification_message2
    final override fun getNotificationButtons2ArrayRes() = R.array.notification_button2
    final override fun getNotiTitleRes() = R.string.noti_title
    final override fun getNotiContentRes() = R.string.noti_content
    final override fun getNotificationOutAppTitleRes() = R.string.you_have_unfinished_file
    final override fun getNotificationOutAppContentRes() = R.string.click_to_see_more

    final override fun getFeature1TextRes(): Int = R.string.feature1_text
    final override fun getFeature1IconRes() = R.drawable.icon_pdf_iap
    final override fun getFeature2TextRes(): Int = R.string.feature2_text
    final override fun getFeature2IconRes() = R.drawable.icon_word_iap
    final override fun getFeature3TextRes(): Int = R.string.feature3_text
    final override fun getFeature3IconRes() = R.drawable.icon_all_file_iap
    final override fun getFeature4TextRes(): Int = R.string.feature4_text
    final override fun getFeature4IconRes() = R.drawable.icon_phone_iap
    final override fun getFeature5TextRes(): Int = R.string.feature5_text
    final override fun getFeature5IconRes() = R.drawable.icon_remove_iap
}
