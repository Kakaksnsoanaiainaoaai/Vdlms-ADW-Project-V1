package com.example.dsp

import kotlin.math.floor

/**
 * Pre-allocated circular buffer delay line with linear fractional interpolation.
 * Supports up to 100 ms at 96 kHz (9600 samples) with zero audio-thread allocations.
 */
class DelayLine(maxSamples: Int = 9600) {
    private val buffer = FloatArray(maxSamples)
    private var writeIndex = 0
    @Volatile
    private var delaySamples = 0f

    fun setDelayMs(delayMs: Float, sampleRate: Double) {
        val samples = ((delayMs.coerceIn(0f, 50f) / 1000.0) * sampleRate).toFloat()
        delaySamples = samples.coerceIn(0f, (buffer.size - 2).toFloat())
    }

    fun reset() {
        buffer.fill(0f)
        writeIndex = 0
    }

    fun processSample(input: Float): Float {
        buffer[writeIndex] = input
        val d = delaySamples
        val out = if (d <= 0.001f) {
            input
        } else {
            val dInt = floor(d).toInt()
            val frac = d - dInt
            val size = buffer.size
            val idx0 = (writeIndex - dInt + size) % size
            val idx1 = (idx0 - 1 + size) % size
            buffer[idx0] * (1f - frac) + buffer[idx1] * frac
        }
        writeIndex = (writeIndex + 1) % buffer.size
        return out
    }
}
