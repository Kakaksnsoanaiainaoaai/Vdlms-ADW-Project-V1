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
import com.example.dsp.CrossoverBandConfig
import com.example.dsp.CrossoverConfig
import com.example.dsp.CrossoverFilterType
import com.example.dsp.CrossoverSlope
import com.example.hardware.HardwareUiState
import com.example.ui.components.CrossoverFrequencyGraph
import com.example.ui.theme.LocalDlmsPalette
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun DisplayCrossoverScreen(
    crossover: CrossoverConfig,
    hwState: HardwareUiState,
    onSelectBandTab: (Int) -> Unit,
    onFocusParam: (Int) -> Unit,
    onToggleGlobalBypass: (Boolean) -> Unit,
    onUpdateBand: (isLow: Boolean, (CrossoverBandConfig) -> CrossoverBandConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val scroll = rememberScrollState()
    val isLow = hwState.crossoverBandTab == 0
    val activeBand = if (isLow) crossover.low else crossover.high

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CrossoverFrequencyGraph(
            crossover = crossover,
            selectedBand = hwState.crossoverBandTab,
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp)
        )

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
                text = if (crossover.bypass) "XOVER: BYP" else "XOVER: ON",
                active = !crossover.bypass,
                activeColor = if (crossover.bypass) palette.meterYellow else palette.meterGreen,
                onClick = { onToggleGlobalBypass(!crossover.bypass) }
            )
        }

        LcdParamRow(
            label = if (isLow) "LOW CUTOFF FREQ" else "HIGH CUTOFF FREQ",
            valueText = "${activeBand.frequencyHz.toInt()} Hz",
            isFocused = hwState.focusIndex == 0,
            isEditing = hwState.isEditingParam,
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
            label = "FILTER TYPE",
            valueText = activeBand.filterType.label,
            isFocused = hwState.focusIndex == 1,
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
            label = if (isLow) "LOW BAND BYPASS" else "HIGH BAND BYPASS",
            valueText = if (activeBand.bypass) "BYPASSED" else "FILTER ACTIVE",
            isFocused = hwState.focusIndex == 3,
            isEditing = false,
            accentColor = if (activeBand.bypass) palette.meterYellow else palette.meterGreen,
            onClick = {
                onFocusParam(3)
                onUpdateBand(isLow) { it.copy(bypass = !it.bypass) }
            }
        )
    }
}
