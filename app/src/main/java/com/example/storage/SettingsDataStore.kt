package com.example.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dlmsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dlms_settings")

enum class RackThemeStyle(val label: String) {
    RACK_OBSIDIAN("Rack Obsidian"),
    STUDIO_TITANIUM("Studio Titanium"),
    AMBER_PHOSPHOR("Amber Phosphor")
}

enum class DisplayTimeoutOption(val label: String, val seconds: Long) {
    NEVER("Never", 0L),
    SEC_30("30 sec", 30L),
    MIN_1("1 min", 60L),
    MIN_5("5 min", 300L)
}

enum class MeterStyleOption(val label: String) {
    LED_SEGMENTED("LED Segmented"),
    SMOOTH_BAR("Smooth Bar"),
    PEAK_HOLD("Peak Hold")
}

data class SettingsState(
    val theme: RackThemeStyle = RackThemeStyle.RACK_OBSIDIAN,
    val displayBrightness: Float = 1.0f,
    val displayTimeout: DisplayTimeoutOption = DisplayTimeoutOption.NEVER,
    val meterStyle: MeterStyleOption = MeterStyleOption.LED_SEGMENTED,
    val inputDeviceId: Int? = null,
    val outputDeviceId: Int? = null,
    val sampleRate: Int = 48000,
    val bufferMultiplier: Int = 2,
    val activePresetName: String = PresetRepository.DEFAULT_PRESET_NAME,
    val lastDspJson: String? = null
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val TIMEOUT = stringPreferencesKey("timeout")
        val METER_STYLE = stringPreferencesKey("meter_style")
        val INPUT_DEVICE_ID = intPreferencesKey("input_device_id")
        val OUTPUT_DEVICE_ID = intPreferencesKey("output_device_id")
        val SAMPLE_RATE = intPreferencesKey("sample_rate")
        val BUFFER_MULTIPLIER = intPreferencesKey("buffer_multiplier")
        val ACTIVE_PRESET = stringPreferencesKey("active_preset")
        val LAST_DSP_JSON = stringPreferencesKey("last_dsp_json")
    }

    val settingsFlow: Flow<SettingsState> = context.dlmsDataStore.data.map { prefs ->
        SettingsState(
            theme = prefs[Keys.THEME]?.let { runCatching { RackThemeStyle.valueOf(it) }.getOrNull() }
                ?: RackThemeStyle.RACK_OBSIDIAN,
            displayBrightness = (prefs[Keys.BRIGHTNESS] ?: 1.0f).coerceIn(0.3f, 1.0f),
            displayTimeout = prefs[Keys.TIMEOUT]?.let { runCatching { DisplayTimeoutOption.valueOf(it) }.getOrNull() }
                ?: DisplayTimeoutOption.NEVER,
            meterStyle = prefs[Keys.METER_STYLE]?.let { runCatching { MeterStyleOption.valueOf(it) }.getOrNull() }
                ?: MeterStyleOption.LED_SEGMENTED,
            inputDeviceId = prefs[Keys.INPUT_DEVICE_ID]?.takeIf { it >= 0 },
            outputDeviceId = prefs[Keys.OUTPUT_DEVICE_ID]?.takeIf { it >= 0 },
            sampleRate = prefs[Keys.SAMPLE_RATE] ?: 48000,
            bufferMultiplier = (prefs[Keys.BUFFER_MULTIPLIER] ?: 2).coerceIn(1, 8),
            activePresetName = prefs[Keys.ACTIVE_PRESET] ?: PresetRepository.DEFAULT_PRESET_NAME,
            lastDspJson = prefs[Keys.LAST_DSP_JSON]
        )
    }

    suspend fun updateTheme(theme: RackThemeStyle) {
        context.dlmsDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun updateBrightness(brightness: Float) {
        context.dlmsDataStore.edit { it[Keys.BRIGHTNESS] = brightness.coerceIn(0.3f, 1.0f) }
    }

    suspend fun updateTimeout(timeout: DisplayTimeoutOption) {
        context.dlmsDataStore.edit { it[Keys.TIMEOUT] = timeout.name }
    }

    suspend fun updateMeterStyle(style: MeterStyleOption) {
        context.dlmsDataStore.edit { it[Keys.METER_STYLE] = style.name }
    }

    suspend fun updateInputDeviceId(deviceId: Int?) {
        context.dlmsDataStore.edit { it[Keys.INPUT_DEVICE_ID] = deviceId ?: -1 }
    }

    suspend fun updateOutputDeviceId(deviceId: Int?) {
        context.dlmsDataStore.edit { it[Keys.OUTPUT_DEVICE_ID] = deviceId ?: -1 }
    }

    suspend fun updateSampleRate(sampleRate: Int) {
        context.dlmsDataStore.edit { it[Keys.SAMPLE_RATE] = sampleRate }
    }

    suspend fun updateBufferMultiplier(multiplier: Int) {
        context.dlmsDataStore.edit { it[Keys.BUFFER_MULTIPLIER] = multiplier.coerceIn(1, 8) }
    }

    suspend fun saveLastDspState(presetName: String, dspJson: String) {
        context.dlmsDataStore.edit {
            it[Keys.ACTIVE_PRESET] = presetName
            it[Keys.LAST_DSP_JSON] = dspJson
        }
    }

    suspend fun resetAll() {
        context.dlmsDataStore.edit { it.clear() }
    }
}
