@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spacebachao.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.FREE_PDFS_PER_DAY
import com.spacebachao.app.FREE_VIDEOS_PER_DAY
import com.spacebachao.app.Paywall
import com.spacebachao.app.UiState
import com.spacebachao.app.ui.theme.ProGold

@Composable
fun ProScreen(state: UiState, paywall: Paywall, vm: AppViewModel) {
    val activity = LocalContext.current as? Activity
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = { vm.dismissPaywall() }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                Modifier
                    .size(76.dp)
                    .background(ProGold.copy(alpha = 0.2f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = ProGold, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (state.isPro) "You're Pro" else "Space Bachao Pro",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                paywall.reason ?: "Pay once. Free up space forever.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Free", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Bullet("Unlimited photos")
                    Bullet("$FREE_VIDEOS_PER_DAY videos and $FREE_PDFS_PER_DAY PDFs per day")
                    Bullet("Balanced quality, Bin for 7 days")
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Pro", style = MaterialTheme.typography.labelLarge, color = ProGold)
                    Bullet("Unlimited videos and PDFs, any size")
                    Bullet("High quality and Max saving modes")
                    Bullet("Keep originals in the Bin for 15 or 30 days")
                    Bullet("All future features, no ads, no subscription")
                }
            }
            Spacer(Modifier.height(28.dp))

            if (state.isPro) {
                Button(
                    onClick = { vm.dismissPaywall() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                ) { Text("Thanks for supporting!", style = MaterialTheme.typography.titleMedium) }
            } else {
                Button(
                    onClick = { activity?.let { vm.buyPro(it) } },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                ) {
                    Text(
                        if (state.proPrice != null) "Unlock Pro · ${state.proPrice} once" else "Unlock Pro",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (paywall.reason != null) {
                    Spacer(Modifier.height(10.dp))
                    val n = paywall.freeItems.size
                    OutlinedButton(
                        onClick = { vm.continueFree() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = CircleShape,
                    ) { Text(if (n > 0) "Continue free ($n files today)" else "Not now") }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { vm.restorePurchase() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Restore purchase")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "One-time payment through Google Play. Your files still never leave your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
