package com.ivistatect.qrscanner.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.data.HistoryEntity
import com.ivistatect.qrscanner.data.HistoryRepository
import com.ivistatect.qrscanner.data.ScanUploadRepository
import com.ivistatect.qrscanner.data.SettingsRepository
import com.ivistatect.qrscanner.domain.CreateCategory
import com.ivistatect.qrscanner.domain.CreateTile
import com.ivistatect.qrscanner.domain.DecodedCode
import com.ivistatect.qrscanner.domain.ScanValueType
import com.ivistatect.qrscanner.domain.buildContent
import com.ivistatect.qrscanner.scan.QrGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreatedResult(
    val content: String,
    val formatName: String,
    val valueType: ScanValueType,
    val bitmap: Bitmap,
)

/** Progress and outcome of the current server submission run shown in the Batch screen. */
data class ServerSubmissionState(
    val total: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val isSending: Boolean = false,
) {
    val completed: Int get() = succeeded + failed
}

/**
 * Activity-scoped shared state for the single-activity host: the transient nav payloads
 * (current scan result, current created result, batch accumulation) plus decode/create/persist.
 * Screens obtain it with `hiltViewModel(activity)`.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val historyRepo: HistoryRepository,
    private val settingsRepo: SettingsRepository,
    private val scanUploadRepository: ScanUploadRepository,
) : ViewModel() {

    val settings: StateFlow<SettingsRepository.Settings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.Settings())

    // Derived from the RAW DataStore flow (not the defaulted StateFlow above) with initial `true`, so
    // the scan-guide never flashes on a cold launch before DataStore loads: it stays hidden until the
    // store confirms it is unseen (genuine first run), then shows once.
    val scanGuideSeen: StateFlow<Boolean> =
        settingsRepo.settings.map { it.scanGuideSeen }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        // Mirror the persisted theme mode into the process-wide holder so every themed root
        // (splash / host / ProX / share-in) reflects the picked theme, including across restarts.
        viewModelScope.launch { settings.collect { com.ivistatect.qrscanner.ui.theme.ThemeState.mode.value = it.themeMode } }
    }

    var currentScan by mutableStateOf<DecodedCode?>(null); private set
    var currentScanHistoryId by mutableStateOf<Long?>(null); private set
    var currentScanFavorite by mutableStateOf(false); private set
    var currentCreated by mutableStateOf<CreatedResult?>(null); private set

    var batchMode by mutableStateOf(false); private set
    val batchItems = mutableStateListOf<DecodedCode>()
    var serverSubmissionState by mutableStateOf(ServerSubmissionState()); private set

    fun updateBatchMode(enabled: Boolean) {
        batchMode = enabled
        if (!enabled) {
            batchItems.clear()
            serverSubmissionState = ServerSubmissionState()
        }
        Logger.d("Scanner: batch mode ${if (enabled) "on" else "off"}")
    }

    /** Mirrors the persisted Batch Scanning setting when the scanner becomes active. */
    fun syncBatchMode(enabled: Boolean) {
        if (batchMode != enabled) updateBatchMode(enabled)
    }

    /** Changes batch mode from the Scanner control and persists the matching setting. */
    fun setBatchScanning(enabled: Boolean) {
        updateBatchMode(enabled)
        viewModelScope.launch { settingsRepo.setBoolean(SettingsRepository.Key.BATCH, enabled) }
    }

    fun removeBatchItem(code: DecodedCode) {
        if (batchItems.remove(code)) {
            Logger.d("Delete Batch item", "remaining=${batchItems.size}")
        }
    }

    /** Sends the currently accumulated codes once, reporting both accepted and failed requests. */
    fun sendBatchToServer() {
        val codes = batchItems.toList()
        if (codes.isEmpty() || serverSubmissionState.isSending) return

        Logger.d("Send Batch to server", "count=${codes.size}")
        serverSubmissionState = ServerSubmissionState(total = codes.size, isSending = true)
        viewModelScope.launch {
            codes.forEach { code ->
                recordServerSubmission(scanUploadRepository.submitScan(code.rawValue))
            }
            Logger.d(
                "Batch server send finished",
                "success=${serverSubmissionState.succeeded} failed=${serverSubmissionState.failed}",
            )
        }
    }

    /** Persist that the scanner tutorial sheet has been seen (shown once, like the reference). */
    fun markScanGuideSeen() {
        viewModelScope.launch { settingsRepo.setScanGuideSeen(true) }
    }

    /** Camera / gallery / share-in decode entry point. */
    fun onDecoded(code: DecodedCode) {
        if (batchMode) {
            if (batchItems.none { it.rawValue == code.rawValue }) {
                batchItems.add(code)
                if (settings.value.autoSubmitServer) submitScannedBarcode(code.rawValue)
                Logger.d("Scanner: batch add", "count=${batchItems.size}")
            }
        } else {
            currentScan = code
            currentScanFavorite = false
            currentScanHistoryId = null
            viewModelScope.launch {
                if (settingsRepo.settings.first().saveHistory) {
                    currentScanHistoryId = historyRepo.add(code.toEntity(HistoryEntity.ORIGIN_SCANNED))
                }
            }
        }
    }

    /** Sends one scan immediately when the explicit automatic server setting is enabled. */
    private fun submitScannedBarcode(barcode: String) {
        serverSubmissionState = serverSubmissionState.copy(
            total = serverSubmissionState.total + 1,
            isSending = true,
        )
        Logger.d("Auto send scan to server", "queued=${serverSubmissionState.total}")
        viewModelScope.launch {
            recordServerSubmission(scanUploadRepository.submitScan(barcode))
        }
    }

    private fun recordServerSubmission(succeeded: Boolean) {
        val updated = serverSubmissionState.copy(
            succeeded = serverSubmissionState.succeeded + if (succeeded) 1 else 0,
            failed = serverSubmissionState.failed + if (succeeded) 0 else 1,
        )
        serverSubmissionState = updated.copy(isSending = updated.completed < updated.total)
    }

    fun openFromHistory(item: HistoryEntity) {
        currentScan = DecodedCode(
            rawValue = item.rawValue,
            formatName = item.format,
            valueType = runCatching { ScanValueType.valueOf(item.valueType) }.getOrDefault(ScanValueType.TEXT),
            display = item.displayContent,
        )
        currentScanHistoryId = item.id
        currentScanFavorite = item.isFavorite
    }

    fun openBatchItem(code: DecodedCode) {
        currentScan = code
        currentScanFavorite = false
        currentScanHistoryId = null
    }

    fun toggleCurrentScanFavorite() {
        val id = currentScanHistoryId ?: return
        val next = !currentScanFavorite
        currentScanFavorite = next
        viewModelScope.launch { historyRepo.setFavorite(id, next) }
    }

    /** Delete the current scan result from history (result More sheet → Xóa bỏ). */
    fun deleteCurrentScan() {
        val id = currentScanHistoryId ?: return
        viewModelScope.launch { historyRepo.deleteByIds(listOf(id)) }
    }

    /** Create flow: build content → generate bitmap → persist → stash for the result screen. */
    fun createCode(tile: CreateTile, primary: String, secondary: String): Boolean {
        val content = tile.buildContent(primary, secondary) ?: return false
        val bitmap = QrGenerator.encode(content, tile.format) ?: return false
        val valueType = if (tile.category == CreateCategory.BARCODE) ScanValueType.TEXT else ScanValueType.infer(content)
        currentCreated = CreatedResult(content, tile.format.name, valueType, bitmap)
        viewModelScope.launch {
            historyRepo.add(
                HistoryEntity(
                    rawValue = content,
                    displayContent = content,
                    format = tile.format.name,
                    valueType = valueType.name,
                    origin = HistoryEntity.ORIGIN_CREATED,
                ),
            )
        }
        return true
    }

    private fun DecodedCode.toEntity(origin: String) = HistoryEntity(
        rawValue = rawValue,
        displayContent = display,
        format = formatName,
        valueType = valueType.name,
        origin = origin,
    )
}
