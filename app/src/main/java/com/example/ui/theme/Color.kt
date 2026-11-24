package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.storage.RackThemeStyle

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

@Immutable
data class DlmsHardwarePalette(
    val chassisBg: Color,
    val chassisSurface: Color,
    val chassisBezel: Color,
    val rackEarBg: Color,
    val screwMetal: Color,
    val silkscreenText: Color,
    val lcdBg: Color,
    val lcdGrid: Color,
    val lcdBorder: Color,
    val lcdPrimary: Color,
    val lcdSecondary: Color,
    val lcdText: Color,
    val lcdMutedText: Color,
    val meterGreen: Color,
    val meterYellow: Color,
    val meterRed: Color,
    val ledOff: Color,
    val buttonTop: Color,
    val buttonBottom: Color,
    val buttonBorder: Color,
    val knobBody: Color,
    val knobHighlight: Color
)

fun paletteForTheme(theme: RackThemeStyle): DlmsHardwarePalette = when (theme) {
    RackThemeStyle.RACK_OBSIDIAN -> DlmsHardwarePalette(
        chassisBg = Color(0xFF101318),
        chassisSurface = Color(0xFF191E26),
        chassisBezel = Color(0xFF232A35),
        rackEarBg = Color(0xFF0C0F13),
        screwMetal = Color(0xFF4B5563),
        silkscreenText = Color(0xFFB8C4D4),
        lcdBg = Color(0xFF060C12),
        lcdGrid = Color(0xFF132433),
        lcdBorder = Color(0xFF283546),
        lcdPrimary = Color(0xFF00E5FF),
        lcdSecondary = Color(0xFFFFB300),
        lcdText = Color(0xFFE6F4FA),
        lcdMutedText = Color(0xFF7690A6),
        meterGreen = Color(0xFF00E676),
        meterYellow = Color(0xFFFFCA28),
        meterRed = Color(0xFFFF3B30),
        ledOff = Color(0xFF202731),
        buttonTop = Color(0xFF28303D),
        buttonBottom = Color(0xFF181D26),
        buttonBorder = Color(0xFF394456),
        knobBody = Color(0xFF232933),
        knobHighlight = Color(0xFF475569)
    )

    RackThemeStyle.STUDIO_TITANIUM -> DlmsHardwarePalette(
        chassisBg = Color(0xFF1E232B),
        chassisSurface = Color(0xFF272D37),
        chassisBezel = Color(0xFF333B48),
        rackEarBg = Color(0xFF171B22),
        screwMetal = Color(0xFF7B8798),
        silkscreenText = Color(0xFFDDE5EE),
        lcdBg = Color(0xFF070F18),
        lcdGrid = Color(0xFF172B40),
        lcdBorder = Color(0xFF3D495B),
        lcdPrimary = Color(0xFF38BDF8),
        lcdSecondary = Color(0xFF34D399),
        lcdText = Color(0xFFF0F9FF),
        lcdMutedText = Color(0xFF8AA5BD),
        meterGreen = Color(0xFF22C55E),
        meterYellow = Color(0xFFFACC15),
        meterRed = Color(0xFFEF4444),
        ledOff = Color(0xFF2A323E),
        buttonTop = Color(0xFF3A4352),
        buttonBottom = Color(0xFF252C37),
        buttonBorder = Color(0xFF546175),
        knobBody = Color(0xFF343D4B),
        knobHighlight = Color(0xFF6B7C93)
    )

    RackThemeStyle.AMBER_PHOSPHOR -> DlmsHardwarePalette(
        chassisBg = Color(0xFF121110),
        chassisSurface = Color(0xFF1C1A17),
        chassisBezel = Color(0xFF292520),
        rackEarBg = Color(0xFF0D0C0B),
        screwMetal = Color(0xFF5A5247),
        silkscreenText = Color(0xFFD6C7B2),
        lcdBg = Color(0xFF0D0904),
        lcdGrid = Color(0xFF2B1D0A),
        lcdBorder = Color(0xFF3B3228),
        lcdPrimary = Color(0xFFFF9F1C),
        lcdSecondary = Color(0xFF38E54D),
        lcdText = Color(0xFFFFE8C8),
        lcdMutedText = Color(0xFFA6865A),
        meterGreen = Color(0xFF4ADE80),
        meterYellow = Color(0xFFFBBF24),
        meterRed = Color(0xFFFF3B30),
        ledOff = Color(0xFF26201A),
        buttonTop = Color(0xFF2D2822),
        buttonBottom = Color(0xFF1B1814),
        buttonBorder = Color(0xFF463E34),
        knobBody = Color(0xFF28231D),
        knobHighlight = Color(0xFF594E41)
    )
}
