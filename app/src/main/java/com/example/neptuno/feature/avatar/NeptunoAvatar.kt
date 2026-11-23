package com.example.neptuno.feature.avatar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.neptuno.core.model.AssistantState
import com.example.neptuno.core.model.AvatarStyle
import com.example.ui.theme.AbyssalNavy
import com.example.ui.theme.BioluminescentTurquoise
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepOcean
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateBlue
import com.example.ui.theme.SonarCyan
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Personaje 2D provisional de NEPTUNO.
 * Soporta los 6 estados visuales requeridos:
 * IDLE, LISTENING, PROCESSING, SPEAKING, ERROR y OFFLINE.
 */
@Composable
fun NeptunoAvatar(
    state: AssistantState,
    style: AvatarStyle = AvatarStyle.ABYSSAL_CORE,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neptuno_avatar_motion")

    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 1100
                    AssistantState.SPEAKING -> 750
                    AssistantState.PROCESSING -> 900
                    AssistantState.ERROR -> 650
                    AssistantState.OFFLINE -> 3200
                    AssistantState.IDLE -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.PROCESSING -> 2200
                    AssistantState.LISTENING -> 4200
                    AssistantState.SPEAKING -> 3200
                    AssistantState.OFFLINE -> 16000
                    else -> 9000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    val accentColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> ElectricBlue
            AssistantState.LISTENING -> BioluminescentTurquoise
            AssistantState.PROCESSING -> SonarCyan
            AssistantState.SPEAKING -> BioluminescentTurquoise
            AssistantState.ERROR -> CoralAlert
            AssistantState.OFFLINE -> SlateBlue
        },
        animationSpec = tween(350),
        label = "accent_color"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> BioluminescentTurquoise
            AssistantState.LISTENING -> ElectricBlue
            AssistantState.PROCESSING -> ElectricBlue
            AssistantState.SPEAKING -> ElectricBlue
            AssistantState.ERROR -> Color(0xFFFF8FA3)
            AssistantState.OFFLINE -> Color(0xFF3E5573)
        },
        animationSpec = tween(350),
        label = "secondary_color"
    )

    val stateDescription = when (state) {
        AssistantState.IDLE -> "Personaje Neptuno en estado inactivo"
        AssistantState.LISTENING -> "Personaje Neptuno escuchando"
        AssistantState.PROCESSING -> "Personaje Neptuno procesando"
        AssistantState.SPEAKING -> "Personaje Neptuno hablando"
        AssistantState.ERROR -> "Personaje Neptuno en estado de error"
        AssistantState.OFFLINE -> "Personaje Neptuno sin conexión"
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = stateDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val baseRadius = minOf(w, h) * 0.34f

            // 1. Halo oceánico radial
            val auraAlpha = when (state) {
                AssistantState.OFFLINE -> 0.14f
                AssistantState.LISTENING, AssistantState.SPEAKING -> 0.34f + breathe * 0.14f
                else -> 0.22f + breathe * 0.10f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = auraAlpha),
                        secondaryColor.copy(alpha = auraAlpha * 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minOf(w, h) * 0.50f
                ),
                radius = minOf(w, h) * 0.50f,
                center = center
            )

            // 2. Anillos exteriores según AvatarStyle
            val outerRingRadius = baseRadius * (1.28f + breathe * 0.05f)
            val dashEffect = if (state == AssistantState.OFFLINE) {
                PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            } else {
                null
            }

            when (style) {
                AvatarStyle.ABYSSAL_CORE -> {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.26f),
                        radius = outerRingRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = dashEffect)
                    )
                    rotate(degrees = orbitAngle, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, accentColor, secondaryColor, Color.Transparent)
                            ),
                            startAngle = 0f,
                            sweepAngle = if (state == AssistantState.PROCESSING) 220f else 135f,
                            useCenter = false,
                            topLeft = Offset(center.x - outerRingRadius, center.y - outerRingRadius),
                            size = Size(outerRingRadius * 2f, outerRingRadius * 2f),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                AvatarStyle.TIDAL_PULSE -> {
                    val ripple1 = baseRadius * (1.15f + (breathe * 0.22f))
                    val ripple2 = baseRadius * (1.30f + ((1f - breathe) * 0.16f))
                    drawCircle(
                        color = secondaryColor.copy(alpha = 0.32f * (1f - breathe * 0.4f)),
                        radius = ripple1,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx(), pathEffect = dashEffect)
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.20f),
                        radius = ripple2,
                        center = center,
                        style = Stroke(width = 1.8.dp.toPx(), pathEffect = dashEffect)
                    )
                }

                AvatarStyle.TRIDENT_MATRIX -> {
                    rotate(degrees = orbitAngle * 0.6f, pivot = center) {
                        val arcRadius = baseRadius * 1.32f
                        for (i in 0 until 3) {
                            drawArc(
                                color = if (i % 2 == 0) accentColor.copy(alpha = 0.7f) else secondaryColor.copy(alpha = 0.6f),
                                startAngle = i * 120f,
                                sweepAngle = 68f,
                                useCenter = false,
                                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                                size = Size(arcRadius * 2f, arcRadius * 2f),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            // 3. Carcasa esférica 2D de Neptuno
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF102A4A), DeepOcean, AbyssalNavy),
                    startY = center.y - baseRadius,
                    endY = center.y + baseRadius
                ),
                radius = baseRadius,
                center = center
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.72f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 2.2.dp.toPx())
            )

            // 4. Visor holográfico interior
            val visorWidth = baseRadius * 1.34f
            val visorHeight = baseRadius * 0.78f
            val visorTopLeft = Offset(center.x - visorWidth / 2f, center.y - visorHeight / 2f)
            drawRoundRect(
                color = Color(0xFF04101F),
                topLeft = visorTopLeft,
                size = Size(visorWidth, visorHeight),
                cornerRadius = CornerRadius(visorHeight * 0.45f, visorHeight * 0.45f)
            )
            drawRoundRect(
                color = accentColor.copy(alpha = 0.45f),
                topLeft = visorTopLeft,
                size = Size(visorWidth, visorHeight),
                cornerRadius = CornerRadius(visorHeight * 0.45f, visorHeight * 0.45f),
                style = Stroke(width = 1.4.dp.toPx())
            )

            // 5. Ojos y expresión según el estado del asistente
            val eyeY = center.y - visorHeight * 0.10f
            val eyeSpacing = visorWidth * 0.22f
            val eyeWidth = visorWidth * 0.14f
            val eyeHeight = when (state) {
                AssistantState.OFFLINE -> visorHeight * 0.06f
                AssistantState.ERROR -> visorHeight * 0.22f
                AssistantState.LISTENING -> visorHeight * (0.24f + breathe * 0.06f)
                else -> visorHeight * 0.20f
            }

            val leftEyeCenter = Offset(center.x - eyeSpacing, eyeY)
            val rightEyeCenter = Offset(center.x + eyeSpacing, eyeY)

            if (state == AssistantState.ERROR) {
                val stroke = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                val r = eyeWidth * 0.48f
                listOf(leftEyeCenter, rightEyeCenter).forEach { eye ->
                    drawLine(accentColor, Offset(eye.x - r, eye.y - r), Offset(eye.x + r, eye.y + r), stroke.width, StrokeCap.Round)
                    drawLine(accentColor, Offset(eye.x + r, eye.y - r), Offset(eye.x - r, eye.y + r), stroke.width, StrokeCap.Round)
                }
            } else {
                listOf(leftEyeCenter, rightEyeCenter).forEach { eye ->
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(eye.x - eyeWidth / 2f, eye.y - eyeHeight / 2f),
                        size = Size(eyeWidth, eyeHeight),
                        cornerRadius = CornerRadius(eyeWidth / 2f, eyeWidth / 2f)
                    )
                }
            }

            // 6. Barra acústica / boca de telemetría inferior dentro del visor
            val waveY = center.y + visorHeight * 0.24f
            val barCount = 7
            val totalWaveWidth = visorWidth * 0.56f
            val step = totalWaveWidth / (barCount - 1)
            val startX = center.x - totalWaveWidth / 2f

            for (i in 0 until barCount) {
                val x = startX + i * step
                val amplitude = when (state) {
                    AssistantState.SPEAKING -> 0.35f + 0.65f * abs(sin(wavePhase + i * 0.85f))
                    AssistantState.LISTENING -> 0.28f + 0.52f * abs(sin(wavePhase * 1.2f + i * 0.65f))
                    AssistantState.PROCESSING -> 0.20f + 0.40f * abs(sin(wavePhase * 1.6f + i * 1.1f))
                    AssistantState.IDLE -> 0.16f + 0.08f * breathe
                    AssistantState.ERROR -> 0.18f
                    AssistantState.OFFLINE -> 0.08f
                }
                val barHeight = visorHeight * 0.24f * amplitude
                drawLine(
                    color = secondaryColor.copy(alpha = if (state == AssistantState.OFFLINE) 0.4f else 0.9f),
                    start = Offset(x, waveY - barHeight / 2f),
                    end = Offset(x, waveY + barHeight / 2f),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
