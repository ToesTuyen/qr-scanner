package com.vnnami.appkit.internal.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/**
 * Walks a Context's wrapper chain to the Activity behind it, or null.
 *
 * Compose hands out a Context that is often a ContextWrapper, while the ad and billing SDKs all
 * demand a real Activity, so almost every bridge in this module starts here.
 */
internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** [findActivity] or throw — for callers that cannot do anything useful without one. */
internal fun Context.requireActivity(): Activity = findActivity() 
    ?: throw IllegalStateException("Context $this is not an Activity.")

/** As [findActivity], but for the AAR screens that need a FragmentActivity. */
internal fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/** [findFragmentActivity] or throw. */
internal fun Context.requireFragmentActivity(): FragmentActivity = findFragmentActivity()
    ?: throw IllegalStateException("Context $this is not a FragmentActivity.")
