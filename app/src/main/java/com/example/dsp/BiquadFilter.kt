package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Direct Form II Transposed Biquad / 1st-Order IIR Filter.
 * Audio-thread safe: zero heap allocations during processSample().
 */
class BiquadFilter {
    @Volatile
    private var b0 = 1.0
    @Volatile
    private var b1 = 0.0
    @Volatile
    private var b2 = 0.0
    @Volatile
    private var a1 = 0.0
    @Volatile
    private var a2 = 0.0

    private var z1 = 0.0
    private var z2 = 0.0

    fun resetState() {
        z1 = 0.0
        z2 = 0.0
    }

    fun setBypass() {
        b0 = 1.0
        b1 = 0.0
        b2 = 0.0
        a1 = 0.0
        a2 = 0.0
    }

    fun configurePeaking(freqHz: Double, gainDb: Double, q: Double, sampleRate: Double) {
        val safeFreq = freqHz.coerceIn(10.0, sampleRate * 0.48)
        val safeQ = q.coerceIn(0.2, 20.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * safeQ)

        val a0 = 1.0 + alpha / a
        b0 = (1.0 + alpha * a) / a0
        b1 = (-2.0 * cosW0) / a0
        b2 = (1.0 - alpha * a) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha / a) / a0
    }

    fun configureLowShelf(freqHz: Double, gainDb: Double, q: Double, sampleRate: Double) {
        val safeFreq = freqHz.coerceIn(10.0, sampleRate * 0.48)
        val safeQ = q.coerceIn(0.2, 4.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * safeQ)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)) / a0
        b2 = (a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW0)) / a0
        a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
    }

    fun configureHighShelf(freqHz: Double, gainDb: Double, q: Double, sampleRate: Double) {
        val safeFreq = freqHz.coerceIn(10.0, sampleRate * 0.48)
        val safeQ = q.coerceIn(0.2, 4.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * safeQ)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)) / a0
        b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0)) / a0
        a2 = ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
    }

    fun configureLowPass2nd(freqHz: Double, q: Double, sampleRate: Double) {
        val safeFreq = freqHz.coerceIn(10.0, sampleRate * 0.48)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1))

        val a0 = 1.0 + alpha
        b0 = ((1.0 - cosW0) * 0.5) / a0
        b1 = (1.0 - cosW0) / a0
        b2 = ((1.0 - cosW0) * 0.5) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun configureHighPass2nd(freqHz: Double, q: Double, sampleRate: Double) {
        val safeFreq = freqHz.coerceIn(10.0, sampleRate * 0.48)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1))

        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) * 0.5) / a0
        b1 = -(1.0 + cosW0) / a0
        b2 = ((1.0 + cosW0) * 0.5) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun configureLowPass1st(freqHz: Double, sampleRate: Double) {
        val safeFreq = freqHz.coerceIn(10.0, sampleRate * 0.48)
        val k = tan(PI * safeFreq / sampleRate)
        val norm = 1.0 / (1.0 + k)
        b0 = k * norm
        b1 = k * norm
        b2 = 0.0
        a1 = (k - 1.0) * norm
        a2 = 0.0
    }

    fun configureHighPass1st(freqHz: Double, sampleRate: Double) {
        val safeFreq = freqHz.coerceIn(10.0, sampleRate * 0.48)
        val k = tan(PI * safeFreq / sampleRate)
        val norm = 1.0 / (1.0 + k)
        b0 = norm
        b1 = -norm
        b2 = 0.0
        a1 = (k - 1.0) * norm
        a2 = 0.0
    }

    fun processSample(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1
        z1 = b1 * xd - a1 * y + z2
        z2 = b2 * xd - a2 * y
        return y.toFloat()
    }

    fun magnitudeDbAt(freqHz: Double, sampleRate: Double): Double {
        val w = 2.0 * PI * freqHz / sampleRate
        val cos1 = cos(w)
        val sin1 = sin(w)
        val cos2 = cos(2.0 * w)
        val sin2 = sin(2.0 * w)

        val numRe = b0 + b1 * cos1 + b2 * cos2
        val numIm = -(b1 * sin1 + b2 * sin2)
        val denRe = 1.0 + a1 * cos1 + a2 * cos2
        val denIm = -(a1 * sin1 + a2 * sin2)

        val mag = hypot(numRe, numIm) / max(1e-12, hypot(denRe, denIm))
        return 20.0 * log10(max(1e-9, mag))
    }

    companion object {
        fun configureForEqBand(band: EqBandConfig, sampleRate: Double, target: BiquadFilter = BiquadFilter()): BiquadFilter {
            if (!band.enabled) {
                target.setBypass()
                return target
            }
            when (band.filterType) {
                EqFilterType.PEAK -> target.configurePeaking(band.frequencyHz.toDouble(), band.gainDb.toDouble(), band.q.toDouble(), sampleRate)
                EqFilterType.LOW_SHELF -> target.configureLowShelf(band.frequencyHz.toDouble(), band.gainDb.toDouble(), band.q.toDouble(), sampleRate)
                EqFilterType.HIGH_SHELF -> target.configureHighShelf(band.frequencyHz.toDouble(), band.gainDb.toDouble(), band.q.toDouble(), sampleRate)
            }
            return target
        }

        fun eqChainMagnitudeDb(bands: List<EqBandConfig>, eqBypass: Boolean, freqHz: Double, sampleRate: Double = 48000.0): Float {
            if (eqBypass) return 0f
            var totalDb = 0.0
            val temp = BiquadFilter()
            for (band in bands) {
                if (band.enabled) {
                    configureForEqBand(band, sampleRate, temp)
                    totalDb += temp.magnitudeDbAt(freqHz, sampleRate)
                }
            }
            return totalDb.toFloat()
        }
    }
}
