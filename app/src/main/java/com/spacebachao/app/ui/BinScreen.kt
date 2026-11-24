package com.spacebachao.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.UiState
import com.spacebachao.app.data.BinEntry
import com.spacebachao.app.readableSize
import com.spacebachao.app.ui.theme.LocalApple

@Composable
fun BinScreen(state: UiState, vm: AppViewModel, onBack: () -> Unit) {
    val p = LocalApple.current
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<BinEntry?>(null) }
    val keepDays = if (state.effectiveBinDays > 0) state.effectiveBinDays else 7
    val insets = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding() + 4.dp,
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { LargeTitle("Bin", onBack = onBack) }

        if (state.binEntries.isEmpty()) {
            item {
                GlassCard {
                    Text("The Bin is empty", style = MaterialTheme.typography.titleMedium, color = p.label)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Originals appear here after you compress in Replace mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.secondary,
                    )
                }
            }
        } else {
            item {
                GlassCard(strong = true, shape = RoundedCornerShape(30.dp), padding = PaddingValues(22.dp)) {
                    Text(state.binSize.readableSize(), style = MaterialTheme.typography.displayMedium, color = p.label)
                    Text(
                        "${state.binEntries.size} originals waiting",
                        style = MaterialTheme.typography.titleMedium,
                        color = p.secondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deleted automatically after $keepDays days. Not happy with a file? Tap restore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.secondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    GlossyButton(
                        "Empty Bin · free ${state.binSize.readableSize()}",
                        { confirmAll = true },
                        Modifier.fillMaxWidth(),
                        colors = listOf(p.orange, p.red),
                    )
                }
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
    val p = LocalApple.current
    val ageDays = ((System.currentTimeMillis() - entry.createdAt) / (24L * 60 * 60 * 1000)).toInt()
    val left = (keepDays - ageDays).coerceAtLeast(0)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(p, RoundedCornerShape(20.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(entry.kind), contentDescription = null, tint = p.accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = p.label,
            )
            Text(
                "${entry.originalSize.readableSize()} → ${entry.compressedSize.readableSize()} · $left days left",
                style = MaterialTheme.typography.bodySmall,
                color = p.secondary,
            )
        }
        Icon(
            Icons.Filled.Restore,
            contentDescription = "Restore",
            tint = p.accent,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRestore)
                .padding(8.dp)
                .size(22.dp),
        )
        Icon(
            Icons.Filled.DeleteForever,
            contentDescription = "Delete",
            tint = p.red,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDelete)
                .padding(8.dp)
                .size(22.dp),
        )
    }
}
