package com.assistant.feature.aichat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assistant.core.theme.AssistantTheme
import kotlin.math.*

/**
 * Deterministic generative icon — 4 layered angular SVG-style shapes.
 * Same name always produces the same icon (djb2 hash seed).
 * Rings rotate counter-clockwise, speed is seed-derived.
 * During [isActive], rotation speeds increase and core glows.
 */
@Composable
fun DynamicIcon(
    name: String,
    size: Dp = 32.dp,
    isActive: Boolean = false,
    backgroundColor: Color = AssistantTheme.colors.accentText,
    modifier: Modifier = Modifier,
) {
    val seed = djb2Hash(name)
    val colors = AssistantTheme.colors

    // Derive rotation speeds from seed (different bit ranges per layer)
    val l1SpeedBase = 6f + (seed and 0xFF).toFloat() / 255f * 8f   // 6–14s
    val l2SpeedBase = 10f + ((seed shr 8) and 0xFF).toFloat() / 255f * 10f  // 10–20s
    val l3SpeedBase = 18f + ((seed shr 16) and 0xFF).toFloat() / 255f * 12f // 18–30s

    val l1Speed = if (isActive) l1SpeedBase / 1.4f else l1SpeedBase
    val l2Speed = if (isActive) l2SpeedBase / 1.3f else l2SpeedBase
    val l3Speed = if (isActive) l3SpeedBase / 1.2f else l3SpeedBase

    // Infinite rotation animations
    val infiniteTransition = rememberInfiniteTransition(label = "icon_rotation")

    val l1Angle by infiniteTransition.animateFloat(
        initialValue = ((seed and 0xFF).toFloat() / 255f * 360f),
        targetValue  = ((seed and 0xFF).toFloat() / 255f * 360f) - 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween((l1Speed * 1000).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "l1"
    )
    val l2Angle by infiniteTransition.animateFloat(
        initialValue = (((seed shr 8) and 0xFF).toFloat() / 255f * 360f),
        targetValue  = (((seed shr 8) and 0xFF).toFloat() / 255f * 360f) - 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween((l2Speed * 1000).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "l2"
    )
    val l3Angle by infiniteTransition.animateFloat(
        initialValue = (((seed shr 16) and 0xFF).toFloat() / 255f * 360f),
        targetValue  = (((seed shr 16) and 0xFF).toFloat() / 255f * 360f) - 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween((l3Speed * 1000).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "l3"
    )

    // Core pulse when active
    val corePulse by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1.0f,
        animationSpec = if (isActive) {
            infiniteRepeatable(
                animation = tween(1200, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse,
            )
        } else tween(300),
        label = "core_pulse"
    )

    // Derive colors per layer from seed
    val l1Color = deriveColor(seed, 1, isActive)
    val l2Color = deriveColor(seed, 2, isActive)
    val l3Color = deriveColor(seed, 3, isActive)
    val coreGlowColor = if (isActive) colors.accentText else Color.White

    Box(
        modifier = modifier
            .size(size)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.toPx() / 2
            val cy = size.toPx() / 2
            val maxR = minOf(cx, cy)

            // L3 — outer ring (90% of frame, faintest)
            rotate(l3Angle, Offset(cx, cy)) {
                drawAngularPolygon(
                    cx, cy,
                    radius     = maxR * 0.9f,
                    sides      = shapeForLayer(seed, 3),
                    color      = l3Color.copy(alpha = 0.2f + (seed and 0x1F).toFloat() / 255f * 0.2f),
                    strokeOnly = true,
                )
            }

            // L2 — mid ring (65% of frame)
            rotate(l2Angle, Offset(cx, cy)) {
                drawAngularPolygon(
                    cx, cy,
                    radius     = maxR * 0.65f,
                    sides      = shapeForLayer(seed, 2),
                    color      = l2Color.copy(alpha = 0.35f + ((seed shr 4) and 0x1F).toFloat() / 255f * 0.2f),
                    strokeOnly = true,
                )
            }

            // L1 — inner ring (40% of frame)
            rotate(l1Angle, Offset(cx, cy)) {
                drawAngularPolygon(
                    cx, cy,
                    radius     = maxR * 0.4f,
                    sides      = shapeForLayer(seed, 1),
                    color      = l1Color.copy(alpha = 0.55f + ((seed shr 12) and 0x1F).toFloat() / 255f * 0.2f),
                    strokeOnly = true,
                )
            }

            // L0 — static core (20% of frame)
            val coreR = maxR * 0.2f * corePulse
            if (isActive) {
                drawCircle(coreGlowColor.copy(alpha = 0.3f), radius = coreR * 1.8f, center = Offset(cx, cy))
            }
            drawAngularPolygon(
                cx, cy,
                radius     = coreR,
                sides      = shapeForLayer(seed, 0),
                color      = coreGlowColor,
                strokeOnly = false,
            )
        }
    }
}

// ─── HELPERS ─────────────────────────────────────────────────────────────────

private fun djb2Hash(str: String): Int {
    var hash = 5381
    for (c in str) {
        hash = (hash * 33) xor c.code
    }
    return hash
}

private fun shapeForLayer(seed: Int, layer: Int): Int {
    val shifted = seed shr (layer * 3)
    val pool = intArrayOf(3, 4, 5, 6, 4, 3) // triangle, square, pentagon, hexagon, square, triangle
    return pool[abs(shifted) % pool.size]
}

private fun deriveColor(seed: Int, layer: Int, isActive: Boolean): Color {
    val shifted = seed shr (layer * 8)
    val r = ((shifted and 0xFF) / 255f).coerceIn(0.2f, 0.9f)
    val g = (((shifted shr 4) and 0xFF) / 255f).coerceIn(0.2f, 0.9f)
    val b = (((shifted shr 8) and 0xFF) / 255f).coerceIn(0.3f, 1.0f)
    return if (isActive) Color(r * 0.5f + 0.26f, g * 0.5f + 0.36f, b * 0.5f + 0.38f)
    else Color(r, g, b)
}

private fun DrawScope.drawAngularPolygon(
    cx: Float, cy: Float,
    radius: Float, sides: Int,
    color: Color, strokeOnly: Boolean,
) {
    val path = Path()
    val angleStep = (2 * PI / sides).toFloat()
    for (i in 0 until sides) {
        val angle = i * angleStep - (PI / 2).toFloat()
        val px = cx + radius * cos(angle)
        val py = cy + radius * sin(angle)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()

    if (strokeOnly) {
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
    } else {
        drawPath(path, color)
    }
}
