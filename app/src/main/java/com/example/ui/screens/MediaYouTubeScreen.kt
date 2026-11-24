package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette
import com.example.youtube.YouTubeEmbeddedWebView
import com.example.youtube.YouTubePlaybackStatus
import com.example.youtube.YouTubePlayerController
import com.example.youtube.YouTubeUiState
import com.example.youtube.YouTubeViewMode
import java.util.Locale

@Composable
fun MediaYouTubeScreen(
    ytState: YouTubeUiState,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onVideoDetected: (videoId: String) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onSetViewMode: (YouTubeViewMode) -> Unit,
    onGoYouTubeHome: () -> Unit,
    onPlaybackStatus: (YouTubePlaybackStatus) -> Unit,
    onProgress: (Float, Float) -> Unit,
    onError: (String) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onSetVolume: (Int) -> Unit,
    onBackToDlms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val playerController = remember { YouTubePlayerController() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Top Direct YouTube Search + Navigation + Back to DLMS Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, palette.lcdGrid, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (ytState.searchQuery.isEmpty()) {
                    Text(
                        text = "Cari di YouTube atau tempel URL / Video ID...",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = palette.lcdMutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = ytState.searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = palette.lcdText
                    ),
                    cursorBrush = SolidColor(palette.lcdPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitQuery(ytState.searchQuery) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_search_input")
                )
            }
            LcdSmallActionButton(
                text = "SEARCH",
                active = true,
                onClick = { onSubmitQuery(ytState.searchQuery) },
                modifier = Modifier.testTag("youtube_search_btn")
            )
            LcdSmallActionButton(
                text = "YT HOME",
                onClick = {
                    onGoYouTubeHome()
                    playerController.goHome()
                }
            )
            LcdSmallActionButton(
                text = "BACK TO DLMS",
                active = true,
                activeColor = palette.lcdSecondary,
                onClick = onBackToDlms,
                modifier = Modifier.testTag("youtube_back_dlms_btn")
            )
        }

        // Honest Audio Routing & Compliance Notice
        Text(
            text = stringResource(R.string.youtube_routing_notice),
            fontFamily = JetBrainsMonoFamily,
            fontSize = 8.sp,
            color = palette.meterYellow,
            lineHeight = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (ytState.errorMessage != null) {
            Text(
                text = ytState.errorMessage,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = palette.meterRed
            )
        }

        // Direct YouTube Embedded View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, palette.lcdGrid, RoundedCornerShape(4.dp))
        ) {
            YouTubeEmbeddedWebView(
                viewMode = ytState.viewMode,
                activeVideoId = ytState.activeVideoId,
                submittedQuery = ytState.submittedQuery,
                navigationNonce = ytState.navigationNonce,
                controller = playerController,
                onVideoDetected = onVideoDetected,
                onCanGoBackChanged = onCanGoBackChanged,
                onStateChanged = onPlaybackStatus,
                onProgress = onProgress,
                onError = onError,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Playback & Browser Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, palette.lcdGrid, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (ytState.canGoBack && ytState.viewMode == YouTubeViewMode.DIRECT_YOUTUBE) {
                LcdSmallActionButton(
                    text = "YT ‹",
                    onClick = { playerController.goBack() }
                )
            }
            LcdSmallActionButton(
                text = if (ytState.viewMode == YouTubeViewMode.DIRECT_YOUTUBE) "IFRAME" else "YT WEB",
                active = ytState.viewMode == YouTubeViewMode.IFRAME_PLAYER,
                onClick = {
                    val next = if (ytState.viewMode == YouTubeViewMode.DIRECT_YOUTUBE) {
                        YouTubeViewMode.IFRAME_PLAYER
                    } else {
                        YouTubeViewMode.DIRECT_YOUTUBE
                    }
                    onSetViewMode(next)
                }
            )
            LcdSmallActionButton(
                text = "PLAY",
                active = ytState.playbackStatus == YouTubePlaybackStatus.PLAYING,
                activeColor = palette.meterGreen,
                onClick = { playerController.play() }
            )
            LcdSmallActionButton(
                text = "PAUSE",
                active = ytState.playbackStatus == YouTubePlaybackStatus.PAUSED,
                onClick = { playerController.pause() }
            )
            LcdSmallActionButton(
                text = "-10s",
                onClick = { playerController.seekBy(-10f) }
            )
            LcdSmallActionButton(
                text = "+10s",
                onClick = { playerController.seekBy(10f) }
            )
            LcdSmallActionButton(
                text = if (ytState.isMuted) "UNMUTE" else "MUTE",
                active = ytState.isMuted,
                activeColor = palette.meterRed,
                onClick = {
                    val next = !ytState.isMuted
                    onToggleMute(next)
                    playerController.setMuted(next)
                }
            )
            if (ytState.viewMode == YouTubeViewMode.IFRAME_PLAYER && ytState.durationSec > 0f) {
                Text(
                    text = formatTime(ytState.currentTimeSec) + "/" + formatTime(ytState.durationSec),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = palette.lcdMutedText
                )
            }
            Slider(
                value = ytState.volumePercent.toFloat(),
                onValueChange = {
                    val v = it.toInt()
                    onSetVolume(v)
                    playerController.setVolume(v)
                },
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = palette.lcdPrimary,
                    activeTrackColor = palette.lcdPrimary,
                    inactiveTrackColor = palette.lcdGrid
                )
            )
        }
    }
}

private fun formatTime(sec: Float): String {
    val total = sec.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}
