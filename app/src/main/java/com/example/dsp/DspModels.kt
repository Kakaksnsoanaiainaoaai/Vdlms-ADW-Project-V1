package com.example.dsp

import com.squareup.moshi.JsonClass
import kotlin.math.roundToInt

enum class EqFilterType(val label: String, val shortLabel: String) {
    PEAK("Parametric Peak", "PEAK"),
    LOW_SHELF("Low Shelf", "L-SHF"),
    HIGH_SHELF("High Shelf", "H-SHF")
}

enum class CrossoverFilterType(val label: String, val shortLabel: String) {
    BUTTERWORTH("Butterworth", "BW"),
    LINKWITZ_RILEY("Linkwitz-Riley", "LR")
}

enum class CrossoverSlope(val dbPerOct: Int, val label: String) {
    DB_12(12, "12 dB/oct"),
    DB_18(18, "18 dB/oct"),
    DB_24(24, "24 dB/oct"),
    DB_48(48, "48 dB/oct")
}

enum class CrossoverBand(val label: String) {
    LOW("LOW"),
    HIGH("HIGH"),
    FULL("FULL")
}

@JsonClass(generateAdapter = true)
data class EqBandConfig(
    val id: Int,
    val enabled: Boolean = true,
    val filterType: EqFilterType = EqFilterType.PEAK,
    val frequencyHz: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 1.4f
) {
    fun validated(): EqBandConfig = copy(
        frequencyHz = frequencyHz.coerceIn(20f, 20000f),
        gainDb = ((gainDb.coerceIn(-15f, 15f)) * 10f).roundToInt() / 10f,
        q = ((q.coerceIn(0.3f, 16.0f)) * 10f).roundToInt() / 10f
    )
}

@JsonClass(generateAdapter = true)
data class CrossoverBandConfig(
    val frequencyHz: Float = 100f,
    val filterType: CrossoverFilterType = CrossoverFilterType.LINKWITZ_RILEY,
    val slope: CrossoverSlope = CrossoverSlope.DB_24,
    val bypass: Boolean = false
) {
    fun validated(): CrossoverBandConfig {
        val validSlope = if (filterType == CrossoverFilterType.LINKWITZ_RILEY && slope == CrossoverSlope.DB_18) {
            CrossoverSlope.DB_24
        } else {
            slope
        }
        return copy(
            frequencyHz = frequencyHz.coerceIn(20f, 20000f),
            slope = validSlope
        )
    }
}

@JsonClass(generateAdapter = true)
data class CrossoverConfig(
    val bypass: Boolean = false,
    val low: CrossoverBandConfig = CrossoverBandConfig(
        frequencyHz = 100f,
        filterType = CrossoverFilterType.LINKWITZ_RILEY,
        slope = CrossoverSlope.DB_24,
        bypass = false
    ),
    val high: CrossoverBandConfig = CrossoverBandConfig(
        frequencyHz = 100f,
        filterType = CrossoverFilterType.LINKWITZ_RILEY,
        slope = CrossoverSlope.DB_24,
        bypass = false
    )
) {
    fun validated(): CrossoverConfig = copy(
        low = low.validated(),
        high = high.validated()
    )
}

@JsonClass(generateAdapter = true)
data class LimiterConfig(
    val enabled: Boolean = true,
    val thresholdDbfs: Float = -3.0f,
    val attackMs: Float = 5.0f,
    val releaseMs: Float = 100f,
    val makeupGainDb: Float = 0.0f
) {
    fun validated(): LimiterConfig = copy(
        thresholdDbfs = ((thresholdDbfs.coerceIn(-30f, 0f)) * 10f).roundToInt() / 10f,
        attackMs = ((attackMs.coerceIn(0.5f, 100f)) * 10f).roundToInt() / 10f,
        releaseMs = releaseMs.coerceIn(10f, 2000f).roundToInt().toFloat(),
        makeupGainDb = ((makeupGainDb.coerceIn(-12f, 12f)) * 10f).roundToInt() / 10f
    )
}

/**
 * Completely independent per-channel DSP chain configuration.
 * Channel L and Channel R each own their own separate instance of this class:
 * INPUT -> INPUT GAIN -> 5-BAND EQ -> DELAY -> XOVER (LOW/HIGH/FULL) -> OUTPUT GAIN -> LIMITER -> MUTE -> OUTPUT.
 */
@JsonClass(generateAdapter = true)
data class ChannelDspConfig(
    val inputGainDb: Float = 0f,
    val inputPolarityInverted: Boolean = false,
    val eqBypass: Boolean = false,
    val eqBands: List<EqBandConfig> = defaultEqBands(),
    val delayMs: Float = 0f,
    val crossover: CrossoverConfig = CrossoverConfig(),
    val activeBand: CrossoverBand = CrossoverBand.LOW,
    val limiter: LimiterConfig = LimiterConfig(),
    val outputGainDb: Float = 0f,
    val outputPolarityInverted: Boolean = false,
    val muted: Boolean = false
) {
    fun validated(): ChannelDspConfig = copy(
        inputGainDb = ((inputGainDb.coerceIn(-24f, 12f)) * 10f).roundToInt() / 10f,
        eqBands = (if (eqBands.size == 5) eqBands else defaultEqBands()).map { it.validated() },
        delayMs = ((delayMs.coerceIn(0f, 50f)) * 100f).roundToInt() / 100f,
        crossover = crossover.validated(),
        limiter = limiter.validated(),
        outputGainDb = ((outputGainDb.coerceIn(-24f, 12f)) * 10f).roundToInt() / 10f
    )
}

/**
 * Top-level DSP configuration containing two strictly separated channel chains:
 * channelL and channelR. No shared EQ, Crossover, Delay, Gain, Limiter, or Mute exists.
 */
@JsonClass(generateAdapter = true)
data class DspState(
    val dspEnabled: Boolean = true,
    val channelL: ChannelDspConfig = ChannelDspConfig(activeBand = CrossoverBand.LOW),
    val channelR: ChannelDspConfig = ChannelDspConfig(activeBand = CrossoverBand.HIGH)
) {
    fun validated(): DspState = copy(
        channelL = channelL.validated(),
        channelR = channelR.validated()
    )

    companion object {
        fun neutralDefault(): DspState = DspState()
    }
}

fun defaultEqBands(): List<EqBandConfig> = listOf(
    EqBandConfig(id = 1, enabled = true, filterType = EqFilterType.LOW_SHELF, frequencyHz = 63f, gainDb = 0f, q = 0.7f),
    EqBandConfig(id = 2, enabled = true, filterType = EqFilterType.PEAK, frequencyHz = 250f, gainDb = 0f, q = 1.4f),
    EqBandConfig(id = 3, enabled = true, filterType = EqFilterType.PEAK, frequencyHz = 1000f, gainDb = 0f, q = 1.4f),
    EqBandConfig(id = 4, enabled = true, filterType = EqFilterType.PEAK, frequencyHz = 4000f, gainDb = 0f, q = 1.4f),
    EqBandConfig(id = 5, enabled = true, filterType = EqFilterType.HIGH_SHELF, frequencyHz = 12000f, gainDb = 0f, q = 0.7f)
)

data class ChannelTelemetry(
    val rmsDb: Float = -60f,
    val peakDb: Float = -60f,
    val peakHoldDb: Float = -60f,
    val clipped: Boolean = false,
    val limiterGrDb: Float = 0f,
    val limiterActive: Boolean = false
)

data class AudioTelemetry(
    val inputL: ChannelTelemetry = ChannelTelemetry(),
    val inputR: ChannelTelemetry = ChannelTelemetry(),
    val outputL: ChannelTelemetry = ChannelTelemetry(),
    val outputR: ChannelTelemetry = ChannelTelemetry(),
    val actualSampleRate: Int = 0,
    val actualBufferFrames: Int = 0,
    val hwInputChannels: Int = 0
)

object DspUnits {
    private const val SPEED_OF_SOUND_M_PER_SEC = 343.0f

    fun msToMeters(ms: Float): Float = (ms / 1000f) * SPEED_OF_SOUND_M_PER_SEC

    fun metersToMs(meters: Float): Float = (meters / SPEED_OF_SOUND_M_PER_SEC) * 1000f
}
