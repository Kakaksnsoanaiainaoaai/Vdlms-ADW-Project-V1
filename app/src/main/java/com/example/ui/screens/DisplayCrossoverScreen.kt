package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dsp.ChannelDspConfig
import com.example.dsp.CrossoverBand
import com.example.dsp.CrossoverBandConfig
import com.example.hardware.HardwareUiState
import com.example.ui.theme.LocalDlmsPalette

@Composable
fun DisplayCrossoverScreen(
    channelL: ChannelDspConfig,
    channelR: ChannelDspConfig,
    hwState: HardwareUiState,
    onSelectChannelTab: (Int) -> Unit,
    onSelectBandTab: (Int) -> Unit,
    onFocusParam: (Int) -> Unit,
    onSelectActiveBand: (isLeft: Boolean, CrossoverBand) -> Unit,
    onToggleGlobalBypass: (isLeft: Boolean, Boolean) -> Unit,
    onUpdateBand: (isLeft: Boolean, isLow: Boolean, (CrossoverBandConfig) -> CrossoverBandConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val scroll = rememberScrollState()
    val isLeft = hwState.crossoverChannelTab == 0
    val activeChannel = if (isLeft) channelL else channelR
    val chTag = if (isLeft) "L" else "R"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Explicit XOVER L vs XOVER R Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LcdSmallActionButton(
                text = "XOVER L (CH L)",
                active = isLeft,
                activeColor = palette.lcdPrimary,
                onClick = { onSelectChannelTab(0) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("xover_tab_l")
            )
            LcdSmallActionButton(
                text = "XOVER R (CH R)",
                active = !isLeft,
                activeColor = palette.lcdSecondary,
                onClick = { onSelectChannelTab(1) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("xover_tab_r")
            )
        }

        ChannelCrossoverSection(
            channelTag = chTag,
            crossover = activeChannel.crossover,
            activeBandAssignment = activeChannel.activeBand,
            selectedBandTab = hwState.crossoverBandTab,
            focusIndex = hwState.focusIndex,
            isEditing = hwState.isEditingParam,
            onSelectBandAssignment = { onSelectActiveBand(isLeft, it) },
            onSelectBandTab = onSelectBandTab,
            onFocusParam = onFocusParam,
            onToggleGlobalBypass = { onToggleGlobalBypass(isLeft, it) },
            onUpdateBand = { isLow, blk -> onUpdateBand(isLeft, isLow, blk) }
        )
    }
}
