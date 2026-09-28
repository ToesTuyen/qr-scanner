package com.ivistatect.qrscanner.ui.language

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Persists and applies the app locale without an SDK or shared application module. */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, "").orEmpty()

    fun setTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TAG, tag).apply()
    }

    fun wrapContext(context: Context): Context {
        val tag = currentTag(context)
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration)
    }
}
