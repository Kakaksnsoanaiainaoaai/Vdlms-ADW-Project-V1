package com.example.dsp

/**
 * Dual-Independent-Channel 2-Way DLMS DSP Graph.
 *
 * Strictly separated stereo architecture:
 *   INPUT L -> ChannelDspEngine (L) [Gain L -> EQ L -> Delay L -> XOVER L -> Out Gain L -> Limiter L -> Mute L] -> OUTPUT L
 *   INPUT R -> ChannelDspEngine (R) [Gain R -> EQ R -> Delay R -> XOVER R -> Out Gain R -> Limiter R -> Mute R] -> OUTPUT R
 *
 * Zero DSP filters, delay buffers, crossover banks, limiters, or meters are shared between L and R.
 */
class DlmsDspProcessor {
    @Volatile
    private var currentConfig: DspState = DspState.neutralDefault()
    @Volatile
    private var sampleRate: Double = 48000.0
    @Volatile
    private var pendingReconfigure = true

    val engineL = ChannelDspEngine()
    val engineR = ChannelDspEngine()

    fun updateConfig(state: DspState, newSampleRate: Double = sampleRate) {
        currentConfig = state.validated()
        sampleRate = newSampleRate
        pendingReconfigure = true
    }

    fun resetAllStates() {
        engineL.resetState()
        engineR.resetState()
    }

    private fun applyPendingConfig() {
        val cfg = currentConfig
        val sr = sampleRate
        engineL.configure(cfg.channelL, sr)
        engineR.configure(cfg.channelR, sr)
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

        val cfg = currentConfig
        val sr = sampleRate

        val resL = engineL.processBlock(inL, outL, frameCount, cfg.dspEnabled, cfg.channelL, sr)
        val resR = engineR.processBlock(inR, outR, frameCount, cfg.dspEnabled, cfg.channelR, sr)

        return AudioTelemetry(
            inputL = resL.input,
            inputR = resR.input,
            outputL = resL.output,
            outputR = resR.output,
            actualSampleRate = sr.toInt(),
            actualBufferFrames = frameCount,
            hwInputChannels = hwInputChannels
        )
    }
}
