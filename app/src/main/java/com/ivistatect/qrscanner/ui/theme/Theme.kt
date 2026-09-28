package com.ivistatect.qrscanner.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ivistatect.qrscanner.data.SettingsRepository

private val BrandBlue = Color(0xFF2563EB)
private val BrandBlueDark = Color(0xFF1D4ED8)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandBlueDark,
    // Cool near-white app background (reference One-UI tint) with WHITE surfaces so cards/tiles read
    // as distinct white panels on the background — matches the reference Create/Settings/History.
    background = Color(0xFFEFF3F9),
    onBackground = Color(0xFF1B1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1E),
    surfaceVariant = Color(0xFFE7ECF3),
    onSurfaceVariant = Color(0xFF5B626D),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF93B4FF),
)

@Composable
fun QrScannerTheme(
    content: @Composable () -> Unit,
) {
    val mode by ThemeState.mode.collectAsState()
    val darkTheme = when (mode) {
        SettingsRepository.THEME_LIGHT -> false
        SettingsRepository.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
    ) {
        // Every setContent root (splash, ProX, share-in, and the Scaffold host) draws on a themed
        // Surface so text/icons inherit the correct on-background content colour in light AND dark.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

/** Reference parity: the Theme sheet applies AppCompat day/night. Called on selection + app start. */
fun applyThemeMode(mode: Int) {
    val night = when (mode) {
        SettingsRepository.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        SettingsRepository.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(night)
}
