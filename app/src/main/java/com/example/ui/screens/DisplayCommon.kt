package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEngineState
import com.example.audio.AudioEngineStatus
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDlmsPalette

@Composable
fun LcdSystemStatusBar(
    dspEnabled: Boolean,
    audioState: AudioEngineState,
    activePresetName: String,
    pageTitle: String,
    onToggleDsp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    val audioColor = when (audioState.status) {
        AudioEngineStatus.READY -> palette.meterGreen
        AudioEngineStatus.STARTING -> palette.meterYellow
        AudioEngineStatus.PERMISSION_REQUIRED, AudioEngineStatus.ERROR -> palette.meterRed
        AudioEngineStatus.NOT_CONNECTED -> palette.lcdMutedText
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .border(width = 0.5.dp, color = palette.lcdGrid)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                StatusPill(
                    text = if (dspEnabled) "DSP:ON" else "DSP:BYP",
                    color = if (dspEnabled) palette.lcdPrimary else palette.meterYellow,
                    onClick = onToggleDsp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(audioColor)
                    )
                    Text(
                        text = audioState.statusLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = audioColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "IN:2 OUT:2 | $activePresetName",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = palette.lcdSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pageTitle,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.lcdPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (audioState.status == AudioEngineStatus.READY) {
                val hwNote = if (audioState.hwInputChannels == 1) "1CH->L/R" else "2CH"
                Text(
                    text = "${audioState.activeSampleRate / 1000}kHz/${audioState.activeBufferFrames}f ($hwNote)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = palette.lcdMutedText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun LcdSubTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDlmsPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 32.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) palette.lcdPrimary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f))
                    .border(
                        width = 1.dp,
                        color = if (active) palette.lcdPrimary else palette.lcdGrid,
                        shape = RoundedCornerShape(3.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) palette.lcdPrimary else palette.lcdMutedText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LcdParamRow(
    label: String,
    valueText: String,
    isFocused: Boolean,
    isEditing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    sliderValue: Float? = null,
    sliderRange: ClosedFloatingPointRange<Float>? = null,
    onSliderChange: ((Float) -> Unit)? = null
) {
    val palette = LocalDlmsPalette.current
    val highlight = accentColor ?: palette.lcdPrimary
    val bgColor = when {
        isFocused && isEditing -> highlight.copy(alpha = 0.22f)
        isFocused -> highlight.copy(alpha = 0.11f)
        else -> Color.Black.copy(alpha = 0.25f)
    }
    val borderColor = when {
        isFocused && isEditing -> highlight
        isFocused -> highlight.copy(alpha = 0.55f)
        else -> palette.lcdGrid
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isFocused) "▶ " else "  ",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = highlight
                )
                Text(
                    text = label,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = if (isFocused) palette.lcdText else palette.lcdMutedText
                )
                if (isFocused && isEditing) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[EDIT]",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.lcdSecondary
                    )
                }
            }
            Text(
                text = valueText,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) highlight else palette.lcdText
            )
        }
        if (sliderValue != null && sliderRange != null && onSliderChange != null) {
            Slider(
                value = sliderValue.coerceIn(sliderRange.start, sliderRange.endInclusive),
                onValueChange = onSliderChange,
                valueRange = sliderRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = highlight,
                    activeTrackColor = highlight,
                    inactiveTrackColor = palette.lcdGrid
                )
            )
        }
    }
}

@Composable
fun LcdSmallActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color? = null
) {
    val palette = LocalDlmsPalette.current
    val color = activeColor ?: palette.lcdPrimary
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) color.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.35f))
            .border(1.dp, if (active) color else palette.lcdGrid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) color else palette.lcdText,
            maxLines = 1
        )
    }
}
