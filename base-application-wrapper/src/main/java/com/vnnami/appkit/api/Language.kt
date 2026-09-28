package com.vnnami.appkit.api

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vnnami.appkit.internal.language.LanguageScreen
import com.vnnami.appkit.internal.language.LanguageViewModel

/**
 * In-app language. The picker UI is [LanguageRoute]; this interface is the non-UI half.
 *
 * The wrapper owns the locale store — an app must not keep a second one of its own, or the picker
 * and the app disagree about what language is selected.
 *
 * Reached as `AppKit.language`.
 *
 * ```
 * // The picker, as a destination in the app's NavHost.
 * composable("language") {
 *     LanguageRoute(
 *         onBack = { navController.popBackStack() },
 *         onApplied = { tag -> navController.popBackStack() },   // tag is BCP-47: "vi", "zh-TW"
 *     )
 * }
 *
 * // Make an Activity's resources resolve in the chosen language.
 * override fun attachBaseContext(base: Context) {
 *     super.attachBaseContext(AppKit.language.wrapContext(base, AppKit.language.currentTag(base)))
 * }
 * ```
 * Applying a language also fires [AppKitHost.onLanguageApplied], which is where an app mirrors the
 * change into its own state.
 */
interface LanguageApi {
    /** BCP-47 tag the user last applied, or empty when they never chose one. */
    fun currentTag(context: Context): String

    /** Wrap a base context so resources resolve in the chosen language. Use in attachBaseContext. */
    fun wrapContext(context: Context, tag: String): Context
}

/**
 * The wrapper's language picker as a destination inside the app's own NavHost.
 * [onApplied] receives a BCP-47 tag ("vi", "zh-TW") after the user taps Apply.
 */
@Composable
fun LanguageRoute(onBack: () -> Unit, onApplied: (languageTag: String) -> Unit) {
    val viewModel: LanguageViewModel = hiltViewModel()
    // LanguageScreen still takes a NavController it never navigates with; satisfy the signature
    // locally so no app has to create one.
    val navController = rememberNavController()
    LanguageScreen(
        navController = navController,
        onNavigateBack = onBack,
        onLanguageApplied = {
            val id = viewModel.uiState.value.appliedLanguage?.id
            if (id != null) onApplied(id.replace("-r", "-")) else onBack()
        },
        viewModel = viewModel,
    )
}
