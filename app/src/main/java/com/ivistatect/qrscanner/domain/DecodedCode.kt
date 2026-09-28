package com.ivistatect.qrscanner.domain

import androidx.annotation.DrawableRes
import com.ivistatect.qrscanner.R

/** Result of a decode (camera / gallery / share-in) or a lookup from history. App-owned model. */
data class DecodedCode(
    val rawValue: String,
    val formatName: String,   // "QR_CODE", "CODE_128", …
    val valueType: ScanValueType,
    val display: String = rawValue,
) {
    companion object {
        /** Build from a raw string, inferring the value type. Used for gallery/created/history. */
        fun of(rawValue: String, formatName: String): DecodedCode =
            DecodedCode(rawValue, formatName, ScanValueType.infer(rawValue))
    }
}

enum class ScanValueType {
    URL, TEXT, PHONE, SMS, EMAIL, WIFI, GEO, CONTACT;

    /** Actions the reference's result grid offers for this value type. */
    fun actions(showProductDetails: Boolean = false, formatName: String = ""): List<ResultAction> = when (this) {
        URL -> listOf(ResultAction.OPEN, ResultAction.SHARE, ResultAction.COPY)
        PHONE -> listOf(ResultAction.CALL, ResultAction.COPY, ResultAction.SHARE)
        SMS -> listOf(ResultAction.SMS, ResultAction.COPY, ResultAction.SHARE)
        EMAIL -> listOf(ResultAction.EMAIL, ResultAction.COPY, ResultAction.SHARE)
        WIFI -> listOf(ResultAction.WIFI, ResultAction.COPY, ResultAction.SHARE)
        GEO -> listOf(ResultAction.MAP, ResultAction.COPY, ResultAction.SHARE)
        CONTACT -> listOf(ResultAction.CONTACT, ResultAction.COPY, ResultAction.SHARE)
        TEXT -> buildList {
            if (showProductDetails && formatName.isProductBarcodeFormat()) {
                add(ResultAction.PRODUCT_DETAILS)
            }
            add(ResultAction.WEB_SEARCH)
            add(ResultAction.COPY)
            add(ResultAction.SHARE)
        }
    }

    companion object {
        fun infer(raw: String): ScanValueType {
            val v = raw.trim()
            val lower = v.lowercase()
            return when {
                lower.startsWith("http://") || lower.startsWith("https://") -> URL
                lower.startsWith("tel:") -> PHONE
                lower.startsWith("smsto:") || lower.startsWith("sms:") -> SMS
                lower.startsWith("mailto:") || lower.startsWith("matmsg:") -> EMAIL
                lower.startsWith("wifi:") -> WIFI
                lower.startsWith("geo:") -> GEO
                lower.startsWith("mecard:") || lower.startsWith("begin:vcard") -> CONTACT
                else -> TEXT
            }
        }
    }
}

enum class ResultAction { OPEN, WEB_SEARCH, PRODUCT_DETAILS, CALL, SMS, EMAIL, WIFI, MAP, CONTACT, COPY, SHARE }

private fun String.isProductBarcodeFormat(): Boolean = this in setOf(
    "EAN_13", "EAN_8", "UPC_A", "UPC_E", "CODE_128", "CODE_93", "CODE_39", "ITF", "CODABAR",
)

/** Real reference tile glyph for a value type (reused by the result / created / history screens). */
@DrawableRes
fun ScanValueType.tileGlyph(): Int = when (this) {
    ScanValueType.URL -> R.drawable.ic_create_tile_url
    ScanValueType.TEXT -> R.drawable.ic_create_tile_text
    ScanValueType.PHONE -> R.drawable.ic_create_tile_phone
    ScanValueType.SMS -> R.drawable.ic_create_tile_message
    ScanValueType.EMAIL -> R.drawable.ic_create_tile_email
    ScanValueType.WIFI -> R.drawable.ic_create_tile_wifi
    ScanValueType.GEO -> R.drawable.ic_create_tile_location
    ScanValueType.CONTACT -> R.drawable.ic_create_tile_contact
}
