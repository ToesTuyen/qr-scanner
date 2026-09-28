package com.ivistatect.qrscanner.scan

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Save-to-gallery (MediaStore) and share-via-FileProvider for generated code bitmaps. App-owned. */
object ImageSaver {

    /** Save [bitmap] into Pictures/Barcode Scanner. Returns true on success. API 33+ needs no permission. */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean = runCatching {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Barcode Scanner")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val saved = MediaStore.Images.Media.insertImage(resolver, bitmap, displayName, "")
            saved != null
        }
    }.getOrDefault(false)

    /** Write [bitmap] to the shared cache and return a FileProvider uri for an ACTION_SEND chooser. */
    fun shareUri(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "barcode_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}
