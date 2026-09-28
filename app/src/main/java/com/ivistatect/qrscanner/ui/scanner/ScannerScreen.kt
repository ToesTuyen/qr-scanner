package com.ivistatect.qrscanner.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticView = LocalView.current
    val scope = rememberCoroutineScope()

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
    val scanTone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }
    DisposableEffect(scanTone) { onDispose { scanTone.release() } }
    LaunchedEffect(settings.batchScanning) { mainVm.syncBatchMode(settings.batchScanning) }
    var guideDismissed by remember { mutableStateOf(false) }
    val showGuide = !guideSeen && !guideDismissed
    var torchOn by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val analyzerHolder = remember { arrayOfNulls<BarcodeAnalyzer>(1) }
    val analyzer = remember {
        BarcodeAnalyzer { code ->
            val scanSettings = currentSettings.value
            val hapticPlayed = if (scanSettings.vibration) {
                hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                false
            }
            if (scanSettings.sound) scanTone.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
            Logger.d("Scan feedback", "vibration=$hapticPlayed sound=${scanSettings.sound}")
            Logger.d("Scanner: decoded", "type=${code.valueType} format=${code.formatName}")
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
                    mainVm.onDecoded(decoded)
                    if (!mainVm.batchMode) onResult()
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
            LaunchedEffect(camera, zoom) { camera?.cameraControl?.setLinearZoom(zoom) }

            // Blue scan reticle (viewfinder) — a camera framing guide, upper-centre.
            Box(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(top = 96.dp, start = 32.dp, end = 32.dp),
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
                    val c = Color(0xFF2879FA)
                    val len = size.minDimension * 0.16f
                    val w = 6f
                    // 4 L-shaped corner brackets.
                    drawLine(c, Offset(0f, 0f), Offset(len, 0f), w, StrokeCap.Round)
                    drawLine(c, Offset(0f, 0f), Offset(0f, len), w, StrokeCap.Round)
                    drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), w, StrokeCap.Round)
                    drawLine(c, Offset(size.width, 0f), Offset(size.width, len), w, StrokeCap.Round)
                    drawLine(c, Offset(0f, size.height), Offset(len, size.height), w, StrokeCap.Round)
                    drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), w, StrokeCap.Round)
                    drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), w, StrokeCap.Round)
                    drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), w, StrokeCap.Round)
                    // Horizontal scan line.
                    drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f)
                }
            }

            // Bottom controls: Gallery / Batch / Flash row, then the zoom slider.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (mainVm.batchMode && mainVm.batchItems.isNotEmpty()) {
                    Card(onClick = {
                        Logger.d("Click Batch card @ Scanner", "count=${mainVm.batchItems.size}")
                        onOpenBatch()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.scanner_batch_count, mainVm.batchItems.size),
                            Modifier.padding(16.dp),
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // Plain white icons (no circular button background) — matches the reference.
                    ControlItem(
                        label = stringResource(R.string.scanner_gallery),
                        icon = R.drawable.ic_fig_gallery,
                        contentDescription = stringResource(R.string.cd_gallery),
                    ) {
                        Logger.d("Click Gallery @ Scanner")
                        galleryLauncher.launch("image/*")
                    }
                    ControlItem(
                        label = stringResource(R.string.scanner_batch),
                        icon = R.drawable.ic_fig_batch,
                        contentDescription = stringResource(R.string.cd_batch),
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
                    ControlItem(
                        label = stringResource(if (torchOn) R.string.scanner_flash_off else R.string.scanner_flash_on),
                        icon = R.drawable.ic_fig_flash,
                        contentDescription = stringResource(R.string.cd_flash),
                    ) {
                        torchOn = !torchOn
                        Logger.d("Click Flash @ Scanner", "on=$torchOn")
                    }
                }

                // Zoom slider: [-]  ====O====  [+]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_fig_zoom_minus),
                        stringResource(R.string.cd_zoom_out),
                        Modifier.size(24.dp),
                        tint = Color.White,
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        onValueChangeFinished = { Logger.d("Adjust Zoom @ Scanner", "value=$zoom") },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Icon(
                        painterResource(R.drawable.ic_fig_zoom_plus),
                        stringResource(R.string.cd_zoom_in),
                        Modifier.size(24.dp),
                        tint = Color.White,
                    )
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

/** A scanner control = plain white icon (no button background) with a white caption below it. */
@Composable
private fun ControlItem(
    label: String,
    @androidx.annotation.DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(icon),
                contentDescription,
                Modifier.size(28.dp),
                tint = Color.White,
            )
        }
        Text(
            label,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
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
