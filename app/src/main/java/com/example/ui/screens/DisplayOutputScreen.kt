package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.ChannelTelemetry
import com.example.dsp.CrossoverBand
import com.example.dsp.DspUnits
import com.example.dsp.EqBandConfig
import com.example.dsp.LimiterConfig
import com.example.dsp.OutputChannelConfig
import com.example.dsp.OutputSource
import com.example.hardware.EqParamField
import com.example.hardware.HardwareUiState
import com.example.hardware.OutputSubTab
import com.example.storage.MeterStyleOption
import com.example.ui.components.HorizontalVuMeter
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette
import java.util.Locale

@Composable
fun DisplayOutputScreen(
    channelLabel: String,
    config: OutputChannelConfig,
    telemetry: ChannelTelemetry,
    hwState: HardwareUiState,
    meterStyle: MeterStyleOption,
    onSelectSubTab: (OutputSubTab) -> Unit,
    onFocusParam: (Int) -> Unit,
    onUpdateConfig: ((OutputChannelConfig) -> OutputChannelConfig) -> Unit,
    onSelectEqBand: (Int) -> Unit,
    onSelectEqField: (EqParamField) -> Unit,
    onToggleEqBand: (Int) -> Unit,
    onUpdateEqBand: (Int, (EqBandConfig) -> EqBandConfig) -> Unit,
    onUpdateLimiter: ((LimiterConfig) -> LimiterConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalVuMeter(
            label = "$channelLabel OUT METER",
            telemetry = telemetry,
            meterStyle = meterStyle,
            isMuted = config.muted
        )

        LcdSubTabRow(
            tabs = OutputSubTab.entries.map { it.label },
            selectedIndex = OutputSubTab.entries.indexOf(hwState.outputSubTab),
            onSelect = { onSelectSubTab(OutputSubTab.entries[it]) }
        )

        when (hwState.outputSubTab) {
            OutputSubTab.MAIN -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutputSource.entries.forEach { src ->
                        LcdSmallActionButton(
                            text = "SRC:${src.label}",
                            active = config.source == src,
                            activeColor = palette.lcdPrimary,
                            onClick = {
                                onFocusParam(0)
                                onUpdateConfig { it.copy(source = src) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CrossoverBand.entries.forEach { b ->
                        LcdSmallActionButton(
                            text = "BAND:${b.label}",
                            active = config.band == b,
                            activeColor = palette.lcdSecondary,
                            onClick = {
                                onFocusParam(1)
                                onUpdateConfig { it.copy(band = b) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                LcdParamRow(
                    label = "OUTPUT GAIN",
                    valueText = String.format(Locale.US, "%+.1f dB", config.gainDb),
                    isFocused = hwState.focusIndex == 2,
                    isEditing = hwState.isEditingParam,
                    onClick = { onFocusParam(2) },
                    sliderValue = config.gainDb,
                    sliderRange = -24f..12f,
                    onSliderChange = { v -> onUpdateConfig { it.copy(gainDb = v) } }
                )

                val distM = DspUnits.msToMeters(config.delayMs)
                LcdParamRow(
                    label = "OUTPUT DELAY",
                    valueText = String.format(Locale.US, "%.2f ms / %.2f m", config.delayMs, distM),
                    isFocused = hwState.focusIndex == 3,
                    isEditing = hwState.isEditingParam,
                    onClick = { onFocusParam(3) },
                    sliderValue = config.delayMs,
                    sliderRange = 0f..50f,
                    onSliderChange = { v -> onUpdateConfig { it.copy(delayMs = v) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LcdSmallActionButton(
                        text = if (config.polarityInverted) "POL: -180°" else "POL: +0°",
                        active = config.polarityInverted,
                        activeColor = palette.lcdSecondary,
                        onClick = {
                            onFocusParam(4)
                            onUpdateConfig { it.copy(polarityInverted = !it.polarityInverted) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    LcdSmallActionButton(
                        text = if (config.muted) "MUTE: ON" else "MUTE: OFF",
                        active = config.muted,
                        activeColor = palette.meterRed,
                        onClick = {
                            onFocusParam(5)
                            onUpdateConfig { it.copy(muted = !it.muted) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutputSubTab.EQ -> {
                EqEditorSection(
                    eqBypass = config.eqBypass,
                    bands = config.eqBands,
                    selectedBandIndex = hwState.selectedEqBand,
                    selectedField = hwState.eqParamField,
                    isEditing = hwState.isEditingParam,
                    accentColor = palette.lcdSecondary,
                    onToggleGlobalBypass = { onUpdateConfig { it.copy(eqBypass = !it.eqBypass) } },
                    onSelectBand = onSelectEqBand,
                    onSelectField = onSelectEqField,
                    onToggleBand = onToggleEqBand,
                    onUpdateBand = onUpdateEqBand
                )
            }

            OutputSubTab.LIMITER -> {
                // Live Gain Reduction Bar & Active Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(
                            1.dp,
                            if (telemetry.limiterActive) palette.meterYellow else palette.lcdGrid,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (telemetry.limiterActive) "● LIMITER ACTIVE" else "○ LIMITER IDLE",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (telemetry.limiterActive) palette.meterYellow else palette.lcdMutedText
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(palette.lcdGrid.copy(alpha = 0.4f))
                    ) {
                        val grFrac = (telemetry.limiterGrDb / 24f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(grFrac)
                                .background(if (grFrac > 0.5f) palette.meterRed else palette.meterYellow)
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "-%.1f dB GR", telemetry.limiterGrDb),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = if (telemetry.limiterActive) palette.meterYellow else palette.lcdText
                    )
                }

                LcdParamRow(
                    label = "LIMITER ENABLE",
                    valueText = if (config.limiter.enabled) "ACTIVE" else "BYPASSED",
                    isFocused = hwState.focusIndex == 0,
                    isEditing = false,
                    accentColor = if (config.limiter.enabled) palette.meterGreen else palette.meterYellow,
                    onClick = {
                        onFocusParam(0)
                        onUpdateLimiter { it.copy(enabled = !it.enabled) }
                    }
                )
                LcdParamRow(
                    label = "THRESHOLD",
                    valueText = String.format(Locale.US, "%.1f dBFS", config.limiter.thresholdDbfs),
                    isFocused = hwState.focusIndex == 1,
                    isEditing = hwState.isEditingParam,
                    accentColor = palette.lcdSecondary,
                    onClick = { onFocusParam(1) },
                    sliderValue = config.limiter.thresholdDbfs,
                    sliderRange = -30f..0f,
                    onSliderChange = { v -> onUpdateLimiter { it.copy(thresholdDbfs = v) } }
                )
                LcdParamRow(
                    label = "ATTACK",
                    valueText = String.format(Locale.US, "%.1f ms", config.limiter.attackMs),
                    isFocused = hwState.focusIndex == 2,
                    isEditing = hwState.isEditingParam,
                    onClick = { onFocusParam(2) },
                    sliderValue = config.limiter.attackMs,
                    sliderRange = 0.5f..100f,
                    onSliderChange = { v -> onUpdateLimiter { it.copy(attackMs = v) } }
                )
                LcdParamRow(
                    label = "RELEASE",
                    valueText = "${config.limiter.releaseMs.toInt()} ms",
                    isFocused = hwState.focusIndex == 3,
                    isEditing = hwState.isEditingParam,
                    onClick = { onFocusParam(3) },
                    sliderValue = config.limiter.releaseMs,
                    sliderRange = 10f..2000f,
                    onSliderChange = { v -> onUpdateLimiter { it.copy(releaseMs = v) } }
                )
                LcdParamRow(
                    label = "MAKE-UP GAIN",
                    valueText = String.format(Locale.US, "%+.1f dB", config.limiter.makeupGainDb),
                    isFocused = hwState.focusIndex == 4,
                    isEditing = hwState.isEditingParam,
                    onClick = { onFocusParam(4) },
                    sliderValue = config.limiter.makeupGainDb,
                    sliderRange = -12f..12f,
                    onSliderChange = { v -> onUpdateLimiter { it.copy(makeupGainDb = v) } }
                )
            }
        }
    }
}
