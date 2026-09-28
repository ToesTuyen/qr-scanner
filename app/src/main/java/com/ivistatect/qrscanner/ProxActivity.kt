package com.ivistatect.qrscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.vnnami.appkit.api.AppKit
import com.vnnami.appkit.api.Logger
import com.ivistatect.qrscanner.ui.prox.ProxScreen
import com.ivistatect.qrscanner.ui.theme.QrScannerTheme

/**
 * Native ProX-style paywall host (n_prox), retained for legacy deep links.
 * The CTA hits the billing boundary only — purchase is external/GATED (n_billing).
 */
class ProxActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = AppKit.language
        super.attachBaseContext(language.wrapContext(newBase, language.currentTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false)
        if (AppKit.premium.isPremium(this)) {
            Logger.d("ProX skipped — already premium")
            close(fromStart)
            return
        }
        setContent {
            QrScannerTheme {
                ProxScreen(
                    onClose = { close(fromStart) },
                    onStartTrial = {
                        // External/gated: no purchase completes in the POC (out of authorization).
                        Logger.d("ProX Start Trial → billing boundary (external/gated)")
                        Toast.makeText(this, "Purchase is handled by Google Play (not in this build)", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    private fun close(fromStart: Boolean) {
        if (fromStart) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_FROM_START = "from_start"
    }
}
