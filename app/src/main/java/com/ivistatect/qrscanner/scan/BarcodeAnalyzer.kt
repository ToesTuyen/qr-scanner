package com.ivistatect.qrscanner.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ivistatect.qrscanner.domain.DecodedCode
import com.ivistatect.qrscanner.util.Logger

/** MLKit numeric format → readable name (matches ZXing BarcodeFormat names where possible). */
fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    Barcode.FORMAT_PDF417 -> "PDF_417"
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_CODE_93 -> "CODE_93"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    Barcode.FORMAT_ITF -> "ITF"
    else -> "UNKNOWN"
}

/**
 * CameraX frame analyzer that runs MLKit barcode-scanning (bundled models — no .tflite copied).
 * On the first decode it invokes [onDecoded] once and stops reporting until [reset].
 */
class BarcodeAnalyzer(private val onDecoded: (DecodedCode) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    @Volatile private var handled = false
    @Volatile private var outsideFrameLogged = false

    fun reset() {
        handled = false
        outsideFrameLogged = false
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || handled) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                if (!handled) {
                    barcodes.firstOrNull { barcode ->
                        !barcode.rawValue.isNullOrEmpty() && barcode.isInsideScannerFrame(input)
                    }?.let { bc ->
                        handled = true
                        onDecoded(DecodedCode.of(bc.rawValue!!, formatName(bc.format)))
                    } ?: run {
                        if (!outsideFrameLogged && barcodes.any { !it.rawValue.isNullOrEmpty() }) {
                            outsideFrameLogged = true
                            Logger.d("Scanner: ignored code outside frame")
                        }
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
}

/**
 * The viewfinder occupies x=8–92% and y=24–64% of the upright camera frame.
 * Matching by the decoded bounding-box centre prevents codes outside that on-screen frame
 * from navigating to a result while allowing a code that fills most of the frame.
 */
private fun Barcode.isInsideScannerFrame(image: InputImage): Boolean {
    val bounds = boundingBox ?: return false
    val centreX = (bounds.left + bounds.right) / 2f
    val centreY = (bounds.top + bounds.bottom) / 2f
    return centreX in image.width * 0.08f..image.width * 0.92f &&
        centreY in image.height * 0.24f..image.height * 0.64f
}
}
