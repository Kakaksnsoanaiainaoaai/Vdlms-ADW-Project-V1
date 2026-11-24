package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalDlmsPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Virtual hardware rotary encoder with angular drag rotation, knurled aluminum ring,
 * tactile detent haptics, and PUSH = ENTER press animation.
 */
@Composable
fun RotaryEncoderKnob(
    angleDeg: Float,
    isEditing: Boolean,
    onRotateSteps: (steps: Int, deltaDeg: Float) -> Unit,
    onPushEnter: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 84.dp
) {
    val palette = LocalDlmsPalette.current
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    var accumulatedDeg by remember { mutableFloatStateOf(0f) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(stiffness = 700f),
        label = "knob_push_scale"
    )
    val smoothAngle by animateFloatAsState(
        targetValue = angleDeg,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.85f),
        label = "knob_angle"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .testTag("rotary_encoder")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPushEnter()
                        }
                    )
                }
                .pointerInput(Unit) {
                    var lastAngle = 0f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    detectDragGestures(
                        onDragStart = { start ->
                            val dx = start.x - center.x
                            val dy = start.y - center.y
                            lastAngle = (atan2(dy, dx) * 180f / PI.toFloat())
                            accumulatedDeg = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = change.position.x - center.x
                            val dy = change.position.y - center.y
                            val distFromCenter = kotlin.math.hypot(dx, dy)
                            val deltaDeg = if (distFromCenter > size.width * 0.14f) {
                                val currentAngle = (atan2(dy, dx) * 180f / PI.toFloat())
                                var diff = currentAngle - lastAngle
                                if (diff > 180f) diff -= 360f
                                if (diff < -180f) diff += 360f
                                lastAngle = currentAngle
                                diff
                            } else {
                                // Linear fallback near center
                                (dragAmount.x - dragAmount.y) * 1.4f
                            }

                            accumulatedDeg += deltaDeg
                            val detentDeg = 16f
                            val steps = (accumulatedDeg / detentDeg).toInt()
                            if (steps != 0) {
                                accumulatedDeg -= steps * detentDeg
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRotateSteps(steps, steps * detentDeg)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(sizeDp)) {
                val r = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                // Outer tick ring around encoder
                val tickCount = 24
                for (i in 0 until tickCount) {
                    val a = (i * (360f / tickCount)) * PI.toFloat() / 180f
                    val outer = Offset(center.x + cos(a) * (r - 1f), center.y + sin(a) * (r - 1f))
                    val inner = Offset(center.x + cos(a) * (r - 5f), center.y + sin(a) * (r - 5f))
                    drawLine(
                        color = if (isEditing) palette.lcdPrimary.copy(alpha = 0.7f) else palette.silkscreenText.copy(alpha = 0.3f),
                        start = inner,
                        end = outer,
                        strokeWidth = 1.4f
                    )
                }

                // Recessed collar
                val knobR = r * 0.78f
                drawCircle(
                    color = Color.Black.copy(alpha = 0.7f),
                    radius = knobR + 4f,
                    center = center
                )

                // Knurled metallic dial rim
                val knurlCount = 32
                for (i in 0 until knurlCount) {
                    val a = ((i * (360f / knurlCount)) + smoothAngle) * PI.toFloat() / 180f
                    val p1 = Offset(center.x + cos(a) * (knobR - 2f), center.y + sin(a) * (knobR - 2f))
                    val p2 = Offset(center.x + cos(a) * (knobR + 2.5f), center.y + sin(a) * (knobR + 2.5f))
                    drawLine(
                        color = palette.knobHighlight.copy(alpha = 0.55f),
                        start = p1,
                        end = p2,
                        strokeWidth = 2f
                    )
                }

                // Main machined aluminum cap
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.knobHighlight,
                            palette.knobBody,
                            Color(0xFF0E1116)
                        ),
                        center = Offset(center.x - knobR * 0.25f, center.y - knobR * 0.25f),
                        radius = knobR * 1.4f
                    ),
                    radius = knobR,
                    center = center
                )

                // Ring border (illuminated when editing parameter)
                drawCircle(
                    color = if (isEditing) palette.lcdPrimary else palette.buttonBorder,
                    radius = knobR,
                    center = center,
                    style = Stroke(width = if (isEditing) 2.5f else 1.5f)
                )

                // Dimple & pointer indicator
                val rad = (smoothAngle - 90f) * PI.toFloat() / 180f
                val pointerStart = Offset(center.x + cos(rad) * (knobR * 0.28f), center.y + sin(rad) * (knobR * 0.28f))
                val pointerEnd = Offset(center.x + cos(rad) * (knobR * 0.84f), center.y + sin(rad) * (knobR * 0.84f))
                drawLine(
                    color = if (isEditing) palette.lcdPrimary else palette.lcdSecondary,
                    start = pointerStart,
                    end = pointerEnd,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isEditing) "PUSH=SET / TURN" else "ENCODER / PUSH",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = if (isEditing) palette.lcdPrimary else palette.silkscreenText.copy(alpha = 0.75f),
            maxLines = 1
        )
    }
}
