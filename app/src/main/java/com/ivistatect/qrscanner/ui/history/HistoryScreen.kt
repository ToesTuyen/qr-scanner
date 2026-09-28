package com.ivistatect.qrscanner.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivistatect.qrscanner.util.Logger
import com.ivistatect.qrscanner.R
import com.ivistatect.qrscanner.data.HistoryEntity
import com.ivistatect.qrscanner.domain.ScanValueType
import com.ivistatect.qrscanner.domain.tileGlyph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenItem: (HistoryEntity) -> Unit,
    onGoScan: () -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { Logger.d("Enter History") }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var dateFilter by remember { mutableStateOf(HistoryDateFilter.ALL) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var confirmDeleteVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HistoryEntity?>(null) }

    val scanned by vm.scanned.collectAsState()
    val favorites by vm.favorites.collectAsState()

    val tabs = listOf(
        stringResource(R.string.history_scanned),
        stringResource(R.string.history_favorites),
    )
    val emptyMsg = listOf(
        stringResource(R.string.history_empty_scanned),
        stringResource(R.string.history_empty_favorites),
    )
    val list = (if (tab == 0) scanned else favorites)
        .filter { query.isBlank() || it.displayContent.contains(query, ignoreCase = true) }
        .filter { it.matches(dateFilter) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding()) {
                // Header: title + search / filter / more.
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.nav_history),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = { Logger.d("Click Search @ History", "open=${!searchOpen}"); searchOpen = !searchOpen }) {
                        Icon(painterResource(R.drawable.ic_search_icon), stringResource(R.string.cd_search), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        Logger.d("Click Filter @ History")
                        showFilterSheet = true
                    }) { Icon(painterResource(R.drawable.iv_filter), stringResource(R.string.cd_filter), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = {
                        Logger.d("Click More @ History")
                        showMoreSheet = true
                    }) { Icon(painterResource(R.drawable.ic_three_dot), stringResource(R.string.cd_more), Modifier.size(24.dp)) }
                }

                TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = tab == i,
                            onClick = { Logger.d("Click History tab @ History", "tab=$title"); tab = i },
                            text = { Text(title) },
                        )
                    }
                }

                if (searchOpen) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.history_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painterResource(R.drawable.iv_empty),
                        contentDescription = null,
                        modifier = Modifier.size(160.dp),
                    )
                    Text(emptyMsg[tab], Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            Logger.d("Click Empty-state CTA @ History", "tab=$tab")
                            onGoScan()
                        },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.padding(top = 20.dp).fillMaxWidth(0.72f).height(56.dp),
                    ) {
                        Text(stringResource(R.string.history_cta_scan))
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                item(key = "today_header") {
                    Text(
                        stringResource(R.string.history_group_today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                list.forEach { item ->
                    item(key = item.id) {
                        HistoryRow(
                            item = item,
                            onOpen = { Logger.d("Click History item @ History", "id=${item.id}"); onOpenItem(item) },
                            onLongPress = { Logger.d("Long-press History item @ History", "id=${item.id}"); pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = {
                Logger.d("Dismiss Delete item dialog @ History")
                pendingDelete = null
            },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(item.displayContent) },
            confirmButton = {
                TextButton(onClick = {
                    Logger.d("Click Delete history item @ History", "id=${item.id}")
                    vm.delete(item.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Logger.d("Click Cancel @ Delete item dialog")
                    pendingDelete = null
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = {
            Logger.d("Dismiss Filter sheet @ History")
            showFilterSheet = false
        }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.history_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                HistoryFilterOption(stringResource(R.string.history_filter_all), dateFilter == HistoryDateFilter.ALL) {
                    Logger.d("Select Filter @ History", "value=all")
                    dateFilter = HistoryDateFilter.ALL
                    showFilterSheet = false
                }
                HistoryFilterOption(stringResource(R.string.history_filter_today), dateFilter == HistoryDateFilter.TODAY) {
                    Logger.d("Select Filter @ History", "value=today")
                    dateFilter = HistoryDateFilter.TODAY
                    showFilterSheet = false
                }
                HistoryFilterOption(stringResource(R.string.history_filter_last_7_days), dateFilter == HistoryDateFilter.LAST_7_DAYS) {
                    Logger.d("Select Filter @ History", "value=last_7_days")
                    dateFilter = HistoryDateFilter.LAST_7_DAYS
                    showFilterSheet = false
                }
                HistoryFilterOption(stringResource(R.string.history_filter_last_30_days), dateFilter == HistoryDateFilter.LAST_30_DAYS) {
                    Logger.d("Select Filter @ History", "value=last_30_days")
                    dateFilter = HistoryDateFilter.LAST_30_DAYS
                    showFilterSheet = false
                }
            }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = {
            Logger.d("Dismiss More sheet @ History")
            showMoreSheet = false
        }) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(stringResource(R.string.history_more_title), style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = {
                        Logger.d("Click Delete visible @ History", "count=${list.size}")
                        showMoreSheet = false
                        if (list.isNotEmpty()) confirmDeleteVisible = true
                    },
                    enabled = list.isNotEmpty(),
                ) { Text(stringResource(R.string.history_delete_visible)) }
            }
        }
    }

    if (confirmDeleteVisible) {
        AlertDialog(
            onDismissRequest = {
                Logger.d("Dismiss Delete visible dialog @ History")
                confirmDeleteVisible = false
            },
            title = { Text(stringResource(R.string.history_delete_visible)) },
            text = { Text(stringResource(R.string.history_delete_visible_message, list.size)) },
            confirmButton = {
                TextButton(onClick = {
                    Logger.d("Click Delete visible history @ History", "count=${list.size}")
                    vm.deleteAll(list.map { it.id })
                    confirmDeleteVisible = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Logger.d("Click Cancel @ Delete visible dialog")
                    confirmDeleteVisible = false
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private enum class HistoryDateFilter { ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS }

private fun HistoryEntity.matches(filter: HistoryDateFilter): Boolean {
    if (filter == HistoryDateFilter.ALL) return true
    val days = when (filter) {
        HistoryDateFilter.TODAY -> 0
        HistoryDateFilter.LAST_7_DAYS -> 6
        HistoryDateFilter.LAST_30_DAYS -> 29
        HistoryDateFilter.ALL -> return true
    }
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -days)
    }.timeInMillis
    return createdAt >= start
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryFilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(item: HistoryEntity, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val glyph = remember(item.valueType) {
        runCatching { ScanValueType.valueOf(item.valueType).tileGlyph() }.getOrDefault(R.drawable.ic_create_tile_text)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(glyph), contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.valueType, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                formatTime(item.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
