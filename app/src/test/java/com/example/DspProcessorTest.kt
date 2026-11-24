package com.example

import com.example.audio.AudioEngineState
import com.example.audio.AudioRoutingStatus
import com.example.audio.AudioSourceType
import com.example.dsp.BiquadFilter
import com.example.dsp.CrossoverBand
import com.example.dsp.CrossoverFilterBank
import com.example.dsp.CrossoverFilterType
import com.example.dsp.CrossoverSlope
import com.example.dsp.DlmsDspProcessor
import com.example.dsp.DspState
import com.example.dsp.DspUnits
import com.example.dsp.EqFilterType
import com.example.dsp.LimiterConfig
import com.example.youtube.YouTubeUrlParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class DspProcessorTest {

    private fun rms(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        var sum = 0.0
        val count = (end - start).coerceAtLeast(1)
        for (i in start until end) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count).toFloat()
    }

    @Test
    fun test1_gainLChangesOnlyChannelLAndLeavesChannelRUnchanged() {
        val processor = DlmsDspProcessor()
        val baseline = DspState.neutralDefault().let {
            it.copy(
                channelL = it.channelL.copy(activeBand = CrossoverBand.FULL, inputGainDb = 0f, outputGainDb = 0f),
                channelR = it.channelR.copy(activeBand = CrossoverBand.FULL, inputGainDb = 0f, outputGainDb = 0f)
            )
        }
        val n = 1024
        val inL = FloatArray(n) { 0.15f * sin(2.0 * PI * 440.0 * it / 48000.0).toFloat() }
        val inR = FloatArray(n) { 0.15f * sin(2.0 * PI * 440.0 * it / 48000.0).toFloat() }
        val baseOutL = FloatArray(n)
        val baseOutR = FloatArray(n)

        processor.updateConfig(baseline, 48000.0)
        processor.processBlock(inL, inR, baseOutL, baseOutR, n, hwInputChannels = 2)
        val baseRmsL = rms(baseOutL, 256, n)
        val baseRmsR = rms(baseOutR, 256, n)

        val boostedL = baseline.copy(
            channelL = baseline.channelL.copy(inputGainDb = 6.0f, outputGainDb = 3.0f)
        )
        val newOutL = FloatArray(n)
        val newOutR = FloatArray(n)
        processor.resetAllStates()
        processor.updateConfig(boostedL, 48000.0)
        processor.processBlock(inL, inR, newOutL, newOutR, n, hwInputChannels = 2)
        val newRmsL = rms(newOutL, 256, n)
        val newRmsR = rms(newOutR, 256, n)

        assertTrue("Raising Gain L must significantly increase Output L RMS", newRmsL > baseRmsL * 2.2f)
        assertEquals("Raising Gain L must NOT alter Output R RMS", baseRmsR, newRmsR, 1e-5f)
    }

    @Test
    fun test2_eqLAffectsOnlyChannelLAndNotChannelR() {
        val processor = DlmsDspProcessor()
        val baseBands = DspState.neutralDefault().channelL.eqBands.mapIndexed { idx, band ->
            if (idx == 2) band.copy(enabled = true, filterType = EqFilterType.PEAK, frequencyHz = 1000f, gainDb = 12f, q = 1.4f)
            else band.copy(gainDb = 0f)
        }
        val state = DspState.neutralDefault().let {
            it.copy(
                channelL = it.channelL.copy(activeBand = CrossoverBand.FULL, eqBypass = false, eqBands = baseBands),
                channelR = it.channelR.copy(activeBand = CrossoverBand.FULL, eqBypass = false)
            )
        }
        processor.updateConfig(state, 48000.0)

        val n = 2048
        val inL = FloatArray(n) { 0.1f * sin(2.0 * PI * 1000.0 * it / 48000.0).toFloat() }
        val inR = FloatArray(n) { 0.1f * sin(2.0 * PI * 1000.0 * it / 48000.0).toFloat() }
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        processor.processBlock(inL, inR, outL, outR, n, hwInputChannels = 2)
        val rmsL = rms(outL, 512, n)
        val rmsR = rms(outR, 512, n)

        assertTrue("EQ L +12dB boost at 1kHz must boost Channel L", rmsL > 0.25f)
        assertTrue("EQ L must NOT boost flat Channel R", rmsR < 0.08f)
        assertEquals(0f, state.channelR.eqBands[2].gainDb, 0.001f)
    }

    @Test
    fun test3_xoverLAffectsOnlyChannelLWhileXoverRKeepsOwnSettings() {
        val processor = DlmsDspProcessor()
        val state = DspState.neutralDefault().let {
            it.copy(
                channelL = it.channelL.copy(
                    activeBand = CrossoverBand.LOW,
                    crossover = it.channelL.crossover.copy(
                        bypass = false,
                        low = it.channelL.crossover.low.copy(
                            frequencyHz = 150f,
                            filterType = CrossoverFilterType.LINKWITZ_RILEY,
                            slope = CrossoverSlope.DB_24
                        )
                    )
                ),
                channelR = it.channelR.copy(
                    activeBand = CrossoverBand.HIGH,
                    crossover = it.channelR.crossover.copy(
                        bypass = false,
                        high = it.channelR.crossover.high.copy(
                            frequencyHz = 500f,
                            filterType = CrossoverFilterType.BUTTERWORTH,
                            slope = CrossoverSlope.DB_12
                        )
                    )
                )
            )
        }
        processor.updateConfig(state, 48000.0)

        // Feed 2 kHz into both L and R:
        // L uses 150 Hz LPF -> heavily attenuated; R uses 500 Hz HPF -> passes cleanly
        val n = 2048
        val inL = FloatArray(n) { 0.3f * sin(2.0 * PI * 2000.0 * it / 48000.0).toFloat() }
        val inR = FloatArray(n) { 0.3f * sin(2.0 * PI * 2000.0 * it / 48000.0).toFloat() }
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        processor.processBlock(inL, inR, outL, outR, n, hwInputChannels = 2)
        val rmsL = rms(outL, 1024, n)
        val rmsR = rms(outR, 1024, n)

        assertTrue("XOVER L 150Hz LPF should strongly attenuate 2kHz on Channel L", rmsL < 0.01f)
        assertTrue("XOVER R 500Hz HPF should pass 2kHz cleanly on Channel R", rmsR > 0.18f)
        assertNotEquals(state.channelL.crossover.low.frequencyHz, state.channelR.crossover.high.frequencyHz)
    }

    @Test
    fun test4_muteLSilencesOutputLOnlyWhileOutputRContinues() {
        val processor = DlmsDspProcessor()
        val state = DspState.neutralDefault().let {
            it.copy(
                channelL = it.channelL.copy(muted = true, activeBand = CrossoverBand.FULL),
                channelR = it.channelR.copy(muted = false, activeBand = CrossoverBand.FULL)
            )
        }
        processor.updateConfig(state, 48000.0)

        val n = 512
        val inL = FloatArray(n) { 0.4f * sin(2.0 * PI * 440.0 * it / 48000.0).toFloat() }
        val inR = FloatArray(n) { 0.4f * sin(2.0 * PI * 1000.0 * it / 48000.0).toFloat() }
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        val tel = processor.processBlock(inL, inR, outL, outR, n, hwInputChannels = 2)

        assertTrue("Output L must be silent when Channel L is muted", outL.all { abs(it) < 1e-6f })
        assertTrue("Output R must stay active when Channel L is muted", outR.any { abs(it) > 0.1f })
        assertTrue(tel.outputL.rmsDb <= -59f)
        assertTrue(tel.outputR.rmsDb > -20f)
    }

    @Test
    fun test5_delayRDelaysOnlyChannelRAndNotChannelL() {
        val processor = DlmsDspProcessor()
        val state = DspState.neutralDefault().let {
            it.copy(
                channelL = it.channelL.copy(delayMs = 0f, activeBand = CrossoverBand.FULL),
                channelR = it.channelR.copy(delayMs = 10f, activeBand = CrossoverBand.FULL)
            )
        }
        processor.updateConfig(state, 48000.0)

        // Impulse at index 0 on both channels; 10ms at 48kHz = 480 samples
        val n = 1024
        val inL = FloatArray(n).apply { this[0] = 0.5f }
        val inR = FloatArray(n).apply { this[0] = 0.5f }
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        processor.processBlock(inL, inR, outL, outR, n, hwInputChannels = 2)

        assertTrue("Channel L (0ms delay) should output impulse immediately at index 0", abs(outL[0]) > 0.4f)
        assertTrue("Channel R (10ms delay) must be silent at index 0", abs(outR[0]) < 1e-6f)
        assertTrue("Channel R impulse should arrive around 480 samples (10ms)", abs(outR[480]) > 0.4f)
    }

    @Test
    fun test6_limiterREngagesOnlyChannelRAndNotChannelL() {
        val processor = DlmsDspProcessor()
        val state = DspState.neutralDefault().let {
            it.copy(
                channelL = it.channelL.copy(
                    activeBand = CrossoverBand.FULL,
                    limiter = LimiterConfig(enabled = false)
                ),
                channelR = it.channelR.copy(
                    activeBand = CrossoverBand.FULL,
                    limiter = LimiterConfig(
                        enabled = true,
                        thresholdDbfs = -12f,
                        attackMs = 0.5f,
                        releaseMs = 100f,
                        makeupGainDb = 0f
                    )
                )
            )
        }
        processor.updateConfig(state, 48000.0)

        val n = 2048
        val inL = FloatArray(n) { 0.85f * sin(2.0 * PI * 1000.0 * it / 48000.0).toFloat() }
        val inR = FloatArray(n) { 0.85f * sin(2.0 * PI * 1000.0 * it / 48000.0).toFloat() }
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        val tel = processor.processBlock(inL, inR, outL, outR, n, hwInputChannels = 2)

        assertFalse("Limiter L is bypassed and must not engage", tel.outputL.limiterActive)
        assertTrue("Limiter R must engage on -1.4 dBFS signal vs -12 dBFS threshold", tel.outputR.limiterActive)
        assertTrue("Limiter R GR must be positive", tel.outputR.limiterGrDb > 2f)
        assertTrue("Un-limited Output L should remain louder than limited Output R", rms(outL, 512, n) > rms(outR, 512, n) * 1.5f)
    }

    @Test
    fun test7_stereoSourceRoutesStrictlyLeftToChainLAndRightToChainR() {
        val processor = DlmsDspProcessor()
        val state = DspState.neutralDefault().let {
            it.copy(
                channelL = it.channelL.copy(activeBand = CrossoverBand.FULL),
                channelR = it.channelR.copy(activeBand = CrossoverBand.FULL)
            )
        }
        processor.updateConfig(state, 48000.0)

        // Stereo test: L has signal, R is silent -> Out R must have ZERO crosstalk
        val n = 1024
        val inLOnly = FloatArray(n) { 0.5f * sin(2.0 * PI * 500.0 * it / 48000.0).toFloat() }
        val silentR = FloatArray(n)
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        processor.processBlock(inLOnly, silentR, outL, outR, n, hwInputChannels = 2)
        assertTrue("Signal in L must exit on Output L", rms(outL) > 0.25f)
        assertTrue("Zero crosstalk allowed into Channel R", outR.all { abs(it) < 1e-6f })

        // Reverse: R has signal, L is silent -> Out L must have ZERO crosstalk
        processor.resetAllStates()
        val silentL = FloatArray(n)
        val inROnly = FloatArray(n) { 0.5f * sin(2.0 * PI * 2500.0 * it / 48000.0).toFloat() }
        processor.processBlock(silentL, inROnly, outL, outR, n, hwInputChannels = 2)
        assertTrue("Zero crosstalk allowed into Channel L", outL.all { abs(it) < 1e-6f })
        assertTrue("Signal in R must exit on Output R", rms(outR) > 0.25f)
    }

    @Test
    fun presetSerializationPreservesSeparatedChannelLAndChannelR() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(DspState::class.java)

        val original = DspState.neutralDefault().let {
            it.copy(
                channelL = it.channelL.copy(
                    inputGainDb = 4.5f,
                    delayMs = 12.5f,
                    activeBand = CrossoverBand.LOW,
                    crossover = it.channelL.crossover.copy(
                        low = it.channelL.crossover.low.copy(frequencyHz = 120f)
                    )
                ),
                channelR = it.channelR.copy(
                    inputGainDb = -3.0f,
                    delayMs = 2.0f,
                    activeBand = CrossoverBand.HIGH,
                    crossover = it.channelR.crossover.copy(
                        high = it.channelR.crossover.high.copy(frequencyHz = 800f)
                    )
                )
            )
        }

        val json = adapter.toJson(original)
        val decoded = adapter.fromJson(json)!!

        assertEquals(4.5f, decoded.channelL.inputGainDb, 0.01f)
        assertEquals(12.5f, decoded.channelL.delayMs, 0.01f)
        assertEquals(120f, decoded.channelL.crossover.low.frequencyHz, 0.01f)
        assertEquals(-3.0f, decoded.channelR.inputGainDb, 0.01f)
        assertEquals(2.0f, decoded.channelR.delayMs, 0.01f)
        assertEquals(800f, decoded.channelR.crossover.high.frequencyHz, 0.01f)
    }

    @Test
    fun crossover2WayFilterBankAttenuatesOutOfBandFrequencies() {
        val lowBank = CrossoverFilterBank(isLowPass = true)
        val highBank = CrossoverFilterBank(isLowPass = false)
        val cfg = DspState.neutralDefault().channelL.crossover.copy(
            low = DspState.neutralDefault().channelL.crossover.low.copy(
                frequencyHz = 200f,
                filterType = CrossoverFilterType.LINKWITZ_RILEY,
                slope = CrossoverSlope.DB_24
            ),
            high = DspState.neutralDefault().channelL.crossover.high.copy(
                frequencyHz = 200f,
                filterType = CrossoverFilterType.LINKWITZ_RILEY,
                slope = CrossoverSlope.DB_24
            )
        )
        lowBank.configure(cfg.low, globalBypass = false, sampleRate = 48000.0)
        highBank.configure(cfg.high, globalBypass = false, sampleRate = 48000.0)

        assertTrue(lowBank.magnitudeDbAt(50.0, 48000.0) > -1.0f)
        assertTrue(lowBank.magnitudeDbAt(4000.0, 48000.0) < -40.0f)
        assertTrue(highBank.magnitudeDbAt(4000.0, 48000.0) > -1.0f)
        assertTrue(highBank.magnitudeDbAt(40.0, 48000.0) < -30.0f)
    }

    @Test
    fun delayUnitsEqMathAndUrlParserAreAccurate() {
        assertEquals(3.43f, DspUnits.msToMeters(10f), 0.01f)

        val eq = BiquadFilter()
        eq.configurePeaking(1000.0, 6.0, 1.4, 48000.0)
        assertEquals(6.0, eq.magnitudeDbAt(1000.0, 48000.0), 0.2)

        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, YouTubeUrlParser.extractVideoId("subwoofer crossover test"))

        val defaultState = AudioEngineState()
        assertEquals(AudioSourceType.DEVICE_INPUT, defaultState.selectedSource)
        assertEquals(AudioRoutingStatus.UNAVAILABLE, defaultState.routingStatus)
    }
}
