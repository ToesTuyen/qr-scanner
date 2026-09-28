package com.vnnami.appkit.internal.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnnami.appkit.internal.language.Language
import com.vnnami.appkit.internal.language.LanguageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
/**
 * Holds the picker's state: what is applied, what is selected but not yet applied, and the current
 * search. Applying persists the choice and tells the AAR, which is what makes it survive a restart.
 */
internal class LanguageViewModel @Inject constructor(
    private val languageRepository: LanguageRepository
) : ViewModel() {

    private val allLanguages = languageRepository.getSupportedLanguages()

    private val _uiState = MutableStateFlow(LanguageUiState())
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currentTag = languageRepository.getCurrentLanguageTag()

            val initialLang = allLanguages.find { it.id == currentTag }
                ?: allLanguages.find { it.id == "en-rUS" }

            _uiState.update { state ->
                state.copy(
                    languages = allLanguages,
                    appliedLanguage = initialLang,
                    selectedLanguage = initialLang,
                    filteredLanguages = filterLanguages("", initialLang)
                )
            }
        }
    }

    private fun String.normalizeForSearch(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        return normalized.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "")
            .lowercase(Locale.getDefault())
            .trim()
    }

    private fun filterLanguages(query: String, appliedLang: Language?): List<Language> {
        val normalizedQuery = query.normalizeForSearch()
        val matches = if (normalizedQuery.isEmpty()) {
            allLanguages
        } else {
            val currentDeviceLocale = Locale.getDefault()
            allLanguages.filter { lang ->
                val targetLocale = Locale.forLanguageTag(lang.id.replace("-r", "-"))

                val nativeName = targetLocale.getDisplayLanguage(targetLocale).normalizeForSearch()
                val localizedName =
                    targetLocale.getDisplayLanguage(currentDeviceLocale).normalizeForSearch()
                val englishName =
                    targetLocale.getDisplayLanguage(Locale.ENGLISH).normalizeForSearch()
                val matchExtraKeywords = lang.extraKeywords.any {
                    it.normalizeForSearch().contains(normalizedQuery)
                }

                nativeName.contains(normalizedQuery) ||
                        localizedName.contains(normalizedQuery) ||
                        englishName.contains(normalizedQuery) ||
                        matchExtraKeywords
            }
        }

        return matches
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredLanguages = filterLanguages(query, state.appliedLanguage)
            )
        }
    }

    fun onLanguageSelected(language: Language) {
        _uiState.update { state ->
            state.copy(
                selectedLanguage = language,
                isShowingInitialFocus = false
            )
        }
    }

    fun applyLanguage(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedLanguage ?: return@launch

            _uiState.update { it.copy(isLoading = true) }
            delay(500)

            languageRepository.setLanguage(selected.id)

            _uiState.update {
                it.copy(
                    appliedLanguage = selected,
                    searchQuery = "",
                    filteredLanguages = filterLanguages("", selected),
                    isLoading = false
                )
            }
            onSuccess()
        }
    }
}

/** Everything [LanguageScreen] needs to draw itself. */
internal data class LanguageUiState(
    val languages: List<Language> = emptyList(),
    val filteredLanguages: List<Language> = emptyList(),
    val appliedLanguage: Language? = null,
    val selectedLanguage: Language? = null,
    val searchQuery: String = "",
    val isShowingInitialFocus: Boolean = true,
    val isLoading: Boolean = false
)
