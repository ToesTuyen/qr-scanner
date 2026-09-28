package com.vnnami.appkit.internal.language

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Singleton quản lý locale của ứng dụng.
 * Giữ locale hiện tại dưới dạng StateFlow để các composable có thể observe
 * và re-render mà KHÔNG cần recreate Activity.
 */
internal object LocaleManager {

    private val _currentLocale = MutableStateFlow(Locale.getDefault())
    val currentLocale: StateFlow<Locale> = _currentLocale.asStateFlow()

    /** Áp dụng locale tag (ví dụ: "vi", "en", "fr") và notify observer */
    fun applyLocale(tag: String) {
        val locale = tagToLocale(tag)
        Locale.setDefault(locale)
        _currentLocale.value = locale
    }

    /** Wrap context với locale hiện tại — gọi trong attachBaseContext */
    fun wrapContext(context: Context, tag: String): Context {
        val locale = tagToLocale(tag)
        Locale.setDefault(locale)
        _currentLocale.value = locale

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    private fun tagToLocale(tag: String): Locale {
        return when {
            tag.contains("-") -> {
                val parts = tag.replace("-r", "-").split("-")
                if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
            }
            tag.contains("_") -> {
                val parts = tag.split("_")
                if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
            }
            else -> Locale(tag)
        }
    }
}
