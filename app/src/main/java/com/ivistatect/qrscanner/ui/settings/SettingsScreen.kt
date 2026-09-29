package com.ivistatect.qrscanner.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ivistatect.qrscanner.BuildConfig
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.data.ScanDeviceId
import com.ivistatect.qrscanner.data.SettingsRepository
import com.ivistatect.qrscanner.ui.common.openUrl
import com.ivistatect.qrscanner.ui.theme.applyThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenLanguage: () -> Unit,
    onScanTableId: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val deviceId = remember(context) { ScanDeviceId.from(context) }
    LaunchedEffect(Unit) { Logger.d("Enter Settings") }
    val s by vm.settings.collectAsState()
    val updateState by vm.updateState.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showSearchEngine by remember { mutableStateOf(false) }
    var showCameraFacing by remember { mutableStateOf(false) }
    var showTableIdDialog by remember { mutableStateOf(false) }
    var tableIdInput by remember { mutableStateOf("") }
    val searchEngines = listOf(stringResource(R.string.settings_value_default), "Google", "Bing", "Yahoo", "Yandex", "DuckDuckGo", "Qwant")
    val searchEngine = s.searchEngine.coerceIn(searchEngines.indices)
    val cameraOptions = listOf(
        stringResource(R.string.settings_value_rear_camera),
        stringResource(R.string.settings_value_front_camera),
    )
    val cameraFacing = s.cameraFacing.coerceIn(SettingsRepository.CAMERA_REAR, SettingsRepository.CAMERA_FRONT)
    val tableIdValue = when {
        s.tableId.isBlank() -> stringResource(R.string.settings_table_id_not_set)
        s.tableId.length <= 18 -> s.tableId
        else -> "${s.tableId.take(8)}…${s.tableId.takeLast(4)}"
    }

    val themeValue = when (s.themeMode) {
        SettingsRepository.THEME_LIGHT -> stringResource(R.string.theme_light)
        SettingsRepository.THEME_DARK -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        NavRow(R.drawable.ic_barcode, stringResource(R.string.settings_table_id), value = tableIdValue) {
            Logger.d("Click Table ID @ Settings")
            tableIdInput = s.tableId
            showTableIdDialog = true
        }
        InfoRow(stringResource(R.string.settings_device_id), deviceId)

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
        ToggleRow(R.drawable.ic_connect, stringResource(R.string.settings_auto_submit_server), s.autoSubmitServer) { vm.toggleLogged(SettingsRepository.Key.AUTO_SUBMIT_SERVER, it, "Auto server submit") }
        ToggleRow(R.drawable.ic_set_vibration, stringResource(R.string.settings_vibration), s.vibration) { vm.toggleLogged(SettingsRepository.Key.VIBRATION, it, "Vibration") }
        ToggleRow(R.drawable.ic_set_sound, stringResource(R.string.settings_sound), s.sound) { vm.toggleLogged(SettingsRepository.Key.SOUND, it, "Sound") }

        SectionLabel(stringResource(R.string.settings_about))
        val updateValue = when (val state = updateState) {
            AppUpdateUiState.Idle -> stringResource(R.string.settings_update_current_version, BuildConfig.VERSION_NAME)
            AppUpdateUiState.Checking -> stringResource(R.string.settings_update_checking)
            AppUpdateUiState.Downloading -> stringResource(R.string.settings_update_downloading)
            AppUpdateUiState.InstallPromptOpened -> stringResource(R.string.settings_update_install_prompt_opened)
            is AppUpdateUiState.Available -> state.release.tagName
            is AppUpdateUiState.UpToDate -> stringResource(R.string.settings_update_latest)
            AppUpdateUiState.NoRelease -> stringResource(R.string.settings_update_no_release)
            is AppUpdateUiState.Error -> stringResource(R.string.settings_update_failed)
            is AppUpdateUiState.InstallPermissionRequired -> state.release.tagName
        }
        NavRow(R.drawable.ic_set_update, stringResource(R.string.settings_check_update), value = updateValue) {
            Logger.d("Click Check update @ Settings", "version=${BuildConfig.VERSION_NAME}")
            vm.checkForUpdate()
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
        ModalBottomSheet(onDismissRequest = {
            Logger.d("Dismiss Theme sheet @ Settings")
            showThemeSheet = false
        }) {
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
            onSelect = {
                Logger.d("Select Search Engine @ Settings", "value=${searchEngines[it]}")
                vm.setSearchEngine(it)
                showSearchEngine = false
            },
            onDismiss = {
                Logger.d("Dismiss Search Engine dialog @ Settings")
                showSearchEngine = false
            },
        )
    }
    if (showCameraFacing) {
        ChoiceDialog(
            title = stringResource(R.string.settings_camera),
            options = cameraOptions,
            selected = cameraFacing,
            onSelect = {
                Logger.d("Select Camera @ Settings", "value=${cameraOptions[it]}")
                vm.setCameraFacing(it)
                showCameraFacing = false
            },
            onDismiss = {
                Logger.d("Dismiss Camera dialog @ Settings")
                showCameraFacing = false
            },
        )
    }

    if (showTableIdDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                Logger.d("Dismiss Table ID dialog @ Settings")
                showTableIdDialog = false
            },
            title = { Text(stringResource(R.string.settings_table_id)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = tableIdInput,
                    onValueChange = { tableIdInput = it },
                    label = { Text(stringResource(R.string.settings_table_id_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val tableId = tableIdInput.trim()
                        if (tableId.isNotBlank()) {
                            Logger.d("Save Table ID @ Settings", "value=$tableId")
                            vm.setTableId(tableId)
                            showTableIdDialog = false
                        }
                    },
                    enabled = tableIdInput.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = {
                        Logger.d("Click Scan Table ID @ Settings")
                        showTableIdDialog = false
                        onScanTableId()
                    }) { Text(stringResource(R.string.settings_scan_table_id)) }
                    androidx.compose.material3.TextButton(onClick = { showTableIdDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }

    when (val state = updateState) {
        is AppUpdateUiState.Available -> androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::dismissUpdateMessage,
            title = { Text(stringResource(R.string.settings_update_available_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_update_available_message,
                        state.release.versionName,
                        BuildConfig.VERSION_NAME,
                    ),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    Logger.d("Click Download update @ Settings", "version=${state.release.versionName}")
                    vm.downloadAvailableUpdate()
                }) { Text(stringResource(R.string.settings_update_download)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = vm::dismissUpdateMessage) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        is AppUpdateUiState.InstallPermissionRequired -> androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::dismissUpdateMessage,
            title = { Text(stringResource(R.string.settings_update_permission_title)) },
            text = { Text(stringResource(R.string.settings_update_permission_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    Logger.d("Click Allow update install @ Settings")
                    vm.openInstallPermissionSettings()
                }) { Text(stringResource(R.string.settings_update_open_settings)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = vm::dismissUpdateMessage) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        is AppUpdateUiState.UpToDate -> UpdateInfoDialog(
            title = stringResource(R.string.settings_update_latest_title),
            message = stringResource(R.string.settings_update_latest_message, state.versionName),
            onDismiss = vm::dismissUpdateMessage,
        )
        AppUpdateUiState.NoRelease -> UpdateInfoDialog(
            title = stringResource(R.string.settings_update_no_release_title),
            message = stringResource(R.string.settings_update_no_release_message),
            onDismiss = vm::dismissUpdateMessage,
        )
        is AppUpdateUiState.Error -> UpdateInfoDialog(
            title = stringResource(R.string.settings_update_failed_title),
            message = state.message,
            onDismiss = vm::dismissUpdateMessage,
        )
        AppUpdateUiState.Idle,
        AppUpdateUiState.Checking,
        AppUpdateUiState.Downloading,
        AppUpdateUiState.InstallPromptOpened,
        -> Unit
    }
}

@Composable
private fun UpdateInfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.got_it))
            }
        },
    )
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
            androidx.compose.material3.TextButton(onClick = {
                Logger.d("Click Cancel @ Choice dialog", "title=$title")
                onDismiss()
            }) { Text(stringResource(R.string.cancel)) }
        },
    )
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
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
