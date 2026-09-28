package com.ivistatect.qrscanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.ivistatect.qrscanner.domain.DecodedCode
import com.ivistatect.qrscanner.domain.ResultAction
import com.ivistatect.qrscanner.domain.ScanValueType
import com.ivistatect.qrscanner.domain.tileGlyph
import com.ivistatect.qrscanner.scan.ImageQrDecoder
import com.ivistatect.qrscanner.scan.QrGenerator
import com.ivistatect.qrscanner.ui.common.copyToClipboard
import com.ivistatect.qrscanner.ui.common.fireResultAction
import com.ivistatect.qrscanner.ui.common.shareText
import com.ivistatect.qrscanner.ui.common.hideSystemNavigationBar
import com.ivistatect.qrscanner.ui.language.AppLanguage
import com.ivistatect.qrscanner.ui.theme.QrScannerTheme
import com.ivistatect.qrscanner.util.Logger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Share-in target (n_sharein_entry / n_sharein_result): external VIEW/SEND of an image or text →
 * MLKit decode → in-place result, or an "Error detecting QR or Barcode!" message. Does NOT route
 * into the main history viewer (matches the reference).
 */
class ShareInActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemNavigationBar()
        Logger.d("Enter ShareInActivity", "action=${intent?.action}")
        setContent { QrScannerTheme { ShareInScreen(intent) { finish() } } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigationBar()
    }
}

private sealed interface ShareInState {
    data object Loading : ShareInState
    data class Success(val code: DecodedCode) : ShareInState
    data object Error : ShareInState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShareInScreen(intent: Intent?, onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ShareInState>(ShareInState.Loading) }

    LaunchedEffect(Unit) {
        Logger.d("Enter ShareIn")
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val uri: Uri? = IntentCompat.getParcelableExtra(intent ?: Intent(), Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent?.data
        state = when {
            !text.isNullOrBlank() -> ShareInState.Success(DecodedCode.of(text, "TEXT"))
            uri != null -> ImageQrDecoder.decode(context, uri)?.let { ShareInState.Success(it) } ?: ShareInState.Error
            else -> ShareInState.Error
        }
        Logger.d("ShareIn decode complete", "success=${state is ShareInState.Success}")
        if (state is ShareInState.Error) {
            Toast.makeText(context, R.string.error_detecting, Toast.LENGTH_LONG).show()
        }
    }

    when (val s = state) {
        ShareInState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        ShareInState.Error -> Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.error_detecting))
                OutlinedButton(onClick = {
                    Logger.d("Click Close @ ShareIn error")
                    onClose()
                }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
        is ShareInState.Success -> ShareInResult(s.code, onClose)
    }
}

/** Share-in success = the reference scan-result layout (top bar / header / action grid / QR). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShareInResult(code: DecodedCode, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(code.rawValue) { Logger.d("Enter ShareInResult", "type=${code.valueType}") }
    val previewFormat = remember(code.formatName) { QrGenerator.formatFromName(code.formatName) }
    val preview = remember(code.rawValue, previewFormat) {
        previewFormat
            ?.takeIf(QrGenerator::isTwoDimensional)
            ?.let { QrGenerator.encode(code.rawValue, it, 600) }
    }
    val friendly = if (code.formatName == "QR_CODE") "QR Code" else code.formatName
    val subtitle = remember(code.formatName) {
        SimpleDateFormat("MMM d, yyyy hh:mm a", Locale.getDefault()).format(Date()) + ", $friendly"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$friendly (${code.valueType.name})") },
                navigationIcon = {
                    IconButton(onClick = { Logger.d("Click Back @ ShareIn"); onClose() }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back), Modifier.size(24.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(code.valueType.tileGlyph()), null, Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(code.valueType.name, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    painterResource(R.drawable.ic_un_fav),
                    contentDescription = null,
                    Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Text(
                code.display,
                style = MaterialTheme.typography.bodyLarge,
                color = if (code.valueType == ScanValueType.URL) Color(0xFF1877F2) else MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                code.valueType.actions().forEach { action ->
                    Column(
                        Modifier.clickable {
                            Logger.d("Click ${action.name} @ ShareIn")
                            when (action) {
                                ResultAction.COPY -> context.copyToClipboard(code.rawValue)
                                ResultAction.SHARE -> context.shareText(code.rawValue)
                                else -> context.fireResultAction(action, code.rawValue)
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painterResource(action.shareInGlyph()),
                            null,
                            Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(action.shareInLabel()), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            preview?.let {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(it.asImageBitmap(), stringResource(R.string.cd_qr_preview), Modifier.size(220.dp).background(Color.White).padding(10.dp))
                }
            }
        }
    }
}

private fun ResultAction.shareInGlyph(): Int = when (this) {
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

private fun ResultAction.shareInLabel(): Int = when (this) {
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
