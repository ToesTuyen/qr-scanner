package com.ivistatect.qrscanner.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.data.SettingsRepository
import com.ivistatect.qrscanner.scan.BarcodeAnalyzer
import com.ivistatect.qrscanner.scan.ImageQrDecoder
import com.ivistatect.qrscanner.ui.MainViewModel
import com.ivistatect.qrscanner.ui.ServerDeliveryState
import com.ivistatect.qrscanner.ui.common.copyToClipboard
import com.ivistatect.qrscanner.ui.common.fireResultAction
import com.ivistatect.qrscanner.ui.common.findActivity
import com.ivistatect.qrscanner.ui.common.openAppSettings
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    mainVm: MainViewModel,
    onResult: () -> Unit,
    onOpenBatch: () -> Unit,
    onTableIdScanned: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val isTableIdCapture = onTableIdScanned != null

    LaunchedEffect(Unit) { Logger.d("Enter Scanner") }

    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(granted()) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    // Scan-guide is a one-time tutorial (reference parity): shown until dismissed once, then persisted.
    // `scanGuideSeen` defaults to true until DataStore loads, so it never flashes on a cold launch.
    val guideSeen by mainVm.scanGuideSeen.collectAsState()
    val settings by mainVm.settings.collectAsState()
    val currentSettings = rememberUpdatedState(settings)
    LaunchedEffect(settings.batchScanning) { mainVm.syncBatchMode(settings.batchScanning) }
    var guideDismissed by remember { mutableStateOf(false) }
    val showGuide = !isTableIdCapture && !guideSeen && !guideDismissed
    var torchOn by remember { mutableStateOf(false) }
    var zoomStepIndex by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var scanFrameBounds by remember { mutableStateOf<Rect?>(null) }
    val zoomSteps = remember { listOf(1f, 2f, 3f, 5f) }
    val zoomRatio = zoomSteps[zoomStepIndex]
    val scannerMotion = rememberInfiniteTransition(label = "scannerMotion")
    val scanSweepPhase by scannerMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scannerSweepPhase",
    )
    val scanLineProgress = if (scanSweepPhase <= 0.5f) {
        0.12f + scanSweepPhase * 1.52f
    } else {
        0.88f - (scanSweepPhase - 0.5f) * 1.52f
    }
    val scanMovesUpward = scanSweepPhase > 0.5f

    val analyzerHolder = remember { arrayOfNulls<BarcodeAnalyzer>(1) }
    val analyzer = remember {
        BarcodeAnalyzer { code ->
            val scanSettings = currentSettings.value
            val hapticPlayed = if (scanSettings.vibration) {
                context.vibrateOnScan()
            } else {
                false
            }
            val soundPlayed = if (scanSettings.sound) context.playScanTone() else false
            Logger.d("Scan feedback", "vibration=$hapticPlayed sound=$soundPlayed")
            Logger.d("Scanner: decoded", "type=${code.valueType} format=${code.formatName}")
            if (onTableIdScanned != null) {
                Logger.d("Table ID scanned", "value=${code.rawValue}")
                onTableIdScanned(code.rawValue.trim())
            } else {
                mainVm.onDecoded(code)
                if (scanSettings.autoCopy) {
                    context.copyToClipboard(code.rawValue)
                    Logger.d("Scanner: auto copied result")
                }
                if (mainVm.batchMode) {
                    scope.launch { kotlinx.coroutines.delay(1200); analyzerHolder[0]?.reset() }
                } else {
                    onResult()
                    if (scanSettings.webSearch) {
                        Logger.d("Scanner: auto web search", "engine=${scanSettings.searchEngine}")
                        context.fireResultAction(
                            com.ivistatect.qrscanner.domain.ResultAction.WEB_SEARCH,
                            code.rawValue,
                            scanSettings.searchEngine,
                        )
                    }
                }
            }
        }.also { analyzerHolder[0] = it }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        Logger.d("Scanner: camera permission result", "granted=$isGranted")
        hasPermission = isGranted
        if (!isGranted) {
            val act = context.findActivity()
            val showRationale = act != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.CAMERA)
            permanentlyDenied = !showRationale
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            Logger.d("Scanner: gallery image picked")
            scope.launch {
                val decoded = ImageQrDecoder.decode(context, uri)
                if (decoded != null) {
                    val scanSettings = currentSettings.value
                    val hapticPlayed = if (scanSettings.vibration) context.vibrateOnScan() else false
                    val soundPlayed = if (scanSettings.sound) context.playScanTone() else false
                    Logger.d("Scan feedback", "vibration=$hapticPlayed sound=$soundPlayed source=gallery")
                    if (onTableIdScanned != null) {
                        Logger.d("Table ID scanned", "value=${decoded.rawValue} source=gallery")
                        onTableIdScanned(decoded.rawValue.trim())
                    } else {
                        mainVm.onDecoded(decoded)
                        if (!mainVm.batchMode) {
                            onResult()
                            if (scanSettings.webSearch) {
                                Logger.d("Scanner: auto web search", "engine=${scanSettings.searchEngine} source=gallery")
                                context.fireResultAction(
                                    com.ivistatect.qrscanner.domain.ResultAction.WEB_SEARCH,
                                    decoded.rawValue,
                                    scanSettings.searchEngine,
                                )
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, R.string.error_detecting, Toast.LENGTH_SHORT).show()
                    Logger.d("Scanner: gallery decode failed")
                }
            }
        }
    }

    // Re-check permission and re-arm the scanner on resume (e.g. returning from System Settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = granted()
                analyzer.reset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            val cameraSelector = if (settings.cameraFacing == SettingsRepository.CAMERA_FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            key(cameraSelector) {
                CameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    analyzer = analyzer,
                    cameraSelector = cameraSelector,
                    onCameraReady = { camera = it },
                )
            }
            LaunchedEffect(camera, torchOn) { camera?.cameraControl?.enableTorch(torchOn) }
            LaunchedEffect(camera, zoomRatio) {
                val zoomState = camera?.cameraInfo?.zoomState?.value
                val supportedRatio = zoomRatio.coerceIn(
                    zoomState?.minZoomRatio ?: 1f,
                    zoomState?.maxZoomRatio ?: 1f,
                )
                camera?.cameraControl?.setZoomRatio(supportedRatio)
            }

            // Darken the complete camera preview except the measured viewfinder rectangle.
            scanFrameBounds?.let { frame ->
                Canvas(Modifier.fillMaxSize()) {
                    val left = frame.left.coerceIn(0f, size.width)
                    val top = frame.top.coerceIn(0f, size.height)
                    val right = frame.right.coerceIn(left, size.width)
                    val bottom = frame.bottom.coerceIn(top, size.height)
                    val shade = Color.Black.copy(alpha = 0.58f)
                    val cornerRadius = minOf(right - left, bottom - top) * 0.035f
                    val outsideViewfinder = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRoundRect(
                            RoundRect(
                                Rect(left, top, right, bottom),
                                CornerRadius(cornerRadius, cornerRadius),
                            ),
                        )
                    }
                    drawPath(outsideViewfinder, shade)
                }
            }

            // Centred product-style viewfinder, with the scan line confined to its rounded brackets.
            Column(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(top = 168.dp, start = 32.dp, end = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1.15f).onGloballyPositioned { coordinates ->
                        scanFrameBounds = coordinates.boundsInRoot()
                    },
                ) {
                    val c = Color.White
                    val len = size.minDimension * 0.16f
                    val w = 6f
                    val cornerRadius = size.minDimension * 0.035f
                    val borderStroke = Stroke(width = w, cap = StrokeCap.Round)
                    // Four white corner brackets preserve the camera image inside the recognition area.
                    drawLine(c, Offset(cornerRadius, 0f), Offset(len, 0f), w, StrokeCap.Round)
                    drawLine(c, Offset(0f, cornerRadius), Offset(0f, len), w, StrokeCap.Round)
                    drawArc(
                        c, 180f, 90f, false, Offset.Zero,
                        Size(cornerRadius * 2f, cornerRadius * 2f), style = borderStroke,
                    )
                    drawLine(c, Offset(size.width - len, 0f), Offset(size.width - cornerRadius, 0f), w, StrokeCap.Round)
                    drawLine(c, Offset(size.width, cornerRadius), Offset(size.width, len), w, StrokeCap.Round)
                    drawArc(
                        c, 270f, 90f, false, Offset(size.width - cornerRadius * 2f, 0f),
                        Size(cornerRadius * 2f, cornerRadius * 2f), style = borderStroke,
                    )
                    drawLine(c, Offset(cornerRadius, size.height), Offset(len, size.height), w, StrokeCap.Round)
                    drawLine(c, Offset(0f, size.height - cornerRadius), Offset(0f, size.height - len), w, StrokeCap.Round)
                    drawArc(
                        c, 90f, 90f, false, Offset(0f, size.height - cornerRadius * 2f),
                        Size(cornerRadius * 2f, cornerRadius * 2f), style = borderStroke,
                    )
                    drawLine(c, Offset(size.width - len, size.height), Offset(size.width - cornerRadius, size.height), w, StrokeCap.Round)
                    drawLine(c, Offset(size.width, size.height - cornerRadius), Offset(size.width, size.height - len), w, StrokeCap.Round)
                    drawArc(
                        c, 0f, 90f, false, Offset(size.width - cornerRadius * 2f, size.height - cornerRadius * 2f),
                        Size(cornerRadius * 2f, cornerRadius * 2f), style = borderStroke,
                    )
                    val viewfinderPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                Rect(0f, 0f, size.width, size.height),
                                CornerRadius(cornerRadius, cornerRadius),
                            ),
                        )
                    }
                    clipPath(viewfinderPath) {
                        val scanY = size.height * scanLineProgress
                        val scanBlue = Color(0xFF2879FA)
                        // The blue sweep carries a translucent gradient through the recognition area.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    scanBlue.copy(alpha = 0.42f),
                                    scanBlue.copy(alpha = 0.16f),
                                    Color.Transparent,
                                ),
                                startY = scanY,
                                endY = size.height,
                            ),
                            topLeft = Offset(0f, scanY),
                            size = Size(size.width, size.height - scanY),
                        )
                        drawLine(
                            scanBlue.copy(alpha = 0.5f),
                            Offset(0f, scanY),
                            Offset(size.width, scanY),
                            12f,
                            StrokeCap.Round,
                        )
                        drawLine(scanBlue, Offset(0f, scanY), Offset(size.width, scanY), 3f, StrokeCap.Round)
                        // A fixed grid appears only after the upward sweep has passed each dot.
                        val sweepSpeed = size.height * 0.76f / 1.5f
                        repeat(6) { row ->
                            val y = size.height * ((row + 1f) / 7f)
                            repeat(7) { column ->
                                val x = size.width * ((column + 1f) / 8f)
                                if (scanMovesUpward && y >= scanY) {
                                    val secondsSincePass = (y - scanY) / sweepSpeed
                                    val visibility = when {
                                        secondsSincePass <= 0.25f -> 1f
                                        else -> (1f - (secondsSincePass - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                    }
                                    if (visibility > 0f) {
                                        val dotAge = (secondsSincePass / 1f).coerceIn(0f, 1f)
                                        val dotRadius = 7.5f - dotAge * 4f
                                        val dotCenter = Offset(x, y)
                                        val dotAlpha = 0.68f * visibility
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = dotAlpha),
                                                    Color.White.copy(alpha = dotAlpha * 0.35f),
                                                    Color.Transparent,
                                                ),
                                                center = dotCenter,
                                                radius = dotRadius,
                                            ),
                                            radius = dotRadius,
                                            center = dotCenter,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isTableIdCapture) {
                    Text(
                        stringResource(R.string.scan_table_id_title),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // The three scanner actions become a compact floating control strip.
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(28.dp))
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ScannerControl(R.drawable.ic_fig_gallery, stringResource(R.string.cd_gallery)) {
                                Logger.d("Click Gallery @ Scanner")
                                galleryLauncher.launch("image/*")
                            }
                            ScannerControl(
                                R.drawable.ic_fig_batch,
                                stringResource(R.string.cd_batch),
                                tint = if (mainVm.batchMode) {
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                },
                            ) {
                                val next = !mainVm.batchMode
                                Logger.d("Click Batch toggle @ Scanner", "enabled=$next")
                                mainVm.setBatchScanning(next)
                                Toast.makeText(
                                    context,
                                    if (next) R.string.batch_on else R.string.batch_off,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            ScannerControl(
                                R.drawable.ic_fig_flash,
                                stringResource(if (torchOn) R.string.scanner_flash_off else R.string.scanner_flash_on),
                                tint = if (torchOn) {
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                },
                            ) {
                                torchOn = !torchOn
                                Logger.d("Click Flash @ Scanner", "on=$torchOn")
                            }
                            ScannerZoomControl(zoomRatio) {
                                zoomStepIndex = (zoomStepIndex + 1) % zoomSteps.size
                                Logger.d("Adjust Zoom @ Scanner", "ratio=${zoomSteps[zoomStepIndex]}x")
                            }
                            if (mainVm.activeServerSessionBarcode != null) {
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        Logger.d("Click Stop server session @ Scanner")
                                        mainVm.stopServerSession()
                                    },
                                    enabled = !mainVm.serverSessionStopState.isStopping,
                                    modifier = Modifier.size(52.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.StopCircle,
                                        contentDescription = stringResource(R.string.batch_stop_server_session),
                                        tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isTableIdCapture && mainVm.batchMode && mainVm.batchItems.isNotEmpty()) {
                    val sentCount = mainVm.batchItems.count {
                        mainVm.batchServerDelivery[it.rawValue] == ServerDeliveryState.SUCCEEDED
                    }
                    val failedCount = mainVm.batchItems.count {
                        mainVm.batchServerDelivery[it.rawValue] == ServerDeliveryState.FAILED
                    }
                    val pendingCount = mainVm.batchItems.size - sentCount - failedCount
                    Card(onClick = {
                        Logger.d("Click Batch card @ Scanner", "count=${mainVm.batchItems.size}")
                        onOpenBatch()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.scanner_batch_count, mainVm.batchItems.size))
                            Row(
                                Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    stringResource(R.string.scanner_batch_sent, sentCount),
                                    color = BatchSuccessGreen,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    stringResource(R.string.scanner_batch_pending, pendingCount),
                                    color = BatchPendingYellow,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    stringResource(R.string.scanner_batch_failed, failedCount),
                                    color = BatchFailureRed,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }

            }

            if (showGuide) {
                LaunchedEffect(Unit) { Logger.d("Scan-guide sheet shown") }
                ModalBottomSheet(onDismissRequest = {
                    Logger.d("Dismiss Scan-guide @ Scanner")
                    guideDismissed = true
                    mainVm.markScanGuideSeen()
                }) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            stringResource(R.string.scan_guide_title),
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        )
                        // Numbered steps, in the reference order (1 = clarity, 2 = hold steady).
                        GuideStep(1, stringResource(R.string.scan_guide_step2))
                        GuideStep(2, stringResource(R.string.scan_guide_step1))
                        Button(
                            onClick = {
                                Logger.d("Click Got It @ Scan-guide")
                                guideDismissed = true
                                mainVm.markScanGuideSeen()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) { Text(stringResource(R.string.got_it)) }
                    }
                }
            }
        } else {
            CameraPermissionOverlay(
                onAllow = {
                    Logger.d("Click Allow @ Camera permission overlay", "permanentlyDenied=$permanentlyDenied")
                    if (permanentlyDenied) showSettingsDialog = true
                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
            )
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = {
                Logger.d("Dismiss Permission dialog @ Scanner")
                showSettingsDialog = false
            },
            title = { Text(stringResource(R.string.scan_permission_settings_title)) },
            text = { Text(stringResource(R.string.scan_permission_settings_body)) },
            confirmButton = {
                TextButton(onClick = {
                    Logger.d("Click Open Settings @ Permission dialog")
                    showSettingsDialog = false
                    context.openAppSettings()
                }) { Text(stringResource(R.string.scan_permission_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Logger.d("Click Cancel @ Permission dialog")
                    showSettingsDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private val BatchSuccessGreen = Color(0xFF2E7D32)
private val BatchPendingYellow = Color(0xFFF9A825)
private val BatchFailureRed = Color(0xFFB3261E)

/** One numbered scan-guide step: a small circled index + the step text. */
@Composable
private fun GuideStep(index: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .background(Color(0x142879FA), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index.toString(),
                color = Color(0xFF2879FA),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Uses the device vibrator directly so the Settings "Vibration" switch affects every scan. */
@Suppress("DEPRECATION")
private fun Context.vibrateOnScan(): Boolean = runCatching {
    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return false
    if (!vibrator.hasVibrator()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(90L)
    }
    true
}.getOrDefault(false)

/** Plays one short barcode-scanner beep beyond the Scanner composition. */
private fun Context.playScanTone(): Boolean = runCatching {
    val player = MediaPlayer.create(this, R.raw.scan_success) ?: return@runCatching false
    player.setVolume(1f, 1f)
    player.setOnCompletionListener { completedPlayer -> completedPlayer.release() }
    player.start()
    true
}.getOrDefault(false)

/** One compact action inside the floating scanner control strip. */
@Composable
private fun ScannerControl(
    @androidx.annotation.DrawableRes icon: Int,
    contentDescription: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(
            painterResource(icon),
            contentDescription,
            Modifier.size(22.dp),
            tint = tint,
        )
    }
}

@Composable
private fun ScannerZoomControl(zoomRatio: Float, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Text("${zoomRatio.toInt()}x", color = Color.White)
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    analyzer: BarcodeAnalyzer,
    cameraSelector: CameraSelector,
    onCameraReady: (Camera) -> Unit,
) {
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                runCatching {
                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, analysis,
                    )
                    onCameraReady(cam)
                    Logger.d("Enter Scanner", "camera bound")
                }.onFailure { Logger.e("Camera bind failed", throwable = it) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
