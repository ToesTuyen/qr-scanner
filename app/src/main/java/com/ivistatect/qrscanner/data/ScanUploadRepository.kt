package com.ivistatect.qrscanner.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import com.ivistatect.qrscanner.util.Logger

data class ServerRequestResult(
    val succeeded: Boolean,
    val status: Int? = null,
)

/** Sends scanner events to the IVISTA server without blocking the scanner UI. */
@Singleton
class ScanUploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Sends codes in strict order. Before starting the next code's server session, the previous
     * one is ended, so a phone never leaves two capture sessions open at the same time.
     */
    suspend fun submitScan(barcode: String): Boolean = sequentialRequestMutex.withLock {
        val settings = settingsRepository.settings.first()
        val tableId = settings.tableId.trim()
        if (tableId.isBlank()) {
            Logger.e("CURL server thiếu Mã bàn", "operation=Gửi mã đến server")
            return@withLock false
        }

        settings.activeServerSessionTableId?.let { activeTableId ->
            Logger.d("Dừng phiên trước khi quét mã mới", "table_id=$activeTableId")
            val stopResult = endSessionRequest(activeTableId)
            if (!stopResult.succeeded) return@withLock false
            settingsRepository.clearActiveServerSession()
        }

        val result = request(
            url = SCAN_URL,
            body = JSONObject()
                .put("table_id", tableId)
                .put("barcode", barcode)
                .put("device_id", ScanDeviceId.from(context))
                .put("operator_id", OPERATOR_ID)
                .toString(),
            operation = "Gửi mã đến server",
        )
        if (result.succeeded) settingsRepository.markServerSessionActive(tableId)
        result.succeeded
    }

    /** Ends the active server-side scanner session. Used by the temporary manual stop control. */
    suspend fun endSession(): ServerRequestResult = sequentialRequestMutex.withLock {
        val settings = settingsRepository.settings.first()
        val tableId = settings.activeServerSessionTableId ?: settings.tableId
        val result = endSessionRequest(tableId)
        if (result.succeeded) settingsRepository.clearActiveServerSession()
        result
    }

    private suspend fun endSessionRequest(tableId: String): ServerRequestResult = request(
        url = SESSION_END_URL,
        body = JSONObject()
            .put("table_id", tableId)
            .put("reason", "MANUAL_STOP")
            .toString(),
        operation = "Dừng phiên server",
    )

    private suspend fun request(url: String, body: String, operation: String): ServerRequestResult =
        withContext(Dispatchers.IO) {
            Logger.d("CURL gửi server", curlCommand(url, body))
            val connection = (URL(url).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.doOutput = true
                connection.setRequestProperty("X-API-Key", SERVER_ACCESS_KEY)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                val detail = "operation=$operation status=$status response=${response.take(1_000)}"
                if (status in 200..299) {
                    Logger.d("CURL phản hồi server", detail)
                    ServerRequestResult(succeeded = true, status = status)
                } else {
                    Logger.e("CURL server thất bại", detail)
                    ServerRequestResult(succeeded = false, status = status)
                }
            } catch (error: Exception) {
                Logger.e(
                    "CURL server không gọi được",
                    "operation=$operation reason=${error.javaClass.simpleName}",
                    error,
                )
                ServerRequestResult(succeeded = false)
            } finally {
                connection.disconnect()
            }
        }

    private fun curlCommand(url: String, body: String): String =
        "curl -s -X POST ${shellQuote(url)} " +
            "-H ${shellQuote("X-API-Key: $SERVER_ACCESS_KEY")} " +
            "-H ${shellQuote("Content-Type: application/json")} " +
            "-d ${shellQuote(body)}"

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\\"'\\\"'")}'"

    private companion object {
        const val SCAN_URL = "https://vtpautopackage.ivistatech.vn/api/v1/scanner/scan"
        const val SESSION_END_URL = "https://vtpautopackage.ivistatech.vn/api/v1/scanner/session/end"
        const val SERVER_ACCESS_KEY = "919f9e2da39a153bb150343e551d73b3d4867f407e8e315bb26b89170a1928fc"
        const val OPERATOR_ID = "DEV_TEST"
        val sequentialRequestMutex = Mutex()
    }
}
