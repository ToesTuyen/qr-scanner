package com.vnnami.appkit.api

import android.util.Log
import com.vnnami.appkit.BuildConfig

/**
 * The project's logger. One tag, `VN_NAMI`, for every app built on this wrapper — so a single
 * `adb logcat -s VN_NAMI` follows the whole app.
 *
 * ```
 * Logger.d("Enter HomeScreen")
 * Logger.d("Click Template @ HomeScreen", templateId)
 * // D VN_NAMI : (HomeScreen.kt:89) Click Template @ HomeScreen | Params: 42
 * ```
 *
 * **Call it at the site you want to see.** The `(File.kt:line)` prefix comes from walking the
 * stack, so the first non-Logger frame is what gets printed. Wrapping this in a helper of your own
 * — `fun log(m: String) = Logger.d(m)` — makes every line in the app report that helper's file and
 * line instead, and the log becomes useless. Same reason there is no `Logger.d(tag, msg)`: the tag
 * is fixed, the location comes from the stack.
 *
 * House sentences, so logs from different apps read alike: `Enter <Screen>` on entry,
 * `Click <Thing> @ <Screen>` on a tap, and structured values in [params] rather than interpolated
 * into the message.
 *
 * In **release** builds d/i/v are no-ops — `app/proguard-rules.pro` strips them with
 * `-assumenosideeffects`, so no message strings survive in the shipped DEX. w/e still print.
 * In **debug** builds the `(FileName:Line)` prefix is a clickable link in the logcat pane.
 */
object Logger {

    private const val TAG = "VN_NAMI"
    private val DEBUG = BuildConfig.DEBUG

    private fun getCallerInfo(): String {
        val elements = Thread.currentThread().stackTrace
        val loggerClassName = Logger::class.java.name
        for (i in 2 until elements.size) {
            val element = elements[i]
            if (element.className != loggerClassName && !element.className.startsWith("java.lang.Thread")) {
                return "(${element.fileName}:${element.lineNumber}) "
            }
        }
        return ""
    }

    private fun buildMessage(description: String, params: Any?): String {
        val callerInfo = getCallerInfo()
        return if (params != null) "$callerInfo$description | Params: $params" else "$callerInfo$description"
    }

    fun d(description: String, params: Any? = null) {
        Log.d(TAG, buildMessage(description, params))
    }

    fun e(description: String, params: Any? = null, throwable: Throwable? = null) {
        val msg = buildMessage(description, params)
        if (throwable != null) Log.e(TAG, msg, throwable) else Log.e(TAG, msg)
    }

    fun i(description: String, params: Any? = null) {
        Log.i(TAG, buildMessage(description, params))
    }

    fun w(description: String, params: Any? = null, throwable: Throwable? = null) {
        val msg = buildMessage(description, params)
        if (throwable != null) Log.w(TAG, msg, throwable) else Log.w(TAG, msg)
    }

    fun v(description: String, params: Any? = null) {
        Log.v(TAG, buildMessage(description, params))
    }
}
