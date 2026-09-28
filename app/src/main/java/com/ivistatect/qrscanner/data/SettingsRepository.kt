package com.ivistatect.qrscanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "qr_settings")

/** App-owned settings (DataStore). Mirrors the reference Settings toggles + theme mode. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    data class Settings(
        val batchScanning: Boolean = false,
        /** Off by default: scanned batch items wait for the user to submit them together. */
        val autoSubmitServer: Boolean = false,
        val vibration: Boolean = true,
        val sound: Boolean = true,
        val autoCopy: Boolean = false,
        val webSearch: Boolean = false,
        val saveHistory: Boolean = true,
        val showProduct: Boolean = true,
        val themeMode: Int = THEME_SYSTEM,
        val cameraFacing: Int = CAMERA_REAR,
        val searchEngine: Int = 0,
        val imageFormat: Int = 0,
        /** True once the scanner tutorial sheet has been dismissed (shown once, like the reference). */
        val scanGuideSeen: Boolean = false,
    )

    val settings: Flow<Settings> = context.settingsDataStore.data.map { p ->
        Settings(
            batchScanning = p[KEY_BATCH] ?: false,
            autoSubmitServer = p[KEY_AUTO_SUBMIT_SERVER] ?: false,
            vibration = p[KEY_VIBRATION] ?: true,
            sound = p[KEY_SOUND] ?: true,
            autoCopy = p[KEY_AUTO_COPY] ?: false,
            webSearch = p[KEY_WEB_SEARCH] ?: false,
            saveHistory = p[KEY_SAVE_HISTORY] ?: true,
            showProduct = p[KEY_SHOW_PRODUCT] ?: true,
            themeMode = p[KEY_THEME] ?: THEME_SYSTEM,
            cameraFacing = p[KEY_CAMERA_FACING] ?: CAMERA_REAR,
            searchEngine = p[KEY_SEARCH_ENGINE] ?: 0,
            imageFormat = p[KEY_IMAGE_FORMAT] ?: 0,
            scanGuideSeen = p[KEY_SCAN_GUIDE_SEEN] ?: false,
        )
    }

    suspend fun setBoolean(key: Key, value: Boolean) {
        context.settingsDataStore.edit { it[key.pref] = value }
    }

    suspend fun setThemeMode(mode: Int) {
        context.settingsDataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun setCameraFacing(facing: Int) {
        context.settingsDataStore.edit { it[KEY_CAMERA_FACING] = facing }
    }

    suspend fun setSearchEngine(index: Int) {
        context.settingsDataStore.edit { it[KEY_SEARCH_ENGINE] = index }
    }

    suspend fun setImageFormat(index: Int) {
        context.settingsDataStore.edit { it[KEY_IMAGE_FORMAT] = index }
    }

    suspend fun setScanGuideSeen(seen: Boolean) {
        context.settingsDataStore.edit { it[KEY_SCAN_GUIDE_SEEN] = seen }
    }

    enum class Key(val pref: Preferences.Key<Boolean>) {
        BATCH(KEY_BATCH), AUTO_SUBMIT_SERVER(KEY_AUTO_SUBMIT_SERVER), VIBRATION(KEY_VIBRATION), SOUND(KEY_SOUND),
        AUTO_COPY(KEY_AUTO_COPY), WEB_SEARCH(KEY_WEB_SEARCH),
        SAVE_HISTORY(KEY_SAVE_HISTORY), SHOW_PRODUCT(KEY_SHOW_PRODUCT),
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val CAMERA_REAR = 0
        const val CAMERA_FRONT = 1

        private val KEY_BATCH = booleanPreferencesKey("batch_scanning")
        private val KEY_AUTO_SUBMIT_SERVER = booleanPreferencesKey("auto_submit_server")
        private val KEY_VIBRATION = booleanPreferencesKey("vibration")
        private val KEY_SOUND = booleanPreferencesKey("sound")
        private val KEY_AUTO_COPY = booleanPreferencesKey("auto_copy")
        private val KEY_WEB_SEARCH = booleanPreferencesKey("web_search")
        private val KEY_SAVE_HISTORY = booleanPreferencesKey("save_history")
        private val KEY_SHOW_PRODUCT = booleanPreferencesKey("show_product")
        private val KEY_THEME = intPreferencesKey("theme_mode")
        private val KEY_CAMERA_FACING = intPreferencesKey("camera_facing")
        private val KEY_SEARCH_ENGINE = intPreferencesKey("search_engine")
        private val KEY_IMAGE_FORMAT = intPreferencesKey("image_format")
        private val KEY_SCAN_GUIDE_SEEN = booleanPreferencesKey("scan_guide_seen")
    }
}
