package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.audio.AudioRoutingStatus
import com.example.audio.AudioSourceType
import com.example.dsp.AudioTelemetry
import com.example.dsp.ChannelDspConfig
import com.example.dsp.ChannelTelemetry
import com.example.dsp.CrossoverBand
import com.example.dsp.DspState
import com.example.hardware.DisplayPage
import com.example.storage.MeterStyleOption
import com.example.ui.components.VerticalVuMeter
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette
import java.util.Locale

@Composable
fun DisplayHomeScreen(
    dspState: DspState,
    telemetry: AudioTelemetry,
    audioState: AudioEngineState,
    meterStyle: MeterStyleOption,
    onSelectSource: (AudioSourceType) -> Unit,
    onStartAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onPickInternalMedia: () -> Unit,
    onToggleMediaPlayPause: () -> Unit,
    onOpenPage: (DisplayPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Strictly Separated Dual-Channel DSP Matrix: CHANNEL L | CHANNEL R
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IndependentChannelRackCard(
                channelName = "CHANNEL L",
                shortTag = "L",
                config = dspState.channelL,
                inTelemetry = telemetry.inputL,
                outTelemetry = telemetry.outputL,
                meterStyle = meterStyle,
                accentColor = palette.lcdPrimary,
                onClick = { onOpenPage(DisplayPage.CHANNEL_L) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("home_channel_l_card")
            )

            IndependentChannelRackCard(
                channelName = "CHANNEL R",
                shortTag = "R",
                config = dspState.channelR,
                inTelemetry = telemetry.inputR,
                outTelemetry = telemetry.outputR,
                meterStyle = meterStyle,
                accentColor = palette.lcdSecondary,
                onClick = { onOpenPage(DisplayPage.CHANNEL_R) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("home_channel_r_card")
            )
        }

        // Audio Source Selector + Real Audio Routing Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .border(1.dp, palette.lcdGrid, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SRC:",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.lcdMutedText
                )
                AudioSourceType.entries.forEach { src ->
                    val active = audioState.selectedSource == src
                    val chipColor = if (src == AudioSourceType.YOUTUBE && active) palette.meterRed else palette.lcdPrimary
                    LcdSmallActionButton(
                        text = src.shortLabel,
                        active = active,
                        activeColor = chipColor,
                        onClick = { onSelectSource(src) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_source_${src.shortLabel.lowercase()}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val routingActive = audioState.routingStatus == AudioRoutingStatus.ACTIVE
                    val infoLine = when (audioState.selectedSource) {
                        AudioSourceType.YOUTUBE ->
                            "YOUTUBE AUDIO ROUTING: NOT AVAILABLE (DRM/ToS)"
                        AudioSourceType.INTERNAL_MEDIA -> when {
                            audioState.mediaState.isPlaying ->
                                "STEREO L/R → DSP L/R: ${audioState.mediaState.trackName}"
                            audioState.mediaState.hasTrack ->
                                "PAUSED: ${audioState.mediaState.trackName}"
                            audioState.mediaState.errorMessage != null ->
                                audioState.mediaState.errorMessage
                            else -> "Pick a local audio file to route stereo L/R into DSP."
                        }
                        AudioSourceType.DEVICE_INPUT -> when (audioState.status) {
                            AudioEngineStatus.READY -> {
                                val fb = if (audioState.isFeedbackRisk) " | Use headphones" else ""
                                "LIVE L/R → DSP L/R: ${audioState.activeInputDeviceName}$fb"
                            }
                            AudioEngineStatus.NOT_CONNECTED ->
                                "Device Input idle. Tap START AUDIO to route."
                            AudioEngineStatus.PERMISSION_REQUIRED, AudioEngineStatus.ERROR ->
                                audioState.errorMessage ?: "Audio device unavailable."
                            AudioEngineStatus.STARTING -> "Initializing native audio stream..."
                        }
                    }
                    Text(
                        text = infoLine,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        fontWeight = if (audioState.selectedSource == AudioSourceType.YOUTUBE) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            audioState.selectedSource == AudioSourceType.YOUTUBE -> palette.meterRed
                            routingActive -> if (audioState.isFeedbackRisk) palette.meterYellow else palette.meterGreen
                            audioState.status == AudioEngineStatus.PERMISSION_REQUIRED ||
                                audioState.status == AudioEngineStatus.ERROR -> palette.meterRed
                            else -> palette.lcdMutedText
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                when (audioState.selectedSource) {
                    AudioSourceType.DEVICE_INPUT -> {
                        val running = audioState.status == AudioEngineStatus.READY ||
                            audioState.status == AudioEngineStatus.STARTING
                        LcdSmallActionButton(
                            text = if (running) "STOP" else "START AUDIO",
                            active = running,
                            activeColor = if (running) palette.meterRed else palette.meterGreen,
                            onClick = if (running) onStopAudio else onStartAudio,
                            modifier = Modifier.testTag("audio_engine_toggle_btn")
                        )
                    }
                    AudioSourceType.INTERNAL_MEDIA -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            LcdSmallActionButton(
                                text = "FILE",
                                active = !audioState.mediaState.hasTrack,
                                activeColor = palette.lcdPrimary,
                                onClick = onPickInternalMedia,
                                modifier = Modifier.testTag("home_pick_media_btn")
                            )
                            if (audioState.mediaState.hasTrack) {
                                val playing = audioState.mediaState.isPlaying
                                LcdSmallActionButton(
                                    text = if (playing) "PAUSE" else "PLAY",
                                    active = playing,
                                    activeColor = if (playing) palette.meterYellow else palette.meterGreen,
                                    onClick = onToggleMediaPlayPause,
                                    modifier = Modifier.testTag("home_media_play_btn")
                                )
                            }
                        }
                    }
                    AudioSourceType.YOUTUBE -> {
                        LcdSmallActionButton(
                            text = "OPEN YT",
                            active = true,
                            activeColor = palette.lcdSecondary,
                            onClick = { onOpenPage(DisplayPage.MEDIA_YOUTUBE) },
                            modifier = Modifier.testTag("home_open_yt_btn")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndependentChannelRackCard(
    channelName: String,
    shortTag: String,
    config: ChannelDspConfig,
    inTelemetry: ChannelTelemetry,
    outTelemetry: ChannelTelemetry,
    meterStyle: MeterStyleOption,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val xoBandCfg = when (config.activeBand) {
        CrossoverBand.LOW -> config.crossover.low
        CrossoverBand.HIGH -> config.crossover.high
        CrossoverBand.FULL -> config.crossover.low
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.38f))
            .border(
                width = 1.dp,
                color = if (config.muted) palette.meterRed.copy(alpha = 0.7f) else accentColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Channel Header + Mute Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channelName,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = if (config.muted) "MUTE $shortTag" else config.activeBand.label,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (config.muted) palette.meterRed else palette.meterGreen
            )
        }

        // Separated IN & OUT Vertical VU Meters for this channel
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.lcdGrid.copy(alpha = 0.25f))
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                VerticalVuMeter(
                    label = "IN $shortTag",
                    telemetry = inTelemetry,
                    meterStyle = meterStyle,
                    isMuted = false,
                    modifier = Modifier.fillMaxHeight()
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.lcdGrid.copy(alpha = 0.25f))
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                VerticalVuMeter(
                    label = "OUT $shortTag",
                    telemetry = outTelemetry,
                    meterStyle = meterStyle,
                    isMuted = config.muted,
                    showLimiterGr = true,
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }

        // Independent DSP Chain Summary for this channel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(palette.lcdGrid.copy(alpha = 0.22f))
                .padding(horizontal = 5.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "IN:%+.1fdB OUT:%+.1fdB DLY:%.1fms",
                    config.inputGainDb,
                    config.outputGainDb,
                    config.delayMs
                ),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 8.sp,
                color = palette.lcdText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val xoText = if (config.crossover.bypass || config.activeBand == CrossoverBand.FULL) {
                "XOVER $shortTag: BYP/FULL"
            } else {
                "XOVER $shortTag: ${xoBandCfg.frequencyHz.toInt()}Hz ${xoBandCfg.filterType.shortLabel}${xoBandCfg.slope.dbPerOct}"
            }
            Text(
                text = "$xoText | EQ:${if (config.eqBypass) "BYP" else "ON"} LIM:${if (config.limiter.enabled) "${config.limiter.thresholdDbfs.toInt()}dB" else "BYP"}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 8.sp,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
