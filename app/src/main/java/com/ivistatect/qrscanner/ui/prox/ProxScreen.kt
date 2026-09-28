package com.ivistatect.qrscanner.ui.prox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vnnami.appkit.api.Logger
import com.ivistatect.qrscanner.R

private val GreenBadge = Color(0xFF23C265)

/**
 * Native ProX-style paywall (n_prox, EVIDENCED). Own layout/assets. The CTA reaches the billing
 * boundary only — real purchase is external/GATED (n_billing) and out of authorization.
 */
@Composable
fun ProxScreen(onClose: () -> Unit, onStartTrial: () -> Unit) {
    LaunchedEffect(Unit) { Logger.d("Enter ProX paywall") }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Close X — top-LEFT, matching the reference.
        IconButton(
            onClick = { Logger.d("Click Close @ ProX"); onClose() },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) { Image(painterResource(R.drawable.ic_prox_close), stringResource(R.string.cd_close), Modifier.size(28.dp)) }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            HeroBadge()

            Text(
                stringResource(R.string.prox_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                stringResource(R.string.prox_price_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Column(
                Modifier.fillMaxWidth().padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf(
                    R.string.prox_feature_1, R.string.prox_feature_2,
                    R.string.prox_feature_3, R.string.prox_feature_4,
                ).forEach { res ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_prox_check), null, Modifier.size(22.dp))
                        Text(stringResource(res), Modifier.padding(start = 12.dp))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { Logger.d("Click Start Trial @ ProX (billing boundary; external/gated)"); onStartTrial() },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(stringResource(R.string.prox_cta)) }
            Text(
                stringResource(R.string.prox_auto_renew),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

/** Central green check badge ringed by 6 feature glyphs (reference hero). */
@Composable
private fun HeroBadge() {
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        // Ring icons: person / wifi (top), location / email (mid), chat / globe (bottom).
        RingIcon(R.drawable.ic_create_tile_contact, (-70).dp, (-78).dp)
        RingIcon(R.drawable.ic_create_tile_wifi, 70.dp, (-78).dp)
        RingIcon(R.drawable.ic_create_tile_location, (-98).dp, 4.dp)
        RingIcon(R.drawable.ic_create_tile_email, 98.dp, 4.dp)
        RingIcon(R.drawable.ic_create_tile_message, (-70).dp, 82.dp)
        RingIcon(R.drawable.ic_create_tile_url, 70.dp, 82.dp)

        Box(
            Modifier.size(120.dp).background(GreenBadge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun RingIcon(icon: Int, dx: androidx.compose.ui.unit.Dp, dy: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.offset(x = dx, y = dy).size(56.dp).background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
    }
}
