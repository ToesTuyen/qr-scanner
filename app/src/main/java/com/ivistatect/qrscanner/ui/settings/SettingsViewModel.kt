package com.ivistatect.qrscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivistatect.qrscanner.BuildConfig
import com.ivistatect.qrscanner.data.GithubAppUpdateRepository
import com.ivistatect.qrscanner.data.GithubUpdateCheck
import com.ivistatect.qrscanner.data.GithubRelease
import com.ivistatect.qrscanner.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class Available(val release: GithubRelease) : AppUpdateUiState
    data class UpToDate(val versionName: String) : AppUpdateUiState
    data object NoRelease : AppUpdateUiState
    data class InstallPermissionRequired(val release: GithubRelease) : AppUpdateUiState
    data object Downloading : AppUpdateUiState
    data object InstallPromptOpened : AppUpdateUiState
    data class Error(val message: String) : AppUpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val updateRepo: GithubAppUpdateRepository,
) : ViewModel() {

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

    private val _updateState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val updateState: StateFlow<AppUpdateUiState> = _updateState

    fun checkForUpdate() {
        if (_updateState.value == AppUpdateUiState.Checking || _updateState.value == AppUpdateUiState.Downloading) return
        _updateState.value = AppUpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value = when (val result = updateRepo.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is GithubUpdateCheck.Available -> AppUpdateUiState.Available(result.release)
                is GithubUpdateCheck.UpToDate -> AppUpdateUiState.UpToDate(result.versionName)
                GithubUpdateCheck.NoRelease -> AppUpdateUiState.NoRelease
                is GithubUpdateCheck.Error -> AppUpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadAvailableUpdate() {
        val release = (_updateState.value as? AppUpdateUiState.Available)?.release ?: return
        if (!updateRepo.canRequestPackageInstalls()) {
            _updateState.value = AppUpdateUiState.InstallPermissionRequired(release)
            return
        }
        _updateState.value = AppUpdateUiState.Downloading
        viewModelScope.launch {
            _updateState.value = when (val result = updateRepo.downloadAndOpenInstaller(release)) {
                is GithubAppUpdateRepository.DownloadResult.InstallerOpened -> AppUpdateUiState.InstallPromptOpened
                is GithubAppUpdateRepository.DownloadResult.Error -> AppUpdateUiState.Error(result.message)
            }
        }
    }

    fun openInstallPermissionSettings() {
        updateRepo.openInstallPermissionSettings()
        dismissUpdateMessage()
    }

    fun dismissUpdateMessage() {
        _updateState.value = AppUpdateUiState.Idle
    }
}
