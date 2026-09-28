package com.ivistatect.qrscanner.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.data.SettingsRepository
import com.ivistatect.qrscanner.ui.common.openUrl
import com.ivistatect.qrscanner.ui.common.openStoreListing
import com.ivistatect.qrscanner.ui.common.shareText
import com.ivistatect.qrscanner.ui.theme.applyThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenLanguage: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Logger.d("Enter Settings") }
    val s by vm.settings.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showSearchEngine by remember { mutableStateOf(false) }
    var showCameraFacing by remember { mutableStateOf(false) }
    var showRateSheet by remember { mutableStateOf(false) }
    val searchEngines = listOf(stringResource(R.string.settings_value_default), "Google", "Bing", "Yahoo", "Yandex", "DuckDuckGo", "Qwant")
    val searchEngine = s.searchEngine.coerceIn(searchEngines.indices)
    val cameraOptions = listOf(
        stringResource(R.string.settings_value_rear_camera),
        stringResource(R.string.settings_value_front_camera),
    )
    val cameraFacing = s.cameraFacing.coerceIn(SettingsRepository.CAMERA_REAR, SettingsRepository.CAMERA_FRONT)

    val themeValue = when (s.themeMode) {
        SettingsRepository.THEME_LIGHT -> stringResource(R.string.theme_light)
        SettingsRepository.THEME_DARK -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        SectionLabel(stringResource(R.string.settings_overview))
        NavRow(R.drawable.ic_set_language, stringResource(R.string.settings_app_language)) {
            Logger.d("Click App Language @ Settings"); onOpenLanguage()
        }
        NavRow(R.drawable.ic_set_theme, stringResource(R.string.settings_theme_mode), value = themeValue) {
            Logger.d("Click Theme Mode @ Settings"); showThemeSheet = true
        }

        SectionLabel(stringResource(R.string.settings_application))
        NavRow(R.drawable.ic_set_search_engine, stringResource(R.string.settings_search_engine), value = searchEngines[searchEngine]) {
            Logger.d("Click Search Engine @ Settings"); showSearchEngine = true
        }
        NavRow(R.drawable.ic_set_camera, stringResource(R.string.settings_camera), value = cameraOptions[cameraFacing]) {
            Logger.d("Click Camera @ Settings"); showCameraFacing = true
        }
        ToggleRow(R.drawable.ic_set_batch, stringResource(R.string.settings_batch), s.batchScanning) { vm.toggleLogged(SettingsRepository.Key.BATCH, it, "Batch") }
        ToggleRow(R.drawable.ic_set_vibration, stringResource(R.string.settings_vibration), s.vibration) { vm.toggleLogged(SettingsRepository.Key.VIBRATION, it, "Vibration") }
        ToggleRow(R.drawable.ic_set_sound, stringResource(R.string.settings_sound), s.sound) { vm.toggleLogged(SettingsRepository.Key.SOUND, it, "Sound") }
        ToggleRow(R.drawable.ic_set_auto_copy, stringResource(R.string.settings_auto_copy), s.autoCopy) { vm.toggleLogged(SettingsRepository.Key.AUTO_COPY, it, "AutoCopy") }
        ToggleRow(R.drawable.ic_set_web_search, stringResource(R.string.settings_web_search), s.webSearch) { vm.toggleLogged(SettingsRepository.Key.WEB_SEARCH, it, "WebSearch") }
        ToggleRow(R.drawable.ic_set_save_history, stringResource(R.string.settings_save_history), s.saveHistory) { vm.toggleLogged(SettingsRepository.Key.SAVE_HISTORY, it, "SaveHistory") }
        ToggleRow(R.drawable.ic_set_product, stringResource(R.string.settings_product_details), s.showProduct) { vm.toggleLogged(SettingsRepository.Key.SHOW_PRODUCT, it, "ShowProduct") }

        SectionLabel(stringResource(R.string.settings_about))
        NavRow(R.drawable.ic_set_rate, stringResource(R.string.settings_rate)) {
            Logger.d("Click Rate Us @ Settings"); showRateSheet = true
        }
        NavRow(R.drawable.ic_set_share, stringResource(R.string.settings_share_app)) {
            Logger.d("Click Share App @ Settings")
            context.shareText(context.getString(R.string.share_app_text))
        }
        NavRow(R.drawable.ic_set_privacy, stringResource(R.string.settings_privacy)) {
            Logger.d("Click Privacy @ Settings")
            context.openUrl(context.getString(R.string.url_privacy))
        }
        NavRow(R.drawable.ic_set_terms, stringResource(R.string.settings_terms)) {
            Logger.d("Click Terms @ Settings")
            context.openUrl(context.getString(R.string.url_terms))
        }
        Text("", Modifier.padding(bottom = 8.dp))
    }

    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                ThemeOption(stringResource(R.string.theme_system), SettingsRepository.THEME_SYSTEM, vm) { showThemeSheet = false }
                ThemeOption(stringResource(R.string.theme_light), SettingsRepository.THEME_LIGHT, vm) { showThemeSheet = false }
                ThemeOption(stringResource(R.string.theme_dark), SettingsRepository.THEME_DARK, vm) { showThemeSheet = false }
            }
        }
    }

    if (showSearchEngine) {
        ChoiceDialog(
            title = stringResource(R.string.settings_search_engine),
            options = searchEngines,
            selected = searchEngine,
            onSelect = { vm.setSearchEngine(it); Logger.d("Select Search Engine @ Settings", "index=$it"); showSearchEngine = false },
            onDismiss = { showSearchEngine = false },
        )
    }
    if (showCameraFacing) {
        ChoiceDialog(
            title = stringResource(R.string.settings_camera),
            options = cameraOptions,
            selected = cameraFacing,
            onSelect = { vm.setCameraFacing(it); Logger.d("Select Camera @ Settings", "facing=$it"); showCameraFacing = false },
            onDismiss = { showCameraFacing = false },
        )
    }
    if (showRateSheet) {
        ModalBottomSheet(onDismissRequest = { showRateSheet = false }) {
            RateSheetContent {
                Logger.d("Click Rate on Google Play @ Rate sheet")
                context.openStoreListing()
                showRateSheet = false
            }
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, opt ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(i) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = i == selected, onClick = { onSelect(i) })
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RateSheetContent(onRate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.rate_thanks), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Button(onClick = onRate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.rate_cta))
        }
    }
}

private fun SettingsViewModel.toggleLogged(key: SettingsRepository.Key, value: Boolean, name: String) {
    Logger.d("Toggle $name @ Settings", "value=$value")
    toggle(key, value)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RowLeadingIcon(@DrawableRes iconRes: Int) {
    Icon(
        painterResource(iconRes),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(26.dp),
    )
}

@Composable
private fun NavRow(@DrawableRes iconRes: Int, title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLeadingIcon(iconRes)
        Text(title, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(R.drawable.ic_set_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).padding(start = 8.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun ToggleRow(@DrawableRes iconRes: Int, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLeadingIcon(iconRes)
        Text(title, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun ThemeOption(label: String, mode: Int, vm: SettingsViewModel, onDone: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                Logger.d("Click Theme option @ Settings", "mode=$mode")
                vm.setThemeMode(mode)
                applyThemeMode(mode)
                onDone()
            }
            .padding(16.dp),
    )
}
