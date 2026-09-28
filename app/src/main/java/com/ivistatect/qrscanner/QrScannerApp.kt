package com.ivistatect.qrscanner

import android.app.Application
import android.content.Context
import android.content.res.Resources
import com.vnnami.appkit.api.AppKit
import com.vnnami.appkit.api.AppKitHost
import com.vnnami.appkit.api.BaseLibApplication
import com.ivistatect.qrscanner.appkit.AppHost
import dagger.hilt.android.HiltAndroidApp

/**
 * Application root. Supplies the wrapper with [AppHost] and nothing else — all wrapper access goes
 * through `com.vnnami.appkit.api`.
 *
 * With [AppHost.bootstrapMode] = LOCAL_ONLY, `super.onCreate()` skips the legacy AAR bootstrap
 * (Firebase / ads SDK / AppsFlyer), so this is a fully offline POC.
 */
@HiltAndroidApp
class QrScannerApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }

}
