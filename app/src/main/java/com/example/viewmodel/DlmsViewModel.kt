package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DlmsApplication
import com.example.audio.AudioEngineState
import com.example.audio.AudioHardwareCapabilities
import com.example.dsp.AudioTelemetry
import com.example.dsp.CrossoverBand
import com.example.dsp.CrossoverBandConfig
import com.example.dsp.CrossoverFilterType
import com.example.dsp.CrossoverSlope
import com.example.dsp.DspState
import com.example.dsp.EqBandConfig
import com.example.dsp.EqFilterType
import com.example.dsp.InputChannelConfig
import com.example.dsp.LimiterConfig
import com.example.dsp.OutputChannelConfig
import com.example.dsp.OutputSource
import com.example.hardware.DisplayPage
import com.example.hardware.EqParamField
import com.example.hardware.HardwareKey
import com.example.hardware.HardwareUiState
import com.example.hardware.InputSubTab
import com.example.hardware.OutputSubTab
import com.example.hardware.mainMenuEntries
import com.example.storage.DisplayTimeoutOption
import com.example.storage.DlmsPreset
import com.example.storage.MeterStyleOption
import com.example.storage.PresetRepository
import com.example.storage.RackThemeStyle
import com.example.storage.SettingsState
import com.example.youtube.YouTubePlaybackStatus
import com.example.youtube.YouTubeUiState
import com.example.youtube.YouTubeUrlParser
import com.example.youtube.YouTubeViewMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DlmsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DlmsApplication
    private val presetRepo = app.presetRepository
    private val settingsStore = app.settingsDataStore
    private val audioEnumerator = app.audioEnumerator
    private val audioEngine = app.audioEngine

    private val _dspState = MutableStateFlow(DspState.neutralDefault())
    val dspState: StateFlow<DspState> = _dspState.asStateFlow()

    private val _hardwareState = MutableStateFlow(HardwareUiState())
    val hardwareState: StateFlow<HardwareUiState> = _hardwareState.asStateFlow()

    private val _capabilities = MutableStateFlow(audioEnumerator.queryCapabilities())
    val capabilities: StateFlow<AudioHardwareCapabilities> = _capabilities.asStateFlow()

    val audioEngineState: StateFlow<AudioEngineState> = audioEngine.engineState
    val telemetry: StateFlow<AudioTelemetry> = audioEngine.telemetry

    val presets: StateFlow<List<DlmsPreset>> = presetRepo.presets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settingsState: StateFlow<SettingsState> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    private val _activePresetName = MutableStateFlow(PresetRepository.DEFAULT_PRESET_NAME)
    val activePresetName: StateFlow<String> = _activePresetName.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _youtubeState = MutableStateFlow(
        YouTubeUiState(
            isOnline = checkNetworkConnected()
        )
    )
    val youtubeState: StateFlow<YouTubeUiState> = _youtubeState.asStateFlow()

    private var saveDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            presetRepo.ensureDefaultPreset()
            val savedSettings = settingsStore.settingsFlow.first()
            _activePresetName.value = savedSettings.activePresetName
            val restoredState = savedSettings.lastDspJson?.let { presetRepo.decodeState(it) }
                ?: DspState.neutralDefault()
            _dspState.value = restoredState
            audioEngine.updateDspConfig(restoredState)
            refreshAudioCapabilities()
        }

        // Display timeout watcher
        viewModelScope.launch {
            while (true) {
                delay(2000L)
                val timeoutSec = settingsState.value.displayTimeout.seconds
                if (timeoutSec > 0L) {
                    val elapsed = System.currentTimeMillis() - _hardwareState.value.lastInteractionTimeMs
                    val shouldDim = elapsed >= timeoutSec * 1000L
                    if (shouldDim != _hardwareState.value.isDisplayDimmed) {
                        _hardwareState.update { it.copy(isDisplayDimmed = shouldDim) }
                    }
                } else if (_hardwareState.value.isDisplayDimmed) {
                    _hardwareState.update { it.copy(isDisplayDimmed = false) }
                }
            }
        }
    }

    fun registerInteraction() {
        _hardwareState.update {
            it.copy(
                lastInteractionTimeMs = System.currentTimeMillis(),
                isDisplayDimmed = false
            )
        }
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun showBanner(message: String) {
        _statusBannerMessage.value = message
    }

    fun refreshAudioCapabilities() {
        _capabilities.value = audioEnumerator.queryCapabilities()
    }

    fun startAudioEngine() {
        registerInteraction()
        refreshAudioCapabilities()
        val s = settingsState.value
        audioEngine.start(
            preferredSampleRate = s.sampleRate,
            bufferMultiplier = s.bufferMultiplier,
            preferredInputDeviceId = s.inputDeviceId,
            preferredOutputDeviceId = s.outputDeviceId,
            dspState = _dspState.value
        )
    }

    fun stopAudioEngine() {
        registerInteraction()
        audioEngine.stop()
    }

    fun onPermissionDenied() {
        _statusBannerMessage.value = "Microphone permission denied. Live audio input is disabled."
        audioEngine.stop()
    }

    fun toggleMasterDsp() {
        updateDsp { it.copy(dspEnabled = !it.dspEnabled) }
    }

    // --- Hardware Front-Panel Navigation & Encoder ---

    fun navigateTo(page: DisplayPage) {
        registerInteraction()
        _hardwareState.update { hw ->
            if (hw.currentPage == page) {
                hw.copy(isEditingParam = false)
            } else {
                hw.copy(
                    currentPage = page,
                    backStack = (hw.backStack + hw.currentPage).takeLast(12),
                    focusIndex = 0,
                    isEditingParam = false
                )
            }
        }
    }

    fun navigateBack(): Boolean {
        registerInteraction()
        val hw = _hardwareState.value
        if (hw.isEditingParam) {
            _hardwareState.update { it.copy(isEditingParam = false) }
            return true
        }
        if (hw.currentPage == DisplayPage.HOME) {
            return false
        }
        val prev = hw.backStack.lastOrNull() ?: DisplayPage.HOME
        _hardwareState.update {
            it.copy(
                currentPage = prev,
                backStack = if (it.backStack.isNotEmpty()) it.backStack.dropLast(1) else emptyList(),
                focusIndex = 0,
                isEditingParam = false
            )
        }
        return true
    }

    fun onHardwareKey(key: HardwareKey) {
        registerInteraction()
        _hardwareState.update { it.copy(lastKeyPressed = key) }
        when (key) {
            HardwareKey.MENU -> {
                if (_hardwareState.value.currentPage == DisplayPage.MENU) {
                    navigateTo(DisplayPage.HOME)
                } else {
                    navigateTo(DisplayPage.MENU)
                }
            }
            HardwareKey.BACK -> navigateBack()
            HardwareKey.QUICK_IN_L -> navigateTo(DisplayPage.INPUT_L)
            HardwareKey.QUICK_IN_R -> navigateTo(DisplayPage.INPUT_R)
            HardwareKey.QUICK_XOVER -> navigateTo(DisplayPage.CROSSOVER)
            HardwareKey.QUICK_OUT_L -> navigateTo(DisplayPage.OUTPUT_L)
            HardwareKey.QUICK_OUT_R -> navigateTo(DisplayPage.OUTPUT_R)
            HardwareKey.QUICK_MEDIA -> navigateTo(DisplayPage.MEDIA_YOUTUBE)
            HardwareKey.MUTE -> handleHardwareMuteKey()
            HardwareKey.ENTER -> handleHardwareEnterKey()
            HardwareKey.UP -> handleDirectionalVertical(-1)
            HardwareKey.DOWN -> handleDirectionalVertical(1)
            HardwareKey.LEFT -> handleDirectionalHorizontal(-1)
            HardwareKey.RIGHT -> handleDirectionalHorizontal(1)
        }
    }

    fun onEncoderTurn(steps: Int, angleDeltaDeg: Float) {
        if (steps == 0) return
        registerInteraction()
        val hw = _hardwareState.value
        _hardwareState.update { it.copy(encoderAngleDeg = (it.encoderAngleDeg + angleDeltaDeg) % 3600f) }

        if (hw.currentPage == DisplayPage.MENU) {
            val next = (hw.menuIndex + steps).coerceIn(0, mainMenuEntries.lastIndex)
            _hardwareState.update { it.copy(menuIndex = next) }
            return
        }

        if (hw.isEditingParam) {
            adjustFocusedParameter(steps)
        } else {
            moveFocus(steps)
        }
    }

    fun onEncoderPush() {
        onHardwareKey(HardwareKey.ENTER)
    }

    fun setInputSubTab(tab: InputSubTab) {
        registerInteraction()
        _hardwareState.update { it.copy(inputSubTab = tab, focusIndex = 0, isEditingParam = false) }
    }

    fun setOutputSubTab(tab: OutputSubTab) {
        registerInteraction()
        _hardwareState.update { it.copy(outputSubTab = tab, focusIndex = 0, isEditingParam = false) }
    }

    fun setCrossoverBandTab(bandIndex: Int) {
        registerInteraction()
        _hardwareState.update { it.copy(crossoverBandTab = bandIndex.coerceIn(0, 1), focusIndex = 0, isEditingParam = false) }
    }

    fun selectAndEditParameter(focusIndex: Int, enterEdit: Boolean = true) {
        registerInteraction()
        _hardwareState.update { it.copy(focusIndex = focusIndex, isEditingParam = enterEdit) }
    }

    fun selectEqBand(bandIdx: Int) {
        registerInteraction()
        _hardwareState.update { it.copy(selectedEqBand = bandIdx.coerceIn(0, 4)) }
    }

    fun selectEqField(field: EqParamField) {
        registerInteraction()
        _hardwareState.update { it.copy(eqParamField = field, isEditingParam = true) }
    }

    private fun handleHardwareMuteKey() {
        when (_hardwareState.value.currentPage) {
            DisplayPage.INPUT_L -> updateInputL { it.copy(muted = !it.muted) }
            DisplayPage.INPUT_R -> updateInputR { it.copy(muted = !it.muted) }
            DisplayPage.OUTPUT_L -> updateOutputL { it.copy(muted = !it.muted) }
            DisplayPage.OUTPUT_R -> updateOutputR { it.copy(muted = !it.muted) }
            else -> {
                val bothMuted = _dspState.value.outputL.muted && _dspState.value.outputR.muted
                updateDsp {
                    it.copy(
                        outputL = it.outputL.copy(muted = !bothMuted),
                        outputR = it.outputR.copy(muted = !bothMuted)
                    )
                }
            }
        }
    }

    private fun handleHardwareEnterKey() {
        val hw = _hardwareState.value
        when (hw.currentPage) {
            DisplayPage.MENU -> {
                val entry = mainMenuEntries.getOrNull(hw.menuIndex) ?: return
                navigateTo(entry.targetPage)
            }
            DisplayPage.INPUT_L, DisplayPage.INPUT_R -> {
                if (hw.inputSubTab == InputSubTab.MAIN) {
                    when (hw.focusIndex) {
                        1 -> {
                            if (hw.currentPage == DisplayPage.INPUT_L) updateInputL { it.copy(polarityInverted = !it.polarityInverted) }
                            else updateInputR { it.copy(polarityInverted = !it.polarityInverted) }
                        }
                        2 -> {
                            if (hw.currentPage == DisplayPage.INPUT_L) updateInputL { it.copy(muted = !it.muted) }
                            else updateInputR { it.copy(muted = !it.muted) }
                        }
                        else -> _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                    }
                } else {
                    if (hw.eqParamField == EqParamField.ENABLED) {
                        val isLeft = hw.currentPage == DisplayPage.INPUT_L
                        toggleInputEqBand(isLeft, hw.selectedEqBand)
                    } else {
                        _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                    }
                }
            }
            DisplayPage.CROSSOVER -> {
                if (hw.focusIndex == 3) {
                    val isLow = hw.crossoverBandTab == 0
                    updateCrossoverBand(isLow) { it.copy(bypass = !it.bypass) }
                } else {
                    _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                }
            }
            DisplayPage.OUTPUT_L, DisplayPage.OUTPUT_R -> {
                val isLeft = hw.currentPage == DisplayPage.OUTPUT_L
                when (hw.outputSubTab) {
                    OutputSubTab.MAIN -> when (hw.focusIndex) {
                        4 -> updateOutput(isLeft) { it.copy(polarityInverted = !it.polarityInverted) }
                        5 -> updateOutput(isLeft) { it.copy(muted = !it.muted) }
                        else -> _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                    }
                    OutputSubTab.EQ -> {
                        if (hw.eqParamField == EqParamField.ENABLED) {
                            toggleOutputEqBand(isLeft, hw.selectedEqBand)
                        } else {
                            _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                        }
                    }
                    OutputSubTab.LIMITER -> {
                        if (hw.focusIndex == 0) {
                            updateOutput(isLeft) { it.copy(limiter = it.limiter.copy(enabled = !it.limiter.enabled)) }
                        } else {
                            _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                        }
                    }
                }
            }
            DisplayPage.HOME -> navigateTo(DisplayPage.MENU)
            else -> _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
        }
    }

    private fun handleDirectionalVertical(delta: Int) {
        val hw = _hardwareState.value
        if (hw.currentPage == DisplayPage.MENU) {
            val next = (hw.menuIndex + delta).coerceIn(0, mainMenuEntries.lastIndex)
            _hardwareState.update { it.copy(menuIndex = next) }
            return
        }
        if (hw.isEditingParam) {
            adjustFocusedParameter(-delta)
        } else {
            moveFocus(delta)
        }
    }

    private fun handleDirectionalHorizontal(delta: Int) {
        val hw = _hardwareState.value
        when (hw.currentPage) {
            DisplayPage.INPUT_L, DisplayPage.INPUT_R -> {
                if (hw.inputSubTab == InputSubTab.EQ && hw.isEditingParam) {
                    val fields = EqParamField.entries
                    val nextIdx = (fields.indexOf(hw.eqParamField) + delta).coerceIn(0, fields.lastIndex)
                    _hardwareState.update { it.copy(eqParamField = fields[nextIdx]) }
                } else {
                    val tabs = InputSubTab.entries
                    val nextTab = (tabs.indexOf(hw.inputSubTab) + delta).coerceIn(0, tabs.lastIndex)
                    setInputSubTab(tabs[nextTab])
                }
            }
            DisplayPage.CROSSOVER -> {
                if (!hw.isEditingParam) {
                    setCrossoverBandTab((hw.crossoverBandTab + delta).coerceIn(0, 1))
                } else {
                    adjustFocusedParameter(delta)
                }
            }
            DisplayPage.OUTPUT_L, DisplayPage.OUTPUT_R -> {
                if (hw.outputSubTab == OutputSubTab.EQ && hw.isEditingParam) {
                    val fields = EqParamField.entries
                    val nextIdx = (fields.indexOf(hw.eqParamField) + delta).coerceIn(0, fields.lastIndex)
                    _hardwareState.update { it.copy(eqParamField = fields[nextIdx]) }
                } else {
                    val tabs = OutputSubTab.entries
                    val nextTab = (tabs.indexOf(hw.outputSubTab) + delta).coerceIn(0, tabs.lastIndex)
                    setOutputSubTab(tabs[nextTab])
                }
            }
            else -> {}
        }
    }

    private fun moveFocus(delta: Int) {
        val hw = _hardwareState.value
        val maxIndex = when (hw.currentPage) {
            DisplayPage.INPUT_L, DisplayPage.INPUT_R ->
                if (hw.inputSubTab == InputSubTab.MAIN) 3 else 4
            DisplayPage.CROSSOVER -> 3
            DisplayPage.OUTPUT_L, DisplayPage.OUTPUT_R -> when (hw.outputSubTab) {
                OutputSubTab.MAIN -> 5
                OutputSubTab.EQ -> 4
                OutputSubTab.LIMITER -> 4
            }
            else -> 0
        }
        val next = (hw.focusIndex + delta).coerceIn(0, maxIndex)
        if ((hw.currentPage == DisplayPage.INPUT_L || hw.currentPage == DisplayPage.INPUT_R) && hw.inputSubTab == InputSubTab.EQ) {
            _hardwareState.update { it.copy(focusIndex = next, selectedEqBand = next) }
        } else if ((hw.currentPage == DisplayPage.OUTPUT_L || hw.currentPage == DisplayPage.OUTPUT_R) && hw.outputSubTab == OutputSubTab.EQ) {
            _hardwareState.update { it.copy(focusIndex = next, selectedEqBand = next) }
        } else {
            _hardwareState.update { it.copy(focusIndex = next) }
        }
    }

    private fun adjustFocusedParameter(steps: Int) {
        val hw = _hardwareState.value
        when (hw.currentPage) {
            DisplayPage.INPUT_L -> adjustInputParam(isLeft = true, hw, steps)
            DisplayPage.INPUT_R -> adjustInputParam(isLeft = false, hw, steps)
            DisplayPage.CROSSOVER -> adjustCrossoverParam(hw, steps)
            DisplayPage.OUTPUT_L -> adjustOutputParam(isLeft = true, hw, steps)
            DisplayPage.OUTPUT_R -> adjustOutputParam(isLeft = false, hw, steps)
            else -> {}
        }
    }

    private fun adjustInputParam(isLeft: Boolean, hw: HardwareUiState, steps: Int) {
        if (hw.inputSubTab == InputSubTab.MAIN) {
            when (hw.focusIndex) {
                0 -> updateInput(isLeft) { it.copy(gainDb = it.gainDb + steps * 0.5f) }
                1 -> updateInput(isLeft) { it.copy(polarityInverted = !it.polarityInverted) }
                2 -> updateInput(isLeft) { it.copy(muted = !it.muted) }
                3 -> updateInput(isLeft) { it.copy(delayMs = it.delayMs + steps * 0.10f) }
            }
        } else {
            adjustEqBand(
                bandIndex = hw.selectedEqBand,
                field = hw.eqParamField,
                steps = steps,
                getBands = { if (isLeft) _dspState.value.inputL.eqBands else _dspState.value.inputR.eqBands },
                setBands = { newBands -> updateInput(isLeft) { it.copy(eqBands = newBands) } }
            )
        }
    }

    private fun adjustCrossoverParam(hw: HardwareUiState, steps: Int) {
        val isLow = hw.crossoverBandTab == 0
        when (hw.focusIndex) {
            0 -> updateCrossoverBand(isLow) { band ->
                val factor = if (steps > 0) 1.05f else 0.952f
                var freq = band.frequencyHz
                repeat(kotlin.math.abs(steps)) { freq *= factor }
                band.copy(frequencyHz = freq.coerceIn(20f, 20000f))
            }
            1 -> updateCrossoverBand(isLow) { band ->
                val types = CrossoverFilterType.entries
                val next = (types.indexOf(band.filterType) + if (steps > 0) 1 else -1).mod(types.size)
                band.copy(filterType = types[next])
            }
            2 -> updateCrossoverBand(isLow) { band ->
                val allowed = if (band.filterType == CrossoverFilterType.LINKWITZ_RILEY) {
                    listOf(CrossoverSlope.DB_12, CrossoverSlope.DB_24, CrossoverSlope.DB_48)
                } else {
                    CrossoverSlope.entries
                }
                val idx = allowed.indexOf(band.slope).coerceAtLeast(0)
                val next = (idx + if (steps > 0) 1 else -1).mod(allowed.size)
                band.copy(slope = allowed[next])
            }
            3 -> updateCrossoverBand(isLow) { it.copy(bypass = !it.bypass) }
        }
    }

    private fun adjustOutputParam(isLeft: Boolean, hw: HardwareUiState, steps: Int) {
        when (hw.outputSubTab) {
            OutputSubTab.MAIN -> when (hw.focusIndex) {
                0 -> updateOutput(isLeft) { ch ->
                    val list = OutputSource.entries
                    val next = (list.indexOf(ch.source) + if (steps > 0) 1 else -1).mod(list.size)
                    ch.copy(source = list[next])
                }
                1 -> updateOutput(isLeft) { ch ->
                    val list = CrossoverBand.entries
                    val next = (list.indexOf(ch.band) + if (steps > 0) 1 else -1).mod(list.size)
                    ch.copy(band = list[next])
                }
                2 -> updateOutput(isLeft) { it.copy(gainDb = it.gainDb + steps * 0.5f) }
                3 -> updateOutput(isLeft) { it.copy(delayMs = it.delayMs + steps * 0.10f) }
                4 -> updateOutput(isLeft) { it.copy(polarityInverted = !it.polarityInverted) }
                5 -> updateOutput(isLeft) { it.copy(muted = !it.muted) }
            }
            OutputSubTab.EQ -> adjustEqBand(
                bandIndex = hw.selectedEqBand,
                field = hw.eqParamField,
                steps = steps,
                getBands = { if (isLeft) _dspState.value.outputL.eqBands else _dspState.value.outputR.eqBands },
                setBands = { newBands -> updateOutput(isLeft) { it.copy(eqBands = newBands) } }
            )
            OutputSubTab.LIMITER -> when (hw.focusIndex) {
                0 -> updateOutput(isLeft) { it.copy(limiter = it.limiter.copy(enabled = !it.limiter.enabled)) }
                1 -> updateOutput(isLeft) { it.copy(limiter = it.limiter.copy(thresholdDbfs = it.limiter.thresholdDbfs + steps * 0.5f)) }
                2 -> updateOutput(isLeft) { it.copy(limiter = it.limiter.copy(attackMs = it.limiter.attackMs + steps * 0.5f)) }
                3 -> updateOutput(isLeft) { it.copy(limiter = it.limiter.copy(releaseMs = it.limiter.releaseMs + steps * 10f)) }
                4 -> updateOutput(isLeft) { it.copy(limiter = it.limiter.copy(makeupGainDb = it.limiter.makeupGainDb + steps * 0.5f)) }
            }
        }
    }

    private fun adjustEqBand(
        bandIndex: Int,
        field: EqParamField,
        steps: Int,
        getBands: () -> List<EqBandConfig>,
        setBands: (List<EqBandConfig>) -> Unit
    ) {
        val current = getBands().toMutableList()
        val band = current.getOrNull(bandIndex) ?: return
        val updated = when (field) {
            EqParamField.ENABLED -> band.copy(enabled = !band.enabled)
            EqParamField.TYPE -> {
                val types = EqFilterType.entries
                val next = (types.indexOf(band.filterType) + if (steps > 0) 1 else -1).mod(types.size)
                band.copy(filterType = types[next])
            }
            EqParamField.FREQ -> {
                val factor = if (steps > 0) 1.05f else 0.952f
                var f = band.frequencyHz
                repeat(kotlin.math.abs(steps)) { f *= factor }
                band.copy(frequencyHz = f.coerceIn(20f, 20000f))
            }
            EqParamField.GAIN -> band.copy(gainDb = band.gainDb + steps * 0.5f)
            EqParamField.Q -> band.copy(q = band.q + steps * 0.1f)
        }
        current[bandIndex] = updated.validated()
        setBands(current)
    }

    // --- Direct DSP Mutations (Touch + Hardware share same single source of truth) ---

    fun updateInputL(block: (InputChannelConfig) -> InputChannelConfig) = updateInput(true, block)
    fun updateInputR(block: (InputChannelConfig) -> InputChannelConfig) = updateInput(false, block)

    fun updateInput(isLeft: Boolean, block: (InputChannelConfig) -> InputChannelConfig) {
        updateDsp { state ->
            if (isLeft) state.copy(inputL = block(state.inputL))
            else state.copy(inputR = block(state.inputR))
        }
    }

    fun toggleInputEqBand(isLeft: Boolean, index: Int) {
        updateInput(isLeft) { ch ->
            val updated = ch.eqBands.mapIndexed { i, b -> if (i == index) b.copy(enabled = !b.enabled) else b }
            ch.copy(eqBands = updated)
        }
    }

    fun updateInputEqBand(isLeft: Boolean, index: Int, block: (EqBandConfig) -> EqBandConfig) {
        updateInput(isLeft) { ch ->
            val updated = ch.eqBands.mapIndexed { i, b -> if (i == index) block(b) else b }
            ch.copy(eqBands = updated)
        }
    }

    fun updateCrossoverBypass(bypass: Boolean) {
        updateDsp { it.copy(crossover = it.crossover.copy(bypass = bypass)) }
    }

    fun updateCrossoverBand(isLow: Boolean, block: (CrossoverBandConfig) -> CrossoverBandConfig) {
        updateDsp { state ->
            val xo = state.crossover
            state.copy(
                crossover = if (isLow) xo.copy(low = block(xo.low)) else xo.copy(high = block(xo.high))
            )
        }
    }

    fun updateOutputL(block: (OutputChannelConfig) -> OutputChannelConfig) = updateOutput(true, block)
    fun updateOutputR(block: (OutputChannelConfig) -> OutputChannelConfig) = updateOutput(false, block)

    fun updateOutput(isLeft: Boolean, block: (OutputChannelConfig) -> OutputChannelConfig) {
        updateDsp { state ->
            if (isLeft) state.copy(outputL = block(state.outputL))
            else state.copy(outputR = block(state.outputR))
        }
    }

    fun toggleOutputEqBand(isLeft: Boolean, index: Int) {
        updateOutput(isLeft) { ch ->
            val updated = ch.eqBands.mapIndexed { i, b -> if (i == index) b.copy(enabled = !b.enabled) else b }
            ch.copy(eqBands = updated)
        }
    }

    fun updateOutputEqBand(isLeft: Boolean, index: Int, block: (EqBandConfig) -> EqBandConfig) {
        updateOutput(isLeft) { ch ->
            val updated = ch.eqBands.mapIndexed { i, b -> if (i == index) block(b) else b }
            ch.copy(eqBands = updated)
        }
    }

    fun updateOutputLimiter(isLeft: Boolean, block: (LimiterConfig) -> LimiterConfig) {
        updateOutput(isLeft) { ch -> ch.copy(limiter = block(ch.limiter)) }
    }

    private fun updateDsp(transform: (DspState) -> DspState) {
        val next = transform(_dspState.value).validated()
        _dspState.value = next
        audioEngine.updateDspConfig(next)
        schedulePersistLastState(next)
    }

    private fun schedulePersistLastState(state: DspState) {
        saveDebounceJob?.cancel()
        saveDebounceJob = viewModelScope.launch {
            delay(350L)
            settingsStore.saveLastDspState(_activePresetName.value, presetRepo.encodeState(state))
        }
    }

    // --- Preset Actions ---

    fun saveCurrentPreset(name: String) {
        viewModelScope.launch {
            val res = presetRepo.savePreset(name, _dspState.value)
            res.onSuccess { savedName ->
                _activePresetName.value = savedName
                settingsStore.saveLastDspState(savedName, presetRepo.encodeState(_dspState.value))
                _statusBannerMessage.value = "Preset '$savedName' saved locally."
            }.onFailure { err ->
                _statusBannerMessage.value = err.message ?: "Failed to save preset."
            }
        }
    }

    fun loadPreset(preset: DlmsPreset) {
        viewModelScope.launch {
            val validated = preset.dspState.validated()
            _dspState.value = validated
            _activePresetName.value = preset.name
            audioEngine.updateDspConfig(validated)
            settingsStore.saveLastDspState(preset.name, presetRepo.encodeState(validated))
            _statusBannerMessage.value = "Loaded preset '${preset.name}'."
        }
    }

    fun renamePreset(preset: DlmsPreset, newName: String) {
        viewModelScope.launch {
            val res = presetRepo.renamePreset(preset.id, newName)
            res.onSuccess { updated ->
                if (_activePresetName.value == preset.name) {
                    _activePresetName.value = updated
                    settingsStore.saveLastDspState(updated, presetRepo.encodeState(_dspState.value))
                }
                _statusBannerMessage.value = "Renamed to '$updated'."
            }.onFailure { err ->
                _statusBannerMessage.value = err.message ?: "Rename failed."
            }
        }
    }

    fun deletePreset(preset: DlmsPreset) {
        viewModelScope.launch {
            val res = presetRepo.deletePreset(preset.id)
            res.onSuccess {
                if (_activePresetName.value == preset.name) {
                    _activePresetName.value = PresetRepository.DEFAULT_PRESET_NAME
                }
                _statusBannerMessage.value = "Deleted preset '${preset.name}'."
            }.onFailure { err ->
                _statusBannerMessage.value = err.message ?: "Delete failed."
            }
        }
    }

    fun resetToDefaultPreset() {
        viewModelScope.launch {
            val neutral = DspState.neutralDefault()
            _dspState.value = neutral
            _activePresetName.value = PresetRepository.DEFAULT_PRESET_NAME
            audioEngine.updateDspConfig(neutral)
            settingsStore.saveLastDspState(PresetRepository.DEFAULT_PRESET_NAME, presetRepo.encodeState(neutral))
            _statusBannerMessage.value = "DSP reset to neutral DEFAULT preset."
        }
    }

    // --- Settings Actions ---

    fun setTheme(theme: RackThemeStyle) = viewModelScope.launch { settingsStore.updateTheme(theme) }
    fun setBrightness(value: Float) = viewModelScope.launch { settingsStore.updateBrightness(value) }
    fun setTimeout(option: DisplayTimeoutOption) = viewModelScope.launch { settingsStore.updateTimeout(option) }
    fun setMeterStyle(style: MeterStyleOption) = viewModelScope.launch { settingsStore.updateMeterStyle(style) }

    fun setInputDevice(deviceId: Int?) {
        viewModelScope.launch {
            settingsStore.updateInputDeviceId(deviceId)
            restartAudioIfRunning()
        }
    }

    fun setOutputDevice(deviceId: Int?) {
        viewModelScope.launch {
            settingsStore.updateOutputDeviceId(deviceId)
            restartAudioIfRunning()
        }
    }

    fun setSampleRate(rate: Int) {
        viewModelScope.launch {
            settingsStore.updateSampleRate(rate)
            restartAudioIfRunning()
        }
    }

    fun setBufferMultiplier(multiplier: Int) {
        viewModelScope.launch {
            settingsStore.updateBufferMultiplier(multiplier)
            restartAudioIfRunning()
        }
    }

    fun resetApplication() {
        viewModelScope.launch {
            audioEngine.stop()
            presetRepo.resetAllUserPresets()
            settingsStore.resetAll()
            val neutral = DspState.neutralDefault()
            _dspState.value = neutral
            _activePresetName.value = PresetRepository.DEFAULT_PRESET_NAME
            audioEngine.updateDspConfig(neutral)
            _statusBannerMessage.value = "Application reset to factory settings."
        }
    }

    private fun restartAudioIfRunning() {
        if (audioEngine.engineState.value.status == com.example.audio.AudioEngineStatus.READY) {
            audioEngine.stop()
            startAudioEngine()
        }
    }

    // --- YouTube / Media Actions ---

    fun updateYouTubeSearchQuery(query: String) {
        _youtubeState.update { it.copy(searchQuery = query, errorMessage = null) }
    }

    fun submitYouTubeQuery(queryInput: String) {
        registerInteraction()
        val trimmed = queryInput.trim()

        if (!checkNetworkConnected()) {
            _youtubeState.update {
                it.copy(
                    isOnline = false,
                    errorMessage = "YouTube unavailable: No internet connection detected."
                )
            }
            return
        }

        val parsedId = YouTubeUrlParser.extractVideoId(trimmed)
        if (parsedId != null) {
            _youtubeState.update {
                it.copy(
                    isOnline = true,
                    viewMode = YouTubeViewMode.DIRECT_YOUTUBE,
                    activeVideoId = parsedId,
                    submittedQuery = "",
                    navigationNonce = it.navigationNonce + 1L,
                    activeVideoTitle = "Video ID: $parsedId",
                    errorMessage = null
                )
            }
            return
        }

        _youtubeState.update {
            it.copy(
                isOnline = true,
                searchQuery = trimmed,
                submittedQuery = trimmed,
                navigationNonce = it.navigationNonce + 1L,
                viewMode = YouTubeViewMode.DIRECT_YOUTUBE,
                errorMessage = null
            )
        }
    }

    fun onYouTubeVideoDetected(videoId: String) {
        _youtubeState.update {
            it.copy(
                activeVideoId = videoId,
                activeVideoTitle = "YouTube ($videoId)",
                errorMessage = null
            )
        }
    }

    fun onYouTubeCanGoBackChanged(canGoBack: Boolean) {
        _youtubeState.update { it.copy(canGoBack = canGoBack) }
    }

    fun goYouTubeHome() {
        registerInteraction()
        _youtubeState.update {
            it.copy(
                viewMode = YouTubeViewMode.DIRECT_YOUTUBE,
                submittedQuery = "",
                activeVideoId = null,
                navigationNonce = it.navigationNonce + 1L,
                errorMessage = null
            )
        }
    }

    fun setYouTubeViewMode(mode: YouTubeViewMode) {
        _youtubeState.update {
            it.copy(
                viewMode = mode,
                navigationNonce = it.navigationNonce + 1L,
                errorMessage = null
            )
        }
    }

    fun onYouTubePlaybackStatus(status: YouTubePlaybackStatus) {
        _youtubeState.update { it.copy(playbackStatus = status) }
    }

    fun onYouTubeProgress(currentSec: Float, durationSec: Float) {
        _youtubeState.update { it.copy(currentTimeSec = currentSec, durationSec = durationSec) }
    }

    fun onYouTubeError(message: String) {
        _youtubeState.update {
            it.copy(
                playbackStatus = YouTubePlaybackStatus.ERROR,
                errorMessage = message
            )
        }
    }

    fun setYouTubeMuted(muted: Boolean) {
        _youtubeState.update { it.copy(isMuted = muted) }
    }

    fun setYouTubeVolume(volume: Int) {
        _youtubeState.update { it.copy(volumePercent = volume.coerceIn(0, 100)) }
    }

    private fun checkNetworkConnected(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        super.onCleared()
        // Persist immediately if needed; audioEngine lives in Application so rotation won't drop audio
    }
}
