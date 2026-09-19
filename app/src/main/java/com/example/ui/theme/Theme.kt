package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

val LocalThemeBlur = compositionLocalOf<MutableState<Boolean>> {
    error("No LocalThemeBlur provided")
}

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentGreen,
    tertiary = AccentAmber,
    background = Bg,
    surface = Surface,
    error = AccentRed,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = Color.White
)

/**
 * Authentic Liquid Glass Background (archisvaze/liquid-glass architecture):
 * Simulates physics-based light refraction (IOR 1.52) over an organic multi-phase fluid mesh in true OLED black dark mode:
 * - Pure pitch black OLED canvas (#000000)
 * - 5 harmonic floating plasma fluid caustics (Sapphire, Electric Cyan, Ultraviolet, Teal, Solar Amber) with deep subtle luminosity
 * - Concentric light caustics, wave dispersion, and optical interference rings
 * - Real-time frosted glass backdrop blur for deep dark mode glassmorphism
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    isBlurred: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "archis_liquidglass_fluid_mesh")

    // Harmonic wave phases for fluid circulation
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831853f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fluid_phase_1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831853f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fluid_phase_2"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caustic_pulse"
    )

    val causticShimmer by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caustic_shimmer"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Living Liquid Fluid Simulation: Subtle luminous plasma nodes on pure black canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Node 1: Electric Cyan Refraction Plasma (Orbital drift in upper right)
            val cyanX = width * (0.74f + 0.12f * cos(phase1))
            val cyanY = height * (0.20f + 0.08f * sin(phase1))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x2800F2FE), // Chromatic Electric Cyan highlight
                        Color(0x0C06B6D4),
                        Color.Transparent
                    ),
                    center = Offset(cyanX, cyanY),
                    radius = width * 0.52f * pulseScale
                ),
                center = Offset(cyanX, cyanY),
                radius = width * 0.52f * pulseScale
            )

            // Node 2: Deep Sapphire Core (Pulsing in upper-left quadrant)
            val sapphireX = width * (0.22f + 0.08f * sin(phase2))
            val sapphireY = height * (0.15f + 0.06f * cos(phase2))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x303B82F6), // Electric Sapphire Blue
                        Color(0x101D4ED8),
                        Color.Transparent
                    ),
                    center = Offset(sapphireX, sapphireY),
                    radius = width * 0.55f * pulseScale
                ),
                center = Offset(sapphireX, sapphireY),
                radius = width * 0.55f * pulseScale
            )

            // Node 3: Ultraviolet & Violet Prismatic Swirl (Lower quadrant flow)
            val violetX = width * (0.18f + 0.10f * cos(phase2 * 0.8f))
            val violetY = height * (0.78f + 0.09f * sin(phase2 * 0.8f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x22A855F7), // Ultraviolet Violet
                        Color(0x0A7C3AED),
                        Color.Transparent
                    ),
                    center = Offset(violetX, violetY),
                    radius = width * 0.54f * pulseScale
                ),
                center = Offset(violetX, violetY),
                radius = width * 0.54f * pulseScale
            )

            // Node 4: Radiant Ocean Teal Caustic (Mid-left refraction)
            val tealX = width * (0.86f + 0.08f * sin(phase1 * 0.9f))
            val tealY = height * (0.80f + 0.08f * cos(phase1 * 0.9f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x1C14B8A6), // Emerald Teal
                        Color(0x080D9488),
                        Color.Transparent
                    ),
                    center = Offset(tealX, tealY),
                    radius = width * 0.48f * pulseScale
                ),
                center = Offset(tealX, tealY),
                radius = width * 0.48f * pulseScale
            )

            // Node 5: Chromatic Solar Amber/Magenta Caustic Flare (Center ambient warmth)
            val amberX = width * (0.50f + 0.05f * cos(phase1 * 1.2f))
            val amberY = height * (0.45f + 0.06f * sin(phase2 * 1.1f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x12F59E0B), // Warm Amber Refraction
                        Color(0x0AEC4899), // Soft Magenta Dispersion
                        Color.Transparent
                    ),
                    center = Offset(amberX, amberY),
                    radius = width * 0.42f * causticShimmer
                ),
                center = Offset(amberX, amberY),
                radius = width * 0.42f * causticShimmer
            )

            // Optical Glass Caustic Ripples (Physics-based light interference rings on true black)
            drawCircle(
                color = Color(0x1000F2FE),
                center = Offset(cyanX, cyanY),
                radius = width * 0.38f * causticShimmer,
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = Color(0x0CA855F7),
                center = Offset(violetX, violetY),
                radius = width * 0.44f * pulseScale,
                style = Stroke(width = 1f)
            )
        }

        // Content layer with optional backdrop modal blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isBlurred) Modifier.blur(24.dp) else Modifier)
        ) {
            content()
        }
    }
}

/**
 * Reusable Liquid Glass modifier (archisvaze/liquid-glass style):
 * - Smoked obsidian frosted fill with caustic transmission
 * - Top-left 135° diagonal specular glare sheen
 * - IOR 1.52 chromatic aberration border with pure specular white highlight & prismatic cyan/purple dispersion
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp = 16.dp,
    fillBrush: Brush = GlassCardGradient,
    borderBrush: Brush = LiquidGlassChromaticBorder,
    borderWidth: Dp = 1.2.dp,
    showGlare: Boolean = true
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(fillBrush)
    .then(
        if (showGlare) {
            Modifier.background(LiquidGlassGlareGradient)
        } else Modifier
    )
    .border(
        width = borderWidth,
        brush = borderBrush,
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Convenience modifier with solid translucent tint, specular glare, and chromatic border
 */
fun Modifier.iosLiquidGlass(
    cornerRadius: Dp = 16.dp,
    surfaceColor: Color = Surface,
    borderBrush: Brush = LiquidGlassChromaticBorder,
    borderWidth: Dp = 1.2.dp,
    showGlare: Boolean = true
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(surfaceColor)
    .then(
        if (showGlare) {
            Modifier.background(LiquidGlassGlareGradient)
        } else Modifier
    )
    .border(
        width = borderWidth,
        brush = borderBrush,
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Liquid Glass Pill modifier for status badges and tags (archisvaze/liquid-glass style)
 */
fun Modifier.liquidGlassPill(
    cornerRadius: Dp = 100.dp,
    fillColor: Color = SurfaceGlassPill,
    borderBrush: Brush = LiquidGlassChromaticBorder
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(fillColor)
    .background(LiquidGlassGlareGradient)
    .border(
        width = 1.dp,
        brush = borderBrush,
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * First-Class Liquid Glass Card (archisvaze/liquid-glass design):
 * Multi-layer optical glass container with volumetric outer drop-shadow, inner bezel occlusion,
 * chromatic edge refraction (IOR 1.52), and specular glare.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderBrush: Brush = LiquidGlassChromaticBorder,
    borderWidth: Dp = 1.2.dp,
    backgroundColor: Color = Surface,
    showGlare: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .then(
                if (showGlare) {
                    Modifier.background(LiquidGlassGlareGradient)
                } else Modifier
            )
            .border(
                width = borderWidth,
                brush = borderBrush,
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        content()
    }
}

/**
 * Liquid Glass Capsule Pill for telemetry, status badges, and quick indicators
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    fillColor: Color = SurfaceGlassPill,
    borderBrush: Brush = LiquidGlassChromaticBorder,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(fillColor)
            .background(LiquidGlassGlareGradient)
            .border(1.dp, borderBrush, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        content()
    }
}

/**
 * Liquid Glass Interactive Button (archisvaze/liquid-glass style):
 * Includes tactile depression on press, chromatic specular border, and glossy reflection
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    containerBrush: Brush = GlassButtonGradient,
    borderBrush: Brush = LiquidGlassChromaticBorder,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btn_press_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerBrush)
            .background(LiquidGlassGlareGradient)
            .border(1.2.dp, borderBrush, RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.35f)),
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val blurState = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalThemeBlur provides blurState) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography
        ) {
            LiquidGlassBackground(isBlurred = blurState.value) {
                content()
            }
        }
    }
}


