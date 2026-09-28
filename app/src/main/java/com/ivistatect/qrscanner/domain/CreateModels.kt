package com.ivistatect.qrscanner.domain

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.zxing.BarcodeFormat
import com.ivistatect.qrscanner.R

enum class CreateCategory { QR, SOCIAL, BARCODE }

/** How the Create form collects input for a tile. */
enum class FormKind { SINGLE, WIFI, CONTACT }

/**
 * A Create-tab tile.
 *
 * QR + Barcode tiles bind a REAL reference drawable ([iconRes], provenance in
 * `docs/ASSET_MIRROR.md`). The 9 Social tiles carry only a generic [iconVec] glyph — the reference's
 * own ic_social_* art is a third-party brand trademark and is excluded from verbatim reuse per
 * `reconstruction/step4/CURRENT_ASSET_MANIFEST.json` (bundled_social_logos). Exactly one of the two
 * icon fields is non-null.
 */
data class CreateTile(
    val id: String,
    val label: String,
    val category: CreateCategory,
    /** Localised label for QR tiles (null = use [label] verbatim, e.g. social/barcode proper names). */
    @StringRes val labelRes: Int? = null,
    @DrawableRes val iconRes: Int? = null,
    val iconVec: ImageVector? = null,
    val kind: FormKind = FormKind.SINGLE,
    val hint: String = "Enter content",
    val prefix: String = "",
    val format: BarcodeFormat = BarcodeFormat.QR_CODE,
)

/** The Create tab catalog. Social tiles use GENERIC glyphs — third-party brand logos are excluded. */
object CreateCatalog {

    // Order matches the reference Create tab QR grid exactly (Clipboard, URL, Wi-Fi, Text, …).
    val qrTiles: List<CreateTile> = listOf(
        CreateTile("clipboard", "Clipboard", CreateCategory.QR, labelRes = R.string.create_tile_clipboard, iconRes = R.drawable.ic_create_tile_clipboard, hint = "Clipboard text"),
        CreateTile("url", "URL", CreateCategory.QR, labelRes = R.string.create_tile_url, iconRes = R.drawable.ic_create_tile_url, hint = "https://example.com", prefix = "https://"),
        CreateTile("wifi", "Wi-Fi", CreateCategory.QR, labelRes = R.string.create_tile_wifi, iconRes = R.drawable.ic_create_tile_wifi, kind = FormKind.WIFI),
        CreateTile("text", "Text", CreateCategory.QR, labelRes = R.string.create_tile_text, iconRes = R.drawable.ic_create_tile_text, hint = "Plain text"),
        CreateTile("contact", "Contacts", CreateCategory.QR, labelRes = R.string.create_tile_contact, iconRes = R.drawable.ic_create_tile_contact, kind = FormKind.CONTACT),
        CreateTile("phone", "Phone", CreateCategory.QR, labelRes = R.string.create_tile_phone, iconRes = R.drawable.ic_create_tile_phone, hint = "Phone number", prefix = "tel:"),
        CreateTile("email", "Email", CreateCategory.QR, labelRes = R.string.create_tile_email, iconRes = R.drawable.ic_create_tile_email, hint = "name@example.com", prefix = "mailto:"),
        CreateTile("sms", "SMS", CreateCategory.QR, labelRes = R.string.create_tile_sms, iconRes = R.drawable.ic_create_tile_message, hint = "Phone number", prefix = "smsto:"),
        CreateTile("calendar", "Calendar", CreateCategory.QR, labelRes = R.string.create_tile_calendar, iconRes = R.drawable.ic_create_tile_calendar, hint = "Event text"),
        CreateTile("geo", "Location", CreateCategory.QR, labelRes = R.string.create_tile_geo, iconRes = R.drawable.ic_create_tile_location, hint = "lat,long", prefix = "geo:"),
        CreateTile("myqr", "My QR", CreateCategory.QR, labelRes = R.string.create_tile_myqr, iconRes = R.drawable.ic_create_tile_my_qr, kind = FormKind.CONTACT),
        CreateTile("apps", "Apps", CreateCategory.QR, labelRes = R.string.create_tile_apps, iconRes = R.drawable.ic_create_tile_apps, hint = "App / Play URL"),
    )

    // Order + brand-tinted line icons match the reference Social grid (TikTok absent → 9 tiles).
    // Icons are the reference's own single-icon vector drawables (provenance in docs/ASSET_MIRROR.md).
    val socialTiles: List<CreateTile> = listOf(
        CreateTile("facebook", "Facebook", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_facebook, hint = "username", prefix = "https://facebook.com/"),
        CreateTile("instagram", "Instagram", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_instagram, hint = "@handle", prefix = "https://instagram.com/"),
        CreateTile("whatsapp", "WhatsApp", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_whatsapp, hint = "phone", prefix = "https://wa.me/"),
        CreateTile("youtube", "YouTube", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_youtube, hint = "channel", prefix = "https://youtube.com/"),
        CreateTile("x", "X", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_x, hint = "@handle", prefix = "https://x.com/"),
        CreateTile("spotify", "Spotify", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_spotify, hint = "user id", prefix = "https://open.spotify.com/user/"),
        CreateTile("paypal", "PayPal", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_paypal, hint = "username", prefix = "https://paypal.me/"),
        CreateTile("viber", "Viber", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_viber, hint = "phone", prefix = "viber://chat?number="),
        CreateTile("bitcoin", "Bitcoin", CreateCategory.SOCIAL, iconRes = R.drawable.ic_social_bitcoin, hint = "address", prefix = "bitcoin:"),
    )

    val barcodeTiles: List<CreateTile> = listOf(
        barcode("product", "Product", BarcodeFormat.EAN_13, labelRes = R.string.bc_product),
        barcode("isbn", "ISBN", BarcodeFormat.EAN_13),
        barcode("pdf417", "PDF-417", BarcodeFormat.PDF_417, R.drawable.ic_barcode_pdf417),
        barcode("datamatrix", "Data-Matrix", BarcodeFormat.DATA_MATRIX, R.drawable.ic_barcode_data_matrix, labelRes = R.string.bc_datamatrix),
        barcode("aztec", "AZTEC", BarcodeFormat.AZTEC, R.drawable.ic_barcode_data_matrix),
        barcode("ean8", "EAN-8", BarcodeFormat.EAN_8),
        barcode("ean13", "EAN-13", BarcodeFormat.EAN_13),
        barcode("upce", "UPC-E", BarcodeFormat.UPC_E),
        barcode("upca", "UPC-A", BarcodeFormat.UPC_A),
        barcode("code93", "Code-93", BarcodeFormat.CODE_93, labelRes = R.string.bc_code93),
        barcode("code39", "Code-39", BarcodeFormat.CODE_39, labelRes = R.string.bc_code39),
        barcode("code128", "Code-128", BarcodeFormat.CODE_128, labelRes = R.string.bc_code128),
        barcode("itf", "ITF", BarcodeFormat.ITF),
        barcode("codabar", "CODABAR", BarcodeFormat.CODABAR),
    )

    private fun barcode(
        id: String,
        label: String,
        format: BarcodeFormat,
        @DrawableRes iconRes: Int = R.drawable.ic_barcode,
        @StringRes labelRes: Int? = null,
    ) = CreateTile(id, label, CreateCategory.BARCODE, labelRes = labelRes, iconRes = iconRes, hint = "Value", format = format)

    fun find(id: String): CreateTile? =
        (qrTiles + socialTiles + barcodeTiles).firstOrNull { it.id == id }
}

/** Build the encoded content string for a tile from the form input(s). Null = invalid input. */
fun CreateTile.buildContent(primary: String, secondary: String = ""): String? {
    val p = primary.trim()
    return when (kind) {
        FormKind.WIFI -> if (p.isEmpty()) null else "WIFI:T:WPA;S:$p;P:${secondary.trim()};;"
        FormKind.CONTACT -> if (p.isEmpty()) null else "MECARD:N:$p;TEL:${secondary.trim()};;"
        FormKind.SINGLE -> when {
            p.isEmpty() -> null
            id == "url" -> if (p.startsWith("http://") || p.startsWith("https://")) p else "https://$p"
            prefix.isNotEmpty() && !p.startsWith(prefix, ignoreCase = true) -> "$prefix$p"
            else -> p
        }
    }
}
