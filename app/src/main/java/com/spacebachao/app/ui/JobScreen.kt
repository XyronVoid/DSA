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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebachao.app.JobState
import com.spacebachao.app.data.ItemStatus
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
    // Kaam chalte waqt screen band na ho
    val view = LocalView.current
    DisposableEffect(job.running) {
        view.keepScreenOn = job.running
        onDispose { view.keepScreenOn = false }
    }
    if (job.running) RunningView(job, onCancel) else ResultView(job, binDays, binSize, onDone, onEmptyBin)
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
        Text("Compress ho raha hai…", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            val p = job.currentProgress
            if (p >= 0f) {
                CircularProgressIndicator(
                    progress = { p },
                    modifier = Modifier.size(170.dp),
                    strokeWidth = 10.dp,
                )
                Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(170.dp), strokeWidth = 10.dp)
                Text("${job.index + 1}/${job.total}", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("File ${job.index + 1} / ${job.total}", style = MaterialTheme.typography.titleMedium)
        Text(
            job.currentName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { if (job.total > 0) job.index.toFloat() / job.total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ab tak bacha: ${job.savedSoFar.readableSize()}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "App khula rakho. Sab kuch isi phone pe ho raha hai, kuch bhi upload nahi ho raha.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) { Text("Ruko (Cancel)") }
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                Spacer(Modifier.height(8.dp))
                Text(
                    job.savedSoFar.readableSize(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (job.cancelled) "bachaya (beech me roka)" else "bachaya",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$savedCount compress · $skippedCount skip · $failedCount fail",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (job.mode == Mode.REPLACE && job.binUsed && savedCount > 0 && binSize > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Originals Bin me safe hain ($binDays din)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pehle gallery me check kar lo. Jagah tab free hogi jab Bin khali hoga " +
                                "(ya $binDays din baad apne aap).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { confirmEmpty = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Bin khali karo · ${binSize.readableSize()} free karo") }
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
                    .height(52.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Done") }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Bin khali karein?",
            text = "Originals hamesha ke liye delete ho jayenge aur ${binSize.readableSize()} jagah free hogi. " +
                "Ye wapas nahi aayenge.",
            confirmLabel = "Haan, delete karo",
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
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
