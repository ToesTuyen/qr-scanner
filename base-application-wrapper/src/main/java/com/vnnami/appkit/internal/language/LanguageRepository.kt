package com.vnnami.appkit.internal.language

import android.content.Context
import com.vnnami.appkit.internal.language.Language
import com.vnnami.appkit.internal.language.LocaleManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Source of the language list and of the tag the user last applied. */
internal interface LanguageRepository {
    fun getSupportedLanguages(): List<Language>
    fun getCurrentLanguageTag(): String
    fun setLanguage(tag: String)
}

/**
 * One row of the picker. [flagIconName] is a drawable name rather than a resource id because the
 * flags are resolved at runtime with `getIdentifier`, which is why they look unused to lint.
 */
internal data class Language(
    val id: String,
    val name: String,
    val localName: String,
    val countryCode: String,
    val flagIconName: String,
    val extraKeywords: List<String> = emptyList()
)

/**
 * The 30 supported languages, hard-coded on purpose: the list must match the `values-*` folders
 * that actually ship, and a server could offer a language with no translations behind it.
 *
 * First run has no saved tag, so it falls back to the closest match for the device locale.
 */
internal class LanguageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LanguageRepository {

    private val prefs = context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)

    override fun getSupportedLanguages(): List<Language> {
        return listOf(
            Language("en-rUS", "English US", "English US", "US", "ic_us"),
            Language("en-rGB", "English UK", "English UK", "GB", "ic_gb"),
            Language("en-rAU", "English Australia", "English Australia", "AU", "ic_au"),
            Language("en-rIN", "English India", "English India", "IN", "ic_in"),
            Language("pt", "Portuguese", "Português", "PT", "ic_pt"),
            Language("pt-rBR", "Portuguese Brazil", "Português (Brasil)", "BR", "ic_br"),
            Language("pt-rPT", "Portuguese Portugal", "Português (Portugal)", "PT", "ic_pt"),
            Language("es", "Spanish", "Español", "ES", "ic_es"),
            Language("es-rES", "Spanish Spain", "Español (España)", "ES", "ic_es"),
            Language("es-rUS", "Spanish US", "Español (Estados Unidos)", "US", "ic_us"),
            Language("es-rMX", "Spanish Mexico", "Español (México)", "MX", "ic_mx"),
            Language("de", "German", "Deutsch", "DE", "ic_de"),
            Language("fr", "French", "Français", "FR", "ic_fr"),
            Language("fr-rCA", "French Canada", "Français (Canada)", "CA", "ic_ca"),
            Language("id", "Indonesian", "Bahasa Indonesia", "ID", "ic_id"),
            Language("ar", "Arabic", "العربية", "SA", "ic_sa"),
            Language("it", "Italian", "Italiano", "IT", "ic_it"),
            Language("tr", "Turkish", "Türkçe", "TR", "ic_tr"),
            Language("uk", "Ukrainian", "Українська", "UA", "ic_ua"),
            Language("nl", "Dutch", "Nederlands", "NL", "ic_nl"),
            Language("ja", "Japanese", "日本語", "JP", "ic_jp"),
            Language("fa", "Persian", "فارسی", "IR", "ic_ir"),
            Language("zh", "Chinese Simplified", "简体中文", "CN", "ic_cn"),
            Language("zh-rCN", "Chinese China", "简体中文（中国）", "CN", "ic_cn"),
            Language("zh-rTW", "Chinese Traditional", "繁體中文（台灣）", "TW", "ic_tw"),
            Language("ko", "Korean", "한국어", "KR", "ic_kr"),
            Language("vi", "Vietnamese", "Tiếng Việt", "VN", "ic_vn"),
            Language("ru", "Russian", "Русский", "RU", "ic_ru"),
            Language("hi", "Hindi", "हिन्दी", "IN", "ic_in"),
            Language("iw", "Hebrew", "עברית", "IL", "ic_il")
        )
    }

    override fun getCurrentLanguageTag(): String {
        val saved = prefs.getString("language_tag", "")
        if (!saved.isNullOrEmpty()) return saved

        val systemLang = java.util.Locale.getDefault().language
        val supportedTags = getSupportedLanguages().map { it.id }
        return supportedTags.find { it.startsWith(systemLang) } ?: "en-rUS"
    }

    override fun setLanguage(tag: String) {
        prefs.edit().putString("language_tag", tag).apply()
        // LocaleManager consumes BCP-47 tags while Android resource folders use the -r qualifier.
        val localeTag = tag.replace("-r", "-")
        LocaleManager.applyLocale(localeTag)
    }
}

@Module
@InstallIn(SingletonComponent::class)
/** Binds the repository. The only Hilt module the wrapper owns. */
internal abstract class LanguageModule {

    @Binds
    @Singleton
    abstract fun bindLanguageRepository(
        impl: LanguageRepositoryImpl
    ): LanguageRepository
}
