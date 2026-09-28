package com.ivistatect.qrscanner.appkit

import android.app.Activity
import com.vnnami.appkit.api.AdKeys
import com.vnnami.appkit.api.AppKitBootstrapMode
import com.vnnami.appkit.api.AppKitHost
import com.vnnami.appkit.api.Logger
import com.ivistatect.qrscanner.MainActivity
import com.ivistatect.qrscanner.R

/**
 * Package `…qrscanner.appkit` — this app's side of base-application-wrapper.
 *
 * Everything that configures the wrapper for this app lives here and nowhere else. The app pushes
 * values IN through [AppKitHost]; it never reaches into the wrapper. See docs/TEMPLATE_SURVEY.md.
 */
object AppHost : AppKitHost {

    override val appNameRes: Int = R.string.app_name
    override val splashIconRes: Int = R.drawable.ic_qr_logo
    override val homeActivity: Class<out Activity> = MainActivity::class.java
    override val adKeys: AdKeys = AppAdKeys

    /**
     * Offline POC: skip the legacy AAR boot (Firebase Remote Config, ads SDK, AppsFlyer,
     * AAR notifications/billing). Step 5 has no Firebase project by design — release services are
     * deferred (docs/DEFERRED_SERVICES.md). The app uses only local data + the public AppKit API.
     */
    override val bootstrapMode: AppKitBootstrapMode = AppKitBootstrapMode.LOCAL_ONLY

    /** KeyVault OFF — a QR scanner needs no server-held API key. */
    override val keyVaultAppId: String = ""

    override fun onLanguageApplied(languageTag: String) {
        // The wrapper already persisted the choice; the app keeps no second copy.
        Logger.d("Language applied", "tag=$languageTag")
    }
}
