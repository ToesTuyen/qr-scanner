package com.ivistatect.qrscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vnnami.appkit.api.AppKit
import com.vnnami.appkit.api.Logger
import com.ivistatect.qrscanner.ui.theme.QrScannerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Launcher = branded routing splash (n_start / n_route_gate). Clone baseline is premium/no-ads, so
 * the route gate takes the Splash/Main branch (matching the Step-2 baseline runtime evidence). The
 * ProX paywall remains available as a standalone Activity for legacy deep links.
 */
class StartActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = AppKit.language
        super.attachBaseContext(language.wrapContext(newBase, language.currentTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d("Enter StartActivity", "route gate")
        setContent { QrScannerTheme { SplashContent() } }
        lifecycleScope.launch {
            delay(700)
            Logger.d("StartActivity route → MainActivity", "premium=${AppKit.premium.isPremium(this@StartActivity)}")
            startActivity(Intent(this@StartActivity, MainActivity::class.java))
            finish()
        }
    }
}

@Composable
private fun SplashContent() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.ic_qr_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
