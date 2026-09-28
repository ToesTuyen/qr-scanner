package com.ivistatect.qrscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivistatect.qrscanner.ui.language.AppLanguage
import com.ivistatect.qrscanner.ui.common.hideSystemNavigationBar
import com.ivistatect.qrscanner.ui.theme.QrScannerTheme
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.data.GithubAppUpdateRepository
import com.ivistatect.qrscanner.data.GithubRelease
import com.ivistatect.qrscanner.data.GithubUpdateCheck
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Launcher = branded splash before the standalone main activity.
 */
@AndroidEntryPoint
class StartActivity : FragmentActivity() {

    @Inject lateinit var updateRepo: GithubAppUpdateRepository

    private var startupUpdateState by mutableStateOf<StartupUpdateState>(StartupUpdateState.Checking)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemNavigationBar()
        Logger.d("Enter StartActivity", "route gate")
        setContent {
            QrScannerTheme {
                when (val state = startupUpdateState) {
                    StartupUpdateState.Checking -> SplashContent(checkingForUpdate = true)
                    is StartupUpdateState.Required -> ForceUpdateContent(
                        release = state.release,
                        downloading = false,
                        onUpdate = { downloadUpdate(state.release) },
                    )
                    is StartupUpdateState.InstallPermissionRequired -> ForceUpdateContent(
                        release = state.release,
                        downloading = false,
                        permissionRequired = true,
                        onUpdate = { openInstallPermissionSettings(state.release) },
                    )
                    is StartupUpdateState.DownloadFailed -> ForceUpdateContent(
                        release = state.release,
                        errorMessage = state.message,
                        onUpdate = { downloadUpdate(state.release) },
                    )
                    is StartupUpdateState.Downloading -> ForceUpdateContent(
                        release = state.release,
                        downloading = true,
                        onUpdate = {},
                    )
                    is StartupUpdateState.CheckFailed -> UpdateCheckFailedContent(
                        message = state.message,
                        onRetry = ::checkUpdateThenRoute,
                    )
                }
            }
        }
        checkUpdateThenRoute()
    }

    private fun checkUpdateThenRoute() {
        startupUpdateState = StartupUpdateState.Checking
        lifecycleScope.launch {
            when (val result = updateRepo.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is GithubUpdateCheck.Available -> {
                    Logger.d("Startup update required", "version=${result.release.versionName}")
                    startupUpdateState = StartupUpdateState.Required(result.release)
                }
                is GithubUpdateCheck.UpToDate,
                GithubUpdateCheck.NoRelease,
                -> openMainApp()
                is GithubUpdateCheck.Error -> {
                    Logger.d("Startup update check failed", result.message)
                    startupUpdateState = StartupUpdateState.CheckFailed(result.message)
                }
            }
        }
    }

    private fun downloadUpdate(release: GithubRelease) {
        if (!updateRepo.canRequestPackageInstalls()) {
            startupUpdateState = StartupUpdateState.InstallPermissionRequired(release)
            return
        }
        startupUpdateState = StartupUpdateState.Downloading(release)
        lifecycleScope.launch {
            when (val result = updateRepo.downloadAndOpenInstaller(release)) {
                GithubAppUpdateRepository.DownloadResult.InstallerOpened -> {
                    // The installer overlays this activity. If it is cancelled, the required
                    // update page is still shown and the old version remains blocked.
                    startupUpdateState = StartupUpdateState.Required(release)
                }
                is GithubAppUpdateRepository.DownloadResult.Error -> {
                    startupUpdateState = StartupUpdateState.DownloadFailed(release, result.message)
                }
            }
        }
    }

    private fun openInstallPermissionSettings(release: GithubRelease) {
        updateRepo.openInstallPermissionSettings()
        startupUpdateState = StartupUpdateState.Required(release)
    }

    private fun openMainApp() {
        Logger.d("StartActivity route → MainActivity", "update=clear")
        startActivity(Intent(this@StartActivity, MainActivity::class.java))
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigationBar()
    }
}

@Composable
private fun SplashContent(checkingForUpdate: Boolean = false) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.ic_qr_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (checkingForUpdate) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            Text(
                stringResource(R.string.startup_update_checking),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ForceUpdateContent(
    release: GithubRelease,
    downloading: Boolean = false,
    permissionRequired: Boolean = false,
    errorMessage: String? = null,
    onUpdate: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.ic_qr_logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(
            stringResource(R.string.startup_update_required_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            stringResource(R.string.startup_update_required_message, release.versionName),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (permissionRequired) {
            Text(
                stringResource(R.string.settings_update_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        errorMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (downloading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 28.dp))
            Text(
                stringResource(R.string.settings_update_downloading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Button(onClick = onUpdate, modifier = Modifier.padding(top = 28.dp)) {
                Text(
                    stringResource(
                        if (permissionRequired) R.string.settings_update_open_settings
                        else R.string.settings_update_download,
                    ),
                )
            }
        }
    }
}

@Composable
private fun UpdateCheckFailedContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.ic_qr_logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(
            stringResource(R.string.startup_update_check_failed_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 28.dp)) {
            Text(stringResource(R.string.startup_update_retry))
        }
    }
}

private sealed interface StartupUpdateState {
    data object Checking : StartupUpdateState
    data class Required(val release: GithubRelease) : StartupUpdateState
    data class InstallPermissionRequired(val release: GithubRelease) : StartupUpdateState
    data class Downloading(val release: GithubRelease) : StartupUpdateState
    data class DownloadFailed(val release: GithubRelease, val message: String) : StartupUpdateState
    data class CheckFailed(val message: String) : StartupUpdateState
}
