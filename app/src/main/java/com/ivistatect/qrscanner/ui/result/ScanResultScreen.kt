package com.ivistatect.qrscanner.ui.result

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.domain.ResultAction
import com.ivistatect.qrscanner.domain.ScanValueType
import com.ivistatect.qrscanner.domain.tileGlyph
import com.ivistatect.qrscanner.scan.QrGenerator
import com.ivistatect.qrscanner.ui.MainViewModel
import com.ivistatect.qrscanner.ui.common.copyToClipboard
import com.ivistatect.qrscanner.ui.common.fireResultAction
import com.ivistatect.qrscanner.ui.common.printBitmap
import com.ivistatect.qrscanner.ui.common.shareText
import com.ivistatect.qrscanner.ui.common.shareTextAs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScanResultScreen(mainVm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val code = mainVm.currentScan
    val favorite = mainVm.currentScanFavorite
    val settings by mainVm.settings.collectAsState()
    var showMore by remember { mutableStateOf(false) }

    if (code == null) {
        // Defensive: nothing to show → go back.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    LaunchedEffect(Unit) { Logger.d("Enter ScanResult", "type=${code.valueType}") }

    val previewFormat = remember(code.formatName) { QrGenerator.formatFromName(code.formatName) }
    val previewBitmap = remember(code.rawValue, previewFormat) {
        previewFormat?.let { QrGenerator.encode(code.rawValue, it, 600) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${friendlyFormat(code.formatName)} (${code.valueType.name})") },
                navigationIcon = {
                    IconButton(onClick = { Logger.d("Click Back @ ScanResult"); onBack() }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back), Modifier.size(24.dp))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Print → Android system print spooler (reference parity, external boundary).
                        Logger.d("Click Print @ ScanResult")
                        previewBitmap?.let { context.printBitmap(context.getString(R.string.print_job), it) }
                    }) { Icon(painterResource(R.drawable.ic_printer), stringResource(R.string.cd_print), Modifier.size(24.dp)) }
                    IconButton(onClick = { Logger.d("Click More @ ScanResult"); showMore = true }) {
                        Icon(painterResource(R.drawable.ic_three_dot), stringResource(R.string.cd_more), Modifier.size(24.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header row (flat, no card — matches the reference scan result): glyph + type + favorite.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(code.valueType.tileGlyph()),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(code.valueType.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        resultSubtitle(code.formatName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    Logger.d("Click Favorite @ ScanResult", "next=${!favorite}")
                    mainVm.toggleCurrentScanFavorite()
                }) {
                    Image(
                        painterResource(if (favorite) R.drawable.ic_fav else R.drawable.ic_un_fav),
                        contentDescription = stringResource(R.string.action_favorite),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Decoded content (blue hyperlink styling for URLs).
            Text(
                code.display,
                style = MaterialTheme.typography.bodyLarge,
                color = if (code.valueType == ScanValueType.URL) Color(0xFF1877F2) else MaterialTheme.colorScheme.onSurface,
            )

            // Type-specific action grid (rvQrOption) — icon + label, real reference glyphs.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                code.valueType.actions(settings.showProduct, code.formatName).forEach { action ->
                    ActionItem(action) {
                        Logger.d("Click ${action.name} @ ScanResult", "raw=${code.rawValue.take(64)}")
                        when (action) {
                            ResultAction.COPY -> {
                                context.copyToClipboard(code.rawValue)
                                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                            }
                            ResultAction.SHARE -> context.shareText(code.rawValue)
                            else -> context.fireResultAction(action, code.rawValue, settings.searchEngine)
                        }
                    }
                }
            }

            // Recreated in the same symbology as the scanned code: EAN/Code 128 stays a barcode.
            previewBitmap?.let {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (previewFormat != null && QrGenerator.isTwoDimensional(previewFormat)) {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(220.dp).background(Color.White).padding(10.dp),
                        )
                    } else {
                        Column(
                            modifier = Modifier.width(300.dp).background(Color.White).padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                            )
                            Text(
                                text = code.rawValue,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = {
            Logger.d("Dismiss More sheet @ ScanResult")
            showMore = false
        }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                // Reference share/delete sheet: Share · CSV file · Text file · Delete.
                SheetItem(R.drawable.ic_share, stringResource(R.string.action_share)) {
                    Logger.d("Click Share @ ScanResult More sheet"); showMore = false; context.shareText(code.rawValue)
                }
                SheetItem(R.drawable.ic_copy, stringResource(R.string.share_sheet_csv)) {
                    Logger.d("Click CSV @ ScanResult More sheet"); showMore = false
                    context.shareTextAs(code.rawValue, "qr.csv", "text/csv")
                }
                SheetItem(R.drawable.ic_copy, stringResource(R.string.share_sheet_text)) {
                    Logger.d("Click Text file @ ScanResult More sheet"); showMore = false
                    context.shareTextAs(code.rawValue, "qr.txt", "text/plain")
                }
                SheetItem(R.drawable.ic_un_fav, stringResource(R.string.action_delete)) {
                    Logger.d("Click Delete @ ScanResult More sheet"); showMore = false
                    mainVm.deleteCurrentScan(); onBack()
                }
            }
        }
    }
}

@Composable
private fun SheetItem(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.padding(start = 20.dp))
    }
}

@Composable
private fun ActionItem(action: ResultAction, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(action.glyph()),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(action.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@DrawableRes
private fun ResultAction.glyph(): Int = when (this) {
    ResultAction.OPEN -> R.drawable.ic_open
    ResultAction.WEB_SEARCH -> R.drawable.ic_globe
    ResultAction.PRODUCT_DETAILS -> R.drawable.ic_globe
    ResultAction.CALL -> R.drawable.ic_dialer
    ResultAction.SMS -> R.drawable.ic_sms
    ResultAction.EMAIL -> R.drawable.ic_email
    ResultAction.WIFI -> R.drawable.ic_connect
    ResultAction.MAP -> R.drawable.ic_navigation
    ResultAction.CONTACT -> R.drawable.ic_add_contact
    ResultAction.COPY -> R.drawable.ic_copy
    ResultAction.SHARE -> R.drawable.ic_share
}

/** Friendly format name: QR_CODE → "QR Code", CODE_128 → "Code 128". */
private fun friendlyFormat(formatName: String): String =
    if (formatName == "QR_CODE") "QR Code"
    else formatName.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }

/** "MMM d, yyyy hh:mm a, <friendly format>" — matches the reference result subtitle shape. */
private fun resultSubtitle(formatName: String): String {
    val date = java.text.SimpleDateFormat("MMM d, yyyy hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
    return "$date, ${friendlyFormat(formatName)}"
}

@StringRes
private fun ResultAction.labelRes(): Int = when (this) {
    ResultAction.OPEN -> R.string.action_open
    ResultAction.WEB_SEARCH -> R.string.action_web_search
    ResultAction.PRODUCT_DETAILS -> R.string.action_product_details
    ResultAction.CALL -> R.string.action_call
    ResultAction.SMS -> R.string.action_sms
    ResultAction.EMAIL -> R.string.action_email
    ResultAction.WIFI -> R.string.action_wifi
    ResultAction.MAP -> R.string.action_map
    ResultAction.CONTACT -> R.string.action_contact
    ResultAction.COPY -> R.string.action_copy
    ResultAction.SHARE -> R.string.action_share
}
