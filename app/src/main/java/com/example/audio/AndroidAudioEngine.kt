package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.dsp.AudioTelemetry
import com.example.dsp.DlmsDspProcessor
import com.example.dsp.DspState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time low-latency Android AudioRecord -> DlmsDspProcessor -> AudioTrack engine.
 * Never generates synthetic/fake audio or fake READY status when hardware/permissions are absent.
 */
class AndroidAudioEngine(
    private val context: Context,
    private val enumerator: AudioDeviceEnumerator,
    val dspProcessor: DlmsDspProcessor = DlmsDspProcessor()
) {
    companion object {
        private const val TAG = "AndroidAudioEngine"
    }

    private val _engineState = MutableStateFlow(AudioEngineState())
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    private val _telemetry = MutableStateFlow(AudioTelemetry())
    val telemetry: StateFlow<AudioTelemetry> = _telemetry.asStateFlow()

    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var activeRecord: AudioRecord? = null
    private var activeTrack: AudioTrack? = null

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun updateDspConfig(state: DspState) {
        val sr = _engineState.value.activeSampleRate.takeIf { it > 0 }?.toDouble() ?: 48000.0
        dspProcessor.updateConfig(state, sr)
    }

    fun start(
        preferredSampleRate: Int,
        bufferMultiplier: Int,
        preferredInputDeviceId: Int?,
        preferredOutputDeviceId: Int?,
        dspState: DspState
    ) {
        if (running.get()) return

        if (!hasRecordPermission()) {
            _engineState.value = AudioEngineState(
                status = AudioEngineStatus.PERMISSION_REQUIRED,
                statusLabel = "AUDIO: PERMISSION DENIED",
                errorMessage = "Microphone permission is required to capture live audio into the 2-way DSP engine."
            )
            _telemetry.value = AudioTelemetry()
            return
        }

        val caps = enumerator.queryCapabilities()
        if (caps.inputDevices.isEmpty()) {
            _engineState.value = AudioEngineState(
                status = AudioEngineStatus.ERROR,
                statusLabel = "AUDIO: NOT CONNECTED",
                errorMessage = "No audio input hardware device available on this Android device."
            )
            _telemetry.value = AudioTelemetry()
            return
        }
        if (caps.outputDevices.isEmpty()) {
            _engineState.value = AudioEngineState(
                status = AudioEngineStatus.ERROR,
                statusLabel = "AUDIO: NOT CONNECTED",
                errorMessage = "No audio output hardware device available on this Android device."
            )
            _telemetry.value = AudioTelemetry()
            return
        }

        _engineState.value = _engineState.value.copy(
            status = AudioEngineStatus.STARTING,
            statusLabel = "AUDIO: STARTING...",
            errorMessage = null
        )

        val sampleRate = if (preferredSampleRate in caps.supportedSampleRates) {
            preferredSampleRate
        } else {
            caps.nativeSampleRate
        }
        val frameBlock = (caps.nativeFramesPerBuffer * bufferMultiplier.coerceIn(1, 8)).coerceIn(128, 4096)

        try {
            // Probe Stereo Input first, fall back to Mono Input if device only supports 1-ch capture
            var hwChannels = 2
            var inChannelMask = AudioFormat.CHANNEL_IN_STEREO
            var minRecBytes = AudioRecord.getMinBufferSize(sampleRate, inChannelMask, AudioFormat.ENCODING_PCM_16BIT)
            if (minRecBytes <= 0) {
                hwChannels = 1
                inChannelMask = AudioFormat.CHANNEL_IN_MONO
                minRecBytes = AudioRecord.getMinBufferSize(sampleRate, inChannelMask, AudioFormat.ENCODING_PCM_16BIT)
            }
            if (minRecBytes <= 0) {
                throw IllegalStateException("Audio input format unsupported at $sampleRate Hz.")
            }

            val recBufferBytes = maxOf(minRecBytes, frameBlock * hwChannels * 2 * 2)
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(inChannelMask)
                        .build()
                )
                .setBufferSizeInBytes(recBufferBytes)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                // Fallback retry in Mono if Stereo init failed on device
                if (hwChannels == 2) {
                    hwChannels = 1
                    inChannelMask = AudioFormat.CHANNEL_IN_MONO
                    val monoMin = AudioRecord.getMinBufferSize(sampleRate, inChannelMask, AudioFormat.ENCODING_PCM_16BIT)
                    if (monoMin <= 0) throw IllegalStateException("AudioRecord initialization failed.")
                    val monoRecord = AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(inChannelMask)
                                .build()
                        )
                        .setBufferSizeInBytes(maxOf(monoMin, frameBlock * 2 * 2))
                        .build()
                    if (monoRecord.state != AudioRecord.STATE_INITIALIZED) {
                        monoRecord.release()
                        throw IllegalStateException("AudioRecord failed to initialize on device hardware.")
                    }
                    activeRecord = monoRecord
                } else {
                    throw IllegalStateException("AudioRecord failed to initialize on device hardware.")
                }
            } else {
                activeRecord = record
            }

            val finalRecord = activeRecord!!
            val inDeviceInfo = enumerator.findInputDeviceInfo(preferredInputDeviceId)
            if (inDeviceInfo != null) {
                finalRecord.setPreferredDevice(inDeviceInfo)
            }

            val minTrkBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minTrkBytes <= 0) {
                finalRecord.release()
                activeRecord = null
                throw IllegalStateException("Stereo AudioTrack format unsupported at $sampleRate Hz.")
            }

            val trkBufferBytes = maxOf(minTrkBytes, frameBlock * 2 * 2 * 2)
            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(trkBufferBytes)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val track = trackBuilder.build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                finalRecord.release()
                activeRecord = null
                throw IllegalStateException("AudioTrack failed to initialize output stream.")
            }
            activeTrack = track

            val outDeviceInfo = enumerator.findOutputDeviceInfo(preferredOutputDeviceId)
            if (outDeviceInfo != null) {
                track.setPreferredDevice(outDeviceInfo)
            }

            dspProcessor.resetAllStates()
            dspProcessor.updateConfig(dspState, sampleRate.toDouble())

            finalRecord.startRecording()
            track.play()

            val inName = inDeviceInfo?.let {
                caps.inputDevices.firstOrNull { d -> d.id == it.id }?.name
            } ?: caps.inputDevices.firstOrNull()?.name ?: "Default Input"

            val outName = outDeviceInfo?.let {
                caps.outputDevices.firstOrNull { d -> d.id == it.id }?.name
            } ?: caps.outputDevices.firstOrNull()?.name ?: "Default Output"

            val feedbackRisk = !enumerator.isHeadsetOrExternalOutput(preferredOutputDeviceId)

            running.set(true)
            _engineState.value = AudioEngineState(
                status = AudioEngineStatus.READY,
                statusLabel = "AUDIO: READY",
                errorMessage = null,
                isFeedbackRisk = feedbackRisk,
                activeSampleRate = sampleRate,
                activeBufferFrames = frameBlock,
                activeInputDeviceName = inName,
                activeOutputDeviceName = outName,
                hwInputChannels = hwChannels
            )

            val captureChannels = hwChannels
            val thread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val shortIn = ShortArray(frameBlock * captureChannels)
                val shortOut = ShortArray(frameBlock * 2)
                val inL = FloatArray(frameBlock)
                val inR = FloatArray(frameBlock)
                val outL = FloatArray(frameBlock)
                val outR = FloatArray(frameBlock)

                try {
                    while (running.get()) {
                        val readSamples = finalRecord.read(shortIn, 0, shortIn.size)
                        if (readSamples < 0) {
                            throw IllegalStateException("AudioRecord read error code: $readSamples")
                        }
                        if (readSamples == 0) continue

                        val framesRead = readSamples / captureChannels
                        if (captureChannels == 2) {
                            for (i in 0 until framesRead) {
                                inL[i] = shortIn[i * 2] / 32768f
                                inR[i] = shortIn[i * 2 + 1] / 32768f
                            }
                        } else {
                            for (i in 0 until framesRead) {
                                val s = shortIn[i] / 32768f
                                inL[i] = s
                                inR[i] = s
                            }
                        }

                        val tel = dspProcessor.processBlock(inL, inR, outL, outR, framesRead, captureChannels)
                        _telemetry.value = tel

                        for (i in 0 until framesRead) {
                            val l = (outL[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            val r = (outR[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            shortOut[i * 2] = l
                            shortOut[i * 2 + 1] = r
                        }
                        val written = track.write(shortOut, 0, framesRead * 2)
                        if (written < 0) {
                            throw IllegalStateException("AudioTrack write error code: $written")
                        }
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        Log.e(TAG, "Audio processing loop error", e)
                        running.set(false)
                        _engineState.value = AudioEngineState(
                            status = AudioEngineStatus.ERROR,
                            statusLabel = "AUDIO: ENGINE ERROR",
                            errorMessage = e.localizedMessage ?: "Audio engine stopped unexpectedly."
                        )
                        _telemetry.value = AudioTelemetry()
                    }
                }
            }, "DLMS-DSP-AudioThread")
            workerThread = thread
            thread.start()
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException starting audio engine", se)
            stopInternal()
            _engineState.value = AudioEngineState(
                status = AudioEngineStatus.PERMISSION_REQUIRED,
                statusLabel = "AUDIO: PERMISSION DENIED",
                errorMessage = "Microphone permission denied by system."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio engine", e)
            stopInternal()
            _engineState.value = AudioEngineState(
                status = AudioEngineStatus.ERROR,
                statusLabel = "AUDIO: NOT CONNECTED",
                errorMessage = e.localizedMessage ?: "Audio engine failed to start."
            )
        }
    }

    fun stop() {
        stopInternal()
        _engineState.value = AudioEngineState(
            status = AudioEngineStatus.NOT_CONNECTED,
            statusLabel = "AUDIO: NOT CONNECTED",
            errorMessage = null
        )
        _telemetry.value = AudioTelemetry()
    }

    private fun stopInternal() {
        running.set(false)
        try {
            activeRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            activeTrack?.pause()
            activeTrack?.flush()
            activeTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            workerThread?.join(400)
        } catch (_: Exception) {
        }
        workerThread = null
        try {
            activeRecord?.release()
        } catch (_: Exception) {
        }
        try {
            activeTrack?.release()
        } catch (_: Exception) {
        }
        activeRecord = null
        activeTrack = null
        dspProcessor.resetAllStates()
    }
}
