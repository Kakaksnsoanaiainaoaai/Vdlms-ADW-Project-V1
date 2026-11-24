package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
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
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified DLMS 2-Way Audio Router supporting:
 * 1. DEVICE_INPUT: Live AudioRecord (Mic / USB / Line) -> DlmsDspProcessor -> AudioTrack
 * 2. INTERNAL_MEDIA: Local Audio File (MediaExtractor/MediaCodec) -> DlmsDspProcessor -> AudioTrack
 * 3. YOUTUBE: Official YouTube Player (strictly checks legal/API PCM availability -> honestly reports UNAVAILABLE)
 *
 * Never generates fake/synthetic signals or claims ACTIVE routing when audio is not flowing into DSP.
 */
class AndroidAudioEngine(
    private val context: Context,
    private val enumerator: AudioDeviceEnumerator,
    val dspProcessor: DlmsDspProcessor = DlmsDspProcessor()
) {
    companion object {
        private const val TAG = "AndroidAudioEngine"
        const val YOUTUBE_UNAVAILABLE_NOTICE =
            "YOUTUBE AUDIO ROUTING: NOT AVAILABLE — Official YouTube embedded player/API does not expose raw PCM audio streams due to YouTube Terms & DRM protection."
    }

    private val _engineState = MutableStateFlow(AudioEngineState())
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    private val _telemetry = MutableStateFlow(AudioTelemetry())
    val telemetry: StateFlow<AudioTelemetry> = _telemetry.asStateFlow()

    private val runningDeviceCapture = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var activeRecord: AudioRecord? = null
    private var activeTrack: AudioTrack? = null
    @Volatile
    private var latestDspState: DspState = DspState.neutralDefault()

    private val mediaPlayer = InternalMediaAudioPlayer(
        context = context,
        dspProcessor = dspProcessor,
        onTelemetry = { tel -> _telemetry.value = tel },
        onMediaStateChanged = { mediaState ->
            _engineState.update { state ->
                val isMediaSource = state.selectedSource == AudioSourceType.INTERNAL_MEDIA
                val active = isMediaSource && mediaState.isPlaying
                state.copy(
                    mediaState = mediaState,
                    routingStatus = if (active) AudioRoutingStatus.ACTIVE else state.routingStatus,
                    activeSampleRate = if (active) mediaState.sampleRate else state.activeSampleRate,
                    hwInputChannels = if (active) mediaState.channels.coerceAtMost(2) else state.hwInputChannels
                )
            }
        },
        onRoutingChanged = { isActive, err ->
            _engineState.update { state ->
                if (state.selectedSource != AudioSourceType.INTERNAL_MEDIA) return@update state
                state.copy(
                    routingStatus = if (isActive) AudioRoutingStatus.ACTIVE else AudioRoutingStatus.UNAVAILABLE,
                    status = when {
                        isActive -> AudioEngineStatus.READY
                        err != null -> AudioEngineStatus.ERROR
                        else -> AudioEngineStatus.NOT_CONNECTED
                    },
                    statusLabel = when {
                        isActive -> "AUDIO: READY"
                        err != null -> "AUDIO: ERROR"
                        else -> "AUDIO: NOT CONNECTED"
                    },
                    errorMessage = err
                )
            }
        }
    ).apply {
        currentDspStateProvider = { latestDspState }
    }

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun updateDspConfig(state: DspState) {
        latestDspState = state
        val sr = _engineState.value.activeSampleRate.takeIf { it > 0 }?.toDouble() ?: 48000.0
        dspProcessor.updateConfig(state, sr)
    }

    fun selectSource(source: AudioSourceType) {
        if (_engineState.value.selectedSource == source) return
        stopAllSources()
        when (source) {
            AudioSourceType.DEVICE_INPUT -> {
                _engineState.update {
                    it.copy(
                        selectedSource = AudioSourceType.DEVICE_INPUT,
                        routingStatus = AudioRoutingStatus.UNAVAILABLE,
                        status = AudioEngineStatus.NOT_CONNECTED,
                        statusLabel = "AUDIO: NOT CONNECTED",
                        youtubeRoutingNotice = null,
                        errorMessage = null
                    )
                }
            }
            AudioSourceType.INTERNAL_MEDIA -> {
                _engineState.update {
                    it.copy(
                        selectedSource = AudioSourceType.INTERNAL_MEDIA,
                        routingStatus = AudioRoutingStatus.UNAVAILABLE,
                        status = AudioEngineStatus.NOT_CONNECTED,
                        statusLabel = "AUDIO: NOT CONNECTED",
                        youtubeRoutingNotice = null,
                        errorMessage = null
                    )
                }
            }
            AudioSourceType.YOUTUBE -> {
                // Honest status: official YouTube player/API does not provide raw PCM audio to app DSP
                _engineState.update {
                    it.copy(
                        selectedSource = AudioSourceType.YOUTUBE,
                        routingStatus = AudioRoutingStatus.UNAVAILABLE,
                        status = AudioEngineStatus.NOT_CONNECTED,
                        statusLabel = "YOUTUBE AUDIO ROUTING: NOT AVAILABLE",
                        youtubeRoutingNotice = YOUTUBE_UNAVAILABLE_NOTICE,
                        errorMessage = null,
                        isFeedbackRisk = false,
                        activeSampleRate = 0,
                        activeBufferFrames = 0,
                        hwInputChannels = 0
                    )
                }
                _telemetry.value = AudioTelemetry()
            }
        }
    }

    fun loadInternalMedia(uri: Uri, preferredOutputDeviceId: Int?, bufferMultiplier: Int) {
        selectSource(AudioSourceType.INTERNAL_MEDIA)
        mediaPlayer.setOutputConfig(
            outputDevice = enumerator.findOutputDeviceInfo(preferredOutputDeviceId),
            bufferMult = bufferMultiplier
        )
        val loaded = mediaPlayer.loadUri(uri)
        if (loaded.hasTrack) {
            mediaPlayer.play()
        }
    }

    fun toggleInternalMediaPlayPause(preferredOutputDeviceId: Int?, bufferMultiplier: Int) {
        if (_engineState.value.selectedSource != AudioSourceType.INTERNAL_MEDIA) {
            selectSource(AudioSourceType.INTERNAL_MEDIA)
        }
        mediaPlayer.setOutputConfig(
            outputDevice = enumerator.findOutputDeviceInfo(preferredOutputDeviceId),
            bufferMult = bufferMultiplier
        )
        if (mediaPlayer.getState().isPlaying) {
            mediaPlayer.pause()
        } else {
            mediaPlayer.play()
        }
    }

    fun seekInternalMedia(posMs: Long) = mediaPlayer.seekTo(posMs)

    fun toggleInternalMediaLoop() {
        mediaPlayer.setLooping(!mediaPlayer.getState().looping)
    }

    fun start(
        preferredSampleRate: Int,
        bufferMultiplier: Int,
        preferredInputDeviceId: Int?,
        preferredOutputDeviceId: Int?,
        dspState: DspState
    ) {
        latestDspState = dspState
        when (_engineState.value.selectedSource) {
            AudioSourceType.YOUTUBE -> {
                // Never pretend YouTube raw audio can be routed into internal DSP
                _engineState.update {
                    it.copy(
                        routingStatus = AudioRoutingStatus.UNAVAILABLE,
                        status = AudioEngineStatus.NOT_CONNECTED,
                        statusLabel = "YOUTUBE AUDIO ROUTING: NOT AVAILABLE",
                        youtubeRoutingNotice = YOUTUBE_UNAVAILABLE_NOTICE
                    )
                }
                _telemetry.value = AudioTelemetry()
                return
            }
            AudioSourceType.INTERNAL_MEDIA -> {
                mediaPlayer.setOutputConfig(
                    outputDevice = enumerator.findOutputDeviceInfo(preferredOutputDeviceId),
                    bufferMult = bufferMultiplier
                )
                mediaPlayer.play()
                return
            }
            AudioSourceType.DEVICE_INPUT -> startDeviceInput(
                preferredSampleRate,
                bufferMultiplier,
                preferredInputDeviceId,
                preferredOutputDeviceId,
                dspState
            )
        }
    }

    private fun startDeviceInput(
        preferredSampleRate: Int,
        bufferMultiplier: Int,
        preferredInputDeviceId: Int?,
        preferredOutputDeviceId: Int?,
        dspState: DspState
    ) {
        if (runningDeviceCapture.get()) return
        mediaPlayer.stop()

        if (!hasRecordPermission()) {
            _engineState.update {
                it.copy(
                    routingStatus = AudioRoutingStatus.UNAVAILABLE,
                    status = AudioEngineStatus.PERMISSION_REQUIRED,
                    statusLabel = "AUDIO: PERMISSION DENIED",
                    errorMessage = "Microphone permission is required to capture live audio into the 2-way DSP engine."
                )
            }
            _telemetry.value = AudioTelemetry()
            return
        }

        val caps = enumerator.queryCapabilities()
        if (caps.inputDevices.isEmpty() || caps.outputDevices.isEmpty()) {
            _engineState.update {
                it.copy(
                    routingStatus = AudioRoutingStatus.UNAVAILABLE,
                    status = AudioEngineStatus.ERROR,
                    statusLabel = "AUDIO: NOT CONNECTED",
                    errorMessage = "Required audio input or output hardware device not available."
                )
            }
            _telemetry.value = AudioTelemetry()
            return
        }

        _engineState.update {
            it.copy(
                status = AudioEngineStatus.STARTING,
                statusLabel = "AUDIO: STARTING...",
                errorMessage = null
            )
        }

        val sampleRate = if (preferredSampleRate in caps.supportedSampleRates) {
            preferredSampleRate
        } else {
            caps.nativeSampleRate
        }
        val frameBlock = (caps.nativeFramesPerBuffer * bufferMultiplier.coerceIn(1, 8)).coerceIn(128, 4096)

        try {
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
            enumerator.findInputDeviceInfo(preferredInputDeviceId)?.let { finalRecord.setPreferredDevice(it) }

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
            enumerator.findOutputDeviceInfo(preferredOutputDeviceId)?.let { track.setPreferredDevice(it) }

            dspProcessor.resetAllStates()
            dspProcessor.updateConfig(dspState, sampleRate.toDouble())

            finalRecord.startRecording()
            track.play()

            val inName = enumerator.findInputDeviceInfo(preferredInputDeviceId)?.let {
                caps.inputDevices.firstOrNull { d -> d.id == it.id }?.name
            } ?: caps.inputDevices.firstOrNull()?.name ?: "Default Input"

            val outName = enumerator.findOutputDeviceInfo(preferredOutputDeviceId)?.let {
                caps.outputDevices.firstOrNull { d -> d.id == it.id }?.name
            } ?: caps.outputDevices.firstOrNull()?.name ?: "Default Output"

            val feedbackRisk = !enumerator.isHeadsetOrExternalOutput(preferredOutputDeviceId)

            runningDeviceCapture.set(true)
            _engineState.update {
                it.copy(
                    selectedSource = AudioSourceType.DEVICE_INPUT,
                    routingStatus = AudioRoutingStatus.ACTIVE,
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
            }

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
                    while (runningDeviceCapture.get()) {
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
                            shortOut[i * 2] = (outL[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            shortOut[i * 2 + 1] = (outR[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        }
                        val written = track.write(shortOut, 0, framesRead * 2)
                        if (written < 0) {
                            throw IllegalStateException("AudioTrack write error code: $written")
                        }
                    }
                } catch (e: Exception) {
                    if (runningDeviceCapture.get()) {
                        Log.e(TAG, "Audio processing loop error", e)
                        runningDeviceCapture.set(false)
                        _engineState.update {
                            it.copy(
                                routingStatus = AudioRoutingStatus.UNAVAILABLE,
                                status = AudioEngineStatus.ERROR,
                                statusLabel = "AUDIO: ENGINE ERROR",
                                errorMessage = e.localizedMessage ?: "Audio engine stopped unexpectedly."
                            )
                        }
                        _telemetry.value = AudioTelemetry()
                    }
                }
            }, "DLMS-DSP-AudioThread")
            workerThread = thread
            thread.start()
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException starting audio engine", se)
            stopDeviceInternal()
            _engineState.update {
                it.copy(
                    routingStatus = AudioRoutingStatus.UNAVAILABLE,
                    status = AudioEngineStatus.PERMISSION_REQUIRED,
                    statusLabel = "AUDIO: PERMISSION DENIED",
                    errorMessage = "Microphone permission denied by system."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio engine", e)
            stopDeviceInternal()
            _engineState.update {
                it.copy(
                    routingStatus = AudioRoutingStatus.UNAVAILABLE,
                    status = AudioEngineStatus.ERROR,
                    statusLabel = "AUDIO: NOT CONNECTED",
                    errorMessage = e.localizedMessage ?: "Audio engine failed to start."
                )
            }
        }
    }

    fun stop() {
        stopAllSources()
        _engineState.update {
            it.copy(
                routingStatus = AudioRoutingStatus.UNAVAILABLE,
                status = AudioEngineStatus.NOT_CONNECTED,
                statusLabel = if (it.selectedSource == AudioSourceType.YOUTUBE) {
                    "YOUTUBE AUDIO ROUTING: NOT AVAILABLE"
                } else {
                    "AUDIO: NOT CONNECTED"
                },
                errorMessage = null
            )
        }
        _telemetry.value = AudioTelemetry()
    }

    private fun stopAllSources() {
        stopDeviceInternal()
        mediaPlayer.stop()
    }

    private fun stopDeviceInternal() {
        runningDeviceCapture.set(false)
        try { activeRecord?.stop() } catch (_: Exception) {}
        try {
            activeTrack?.pause()
            activeTrack?.flush()
            activeTrack?.stop()
        } catch (_: Exception) {}
        try { workerThread?.join(400) } catch (_: Exception) {}
        workerThread = null
        try { activeRecord?.release() } catch (_: Exception) {}
        try { activeTrack?.release() } catch (_: Exception) {}
        activeRecord = null
        activeTrack = null
        dspProcessor.resetAllStates()
    }
}
