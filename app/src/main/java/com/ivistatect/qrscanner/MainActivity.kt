package com.ivistatect.qrscanner

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import com.ivistatect.qrscanner.ui.AppRoot
import com.ivistatect.qrscanner.ui.common.hideSystemNavigationBar
import com.ivistatect.qrscanner.ui.language.AppLanguage
import com.ivistatect.qrscanner.ui.theme.QrScannerTheme
import com.ivistatect.qrscanner.util.Logger

/**
 * Single-activity Compose host for the standalone Scanner, History, Settings and result routes.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Content is edge-to-edge; the app hides only Android's navigation controls.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemNavigationBar()
        setContent { QrScannerTheme { AppRoot() } }
        Logger.d("Enter MainActivity")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigationBar()
    }
}
