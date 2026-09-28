package com.ivistatect.qrscanner.ui.create

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.domain.CreateCatalog
import com.ivistatect.qrscanner.domain.CreateTile

@Composable
fun CreateScreen(onPickTile: (String) -> Unit) {
    LaunchedEffect(Unit) { Logger.d("Enter Create") }
    var barcodesExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.create_header),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        Section(title = stringResource(R.string.create_qr_title)) {
            TileGrid(CreateCatalog.qrTiles, onPickTile)
        }
        Section(title = stringResource(R.string.create_social_title)) {
            TileGrid(CreateCatalog.socialTiles, onPickTile)
        }

        // Expandable Barcodes card (cardBarcode).
        Card(
            onClick = {
                barcodesExpanded = !barcodesExpanded
                Logger.d("Click Barcodes card @ Create", "expanded=$barcodesExpanded")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.create_barcode_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                // Count badge pill (reference).
                Box(
                    Modifier.padding(start = 10.dp)
                        .background(Color(0x142879FA), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${CreateCatalog.barcodeTiles.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF2879FA),
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.iv_drop_down_barcode),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).rotate(if (barcodesExpanded) 180f else 0f),
                )
            }
        }
        if (barcodesExpanded) {
            TileGrid(CreateCatalog.barcodeTiles, onPickTile)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Fixed 4-column grid (matches the reference Create tab). Rows are padded with empty cells. */
@Composable
private fun TileGrid(tiles: List<CreateTile>, onPickTile: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    TileCell(tile, Modifier.weight(1f)) { onPickTile(tile.id) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileCell(tile: CreateTile, modifier: Modifier, onClick: () -> Unit) {
    val label = tile.labelRes?.let { stringResource(it) } ?: tile.label
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = { Logger.d("Click Create tile @ Create", "id=${tile.id}"); onClick() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (tile.iconRes != null) {
                    // Real reference glyph — preserve its own (blue) colour.
                    Image(
                        painterResource(tile.iconRes),
                        contentDescription = label,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    // Social tile: generic glyph (brand trademark excluded — see CreateModels KDoc).
                    Icon(
                        tile.iconVec!!,
                        contentDescription = label,
                        tint = Color(0xFF2879FA),
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
        )
    }
}
