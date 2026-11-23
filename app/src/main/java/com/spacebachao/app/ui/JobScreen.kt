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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebachao.app.JobState
import com.spacebachao.app.data.ItemStatus
import com.spacebachao.app.data.Kind
import com.spacebachao.app.data.Mode
import com.spacebachao.app.data.ResultItem
import com.spacebachao.app.readableSize
import com.spacebachao.app.ui.theme.ErrorRed
import com.spacebachao.app.ui.theme.SuccessGreen
import com.spacebachao.app.ui.theme.WarnAmber

@Composable
fun JobScreen(
    job: JobState,
    binDays: Int,
    binSize: Long,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onEmptyBin: () -> Unit,
) {
    // Keep the screen on while working
    val view = LocalView.current
    DisposableEffect(job.running) {
        view.keepScreenOn = job.running
        onDispose { view.keepScreenOn = false }
    }
    if (job.running) RunningView(job, onCancel) else ResultView(job, binDays, binSize, onDone, onEmptyBin)
}

@Composable
private fun CountPill(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RunningView(job: JobState, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Compressing ${job.currentKind.label.lowercase()} ${job.index + 1} of ${job.total}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(28.dp))
        Box(contentAlignment = Alignment.Center) {
            val p = job.currentProgress
            if (p >= 0f) {
                CircularProgressIndicator(
                    progress = { p },
                    modifier = Modifier.size(180.dp),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(180.dp),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    "${job.index + 1}/${job.total}",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            job.currentName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            CountPill("Done", "${job.doneCount}", Modifier.weight(1f))
            CountPill("Left", "${job.leftCount}", Modifier.weight(1f))
            CountPill("Saved", job.savedSoFar.readableSize(), Modifier.weight(1.4f), highlight = true)
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { if (job.total > 0) job.doneCount.toFloat() / job.total else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.height(10.dp))
        // Per type: "Videos 2/5 · Photos 7/20"
        val perKind = listOf(Kind.VIDEO, Kind.IMAGE, Kind.PDF)
            .filter { (job.totals[it] ?: 0) > 0 }
            .joinToString("  ·  ") { "${it.plural} ${job.doneOf(it)}/${job.totals[it]}" }
        if (perKind.isNotEmpty()) {
            Text(perKind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Keep the app open. Everything happens on this phone, nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onCancel, shape = CircleShape) { Text("Stop") }
    }
}

@Composable
private fun ResultView(job: JobState, binDays: Int, binSize: Long, onDone: () -> Unit, onEmptyBin: () -> Unit) {
    val savedCount = job.results.count { it.status == ItemStatus.SAVED }
    val skippedCount = job.results.count { it.status == ItemStatus.SKIPPED }
    val failedCount = job.results.count { it.status == ItemStatus.FAILED }
    var confirmEmpty by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    job.savedSoFar.readableSize(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (job.cancelled) "saved (stopped early)" else "saved",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CountPill("Compressed", "$savedCount", Modifier.weight(1f), highlight = true)
                    CountPill("Skipped", "$skippedCount", Modifier.weight(1f))
                    CountPill("Failed", "$failedCount", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (job.mode == Mode.REPLACE && job.binUsed && savedCount > 0 && binSize > 0) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Originals are safe in the Bin ($binDays days)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Check your gallery first. The space is freed when the Bin is emptied " +
                                "(or automatically after $binDays days).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { confirmEmpty = true },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Empty Bin · free ${binSize.readableSize()}") }
                    }
                }
            }
        }

        items(job.results) { r -> ResultRow(r) }

        item {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(56.dp)
                    .navigationBarsPadding(),
                shape = CircleShape,
            ) { Text("Done", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Empty the Bin?",
            text = "Originals will be deleted forever and ${binSize.readableSize()} will be freed. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = onEmptyBin,
            onDismiss = { confirmEmpty = false },
        )
    }
}

@Composable
private fun ResultRow(r: ResultItem) {
    val (icon, tint) = when (r.status) {
        ItemStatus.SAVED -> Icons.Filled.CheckCircle to SuccessGreen
        ItemStatus.SKIPPED -> Icons.Filled.Info to WarnAmber
        ItemStatus.FAILED -> Icons.Filled.Error to ErrorRed
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val sub = if (r.status == ItemStatus.SAVED) {
                    val pct = if (r.before > 0) ((r.before - r.after) * 100 / r.before) else 0
                    "${r.before.readableSize()} → ${r.after.readableSize()}  (−$pct%)"
                } else {
                    r.note
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
