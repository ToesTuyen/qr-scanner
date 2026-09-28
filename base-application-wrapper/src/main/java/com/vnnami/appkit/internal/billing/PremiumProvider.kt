package com.vnnami.appkit.internal.billing

import android.content.Context

/**
 * Single source of truth for user's premium state.
 *
 * Priority:
 *   1. Google Billing (live subscription check)
 *   2. SharedPrefs cache (fallback when billing hasn't initialised yet)
 *
 * Usage:
 *   if (PremiumProvider.isPremium(context)) { ... }
 *   PremiumProvider.refresh(context) { newState -> ... }
 */
internal object PremiumProvider {

    fun isPremium(context: Context): Boolean = IAPUtils.isPremiumOrCached(context)

    /** Async refresh from Google Billing. Call after a successful purchase. */
    fun refresh(context: Context, onResult: (isPremium: Boolean) -> Unit = {}) {
        IAPUtils.syncPremiumFromGoogleAsync(context, onResult)
    }

    /** Backend-driven write path. Use after a verified /user or /subs/android response
     *  to make the SharedPrefs cache + every `isPremium()` reader observe the new
     *  state immediately, without waiting for Google Billing's async sync. */
    fun set(context: Context, isPremium: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREMIUM, isPremium).apply()
    }

    /** Last known entitlement, used while Google Billing has not answered yet. */
    internal fun cached(context: Context): Boolean = prefs(context).getBoolean(KEY_PREMIUM, false)

    // Pref file and key name are unchanged so an existing install keeps its entitlement.
    private const val CACHE_NAME = "RECOVERY_FILE_X"
    private const val KEY_PREMIUM = "key_premium"

    private fun prefs(context: Context) =
        context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
}
