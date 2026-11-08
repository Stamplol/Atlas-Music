package com.atlas.music.ui.theme

import androidx.compose.ui.graphics.Color

// Atlas Dark — "deep observatory". One light source (the Atlas gold sun), one accent.
// Planes: background (void) → surface (panel) → highest (floating chrome).
object AtlasDark {
    val Background = Color(0xFF0B0D12)
    val Surface = Color(0xFF12151D)
    val SurfaceElevated = Color(0xFF181D29)
    val SurfaceHighest = Color(0xFF212839)
    val OnBackground = Color(0xFFF4F1E8)
    val OnSurfaceMuted = Color(0xFFA8A79C)
    val Accent = Color(0xFFE5A83D)      // Atlas gold — the one accent
    val AccentBright = Color(0xFFF5C96B)
    val AccentDim = Color(0xFF9A7420)
    val OnAccent = Color(0xFF241A05)
    val Error = Color(0xFFFF8A7A)
    val Scrim = Color(0xA6000000)
    // Ambient gold wash behind the art on Now Playing.
    val AuraTop = Color(0xFF1B2233)
    // Glow / shadow spot — single light source (gold sun) for all shadows.
    val Glow = Color(0xFFE5A83D)
    val ScrimSoft = Color(0x3394E2D5)
}

// Atlas Light — warm paper, not clinical white.
object AtlasLight {
    val Background = Color(0xFFFAF7EF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceElevated = Color(0xFFF0EDE1)
    val SurfaceHighest = Color(0xFFE4E1D4)
    val OnBackground = Color(0xFF1A1C22)
    val OnSurfaceMuted = Color(0xFF6B6A63)
    val Accent = Color(0xFF9A6B12)      // deep brass — passes AA on paper
    val AccentBright = Color(0xFFB78A26)
    val AccentDim = Color(0xFF6E4F0D)
    val OnAccent = Color(0xFFFFF6DE)
    val Error = Color(0xFFB3261E)
    val Scrim = Color(0x66000000)
    val AuraTop = Color(0xFFE9E4D6)
    val Glow = Color(0xFF9A6B12)
    val ScrimSoft = Color(0x22F9E2AF)
}
