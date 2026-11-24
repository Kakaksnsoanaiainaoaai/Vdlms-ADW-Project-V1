package com.example.dsp

import kotlin.math.pow

/**
 * Complete 2-Input / 2-Output Digital Loudspeaker Management System (DLMS) DSP Graph.
 *
 * Signal Flow:
 *  IN L -> Gain -> Polarity -> Mute -> Delay -> 5-Band PEQ -> [Meter IN L] -\
 *                                                                            +-> Matrix & 2-Way Crossover
 *  IN R -> Gain -> Polarity -> Mute -> Delay -> 5-Band PEQ -> [Meter IN R] -/
 *
 *  OUT L <- Limiter <- 5-Band PEQ <- Delay <- Polarity <- Mute <- Gain <- (Source L/R/Sum -> LOW/HIGH/FULL) -> [Meter OUT L]
 *  OUT R <- Limiter <- 5-Band PEQ <- Delay <- Polarity <- Mute <- Gain <- (Source L/R/Sum -> LOW/HIGH/FULL) -> [Meter OUT R]
 */
class DlmsDspProcessor {
    @Volatile
    private var currentConfig: DspState = DspState.neutralDefault()
    @Volatile
    private var sampleRate: Double = 48000.0
    @Volatile
    private var pendingReconfigure = true

    // Input L DSP
    private val inLEq = Array(5) { BiquadFilter() }
    private val inLDelay = DelayLine()
    private val inLMeter = ChannelMeter()

    // Input R DSP
    private val inREq = Array(5) { BiquadFilter() }
    private val inRDelay = DelayLine()
    private val inRMeter = ChannelMeter()

    // Output L Crossover + DSP
    private val outLLowXover = CrossoverFilterBank(isLowPass = true)
    private val outLHighXover = CrossoverFilterBank(isLowPass = false)
    private val outLEq = Array(5) { BiquadFilter() }
    private val outLDelay = DelayLine()
    private val outLLimiter = DynamicsLimiter()
    private val outLMeter = ChannelMeter()

    // Output R Crossover + DSP
    private val outRLowXover = CrossoverFilterBank(isLowPass = true)
    private val outRHighXover = CrossoverFilterBank(isLowPass = false)
    private val outREq = Array(5) { BiquadFilter() }
    private val outRDelay = DelayLine()
    private val outRLimiter = DynamicsLimiter()
    private val outRMeter = ChannelMeter()

    // Pre-allocated scratch buffers (resized only if block size grows)
    private var procInL = FloatArray(4096)
    private var procInR = FloatArray(4096)

    // Precomputed linear gains
    private var inLGain = 1f
    private var inRGain = 1f
    private var outLGain = 1f
    private var outRGain = 1f

    fun updateConfig(state: DspState, newSampleRate: Double = sampleRate) {
        currentConfig = state.validated()
        sampleRate = newSampleRate
        pendingReconfigure = true
    }

    fun resetAllStates() {
        inLMeter.reset()
        inRMeter.reset()
        outLMeter.reset()
        outRMeter.reset()
        inLDelay.reset()
        inRDelay.reset()
        outLDelay.reset()
        outRDelay.reset()
        outLLimiter.reset()
        outRLimiter.reset()
        inLEq.forEach { it.resetState() }
        inREq.forEach { it.resetState() }
        outLEq.forEach { it.resetState() }
        outREq.forEach { it.resetState() }
        outLLowXover.resetState()
        outLHighXover.resetState()
        outRLowXover.resetState()
        outRHighXover.resetState()
    }

    private fun applyPendingConfig() {
        val cfg = currentConfig
        val sr = sampleRate

        inLGain = dbToLinear(cfg.inputL.gainDb) * (if (cfg.inputL.polarityInverted) -1f else 1f)
        inRGain = dbToLinear(cfg.inputR.gainDb) * (if (cfg.inputR.polarityInverted) -1f else 1f)
        outLGain = dbToLinear(cfg.outputL.gainDb) * (if (cfg.outputL.polarityInverted) -1f else 1f)
        outRGain = dbToLinear(cfg.outputR.gainDb) * (if (cfg.outputR.polarityInverted) -1f else 1f)

        inLDelay.setDelayMs(cfg.inputL.delayMs, sr)
        inRDelay.setDelayMs(cfg.inputR.delayMs, sr)
        outLDelay.setDelayMs(cfg.outputL.delayMs, sr)
        outRDelay.setDelayMs(cfg.outputR.delayMs, sr)

        for (i in 0 until 5) {
            if (cfg.inputL.eqBypass) inLEq[i].setBypass()
            else BiquadFilter.configureForEqBand(cfg.inputL.eqBands[i], sr, inLEq[i])

            if (cfg.inputR.eqBypass) inREq[i].setBypass()
            else BiquadFilter.configureForEqBand(cfg.inputR.eqBands[i], sr, inREq[i])

            if (cfg.outputL.eqBypass) outLEq[i].setBypass()
            else BiquadFilter.configureForEqBand(cfg.outputL.eqBands[i], sr, outLEq[i])

            if (cfg.outputR.eqBypass) outREq[i].setBypass()
            else BiquadFilter.configureForEqBand(cfg.outputR.eqBands[i], sr, outREq[i])
        }

        outLLowXover.configure(cfg.crossover.low, cfg.crossover.bypass, sr)
        outLHighXover.configure(cfg.crossover.high, cfg.crossover.bypass, sr)
        outRLowXover.configure(cfg.crossover.low, cfg.crossover.bypass, sr)
        outRHighXover.configure(cfg.crossover.high, cfg.crossover.bypass, sr)

        outLLimiter.configure(cfg.outputL.limiter, sr)
        outRLimiter.configure(cfg.outputR.limiter, sr)

        pendingReconfigure = false
    }

    fun processBlock(
        inL: FloatArray,
        inR: FloatArray,
        outL: FloatArray,
        outR: FloatArray,
        frameCount: Int,
        hwInputChannels: Int
    ): AudioTelemetry {
        if (pendingReconfigure) {
            applyPendingConfig()
        }
        if (procInL.size < frameCount) {
            procInL = FloatArray(frameCount)
            procInR = FloatArray(frameCount)
        }

        val cfg = currentConfig
        val sr = sampleRate

        if (!cfg.dspEnabled) {
            // Hardware DSP bypass: clean pass-through with safety clip and true metering
            for (i in 0 until frameCount) {
                val l = inL[i]
                val r = inR[i]
                procInL[i] = l
                procInR[i] = r
                outL[i] = l.coerceIn(-1f, 1f)
                outR[i] = r.coerceIn(-1f, 1f)
            }
            return AudioTelemetry(
                inputL = inLMeter.analyzeBlock(procInL, frameCount, sr),
                inputR = inRMeter.analyzeBlock(procInR, frameCount, sr),
                outputL = outLMeter.analyzeBlock(outL, frameCount, sr),
                outputR = outRMeter.analyzeBlock(outR, frameCount, sr),
                actualSampleRate = sr.toInt(),
                actualBufferFrames = frameCount,
                hwInputChannels = hwInputChannels
            )
        }

        // 1. Separated Input L Processing
        val inLMuted = cfg.inputL.muted
        val gInL = inLGain
        for (i in 0 until frameCount) {
            if (inLMuted) {
                procInL[i] = 0f
            } else {
                var s = inL[i] * gInL
                s = inLDelay.processSample(s)
                for (b in 0 until 5) {
                    s = inLEq[b].processSample(s)
                }
                procInL[i] = s
            }
        }

        // 2. Separated Input R Processing
        val inRMuted = cfg.inputR.muted
        val gInR = inRGain
        for (i in 0 until frameCount) {
            if (inRMuted) {
                procInR[i] = 0f
            } else {
                var s = inR[i] * gInR
                s = inRDelay.processSample(s)
                for (b in 0 until 5) {
                    s = inREq[b].processSample(s)
                }
                procInR[i] = s
            }
        }

        // 3. Matrix Routing + 2-Way Crossover + Output L Processing
        val outLMuted = cfg.outputL.muted
        val gOutL = outLGain
        val srcL = cfg.outputL.source
        val bandL = cfg.outputL.band
        for (i in 0 until frameCount) {
            if (outLMuted) {
                outL[i] = 0f
            } else {
                var s = when (srcL) {
                    OutputSource.IN_L -> procInL[i]
                    OutputSource.IN_R -> procInR[i]
                    OutputSource.SUM_LR -> (procInL[i] + procInR[i]) * 0.5f
                }
                s = when (bandL) {
                    CrossoverBand.LOW -> outLLowXover.processSample(s)
                    CrossoverBand.HIGH -> outLHighXover.processSample(s)
                    CrossoverBand.FULL -> s
                }
                s *= gOutL
                s = outLDelay.processSample(s)
                for (b in 0 until 5) {
                    s = outLEq[b].processSample(s)
                }
                outL[i] = outLLimiter.processSample(s)
            }
        }

        // 4. Matrix Routing + 2-Way Crossover + Output R Processing
        val outRMuted = cfg.outputR.muted
        val gOutR = outRGain
        val srcR = cfg.outputR.source
        val bandR = cfg.outputR.band
        for (i in 0 until frameCount) {
            if (outRMuted) {
                outR[i] = 0f
            } else {
                var s = when (srcR) {
                    OutputSource.IN_L -> procInL[i]
                    OutputSource.IN_R -> procInR[i]
                    OutputSource.SUM_LR -> (procInL[i] + procInR[i]) * 0.5f
                }
                s = when (bandR) {
                    CrossoverBand.LOW -> outRLowXover.processSample(s)
                    CrossoverBand.HIGH -> outRHighXover.processSample(s)
                    CrossoverBand.FULL -> s
                }
                s *= gOutR
                s = outRDelay.processSample(s)
                for (b in 0 until 5) {
                    s = outREq[b].processSample(s)
                }
                outR[i] = outRLimiter.processSample(s)
            }
        }

        return AudioTelemetry(
            inputL = inLMeter.analyzeBlock(procInL, frameCount, sr),
            inputR = inRMeter.analyzeBlock(procInR, frameCount, sr),
            outputL = outLMeter.analyzeBlock(
                outL,
                frameCount,
                sr,
                limiterGrDb = if (outLMuted) 0f else outLLimiter.lastGainReductionDb,
                limiterActive = !outLMuted && outLLimiter.isLimiting
            ),
            outputR = outRMeter.analyzeBlock(
                outR,
                frameCount,
                sr,
                limiterGrDb = if (outRMuted) 0f else outRLimiter.lastGainReductionDb,
                limiterActive = !outRMuted && outRLimiter.isLimiting
            ),
            actualSampleRate = sr.toInt(),
            actualBufferFrames = frameCount,
            hwInputChannels = hwInputChannels
        )
    }

    private fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()
}
