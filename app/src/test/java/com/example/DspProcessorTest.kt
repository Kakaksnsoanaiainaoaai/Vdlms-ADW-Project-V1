package com.example

import com.example.dsp.BiquadFilter
import com.example.dsp.CrossoverBand
import com.example.dsp.CrossoverFilterBank
import com.example.dsp.CrossoverFilterType
import com.example.dsp.CrossoverSlope
import com.example.dsp.DlmsDspProcessor
import com.example.dsp.DspState
import com.example.dsp.DspUnits
import com.example.dsp.LimiterConfig
import com.example.dsp.OutputSource
import com.example.youtube.YouTubeUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DspProcessorTest {

    @Test
    fun inputChannelsRemainStrictlySeparated() {
        val processor = DlmsDspProcessor()
        val state = DspState.neutralDefault().let {
            it.copy(
                inputL = it.inputL.copy(muted = true),
                inputR = it.inputR.copy(muted = false, gainDb = 0f),
                outputL = it.outputL.copy(source = OutputSource.IN_L, band = CrossoverBand.FULL),
                outputR = it.outputR.copy(source = OutputSource.IN_R, band = CrossoverBand.FULL)
            )
        }
        processor.updateConfig(state, 48000.0)

        val n = 512
        val inL = FloatArray(n) { 0.4f * sin(2.0 * PI * 440.0 * it / 48000.0).toFloat() }
        val inR = FloatArray(n) { 0.4f * sin(2.0 * PI * 1000.0 * it / 48000.0).toFloat() }
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        val tel = processor.processBlock(inL, inR, outL, outR, n, hwInputChannels = 2)

        assertTrue("Output L should be silent when Input L is muted", outL.all { abs(it) < 1e-6f })
        assertTrue("Output R should remain active when Input L is muted", outR.any { abs(it) > 0.1f })
        assertTrue(tel.inputL.rmsDb <= -59f)
        assertTrue(tel.inputR.rmsDb > -20f)
    }

    @Test
    fun crossover2WayAttenuatesOutOfBandFrequencies() {
        val lowBank = CrossoverFilterBank(isLowPass = true)
        val highBank = CrossoverFilterBank(isLowPass = false)
        val cfg = DspState.neutralDefault().crossover.copy(
            low = DspState.neutralDefault().crossover.low.copy(
                frequencyHz = 200f,
                filterType = CrossoverFilterType.LINKWITZ_RILEY,
                slope = CrossoverSlope.DB_24
            ),
            high = DspState.neutralDefault().crossover.high.copy(
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
    fun limiterEngagesAboveThresholdAndReportsGainReduction() {
        val processor = DlmsDspProcessor()
        val state = DspState.neutralDefault().let {
            it.copy(
                outputL = it.outputL.copy(
                    source = OutputSource.IN_L,
                    band = CrossoverBand.FULL,
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
        val inR = FloatArray(n)
        val outL = FloatArray(n)
        val outR = FloatArray(n)

        val tel = processor.processBlock(inL, inR, outL, outR, n, hwInputChannels = 2)

        assertTrue("Limiter should engage for -1.4 dBFS input against -12 dBFS threshold", tel.outputL.limiterActive)
        assertTrue("Limiter GR should be positive", tel.outputL.limiterGrDb > 2f)
        assertFalse(tel.outputR.limiterActive)
    }

    @Test
    fun delayUnitsAndEqBiquadMathAreAccurate() {
        val meters = DspUnits.msToMeters(10f)
        assertEquals(3.43f, meters, 0.01f)

        val eq = BiquadFilter()
        eq.configurePeaking(1000.0, 6.0, 1.4, 48000.0)
        val boostAtCenter = eq.magnitudeDbAt(1000.0, 48000.0)
        assertEquals(6.0, boostAtCenter, 0.2)
    }

    @Test
    fun youtubeUrlParserExtractsValidIds() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("dQw4w9WgXcQ"))
        assertEquals(null, YouTubeUrlParser.extractVideoId("subwoofer crossover test"))
    }
}
