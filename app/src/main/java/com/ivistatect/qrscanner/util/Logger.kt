package com.ivistatect.qrscanner.util

import android.util.Log

/** Small app-owned logger; it keeps runtime diagnostics independent of the old shared wrapper. */
object Logger {
    private const val TAG = "IVISTA_TECH"

    fun d(message: String, detail: String? = null) {
        Log.d(TAG, format(message, detail))
    }

    fun e(message: String, detail: String? = null, throwable: Throwable? = null) {
        Log.e(TAG, format(message, detail), throwable)
    }

    private fun format(message: String, detail: String?) =
        if (detail.isNullOrBlank()) message else "$message | $detail"
}
