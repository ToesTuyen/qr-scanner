package com.ivistatect.qrscanner.data

import android.content.Context
import android.provider.Settings
import java.util.Locale

/** Stable warehouse device label derived from Android's app-scoped device identifier. */
object ScanDeviceId {
    private const val PREFIX = "PDA_GUN_BAY"

    fun from(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val suffix = androidId.filter(Char::isLetterOrDigit)
            .takeLast(5)
            .uppercase(Locale.ROOT)
            .ifBlank { "LOCAL" }
        return "$PREFIX$suffix"
    }
}
