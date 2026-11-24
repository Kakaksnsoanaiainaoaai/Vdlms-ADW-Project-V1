package com.example.hardware

enum class DisplayPage(val title: String) {
    HOME("HOME // SYSTEM OVERVIEW"),
    MENU("MAIN MENU"),
    INPUT_L("INPUT L // CH-A DSP"),
    INPUT_R("INPUT R // CH-B DSP"),
    CROSSOVER("CROSSOVER // 2-WAY"),
    OUTPUT_L("OUTPUT L // CH-1 DSP"),
    OUTPUT_R("OUTPUT R // CH-2 DSP"),
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
    QUICK_IN_L,
    QUICK_IN_R,
    QUICK_XOVER,
    QUICK_OUT_L,
    QUICK_OUT_R,
    QUICK_MEDIA
}

enum class InputSubTab(val label: String) {
    MAIN("MAIN"),
    EQ("5-BAND PEQ")
}

enum class OutputSubTab(val label: String) {
    MAIN("MAIN"),
    EQ("5-BAND PEQ"),
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
    MenuEntry("INPUT L", "Gain, Polarity, Mute, Delay, 5-Band EQ", DisplayPage.INPUT_L),
    MenuEntry("INPUT R", "Gain, Polarity, Mute, Delay, 5-Band EQ", DisplayPage.INPUT_R),
    MenuEntry("CROSSOVER 2-WAY", "LOW / HIGH Filters, Type, Slope, Graph", DisplayPage.CROSSOVER),
    MenuEntry("OUTPUT L", "Routing, Gain, Delay, Polarity, EQ, Limiter", DisplayPage.OUTPUT_L),
    MenuEntry("OUTPUT R", "Routing, Gain, Delay, Polarity, EQ, Limiter", DisplayPage.OUTPUT_R),
    MenuEntry("PRESET MANAGER", "Save, Load, Rename, Delete, Reset", DisplayPage.PRESETS),
    MenuEntry("MEDIA / YOUTUBE", "In-App Official YouTube Player & Search", DisplayPage.MEDIA_YOUTUBE),
    MenuEntry("SETTINGS", "Theme, Display, Audio Device, Rate, Buffer", DisplayPage.SETTINGS)
)

data class HardwareUiState(
    val currentPage: DisplayPage = DisplayPage.HOME,
    val backStack: List<DisplayPage> = emptyList(),
    val menuIndex: Int = 0,
    val inputSubTab: InputSubTab = InputSubTab.MAIN,
    val outputSubTab: OutputSubTab = OutputSubTab.MAIN,
    val crossoverBandTab: Int = 0, // 0 = LOW, 1 = HIGH
    val focusIndex: Int = 0,
    val selectedEqBand: Int = 0, // 0..4
    val eqParamField: EqParamField = EqParamField.GAIN,
    val isEditingParam: Boolean = false,
    val encoderAngleDeg: Float = 0f,
    val lastKeyPressed: HardwareKey? = null,
    val lastInteractionTimeMs: Long = System.currentTimeMillis(),
    val isDisplayDimmed: Boolean = false
)
