package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.storage.RackThemeStyle

val LocalDlmsPalette = staticCompositionLocalOf { paletteForTheme(RackThemeStyle.RACK_OBSIDIAN) }

@Composable
fun DlmsHardwareTheme(
    themeStyle: RackThemeStyle = RackThemeStyle.RACK_OBSIDIAN,
    content: @Composable () -> Unit
) {
    val palette = paletteForTheme(themeStyle)
    val colorScheme = darkColorScheme(
        primary = palette.lcdPrimary,
        secondary = palette.lcdSecondary,
        background = palette.chassisBg,
        surface = palette.chassisSurface,
        surfaceVariant = palette.chassisBezel,
        onPrimary = palette.lcdBg,
        onSecondary = palette.lcdBg,
        onBackground = palette.lcdText,
        onSurface = palette.lcdText,
        error = palette.meterRed
    )

    CompositionLocalProvider(LocalDlmsPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DlmsTypography,
            content = content
        )
    }
}
