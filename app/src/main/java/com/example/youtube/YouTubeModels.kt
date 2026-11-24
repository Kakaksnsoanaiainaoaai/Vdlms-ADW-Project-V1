package com.example.youtube

enum class YouTubePlaybackStatus(val label: String) {
    UNSTARTED("READY"),
    PLAYING("PLAYING"),
    PAUSED("PAUSED"),
    BUFFERING("BUFFERING"),
    ENDED("ENDED"),
    ERROR("ERROR")
}

enum class YouTubeViewMode {
    DIRECT_YOUTUBE,
    IFRAME_PLAYER
}

data class YouTubeUiState(
    val viewMode: YouTubeViewMode = YouTubeViewMode.DIRECT_YOUTUBE,
    val searchQuery: String = "",
    val submittedQuery: String = "",
    val navigationNonce: Long = 0L,
    val activeVideoId: String? = null,
    val activeVideoTitle: String = "YouTube Direct",
    val playbackStatus: YouTubePlaybackStatus = YouTubePlaybackStatus.UNSTARTED,
    val currentTimeSec: Float = 0f,
    val durationSec: Float = 0f,
    val isMuted: Boolean = false,
    val volumePercent: Int = 100,
    val canGoBack: Boolean = false,
    val errorMessage: String? = null,
    val isOnline: Boolean = true
)

object YouTubeUrlParser {
    private val idRegex = Regex("^[a-zA-Z0-9_-]{11}$")
    private val watchRegex = Regex("[?&]v=([a-zA-Z0-9_-]{11})")
    private val shortRegex = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})")
    private val embedRegex = Regex("youtube\\.com/(?:embed|shorts|live)/([a-zA-Z0-9_-]{11})")

    fun extractVideoId(raw: String): String? {
        val trimmed = raw.trim()
        if (idRegex.matches(trimmed)) return trimmed
        watchRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        shortRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        embedRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }
}
