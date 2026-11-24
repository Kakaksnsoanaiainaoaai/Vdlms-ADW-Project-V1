package com.example.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack

class AudioDeviceEnumerator(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun queryCapabilities(): AudioHardwareCapabilities {
        val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.isSource }
            .map { info ->
                AudioDeviceOption(
                    id = info.id,
                    name = formatDeviceName(info),
                    typeLabel = deviceTypeLabel(info.type),
                    isInput = true,
                    channelCounts = info.channelCounts
                )
            }

        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .map { info ->
                AudioDeviceOption(
                    id = info.id,
                    name = formatDeviceName(info),
                    typeLabel = deviceTypeLabel(info.type),
                    isInput = false,
                    channelCounts = info.channelCounts
                )
            }

        val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)

        val nativeFrames = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: 256

        val candidateRates = listOf(44100, 48000, 96000, nativeRate).distinct().sorted()
        val supportedRates = candidateRates.filter { rate ->
            val recMin = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trkMin = AudioTrack.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            recMin > 0 && trkMin > 0
        }.ifEmpty { listOf(nativeRate) }

        return AudioHardwareCapabilities(
            inputDevices = inputDevices,
            outputDevices = outputDevices,
            supportedSampleRates = supportedRates,
            nativeSampleRate = nativeRate,
            nativeFramesPerBuffer = nativeFrames,
            supportedBufferMultipliers = listOf(1, 2, 4, 8)
        )
    }

    fun findInputDeviceInfo(id: Int?): AudioDeviceInfo? {
        if (id == null) return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }
    }

    fun findOutputDeviceInfo(id: Int?): AudioDeviceInfo? {
        if (id == null) return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
    }

    fun isHeadsetOrExternalOutput(outputId: Int?): Boolean {
        val info = findOutputDeviceInfo(outputId)
        val type = info?.type ?: run {
            val activeOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return activeOutputs.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        }
        return type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    }

    private fun formatDeviceName(info: AudioDeviceInfo): String {
        val label = deviceTypeLabel(info.type)
        val product = info.productName?.toString()?.trim().orEmpty()
        return if (product.isNotBlank() && !product.equals("Android", ignoreCase = true)) {
            "$label ($product)"
        } else {
            "$label #${info.id}"
        }
    }

    private fun deviceTypeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Analog"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line Digital"
        else -> "Audio Device ($type)"
    }
}
