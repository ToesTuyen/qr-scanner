package com.vnnami.appkit.internal.iap

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.vnnami.appkit.internal.language.LocaleManager
import com.vnnami.appkit.internal.billing.PremiumProvider
import dagger.hilt.android.AndroidEntryPoint

/**
 * Lightweight host for [LocalIapRoute] so View-based apps can launch the Compose
 * paywall via a plain Intent. [IapViewModel] is Hilt-injected (it depends on
 * BillingRepository), so the host app must be a `@HiltAndroidApp` Application.
 */
@AndroidEntryPoint
internal class IapActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply the user's chosen in-app language so the paywall renders localized. This Activity
        // is launched standalone (not under MainActivity), so without this it falls back to the
        // system locale and the LocalIapScreen strings appear untranslated. Tag is the one the
        // wrapper Language screen persists (e.g. "vi", "en-rGB"); wrapContext handles the "-r" form.
        val tag = newBase.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
            .getString("language_tag", "").orEmpty()
        val ctx = if (tag.isNotEmpty()) LocaleManager.wrapContext(newBase, tag) else newBase
        super.attachBaseContext(ctx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Already premium → no paywall to show.
        if (PremiumProvider.isPremium(this)) {
            finish()
            return
        }
        setContent {
            LocalIapRoute(onNavigateBack = { finish() })
        }
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context): Intent = Intent(context, IapActivity::class.java)
    }
}
