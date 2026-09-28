package com.ivistatect.qrscanner.ui.create

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vnnami.appkit.api.Logger
import com.ivistatect.qrscanner.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ivistatect.qrscanner.domain.tileGlyph
import com.ivistatect.qrscanner.scan.ImageSaver
import com.ivistatect.qrscanner.ui.MainViewModel
import com.ivistatect.qrscanner.ui.common.copyToClipboard
import com.ivistatect.qrscanner.ui.common.shareImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatedResultScreen(mainVm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val created = mainVm.currentCreated

    if (created == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    LaunchedEffect(Unit) { Logger.d("Enter CreatedResult", "format=${created.formatName}") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_create)) },
                navigationIcon = {
                    IconButton(onClick = { Logger.d("Click Back @ CreatedResult"); onBack() }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back), Modifier.size(24.dp))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Logger.d("Click Print @ CreatedResult (deferred)")
                        Toast.makeText(context, R.string.feature_deferred, Toast.LENGTH_SHORT).show()
                    }) { Icon(painterResource(R.drawable.ic_printer), stringResource(R.string.cd_print), Modifier.size(24.dp)) }
                    IconButton(onClick = {
                        Logger.d("Click More @ CreatedResult (deferred)")
                        Toast.makeText(context, R.string.feature_deferred, Toast.LENGTH_SHORT).show()
                    }) { Icon(painterResource(R.drawable.ic_three_dot), stringResource(R.string.cd_more), Modifier.size(24.dp)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // QR card: header (glyph + type + format) + generated code + Share / Save.
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painterResource(created.valueType.tileGlyph()),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(created.valueType.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                createdSubtitle(created.formatName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_un_fav),
                            contentDescription = stringResource(R.string.action_favorite),
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Image(
                            created.bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_generated_code),
                            modifier = Modifier.size(240.dp).background(Color.White).padding(10.dp),
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = {
                                Logger.d("Click Share @ CreatedResult")
                                val uri = ImageSaver.shareUri(context, created.bitmap)
                                if (uri != null) context.shareImage(uri)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(painterResource(R.drawable.ic_share), null, Modifier.size(20.dp))
                            Text(stringResource(R.string.action_share), Modifier.padding(start = 8.dp))
                        }
                        Button(
                            onClick = {
                                Logger.d("Click Save @ CreatedResult")
                                val ok = ImageSaver.saveToGallery(context, created.bitmap, "qr_${System.currentTimeMillis()}")
                                Toast.makeText(
                                    context,
                                    if (ok) R.string.saved_to_gallery else R.string.error_detecting,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2879FA)),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(painterResource(R.drawable.save), null, Modifier.size(20.dp), tint = Color.White)
                            Text(stringResource(R.string.save), Modifier.padding(start = 8.dp), color = Color.White)
                        }
                    }
                }
            }

            // Content card: label + copy + the encoded value.
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.created_content),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = {
                            Logger.d("Click Copy @ CreatedResult")
                            context.copyToClipboard(created.content)
                            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                        }) { Icon(painterResource(R.drawable.ic_copy), stringResource(R.string.action_copy), Modifier.size(22.dp)) }
                    }
                    Text(
                        created.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF1877F2),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** "MMM d, yyyy hh:mm a, <friendly format>" — matches the reference result subtitle shape. */
private fun createdSubtitle(formatName: String): String {
    val date = SimpleDateFormat("MMM d, yyyy hh:mm a", Locale.getDefault()).format(Date())
    val friendly = if (formatName == "QR_CODE") "QR Code"
    else formatName.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
    return "$date, $friendly"
}
