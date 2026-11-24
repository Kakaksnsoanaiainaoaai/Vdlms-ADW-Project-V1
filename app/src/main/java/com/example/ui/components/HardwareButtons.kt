package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalDlmsPalette
import com.example.ui.theme.SpaceGroteskFamily

/**
 * Tactile hardware rack pushbutton with physical bezel shadow, LED bar indicator,
 * press-depth animation, and haptic feedback.
 */
@Composable
fun HardwarePushButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "hw_btn_${label.lowercase().replace(' ', '_')}",
    isActive: Boolean = false,
    ledColor: Color? = null,
    icon: ImageVector? = null,
    compact: Boolean = false
) {
    val palette = LocalDlmsPalette.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1.0f,
        animationSpec = spring(stiffness = 700f),
        label = "btn_scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 4.dp,
        animationSpec = spring(stiffness = 700f),
        label = "btn_elev"
    )
    val activeLed = ledColor ?: palette.lcdPrimary
    val ledFill by animateColorAsState(
        targetValue = if (isActive) activeLed else palette.ledOff,
        label = "led_fill"
    )

    val shape = RoundedCornerShape(5.dp)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = if (pressed) {
                        listOf(palette.buttonBottom, palette.buttonTop)
                    } else {
                        listOf(palette.buttonTop, palette.buttonBottom)
                    }
                )
            )
            .border(
                width = 1.dp,
                color = if (isActive) activeLed.copy(alpha = 0.7f) else palette.buttonBorder,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag(testTag)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 5.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top hardware LED strip
            Box(
                modifier = Modifier
                    .width(if (compact) 16.dp else 22.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ledFill)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isActive) activeLed else palette.silkscreenText,
                        modifier = Modifier.size(if (compact) 13.dp else 15.dp)
                    )
                    if (label.isNotBlank()) Spacer(modifier = Modifier.width(3.dp))
                }
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        fontFamily = SpaceGroteskFamily,
                        fontSize = if (compact) 9.sp else 10.sp,
                        letterSpacing = 0.5.sp,
                        color = if (isActive) palette.lcdText else palette.silkscreenText,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
fun HardwareRoundDirectionKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 40.dp
) {
    val palette = LocalDlmsPalette.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1.0f,
        animationSpec = spring(stiffness = 750f),
        label = "dpad_scale"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(sizeDp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (pressed) 1.dp else 3.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.buttonTop, palette.buttonBottom)
                )
            )
            .border(1.dp, palette.buttonBorder, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.silkscreenText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun HardwareStatusLed(
    label: String,
    isLit: Boolean,
    litColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val glowColor by animateColorAsState(
        targetValue = if (isLit) litColor else palette.ledOff,
        label = "status_led"
    )
    Row(
        modifier = modifier.defaultMinSize(minHeight = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(glowColor)
                .border(0.8.dp, Color.Black.copy(alpha = 0.6f), CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isLit) palette.lcdText else palette.silkscreenText.copy(alpha = 0.65f),
            maxLines = 1
        )
    }
}
