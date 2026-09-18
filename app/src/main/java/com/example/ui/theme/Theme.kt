package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    onPrimary = TextPrimary,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = TextPrimary
)

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    isBlurred: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_bg_anim")
    
    val blob1OffsetX by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "l_blob1X"
    )
    val blob1OffsetY by infiniteTransition.animateFloat(
        initialValue = -120f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "l_blob1Y"
    )
    
    val blob2OffsetX by infiniteTransition.animateFloat(
        initialValue = 150f,
        targetValue = -180f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "l_blob2X"
    )
    val blob2OffsetY by infiniteTransition.animateFloat(
        initialValue = -90f,
        targetValue = 160f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "l_blob2Y"
    )

    val blob3OffsetX by infiniteTransition.animateFloat(
        initialValue = -160f,
        targetValue = 110f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "l_blob3X"
    )
    val blob3OffsetY by infiniteTransition.animateFloat(
        initialValue = 250f,
        targetValue = -90f,
        animationSpec = infiniteRepeatable(
            animation = tween(21000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "l_blob3Y"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060814), // iOS midnight space canvas
                        Color(0xFF0D1226), // Deep sapphire obsidian
                        Color(0xFF04060C)  // Pure deep night floor
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Blob 1: iOS System Indigo / Deep Orchid Liquid Mesh
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF5E5CE6).copy(alpha = 0.42f), Color.Transparent),
                    radius = width * 1.10f
                ),
                center = androidx.compose.ui.geometry.Offset(
                    width * 0.15f + blob1OffsetX, 
                    height * 0.22f + blob1OffsetY
                ),
                radius = width * 1.10f
            )

            // Blob 2: iOS System Purple / Vibrant Pink Liquid Mesh
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFBF5AF2).copy(alpha = 0.35f), Color.Transparent),
                    radius = width * 1.20f
                ),
                center = androidx.compose.ui.geometry.Offset(
                    width * 0.88f + blob2OffsetX, 
                    height * 0.75f + blob2OffsetY
                ),
                radius = width * 1.20f
            )

            // Blob 3: iOS Electric Azure / Sky Light Mesh
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0A84FF).copy(alpha = 0.38f), Color.Transparent),
                    radius = width * 0.95f
                ),
                center = androidx.compose.ui.geometry.Offset(
                    width * 0.82f + blob3OffsetX, 
                    height * 0.18f + blob3OffsetY
                ),
                radius = width * 0.95f
            )

            // Blob 4: iOS System Cyan / Mint Liquid Sheen
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF63E6E2).copy(alpha = 0.25f), Color.Transparent),
                    radius = width * 0.85f
                ),
                center = androidx.compose.ui.geometry.Offset(
                    width * 0.25f - blob2OffsetX, 
                    height * 0.60f - blob1OffsetY
                ),
                radius = width * 0.85f
            )
        }

        // Apple style subtle glass vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000)),
                        radius = 2200f
                    )
                )
        )

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isBlurred) Modifier.blur(20.dp) else Modifier)
        ) {
            content()
        }
    }
}

/**
 * Reusable iOS Liquid Glass Modifier providing multi-layer frosted acrylic backings,
 * specular dual-reflection gradient border, and rounded squircle shape.
 */
fun Modifier.iosLiquidGlass(
    cornerRadius: Dp = 22.dp,
    surfaceColor: Color = Color(0x3D1A223D),
    borderAlpha: Float = 0.24f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        Brush.verticalGradient(
            colors = listOf(
                surfaceColor.copy(alpha = (surfaceColor.alpha * 1.15f).coerceAtMost(0.95f)),
                surfaceColor.copy(alpha = (surfaceColor.alpha * 0.85f).coerceAtLeast(0.15f))
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = (borderAlpha * 1.5f).coerceAtMost(0.75f)),
                Color.White.copy(alpha = (borderAlpha * 0.6f).coerceAtLeast(0.08f))
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

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
