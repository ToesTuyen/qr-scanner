package com.ivistatect.qrscanner.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ivistatect.qrscanner.domain.DecodedCode

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

    fun reset() { handled = false }

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
                    barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }?.let { bc ->
                        handled = true
                        onDecoded(DecodedCode.of(bc.rawValue!!, formatName(bc.format)))
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
