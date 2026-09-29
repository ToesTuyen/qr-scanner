package com.ivistatect.qrscanner.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.ui.common.findActivity
import com.ivistatect.qrscanner.ui.common.setLightStatusBarAppearance
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
    const val TABLE_ID_SCANNER = "table_id_scanner"
}

private data class TabItem(val route: String, val labelRes: Int, @DrawableRes val icon: Int)

private val TABS = listOf(
    TabItem(Routes.HISTORY, R.string.nav_history, R.drawable.ic_fig_nav_history),
    TabItem(Routes.SCANNER, R.string.nav_scanner, R.drawable.ic_fig_nav_scanner),
    TabItem(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_fig_nav_settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context.findActivity() as ComponentActivity
    val mainVm: MainViewModel = hiltViewModel(activity)

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.SCANNER, Routes.HISTORY, Routes.SETTINGS)

    LaunchedEffect(currentRoute) {
        currentRoute?.let { Logger.d("Enter screen", "route=$it") }
    }

    // Status bar remains visible. Match its icon colour to the surface behind it.
    val lightStatusBar = currentRoute != Routes.SCANNER && MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect { activity.setLightStatusBarAppearance(lightStatusBar) }

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
    BackHandler(enabled = currentRoute == Routes.TABLE_ID_SCANNER) {
        Logger.d("Back to Settings @ Table ID scanner")
        navController.popBackStack()
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.SCANNER,
            modifier = Modifier.fillMaxSize(),
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
                    onScanTableId = { navController.navigate(Routes.TABLE_ID_SCANNER) },
                )
            }
            composable(Routes.TABLE_ID_SCANNER) {
                ScannerScreen(
                    mainVm = mainVm,
                    onResult = {},
                    onOpenBatch = {},
                    onTableIdScanned = { tableId ->
                        Logger.d("Table ID captured", "value=$tableId")
                        mainVm.setTableId(tableId)
                        navController.popBackStack()
                    },
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

        if (showBottomBar) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(68.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        IconButton(
                            onClick = {
                                Logger.d("Click Tab @ ${tab.route}")
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.SCANNER) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.size(48.dp).background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(16.dp),
                            ),
                        ) {
                            Icon(
                                painterResource(tab.icon),
                                contentDescription = stringResource(tab.labelRes),
                                modifier = Modifier.size(22.dp),
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExitSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                Logger.d("Dismiss Exit sheet @ Scanner")
                showExitSheet = false
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    Modifier.padding(top = 12.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            RoundedCornerShape(4.dp),
                        ),
                )
            },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.exit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.exit_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            Logger.d("Click Cancel @ Exit sheet")
                            showExitSheet = false
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = {
                            Logger.d("Click Exit @ Exit sheet")
                            activity.finish()
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text(stringResource(R.string.exit_confirm)) }
                }
            }
        }
    }
}
