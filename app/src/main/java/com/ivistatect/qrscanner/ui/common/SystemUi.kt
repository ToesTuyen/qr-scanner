package com.ivistatect.qrscanner.ui.common

import android.app.Activity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Keeps Android's navigation controls hidden until the user intentionally swipes from the bottom. */
fun Activity.hideSystemNavigationBar() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.navigationBars())
    }
}

/** Uses dark status-bar icons for light app surfaces and light icons for dark surfaces. */
fun Activity.setLightStatusBarAppearance(lightBackground: Boolean) {
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightBackground
}
