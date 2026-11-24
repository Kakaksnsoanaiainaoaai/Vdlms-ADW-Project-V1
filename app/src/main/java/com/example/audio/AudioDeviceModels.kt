package com.example.audio

enum class AudioEngineStatus {
    NOT_CONNECTED,
    PERMISSION_REQUIRED,
    STARTING,
    READY,
    ERROR
}

data class AudioDeviceOption(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isInput: Boolean,
    val channelCounts: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioDeviceOption) return false
        return id == other.id && name == other.name && typeLabel == other.typeLabel && isInput == other.isInput
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + typeLabel.hashCode()
        result = 31 * result + isInput.hashCode()
        return result
    }
}

data class AudioHardwareCapabilities(
    val inputDevices: List<AudioDeviceOption> = emptyList(),
    val outputDevices: List<AudioDeviceOption> = emptyList(),
    val supportedSampleRates: List<Int> = emptyList(),
    val nativeSampleRate: Int = 48000,
    val nativeFramesPerBuffer: Int = 256,
    val supportedBufferMultipliers: List<Int> = listOf(1, 2, 4, 8)
)

data class AudioEngineState(
    val status: AudioEngineStatus = AudioEngineStatus.NOT_CONNECTED,
    val statusLabel: String = "AUDIO: NOT CONNECTED",
    val errorMessage: String? = null,
    val isFeedbackRisk: Boolean = false,
    val activeSampleRate: Int = 0,
    val activeBufferFrames: Int = 0,
    val activeInputDeviceName: String = "None",
    val activeOutputDeviceName: String = "None",
    val hwInputChannels: Int = 0
)
