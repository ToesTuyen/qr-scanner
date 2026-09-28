package com.ivistatect.qrscanner.ui.create

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.domain.CreateCatalog
import com.ivistatect.qrscanner.domain.FormKind
import com.ivistatect.qrscanner.ui.MainViewModel
import com.ivistatect.qrscanner.ui.common.readClipboard

private const val MAX_LEN = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFormScreen(
    tileId: String,
    mainVm: MainViewModel,
    onCreated: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val tile = remember(tileId) { CreateCatalog.find(tileId) }

    if (tile == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    LaunchedEffect(Unit) { Logger.d("Enter CreateForm", "id=$tileId") }

    // URL prefills the "https://" scheme as real text (reference parity); clipboard prefills content.
    var primary by remember {
        mutableStateOf(
            when {
                tileId == "clipboard" -> context.readClipboard()
                tileId == "url" -> "https://"
                else -> ""
            },
        )
    }
    var secondary by remember { mutableStateOf("") }

    fun submit() {
        Logger.d("Click Create @ CreateForm", "id=$tileId")
        val ok = mainVm.createCode(tile, primary, secondary)
        if (ok) onCreated()
        else {
            Toast.makeText(context, R.string.create_empty_toast, Toast.LENGTH_SHORT).show()
            Logger.d("CreateForm: validation failed (stay)", "id=$tileId")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tile.labelRes?.let { stringResource(it) } ?: tile.label) },
                navigationIcon = {
                    IconButton(onClick = { Logger.d("Click Back @ CreateForm"); onBack() }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back), Modifier.size(24.dp))
                    }
                },
                actions = {
                    // Reference: "✓ Tạo nên" as a blue pill action in the top bar.
                    Button(
                        onClick = { submit() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.create_button),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tile.kind) {
                FormKind.WIFI -> {
                    LabeledField(primary, R.string.form_wifi_ssid) { primary = it }
                    LabeledField(secondary, R.string.form_wifi_password) { secondary = it }
                }
                FormKind.CONTACT -> {
                    LabeledField(primary, R.string.form_contact_name) { primary = it }
                    LabeledField(secondary, R.string.form_contact_phone) { secondary = it }
                }
                FormKind.SINGLE -> {
                    // White input card with clear-X and a char counter (reference).
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            TextField(
                                value = primary,
                                onValueChange = { if (it.length <= MAX_LEN) primary = it },
                                placeholder = { Text(tile.hint) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (tileId == "url") KeyboardType.Uri else KeyboardType.Text,
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                            )
                            if (primary.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        Logger.d("Click Clear input @ CreateForm", "id=$tileId")
                                        primary = ""
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cd_clear),
                                        Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                "${primary.length}/$MAX_LEN",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            )
                        }
                    }

                    // URL quick-insert chips (reference).
                    if (tileId == "url") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            listOf("https://", "http://", "www.", ".com").forEach { chip ->
                                SuggestionChip(
                                    onClick = {
                                        Logger.d("Click URL suggestion @ CreateForm", "value=$chip")
                                        primary = when (chip) {
                                            "https://", "http://" -> chip
                                            else -> (primary + chip).take(MAX_LEN)
                                        }
                                    },
                                    label = { Text(chip) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledField(value: String, labelRes: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth(),
    )
}
