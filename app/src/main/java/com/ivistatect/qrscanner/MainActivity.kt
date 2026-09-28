package com.ivistatect.qrscanner

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.vnnami.appkit.api.AppKit
import com.vnnami.appkit.api.Logger
import dagger.hilt.android.AndroidEntryPoint
import com.ivistatect.qrscanner.ui.AppRoot
import com.ivistatect.qrscanner.ui.theme.QrScannerTheme

/**
 * Single-activity Compose host (Scanner / History / Create / Settings + result/create/detail routes).
 *
 * FragmentActivity, not ComponentActivity: the base-application AAR shows DialogFragments that need a
 * FragmentManager. @AndroidEntryPoint so the wrapper's Hilt-injected `LanguageRoute` resolves.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = AppKit.language
        super.attachBaseContext(language.wrapContext(newBase, language.currentTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Deliberate edge-to-edge: content draws behind the transparent status/navigation bars
        // (theme sets both transparent); Compose applies WindowInsets on every screen with a CTA.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Play Billing connection once, early. External/gated; safe under LOCAL_ONLY.
        runCatching { AppKit.billing.start(this) }
        setContent { QrScannerTheme { AppRoot() } }
        Logger.d("Enter MainActivity")
    }
}
