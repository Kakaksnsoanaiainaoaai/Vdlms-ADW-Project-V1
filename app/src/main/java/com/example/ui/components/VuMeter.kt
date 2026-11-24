package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.ChannelTelemetry
import com.example.storage.MeterStyleOption
import com.example.ui.theme.LocalDlmsPalette
import java.util.Locale

private fun dbToNormalized(db: Float): Float = ((db + 60f) / 60f).coerceIn(0f, 1f)

/**
 * Vertical hardware VU/Peak meter with LED Segmented, Smooth Bar, and Peak Hold styles.
 */
@Composable
fun VerticalVuMeter(
    label: String,
    telemetry: ChannelTelemetry,
    meterStyle: MeterStyleOption,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    showLimiterGr: Boolean = false
) {
    val palette = LocalDlmsPalette.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (telemetry.clipped) "CLIP" else String.format(Locale.US, "%.0f", telemetry.peakDb),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = when {
                telemetry.clipped -> palette.meterRed
                isMuted -> palette.meterRed.copy(alpha = 0.8f)
                else -> palette.lcdMutedText
            },
            maxLines = 1
        )

        Canvas(
            modifier = Modifier
                .width(18.dp)
                .weight(1f)
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.6f),
                cornerRadius = CornerRadius(3f, 3f),
                size = Size(w, h)
            )

            val levelNorm = if (isMuted) 0f else dbToNormalized(telemetry.rmsDb)
            val peakNorm = if (isMuted) 0f else dbToNormalized(telemetry.peakHoldDb)

            when (meterStyle) {
                MeterStyleOption.LED_SEGMENTED -> {
                    val segments = 16
                    val gap = 2f
                    val segH = (h - gap * (segments + 1)) / segments
                    val litSegments = (levelNorm * segments).toInt()
                    val peakSegment = (peakNorm * segments).toInt().coerceIn(0, segments - 1)

                    for (i in 0 until segments) {
                        val frac = (i + 1) / segments.toFloat()
                        val segColor = when {
                            frac > 0.88f -> palette.meterRed
                            frac > 0.68f -> palette.meterYellow
                            else -> palette.meterGreen
                        }
                        val isLit = i < litSegments || (peakNorm > 0.02f && i == peakSegment)
                        val y = h - gap - (i + 1) * segH - i * gap
                        drawRoundRect(
                            color = if (isLit) segColor else segColor.copy(alpha = 0.14f),
                            topLeft = Offset(2f, y),
                            size = Size(w - 4f, segH.coerceAtLeast(1f)),
                            cornerRadius = CornerRadius(1.5f, 1.5f)
                        )
                    }
                }

                MeterStyleOption.SMOOTH_BAR, MeterStyleOption.PEAK_HOLD -> {
                    val barH = levelNorm * (h - 4f)
                    if (barH > 0.5f) {
                        val greenH = (barH).coerceAtMost((h - 4f) * 0.68f)
                        drawRect(
                            color = palette.meterGreen,
                            topLeft = Offset(2f, h - 2f - greenH),
                            size = Size(w - 4f, greenH)
                        )
                        if (levelNorm > 0.68f) {
                            val yellowTop = (h - 4f) * levelNorm.coerceAtMost(0.88f)
                            val yellowStart = (h - 4f) * 0.68f
                            drawRect(
                                color = palette.meterYellow,
                                topLeft = Offset(2f, h - 2f - yellowTop),
                                size = Size(w - 4f, (yellowTop - yellowStart).coerceAtLeast(0f))
                            )
                        }
                        if (levelNorm > 0.88f) {
                            val redTop = (h - 4f) * levelNorm
                            val redStart = (h - 4f) * 0.88f
                            drawRect(
                                color = palette.meterRed,
                                topLeft = Offset(2f, h - 2f - redTop),
                                size = Size(w - 4f, (redTop - redStart).coerceAtLeast(0f))
                            )
                        }
                    }
                    if (peakNorm > 0.02f) {
                        val py = h - 2f - peakNorm * (h - 4f)
                        drawLine(
                            color = if (peakNorm > 0.88f) palette.meterRed else palette.lcdText,
                            start = Offset(1f, py),
                            end = Offset(w - 1f, py),
                            strokeWidth = 2.5f
                        )
                    }
                }
            }
        }

        if (showLimiterGr) {
            Text(
                text = if (telemetry.limiterActive) "LIM" else "OK",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = if (telemetry.limiterActive) palette.meterYellow else palette.lcdMutedText,
                maxLines = 1
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (isMuted) palette.meterRed else palette.lcdText,
            maxLines = 1
        )
    }
}

/**
 * Horizontal compact channel VU meter for Input/Output DSP screens.
 */
@Composable
fun HorizontalVuMeter(
    label: String,
    telemetry: ChannelTelemetry,
    meterStyle: MeterStyleOption,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.lcdMutedText
            )
            Text(
                text = if (isMuted) "MUTED" else String.format(Locale.US, "RMS %+.1f dB | PK %+.1f dB", telemetry.rmsDb, telemetry.peakHoldDb),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = if (telemetry.clipped) palette.meterRed else palette.lcdText
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.65f),
                cornerRadius = CornerRadius(2f, 2f),
                size = Size(w, h)
            )
            val norm = if (isMuted) 0f else dbToNormalized(telemetry.rmsDb)
            val peakNorm = if (isMuted) 0f else dbToNormalized(telemetry.peakHoldDb)

            if (meterStyle == MeterStyleOption.LED_SEGMENTED) {
                val segs = 24
                val gap = 2f
                val segW = (w - gap * (segs + 1)) / segs
                val lit = (norm * segs).toInt()
                val pkSeg = (peakNorm * segs).toInt().coerceIn(0, segs - 1)
                for (i in 0 until segs) {
                    val frac = (i + 1) / segs.toFloat()
                    val c = when {
                        frac > 0.88f -> palette.meterRed
                        frac > 0.68f -> palette.meterYellow
                        else -> palette.meterGreen
                    }
                    val active = i < lit || (peakNorm > 0.02f && i == pkSeg)
                    drawRoundRect(
                        color = if (active) c else c.copy(alpha = 0.15f),
                        topLeft = Offset(gap + i * (segW + gap), 1.5f),
                        size = Size(segW.coerceAtLeast(1f), h - 3f),
                        cornerRadius = CornerRadius(1f, 1f)
                    )
                }
            } else {
                drawRoundRect(
                    color = when {
                        norm > 0.88f -> palette.meterRed
                        norm > 0.68f -> palette.meterYellow
                        else -> palette.meterGreen
                    },
                    topLeft = Offset(1f, 1.5f),
                    size = Size((norm * (w - 2f)).coerceAtLeast(0f), h - 3f),
                    cornerRadius = CornerRadius(1.5f, 1.5f)
                )
                if (peakNorm > 0.02f) {
                    val px = peakNorm * (w - 2f)
                    drawLine(
                        color = palette.lcdText,
                        start = Offset(px, 0f),
                        end = Offset(px, h),
                        strokeWidth = 2.5f
                    )
                }
            }
        }
    }
}
