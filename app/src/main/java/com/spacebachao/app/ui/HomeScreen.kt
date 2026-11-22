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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.UiState
import com.spacebachao.app.data.Kind
import com.spacebachao.app.data.MediaFile
import com.spacebachao.app.data.Mode
import com.spacebachao.app.formatDuration
import com.spacebachao.app.readableSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    vm: AppViewModel,
    onPickPdf: () -> Unit,
    onOpenBin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Space Bachao", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onOpenBin) {
                        BadgedBox(badge = {
                            if (state.binEntries.isNotEmpty()) Badge { Text("${state.binEntries.size}") }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Bin")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            if (state.selected.isNotEmpty()) BottomActionBar(state, vm)
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            StorageCard(state, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val tabs = listOf(Kind.IMAGE, Kind.VIDEO, Kind.PDF)
            TabRow(selectedTabIndex = tabs.indexOf(state.tab)) {
                tabs.forEach { kind ->
                    val label = when (kind) {
                        Kind.IMAGE -> "Photos (${state.images.size})"
                        Kind.VIDEO -> "Videos (${state.videos.size})"
                        Kind.PDF -> "PDF"
                    }
                    Tab(
                        selected = state.tab == kind,
                        onClick = { vm.setTab(kind) },
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.tab == Kind.PDF -> PdfTab(state, vm, onPickPdf)
                    state.loading && state.listFor(state.tab).isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> MediaGrid(state, vm)
                }
            }
        }
    }
}

@Composable
private fun StorageCard(state: UiState, modifier: Modifier = Modifier) {
    val total = state.storageTotal
    val free = state.storageFree
    val used = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Phone storage",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    free.readableSize(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "  free / ${total.readableSize()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { used },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            if (state.binEntries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bin me ${state.binSize.readableSize()} pada hai. Khali karoge toh utni jagah aur free hogi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            PrivacyLine()
        }
    }
}

@Composable
private fun MediaGrid(state: UiState, vm: AppViewModel) {
    val list = state.listFor(state.tab)
    if (list.isEmpty()) {
        EmptyState(
            if (state.tab == Kind.IMAGE) "Koi badi JPEG photo nahi mili" else "Koi badi MP4 video nahi mili",
            if (state.hiddenCount > 0) "${state.hiddenCount} files pehle hi compress/check ho chuki hain." else null,
        )
        return
    }
    val allSelected = list.all { it.key in state.selected }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${list.size} files · ${list.sumOf { it.size }.readableSize()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Sabse badi files sabse upar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { if (allSelected) vm.clearTab() else vm.selectAllInTab() }) {
                    Text(if (allSelected) "Sab hatao" else "Sab chuno")
                }
            }
        }
        items(list, key = { it.key }) { file ->
            MediaTile(file, selected = file.key in state.selected, onClick = { vm.toggle(file) })
        }
    }
}

@Composable
private fun MediaTile(file: MediaFile, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
            )
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
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
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
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfTab(state: UiState, vm: AppViewModel, onPickPdf: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Scan / photo wale PDF ke liye", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "• Compress ke baad PDF ka text select/copy nahi hoga\n" +
                            "• Agar bachat kam hui toh original waisa hi rahega\n" +
                            "• Password wale PDF chhod diye jaate hain",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onPickPdf, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("PDF chuno")
                    }
                }
            }
        }
        items(state.pdfs, key = { it.key }) { pdf ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.clickable { vm.toggle(pdf) },
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pdf.key in state.selected, onCheckedChange = { vm.toggle(pdf) })
                    Icon(kindIcon(Kind.PDF), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pdf.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            pdf.size.readableSize(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.removePdf(pdf) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Hatao")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BottomActionBar(state: UiState, vm: AppViewModel) {
    Surface(tonalElevation = 3.dp, shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == Mode.REPLACE,
                    onClick = { vm.setMode(Mode.REPLACE) },
                    label = {
                        Text(if (state.binDays > 0) "Replace · Bin me ${state.binDays} din" else "Replace (Bin off)")
                    },
                    leadingIcon = if (state.mode == Mode.REPLACE) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
                FilterChip(
                    selected = state.mode == Mode.KEEP_BOTH,
                    onClick = { vm.setMode(Mode.KEEP_BOTH) },
                    label = { Text("Dono rakho") },
                    leadingIcon = if (state.mode == Mode.KEEP_BOTH) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
            Text(
                if (state.mode == Mode.REPLACE) {
                    "Compressed file original ki jagah aayegi. Original Bin me safe rahega."
                } else {
                    "Original jaisa hai waisa rahega, chhoti copy SpaceBachao folder me banegi."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Button(
                onClick = { vm.startCompress() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "Compress karo · ${state.selected.size} files (${state.selectedSize.readableSize()})",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
