package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.OpenableColumns
import android.util.Log
import com.example.dsp.AudioTelemetry
import com.example.dsp.DlmsDspProcessor
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Decodes local audio media files via official Android MediaExtractor + MediaCodec,
 * splits stereo channels into Input L / Input R, processes through DlmsDspProcessor,
 * and outputs via low-latency AudioTrack.
 */
class InternalMediaAudioPlayer(
    private val context: Context,
    private val dspProcessor: DlmsDspProcessor,
    private val onTelemetry: (AudioTelemetry) -> Unit,
    private val onMediaStateChanged: (InternalMediaState) -> Unit,
    private val onRoutingChanged: (isActive: Boolean, error: String?) -> Unit
) {
    companion object {
        private const val TAG = "InternalMediaPlayer"
    }

    private var currentUri: Uri? = null
    private var currentState = InternalMediaState()
    private val isPlayingFlag = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val pendingSeekMs = AtomicLong(-1L)
    private var workerThread: Thread? = null
    private var preferredOutputDevice: AudioDeviceInfo? = null
    private var bufferMultiplier: Int = 2

    fun setOutputConfig(outputDevice: AudioDeviceInfo?, bufferMult: Int) {
        preferredOutputDevice = outputDevice
        bufferMultiplier = bufferMult.coerceIn(1, 8)
    }

    fun getState(): InternalMediaState = currentState

    fun loadUri(uri: Uri): InternalMediaState {
        stop()
        currentUri = uri
        val name = resolveDisplayName(uri)
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIdx = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIdx < 0 || audioFormat == null) {
                throw IllegalArgumentException("Selected file contains no playable audio track.")
            }
            val sr = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 48000
            val ch = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            val durUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            currentState = InternalMediaState(
                hasTrack = true,
                trackName = name,
                isPlaying = false,
                positionMs = 0L,
                durationMs = durUs / 1000L,
                sampleRate = sr,
                channels = ch,
                looping = currentState.looping,
                errorMessage = null
            )
            onMediaStateChanged(currentState)
            currentState
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect audio file", e)
            currentUri = null
            currentState = InternalMediaState(
                hasTrack = false,
                trackName = name,
                errorMessage = e.localizedMessage ?: "Unsupported audio file."
            )
            onMediaStateChanged(currentState)
            onRoutingChanged(false, currentState.errorMessage)
            currentState
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    fun play() {
        val uri = currentUri ?: run {
            onRoutingChanged(false, "Select an audio file first for INTERNAL MEDIA.")
            return
        }
        if (isPlayingFlag.get()) return
        startPlaybackThread(uri, startPosMs = currentState.positionMs)
    }

    fun pause() {
        if (!isPlayingFlag.get()) return
        stopInternal(resetPosition = false)
        currentState = currentState.copy(isPlaying = false)
        onMediaStateChanged(currentState)
        onRoutingChanged(false, null)
        onTelemetry(AudioTelemetry())
    }

    fun stop() {
        stopInternal(resetPosition = true)
        currentState = currentState.copy(isPlaying = false, positionMs = 0L)
        onMediaStateChanged(currentState)
        onRoutingChanged(false, null)
        onTelemetry(AudioTelemetry())
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, currentState.durationMs.coerceAtLeast(0L))
        pendingSeekMs.set(clamped)
        currentState = currentState.copy(positionMs = clamped)
        onMediaStateChanged(currentState)
    }

    fun setLooping(loop: Boolean) {
        currentState = currentState.copy(looping = loop)
        onMediaStateChanged(currentState)
    }

    private fun startPlaybackThread(uri: Uri, startPosMs: Long) {
        stopInternal(resetPosition = false)
        stopRequested.set(false)
        isPlayingFlag.set(true)

        val thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            var extractor: MediaExtractor? = null
            var codec: MediaCodec? = null
            var track: AudioTrack? = null

            try {
                extractor = MediaExtractor().apply { setDataSource(context, uri, null) }
                var audioIdx = -1
                var inputFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioIdx = i
                        inputFormat = fmt
                        break
                    }
                }
                if (audioIdx < 0 || inputFormat == null) {
                    throw IllegalStateException("No audio track found in file.")
                }
                extractor.selectTrack(audioIdx)
                if (startPosMs > 0L) {
                    extractor.seekTo(startPosMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }

                val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
                var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(inputFormat, null, null, 0)
                    start()
                }

                fun createAudioTrack(sr: Int): AudioTrack {
                    val minBytes = AudioTrack.getMinBufferSize(
                        sr,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(4096)
                    val bufBytes = maxOf(minBytes, 256 * bufferMultiplier * 4)
                    val builder = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sr)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(bufBytes)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                    return builder.build().also { t ->
                        if (t.state != AudioTrack.STATE_INITIALIZED) {
                            throw IllegalStateException("Failed to initialize AudioTrack at $sr Hz.")
                        }
                        preferredOutputDevice?.let { t.setPreferredDevice(it) }
                        t.play()
                    }
                }

                track = createAudioTrack(sampleRate)
                dspProcessor.resetAllStates()
                dspProcessor.updateConfig(
                    currentDspStateProvider?.invoke() ?: com.example.dsp.DspState.neutralDefault(),
                    sampleRate.toDouble()
                )

                currentState = currentState.copy(
                    isPlaying = true,
                    sampleRate = sampleRate,
                    channels = channelCount,
                    errorMessage = null
                )
                onMediaStateChanged(currentState)
                onRoutingChanged(true, null)

                val bufferInfo = MediaCodec.BufferInfo()
                var inputEos = false
                var outputEos = false
                var inL = FloatArray(4096)
                var inR = FloatArray(4096)
                var outL = FloatArray(4096)
                var outR = FloatArray(4096)
                var shortOut = ShortArray(8192)
                var lastUiPosEmit = 0L

                while (!stopRequested.get() && !outputEos) {
                    val reqSeek = pendingSeekMs.getAndSet(-1L)
                    if (reqSeek >= 0L) {
                        extractor.seekTo(reqSeek * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        codec.flush()
                        inputEos = false
                        outputEos = false
                    }

                    if (!inputEos) {
                        val inIdx = codec.dequeueInputBuffer(5000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = codec.outputFormat
                            val newSr = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            val newCh = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                            if (newSr != sampleRate) {
                                sampleRate = newSr
                                track?.stop()
                                track?.release()
                                track = createAudioTrack(sampleRate)
                                dspProcessor.updateConfig(
                                    currentDspStateProvider?.invoke() ?: com.example.dsp.DspState.neutralDefault(),
                                    sampleRate.toDouble()
                                )
                            }
                            channelCount = newCh
                            currentState = currentState.copy(sampleRate = sampleRate, channels = channelCount)
                            onMediaStateChanged(currentState)
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val totalShorts = shortBuf.remaining()
                                val frames = totalShorts / channelCount

                                if (frames > 0) {
                                    if (inL.size < frames) {
                                        inL = FloatArray(frames)
                                        inR = FloatArray(frames)
                                        outL = FloatArray(frames)
                                        outR = FloatArray(frames)
                                        shortOut = ShortArray(frames * 2)
                                    }

                                    if (channelCount >= 2) {
                                        for (i in 0 until frames) {
                                            inL[i] = shortBuf.get(i * channelCount) / 32768f
                                            inR[i] = shortBuf.get(i * channelCount + 1) / 32768f
                                        }
                                    } else {
                                        for (i in 0 until frames) {
                                            val mono = shortBuf.get(i) / 32768f
                                            inL[i] = mono
                                            inR[i] = mono
                                        }
                                    }

                                    val telemetry = dspProcessor.processBlock(
                                        inL,
                                        inR,
                                        outL,
                                        outR,
                                        frames,
                                        hwInputChannels = channelCount.coerceAtMost(2)
                                    )
                                    onTelemetry(telemetry)

                                    for (i in 0 until frames) {
                                        shortOut[i * 2] = (outL[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                        shortOut[i * 2 + 1] = (outR[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                    }
                                    track?.write(shortOut, 0, frames * 2)
                                }

                                val now = System.currentTimeMillis()
                                if (now - lastUiPosEmit >= 200L) {
                                    lastUiPosEmit = now
                                    currentState = currentState.copy(positionMs = (bufferInfo.presentationTimeUs / 1000L).coerceAtLeast(0L))
                                    onMediaStateChanged(currentState)
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                if (currentState.looping && !stopRequested.get()) {
                                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                    codec.flush()
                                    inputEos = false
                                } else {
                                    outputEos = true
                                }
                            }
                        }
                    }
                }

                if (!stopRequested.get()) {
                    currentState = currentState.copy(isPlaying = false, positionMs = 0L)
                    onMediaStateChanged(currentState)
                    onRoutingChanged(false, null)
                    onTelemetry(AudioTelemetry())
                }
            } catch (e: Exception) {
                if (!stopRequested.get()) {
                    Log.e(TAG, "Internal media decode/DSP error", e)
                    currentState = currentState.copy(
                        isPlaying = false,
                        errorMessage = e.localizedMessage ?: "Audio decode error."
                    )
                    onMediaStateChanged(currentState)
                    onRoutingChanged(false, currentState.errorMessage)
                    onTelemetry(AudioTelemetry())
                }
            } finally {
                isPlayingFlag.set(false)
                try { codec?.stop() } catch (_: Exception) {}
                try { codec?.release() } catch (_: Exception) {}
                try { extractor?.release() } catch (_: Exception) {}
                try {
                    track?.pause()
                    track?.flush()
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }, "DLMS-Media-DSP-Thread")

        workerThread = thread
        thread.start()
    }

    var currentDspStateProvider: (() -> com.example.dsp.DspState)? = null

    private fun stopInternal(resetPosition: Boolean) {
        stopRequested.set(true)
        isPlayingFlag.set(false)
        try {
            workerThread?.join(500)
        } catch (_: Exception) {
        }
        workerThread = null
        if (resetPosition) {
            pendingSeekMs.set(-1L)
        }
        dspProcessor.resetAllStates()
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Local Audio Track"
    }
}
