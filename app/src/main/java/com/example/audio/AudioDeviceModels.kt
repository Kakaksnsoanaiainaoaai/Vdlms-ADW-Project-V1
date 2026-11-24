package com.example.audio

enum class AudioSourceType(val shortLabel: String, val displayName: String) {
    DEVICE_INPUT("DEVICE", "DEVICE INPUT"),
    INTERNAL_MEDIA("MEDIA", "INTERNAL MEDIA"),
    YOUTUBE("YOUTUBE", "YOUTUBE")
}

enum class AudioRoutingStatus(val label: String) {
    ACTIVE("AUDIO ROUTING: ACTIVE"),
    UNAVAILABLE("AUDIO ROUTING: UNAVAILABLE")
}

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

data class InternalMediaState(
    val hasTrack: Boolean = false,
    val trackName: String = "No audio file selected",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val looping: Boolean = true,
    val errorMessage: String? = null
)

data class AudioEngineState(
    val selectedSource: AudioSourceType = AudioSourceType.DEVICE_INPUT,
    val routingStatus: AudioRoutingStatus = AudioRoutingStatus.UNAVAILABLE,
    val status: AudioEngineStatus = AudioEngineStatus.NOT_CONNECTED,
    val statusLabel: String = "AUDIO: NOT CONNECTED",
    val youtubeRoutingNotice: String? = null,
    val errorMessage: String? = null,
    val isFeedbackRisk: Boolean = false,
    val activeSampleRate: Int = 0,
    val activeBufferFrames: Int = 0,
    val activeInputDeviceName: String = "None",
    val activeOutputDeviceName: String = "None",
    val hwInputChannels: Int = 0,
    val mediaState: InternalMediaState = InternalMediaState()
)
