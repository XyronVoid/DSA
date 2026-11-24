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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spacebachao.app.ui.theme.ApplePalette
import com.spacebachao.app.ui.theme.LocalApple
import kotlin.math.max

/** Frosted glass panel: translucent fill + glossy top highlight + light rim. */
fun Modifier.glass(
    p: ApplePalette,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    elevation: Dp = 6.dp,
): Modifier = this
    .shadow(
        elevation = if (p.dark) 0.dp else elevation,
        shape = shape,
        clip = false,
        ambientColor = Color(0x14000000),
        spotColor = Color(0x1A000000),
    )
    .clip(shape)
    .background(if (strong) p.glassStrong else p.glass)
    .background(Brush.verticalGradient(0f to p.gloss, 0.5f to Color.Transparent))
    .border(1.dp, Brush.verticalGradient(listOf(p.rimTop, p.rimBottom)), shape)

/** Soft colourful background, like iOS wallpapers behind glass. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val p = LocalApple.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = max(w, h) * 0.55f
                fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
                    val c = Offset(cx, cy)
                    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = radius), radius, c)
                }
                blob(p.blobA, w * 0.0f, h * 0.05f, r)
                blob(p.blobB, w * 1.05f, h * 0.38f, r * 0.85f)
                blob(p.blobC, w * 0.15f, h * 0.98f, r * 0.9f)
            }
    ) { content() }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    shape: Shape = RoundedCornerShape(24.dp),
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalApple.current
    Column(
        modifier
            .fillMaxWidth()
            .glass(p, shape, strong)
            .padding(padding),
        content = content,
    )
}

/** iOS segmented control with a sliding white pill. */
@Composable
fun IOSSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalApple.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.track)
            .padding(2.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
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
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = p.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Big glossy capsule button (filled gradient with a shine on the top half). */
@Composable
fun GlossyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    colors: List<Color>? = null,
    textColor: Color = Color.White,
    height: Dp = 54.dp,
) {
    val p = LocalApple.current
    val c = colors ?: listOf(p.accentLight, p.accent)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(12.dp, CircleShape, clip = false, ambientColor = c.last(), spotColor = c.last())
            .clip(CircleShape)
            .background(Brush.verticalGradient(c))
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.40f), 0.5f to Color.White.copy(alpha = 0.06f), 0.51f to Color.Transparent))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.05f))), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Secondary capsule button made of glass. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    height: Dp = 50.dp,
) {
    val p = LocalApple.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glass(p, CircleShape, strong = true, elevation = 3.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor ?: p.accent, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** Round glass icon button (top bar). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null,
    badge: String? = null,
) {
    val p = LocalApple.current
    Box {
        Box(
            Modifier
                .size(42.dp)
                .glass(p, CircleShape, strong = true, elevation = 3.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint ?: p.label, modifier = Modifier.size(21.dp))
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(p.red)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(badge, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** iOS-style large title with optional back button and trailing actions. */
@Composable
fun LargeTitle(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalApple.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, tint = p.accent)
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = p.label,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
    }
}

/** Rounded capsule progress bar with gradient fill. */
@Composable
fun CapsuleBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, colors: List<Color>? = null) {
    val p = LocalApple.current
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
                    .background(Brush.horizontalGradient(colors ?: listOf(p.accentLight, p.accent)))
            )
        }
    }
}

/** Apple-Watch-style gradient ring. progress = null spins (unknown progress). */
@Composable
fun GradientRing(progress: Float?, size: Dp, stroke: Dp, colors: List<Color>) {
    val p = LocalApple.current
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

/** Small separator line used inside grouped lists. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val p = LocalApple.current
    Box(
        modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(p.separator)
    )
}
