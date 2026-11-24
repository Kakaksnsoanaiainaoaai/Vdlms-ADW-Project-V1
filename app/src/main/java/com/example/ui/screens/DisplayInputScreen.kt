package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dsp.ChannelTelemetry
import com.example.dsp.DspUnits
import com.example.dsp.EqBandConfig
import com.example.dsp.EqFilterType
import com.example.dsp.InputChannelConfig
import com.example.hardware.EqParamField
import com.example.hardware.HardwareUiState
import com.example.hardware.InputSubTab
import com.example.storage.MeterStyleOption
import com.example.ui.components.HorizontalVuMeter
import com.example.ui.components.ParametricEqGraph
import kotlin.math.pow
import com.example.ui.theme.LocalDlmsPalette
import java.util.Locale

@Composable
fun DisplayInputScreen(
    channelLabel: String,
    config: InputChannelConfig,
    telemetry: ChannelTelemetry,
    hwState: HardwareUiState,
    meterStyle: MeterStyleOption,
    onSelectSubTab: (InputSubTab) -> Unit,
    onFocusParam: (Int) -> Unit,
    onUpdateConfig: ((InputChannelConfig) -> InputChannelConfig) -> Unit,
    onSelectEqBand: (Int) -> Unit,
    onSelectEqField: (EqParamField) -> Unit,
    onToggleEqBand: (Int) -> Unit,
    onUpdateEqBand: (Int, (EqBandConfig) -> EqBandConfig) -> Unit,
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
            label = "$channelLabel METER",
            telemetry = telemetry,
            meterStyle = meterStyle,
            isMuted = config.muted
        )

        LcdSubTabRow(
            tabs = InputSubTab.entries.map { it.label },
            selectedIndex = InputSubTab.entries.indexOf(hwState.inputSubTab),
            onSelect = { onSelectSubTab(InputSubTab.entries[it]) }
        )

        if (hwState.inputSubTab == InputSubTab.MAIN) {
            LcdParamRow(
                label = "INPUT GAIN",
                valueText = String.format(Locale.US, "%+.1f dB", config.gainDb),
                isFocused = hwState.focusIndex == 0,
                isEditing = hwState.isEditingParam,
                onClick = { onFocusParam(0) },
                sliderValue = config.gainDb,
                sliderRange = -24f..12f,
                onSliderChange = { v -> onUpdateConfig { it.copy(gainDb = v) } }
            )
            LcdParamRow(
                label = "POLARITY",
                valueText = if (config.polarityInverted) "INVERTED (180°)" else "NORMAL (0°)",
                isFocused = hwState.focusIndex == 1,
                isEditing = false,
                accentColor = if (config.polarityInverted) palette.lcdSecondary else palette.lcdPrimary,
                onClick = {
                    onFocusParam(1)
                    onUpdateConfig { it.copy(polarityInverted = !it.polarityInverted) }
                }
            )
            LcdParamRow(
                label = "CHANNEL MUTE",
                valueText = if (config.muted) "MUTED" else "ACTIVE",
                isFocused = hwState.focusIndex == 2,
                isEditing = false,
                accentColor = if (config.muted) palette.meterRed else palette.meterGreen,
                onClick = {
                    onFocusParam(2)
                    onUpdateConfig { it.copy(muted = !it.muted) }
                }
            )
            val distM = DspUnits.msToMeters(config.delayMs)
            LcdParamRow(
                label = "INPUT DELAY",
                valueText = String.format(Locale.US, "%.2f ms / %.2f m", config.delayMs, distM),
                isFocused = hwState.focusIndex == 3,
                isEditing = hwState.isEditingParam,
                onClick = { onFocusParam(3) },
                sliderValue = config.delayMs,
                sliderRange = 0f..50f,
                onSliderChange = { v -> onUpdateConfig { it.copy(delayMs = v) } }
            )
        } else {
            EqEditorSection(
                eqBypass = config.eqBypass,
                bands = config.eqBands,
                selectedBandIndex = hwState.selectedEqBand,
                selectedField = hwState.eqParamField,
                isEditing = hwState.isEditingParam,
                accentColor = palette.lcdPrimary,
                onToggleGlobalBypass = { onUpdateConfig { it.copy(eqBypass = !it.eqBypass) } },
                onSelectBand = onSelectEqBand,
                onSelectField = onSelectEqField,
                onToggleBand = onToggleEqBand,
                onUpdateBand = onUpdateEqBand
            )
        }
    }
}

@Composable
fun EqEditorSection(
    eqBypass: Boolean,
    bands: List<EqBandConfig>,
    selectedBandIndex: Int,
    selectedField: EqParamField,
    isEditing: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
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
            text = if (eqBypass) "BYP" else "ON",
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
        label = "B${safeIndex + 1} FREQ",
        valueText = "${band.frequencyHz.toInt()} Hz",
        isFocused = selectedField == EqParamField.FREQ,
        isEditing = isEditing && selectedField == EqParamField.FREQ,
        onClick = { onSelectField(EqParamField.FREQ) },
        sliderValue = kotlin.math.log10(band.frequencyHz.coerceIn(20f, 20000f)),
        sliderRange = kotlin.math.log10(20f)..kotlin.math.log10(20000f),
        onSliderChange = { logVal ->
            val hz = 10.0.pow(logVal.toDouble()).toFloat()
            onUpdateBand(safeIndex) { it.copy(frequencyHz = hz) }
        }
    )

    LcdParamRow(
        label = "B${safeIndex + 1} GAIN",
        valueText = String.format(Locale.US, "%+.1f dB", band.gainDb),
        isFocused = selectedField == EqParamField.GAIN,
        isEditing = isEditing && selectedField == EqParamField.GAIN,
        onClick = { onSelectField(EqParamField.GAIN) },
        sliderValue = band.gainDb,
        sliderRange = -15f..15f,
        onSliderChange = { g -> onUpdateBand(safeIndex) { it.copy(gainDb = g) } }
    )

    LcdParamRow(
        label = "B${safeIndex + 1} Q FACTOR",
        valueText = String.format(Locale.US, "%.1f", band.q),
        isFocused = selectedField == EqParamField.Q,
        isEditing = isEditing && selectedField == EqParamField.Q,
        onClick = { onSelectField(EqParamField.Q) },
        sliderValue = band.q,
        sliderRange = 0.3f..16.0f,
        onSliderChange = { qv -> onUpdateBand(safeIndex) { it.copy(q = qv) } }
    )
}
