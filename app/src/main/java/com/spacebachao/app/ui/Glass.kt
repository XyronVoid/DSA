package com.spacebachao.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spacebachao.app.ui.theme.LocalPalette
import com.spacebachao.app.ui.theme.Palette

/** Flat premium card: matte surface with a thin border. */
fun Modifier.glass(
    p: Palette,
    shape: Shape = RoundedCornerShape(22.dp),
    strong: Boolean = false,
    @Suppress("UNUSED_PARAMETER") elevation: Dp = 0.dp,
): Modifier = this
    .clip(shape)
    .background(if (strong) p.card2 else p.card)
    .border(1.dp, p.border, shape)

/** Plain matte black background. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
    ) { content() }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    shape: Shape = RoundedCornerShape(22.dp),
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .glass(p, shape, strong)
            .padding(padding),
        content = content,
    )
}

/** Small caps label, CRED style. */
@Composable
fun Caps(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color ?: p.secondary, modifier = modifier)
}

/** Segmented control with a sliding off-white pill. */
@Composable
fun IOSSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(p.card)
            .border(1.dp, p.border, CircleShape)
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, spring(dampingRatio = 0.85f, stiffness = 500f), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(p.thumb)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (i == selected) p.onThumb else p.secondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Main button: solid off-white with black text (or gold when colors are given). */
@Composable
fun GlossyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    colors: List<Color>? = null,
    textColor: Color? = null,
    height: Dp = 56.dp,
) {
    val p = LocalPalette.current
    val c = colors ?: listOf(p.button, p.button)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val fg = textColor ?: p.onButton
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(c))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = fg,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Secondary button: dark with a thin border. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    height: Dp = 52.dp,
) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .border(1.dp, p.border, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = textColor ?: p.label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Round icon button for the top bar. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null,
    badge: String? = null,
) {
    val p = LocalPalette.current
    Box {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(p.card)
                .border(1.dp, p.border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint ?: p.label, modifier = Modifier.size(20.dp))
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(p.accent)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(badge, color = p.onButton, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Large bold title with optional back button and trailing actions. */
@Composable
fun LargeTitle(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = p.label,
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        )
    }
}

/** Thin progress bar. */
@Composable
fun CapsuleBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp, colors: List<Color>? = null) {
    val p = LocalPalette.current
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(p.track)
    ) {
        if (anim > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(anim)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(colors ?: listOf(p.goldDeep, p.gold)))
            )
        }
    }
}

/** Storage bar split into coloured parts (fractions of the whole). */
@Composable
fun SegmentBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val p = LocalPalette.current
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(p.track, cornerRadius = r)
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, size.height / 2, size.height / 2))
        }
        clipPath(clip) {
            var x = 0f
            val gap = 2.dp.toPx()
            parts.forEach { (fraction, color) ->
                val w = (fraction.coerceIn(0f, 1f) * size.width)
                if (w > 0.5f) {
                    drawRect(color, topLeft = Offset(x, 0f), size = Size((w - gap).coerceAtLeast(1f), size.height))
                    x += w
                }
            }
        }
    }
}

/** Circular progress ring. progress = null spins (unknown progress). */
@Composable
fun GradientRing(progress: Float?, size: Dp, stroke: Dp, colors: List<Color>) {
    val p = LocalPalette.current
    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rotation")
    val animated by animateFloatAsState(progress ?: 0f, label = "ring")
    Canvas(Modifier.size(size)) {
        val sw = stroke.toPx()
        val topLeft = Offset(sw / 2, sw / 2)
        val arc = Size(this.size.width - sw, this.size.height - sw)
        drawArc(p.track, 0f, 360f, false, topLeft = topLeft, size = arc, style = Stroke(sw))
        val start = if (progress == null) rotation - 90f else -90f
        val sweep = if (progress == null) 100f else 360f * animated.coerceIn(0.001f, 1f)
        rotate(start) {
            drawArc(
                brush = Brush.sweepGradient(colors),
                startAngle = 0f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
    }
}

/** Thin separator line used inside grouped lists. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(p.separator)
    )
}
