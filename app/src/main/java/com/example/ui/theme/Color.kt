package com.example.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Design System Core Colors (Archis Vaze Liquid Glass - True OLED Black Dark Mode)
val Bg = Color(0xFF000000) // Pure Pitch Black (OLED True Black #000000)
val OverlayBg = Color(0xF2000000) // Pitch Black frosted modal backdrop

// Liquid Glass Card & Component Surfaces (Inspired by archisvaze/liquid-glass IOR 1.52 dark mode)
val Surface = Color(0xD40A0A0D) // Pure dark smoked obsidian glass (~83% opacity)
val SurfaceAlt = Color(0xEA121216) // Slightly elevated pure dark glass container
val SurfaceGlassCard = Color(0xD40A0A0D)
val SurfaceGlassPill = Color(0x99141418)
val SurfaceGlassHighlight = Color(0x2BFFFFFF)

// Border Strokes (Specular Chamfer & Refraction Highlights)
val Border = Color(0x33FFFFFF) // Subtle specular glass rim
val BorderSubtle = Color(0x1AFFFFFF)
val BorderHighlight = Color(0x75FFFFFF)

// Signature archisvaze/liquid-glass Physics-Based Refraction Border (IOR 1.52 + Chromatic Dispersion)
val LiquidGlassChromaticBorder = Brush.linearGradient(
    colors = listOf(
        Color(0xF5FFFFFF), // Grazing incident specular highlight (top-left)
        Color(0xDD00F2FE), // Chromatic cyan dispersion
        Color(0x663B82F6), // Optical sapphire refraction
        Color(0x99A855F7), // Prismatic ultraviolet aberration
        Color(0x30FFFFFF), // Bottom-right ambient refraction bounce
        Color(0x8800F2FE)  // Secondary return caustic reflection
    ),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// Archis Vaze Dual Specular Glare (Simulates optical surface reflection and light falloff)
val LiquidGlassGlareGradient = Brush.linearGradient(
    colors = listOf(
        Color(0x2EFFFFFF), // High-angle grazing specular reflection (top-left)
        Color(0x12FFFFFF),
        Color(0x02FFFFFF), // Mid-body transparent glass transmission
        Color(0x06FFFFFF),
        Color(0x1800F2FE)  // Soft cyan caustic illumination on refractive edge
    ),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// Archis Vaze Inner Occlusion Shadow (Simulates physical glass slab depth and bevel absorption)
val LiquidGlassInnerRimBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x50FFFFFF), // Crisp top specular bevel highlight
        Color(0x04FFFFFF),
        Color(0x10000000), // Inset ambient occlusion
        Color(0x22000000), // Bottom edge inner shadow
        Color(0x1800F2FE)  // Bottom edge subtle refraction glow
    )
)

// Specular Brushes for authentic Liquid Glass 3D Refraction with Chromatic Fringe
val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xEEFFFFFF), // Crisp specular reflection on top-left
        Color(0x8800F2FE), // Cyan chromatic fringe
        Color(0x25FFFFFF),
        Color(0x66A855F7), // Subtle violet refraction
        Color(0x45FFFFFF)  // Refraction bounce on bottom-right
    )
)

val GlassAccentBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xEEFFFFFF), // Crisp specular reflection
        Color(0xCC00F2FE), // Electric Cyan
        Color(0x773B82F6), // Electric Sapphire
        Color(0x331D4ED8),
        Color(0x8860A5FA)
    )
)

val GlassGreenBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xEEFFFFFF),
        Color(0xCC34D399), // Emerald Neon
        Color(0x6610B981),
        Color(0x25059669),
        Color(0x8834D399)
    )
)

val GlassRedBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xEEFFFFFF),
        Color(0xCCF87171), // Crimson Neon
        Color(0x77EF4444),
        Color(0x25B91C1C),
        Color(0x88F87171)
    )
)

val GlassAmberBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xEEFFFFFF),
        Color(0xCCFBBF24), // Amber Gold
        Color(0x77F59E0B),
        Color(0x25D97706),
        Color(0x88FBBF24)
    )
)

val GlassCardGradient = Brush.verticalGradient(
    listOf(
        Color(0xE6141418), // subtle specular light reflection at top of dark glass
        Color(0xD408080A)  // pure deep smoked obsidian tone at bottom
    )
)

val GlassButtonGradient = Brush.verticalGradient(
    listOf(
        Color(0xF02563EB),
        Color(0xDE1D4ED8)
    )
)

// System Accents (Vibrant Luminescent Neon for high contrast over dark glass)
val AccentBlue = Color(0xFF3B82F6) // Primary Accent: Clean Electric Blue
val AccentCyan = Color(0xFF00F2FE) // Chromatic cyan
val AccentRed = Color(0xFFEF4444) // Warning / Destructive: Crimson Red (Lockdown & Block)
val AccentGreen = Color(0xFF10B981) // Success / Telemetry: Mint Emerald (Online / synced)
val AccentAmber = Color(0xFFF59E0B) // Warning / connecting
val AccentIndigo = Color(0xFF6366F1)
val AccentPurple = Color(0xFF8B5CF6)
val AccentViolet = Color(0xFFA855F7)
val AccentTeal = Color(0xFF14B8A6)

// Typography Colors
val TextPrimary = Color(0xFFFFFFFF) // 100% White for titles
val TextSecondary = Color(0xFF94A3B8) // Slate for subtext, metadata, and timestamps
val TextTertiary = Color(0xFF64748B) // Muted slate
val TextMono = Color(0xFF10B981) // Mint Emerald terminal readout




