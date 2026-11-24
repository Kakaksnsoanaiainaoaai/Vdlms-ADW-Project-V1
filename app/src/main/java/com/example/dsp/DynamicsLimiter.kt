package com.example.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Real-time feed-forward peak limiter with configurable Threshold, Attack, Release, and Make-up Gain.
 * Reports exact instantaneous gain reduction (dB) and limiting active status.
 */
class DynamicsLimiter {
    @Volatile
    private var enabled = true
    @Volatile
    private var thresholdLinear = 0.7079f // -3 dBFS
    @Volatile
    private var makeupLinear = 1.0f
    @Volatile
    private var attackCoeff = 0.99f
    @Volatile
    private var releaseCoeff = 0.999f

    private var envelope = 0f

    @Volatile
    var lastGainReductionDb: Float = 0f
        private set

    @Volatile
    var isLimiting: Boolean = false
        private set

    fun configure(config: LimiterConfig, sampleRate: Double) {
        enabled = config.enabled
        thresholdLinear = 10.0.pow(config.thresholdDbfs / 20.0).toFloat()
        makeupLinear = 10.0.pow(config.makeupGainDb / 20.0).toFloat()

        val attackSec = (config.attackMs.coerceIn(0.5f, 100f) / 1000.0)
        val releaseSec = (config.releaseMs.coerceIn(10f, 2000f) / 1000.0)
        attackCoeff = exp(-1.0 / (attackSec * sampleRate)).toFloat()
        releaseCoeff = exp(-1.0 / (releaseSec * sampleRate)).toFloat()
    }

    fun reset() {
        envelope = 0f
        lastGainReductionDb = 0f
        isLimiting = false
    }

    fun processSample(input: Float): Float {
        if (!enabled) {
            lastGainReductionDb = 0f
            isLimiting = false
            return input
        }

        val withMakeup = input * makeupLinear
        val absIn = abs(withMakeup)

        envelope = if (absIn > envelope) {
            attackCoeff * envelope + (1f - attackCoeff) * absIn
        } else {
            releaseCoeff * envelope + (1f - releaseCoeff) * absIn
        }

        val gain = if (envelope > thresholdLinear && envelope > 1e-9f) {
            thresholdLinear / envelope
        } else {
            1f
        }

        val grDb = if (gain < 0.999f) -20f * log10(max(1e-6f, gain)) else 0f
        lastGainReductionDb = grDb
        isLimiting = grDb > 0.15f

        return (withMakeup * gain).coerceIn(-1.0f, 1.0f)
    }
}
