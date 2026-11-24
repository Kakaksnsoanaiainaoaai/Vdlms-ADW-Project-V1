package com.example.dsp

/**
 * 2-Way Crossover filter bank supporting Butterworth (12/18/24/48 dB/oct)
 * and Linkwitz-Riley (12/24/48 dB/oct) topologies using cascaded IIR stages.
 */
class CrossoverFilterBank(private val isLowPass: Boolean) {
    private val stages = Array(4) { BiquadFilter() }
    @Volatile
    private var activeStageCount = 0

    fun resetState() {
        for (stage in stages) stage.resetState()
    }

    fun configure(config: CrossoverBandConfig, globalBypass: Boolean, sampleRate: Double) {
        if (globalBypass || config.bypass) {
            activeStageCount = 0
            for (stage in stages) stage.setBypass()
            return
        }

        val freq = config.frequencyHz.toDouble()
        val qValues: DoubleArray
        var hasFirstOrder = false

        when (config.filterType) {
            CrossoverFilterType.BUTTERWORTH -> {
                when (config.slope) {
                    CrossoverSlope.DB_12 -> qValues = doubleArrayOf(0.70710678)
                    CrossoverSlope.DB_18 -> {
                        qValues = doubleArrayOf(1.0)
                        hasFirstOrder = true
                    }
                    CrossoverSlope.DB_24 -> qValues = doubleArrayOf(0.54119610, 1.30656296)
                    CrossoverSlope.DB_48 -> qValues = doubleArrayOf(0.50979558, 0.60134489, 0.89997622, 2.56291545)
                }
            }
            CrossoverFilterType.LINKWITZ_RILEY -> {
                when (config.slope) {
                    CrossoverSlope.DB_12 -> qValues = doubleArrayOf(0.5)
                    CrossoverSlope.DB_18 -> {
                        // Linkwitz-Riley is strictly even-order; use LR4 (24 dB/oct) if DB_18 was passed
                        qValues = doubleArrayOf(0.70710678, 0.70710678)
                    }
                    CrossoverSlope.DB_24 -> qValues = doubleArrayOf(0.70710678, 0.70710678)
                    CrossoverSlope.DB_48 -> qValues = doubleArrayOf(0.54119610, 1.30656296, 0.54119610, 1.30656296)
                }
            }
        }

        var idx = 0
        if (hasFirstOrder) {
            if (isLowPass) {
                stages[idx].configureLowPass1st(freq, sampleRate)
            } else {
                stages[idx].configureHighPass1st(freq, sampleRate)
            }
            idx++
        }
        for (q in qValues) {
            if (isLowPass) {
                stages[idx].configureLowPass2nd(freq, q, sampleRate)
            } else {
                stages[idx].configureHighPass2nd(freq, q, sampleRate)
            }
            idx++
        }
        activeStageCount = idx
        for (i in idx until stages.size) {
            stages[i].setBypass()
        }
    }

    fun processSample(x: Float): Float {
        var y = x
        val count = activeStageCount
        for (i in 0 until count) {
            y = stages[i].processSample(y)
        }
        return y
    }

    fun magnitudeDbAt(freqHz: Double, sampleRate: Double): Float {
        val count = activeStageCount
        if (count == 0) return 0f
        var magDb = 0.0
        for (i in 0 until count) {
            magDb += stages[i].magnitudeDbAt(freqHz, sampleRate)
        }
        return magDb.toFloat()
    }

    companion object {
        fun computeMagnitudeDb(
            config: CrossoverBandConfig,
            isLowPass: Boolean,
            globalBypass: Boolean,
            freqHz: Double,
            sampleRate: Double = 48000.0
        ): Float {
            val bank = CrossoverFilterBank(isLowPass)
            bank.configure(config, globalBypass, sampleRate)
            return bank.magnitudeDbAt(freqHz, sampleRate)
        }
    }
}
