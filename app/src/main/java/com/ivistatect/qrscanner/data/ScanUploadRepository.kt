package com.ivistatect.qrscanner.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import com.ivistatect.qrscanner.util.Logger

/** Sends each newly scanned code to the IVISTA auto-package service without blocking the scanner UI. */
@Singleton
class ScanUploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun submitScan(barcode: String): Boolean = withContext(Dispatchers.IO) {
        val connection = (URL(SCAN_URL).openConnection() as HttpsURLConnection)
        try {
            val body = JSONObject()
                .put("table_id", TABLE_ID)
                .put("barcode", barcode)
                .put("device_id", ScanDeviceId.from(context))
                .put("operator_id", OPERATOR_ID)
                .toString()

            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("X-API-Key", API_KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

            val status = connection.responseCode
            if (status in 200..299) {
                Logger.d("Scanner API scan submitted", "status=$status")
                true
            } else {
                Logger.e("Scanner API rejected scan", "status=$status")
                false
            }
        } catch (error: Exception) {
            Logger.e("Scanner API request failed", "reason=${error.javaClass.simpleName}", error)
            false
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val SCAN_URL = "https://vtpautopackage.ivistatech.vn/api/v1/scanner/scan"
        const val API_KEY = "919f9e2da39a153bb150343e551d73b3d4867f407e8e315bb26b89170a1928fc"
        const val TABLE_ID = "1061baeb-8ea7-4e11-9b0d-181ee4218c2a"
        const val OPERATOR_ID = "DEV_TEST"
    }
}
