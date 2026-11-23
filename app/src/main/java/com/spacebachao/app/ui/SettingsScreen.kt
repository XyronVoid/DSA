@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spacebachao.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.FREE_BIN_DAYS
import com.spacebachao.app.UiState
import com.spacebachao.app.data.Quality
import com.spacebachao.app.ui.theme.ProGold

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                onClick = { vm.openPro() },
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = ProGold, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.isPro) "Space Bachao Pro" else "Get Pro",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            if (state.isPro) "Everything unlocked. Thank you!" else "Unlimited videos & PDFs, all quality modes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Section("Quality") {
                Quality.entries.forEach { q ->
                    RadioRow(
                        selected = state.effectiveQuality == q,
                        title = q.label,
                        subtitle = q.description,
                        locked = q.proOnly && !state.isPro,
                        onClick = { vm.setQuality(q) },
                    )
                }
            }

            Section("Keep originals in the Bin for") {
                listOf(7, 15, 30, 0).forEach { d ->
                    RadioRow(
                        selected = state.effectiveBinDays == d,
                        title = if (d == 0) "Off" else "$d days",
                        subtitle = if (d == 0) {
                            "Originals are removed right away. Space is freed instantly, but you can't restore."
                        } else {
                            "You can restore the original during this time"
                        },
                        locked = d > FREE_BIN_DAYS && !state.isPro,
                        onClick = { vm.setBinDays(d) },
                    )
                }
            }

            Section("Privacy") {
                Text(
                    "• The app has no INTERNET permission, so files can never leave your phone.\n" +
                        "• Compression happens on the phone (videos use the hardware encoder).\n" +
                        "• Photo dates, locations and camera info are kept.\n" +
                        "• Motion and portrait photos are skipped so their effects don't break.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
                if (!state.hasLocation) {
                    Text(
                        "Location permission is off: photo/video locations may be lost after compression.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                "Space Bachao 1.1",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
            content()
        }
    }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, subtitle: String, locked: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick, enabled = !locked)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Pro",
                    tint = ProGold,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(18.dp),
                )
            }
        }
    }
}
