package com.ivistatect.qrscanner.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vnnami.appkit.api.Logger
import com.ivistatect.qrscanner.R

/**
 * In-app camera-permission overlay (n_cam_denied / initial). Models: capability unavailable →
 * explain → request (or route to Settings when permanently denied). Native Compose; the camera
 * glyph is the real reference vector (ic_camera_permission). The overlay sits on the dark scanner
 * surface (reference parity) so it renders on a near-black background with light text.
 */
@Composable
fun CameraPermissionOverlay(onAllow: () -> Unit) {
    Logger.d("Enter Camera permission overlay")
    Box(Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.ic_camera_permission),
                contentDescription = null,
                modifier = Modifier.size(94.dp),
            )
            Text(
                stringResource(R.string.scan_permission_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.scan_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB8BCC4),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onAllow, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.scan_permission_allow))
            }
        }
    }
}
