package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantState
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanGlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VisualizerOrb(
    state: AssistantState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbMotion")

    // Continuous rotation for outer cybernetic ring
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    is AssistantState.Thinking -> 1800
                    is AssistantState.Speaking -> 3200
                    is AssistantState.Listening -> 2400
                    else -> 7000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    // Counter-rotation for inner orbital ring
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    is AssistantState.Thinking -> 1200
                    is AssistantState.Speaking -> 2500
                    else -> 5000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    // Breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state is AssistantState.Thinking) 600 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingPulse"
    )

    // Dynamic scale driven by audio amplitude (RMS) or state pulse
    val targetAudioScale = remember(amplitude, state) {
        when (state) {
            is AssistantState.Listening -> 1.0f + (amplitude * 0.45f)
            is AssistantState.Speaking -> 1.0f + (amplitude * 0.35f)
            is AssistantState.Thinking -> 1.05f
            is AssistantState.Error -> 0.95f
            else -> 1.0f
        }
    }

    val animatedAudioScale by animateFloatAsState(
        targetValue = targetAudioScale,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "animatedAudioScale"
    )

    val primaryGlowColor by animateColorAsState(
        targetValue = state.glowColor,
        animationSpec = tween(durationMillis = 350),
        label = "primaryGlowColor"
    )

    val secondaryGlowColor by animateColorAsState(
        targetValue = when (state) {
            is AssistantState.Thinking -> NeonCyan
            is AssistantState.Listening -> NeonCyanGlow
            is AssistantState.Speaking -> ElectricBlue
            is AssistantState.Error -> Color(0xFF991B1B)
            else -> ElectricBlue
        },
        animationSpec = tween(durationMillis = 350),
        label = "secondaryGlowColor"
    )

    Box(
        modifier = modifier
            .size(size)
            .testTag("ai_visualizer_orb")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.minDimension / 2f) * 0.48f

            // 1. Outermost Glowing Ambient Aura
            val auraRadius = baseRadius * 1.85f * breathingPulse * animatedAudioScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.35f),
                        secondaryGlowColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = center
            )

            // 2. Segmented Cybernetic Outer Ring (rotating)
            val outerRadius = baseRadius * 1.55f * animatedAudioScale
            drawArc(
                color = primaryGlowColor.copy(alpha = 0.55f),
                startAngle = outerRotation,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 12f))
                )
            )
            drawArc(
                color = secondaryGlowColor.copy(alpha = 0.55f),
                startAngle = outerRotation + 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f))
                )
            )

            // 3. Middle Harmonic Audio Waveform Ring (with 32 dynamic radial spikes)
            val waveRingRadius = baseRadius * 1.22f
            val barCount = 32
            val angleStep = (2 * PI / barCount).toFloat()

            for (i in 0 until barCount) {
                val angle = i * angleStep + (innerRotation * (PI / 180f).toFloat())
                // Harmonic variance per bar based on amplitude
                val waveFactor = sin((i * 3 + innerRotation * 0.1f).toDouble()).toFloat()
                val barLength = when (state) {
                    is AssistantState.Listening, is AssistantState.Speaking -> {
                        (amplitude * 35f * (0.6f + waveFactor * 0.4f)).coerceIn(4f, 40f)
                    }
                    is AssistantState.Thinking -> {
                        (14f + (waveFactor * 8f)).coerceIn(6f, 22f)
                    }
                    else -> {
                        (4f + (waveFactor * 3f)).coerceIn(2f, 8f)
                    }
                }

                val startX = center.x + cos(angle) * waveRingRadius
                val startY = center.y + sin(angle) * waveRingRadius
                val endX = center.x + cos(angle) * (waveRingRadius + barLength)
                val endY = center.y + sin(angle) * (waveRingRadius + barLength)

                drawLine(
                    color = if (i % 2 == 0) primaryGlowColor.copy(alpha = 0.85f) else secondaryGlowColor.copy(alpha = 0.75f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // 4. Inner Orbiting Quantum Particle Beads
            val orbitRadius = baseRadius * 0.98f
            for (p in 0 until 4) {
                val pAngle = (innerRotation + (p * 90f)) * (PI / 180f).toFloat()
                val px = center.x + cos(pAngle) * orbitRadius
                val py = center.y + sin(pAngle) * orbitRadius

                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = primaryGlowColor.copy(alpha = 0.6f),
                    radius = 7.5f,
                    center = Offset(px, py)
                )
            }

            // 5. Central High-Tech Holographic Core
            val coreRadius = baseRadius * 0.82f * breathingPulse * (0.9f + animatedAudioScale * 0.1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        primaryGlowColor,
                        secondaryGlowColor,
                        Color(0xFF030712)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Inner core rim highlight
            drawCircle(
                color = primaryGlowColor.copy(alpha = 0.9f),
                radius = coreRadius,
                center = center,
                style = Stroke(width = 2.5f)
            )
        }
    }
}
