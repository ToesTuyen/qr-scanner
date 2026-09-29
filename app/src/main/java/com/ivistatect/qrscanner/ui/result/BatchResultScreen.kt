package com.ivistatect.qrscanner.ui.result

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.domain.DecodedCode
import com.ivistatect.qrscanner.domain.tileGlyph
import com.ivistatect.qrscanner.ui.ServerSubmissionState
import com.ivistatect.qrscanner.ui.ServerDeliveryState
import com.ivistatect.qrscanner.ui.MainViewModel

/**
 * Batch list. n_batch_result is GATED (needs ≥2 physical codes); the list is populated only from
 * live in-memory accumulation — no fabricated data behind the gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchResultScreen(
    mainVm: MainViewModel,
    onOpenItem: (DecodedCode) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { Logger.d("Enter Batch", "count=${mainVm.batchItems.size}") }
    val settings by mainVm.settings.collectAsState()
    var pendingDelete by remember { mutableStateOf<DecodedCode?>(null) }

    // Do not remove an item from confirmValueChange: doing that while the gesture is still
    // settling lets the same pointer be transferred to the rows that shift into its place.
    LaunchedEffect(pendingDelete) {
        val code = pendingDelete ?: return@LaunchedEffect
        Logger.d("Swipe delete @ Batch", "format=${code.formatName}")
        mainVm.removeBatchItem(code)
        pendingDelete = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch") },
                navigationIcon = {
                    IconButton(onClick = { Logger.d("Click Back @ Batch"); onBack() }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back), Modifier.size(24.dp))
                    }
                },
            )
        },
    ) { padding ->
        if (mainVm.batchItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Scan multiple codes in batch mode")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 104.dp),
            ) {
                item(key = "server_submission") {
                    BatchServerSubmissionCard(
                        automatic = settings.autoSubmitServer,
                        state = mainVm.serverSubmissionState,
                        codeCount = mainVm.batchItems.size,
                        onSend = mainVm::sendBatchToServer,
                    )
                }
                items(mainVm.batchItems, key = { it.rawValue }) { code ->
                    val rowShape = RoundedCornerShape(16.dp)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart && pendingDelete == null) {
                                pendingDelete = code
                            } else {
                                Logger.d("Ignore Batch dismiss", "value=$value")
                            }
                            false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(end = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete batch item",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        // Clip the whole swipe container as well as the foreground card. Without
                        // this, the red delete background remained square at the four corners.
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(rowShape),
                    ) {
                        // Keep the foreground itself rounded while it slides over the delete layer.
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(rowShape)
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            BatchRow(
                                code = code,
                                deliveryState = mainVm.batchServerDelivery[code.rawValue]
                                    ?: ServerDeliveryState.PENDING,
                                onOpen = { Logger.d("Click Batch item @ Batch"); onOpenItem(code) },
                                onRetry = {
                                    Logger.d("Click Retry @ Batch", "format=${code.formatName}")
                                    mainVm.retryBatchItem(code)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchServerSubmissionCard(
    automatic: Boolean,
    state: ServerSubmissionState,
    codeCount: Int,
    onSend: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (automatic) {
                Text(
                    stringResource(R.string.batch_auto_submit_server_active),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Button(
                    onClick = onSend,
                    enabled = !state.isSending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.batch_send_to_server, codeCount))
                }
            }
            if (state.total > 0) {
                val summary = if (state.isSending) {
                    stringResource(R.string.batch_server_send_progress, state.completed, state.total)
                } else {
                    stringResource(
                        R.string.batch_server_send_summary,
                        state.total,
                        state.succeeded,
                        state.failed,
                    )
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchRow(
    code: DecodedCode,
    deliveryState: ServerDeliveryState,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val glyph = remember(code.valueType) { code.valueType.tileGlyph() }
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(glyph), contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(code.valueType.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    code.display,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val deliveryText = when (deliveryState) {
                    ServerDeliveryState.PENDING -> stringResource(R.string.batch_delivery_pending)
                    ServerDeliveryState.SENDING -> stringResource(R.string.batch_delivery_sending)
                    ServerDeliveryState.SUCCEEDED -> stringResource(R.string.batch_delivery_succeeded)
                    ServerDeliveryState.FAILED -> stringResource(R.string.batch_delivery_failed)
                }
                val deliveryColor = when (deliveryState) {
                    ServerDeliveryState.SUCCEEDED -> BatchSuccessGreen
                    ServerDeliveryState.FAILED -> BatchFailureRed
                    ServerDeliveryState.PENDING,
                    ServerDeliveryState.SENDING,
                    -> BatchPendingYellow
                }
                Text(
                    deliveryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = deliveryColor,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (deliveryState == ServerDeliveryState.FAILED) {
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(stringResource(R.string.batch_retry_failed))
                    }
                }
            }
            Text(
                code.formatName.replace('_', ' '),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

private val BatchSuccessGreen = Color(0xFF2E7D32)
private val BatchPendingYellow = Color(0xFFF9A825)
private val BatchFailureRed = Color(0xFFB3261E)
