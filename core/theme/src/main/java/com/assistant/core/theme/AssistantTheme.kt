package com.assistant.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── COLOR TOKENS ────────────────────────────────────────────────────────────

@Immutable
data class AssistantColors(
    val background: Color,
    val backgroundPanel: Color,
    val backgroundPanel2: Color,
    val accent: Color,
    val accentDim: Color,
    val accentGlow: Color,
    val accentText: Color,
    val yellow: Color,
    val yellowGlow: Color,
    val red: Color,
    val redGlow: Color,
    val purple: Color,
    val purpleGlow: Color,
    val blue: Color,
    val blueGlow: Color,
    val orange: Color,
    val orangeGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderHard: Color,
)

val DarkColors = AssistantColors(
    background      = Color(0xFF0D1010),
    backgroundPanel  = Color(0xFF161C1C),
    backgroundPanel2 = Color(0xFF1E2626),
    accent          = Color(0xFF43B8C4),
    accentDim       = Color(0xFF2E9AAA),
    accentGlow      = Color(0x2643B8C4),
    accentText      = Color(0xFF43B8C4),
    yellow          = Color(0xFFD4FF00),
    yellowGlow      = Color(0x1FD4FF00),
    red             = Color(0xFFFF3B55),
    redGlow         = Color(0x1FFF3B55),
    purple          = Color(0xFFB44FFF),
    purpleGlow      = Color(0x1FB44FFF),
    blue            = Color(0xFF3B8EFF),
    blueGlow        = Color(0x1F3B8EFF),
    orange          = Color(0xFFFF7A2F),
    orangeGlow      = Color(0x1FFF7A2F),
    textPrimary     = Color(0xFFE8F0F0),
    textSecondary   = Color(0xFFB0C4C4),
    textMuted       = Color(0xFF4A6A6A),
    border          = Color(0x3343B8C4),
    borderHard      = Color(0x8C43B8C4),
)

// ─── TYPOGRAPHY ──────────────────────────────────────────────────────────────

// Fonts are embedded in core:theme res/font/
// Barlow Condensed (display/headlines) + Share Tech Mono (HUD) + Barlow (body)
// These are loaded from assets; fallback to system sans-serif if unavailable.

object AssistantFonts {
    // Declared lazily — actual font files dropped in core/theme/src/main/res/font/
    val BarlowCondensed = FontFamily.Default   // replaced when font files present
    val ShareTechMono   = FontFamily.Monospace // replaced when font files present
    val Barlow          = FontFamily.Default   // replaced when font files present
}

@Immutable
data class AssistantTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val subhead: TextStyle,
    val body: TextStyle,
    val hud: TextStyle,
    val hudSmall: TextStyle,
    val hudLabel: TextStyle,
)

val DefaultTypography = AssistantTypography(
    display  = TextStyle(fontFamily = AssistantFonts.BarlowCondensed, fontWeight = FontWeight.Black,  fontSize = 52.sp, letterSpacing = 0.04.sp),
    headline = TextStyle(fontFamily = AssistantFonts.BarlowCondensed, fontWeight = FontWeight.Bold,   fontSize = 30.sp, letterSpacing = 0.06.sp),
    subhead  = TextStyle(fontFamily = AssistantFonts.BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.10.sp),
    body     = TextStyle(fontFamily = AssistantFonts.Barlow,          fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 23.8.sp),
    hud      = TextStyle(fontFamily = AssistantFonts.ShareTechMono,   fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.08.sp),
    hudSmall = TextStyle(fontFamily = AssistantFonts.ShareTechMono,   fontWeight = FontWeight.Normal, fontSize = 9.sp,  letterSpacing = 0.15.sp),
    hudLabel = TextStyle(fontFamily = AssistantFonts.ShareTechMono,   fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 0.12.sp),
)

// ─── SPACING ─────────────────────────────────────────────────────────────────

@Immutable
data class AssistantSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 52.dp,
    val borderRadius: Dp = 2.dp,   // HARD RULE: never exceed 2dp
)

// ─── COMPOSITION LOCALS ──────────────────────────────────────────────────────

val LocalAssistantColors     = staticCompositionLocalOf { DarkColors }
val LocalAssistantTypography = staticCompositionLocalOf { DefaultTypography }
val LocalAssistantSpacing    = staticCompositionLocalOf { AssistantSpacing() }

// ─── THEME ENTRY POINT ───────────────────────────────────────────────────────

object AssistantTheme {
    val colors: AssistantColors
        @Composable get() = LocalAssistantColors.current
    val typography: AssistantTypography
        @Composable get() = LocalAssistantTypography.current
    val spacing: AssistantSpacing
        @Composable get() = LocalAssistantSpacing.current
}

@Composable
fun AssistantTheme(
    colors: AssistantColors = DarkColors,
    typography: AssistantTypography = DefaultTypography,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAssistantColors provides colors,
        LocalAssistantTypography provides typography,
        LocalAssistantSpacing provides AssistantSpacing(),
    ) {
        content()
    }
}
