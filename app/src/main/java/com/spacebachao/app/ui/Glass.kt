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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spacebachao.app.ui.theme.LocalPalette
import com.spacebachao.app.ui.theme.Palette

/** Card: deep navy surface, soft top light and a thin glowing rim. */
fun Modifier.glass(
    p: Palette,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    @Suppress("UNUSED_PARAMETER") elevation: Dp = 0.dp,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (strong) listOf(p.card2, p.card) else listOf(p.card.copy(alpha = 0.92f), p.card.copy(alpha = 0.80f))
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.03f), p.gradC.copy(alpha = 0.18f))),
        shape,
    )

/** Navy background with a teal glow at the top and a purple glow at the bottom (like the ad). */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun glow(color: Color, cx: Float, cy: Float, radius: Float) {
                    val c = Offset(cx, cy)
                    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = radius), radius, c)
                }
                glow(p.glowTeal, w * 0.15f, h * 0.10f, w * 0.95f)
                glow(p.glowPurple, w * 0.95f, h * 0.88f, w * 1.05f)
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
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .glass(p, shape, strong)
            .padding(padding),
        content = content,
    )
}

/** Small caps label. */
@Composable
fun Caps(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color ?: p.secondary, modifier = modifier)
}

/** Text painted with the brand gradient. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun GradientText(text: String, style: TextStyle, modifier: Modifier = Modifier, colors: List<Color>? = null) {
    val p = LocalPalette.current
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(brush = Brush.linearGradient(colors ?: p.brand))) { append(text) }
        },
        style = style,
        modifier = modifier,
        maxLines = 1,
    )
}

/** "Space Bachao" with "Bachao" in the brand gradient, like the logo. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun Wordmark(style: TextStyle, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = p.label)) { append("Space ") }
            withStyle(SpanStyle(brush = Brush.linearGradient(p.brand))) { append("Bachao") }
        },
        style = style,
        modifier = modifier,
        maxLines = 1,
    )
}

/** The app logo: gradient rounded square with two arrows pressing towards a line. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(
        modifier
            .size(size)
            .shadow(size / 4, RoundedCornerShape(size * 0.26f), clip = false, ambientColor = p.gradC, spotColor = p.gradC)
    ) {
        val s = this.size.minDimension
        val r = CornerRadius(s * 0.26f, s * 0.26f)
        drawRoundRect(brush = Brush.linearGradient(p.logo, start = Offset.Zero, end = Offset(s, s)), cornerRadius = r)
        drawRoundRect(
            brush = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.25f), 0.55f to Color.Transparent),
            cornerRadius = r,
        )
        val w = s * 0.075f
        val c = Color.White
        val cx = s / 2
        // middle line
        drawLine(c, Offset(s * 0.27f, s * 0.5f), Offset(s * 0.73f, s * 0.5f), w, StrokeCap.Round)
        // top arrow pointing down
        drawLine(c, Offset(cx, s * 0.17f), Offset(cx, s * 0.40f), w, StrokeCap.Round)
        drawLine(c, Offset(cx - s * 0.12f, s * 0.29f), Offset(cx, s * 0.41f), w, StrokeCap.Round)
        drawLine(c, Offset(cx + s * 0.12f, s * 0.29f), Offset(cx, s * 0.41f), w, StrokeCap.Round)
        // bottom arrow pointing up
        drawLine(c, Offset(cx, s * 0.83f), Offset(cx, s * 0.60f), w, StrokeCap.Round)
        drawLine(c, Offset(cx - s * 0.12f, s * 0.71f), Offset(cx, s * 0.59f), w, StrokeCap.Round)
        drawLine(c, Offset(cx + s * 0.12f, s * 0.71f), Offset(cx, s * 0.59f), w, StrokeCap.Round)
    }
}

/** Segmented control with a sliding gradient pill. */
@Composable
fun IOSSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
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
                .background(Brush.horizontalGradient(p.brand))
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
                        color = if (i == selected) Color.White else p.secondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Main button: gradient pill with a soft glow (like "Download Now"). */
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
    val c = colors ?: p.brand
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val fg = textColor ?: Color.White
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(18.dp, CircleShape, clip = false, ambientColor = c.last(), spotColor = c.last())
            .clip(CircleShape)
            .background(Brush.horizontalGradient(c))
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.5f to Color.Transparent))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp),
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

/** Secondary button: dark pill with a gradient rim. */
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
            .clip(CircleShape)
            .background(p.card)
            .border(1.dp, Brush.horizontalGradient(p.brand.map { it.copy(alpha = 0.6f) }), CircleShape)
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
                    .background(Brush.horizontalGradient(p.brand))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(badge, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Large bold title with optional back button and trailing actions. brand = logo + gradient wordmark. */
@Composable
fun LargeTitle(
    title: String,
    onBack: (() -> Unit)? = null,
    brand: Boolean = false,
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
            if (brand) AppLogo(40.dp)
            Spacer(Modifier.weight(1f))
            actions()
        }
        if (brand) {
            Wordmark(MaterialTheme.typography.displaySmall, Modifier.padding(top = 12.dp, bottom = 16.dp))
        } else {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = p.label,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
            )
        }
    }
}

/** Thin progress bar with brand gradient. */
@Composable
fun CapsuleBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 7.dp, colors: List<Color>? = null) {
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
                    .background(Brush.horizontalGradient(colors ?: p.brand))
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
