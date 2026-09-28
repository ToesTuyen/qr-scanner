package com.ivistatect.qrscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivistatect.qrscanner.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<SettingsRepository.Settings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.Settings())

    fun toggle(key: SettingsRepository.Key, value: Boolean) =
        viewModelScope.launch { repo.setBoolean(key, value) }

    fun setThemeMode(mode: Int) {
        // Update the process-wide holder immediately for instant repaint, then persist.
        com.ivistatect.qrscanner.ui.theme.ThemeState.mode.value = mode
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun setCameraFacing(facing: Int) = viewModelScope.launch { repo.setCameraFacing(facing) }

    fun setSearchEngine(index: Int) = viewModelScope.launch { repo.setSearchEngine(index) }

    fun setImageFormat(index: Int) = viewModelScope.launch { repo.setImageFormat(index) }
}
