@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spacebachao.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.UiState
import com.spacebachao.app.data.BinEntry
import com.spacebachao.app.readableSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(state: UiState, vm: AppViewModel, onBack: () -> Unit) {
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<BinEntry?>(null) }
    val keepDays = if (state.binDays > 0) state.binDays else 7

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wapas")
                    }
                },
            )
        },
    ) { padding ->
        if (state.binEntries.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bin khali hai", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Compress ke baad originals yahan aate hain",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${state.binEntries.size} originals · ${state.binSize.readableSize()}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ye $keepDays din baad apne aap delete ho jayenge. Koi file pasand nahi aayi toh Restore dabao.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { confirmAll = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Sab delete karo · ${state.binSize.readableSize()} free karo") }
                    }
                }
            }
            items(state.binEntries, key = { it.id }) { entry ->
                BinRow(entry, keepDays, onRestore = { vm.restore(entry) }, onDelete = { confirmOne = entry })
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            title = "Bin khali karein?",
            text = "Saare originals hamesha ke liye delete ho jayenge aur ${state.binSize.readableSize()} free hoga.",
            confirmLabel = "Haan, delete karo",
            onConfirm = { vm.emptyBin() },
            onDismiss = { confirmAll = false },
        )
    }
    confirmOne?.let { e ->
        ConfirmDialog(
            title = "Original delete karein?",
            text = "${e.name} ka original hamesha ke liye delete hoga. Compressed file gallery me rahegi.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteFromBin(e) },
            onDismiss = { confirmOne = null },
        )
    }
}

@Composable
private fun BinRow(entry: BinEntry, keepDays: Int, onRestore: () -> Unit, onDelete: () -> Unit) {
    val ageDays = ((System.currentTimeMillis() - entry.createdAt) / (24L * 60 * 60 * 1000)).toInt()
    val left = (keepDays - ageDays).coerceAtLeast(0)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(kindIcon(entry.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Original ${entry.originalSize.readableSize()} · ab ${entry.compressedSize.readableSize()} · $left din baaki",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteForever, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
