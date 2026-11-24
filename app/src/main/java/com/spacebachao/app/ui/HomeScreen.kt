package com.spacebachao.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.FREE_LIMIT_BYTES
import com.spacebachao.app.UiState
import com.spacebachao.app.data.Kind
import com.spacebachao.app.data.MediaFile
import com.spacebachao.app.data.Mode
import com.spacebachao.app.formatDuration
import com.spacebachao.app.readableSize
import com.spacebachao.app.ui.theme.LocalApple

@Composable
fun HomeScreen(
    state: UiState,
    vm: AppViewModel,
    onPickPdf: () -> Unit,
    onOpenBin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val p = LocalApple.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hasSelection = state.selected.isNotEmpty()
    val list = state.listFor(state.tab)
    val allSelected = list.isNotEmpty() && list.all { it.key in state.selected }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = top + 4.dp,
                bottom = bottom + if (hasSelection) 200.dp else 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LargeTitle("Space Bachao") {
                    ProPill(state.isPro, onClick = { vm.openPro() })
                    GlassIconButton(
                        Icons.Outlined.Delete,
                        "Bin",
                        onOpenBin,
                        badge = if (state.binEntries.isNotEmpty()) "${state.binEntries.size}" else null,
                    )
                    GlassIconButton(Icons.Outlined.Settings, "Settings", onOpenSettings)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { HeroCard(state) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    val kinds = listOf(Kind.IMAGE, Kind.VIDEO, Kind.PDF)
                    IOSSegmented(
                        options = kinds.map { it.plural },
                        selected = kinds.indexOf(state.tab),
                        onSelect = { vm.setTab(kinds[it]) },
                    )
                    Spacer(Modifier.height(12.dp))
                    ProgressCard(state)
                    if (!state.isPro) {
                        Spacer(Modifier.height(10.dp))
                        FreePlanCard(state, onUpgrade = { vm.openPro() })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            when {
                state.tab == Kind.PDF -> {
                    item(span = { GridItemSpan(maxLineSpan) }) { PdfIntro(onPickPdf) }
                    items(state.pdfs, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { pdf ->
                        PdfRow(
                            pdf,
                            selected = pdf.key in state.selected,
                            onToggle = { vm.toggle(pdf) },
                            onRemove = { vm.removePdf(pdf) },
                        )
                    }
                }
                state.loading && list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = p.accent) }
                }
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    GlassCard(Modifier.padding(top = 6.dp)) {
                        Text(
                            if (state.tab == Kind.IMAGE) "No large photos left" else "No large videos left",
                            style = MaterialTheme.typography.titleMedium,
                            color = p.label,
                        )
                        Text(
                            "Everything big has been compressed already. Nice!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.secondary,
                        )
                    }
                }
                else -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${list.size} ${state.tab.plural.lowercase()} · ${list.sumOf { it.size }.readableSize()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = p.label,
                                )
                                Text("Biggest first", style = MaterialTheme.typography.bodySmall, color = p.secondary)
                            }
                            Text(
                                if (allSelected) "Deselect" else "Select all",
                                style = MaterialTheme.typography.titleMedium,
                                color = p.accent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { if (allSelected) vm.clearTab() else vm.selectAllInTab() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(list, key = { it.key }) { file ->
                        MediaTile(file, selected = file.key in state.selected, onClick = { vm.toggle(file) })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hasSelection,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ActionPanel(state, vm)
        }
    }
}

@Composable
private fun ProPill(isPro: Boolean, onClick: () -> Unit) {
    val p = LocalApple.current
    Row(
        Modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(p.gold, p.goldDeep)))
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f), 0.5f to Color.Transparent))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            if (isPro) "PRO" else "Get Pro",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HeroCard(state: UiState) {
    val p = LocalApple.current
    val total = state.storageTotal
    val free = state.storageFree
    val used = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f
    GlassCard(strong = true, shape = RoundedCornerShape(30.dp), padding = PaddingValues(22.dp)) {
        Text("Free space", style = MaterialTheme.typography.labelLarge, color = p.secondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(free.readableSize(), style = MaterialTheme.typography.displayMedium, color = p.label)
            Text(
                "  of ${total.readableSize()}",
                style = MaterialTheme.typography.titleMedium,
                color = p.secondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        CapsuleBar(used, height = 10.dp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Saved so far", state.totalSaved.readableSize(), p.green, Modifier.weight(1f))
            MiniStat("In Bin", state.binSize.readableSize(), p.orange, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        SecureLine()
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val p = LocalApple.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(p.track)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.secondary)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressCard(state: UiState) {
    val p = LocalApple.current
    val tab = state.tab
    val done = state.doneCount[tab] ?: 0
    val left = state.listFor(tab).size
    val fraction = if (done + left > 0) done.toFloat() / (done + left) else 0f
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = p.green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "$done compressed",
                style = MaterialTheme.typography.titleMedium,
                color = p.label,
                modifier = Modifier.weight(1f),
            )
            Text("$left left", style = MaterialTheme.typography.titleMedium, color = p.secondary)
        }
        Spacer(Modifier.height(10.dp))
        CapsuleBar(fraction, colors = listOf(p.greenLight, p.green))
    }
}

@Composable
private fun FreePlanCard(state: UiState, onUpgrade: () -> Unit) {
    val p = LocalApple.current
    val fraction = state.freeUsed.toFloat() / FREE_LIMIT_BYTES
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Free plan", style = MaterialTheme.typography.titleMedium, color = p.label)
                Text(
                    "${state.freeUsed.readableSize()} of ${FREE_LIMIT_BYTES.readableSize()} used",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.secondary,
                )
            }
            Text(
                "Upgrade",
                style = MaterialTheme.typography.titleMedium,
                color = p.accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onUpgrade)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        CapsuleBar(
            fraction,
            colors = if (fraction > 0.85f) listOf(p.orange, p.red) else listOf(p.accentLight, p.accent),
        )
    }
}

@Composable
private fun MediaTile(file: MediaFile, selected: Boolean, onClick: () -> Unit) {
    val p = LocalApple.current
    val scale by animateFloatAsState(if (selected) 0.9f else 1f, label = "tile")
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Thumbnail(file.uri, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                file.size.readableSize(),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (file.kind == Kind.VIDEO && file.durationMs > 0) {
            Text(
                formatDuration(file.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        // iOS Photos style check mark
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) p.accent else Color.White.copy(alpha = 0.25f))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun PdfIntro(onPickPdf: () -> Unit) {
    val p = LocalApple.current
    GlassCard {
        Text("Best for scanned PDFs", style = MaterialTheme.typography.titleMedium, color = p.label)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pages are saved as clean images, so text can't be selected afterwards. " +
                "If the saving is small, the original is kept. Password-protected PDFs are skipped.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.secondary,
        )
        Spacer(Modifier.height(14.dp))
        GlassButton("+  Choose PDFs", onPickPdf)
    }
}

@Composable
private fun PdfRow(pdf: MediaFile, selected: Boolean, onToggle: () -> Unit, onRemove: () -> Unit) {
    val p = LocalApple.current
    Row(
        Modifier
            .fillMaxWidth()
            .glass(p, RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) p.accent else p.track),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(kindIcon(Kind.PDF), contentDescription = null, tint = p.red)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                pdf.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = p.label,
            )
            Text(pdf.size.readableSize(), style = MaterialTheme.typography.bodySmall, color = p.secondary)
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = "Remove",
            tint = p.secondary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(6.dp),
        )
    }
}

@Composable
private fun ActionPanel(state: UiState, vm: AppViewModel) {
    val p = LocalApple.current
    Column(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glass(p, RoundedCornerShape(30.dp), strong = true, elevation = 16.dp)
            .padding(16.dp)
    ) {
        IOSSegmented(
            options = listOf("Replace", "Keep both"),
            selected = if (state.mode == Mode.REPLACE) 0 else 1,
            onSelect = { vm.setMode(if (it == 0) Mode.REPLACE else Mode.KEEP_BOTH) },
        )
        val days = state.effectiveBinDays
        Text(
            when {
                state.mode == Mode.KEEP_BOTH -> "Original stays untouched. A smaller copy is saved in the SpaceBachao folder."
                days > 0 -> "The smaller file replaces the original. The original is kept in the Bin for $days days."
                else -> "The smaller file replaces the original. Bin is off, so it can't be restored."
            },
            style = MaterialTheme.typography.bodySmall,
            color = p.secondary,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        GlossyButton(
            "Compress ${state.selected.size} · ${state.selectedSize.readableSize()}",
            onClick = { vm.startCompress() },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Compress,
        )
    }
}
