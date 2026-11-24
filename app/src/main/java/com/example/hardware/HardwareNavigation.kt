package com.example.hardware

enum class DisplayPage(val title: String) {
    HOME("HOME // CHANNEL L & R"),
    MENU("MAIN MENU"),
    AUDIO_SOURCE("AUDIO SOURCE // ROUTING"),
    CHANNEL_L("CHANNEL L // DSP CHAIN"),
    CHANNEL_R("CHANNEL R // DSP CHAIN"),
    CROSSOVER("CROSSOVER // L & R"),
    PRESETS("PRESET MANAGER"),
    MEDIA_YOUTUBE("MEDIA // YOUTUBE"),
    SETTINGS("SYSTEM SETTINGS")
}

enum class HardwareKey {
    MENU,
    BACK,
    ENTER,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    MUTE,
    QUICK_SOURCE,
    QUICK_IN_L,
    QUICK_IN_R,
    QUICK_XOVER,
    QUICK_OUT_L,
    QUICK_OUT_R,
    QUICK_MEDIA
}

enum class ChannelDspSubTab(val label: String) {
    MAIN("GAIN/DLY"),
    EQ("EQ"),
    XOVER("XOVER"),
    LIMITER("LIMITER")
}

enum class EqParamField(val label: String) {
    ENABLED("BYPASS"),
    TYPE("TYPE"),
    FREQ("FREQ"),
    GAIN("GAIN"),
    Q("Q")
}

data class MenuEntry(
    val title: String,
    val subtitle: String,
    val targetPage: DisplayPage
)

val mainMenuEntries = listOf(
    MenuEntry("AUDIO SOURCE", "Select DEVICE INPUT, INTERNAL MEDIA, or YOUTUBE", DisplayPage.AUDIO_SOURCE),
    MenuEntry("CHANNEL L DSP", "IN L, Gain L, EQ L, Delay L, XOVER L, Limiter L, OUT L", DisplayPage.CHANNEL_L),
    MenuEntry("CHANNEL R DSP", "IN R, Gain R, EQ R, Delay R, XOVER R, Limiter R, OUT R", DisplayPage.CHANNEL_R),
    MenuEntry("CROSSOVER L / R", "Independent XOVER L & XOVER R Filters", DisplayPage.CROSSOVER),
    MenuEntry("PRESET MANAGER", "Save, Load, Rename, Delete (L/R Separate)", DisplayPage.PRESETS),
    MenuEntry("MEDIA / YOUTUBE", "In-App Official YouTube Player & Search", DisplayPage.MEDIA_YOUTUBE),
    MenuEntry("SETTINGS", "Theme, Display, Audio Device, Rate, Buffer", DisplayPage.SETTINGS)
)

data class HardwareUiState(
    val currentPage: DisplayPage = DisplayPage.HOME,
    val backStack: List<DisplayPage> = emptyList(),
    val menuIndex: Int = 0,
    val channelSubTab: ChannelDspSubTab = ChannelDspSubTab.MAIN,
    val crossoverChannelTab: Int = 0, // 0 = XOVER L, 1 = XOVER R
    val crossoverBandTab: Int = 0,    // 0 = LOW, 1 = HIGH
    val focusIndex: Int = 0,
    val selectedEqBand: Int = 0,      // 0..4
    val eqParamField: EqParamField = EqParamField.GAIN,
    val isEditingParam: Boolean = false,
    val encoderAngleDeg: Float = 0f,
    val lastKeyPressed: HardwareKey? = null,
    val lastInteractionTimeMs: Long = System.currentTimeMillis(),
    val isDisplayDimmed: Boolean = false
)
