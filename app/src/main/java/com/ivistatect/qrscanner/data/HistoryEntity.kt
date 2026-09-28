package com.ivistatect.qrscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One scanned or created code. `origin` separates the History tabs; `isFavorite` feeds the 3rd tab. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawValue: String,
    val displayContent: String,
    val format: String,      // ZXing/MLKit format name, e.g. "QR_CODE", "CODE_128"
    val valueType: String,   // parsed value type, e.g. "URL", "TEXT", "PHONE"
    val origin: String,      // ORIGIN_SCANNED or ORIGIN_CREATED
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ORIGIN_SCANNED = "SCANNED"
        const val ORIGIN_CREATED = "CREATED"
    }
}
