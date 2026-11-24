package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.dsp.BiquadFilter
import com.example.dsp.CrossoverConfig
import com.example.dsp.CrossoverFilterBank
import com.example.dsp.EqBandConfig
import com.example.ui.theme.LocalDlmsPalette
import kotlin.math.log10
import kotlin.math.pow

private fun freqToX(freqHz: Double, width: Float): Float {
    val minLog = log10(20.0)
    val maxLog = log10(20000.0)
    val norm = ((log10(freqHz.coerceIn(20.0, 20000.0)) - minLog) / (maxLog - minLog)).toFloat()
    return norm * width
}

/**
 * Exact mathematical frequency-response plot for the 2-Way Crossover (LOW & HIGH bands).
 */
@Composable
fun CrossoverFrequencyGraph(
    crossover: CrossoverConfig,
    selectedBand: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, palette.lcdGrid, shape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val minDb = -36f
            val maxDb = 6f

            fun dbToY(db: Float): Float {
                val norm = ((db.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb))
                return h - norm * h
            }

            // Frequency grid lines (50, 100, 200, 500, 1k, 2k, 5k, 10k)
            val gridFreqs = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
            for (f in gridFreqs) {
                val x = freqToX(f, w)
                drawLine(palette.lcdGrid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            // dB grid lines (+6, 0, -6, -12, -24, -36)
            for (db in listOf(0f, -6f, -12f, -24f)) {
                val y = dbToY(db)
                drawLine(
                    color = if (db == 0f) palette.lcdGrid.copy(alpha = 1f) else palette.lcdGrid.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f
                )
            }

            val lowBank = CrossoverFilterBank(isLowPass = true).apply {
                configure(crossover.low, crossover.bypass, 48000.0)
            }
            val highBank = CrossoverFilterBank(isLowPass = false).apply {
                configure(crossover.high, crossover.bypass, 48000.0)
            }

            val points = 96
            val lowPath = Path()
            val highPath = Path()
            val minLog = log10(20.0)
            val maxLog = log10(20000.0)

            for (i in 0..points) {
                val frac = i / points.toFloat()
                val freq = 10.0.pow(minLog + frac * (maxLog - minLog))
                val x = frac * w
                val yLow = dbToY(lowBank.magnitudeDbAt(freq, 48000.0))
                val yHigh = dbToY(highBank.magnitudeDbAt(freq, 48000.0))
                if (i == 0) {
                    lowPath.moveTo(x, yLow)
                    highPath.moveTo(x, yHigh)
                } else {
                    lowPath.lineTo(x, yLow)
                    highPath.lineTo(x, yHigh)
                }
            }

            drawPath(
                path = lowPath,
                color = palette.lcdPrimary.copy(alpha = if (selectedBand == 0) 1f else 0.55f),
                style = Stroke(width = if (selectedBand == 0) 3.5f else 2f)
            )
            drawPath(
                path = highPath,
                color = palette.lcdSecondary.copy(alpha = if (selectedBand == 1) 1f else 0.55f),
                style = Stroke(width = if (selectedBand == 1) 3.5f else 2f)
            )

            // Crossover frequency markers
            val xLow = freqToX(crossover.low.frequencyHz.toDouble(), w)
            val xHigh = freqToX(crossover.high.frequencyHz.toDouble(), w)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            drawLine(
                color = palette.lcdPrimary.copy(alpha = 0.6f),
                start = Offset(xLow, 0f),
                end = Offset(xLow, h),
                strokeWidth = 1.5f,
                pathEffect = dash
            )
            drawLine(
                color = palette.lcdSecondary.copy(alpha = 0.6f),
                start = Offset(xHigh, 0f),
                end = Offset(xHigh, h),
                strokeWidth = 1.5f,
                pathEffect = dash
            )
        }
    }
}

/**
 * Exact mathematical 5-band Parametric EQ frequency-response plot.
 */
@Composable
fun ParametricEqGraph(
    bands: List<EqBandConfig>,
    eqBypass: Boolean,
    selectedBandIndex: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, palette.lcdGrid, shape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val minDb = -18f
            val maxDb = 18f

            fun dbToY(db: Float): Float {
                val norm = ((db.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb))
                return h - norm * h
            }

            for (f in listOf(50.0, 100.0, 250.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)) {
                val x = freqToX(f, w)
                drawLine(palette.lcdGrid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            for (db in listOf(-12f, -6f, 0f, 6f, 12f)) {
                val y = dbToY(db)
                drawLine(
                    color = if (db == 0f) palette.lcdMutedText.copy(alpha = 0.5f) else palette.lcdGrid,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f
                )
            }

            val path = Path()
            val points = 96
            val minLog = log10(20.0)
            val maxLog = log10(20000.0)
            for (i in 0..points) {
                val frac = i / points.toFloat()
                val freq = 10.0.pow(minLog + frac * (maxLog - minLog))
                val x = frac * w
                val mag = BiquadFilter.eqChainMagnitudeDb(bands, eqBypass, freq, 48000.0)
                val y = dbToY(mag)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = if (eqBypass) palette.lcdMutedText else accentColor,
                style = Stroke(width = 3f)
            )

            // Draw band nodes
            bands.forEachIndexed { idx, band ->
                val bx = freqToX(band.frequencyHz.toDouble(), w)
                val by = dbToY(if (band.enabled && !eqBypass) band.gainDb else 0f)
                val isSelected = idx == selectedBandIndex
                drawCircle(
                    color = if (isSelected) palette.lcdSecondary else if (band.enabled) accentColor else palette.lcdMutedText,
                    radius = if (isSelected) 6.5f else 4f,
                    center = Offset(bx, by)
                )
            }
        }
    }
}
