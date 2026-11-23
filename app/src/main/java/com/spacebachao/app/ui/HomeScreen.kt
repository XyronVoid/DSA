@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spacebachao.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.FREE_PDFS_PER_DAY
import com.spacebachao.app.FREE_VIDEOS_PER_DAY
import com.spacebachao.app.UiState
import com.spacebachao.app.data.Kind
import com.spacebachao.app.data.MediaFile
import com.spacebachao.app.data.Mode
import com.spacebachao.app.formatDuration
import com.spacebachao.app.readableSize
import com.spacebachao.app.ui.theme.ProGold

@Composable
fun HomeScreen(
    state: UiState,
    vm: AppViewModel,
    onPickPdf: () -> Unit,
    onOpenBin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dir = LocalLayoutDirection.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text("Space Bachao", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    ProChip(state.isPro, onClick = { vm.openPro() })
                    IconButton(onClick = onOpenBin) {
                        BadgedBox(badge = {
                            if (state.binEntries.isNotEmpty()) Badge { Text("${state.binEntries.size}") }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Bin")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            if (state.selected.isNotEmpty()) BottomActionBar(state, vm)
        },
    ) { padding ->
        val list = state.listFor(state.tab)
        val allSelected = list.isNotEmpty() && list.all { it.key in state.selected }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(dir) + 16.dp,
                end = padding.calculateEndPadding(dir) + 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { HeroCard(state) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    KindSelector(state, vm)
                    Spacer(Modifier.height(10.dp))
                    ProgressCard(state, vm)
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (state.tab == Kind.PDF) {
                item(span = { GridItemSpan(maxLineSpan) }) { PdfIntro(onPickPdf) }
                items(state.pdfs, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { pdf ->
                    PdfRow(pdf, selected = pdf.key in state.selected, onToggle = { vm.toggle(pdf) }, onRemove = { vm.removePdf(pdf) })
                }
            } else if (state.loading && list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        if (state.tab == Kind.IMAGE) "No large photos left to compress" else "No large videos left to compress",
                        "Everything big has been compressed already. Nice!",
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${list.size} ${state.tab.plural.lowercase()} · ${list.sumOf { it.size }.readableSize()}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Biggest first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { if (allSelected) vm.clearTab() else vm.selectAllInTab() }) {
                            Text(if (allSelected) "Clear" else "Select all")
                        }
                    }
                }
                items(list, key = { it.key }) { file ->
                    MediaTile(file, selected = file.key in state.selected, onClick = { vm.toggle(file) })
                }
            }
        }
    }
}

@Composable
private fun ProChip(isPro: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isPro) ProGold.copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = ProGold,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isPro) "PRO" else "Get Pro",
                style = MaterialTheme.typography.labelLarge,
                color = if (isPro) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun HeroCard(state: UiState) {
    val total = state.storageTotal
    val free = state.storageFree
    val used = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Free space", style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(free.readableSize(), style = MaterialTheme.typography.displaySmall, color = on)
                Text(
                    "  of ${total.readableSize()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = on.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { used },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = on.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("Saved so far", state.totalSaved.readableSize(), Modifier.weight(1f))
                StatPill("Waiting in Bin", state.binSize.readableSize(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            PrivacyLine(color = on)
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun KindSelector(state: UiState, vm: AppViewModel) {
    val kinds = listOf(Kind.IMAGE, Kind.VIDEO, Kind.PDF)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        kinds.forEachIndexed { i, kind ->
            SegmentedButton(
                selected = state.tab == kind,
                onClick = { vm.setTab(kind) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = kinds.size),
            ) {
                Text(kind.plural, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProgressCard(state: UiState, vm: AppViewModel) {
    val tab = state.tab
    val done = state.doneCount[tab] ?: 0
    val left = state.listFor(tab).size
    val fraction = if (done + left > 0) done.toFloat() / (done + left) else 0f
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "$done ${tab.plural.lowercase()} compressed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$left left",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
            )
            if (!state.isPro && tab != Kind.IMAGE) {
                val limit = if (tab == Kind.VIDEO) FREE_VIDEOS_PER_DAY else FREE_PDFS_PER_DAY
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        "Free today: ${vm.freeLeft(tab)} of $limit left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.openPro() }) { Text("Go unlimited") }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(file: MediaFile, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (selected) 26.dp else 16.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
    ) {
        Thumbnail(file.uri, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
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
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfIntro(onPickPdf: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Best for scanned PDFs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pages are saved as clean images, so text can't be selected afterwards. " +
                    "If the saving is small, the original is kept. Password-protected PDFs are skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onPickPdf, shape = CircleShape) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Choose PDFs")
            }
        }
    }
}

@Composable
private fun PdfRow(pdf: MediaFile, selected: Boolean, onToggle: () -> Unit, onRemove: () -> Unit) {
    Surface(
        onClick = onToggle,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Icon(kindIcon(Kind.PDF), contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pdf.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    pdf.size.readableSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BottomActionBar(state: UiState, vm: AppViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.mode == Mode.REPLACE,
                    onClick = { vm.setMode(Mode.REPLACE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Replace") }
                SegmentedButton(
                    selected = state.mode == Mode.KEEP_BOTH,
                    onClick = { vm.setMode(Mode.KEEP_BOTH) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Keep both") }
            }
            val days = state.effectiveBinDays
            Text(
                when {
                    state.mode == Mode.KEEP_BOTH ->
                        "Original stays untouched. A smaller copy is saved in the SpaceBachao folder."
                    days > 0 -> "Smaller file replaces the original. Original is kept in the Bin for $days days."
                    else -> "Smaller file replaces the original. Bin is off, so it can't be restored."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Button(
                onClick = { vm.startCompress() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Compress, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Compress ${state.selected.size} · ${state.selectedSize.readableSize()}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
