package com.example.dsp

import kotlin.math.pow

data class ChannelEngineTelemetry(
    val input: ChannelTelemetry,
    val output: ChannelTelemetry
)

/**
 * Isolated single-channel DSP processing engine.
 * Dedicated instances are created for Channel L and Channel R so zero state,
 * filter coefficients, delay buffers, crossover banks, limiters, or meters are shared.
 *
 * Processing Chain:
 * INPUT -> INPUT GAIN (+POL) -> [IN METER] -> EQ (5-Band PEQ) -> DELAY -> XOVER -> OUTPUT GAIN (+POL) -> LIMITER -> MUTE -> OUTPUT -> [OUT METER]
 */
class ChannelDspEngine {
    private val eqFilters = Array(5) { BiquadFilter() }
    private val delayLine = DelayLine()
    private val lowCrossover = CrossoverFilterBank(isLowPass = true)
    private val highCrossover = CrossoverFilterBank(isLowPass = false)
    private val limiter = DynamicsLimiter()
    private val inputMeter = ChannelMeter()
    private val outputMeter = ChannelMeter()

    private var inMeterScratch = FloatArray(4096)
    private var inputGainLinear = 1f
    private var outputGainLinear = 1f

    fun resetState() {
        inputMeter.reset()
        outputMeter.reset()
        delayLine.reset()
        limiter.reset()
        eqFilters.forEach { it.resetState() }
        lowCrossover.resetState()
        highCrossover.resetState()
    }

    fun configure(config: ChannelDspConfig, sampleRate: Double) {
        inputGainLinear = dbToLinear(config.inputGainDb) * if (config.inputPolarityInverted) -1f else 1f
        outputGainLinear = dbToLinear(config.outputGainDb) * if (config.outputPolarityInverted) -1f else 1f

        for (i in 0 until 5) {
            if (config.eqBypass) {
                eqFilters[i].setBypass()
            } else {
                BiquadFilter.configureForEqBand(config.eqBands[i], sampleRate, eqFilters[i])
            }
        }

        delayLine.setDelayMs(config.delayMs, sampleRate)
        lowCrossover.configure(config.crossover.low, config.crossover.bypass, sampleRate)
        highCrossover.configure(config.crossover.high, config.crossover.bypass, sampleRate)
        limiter.configure(config.limiter, sampleRate)
    }

    fun processBlock(
        input: FloatArray,
        output: FloatArray,
        frameCount: Int,
        dspEnabled: Boolean,
        config: ChannelDspConfig,
        sampleRate: Double
    ): ChannelEngineTelemetry {
        if (inMeterScratch.size < frameCount) {
            inMeterScratch = FloatArray(frameCount)
        }

        if (!dspEnabled) {
            for (i in 0 until frameCount) {
                val sample = input[i]
                inMeterScratch[i] = sample
                output[i] = sample.coerceIn(-1f, 1f)
            }
            return ChannelEngineTelemetry(
                input = inputMeter.analyzeBlock(inMeterScratch, frameCount, sampleRate),
                output = outputMeter.analyzeBlock(output, frameCount, sampleRate)
            )
        }

        val gIn = inputGainLinear
        val gOut = outputGainLinear
        val band = config.activeBand
        val isMuted = config.muted

        for (i in 0 until frameCount) {
            val afterInputGain = input[i] * gIn
            inMeterScratch[i] = afterInputGain

            if (isMuted) {
                output[i] = 0f
                continue
            }

            var s = afterInputGain
            for (b in 0 until 5) {
                s = eqFilters[b].processSample(s)
            }
            s = delayLine.processSample(s)
            s = when (band) {
                CrossoverBand.LOW -> lowCrossover.processSample(s)
                CrossoverBand.HIGH -> highCrossover.processSample(s)
                CrossoverBand.FULL -> s
            }
            s *= gOut
            output[i] = limiter.processSample(s)
        }

        return ChannelEngineTelemetry(
            input = inputMeter.analyzeBlock(inMeterScratch, frameCount, sampleRate),
            output = outputMeter.analyzeBlock(
                output,
                frameCount,
                sampleRate,
                limiterGrDb = if (isMuted) 0f else limiter.lastGainReductionDb,
                limiterActive = !isMuted && limiter.isLimiting
            )
        )
    }

    private fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()
}
