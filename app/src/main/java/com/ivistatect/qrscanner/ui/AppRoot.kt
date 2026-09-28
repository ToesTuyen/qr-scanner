package com.ivistatect.qrscanner.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.ui.common.findActivity
import com.ivistatect.qrscanner.ui.history.HistoryScreen
import com.ivistatect.qrscanner.ui.language.LanguageScreen
import com.ivistatect.qrscanner.ui.result.BatchResultScreen
import com.ivistatect.qrscanner.ui.result.ScanResultScreen
import com.ivistatect.qrscanner.ui.scanner.ScannerScreen
import com.ivistatect.qrscanner.ui.settings.SettingsScreen
import com.ivistatect.qrscanner.util.Logger

object Routes {
    const val SCANNER = "scanner"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RESULT = "result"
    const val BATCH = "batch"
    const val LANGUAGE = "language"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context.findActivity() as ComponentActivity
    val mainVm: MainViewModel = hiltViewModel(activity)

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    var showExitSheet by remember { mutableStateOf(false) }

    // popSafe() back to the Scanner home.
    val goHome: () -> Unit = {
        if (!navController.popBackStack(Routes.SCANNER, inclusive = false)) {
            navController.navigate(Routes.SCANNER) { launchSingleTop = true }
        }
    }

    // Back on the Scanner home shows the exit sheet (n_exit_sheet). Sheet stays ad-free (dialog).
    BackHandler(enabled = currentRoute == Routes.SCANNER) {
        Logger.d("Exit sheet shown @ Scanner")
        showExitSheet = true
    }
    BackHandler(enabled = currentRoute == Routes.HISTORY) {
        Logger.d("Back to Home @ History")
        goHome()
    }
    // Settings is an ad-free surface — its BackHandler carries NO interstitial.
    BackHandler(enabled = currentRoute == Routes.SETTINGS) {
        Logger.d("Back to Home @ Settings (ad-free)")
        goHome()
    }

    Scaffold { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SCANNER,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.SCANNER) {
                ScannerScreen(
                    mainVm = mainVm,
                    onResult = { navController.navigate(Routes.RESULT) },
                    onOpenBatch = { navController.navigate(Routes.BATCH) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenItem = { item -> mainVm.openFromHistory(item); navController.navigate(Routes.RESULT) },
                    onGoScan = {
                        navController.navigate(Routes.SCANNER) {
                            popUpTo(Routes.SCANNER) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenLanguage = { navController.navigate(Routes.LANGUAGE) },
                )
            }
            composable(Routes.RESULT) {
                ScanResultScreen(mainVm = mainVm, onBack = { navController.popBackStack() })
            }
            composable(Routes.BATCH) {
                BatchResultScreen(
                    mainVm = mainVm,
                    onOpenItem = { code -> mainVm.openBatchItem(code); navController.navigate(Routes.RESULT) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LANGUAGE) {
                LanguageScreen(
                    onBack = { navController.popBackStack() },
                    onApplied = { tag ->
                        Logger.d("Language applied", "tag=$tag")
                        navController.popBackStack()
                        activity.recreate()
                    },
                )
            }
        }
    }

    if (showExitSheet) {
        ModalBottomSheet(onDismissRequest = { showExitSheet = false }) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.exit_title))
                Button(
                    onClick = {
                        Logger.d("Click Exit @ Exit sheet")
                        activity.finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.exit_confirm)) }
                OutlinedButton(
                    onClick = { showExitSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
