package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.DlmsPreset
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette

@Composable
fun DisplayPresetsScreen(
    presets: List<DlmsPreset>,
    activePresetName: String,
    onLoadPreset: (DlmsPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onRenamePreset: (DlmsPreset, String) -> Unit,
    onDeletePreset: (DlmsPreset) -> Unit,
    onResetDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DlmsPreset?>(null) }
    var presetNameInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LcdSmallActionButton(
                text = "SAVE / NEW",
                active = true,
                activeColor = palette.lcdPrimary,
                onClick = {
                    presetNameInput = if (activePresetName == "DEFAULT") "" else activePresetName
                    showSaveDialog = true
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("preset_save_btn")
            )
            LcdSmallActionButton(
                text = "RESET DEFAULT",
                active = false,
                activeColor = palette.meterYellow,
                onClick = onResetDefault,
                modifier = Modifier
                    .weight(1f)
                    .testTag("preset_reset_btn")
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                val isActive = preset.name == activePresetName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isActive) palette.lcdPrimary.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.3f))
                        .border(
                            1.dp,
                            if (isActive) palette.lcdPrimary else palette.lcdGrid,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onLoadPreset(preset) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (preset.isFactoryDefault) "${preset.name} [FACTORY]" else preset.name,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) palette.lcdPrimary else palette.lcdText
                        )
                        Text(
                            text = "XOVER ${preset.dspState.crossover.low.frequencyHz.toInt()}Hz | IN ${preset.dspState.inputL.gainDb}/${preset.dspState.inputR.gainDb}dB",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = palette.lcdMutedText
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LcdSmallActionButton(
                            text = "LOAD",
                            active = isActive,
                            onClick = { onLoadPreset(preset) }
                        )
                        if (!preset.isFactoryDefault) {
                            LcdSmallActionButton(
                                text = "REN",
                                onClick = {
                                    presetNameInput = preset.name
                                    renameTarget = preset
                                }
                            )
                            LcdSmallActionButton(
                                text = "DEL",
                                activeColor = palette.meterRed,
                                onClick = { onDeletePreset(preset) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save DSP Preset") },
            text = {
                Column {
                    Text("Save current 2-Way DLMS configuration locally:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        singleLine = true,
                        label = { Text("Preset Name") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSavePreset(presetNameInput)
                        showSaveDialog = false
                    }
                ) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("CANCEL") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Preset") },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    singleLine = true,
                    label = { Text("New Preset Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenamePreset(target, presetNameInput)
                        renameTarget = null
                    }
                ) { Text("RENAME") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("CANCEL") }
            }
        )
    }
}
