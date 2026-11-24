package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEngineState
import com.example.audio.AudioHardwareCapabilities
import com.example.storage.DisplayTimeoutOption
import com.example.storage.MeterStyleOption
import com.example.storage.RackThemeStyle
import com.example.storage.SettingsState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette
import kotlin.math.roundToInt

@Composable
fun DisplaySettingsScreen(
    settings: SettingsState,
    capabilities: AudioHardwareCapabilities,
    audioState: AudioEngineState,
    onSelectTheme: (RackThemeStyle) -> Unit,
    onSetBrightness: (Float) -> Unit,
    onSelectTimeout: (DisplayTimeoutOption) -> Unit,
    onSelectMeterStyle: (MeterStyleOption) -> Unit,
    onSelectInputDevice: (Int?) -> Unit,
    onSelectOutputDevice: (Int?) -> Unit,
    onSelectSampleRate: (Int) -> Unit,
    onSelectBufferMultiplier: (Int) -> Unit,
    onRefreshDevices: () -> Unit,
    onResetApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val scroll = rememberScrollState()
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsSectionCard(title = "DISPLAY & HARDWARE THEME") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RackThemeStyle.entries.forEach { theme ->
                    LcdSmallActionButton(
                        text = theme.label.substringBefore(" "),
                        active = settings.theme == theme,
                        onClick = { onSelectTheme(theme) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DISPLAY BRIGHTNESS",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = palette.lcdMutedText
                )
                Text(
                    text = "${(settings.displayBrightness * 100).roundToInt()}%",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = palette.lcdPrimary
                )
            }
            Slider(
                value = settings.displayBrightness,
                onValueChange = onSetBrightness,
                valueRange = 0.3f..1.0f,
                modifier = Modifier.height(26.dp),
                colors = SliderDefaults.colors(
                    thumbColor = palette.lcdPrimary,
                    activeTrackColor = palette.lcdPrimary,
                    inactiveTrackColor = palette.lcdGrid
                )
            )
            Text(
                text = "DISPLAY TIMEOUT",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.lcdMutedText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DisplayTimeoutOption.entries.forEach { opt ->
                    LcdSmallActionButton(
                        text = opt.label,
                        active = settings.displayTimeout == opt,
                        onClick = { onSelectTimeout(opt) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "VU METER STYLE",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.lcdMutedText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MeterStyleOption.entries.forEach { style ->
                    LcdSmallActionButton(
                        text = style.label.substringBefore(" "),
                        active = settings.meterStyle == style,
                        onClick = { onSelectMeterStyle(style) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SettingsSectionCard(title = "REAL AUDIO HARDWARE & LATENCY") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "INPUT DEVICES (${capabilities.inputDevices.size})",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = palette.lcdMutedText
                )
                LcdSmallActionButton(text = "SCAN", onClick = onRefreshDevices)
            }
            if (capabilities.inputDevices.isEmpty()) {
                Text(
                    text = "No hardware audio input device detected.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = palette.meterRed
                )
            } else {
                LcdSmallActionButton(
                    text = "SYSTEM AUTO INPUT",
                    active = settings.inputDeviceId == null,
                    onClick = { onSelectInputDevice(null) },
                    modifier = Modifier.fillMaxWidth()
                )
                capabilities.inputDevices.forEach { dev ->
                    LcdSmallActionButton(
                        text = dev.name,
                        active = settings.inputDeviceId == dev.id,
                        onClick = { onSelectInputDevice(dev.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "OUTPUT DEVICES (${capabilities.outputDevices.size})",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.lcdMutedText
            )
            if (capabilities.outputDevices.isEmpty()) {
                Text(
                    text = "No hardware audio output device detected.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = palette.meterRed
                )
            } else {
                LcdSmallActionButton(
                    text = "SYSTEM AUTO OUTPUT",
                    active = settings.outputDeviceId == null,
                    onClick = { onSelectOutputDevice(null) },
                    modifier = Modifier.fillMaxWidth()
                )
                capabilities.outputDevices.forEach { dev ->
                    LcdSmallActionButton(
                        text = dev.name,
                        active = settings.outputDeviceId == dev.id,
                        onClick = { onSelectOutputDevice(dev.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SUPPORTED SAMPLE RATES",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.lcdMutedText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                capabilities.supportedSampleRates.forEach { rate ->
                    LcdSmallActionButton(
                        text = "${rate / 1000.0}k",
                        active = settings.sampleRate == rate,
                        onClick = { onSelectSampleRate(rate) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "BUFFER SIZE (NATIVE ${capabilities.nativeFramesPerBuffer}f × MULT)",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.lcdMutedText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                capabilities.supportedBufferMultipliers.forEach { mult ->
                    val frames = capabilities.nativeFramesPerBuffer * mult
                    LcdSmallActionButton(
                        text = "${frames}f",
                        active = settings.bufferMultiplier == mult,
                        onClick = { onSelectBufferMultiplier(mult) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SettingsSectionCard(title = "SYSTEM & ABOUT") {
            Text(
                text = "DLMS 2-Way // Model DLMS-202X\nArchitecture: IN L + IN R -> Input DSP -> 2-Way Crossover -> Output DSP + Limiter -> OUT L + OUT R\nNative SR: ${capabilities.nativeSampleRate} Hz | Engine: ${audioState.statusLabel}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.lcdText
            )
            Spacer(modifier = Modifier.height(4.dp))
            LcdSmallActionButton(
                text = "RESET APPLICATION TO FACTORY",
                active = true,
                activeColor = palette.meterRed,
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Application?") },
            text = { Text("This will stop the audio engine, delete all user presets, and restore factory DEFAULT settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetApp()
                        showResetConfirm = false
                    }
                ) { Text("RESET") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val palette = LocalDlmsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, palette.lcdGrid, RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = title,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.lcdPrimary
        )
        content()
    }
}
