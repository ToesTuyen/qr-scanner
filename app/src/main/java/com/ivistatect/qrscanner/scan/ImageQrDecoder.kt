package com.ivistatect.qrscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.ivistatect.qrscanner.domain.DecodedCode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Decodes a picked / shared image into a code via MLKit. App-owned; caps decode at ~1080px. */
object ImageQrDecoder {

    suspend fun decode(context: Context, uri: Uri): DecodedCode? {
        val bitmap = loadBitmap(context, uri) ?: return null
        val input = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()
        return suspendCancellableCoroutine { cont ->
            scanner.process(input)
                .addOnSuccessListener { list ->
                    val bc = list.firstOrNull { !it.rawValue.isNullOrEmpty() }
                    cont.resume(bc?.let { DecodedCode.of(it.rawValue!!, formatName(it.format)) })
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width
            val h = info.size.height
            val max = maxOf(w, h)
            if (max > 1080) {
                val scale = 1080f / max
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }
    }.getOrNull()
}
