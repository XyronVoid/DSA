package com.spacebachao.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.FREE_BIN_DAYS
import com.spacebachao.app.UiState
import com.spacebachao.app.data.Quality
import com.spacebachao.app.ui.theme.LocalApple

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, onBack: () -> Unit) {
    val p = LocalApple.current
    val insets = WindowInsets.systemBars.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = insets.calculateTopPadding() + 4.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                )
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LargeTitle("Settings", onBack = onBack)

        // Pro banner
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(p.gold, p.goldDeep)))
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.40f), 0.5f to Color.Transparent))
                .clickable { vm.openPro() }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.isPro) "Space Bachao Pro" else "Get Space Bachao Pro",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    when {
                        state.devBuild -> "Developer test build · Pro unlocked"
                        state.isPro -> "Unlimited compression. Thank you!"
                        else -> "Unlimited files, all quality modes, longer Bin"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }

        SectionLabel("QUALITY")
        Group {
            Quality.entries.forEachIndexed { i, q ->
                if (i > 0) Hairline(Modifier.padding(start = 16.dp))
                CheckRow(
                    title = q.label,
                    subtitle = q.description,
                    checked = state.effectiveQuality == q,
                    locked = q.proOnly && !state.isPro,
                    onClick = { vm.setQuality(q) },
                )
            }
        }

        SectionLabel("KEEP ORIGINALS IN THE BIN FOR")
        Group {
            listOf(7, 15, 30, 0).forEachIndexed { i, d ->
                if (i > 0) Hairline(Modifier.padding(start = 16.dp))
                CheckRow(
                    title = if (d == 0) "Off" else "$d days",
                    subtitle = if (d == 0) "Space is freed instantly, but originals can't be restored" else null,
                    checked = state.effectiveBinDays == d,
                    locked = d > FREE_BIN_DAYS && !state.isPro,
                    onClick = { vm.setBinDays(d) },
                )
            }
        }

        SectionLabel("SECURITY")
        Group {
            Column(Modifier.padding(16.dp)) {
                SecureLine(color = p.label)
                Spacer(Modifier.height(8.dp))
                Text(
                    "• No internet permission: your files can never be uploaded.\n" +
                        "• Compression runs on your phone's own chip.\n" +
                        "• Dates, locations and camera info are kept.\n" +
                        "• Motion and portrait photos are skipped so their effects never break.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.secondary,
                )
                if (!state.hasLocation) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Location permission is off: photo/video locations may be lost after compression.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.red,
                    )
                }
            }
        }

        if (!state.isPro) {
            Text(
                "Restore purchase",
                style = MaterialTheme.typography.titleMedium,
                color = p.accent,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.restorePurchase() }
                    .padding(10.dp),
            )
        }
        Text(
            "Space Bachao 1.2",
            style = MaterialTheme.typography.bodySmall,
            color = p.tertiary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val p = LocalApple.current
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = p.secondary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    val p = LocalApple.current
    Column(
        Modifier
            .fillMaxWidth()
            .glass(p, RoundedCornerShape(22.dp))
    ) { content() }
}

@Composable
private fun CheckRow(title: String, subtitle: String?, checked: Boolean, locked: Boolean, onClick: () -> Unit) {
    val p = LocalApple.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (locked) p.secondary else p.label)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.secondary)
            }
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                locked -> Icon(Icons.Filled.Lock, contentDescription = "Pro", tint = p.goldDeep, modifier = Modifier.size(18.dp))
                checked -> Icon(Icons.Filled.Check, contentDescription = "Selected", tint = p.accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}
