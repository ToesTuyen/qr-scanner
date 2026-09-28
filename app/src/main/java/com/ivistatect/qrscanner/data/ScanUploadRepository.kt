package com.ivistatect.qrscanner.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) {
    suspend fun submitScan(barcode: String): Boolean = request(
        url = SCAN_URL,
        body = JSONObject()
            .put("table_id", TABLE_ID)
            .put("barcode", barcode)
            .put("device_id", ScanDeviceId.from(context))
            .put("operator_id", OPERATOR_ID)
            .toString(),
        operation = "Gửi mã đến server",
    ).succeeded

    /** Ends the active server-side scanner session. Used by the temporary manual stop control. */
    suspend fun endSession(): ServerRequestResult = request(
        url = TEST_STOP_URL,
        body = JSONObject()
            .put("table_id", TABLE_ID)
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
        const val TEST_STOP_URL = "http://127.0.0.1:18000/api/v1/scanner/session/end"
        const val SERVER_ACCESS_KEY = "919f9e2da39a153bb150343e551d73b3d4867f407e8e315bb26b89170a1928fc"
        const val TABLE_ID = "1061baeb-8ea7-4e11-9b0d-181ee4218c2a"
        const val OPERATOR_ID = "DEV_TEST"
    }
}
