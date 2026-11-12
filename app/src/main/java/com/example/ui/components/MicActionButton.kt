package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantState
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan

@Composable
fun MicActionButton(
    state: AssistantState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 80.dp
) {
    val isListening = state is AssistantState.Listening
    val isSpeaking = state is AssistantState.Speaking
    val isBusy = state is AssistantState.Thinking

    val transition = rememberInfiniteTransition(label = "MicPulse")
    val rippleScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale"
    )
    val rippleAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    val buttonGlowColor by animateColorAsState(
        targetValue = state.glowColor,
        animationSpec = tween(300),
        label = "buttonGlowColor"
    )

    Box(
        modifier = modifier
            .size(buttonSize + 32.dp)
            .testTag("mic_action_container"),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ripple ring when active
        if (isListening || isSpeaking) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .scale(rippleScale)
                    .clip(CircleShape)
                    .background(buttonGlowColor.copy(alpha = rippleAlpha))
            )
        }

        // Secondary subtle halo
        Box(
            modifier = Modifier
                .size(buttonSize + 8.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            buttonGlowColor.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main action button surface
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            buttonGlowColor.copy(alpha = 0.25f),
                            Color(0xFF0F1A33)
                        )
                    )
                )
                .border(
                    width = if (isListening) 2.5.dp else 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            buttonGlowColor,
                            if (isListening) Color.White else ElectricBlue
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = buttonGlowColor),
                    onClick = onClick
                )
                .testTag("mic_button"),
            contentAlignment = Alignment.Center
        ) {
            val icon = when {
                isListening -> Icons.Default.Stop
                isSpeaking -> Icons.Default.GraphicEq
                else -> Icons.Default.Mic
            }

            val iconTint = when {
                isListening -> Color(0xFF10B981)
                isSpeaking -> NeonCyan
                isBusy -> Color(0xFFA855F7)
                else -> Color.White
            }

            Icon(
                imageVector = icon,
                contentDescription = if (isListening) "Stop Listening" else "Start Voice Input",
                tint = iconTint,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
