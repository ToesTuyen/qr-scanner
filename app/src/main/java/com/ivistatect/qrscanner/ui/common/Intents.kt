package com.ivistatect.qrscanner.ui.common

import android.app.Activity
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.ivistatect.qrscanner.domain.ResultAction

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Context.copyToClipboard(text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("qr", text))
}

fun Context.readClipboard(): String =
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()

private fun Context.safeStart(intent: Intent) {
    runCatching { startActivity(intent) }
        .onFailure { Toast.makeText(this, "No app found to handle this", Toast.LENGTH_SHORT).show() }
}

fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    safeStart(Intent.createChooser(send, "Share"))
}

fun Context.shareImage(uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    safeStart(Intent.createChooser(send, "Share"))
}

fun Context.openUrl(url: String) = safeStart(Intent(Intent.ACTION_VIEW, url.toUri()))

/** Open this app's Play Store listing, falling back to the web listing when Play Store is absent. */
fun Context.openStoreListing() {
    val appId = packageName
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$appId".toUri())) }
        .onFailure { openUrl("https://play.google.com/store/apps/details?id=$appId") }
}

/** Share the decoded value as a named file (CSV / text) via the app FileProvider. */
fun Context.shareTextAs(text: String, fileName: String, mime: String) {
    runCatching {
        val dir = java.io.File(cacheDir, "shared_images").apply { mkdirs() }
        val file = java.io.File(dir, fileName).apply { writeText(text) }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        safeStart(Intent.createChooser(send, "Share"))
    }.onFailure { Toast.makeText(this, "No app found to handle this", Toast.LENGTH_SHORT).show() }
}

/** Print [bitmap] via the Android system print spooler (reference parity for the Print action). */
fun Context.printBitmap(jobName: String, bitmap: android.graphics.Bitmap) {
    runCatching {
        androidx.print.PrintHelper(this).apply {
            scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT
        }.printBitmap(jobName, bitmap)
    }.onFailure { Toast.makeText(this, "No app found to handle this", Toast.LENGTH_SHORT).show() }
}

/** Fire the external intent for a result-grid action. The click itself is logged at the call site. */
fun Context.fireResultAction(action: ResultAction, raw: String, searchEngine: Int = 0) {
    val stripped = raw.substringAfter(":", raw)
    when (action) {
        ResultAction.OPEN -> safeStart(Intent(Intent.ACTION_VIEW, raw.toUri()))
        ResultAction.WEB_SEARCH -> {
            if (searchEngine == 0) {
                val search = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, raw) }
                runCatching { startActivity(search) }.onFailure {
                    openUrl("https://www.google.com/search?q=" + Uri.encode(raw))
                }
            } else {
                val encoded = Uri.encode(raw)
                val url = when (searchEngine) {
                    1 -> "https://www.google.com/search?q=$encoded"
                    2 -> "https://www.bing.com/search?q=$encoded"
                    3 -> "https://search.yahoo.com/search?p=$encoded"
                    4 -> "https://yandex.com/search/?text=$encoded"
                    5 -> "https://duckduckgo.com/?q=$encoded"
                    6 -> "https://www.qwant.com/?q=$encoded"
                    else -> "https://www.google.com/search?q=$encoded"
                }
                openUrl(url)
            }
        }
        ResultAction.PRODUCT_DETAILS -> {
            openUrl("https://www.google.com/search?tbm=shop&q=" + Uri.encode(raw))
        }
        ResultAction.CALL -> safeStart(Intent(Intent.ACTION_DIAL, "tel:$stripped".toUri()))
        ResultAction.SMS -> safeStart(Intent(Intent.ACTION_SENDTO, "smsto:$stripped".toUri()))
        ResultAction.EMAIL -> safeStart(Intent(Intent.ACTION_SENDTO, "mailto:$stripped".toUri()))
        ResultAction.MAP -> safeStart(Intent(Intent.ACTION_VIEW, raw.toUri()))
        ResultAction.WIFI -> safeStart(Intent(Settings.ACTION_WIFI_SETTINGS))
        ResultAction.CONTACT -> {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.NAME, raw)
            }
            safeStart(intent)
        }
        ResultAction.COPY -> copyToClipboard(raw)
        ResultAction.SHARE -> shareText(raw)
    }
}

fun Context.openAppSettings() {
    val activity = findActivity() ?: return
    safeStart(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
    )
}
