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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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

@Composable
fun BinScreen(state: UiState, vm: AppViewModel, onBack: () -> Unit) {
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<BinEntry?>(null) }
    val keepDays = if (state.effectiveBinDays > 0) state.effectiveBinDays else 7

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    Text("The Bin is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Originals appear here after you compress in Replace mode",
                        style = MaterialTheme.typography.bodyMedium,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            state.binSize.readableSize(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "${state.binEntries.size} originals waiting",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "They are deleted automatically after $keepDays days. Not happy with a file? Tap restore.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { confirmAll = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                        ) { Text("Empty Bin · free ${state.binSize.readableSize()}") }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            items(state.binEntries, key = { it.id }) { entry ->
                BinRow(entry, keepDays, onRestore = { vm.restore(entry) }, onDelete = { confirmOne = entry })
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            title = "Empty the Bin?",
            text = "All originals will be deleted forever and ${state.binSize.readableSize()} will be freed.",
            confirmLabel = "Delete all",
            onConfirm = { vm.emptyBin() },
            onDismiss = { confirmAll = false },
        )
    }
    confirmOne?.let { e ->
        ConfirmDialog(
            title = "Delete this original?",
            text = "The original of ${e.name} will be deleted forever. The compressed file stays in your gallery.",
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(kindIcon(entry.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${entry.originalSize.readableSize()} → ${entry.compressedSize.readableSize()} · $left days left",
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
