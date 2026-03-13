package com.assistant.core.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─── CORNER BRACKETS ─────────────────────────────────────────────────────────
// Matches design language "corner bracket" decoration — sharp, 2px, no radius

enum class CornerBracketStyle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, ALL }

fun Modifier.cornerBrackets(
    color: Color,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.dp,
    style: CornerBracketStyle = CornerBracketStyle.ALL,
): Modifier = this.drawBehind {
    val s = size.toPx()
    val sw = strokeWidth.toPx()
    val w = this.size.width
    val h = this.size.height

    fun DrawScope.tl() {
        drawLine(color, Offset(0f, 0f), Offset(s, 0f), sw)
        drawLine(color, Offset(0f, 0f), Offset(0f, s), sw)
    }
    fun DrawScope.tr() {
        drawLine(color, Offset(w, 0f), Offset(w - s, 0f), sw)
        drawLine(color, Offset(w, 0f), Offset(w, s), sw)
    }
    fun DrawScope.bl() {
        drawLine(color, Offset(0f, h), Offset(s, h), sw)
        drawLine(color, Offset(0f, h), Offset(0f, h - s), sw)
    }
    fun DrawScope.br() {
        drawLine(color, Offset(w, h), Offset(w - s, h), sw)
        drawLine(color, Offset(w, h), Offset(w, h - s), sw)
    }

    when (style) {
        CornerBracketStyle.TOP_LEFT     -> tl()
        CornerBracketStyle.TOP_RIGHT    -> tr()
        CornerBracketStyle.BOTTOM_LEFT  -> bl()
        CornerBracketStyle.BOTTOM_RIGHT -> br()
        CornerBracketStyle.ALL          -> { tl(); tr(); bl(); br() }
    }
}

// ─── SCANLINE OVERLAY ────────────────────────────────────────────────────────

fun Modifier.scanlineOverlay(
    color: Color = Color(0xFF43B8C4),
    alpha: Float = 0.012f,
    lineSpacing: Float = 4f,
): Modifier = this.drawBehind {
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += lineSpacing
    }
}

// ─── PULSE DOT ───────────────────────────────────────────────────────────────

@Composable
fun PulseDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAlpha"
    )
    val glowSize by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = size.value * 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowSize"
    )

    Box(
        modifier = modifier.size(size * 3),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // glow ring
            drawCircle(
                color = color.copy(alpha = glowAlpha * 0.4f),
                radius = glowSize.dp.toPx(),
            )
            // solid dot
            drawCircle(
                color = color,
                radius = (size / 2).toPx(),
            )
        }
    }
}

// ─── HUD DIVIDER ─────────────────────────────────────────────────────────────

@Composable
fun HudDivider(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.3f))
        )
        androidx.compose.material3.Text(
            text = label,
            style = AssistantTheme.typography.hudSmall,
            color = color.copy(alpha = 0.7f),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.3f))
        )
    }
}

// ─── GEO BACKGROUND SHAPES ───────────────────────────────────────────────────
// Large decorative shapes at 4–12% opacity for background depth layers

fun Modifier.geoBackground(
    color: Color,
    alpha: Float = 0.06f,
): Modifier = this.drawBehind {
    drawRect(color = color.copy(alpha = alpha))
}

// ─── HUD STATUS BADGE ────────────────────────────────────────────────────────

enum class HudBadgeState { ONLINE, PROCESSING, WARNING, ALERT, ERROR, OFFLINE }

@Composable
fun hudBadgeColor(state: HudBadgeState): Color {
    val c = AssistantTheme.colors
    return when (state) {
        HudBadgeState.ONLINE      -> c.accent
        HudBadgeState.PROCESSING  -> c.purple
        HudBadgeState.WARNING     -> c.yellow
        HudBadgeState.ALERT       -> c.orange
        HudBadgeState.ERROR       -> c.red
        HudBadgeState.OFFLINE     -> c.textMuted
    }
}
