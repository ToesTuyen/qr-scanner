package com.ivistatect.qrscanner.ui.result

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vnnami.appkit.api.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.domain.DecodedCode
import com.ivistatect.qrscanner.ui.MainViewModel

/**
 * Batch list. n_batch_result is GATED (needs ≥2 physical codes); the list is populated only from
 * live in-memory accumulation — no fabricated data behind the gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchResultScreen(
    mainVm: MainViewModel,
    onOpenItem: (DecodedCode) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch (${mainVm.batchItems.size})") },
                navigationIcon = {
                    IconButton(onClick = { Logger.d("Click Back @ Batch"); onBack() }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back), Modifier.size(24.dp))
                    }
                },
            )
        },
    ) { padding ->
        if (mainVm.batchItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Scan multiple codes in batch mode")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                items(mainVm.batchItems) { code ->
                    Card(
                        onClick = { Logger.d("Click Batch item @ Batch"); onOpenItem(code) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        Text(code.display, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
