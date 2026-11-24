package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEngineState
import com.example.audio.AudioEngineStatus
import com.example.audio.AudioHardwareCapabilities
import com.example.audio.AudioRoutingStatus
import com.example.audio.AudioSourceType
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette
import java.util.Locale

@Composable
fun DisplayAudioSourceScreen(
    audioState: AudioEngineState,
    capabilities: AudioHardwareCapabilities,
    onSelectSource: (AudioSourceType) -> Unit,
    onStartDeviceAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onPickInternalMediaFile: () -> Unit,
    onToggleMediaPlayPause: () -> Unit,
    onSeekMedia: (Long) -> Unit,
    onToggleMediaLoop: () -> Unit,
    onOpenYouTubePlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val scroll = rememberScrollState()
    val isRoutingActive = audioState.routingStatus == AudioRoutingStatus.ACTIVE

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Routing Signal Chain Diagram Banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .border(
                    1.dp,
                    if (isRoutingActive) palette.meterGreen.copy(alpha = 0.7f)
                    else if (audioState.selectedSource == AudioSourceType.YOUTUBE) palette.meterRed.copy(alpha = 0.7f)
                    else palette.lcdGrid,
                    RoundedCornerShape(4.dp)
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SOURCE: ${audioState.selectedSource.displayName}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.lcdPrimary
                )
                Text(
                    text = audioState.routingStatus.label,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRoutingActive) palette.meterGreen else palette.meterRed
                )
            }
            Text(
                text = if (isRoutingActive) {
                    "SIGNAL: SOURCE → IN L / IN R → EQ → DELAY → 2-WAY XOVER → OUT L / OUT R"
                } else {
                    "SIGNAL: SOURCE → DSP [INACTIVE / STANDBY]"
                },
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = if (isRoutingActive) palette.meterGreen else palette.lcdMutedText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 1. DEVICE INPUT CARD
        SourceCard(
            title = "1. DEVICE INPUT (MIC / LINE / USB)",
            selected = audioState.selectedSource == AudioSourceType.DEVICE_INPUT,
            statusBadge = if (audioState.selectedSource == AudioSourceType.DEVICE_INPUT && isRoutingActive) {
                "ACTIVE"
            } else {
                "READY"
            },
            badgeColor = if (audioState.selectedSource == AudioSourceType.DEVICE_INPUT && isRoutingActive) {
                palette.meterGreen
            } else {
                palette.lcdPrimary
            },
            onSelect = { onSelectSource(AudioSourceType.DEVICE_INPUT) },
            testTag = "source_card_device"
        ) {
            val inputCount = capabilities.inputDevices.size
            val deviceSummary = capabilities.inputDevices.firstOrNull()?.name ?: "No input hardware detected"
            Text(
                text = "Hardware Capture ($inputCount input(s)): $deviceSummary",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = palette.lcdMutedText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (audioState.selectedSource == AudioSourceType.DEVICE_INPUT) {
                if (audioState.errorMessage != null) {
                    Text(
                        text = audioState.errorMessage,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = palette.meterRed
                    )
                }
                val isRunning = audioState.status == AudioEngineStatus.READY ||
                    audioState.status == AudioEngineStatus.STARTING
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LcdSmallActionButton(
                        text = if (isRunning) "STOP DEVICE INPUT" else "START DEVICE INPUT",
                        active = true,
                        activeColor = if (isRunning) palette.meterRed else palette.meterGreen,
                        onClick = if (isRunning) onStopAudio else onStartDeviceAudio,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("source_device_toggle_btn")
                    )
                }
            }
        }

        // 2. INTERNAL MEDIA CARD (SAF OpenDocument + MediaExtractor + MediaCodec -> DLMS DSP)
        SourceCard(
            title = "2. INTERNAL MEDIA (LOCAL AUDIO → DSP)",
            selected = audioState.selectedSource == AudioSourceType.INTERNAL_MEDIA,
            statusBadge = if (audioState.selectedSource == AudioSourceType.INTERNAL_MEDIA && audioState.mediaState.isPlaying) {
                "ROUTING → DSP"
            } else if (audioState.mediaState.hasTrack) {
                "TRACK LOADED"
            } else {
                "NO FILE"
            },
            badgeColor = if (audioState.selectedSource == AudioSourceType.INTERNAL_MEDIA && audioState.mediaState.isPlaying) {
                palette.meterGreen
            } else {
                palette.lcdSecondary
            },
            onSelect = { onSelectSource(AudioSourceType.INTERNAL_MEDIA) },
            testTag = "source_card_media"
        ) {
            val media = audioState.mediaState
            Text(
                text = "Track: ${media.trackName}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (media.hasTrack) palette.lcdText else palette.lcdMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (media.hasTrack && media.sampleRate > 0) {
                Text(
                    text = "Format: ${media.sampleRate / 1000f} kHz | ${media.channels} ch PCM → IN L / IN R",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = palette.lcdPrimary
                )
            }
            if (media.errorMessage != null) {
                Text(
                    text = media.errorMessage,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = palette.meterRed
                )
            }
            if (media.hasTrack && media.durationMs > 0L) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = media.positionMs.toFloat().coerceIn(0f, media.durationMs.toFloat()),
                        onValueChange = { onSeekMedia(it.toLong()) },
                        valueRange = 0f..media.durationMs.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.lcdPrimary,
                            activeTrackColor = palette.lcdPrimary,
                            inactiveTrackColor = palette.lcdGrid
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(media.positionMs),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            color = palette.lcdMutedText
                        )
                        Text(
                            text = formatMs(media.durationMs),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            color = palette.lcdMutedText
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LcdSmallActionButton(
                    text = "PICK AUDIO FILE",
                    active = true,
                    activeColor = palette.lcdPrimary,
                    onClick = onPickInternalMediaFile,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_internal_media_btn")
                )
                if (media.hasTrack) {
                    LcdSmallActionButton(
                        text = if (media.isPlaying) "PAUSE" else "PLAY",
                        active = media.isPlaying,
                        activeColor = if (media.isPlaying) palette.meterYellow else palette.meterGreen,
                        onClick = onToggleMediaPlayPause,
                        modifier = Modifier
                            .weight(0.75f)
                            .testTag("media_play_pause_btn")
                    )
                    LcdSmallActionButton(
                        text = if (media.looping) "LOOP:ON" else "LOOP:OFF",
                        active = media.looping,
                        activeColor = palette.lcdSecondary,
                        onClick = onToggleMediaLoop
                    )
                }
            }
        }

        // 3. YOUTUBE CARD (Strict Honest Policy Compliance)
        SourceCard(
            title = "3. YOUTUBE (OFFICIAL EMBEDDED PLAYER)",
            selected = audioState.selectedSource == AudioSourceType.YOUTUBE,
            statusBadge = "ROUTING: N/A",
            badgeColor = palette.meterRed,
            onSelect = { onSelectSource(AudioSourceType.YOUTUBE) },
            testTag = "source_card_youtube"
        ) {
            Text(
                text = "YOUTUBE AUDIO ROUTING: NOT AVAILABLE",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.meterRed
            )
            Text(
                text = "Official YouTube embedded playback/API does not expose raw PCM audio samples to internal app DSP due to YouTube Terms & DRM. Meters remain idle unless DEVICE INPUT or INTERNAL MEDIA is active.",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = palette.lcdMutedText,
                lineHeight = 12.sp
            )
            LcdSmallActionButton(
                text = "OPEN YOUTUBE PLAYER",
                active = true,
                activeColor = palette.lcdSecondary,
                onClick = onOpenYouTubePlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_youtube_from_source_btn")
            )
        }
    }
}

@Composable
private fun SourceCard(
    title: String,
    selected: Boolean,
    statusBadge: String,
    badgeColor: Color,
    onSelect: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    val palette = LocalDlmsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) palette.lcdPrimary.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.34f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.lcdPrimary else palette.lcdGrid,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onSelect)
            .padding(8.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (selected) palette.lcdPrimary else palette.lcdGrid)
                )
                Text(
                    text = title,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) palette.lcdPrimary else palette.lcdText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = statusBadge,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
        content()
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", min, sec)
}
