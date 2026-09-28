package com.ivistatect.qrscanner.ui.theme

import com.ivistatect.qrscanner.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide current theme mode ([SettingsRepository.THEME_SYSTEM] / _LIGHT / _DARK), mirrored from
 * the DataStore setting so every `setContent { QrScannerTheme { … } }` root (splash, main host, ProX,
 * share-in) recomposes into the picked theme. Kept in sync by [com.ivistatect.qrscanner.ui.MainViewModel]
 * (observes DataStore, incl. across restarts) and updated immediately on selection by
 * [com.ivistatect.qrscanner.ui.settings.SettingsViewModel].
 */
object ThemeState {
    val mode = MutableStateFlow(SettingsRepository.THEME_SYSTEM)
}
