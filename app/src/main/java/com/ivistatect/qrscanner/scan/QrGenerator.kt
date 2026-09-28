package com.ivistatect.qrscanner.scan

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/** App-owned QR/barcode bitmap generator (ZXing). Content is regenerated locally — no reference art. */
object QrGenerator {

    private val TWO_D = setOf(
        BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417,
    )

    /** Encode [content] as [format]; returns null when the content is invalid for that symbology. */
    fun encode(content: String, format: BarcodeFormat, size: Int = 800): Bitmap? = runCatching {
        val height = if (format in TWO_D) size else (size / 3).coerceAtLeast(160)
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = MultiFormatWriter().encode(content, format, size, height, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.getOrNull()
}
