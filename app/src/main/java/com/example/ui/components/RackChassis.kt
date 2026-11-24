package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalDlmsPalette

/**
 * 19-inch hardware rack mounting ear with slotted screw holes and hex bolts.
 */
@Composable
fun RackMountingEar(modifier: Modifier = Modifier) {
    val palette = LocalDlmsPalette.current
    Column(
        modifier = modifier
            .width(18.dp)
            .fillMaxHeight()
            .background(palette.rackEarBg)
            .border(width = 0.8.dp, color = Color.Black),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RackBoltCanvas(modifier = Modifier.padding(vertical = 12.dp))
        RackBoltCanvas(modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun RackBoltCanvas(modifier: Modifier = Modifier) {
    val palette = LocalDlmsPalette.current
    Canvas(modifier = modifier.width(12.dp).padding(vertical = 2.dp)) {
        val w = size.width
        val r = w * 0.45f
        val cx = w / 2f
        val cy = r + 2f
        // Rack slot
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(cx - r, cy - r * 0.7f),
            size = Size(r * 2f, r * 1.4f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Metallic screw head
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.knobHighlight, palette.screwMetal, Color(0xFF1F242C)),
                center = Offset(cx - 1.5f, cy - 1.5f),
                radius = r * 1.1f
            ),
            radius = r * 0.85f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.6f),
            radius = r * 0.85f,
            center = Offset(cx, cy),
            style = Stroke(width = 1f)
        )
        // Phillips/hex cross
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(cx - 3f, cy), Offset(cx + 3f, cy), strokeWidth = 1.4f)
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(cx, cy - 3f), Offset(cx, cy + 3f), strokeWidth = 1.4f)
    }
}

/**
 * Recessed hardware LCD/TFT bezel with anti-glare border and adjustable backlight brightness.
 */
@Composable
fun HardwareLcdBezel(
    brightness: Float,
    isDimmed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalDlmsPalette.current
    val outerShape = RoundedCornerShape(8.dp)
    val screenShape = RoundedCornerShape(5.dp)

    Box(
        modifier = modifier
            .shadow(6.dp, outerShape)
            .clip(outerShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.chassisBezel, Color(0xFF0A0C10))
                )
            )
            .border(1.5.dp, palette.buttonBorder.copy(alpha = 0.7f), outerShape)
            .padding(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(screenShape)
                .background(palette.lcdBg)
                .border(1.dp, palette.lcdBorder, screenShape)
        ) {
            // Subtle LCD matrix scanline/grid texture
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = palette.lcdGrid.copy(alpha = 0.14f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 0.8f
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = palette.lcdGrid.copy(alpha = 0.14f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.8f
                    )
                    y += step
                }
            }

            content()

            // Hardware backlight dimming / brightness filter
            val effectiveAlpha = if (isDimmed) 0.65f else (1.0f - brightness.coerceIn(0.3f, 1.0f)) * 0.8f
            if (effectiveAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = effectiveAlpha))
                )
            }
        }
    }
}
