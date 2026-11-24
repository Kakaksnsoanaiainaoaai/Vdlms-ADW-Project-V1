package com.example.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Real-time audio level meter computing RMS dBFS, Peak dBFS, Peak-Hold with decay, and Clip indicator.
 */
class ChannelMeter {
    private var peakHoldLinear = 0f
    private var peakHoldSamplesRemaining = 0
    private var smoothedRmsDb = -60f
    private var smoothedPeakDb = -60f
    private var clipHoldSamplesRemaining = 0

    fun reset() {
        peakHoldLinear = 0f
        peakHoldSamplesRemaining = 0
        smoothedRmsDb = -60f
        smoothedPeakDb = -60f
        clipHoldSamplesRemaining = 0
    }

    fun analyzeBlock(
        buffer: FloatArray,
        count: Int,
        sampleRate: Double,
        limiterGrDb: Float = 0f,
        limiterActive: Boolean = false
    ): ChannelTelemetry {
        if (count <= 0) return ChannelTelemetry()

        var sumSq = 0.0
        var blockPeak = 0f
        for (i in 0 until count) {
            val s = buffer[i]
            val a = abs(s)
            if (a > blockPeak) blockPeak = a
            sumSq += s * s
        }

        val rms = sqrt(sumSq / count).toFloat()
        val rmsDbRaw = linearToDb(rms)
        val peakDbRaw = linearToDb(blockPeak)

        // Smooth ballistic decay while allowing instant upward attack
        smoothedRmsDb = if (rmsDbRaw > smoothedRmsDb) {
            rmsDbRaw
        } else {
            (smoothedRmsDb * 0.85f + rmsDbRaw * 0.15f).coerceAtLeast(-60f)
        }

        smoothedPeakDb = if (peakDbRaw > smoothedPeakDb) {
            peakDbRaw
        } else {
            (smoothedPeakDb - 0.8f).coerceAtLeast(peakDbRaw).coerceAtLeast(-60f)
        }

        val holdDurationSamples = (sampleRate * 1.4).toInt()
        if (blockPeak >= peakHoldLinear) {
            peakHoldLinear = blockPeak
            peakHoldSamplesRemaining = holdDurationSamples
        } else if (peakHoldSamplesRemaining > 0) {
            peakHoldSamplesRemaining -= count
        } else {
            peakHoldLinear *= 0.94f
        }

        if (blockPeak >= 0.995f) {
            clipHoldSamplesRemaining = (sampleRate * 1.5).toInt()
        } else if (clipHoldSamplesRemaining > 0) {
            clipHoldSamplesRemaining -= count
        }

        return ChannelTelemetry(
            rmsDb = smoothedRmsDb,
            peakDb = smoothedPeakDb,
            peakHoldDb = linearToDb(peakHoldLinear),
            clipped = clipHoldSamplesRemaining > 0,
            limiterGrDb = limiterGrDb,
            limiterActive = limiterActive
        )
    }

    private fun linearToDb(linear: Float): Float {
        if (linear <= 1e-4f) return -60f
        return (20f * log10(max(1e-4f, linear))).coerceIn(-60f, 3f)
    }
}
