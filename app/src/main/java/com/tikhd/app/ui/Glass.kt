package com.tikhd.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

object Palette {
    val Bg1 = Color(0xFFFFFFFF)
    val Bg2 = Color(0xFFEFF3FA)
    val Ink = Color(0xFF1B1F2A)
    val InkSoft = Color(0xFF6B7280)
    val Teal = Color(0xFF14B8A6)
    val Violet = Color(0xFF7C5CFF)
    val Pink = Color(0xFFFF3B6B)
    val Blue = Color(0xFF2F80FF)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFEF4444)
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** Clean white background with slow-moving pastel light blobs (gives the glass something to refract). */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val b by t.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.Bg1, Palette.Bg2)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            blob(Palette.Teal.copy(alpha = 0.30f), Offset(w * (0.10f + 0.40f * a), h * (0.10f + 0.10f * b)), w * 0.70f)
            blob(Palette.Violet.copy(alpha = 0.26f), Offset(w * (0.90f - 0.35f * b), h * (0.35f + 0.12f * a)), w * 0.75f)
            blob(Palette.Pink.copy(alpha = 0.22f), Offset(w * (0.20f + 0.30f * b), h * (0.78f - 0.10f * a)), w * 0.70f)
            blob(Palette.Blue.copy(alpha = 0.20f), Offset(w * (0.85f - 0.30f * a), h * (0.92f - 0.08f * b)), w * 0.60f)
        }
        content()
    }
}

/** Frosted "liquid glass" surface: translucent white, bright rim, specular highlight, soft shadow. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    tint: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "press"
    )
    var m = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(
            elevation = 18.dp,
            shape = shape,
            ambientColor = Color(0x1A1B1F2A),
            spotColor = Color(0x2B1B1F2A)
        )
        .clip(shape)
        .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.50f))))
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.95f), Color.Transparent),
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.width * 0.95f
                )
            )
        }
        .border(
            1.2.dp,
            Brush.linearGradient(listOf(Color.White, Color(0x55FFFFFF), Color.White)),
            shape
        )
    if (onClick != null) {
        m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    Box(m, content = content)
}

/** Fade + rise + slight scale entrance, staggered by [index]. */
@Composable
fun Appear(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80L + index * 90L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(420)) +
            slideInVertically(tween(480)) { it / 4 } +
            scaleIn(tween(480), initialScale = 0.92f)
    ) {
        content()
    }
}

@Composable
fun GlassTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCard(corner = 22.dp, onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Kembali",
                tint = Palette.Ink,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(22.dp)
            )
        }
        Text(
            title,
            color = Palette.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

/** Solid gradient action button with press animation. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Palette.Violet, Palette.Blue),
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "btn"
    )
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .shadow(14.dp, shape, ambientColor = colors.first().copy(alpha = 0.25f), spotColor = colors.first().copy(alpha = 0.45f))
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.6f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Glass progress bar, [progress] in 0..1. */
@Composable
fun GlassProgress(progress: Float, modifier: Modifier = Modifier, colors: List<Color> = listOf(Palette.Teal, Palette.Blue)) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "prog")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.7f))
            .border(1.dp, Color.White, shape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(12.dp)
                .clip(shape)
                .background(Brush.horizontalGradient(colors))
        )
    }
}

@Composable
fun IconBubble(color: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.65f))))
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
        content = content
    )
}
