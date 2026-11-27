package com.spacebachao.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebachao.app.JobState
import com.spacebachao.app.data.ItemStatus
import com.spacebachao.app.data.Kind
import com.spacebachao.app.data.Mode
import com.spacebachao.app.data.ResultItem
import com.spacebachao.app.readableSize
import com.spacebachao.app.ui.theme.LocalPalette

@Composable
fun JobScreen(
    job: JobState,
    binDays: Int,
    binSize: Long,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onEmptyBin: () -> Unit,
    onGetPro: () -> Unit,
) {
    // Keep the screen on while working
    val view = LocalView.current
    DisposableEffect(job.running) {
        view.keepScreenOn = job.running
        onDispose { view.keepScreenOn = false }
    }
    if (job.running) RunningView(job, onCancel) else ResultView(job, binDays, binSize, onDone, onEmptyBin, onGetPro)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val p = LocalPalette.current
    Column(
        modifier
            .glass(p, RoundedCornerShape(22.dp), strong = true)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = valueColor ?: p.label,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.secondary)
    }
}

@Composable
private fun RunningView(job: JobState, onCancel: () -> Unit) {
    val p = LocalPalette.current
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
            color = p.label,
        )
        Spacer(Modifier.height(30.dp))
        Box(contentAlignment = Alignment.Center) {
            val pr = job.currentProgress
            GradientRing(
                progress = if (pr >= 0f) pr else null,
                size = 210.dp,
                stroke = 20.dp,
                colors = listOf(p.gradA, p.gradB, p.gradC, p.gradA),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (pr >= 0f) "${(pr * 100).toInt()}%" else "${job.index + 1}/${job.total}",
                    style = MaterialTheme.typography.displaySmall,
                    color = p.label,
                )
                Text(job.currentKind.label, style = MaterialTheme.typography.labelLarge, color = p.secondary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            job.currentName,
            style = MaterialTheme.typography.bodyMedium,
            color = p.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Done", "${job.doneCount}", Modifier.weight(1f))
            StatTile("Left", "${job.leftCount}", Modifier.weight(1f))
            StatTile("Saved", job.savedSoFar.readableSize(), Modifier.weight(1.4f), valueColor = p.green)
        }
        Spacer(Modifier.height(16.dp))
        CapsuleBar(if (job.total > 0) job.doneCount.toFloat() / job.total else 0f, height = 10.dp)
        Spacer(Modifier.height(10.dp))
        val perKind = listOf(Kind.VIDEO, Kind.IMAGE, Kind.PDF)
            .filter { (job.totals[it] ?: 0) > 0 }
            .joinToString("   ·   ") { "${it.plural} ${job.doneOf(it)}/${job.totals[it]}" }
        if (perKind.isNotEmpty()) {
            Text(perKind, style = MaterialTheme.typography.labelLarge, color = p.secondary)
        }
        Spacer(Modifier.height(26.dp))
        SecureLine()
        Spacer(Modifier.height(6.dp))
        Text(
            "Keep the app open until it's done.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = p.secondary,
        )
        Spacer(Modifier.height(20.dp))
        GlassButton("Stop", onCancel, Modifier.width(160.dp), textColor = p.red)
    }
}

@Composable
private fun ResultView(
    job: JobState,
    binDays: Int,
    binSize: Long,
    onDone: () -> Unit,
    onEmptyBin: () -> Unit,
    onGetPro: () -> Unit,
) {
    val p = LocalPalette.current
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
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(p.brand)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
                }
                Spacer(Modifier.height(14.dp))
                GradientText(job.savedSoFar.readableSize(), MaterialTheme.typography.displayMedium)
                Text(
                    if (job.cancelled) "saved (stopped early)" else "space saved",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.secondary,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Compressed", "$savedCount", Modifier.weight(1f), valueColor = p.green)
                    StatTile("Skipped", "$skippedCount", Modifier.weight(1f), valueColor = p.orange)
                    StatTile("Failed", "$failedCount", Modifier.weight(1f), valueColor = p.red)
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (job.limitReached) {
            item {
                GlassCard(strong = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = p.goldDeep)
                        Spacer(Modifier.width(10.dp))
                        Text("Your free 5 GB is used up", style = MaterialTheme.typography.titleMedium, color = p.label)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The remaining files were not touched. Subscribe to Pro to compress unlimited files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.secondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    GlossyButton(
                        "Get Pro",
                        onGetPro,
                        Modifier.fillMaxWidth(),
                        colors = listOf(p.gold, p.goldDeep),
                        textColor = p.onGold,
                    )
                }
            }
        }

        if (job.mode == Mode.REPLACE && job.binUsed && savedCount > 0 && binSize > 0) {
            item {
                GlassCard {
                    Text("Originals are safe in the Bin", style = MaterialTheme.typography.titleMedium, color = p.label)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Check your gallery first. The space is freed when the Bin is emptied " +
                            "(or automatically after $binDays days).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.secondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassButton(
                        "Empty Bin · free ${binSize.readableSize()}",
                        { confirmEmpty = true },
                        Modifier.fillMaxWidth(),
                        textColor = p.red,
                    )
                }
            }
        }

        items(job.results) { r -> ResultRow(r) }

        item {
            GlossyButton(
                "Done",
                onDone,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .navigationBarsPadding(),
            )
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
    val p = LocalPalette.current
    val (icon, tint) = when (r.status) {
        ItemStatus.SAVED -> Icons.Filled.CheckCircle to p.green
        ItemStatus.SKIPPED -> Icons.Filled.Info to p.orange
        ItemStatus.FAILED -> Icons.Filled.Error to p.red
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(p, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = p.label,
            )
            val sub = if (r.status == ItemStatus.SAVED) {
                val pct = if (r.before > 0) ((r.before - r.after) * 100 / r.before) else 0
                "${r.before.readableSize()} → ${r.after.readableSize()}  (−$pct%)"
            } else {
                r.note
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = p.secondary)
        }
    }
}
