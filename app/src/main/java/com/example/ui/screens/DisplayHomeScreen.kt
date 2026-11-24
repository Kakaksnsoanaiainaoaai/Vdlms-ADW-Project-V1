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
import androidx.compose.material3.MaterialTheme
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
        // Real-time I/O Metering Matrix (2 IN + XOVER + 2 OUT)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // INPUTS BLOCK
            ChannelMeterGroup(
                title = "INPUTS (L / R)",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ChannelStripColumn(
                    name = "IN L",
                    gainDb = dspState.inputL.gainDb,
                    muted = dspState.inputL.muted,
                    polInverted = dspState.inputL.polarityInverted,
                    delayMs = dspState.inputL.delayMs,
                    onClick = { onOpenPage(DisplayPage.INPUT_L) },
                    modifier = Modifier.weight(1f)
                ) {
                    VerticalVuMeter(
                        label = "L",
                        telemetry = telemetry.inputL,
                        meterStyle = meterStyle,
                        isMuted = dspState.inputL.muted,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
                ChannelStripColumn(
                    name = "IN R",
                    gainDb = dspState.inputR.gainDb,
                    muted = dspState.inputR.muted,
                    polInverted = dspState.inputR.polarityInverted,
                    delayMs = dspState.inputR.delayMs,
                    onClick = { onOpenPage(DisplayPage.INPUT_R) },
                    modifier = Modifier.weight(1f)
                ) {
                    VerticalVuMeter(
                        label = "R",
                        telemetry = telemetry.inputR,
                        meterStyle = meterStyle,
                        isMuted = dspState.inputR.muted,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }

            // CENTER CROSSOVER SUMMARY
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, palette.lcdGrid, RoundedCornerShape(4.dp))
                    .clickable { onOpenPage(DisplayPage.CROSSOVER) }
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "2-WAY XOVER",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.lcdPrimary
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LOW LPF",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        color = palette.lcdMutedText
                    )
                    Text(
                        text = "${dspState.crossover.low.frequencyHz.toInt()} Hz",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.lcdPrimary
                    )
                    Text(
                        text = "${dspState.crossover.low.filterType.shortLabel} ${dspState.crossover.low.slope.dbPerOct}dB",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        color = palette.lcdText
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "HIGH HPF",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        color = palette.lcdMutedText
                    )
                    Text(
                        text = "${dspState.crossover.high.frequencyHz.toInt()} Hz",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.lcdSecondary
                    )
                    Text(
                        text = "${dspState.crossover.high.filterType.shortLabel} ${dspState.crossover.high.slope.dbPerOct}dB",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        color = palette.lcdText
                    )
                }
                Text(
                    text = if (dspState.crossover.bypass) "BYPASSED" else "ACTIVE",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dspState.crossover.bypass) palette.meterYellow else palette.meterGreen
                )
            }

            // OUTPUTS BLOCK
            ChannelMeterGroup(
                title = "OUTPUTS (L / R)",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ChannelStripColumn(
                    name = "OUT L",
                    gainDb = dspState.outputL.gainDb,
                    muted = dspState.outputL.muted,
                    polInverted = dspState.outputL.polarityInverted,
                    delayMs = dspState.outputL.delayMs,
                    badge = dspState.outputL.band.label,
                    onClick = { onOpenPage(DisplayPage.OUTPUT_L) },
                    modifier = Modifier.weight(1f)
                ) {
                    VerticalVuMeter(
                        label = "L",
                        telemetry = telemetry.outputL,
                        meterStyle = meterStyle,
                        isMuted = dspState.outputL.muted,
                        showLimiterGr = true,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
                ChannelStripColumn(
                    name = "OUT R",
                    gainDb = dspState.outputR.gainDb,
                    muted = dspState.outputR.muted,
                    polInverted = dspState.outputR.polarityInverted,
                    delayMs = dspState.outputR.delayMs,
                    badge = dspState.outputR.band.label,
                    onClick = { onOpenPage(DisplayPage.OUTPUT_R) },
                    modifier = Modifier.weight(1f)
                ) {
                    VerticalVuMeter(
                        label = "R",
                        telemetry = telemetry.outputR,
                        meterStyle = meterStyle,
                        isMuted = dspState.outputR.muted,
                        showLimiterGr = true,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
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
                                "SOURCE → DSP: ${audioState.mediaState.trackName}"
                            audioState.mediaState.hasTrack ->
                                "PAUSED: ${audioState.mediaState.trackName}"
                            audioState.mediaState.errorMessage != null ->
                                audioState.mediaState.errorMessage
                            else -> "Pick a local audio file to route into DSP."
                        }
                        AudioSourceType.DEVICE_INPUT -> when (audioState.status) {
                            AudioEngineStatus.READY -> {
                                val fb = if (audioState.isFeedbackRisk) " | Use headphones" else ""
                                "SOURCE → DSP: ${audioState.activeInputDeviceName}$fb"
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
private fun ChannelMeterGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalDlmsPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, palette.lcdGrid, RoundedCornerShape(4.dp))
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = palette.lcdMutedText
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ChannelStripColumn(
    name: String,
    gainDb: Float,
    muted: Boolean,
    polInverted: Boolean,
    delayMs: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    meterContent: @Composable () -> Unit
) {
    val palette = LocalDlmsPalette.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(palette.lcdGrid.copy(alpha = 0.28f))
            .clickable(onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (muted) palette.meterRed else palette.lcdText
        )
        if (badge != null) {
            Text(
                text = badge,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 8.sp,
                color = palette.lcdSecondary
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            meterContent()
        }
        Text(
            text = String.format(Locale.US, "%+.1fdB", gainDb),
            fontFamily = JetBrainsMonoFamily,
            fontSize = 8.sp,
            color = palette.lcdPrimary,
            maxLines = 1
        )
        Text(
            text = "${if (polInverted) "Ø-" else "Ø+"} ${String.format(Locale.US, "%.1fms", delayMs)}",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 7.sp,
            color = palette.lcdMutedText,
            maxLines = 1
        )
    }
}
