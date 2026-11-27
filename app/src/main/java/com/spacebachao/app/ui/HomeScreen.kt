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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.spacebachao.app.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

private val OtherGray = Color(0xFF4A4A4A)

/** Grid item under a touch point (works with content padding). */
private fun LazyGridState.keyAt(pos: Offset): Any? {
    val info = layoutInfo
    val y = pos.y.toInt() + info.viewportStartOffset
    val row = info.visibleItemsInfo.filter { y >= it.offset.y && y < it.offset.y + it.size.height }
    if (row.isEmpty()) return null
    return row.minByOrNull { abs(it.offset.x + it.size.width / 2 - pos.x.toInt()) }?.key
}

@Composable
fun HomeScreen(
    state: UiState,
    vm: AppViewModel,
    onPickPdf: () -> Unit,
    onOpenBin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hasSelection = state.selected.isNotEmpty()
    val list = state.listFor(state.tab)
    val allSelected = list.isNotEmpty() && list.all { it.key in state.selected }

    // ----- Drag to select (long-press, then slide like a gallery app) -----
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    val currentList by rememberUpdatedState(list)
    val currentSelected by rememberUpdatedState(state.selected)
    var dragStart by remember { mutableIntStateOf(-1) }
    var dragBase by remember { mutableStateOf(emptySet<String>()) }
    var dragAdds by remember { mutableStateOf(true) }
    var lastPointer by remember { mutableStateOf<Offset?>(null) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    fun indexAt(pos: Offset): Int? {
        val key = gridState.keyAt(pos) as? String ?: return null
        val idx = currentList.indexOfFirst { it.key == key }
        return if (idx >= 0) idx else null
    }

    fun selectRangeTo(idx: Int) {
        val l = currentList
        if (dragStart !in l.indices || idx !in l.indices) return
        val range = if (idx >= dragStart) dragStart..idx else idx..dragStart
        val keys = range.map { l[it].key }.toSet()
        vm.setSelected(if (dragAdds) dragBase + keys else dragBase - keys)
    }

    LaunchedEffect(autoScroll) {
        if (autoScroll != 0f) {
            while (isActive) {
                gridState.scrollBy(autoScroll)
                lastPointer?.let { pos -> indexAt(pos)?.let { selectRangeTo(it) } }
                delay(16)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = top + 4.dp,
                bottom = bottom + if (hasSelection) 210.dp else 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            val idx = indexAt(pos)
                            if (idx != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragStart = idx
                                dragBase = currentSelected
                                dragAdds = currentList[idx].key !in currentSelected
                                lastPointer = pos
                                selectRangeTo(idx)
                            }
                        },
                        onDrag = { change, _ ->
                            if (dragStart >= 0) {
                                change.consume()
                                lastPointer = change.position
                                indexAt(change.position)?.let { selectRangeTo(it) }
                                val edge = 96.dp.toPx()
                                val y = change.position.y
                                autoScroll = when {
                                    y < edge -> -((edge - y) / edge) * 28f
                                    y > size.height - edge -> ((y - (size.height - edge)) / edge) * 28f
                                    else -> 0f
                                }
                            }
                        },
                        onDragEnd = {
                            dragStart = -1
                            autoScroll = 0f
                        },
                        onDragCancel = {
                            dragStart = -1
                            autoScroll = 0f
                        },
                    )
                },
        ) {
            item(key = "h_title", span = { GridItemSpan(maxLineSpan) }) {
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
            item(key = "h_storage", span = { GridItemSpan(maxLineSpan) }) { StorageCard(state) }
            item(key = "h_controls", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(16.dp))
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
                    item(key = "h_pdf", span = { GridItemSpan(maxLineSpan) }) { PdfIntro(onPickPdf) }
                    items(state.pdfs, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { pdf ->
                        PdfRow(
                            pdf,
                            selected = pdf.key in state.selected,
                            onToggle = { vm.toggle(pdf) },
                            onRemove = { vm.removePdf(pdf) },
                        )
                    }
                }
                state.loading && list.isEmpty() -> item(key = "h_loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = p.accent) }
                }
                list.isEmpty() -> item(key = "h_empty", span = { GridItemSpan(maxLineSpan) }) {
                    GlassCard(Modifier.padding(top = 6.dp)) {
                        Text(
                            if (state.tab == Kind.IMAGE) "No large photos left" else "No large videos left",
                            style = MaterialTheme.typography.titleMedium,
                            color = p.label,
                        )
                        Text(
                            "Everything big has been compressed already.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.secondary,
                        )
                    }
                }
                else -> {
                    item(key = "h_listhead", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${list.size} ${state.tab.plural.lowercase()} · ${list.sumOf { it.size }.readableSize()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = p.label,
                                )
                                Text(
                                    "Biggest first · hold and slide to select many",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.secondary,
                                )
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
    val p = LocalPalette.current
    Row(
        Modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(p.card)
            .border(1.dp, p.goldDeep, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = p.gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            if (isPro) "PRO" else "Get Pro",
            color = p.gold,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** How full the phone is: used / free, split into photos, videos and everything else. */
@Composable
private fun StorageCard(state: UiState) {
    val p = LocalPalette.current
    val total = state.storageTotal.coerceAtLeast(1L)
    val free = state.storageFree.coerceIn(0L, total)
    val used = total - free
    val photos = state.photosBytes.coerceIn(0L, used)
    val videos = state.videosBytes.coerceIn(0L, used - photos)
    val other = (used - photos - videos).coerceAtLeast(0L)
    val usedPct = (used * 100 / total).toInt()

    GlassCard(shape = RoundedCornerShape(26.dp), padding = PaddingValues(22.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Caps("Used")
                Text(used.readableSize(), style = MaterialTheme.typography.headlineLarge, color = p.label)
                Text("$usedPct% full", style = MaterialTheme.typography.bodySmall, color = p.secondary)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Caps("Free")
                Text(free.readableSize(), style = MaterialTheme.typography.headlineLarge, color = p.gold)
                Text("of ${total.readableSize()}", style = MaterialTheme.typography.bodySmall, color = p.secondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        SegmentBar(
            listOf(
                photos.toFloat() / total to p.label,
                videos.toFloat() / total to p.gold,
                other.toFloat() / total to OtherGray,
            )
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Legend("Photos", photos.readableSize(), p.label, Modifier.weight(1f))
            Legend("Videos", videos.readableSize(), p.gold, Modifier.weight(1f))
            Legend("Other", other.readableSize(), OtherGray, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Caps("Saved so far")
                Text(state.totalSaved.readableSize(), style = MaterialTheme.typography.titleLarge, color = p.green)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Caps("In Bin")
                Text(state.binSize.readableSize(), style = MaterialTheme.typography.titleLarge, color = p.orange)
            }
        }
        Spacer(Modifier.height(14.dp))
        SecureLine()
    }
}

@Composable
private fun Legend(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = p.secondary, maxLines = 1)
        }
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = p.label,
            maxLines = 1,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun ProgressCard(state: UiState) {
    val p = LocalPalette.current
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
        Spacer(Modifier.height(12.dp))
        CapsuleBar(fraction, colors = listOf(p.green, p.greenLight))
    }
}

@Composable
private fun FreePlanCard(state: UiState, onUpgrade: () -> Unit) {
    val p = LocalPalette.current
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
                color = p.gold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onUpgrade)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        CapsuleBar(fraction, colors = if (fraction > 0.85f) listOf(p.orange, p.red) else null)
    }
}

@Composable
private fun MediaTile(file: MediaFile, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val scale by animateFloatAsState(if (selected) 0.88f else 1f, label = "tile")
    val shape = RoundedCornerShape(if (selected) 18.dp else 10.dp)
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
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
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
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) p.gold else Color.Black.copy(alpha = 0.3f))
                .border(1.5.dp, if (selected) p.gold else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = p.onButton, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun PdfIntro(onPickPdf: () -> Unit) {
    val p = LocalPalette.current
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
    val p = LocalPalette.current
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
                .background(if (selected) p.gold else p.track),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = p.onButton, modifier = Modifier.size(15.dp))
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
    val p = LocalPalette.current
    Column(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glass(p, RoundedCornerShape(28.dp), strong = true)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                "${state.selected.size} selected",
                style = MaterialTheme.typography.titleMedium,
                color = p.label,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Clear",
                style = MaterialTheme.typography.labelLarge,
                color = p.secondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { vm.setSelected(emptySet()) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        IOSSegmented(
            options = listOf("Replace", "Keep both"),
            selected = if (state.mode == Mode.REPLACE) 0 else 1,
            onSelect = { vm.setMode(if (it == 0) Mode.REPLACE else Mode.KEEP_BOTH) },
        )
        val days = state.effectiveBinDays
        val heicPhotos = state.heic && state.heicSupported &&
            state.selectedFiles.any { it.kind == Kind.IMAGE }
        Text(
            when {
                state.mode == Mode.KEEP_BOTH -> "Original stays untouched. A smaller copy is saved in the SpaceBachao folder."
                days > 0 -> "The smaller file replaces the original. The original is kept in the Bin for $days days."
                else -> "The smaller file replaces the original. Bin is off, so it can't be restored."
            } + if (heicPhotos && state.mode == Mode.REPLACE) {
                " Photos become HEIC (same quality, smaller); you'll confirm once to remove the old JPEGs."
            } else {
                ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = p.secondary,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        GlossyButton(
            "Compress · ${state.selectedSize.readableSize()}",
            onClick = { vm.startCompress() },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Compress,
        )
    }
}
