package com.spacebachao.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacebachao.app.AppViewModel
import com.spacebachao.app.FREE_LIMIT_BYTES
import com.spacebachao.app.Paywall
import com.spacebachao.app.UiState
import com.spacebachao.app.billing.ProPlan
import com.spacebachao.app.readableSize
import com.spacebachao.app.ui.theme.LocalApple

@Composable
fun ProScreen(state: UiState, paywall: Paywall, vm: AppViewModel) {
    val p = LocalApple.current
    val activity = LocalContext.current as? Activity
    val plans = state.plans
    // Yearly selected by default if it exists
    var picked by remember(plans) { mutableIntStateOf(plans.indexOfFirst { it.period == "P1Y" }.coerceAtLeast(0)) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            GlassIconButton(Icons.Filled.Close, "Close", { vm.dismissPaywall() })
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(p.gold, p.goldDeep)))
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.5f), 0.5f to Color.Transparent)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (state.isPro) "You're Pro" else "Space Bachao Pro",
            style = MaterialTheme.typography.displaySmall,
            color = p.label,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            paywall.reason ?: "Compress unlimited photos, videos and PDFs.",
            style = MaterialTheme.typography.bodyLarge,
            color = p.secondary,
        )
        Spacer(Modifier.height(20.dp))

        GlassCard(strong = true) {
            Text("PRO", style = MaterialTheme.typography.labelLarge, color = p.goldDeep)
            Bullet("Unlimited files, any size", p.goldDeep)
            Bullet("High quality and Max saving modes", p.goldDeep)
            Bullet("Keep originals in the Bin for 15 or 30 days", p.goldDeep)
            Bullet("All future features, no ads", p.goldDeep)
        }
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text("FREE", style = MaterialTheme.typography.labelLarge, color = p.secondary)
            Text(
                "Compress up to ${FREE_LIMIT_BYTES.readableSize()} of files (photos, videos and PDFs together). " +
                    "Used: ${state.freeUsed.readableSize()}.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.label,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(18.dp))

        if (state.isPro) {
            GlossyButton(
                if (state.devBuild) "Developer build · Pro unlocked" else "Thanks for supporting!",
                { vm.dismissPaywall() },
                Modifier.fillMaxWidth(),
            )
        } else {
            if (plans.isEmpty()) {
                Text(
                    "Plans load from Google Play. Install the app from the Play Store to subscribe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.secondary,
                )
                Spacer(Modifier.height(10.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    plans.forEachIndexed { i, plan ->
                        PlanCard(plan, selected = i == picked, best = plan.period == "P1Y" && plans.size > 1) {
                            picked = i
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            val plan = plans.getOrNull(picked)
            GlossyButton(
                when {
                    plan == null -> "Subscribe"
                    plan.trialLabel != null -> "Start ${plan.trialLabel}"
                    else -> "Subscribe · ${plan.price}/${plan.periodLabel}"
                },
                { activity?.let { vm.subscribe(it, plan) } },
                Modifier.fillMaxWidth(),
                colors = listOf(p.gold, p.goldDeep),
            )
            if (paywall.reason != null && paywall.freeItems.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    "Compress ${paywall.freeItems.size} files that fit for free",
                    { vm.continueFree() },
                    Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
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
            Text(
                "Subscription renews automatically until cancelled. Cancel anytime in Google Play › " +
                    "Payments & subscriptions. Your files never leave your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = p.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlanCard(plan: ProPlan, selected: Boolean, best: Boolean, onClick: () -> Unit) {
    val p = LocalApple.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(p, shape, strong = true)
            .border(if (selected) 2.dp else 0.dp, if (selected) p.goldDeep else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) p.goldDeep else p.track),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.title, style = MaterialTheme.typography.titleMedium, color = p.label)
                if (best) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "BEST VALUE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(p.green)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            plan.trialLabel?.let {
                Text("$it, then ${plan.price}/${plan.periodLabel}", style = MaterialTheme.typography.bodySmall, color = p.secondary)
            }
        }
        Text(
            "${plan.price}/${plan.periodLabel}",
            style = MaterialTheme.typography.titleMedium,
            color = p.label,
        )
    }
}
