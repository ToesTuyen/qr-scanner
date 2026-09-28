package com.ivistatect.qrscanner.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import com.ivistatect.qrscanner.util.Logger

data class GithubRelease(
    val tagName: String,
    val versionName: String,
    val downloadUrl: String,
)

sealed interface GithubUpdateCheck {
    data class Available(val release: GithubRelease) : GithubUpdateCheck
    data class UpToDate(val versionName: String) : GithubUpdateCheck
    data object NoRelease : GithubUpdateCheck
    data class Error(val message: String) : GithubUpdateCheck
}

/** Reads the public GitHub Release feed and hands the APK to Android's package installer. */
@Singleton
class GithubAppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun checkForUpdate(currentVersion: String): GithubUpdateCheck = withContext(Dispatchers.IO) {
        Logger.d("GitHub update check", "current=$currentVersion")
        runCatching {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "QRScanner-Android")
            }
            try {
                val status = connection.responseCode
                Logger.d("GitHub update response", "status=$status")
                when (status) {
                    HttpURLConnection.HTTP_OK -> parseRelease(
                        connection.inputStream.bufferedReader().use { it.readText() },
                        currentVersion,
                    )
                    HttpURLConnection.HTTP_NOT_FOUND -> GithubUpdateCheck.NoRelease
                    else -> GithubUpdateCheck.Error("GitHub phản hồi HTTP $status.")
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            Logger.e("GitHub update check failed", error.javaClass.simpleName, error)
            GithubUpdateCheck.Error("Không thể kiểm tra bản cập nhật. Vui lòng thử lại.")
        }
    }

    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun downloadAndOpenInstaller(release: GithubRelease): DownloadResult = withContext(Dispatchers.IO) {
        runCatching {
            val downloadManager = context.getSystemService(DownloadManager::class.java)
            val destinationName = "QRScanner-${release.versionName}.apk"
            val request = DownloadManager.Request(Uri.parse(release.downloadUrl)).apply {
                setTitle("QR Scanner ${release.tagName}")
                setDescription("Đang tải bản cập nhật")
                setMimeType(APK_MIME_TYPE)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, destinationName)
            }
            val downloadId = downloadManager.enqueue(request)
            Logger.d("GitHub APK download", "id=$downloadId version=${release.versionName}")
            val apkUri = waitForDownload(downloadManager, downloadId)
            val installerIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installerIntent)
            Logger.d("GitHub APK installer opened", "version=${release.versionName}")
            DownloadResult.InstallerOpened
        }.getOrElse { error ->
            Logger.e("GitHub APK download failed", error.javaClass.simpleName, error)
            DownloadResult.Error("Không thể tải bản cập nhật. Vui lòng thử lại.")
        }
    }

    private suspend fun waitForDownload(downloadManager: DownloadManager, id: Long): Uri {
        while (true) {
            val status = downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                check(cursor.moveToFirst()) { "Không tìm thấy tệp cập nhật." }
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    return requireNotNull(downloadManager.getUriForDownloadedFile(id)) {
                        "Không mở được tệp cập nhật."
                    }
                }
                DownloadManager.STATUS_FAILED -> error("GitHub APK download failed.")
                else -> delay(DOWNLOAD_POLL_DELAY_MS)
            }
        }
    }

    private fun parseRelease(body: String, currentVersion: String): GithubUpdateCheck {
        val release = JSONObject(body)
        val tagName = release.optString("tag_name").trim()
        val versionName = tagName.removePrefix("v").removePrefix("V")
        if (tagName.isBlank() || versionName.isBlank()) {
            return GithubUpdateCheck.Error("GitHub Release không có phiên bản hợp lệ.")
        }
        if (!isNewerVersion(versionName, currentVersion)) {
            return GithubUpdateCheck.UpToDate(currentVersion)
        }
        val assets = release.optJSONArray("assets")
        val asset = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").startsWith(PROFESSIONAL_APK_PREFIX, ignoreCase = true) }
            ?: (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").equals(LEGACY_APK_ASSET_NAME, ignoreCase = true) }
            ?: (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return GithubUpdateCheck.Error("GitHub Release chưa có tệp APK.")
        val downloadUrl = asset.optString("browser_download_url")
        if (downloadUrl.isBlank()) return GithubUpdateCheck.Error("Không lấy được liên kết tải APK.")

        return GithubUpdateCheck.Available(GithubRelease(tagName, versionName, downloadUrl))
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = numericVersion(remote) ?: return false
        val currentParts = numericVersion(current) ?: return false
        val longest = maxOf(remoteParts.size, currentParts.size)
        return (0 until longest).firstNotNullOfOrNull { index ->
            val difference = remoteParts.getOrElse(index) { 0 }.compareTo(currentParts.getOrElse(index) { 0 })
            difference.takeIf { it != 0 }
        }?.let { it > 0 } ?: false
    }

    private fun numericVersion(version: String): List<Int>? =
        VERSION_PATTERN.find(version)?.groupValues?.get(1)
            ?.split('.')
            ?.map { it.toIntOrNull() ?: return null }

    sealed interface DownloadResult {
        data object InstallerOpened : DownloadResult
        data class Error(val message: String) : DownloadResult
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/ToesTuyen/qr-scanner/releases/latest"
        const val PROFESSIONAL_APK_PREFIX = "IVISTA-QR-Scanner-v"
        const val LEGACY_APK_ASSET_NAME = "QRScanner.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val DOWNLOAD_POLL_DELAY_MS = 500L
        val VERSION_PATTERN = Regex("^v?(\\d+(?:\\.\\d+)*)")
    }
}
