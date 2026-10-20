package com.nova.ai.assistant.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nova.ai.assistant.domain.model.VoiceState

@Composable
fun AudioReactiveVisualizer(
    voiceState: VoiceState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SiriOrbMotion")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val dynamicDbScale = when (voiceState) {
        is VoiceState.Listening -> (1.0f + (voiceState.rmsDb.coerceAtLeast(0f) / 9f)).coerceIn(1.0f, 1.7f)
        is VoiceState.Speaking -> (1.0f + (voiceState.amplitude * 0.7f)).coerceIn(1.0f, 1.5f)
        is VoiceState.Processing -> pulseScale * 1.05f
        else -> 1.0f
    }

    val primaryColor = when (voiceState) {
        is VoiceState.Listening -> Color(0xFF00E5FF)
        is VoiceState.Speaking -> Color(0xFF7C4DFF)
        is VoiceState.Processing -> Color(0xFFFF4081)
        is VoiceState.ExecutingAction -> Color(0xFF00E5FF)
        is VoiceState.Error -> Color(0xFFFF5252)
        VoiceState.Idle -> Color(0xFF1DE9B6)
    }

    val secondaryColor = Color(0xFF3D5AFE)

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2, size.height / 2)
            val baseRadius = (size.minDimension / 3.2f) * dynamicDbScale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                    center = centerOffset,
                    radius = baseRadius * 1.55f
                ),
                radius = baseRadius * 1.55f,
                center = centerOffset
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.75f), secondaryColor.copy(alpha = 0.25f)),
                    center = centerOffset,
                    radius = baseRadius * 1.15f
                ),
                radius = baseRadius * 1.15f,
                center = centerOffset
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, primaryColor, secondaryColor),
                    center = centerOffset,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = centerOffset
            )
        }
    }
}
