package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEngineState
import com.example.audio.AudioRoutingStatus
import com.example.audio.AudioSourceType
import com.example.dsp.BiquadFilter
import com.example.dsp.ChannelDspConfig
import com.example.dsp.ChannelTelemetry
import com.example.dsp.CrossoverBand
import com.example.dsp.CrossoverBandConfig
import com.example.dsp.CrossoverFilterType
import com.example.dsp.CrossoverSlope
import com.example.dsp.DspUnits
import com.example.dsp.EqBandConfig
import com.example.dsp.EqFilterType
import com.example.dsp.LimiterConfig
import com.example.hardware.ChannelDspSubTab
import com.example.hardware.EqParamField
import com.example.hardware.HardwareUiState
import com.example.storage.MeterStyleOption
import com.example.ui.components.CrossoverFrequencyGraph
import com.example.ui.components.HorizontalVuMeter
import com.example.ui.components.ParametricEqGraph
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun DisplayChannelDspScreen(
    isLeftChannel: Boolean,
    config: ChannelDspConfig,
    inputTelemetry: ChannelTelemetry,
    outputTelemetry: ChannelTelemetry,
    audioState: AudioEngineState,
    hwState: HardwareUiState,
    meterStyle: MeterStyleOption,
    onSwitchChannel: (isLeft: Boolean) -> Unit,
    onOpenAudioSource: () -> Unit,
    onSelectSubTab: (ChannelDspSubTab) -> Unit,
    onSelectCrossoverBandTab: (Int) -> Unit,
    onFocusParam: (Int) -> Unit,
    onUpdateChannel: ((ChannelDspConfig) -> ChannelDspConfig) -> Unit,
    onSelectEqBand: (Int) -> Unit,
    onSelectEqField: (EqParamField) -> Unit,
    onToggleEqBand: (Int) -> Unit,
    onUpdateEqBand: (Int, (EqBandConfig) -> EqBandConfig) -> Unit,
    onSelectActiveBand: (CrossoverBand) -> Unit,
    onToggleCrossoverBypass: (Boolean) -> Unit,
    onUpdateCrossoverBand: (isLow: Boolean, (CrossoverBandConfig) -> CrossoverBandConfig) -> Unit,
    onUpdateLimiter: ((LimiterConfig) -> LimiterConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val scroll = rememberScrollState()
    val chShort = if (isLeftChannel) "L" else "R"
    val chAccent = if (isLeftChannel) palette.lcdPrimary else palette.lcdSecondary
    val isRoutingActive = audioState.routingStatus == AudioRoutingStatus.ACTIVE

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Channel Identity + Switcher + Source Status Banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, chAccent.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LcdSmallActionButton(
                        text = "CH L",
                        active = isLeftChannel,
                        activeColor = palette.lcdPrimary,
                        onClick = { onSwitchChannel(true) },
                        modifier = Modifier.testTag("dsp_switch_ch_l")
                    )
                    LcdSmallActionButton(
                        text = "CH R",
                        active = !isLeftChannel,
                        activeColor = palette.lcdSecondary,
                        onClick = { onSwitchChannel(false) },
                        modifier = Modifier.testTag("dsp_switch_ch_r")
                    )
                }
                Text(
                    text = "IN $chShort → EQ $chShort → DLY $chShort → XOVER $chShort → LIM $chShort → OUT $chShort",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 8.sp,
                    color = chAccent,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAudioSource),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SOURCE: ${audioState.selectedSource.displayName}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.lcdText
                )
                Text(
                    text = when {
                        audioState.selectedSource == AudioSourceType.YOUTUBE -> "YOUTUBE AUDIO ROUTING: NOT AVAILABLE"
                        isRoutingActive -> "SOURCE → DSP $chShort [ACTIVE]"
                        else -> "SOURCE → DSP $chShort [UNAVAILABLE]"
                    },
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRoutingActive) palette.meterGreen else palette.meterRed
                )
            }
        }

        // Dedicated Channel Input & Output VU Meters (Strictly isolated to this channel)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, palette.lcdGrid, RoundedCornerShape(4.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HorizontalVuMeter(
                label = "INPUT $chShort",
                telemetry = inputTelemetry,
                meterStyle = meterStyle,
                isMuted = false
            )
            HorizontalVuMeter(
                label = "OUTPUT $chShort",
                telemetry = outputTelemetry,
                meterStyle = meterStyle,
                isMuted = config.muted
            )
        }

        LcdSubTabRow(
            tabs = ChannelDspSubTab.entries.map { it.label },
            selectedIndex = ChannelDspSubTab.entries.indexOf(hwState.channelSubTab),
            onSelect = { onSelectSubTab(ChannelDspSubTab.entries[it]) }
        )

        when (hwState.channelSubTab) {
            ChannelDspSubTab.MAIN -> {
                LcdParamRow(
                    label = "INPUT GAIN $chShort",
                    valueText = String.format(Locale.US, "%+.1f dB", config.inputGainDb),
                    isFocused = hwState.focusIndex == 0,
                    isEditing = hwState.isEditingParam,
                    accentColor = chAccent,
                    onClick = { onFocusParam(0) },
                    sliderValue = config.inputGainDb,
                    sliderRange = -24f..12f,
                    onSliderChange = { v -> onUpdateChannel { it.copy(inputGainDb = v) } }
                )
                LcdParamRow(
                    label = "POLARITY $chShort",
                    valueText = if (config.inputPolarityInverted) "INVERTED (180°)" else "NORMAL (0°)",
                    isFocused = hwState.focusIndex == 1,
                    isEditing = false,
                    accentColor = if (config.inputPolarityInverted) palette.lcdSecondary else chAccent,
                    onClick = {
                        onFocusParam(1)
                        onUpdateChannel { it.copy(inputPolarityInverted = !it.inputPolarityInverted) }
                    }
                )
                val distM = DspUnits.msToMeters(config.delayMs)
                LcdParamRow(
                    label = "DELAY $chShort",
                    valueText = String.format(Locale.US, "%.2f ms / %.2f m", config.delayMs, distM),
                    isFocused = hwState.focusIndex == 2,
                    isEditing = hwState.isEditingParam,
                    accentColor = chAccent,
                    onClick = { onFocusParam(2) },
                    sliderValue = config.delayMs,
                    sliderRange = 0f..50f,
                    onSliderChange = { v -> onUpdateChannel { it.copy(delayMs = v) } }
                )
                LcdParamRow(
                    label = "OUTPUT GAIN $chShort",
                    valueText = String.format(Locale.US, "%+.1f dB", config.outputGainDb),
                    isFocused = hwState.focusIndex == 3,
                    isEditing = hwState.isEditingParam,
                    accentColor = chAccent,
                    onClick = { onFocusParam(3) },
                    sliderValue = config.outputGainDb,
                    sliderRange = -24f..12f,
                    onSliderChange = { v -> onUpdateChannel { it.copy(outputGainDb = v) } }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LcdSmallActionButton(
                        text = if (config.outputPolarityInverted) "OUT POL $chShort: -180°" else "OUT POL $chShort: +0°",
                        active = config.outputPolarityInverted,
                        activeColor = palette.lcdSecondary,
                        onClick = {
                            onUpdateChannel { it.copy(outputPolarityInverted = !it.outputPolarityInverted) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    LcdSmallActionButton(
                        text = if (config.muted) "MUTE $chShort: ON" else "MUTE $chShort: OFF",
                        active = config.muted,
                        activeColor = palette.meterRed,
                        onClick = {
                            onFocusParam(4)
                            onUpdateChannel { it.copy(muted = !it.muted) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("channel_${chShort.lowercase()}_mute_btn")
                    )
                }
            }

            ChannelDspSubTab.EQ -> {
                EqEditorSection(
                    titleLabel = "EQ $chShort",
                    eqBypass = config.eqBypass,
                    bands = config.eqBands,
                    selectedBandIndex = hwState.selectedEqBand,
                    selectedField = hwState.eqParamField,
                    isEditing = hwState.isEditingParam,
                    accentColor = chAccent,
                    onToggleGlobalBypass = { onUpdateChannel { it.copy(eqBypass = !it.eqBypass) } },
                    onSelectBand = onSelectEqBand,
                    onSelectField = onSelectEqField,
                    onToggleBand = onToggleEqBand,
                    onUpdateBand = onUpdateEqBand
                )
            }

            ChannelDspSubTab.XOVER -> {
                ChannelCrossoverSection(
                    channelTag = chShort,
                    crossover = config.crossover,
                    activeBandAssignment = config.activeBand,
                    selectedBandTab = hwState.crossoverBandTab,
                    focusIndex = hwState.focusIndex,
                    isEditing = hwState.isEditingParam,
                    onSelectBandAssignment = onSelectActiveBand,
                    onSelectBandTab = onSelectCrossoverBandTab,
                    onFocusParam = onFocusParam,
                    onToggleGlobalBypass = onToggleCrossoverBypass,
                    onUpdateBand = onUpdateCrossoverBand
                )
            }

            ChannelDspSubTab.LIMITER -> {
                ChannelLimiterSection(
                    channelTag = chShort,
                    limiter = config.limiter,
                    telemetry = outputTelemetry,
                    focusIndex = hwState.focusIndex,
                    isEditing = hwState.isEditingParam,
                    accentColor = chAccent,
                    onFocusParam = onFocusParam,
                    onUpdateLimiter = onUpdateLimiter
                )
            }
        }
    }
}

@Composable
fun ChannelCrossoverSection(
    channelTag: String,
    crossover: com.example.dsp.CrossoverConfig,
    activeBandAssignment: CrossoverBand,
    selectedBandTab: Int,
    focusIndex: Int,
    isEditing: Boolean,
    onSelectBandAssignment: (CrossoverBand) -> Unit,
    onSelectBandTab: (Int) -> Unit,
    onFocusParam: (Int) -> Unit,
    onToggleGlobalBypass: (Boolean) -> Unit,
    onUpdateBand: (isLow: Boolean, (CrossoverBandConfig) -> CrossoverBandConfig) -> Unit
) {
    val palette = LocalDlmsPalette.current
    val isLow = selectedBandTab == 0
    val activeBand = if (isLow) crossover.low else crossover.high

    CrossoverFrequencyGraph(
        crossover = crossover,
        selectedBand = selectedBandTab,
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "OUT $channelTag:",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = palette.lcdMutedText
        )
        CrossoverBand.entries.forEach { b ->
            LcdSmallActionButton(
                text = b.label,
                active = activeBandAssignment == b,
                activeColor = palette.lcdSecondary,
                onClick = { onSelectBandAssignment(b) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LcdSmallActionButton(
            text = "LOW (LPF)",
            active = isLow,
            activeColor = palette.lcdPrimary,
            onClick = { onSelectBandTab(0) },
            modifier = Modifier.weight(1f)
        )
        LcdSmallActionButton(
            text = "HIGH (HPF)",
            active = !isLow,
            activeColor = palette.lcdSecondary,
            onClick = { onSelectBandTab(1) },
            modifier = Modifier.weight(1f)
        )
        LcdSmallActionButton(
            text = if (crossover.bypass) "XOVER $channelTag: BYP" else "XOVER $channelTag: ON",
            active = !crossover.bypass,
            activeColor = if (crossover.bypass) palette.meterYellow else palette.meterGreen,
            onClick = { onToggleGlobalBypass(!crossover.bypass) }
        )
    }

    LcdParamRow(
        label = if (isLow) "XOVER $channelTag LOW FREQ" else "XOVER $channelTag HIGH FREQ",
        valueText = "${activeBand.frequencyHz.toInt()} Hz",
        isFocused = focusIndex == 0,
        isEditing = isEditing,
        accentColor = if (isLow) palette.lcdPrimary else palette.lcdSecondary,
        onClick = { onFocusParam(0) },
        sliderValue = log10(activeBand.frequencyHz.coerceIn(20f, 20000f)),
        sliderRange = log10(20f)..log10(20000f),
        onSliderChange = { logVal ->
            val hz = 10.0.pow(logVal.toDouble()).toFloat()
            onUpdateBand(isLow) { it.copy(frequencyHz = hz) }
        }
    )

    LcdParamRow(
        label = "XOVER $channelTag FILTER TYPE",
        valueText = activeBand.filterType.label,
        isFocused = focusIndex == 1,
        isEditing = false,
        accentColor = if (isLow) palette.lcdPrimary else palette.lcdSecondary,
        onClick = {
            onFocusParam(1)
            val types = CrossoverFilterType.entries
            val next = types[(types.indexOf(activeBand.filterType) + 1) % types.size]
            onUpdateBand(isLow) { it.copy(filterType = next) }
        }
    )

    val allowedSlopes = if (activeBand.filterType == CrossoverFilterType.LINKWITZ_RILEY) {
        listOf(CrossoverSlope.DB_12, CrossoverSlope.DB_24, CrossoverSlope.DB_48)
    } else {
        CrossoverSlope.entries
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        allowedSlopes.forEach { slopeOption ->
            LcdSmallActionButton(
                text = "${slopeOption.dbPerOct} dB",
                active = activeBand.slope == slopeOption,
                activeColor = if (isLow) palette.lcdPrimary else palette.lcdSecondary,
                onClick = {
                    onFocusParam(2)
                    onUpdateBand(isLow) { it.copy(slope = slopeOption) }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    LcdParamRow(
        label = if (isLow) "XOVER $channelTag LOW BYPASS" else "XOVER $channelTag HIGH BYPASS",
        valueText = if (activeBand.bypass) "BYPASSED" else "ACTIVE",
        isFocused = focusIndex == 3,
        isEditing = false,
        accentColor = if (activeBand.bypass) palette.meterYellow else palette.meterGreen,
        onClick = {
            onFocusParam(3)
            onUpdateBand(isLow) { it.copy(bypass = !it.bypass) }
        }
    )
}

@Composable
fun ChannelLimiterSection(
    channelTag: String,
    limiter: LimiterConfig,
    telemetry: ChannelTelemetry,
    focusIndex: Int,
    isEditing: Boolean,
    accentColor: Color,
    onFocusParam: (Int) -> Unit,
    onUpdateLimiter: ((LimiterConfig) -> LimiterConfig) -> Unit
) {
    val palette = LocalDlmsPalette.current

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
            text = if (telemetry.limiterActive) "● LIM $channelTag ACTIVE" else "○ LIM $channelTag IDLE",
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
        label = "LIMITER $channelTag",
        valueText = if (limiter.enabled) "ACTIVE" else "BYPASSED",
        isFocused = focusIndex == 0,
        isEditing = false,
        accentColor = if (limiter.enabled) palette.meterGreen else palette.meterYellow,
        onClick = {
            onFocusParam(0)
            onUpdateLimiter { it.copy(enabled = !it.enabled) }
        }
    )
    LcdParamRow(
        label = "THRESHOLD $channelTag",
        valueText = String.format(Locale.US, "%.1f dBFS", limiter.thresholdDbfs),
        isFocused = focusIndex == 1,
        isEditing = isEditing,
        accentColor = accentColor,
        onClick = { onFocusParam(1) },
        sliderValue = limiter.thresholdDbfs,
        sliderRange = -30f..0f,
        onSliderChange = { v -> onUpdateLimiter { it.copy(thresholdDbfs = v) } }
    )
    LcdParamRow(
        label = "ATTACK $channelTag",
        valueText = String.format(Locale.US, "%.1f ms", limiter.attackMs),
        isFocused = focusIndex == 2,
        isEditing = isEditing,
        accentColor = accentColor,
        onClick = { onFocusParam(2) },
        sliderValue = limiter.attackMs,
        sliderRange = 0.5f..100f,
        onSliderChange = { v -> onUpdateLimiter { it.copy(attackMs = v) } }
    )
    LcdParamRow(
        label = "RELEASE $channelTag",
        valueText = "${limiter.releaseMs.toInt()} ms",
        isFocused = focusIndex == 3,
        isEditing = isEditing,
        accentColor = accentColor,
        onClick = { onFocusParam(3) },
        sliderValue = limiter.releaseMs,
        sliderRange = 10f..2000f,
        onSliderChange = { v -> onUpdateLimiter { it.copy(releaseMs = v) } }
    )
    LcdParamRow(
        label = "MAKE-UP GAIN $channelTag",
        valueText = String.format(Locale.US, "%+.1f dB", limiter.makeupGainDb),
        isFocused = focusIndex == 4,
        isEditing = isEditing,
        accentColor = accentColor,
        onClick = { onFocusParam(4) },
        sliderValue = limiter.makeupGainDb,
        sliderRange = -12f..12f,
        onSliderChange = { v -> onUpdateLimiter { it.copy(makeupGainDb = v) } }
    )
}

@Composable
fun EqEditorSection(
    titleLabel: String,
    eqBypass: Boolean,
    bands: List<EqBandConfig>,
    selectedBandIndex: Int,
    selectedField: EqParamField,
    isEditing: Boolean,
    accentColor: Color,
    onToggleGlobalBypass: () -> Unit,
    onSelectBand: (Int) -> Unit,
    onSelectField: (EqParamField) -> Unit,
    onToggleBand: (Int) -> Unit,
    onUpdateBand: (Int, (EqBandConfig) -> EqBandConfig) -> Unit
) {
    val palette = LocalDlmsPalette.current
    val safeIndex = selectedBandIndex.coerceIn(0, bands.lastIndex)
    val band = bands[safeIndex]

    ParametricEqGraph(
        bands = bands,
        eqBypass = eqBypass,
        selectedBandIndex = safeIndex,
        accentColor = accentColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        bands.forEachIndexed { idx, b ->
            LcdSmallActionButton(
                text = "B${idx + 1}",
                active = idx == safeIndex,
                activeColor = if (b.enabled) accentColor else palette.lcdMutedText,
                onClick = { onSelectBand(idx) },
                modifier = Modifier.weight(1f)
            )
        }
        LcdSmallActionButton(
            text = if (eqBypass) "$titleLabel:BYP" else "$titleLabel:ON",
            active = !eqBypass,
            activeColor = if (eqBypass) palette.meterYellow else palette.meterGreen,
            onClick = onToggleGlobalBypass
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LcdSmallActionButton(
            text = if (band.enabled) "BAND: ON" else "BAND: OFF",
            active = band.enabled,
            activeColor = if (band.enabled) palette.meterGreen else palette.meterRed,
            onClick = { onToggleBand(safeIndex) },
            modifier = Modifier.weight(1f)
        )
        LcdSmallActionButton(
            text = "TYPE: ${band.filterType.shortLabel}",
            active = selectedField == EqParamField.TYPE,
            onClick = {
                onSelectField(EqParamField.TYPE)
                val types = EqFilterType.entries
                val next = types[(types.indexOf(band.filterType) + 1) % types.size]
                onUpdateBand(safeIndex) { it.copy(filterType = next) }
            },
            modifier = Modifier.weight(1f)
        )
    }

    LcdParamRow(
        label = "$titleLabel B${safeIndex + 1} FREQ",
        valueText = "${band.frequencyHz.toInt()} Hz",
        isFocused = selectedField == EqParamField.FREQ,
        isEditing = isEditing && selectedField == EqParamField.FREQ,
        accentColor = accentColor,
        onClick = { onSelectField(EqParamField.FREQ) },
        sliderValue = log10(band.frequencyHz.coerceIn(20f, 20000f)),
        sliderRange = log10(20f)..log10(20000f),
        onSliderChange = { logVal ->
            val hz = 10.0.pow(logVal.toDouble()).toFloat()
            onUpdateBand(safeIndex) { it.copy(frequencyHz = hz) }
        }
    )

    LcdParamRow(
        label = "$titleLabel B${safeIndex + 1} GAIN",
        valueText = String.format(Locale.US, "%+.1f dB", band.gainDb),
        isFocused = selectedField == EqParamField.GAIN,
        isEditing = isEditing && selectedField == EqParamField.GAIN,
        accentColor = accentColor,
        onClick = { onSelectField(EqParamField.GAIN) },
        sliderValue = band.gainDb,
        sliderRange = -15f..15f,
        onSliderChange = { g -> onUpdateBand(safeIndex) { it.copy(gainDb = g) } }
    )

    LcdParamRow(
        label = "$titleLabel B${safeIndex + 1} Q FACTOR",
        valueText = String.format(Locale.US, "%.1f", band.q),
        isFocused = selectedField == EqParamField.Q,
        isEditing = isEditing && selectedField == EqParamField.Q,
        accentColor = accentColor,
        onClick = { onSelectField(EqParamField.Q) },
        sliderValue = band.q,
        sliderRange = 0.3f..16.0f,
        onSliderChange = { qv -> onUpdateBand(safeIndex) { it.copy(q = qv) } }
    )
}
