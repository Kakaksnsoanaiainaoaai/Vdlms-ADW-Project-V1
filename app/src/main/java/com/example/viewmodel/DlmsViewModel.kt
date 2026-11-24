package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DlmsApplication
import com.example.audio.AudioEngineState
import com.example.audio.AudioEngineStatus
import com.example.audio.AudioHardwareCapabilities
import com.example.audio.AudioSourceType
import com.example.dsp.AudioTelemetry
import com.example.dsp.ChannelDspConfig
import com.example.dsp.CrossoverBand
import com.example.dsp.CrossoverBandConfig
import com.example.dsp.CrossoverFilterType
import com.example.dsp.CrossoverSlope
import com.example.dsp.DspState
import com.example.dsp.EqBandConfig
import com.example.dsp.EqFilterType
import com.example.dsp.LimiterConfig
import com.example.hardware.ChannelDspSubTab
import com.example.hardware.DisplayPage
import com.example.hardware.EqParamField
import com.example.hardware.HardwareKey
import com.example.hardware.HardwareUiState
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
import kotlin.math.abs

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
            audioEngine.selectSource(savedSettings.selectedAudioSource)
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

    fun selectAudioSource(source: AudioSourceType) {
        registerInteraction()
        viewModelScope.launch {
            settingsStore.updateAudioSource(source)
        }
        audioEngine.selectSource(source)
        _statusBannerMessage.value = if (source == AudioSourceType.YOUTUBE) {
            "YOUTUBE AUDIO ROUTING: NOT AVAILABLE"
        } else {
            "SOURCE: ${source.displayName}"
        }
    }

    fun onInternalMediaFilePicked(uri: Uri) {
        registerInteraction()
        viewModelScope.launch {
            settingsStore.updateAudioSource(AudioSourceType.INTERNAL_MEDIA)
        }
        val s = settingsState.value
        audioEngine.loadInternalMedia(
            uri = uri,
            preferredOutputDeviceId = s.outputDeviceId,
            bufferMultiplier = s.bufferMultiplier
        )
    }

    fun toggleInternalMediaPlayPause() {
        registerInteraction()
        val s = settingsState.value
        audioEngine.toggleInternalMediaPlayPause(
            preferredOutputDeviceId = s.outputDeviceId,
            bufferMultiplier = s.bufferMultiplier
        )
    }

    fun seekInternalMedia(posMs: Long) {
        registerInteraction()
        audioEngine.seekInternalMedia(posMs)
    }

    fun toggleInternalMediaLoop() {
        registerInteraction()
        audioEngine.toggleInternalMediaLoop()
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

    fun navigateTo(page: DisplayPage, subTab: ChannelDspSubTab? = null) {
        registerInteraction()
        _hardwareState.update { hw ->
            val resolvedTab = subTab ?: hw.channelSubTab
            if (hw.currentPage == page) {
                hw.copy(channelSubTab = resolvedTab, isEditingParam = false)
            } else {
                hw.copy(
                    currentPage = page,
                    backStack = (hw.backStack + hw.currentPage).takeLast(12),
                    channelSubTab = resolvedTab,
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
            HardwareKey.QUICK_SOURCE -> navigateTo(DisplayPage.AUDIO_SOURCE)
            HardwareKey.QUICK_IN_L -> navigateTo(DisplayPage.CHANNEL_L, ChannelDspSubTab.MAIN)
            HardwareKey.QUICK_IN_R -> navigateTo(DisplayPage.CHANNEL_R, ChannelDspSubTab.MAIN)
            HardwareKey.QUICK_XOVER -> navigateTo(DisplayPage.CROSSOVER)
            HardwareKey.QUICK_OUT_L -> navigateTo(DisplayPage.CHANNEL_L, ChannelDspSubTab.LIMITER)
            HardwareKey.QUICK_OUT_R -> navigateTo(DisplayPage.CHANNEL_R, ChannelDspSubTab.LIMITER)
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

    fun setChannelSubTab(tab: ChannelDspSubTab) {
        registerInteraction()
        _hardwareState.update { it.copy(channelSubTab = tab, focusIndex = 0, isEditingParam = false) }
    }

    fun setCrossoverChannelTab(channelIdx: Int) {
        registerInteraction()
        _hardwareState.update {
            it.copy(crossoverChannelTab = channelIdx.coerceIn(0, 1), focusIndex = 0, isEditingParam = false)
        }
    }

    fun setCrossoverBandTab(bandIndex: Int) {
        registerInteraction()
        _hardwareState.update {
            it.copy(crossoverBandTab = bandIndex.coerceIn(0, 1), focusIndex = 0, isEditingParam = false)
        }
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
            DisplayPage.CHANNEL_L -> updateChannelL { it.copy(muted = !it.muted) }
            DisplayPage.CHANNEL_R -> updateChannelR { it.copy(muted = !it.muted) }
            else -> {
                val bothMuted = _dspState.value.channelL.muted && _dspState.value.channelR.muted
                updateDsp {
                    it.copy(
                        channelL = it.channelL.copy(muted = !bothMuted),
                        channelR = it.channelR.copy(muted = !bothMuted)
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
            DisplayPage.CHANNEL_L, DisplayPage.CHANNEL_R -> {
                val isLeft = hw.currentPage == DisplayPage.CHANNEL_L
                when (hw.channelSubTab) {
                    ChannelDspSubTab.MAIN -> when (hw.focusIndex) {
                        1 -> updateChannel(isLeft) { it.copy(inputPolarityInverted = !it.inputPolarityInverted) }
                        4 -> updateChannel(isLeft) { it.copy(muted = !it.muted) }
                        else -> _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                    }
                    ChannelDspSubTab.EQ -> {
                        if (hw.eqParamField == EqParamField.ENABLED) {
                            toggleChannelEqBand(isLeft, hw.selectedEqBand)
                        } else {
                            _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                        }
                    }
                    ChannelDspSubTab.XOVER -> {
                        if (hw.focusIndex == 3) {
                            val isLow = hw.crossoverBandTab == 0
                            updateChannelCrossoverBand(isLeft, isLow) { it.copy(bypass = !it.bypass) }
                        } else {
                            _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                        }
                    }
                    ChannelDspSubTab.LIMITER -> {
                        if (hw.focusIndex == 0) {
                            updateChannelLimiter(isLeft) { it.copy(enabled = !it.enabled) }
                        } else {
                            _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
                        }
                    }
                }
            }
            DisplayPage.CROSSOVER -> {
                val isLeft = hw.crossoverChannelTab == 0
                if (hw.focusIndex == 3) {
                    val isLow = hw.crossoverBandTab == 0
                    updateChannelCrossoverBand(isLeft, isLow) { it.copy(bypass = !it.bypass) }
                } else {
                    _hardwareState.update { it.copy(isEditingParam = !it.isEditingParam) }
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
            DisplayPage.CHANNEL_L, DisplayPage.CHANNEL_R -> {
                if (hw.channelSubTab == ChannelDspSubTab.EQ && hw.isEditingParam) {
                    val fields = EqParamField.entries
                    val nextIdx = (fields.indexOf(hw.eqParamField) + delta).coerceIn(0, fields.lastIndex)
                    _hardwareState.update { it.copy(eqParamField = fields[nextIdx]) }
                } else if (hw.channelSubTab == ChannelDspSubTab.XOVER && hw.isEditingParam) {
                    adjustFocusedParameter(delta)
                } else {
                    val tabs = ChannelDspSubTab.entries
                    val nextTab = (tabs.indexOf(hw.channelSubTab) + delta).coerceIn(0, tabs.lastIndex)
                    setChannelSubTab(tabs[nextTab])
                }
            }
            DisplayPage.CROSSOVER -> {
                if (!hw.isEditingParam) {
                    setCrossoverBandTab((hw.crossoverBandTab + delta).coerceIn(0, 1))
                } else {
                    adjustFocusedParameter(delta)
                }
            }
            else -> {}
        }
    }

    private fun moveFocus(delta: Int) {
        val hw = _hardwareState.value
        val maxIndex = when (hw.currentPage) {
            DisplayPage.CHANNEL_L, DisplayPage.CHANNEL_R -> when (hw.channelSubTab) {
                ChannelDspSubTab.MAIN -> 4
                ChannelDspSubTab.EQ -> 4
                ChannelDspSubTab.XOVER -> 3
                ChannelDspSubTab.LIMITER -> 4
            }
            DisplayPage.CROSSOVER -> 3
            else -> 0
        }
        val next = (hw.focusIndex + delta).coerceIn(0, maxIndex)
        if ((hw.currentPage == DisplayPage.CHANNEL_L || hw.currentPage == DisplayPage.CHANNEL_R) &&
            hw.channelSubTab == ChannelDspSubTab.EQ
        ) {
            _hardwareState.update { it.copy(focusIndex = next, selectedEqBand = next) }
        } else {
            _hardwareState.update { it.copy(focusIndex = next) }
        }
    }

    private fun adjustFocusedParameter(steps: Int) {
        val hw = _hardwareState.value
        when (hw.currentPage) {
            DisplayPage.CHANNEL_L -> adjustChannelParam(isLeft = true, hw, steps)
            DisplayPage.CHANNEL_R -> adjustChannelParam(isLeft = false, hw, steps)
            DisplayPage.CROSSOVER -> adjustCrossoverParam(isLeft = hw.crossoverChannelTab == 0, hw, steps)
            else -> {}
        }
    }

    private fun adjustChannelParam(isLeft: Boolean, hw: HardwareUiState, steps: Int) {
        when (hw.channelSubTab) {
            ChannelDspSubTab.MAIN -> when (hw.focusIndex) {
                0 -> updateChannel(isLeft) { it.copy(inputGainDb = it.inputGainDb + steps * 0.5f) }
                1 -> updateChannel(isLeft) { it.copy(inputPolarityInverted = !it.inputPolarityInverted) }
                2 -> updateChannel(isLeft) { it.copy(delayMs = it.delayMs + steps * 0.10f) }
                3 -> updateChannel(isLeft) { it.copy(outputGainDb = it.outputGainDb + steps * 0.5f) }
                4 -> updateChannel(isLeft) { it.copy(muted = !it.muted) }
            }
            ChannelDspSubTab.EQ -> adjustEqBand(
                bandIndex = hw.selectedEqBand,
                field = hw.eqParamField,
                steps = steps,
                getBands = { if (isLeft) _dspState.value.channelL.eqBands else _dspState.value.channelR.eqBands },
                setBands = { newBands -> updateChannel(isLeft) { it.copy(eqBands = newBands) } }
            )
            ChannelDspSubTab.XOVER -> adjustCrossoverParam(isLeft, hw, steps)
            ChannelDspSubTab.LIMITER -> when (hw.focusIndex) {
                0 -> updateChannelLimiter(isLeft) { it.copy(enabled = !it.enabled) }
                1 -> updateChannelLimiter(isLeft) { it.copy(thresholdDbfs = it.thresholdDbfs + steps * 0.5f) }
                2 -> updateChannelLimiter(isLeft) { it.copy(attackMs = it.attackMs + steps * 0.5f) }
                3 -> updateChannelLimiter(isLeft) { it.copy(releaseMs = it.releaseMs + steps * 10f) }
                4 -> updateChannelLimiter(isLeft) { it.copy(makeupGainDb = it.makeupGainDb + steps * 0.5f) }
            }
        }
    }

    private fun adjustCrossoverParam(isLeft: Boolean, hw: HardwareUiState, steps: Int) {
        val isLow = hw.crossoverBandTab == 0
        when (hw.focusIndex) {
            0 -> updateChannelCrossoverBand(isLeft, isLow) { band ->
                val factor = if (steps > 0) 1.05f else 0.952f
                var freq = band.frequencyHz
                repeat(abs(steps)) { freq *= factor }
                band.copy(frequencyHz = freq.coerceIn(20f, 20000f))
            }
            1 -> updateChannelCrossoverBand(isLeft, isLow) { band ->
                val types = CrossoverFilterType.entries
                val next = (types.indexOf(band.filterType) + if (steps > 0) 1 else -1).mod(types.size)
                band.copy(filterType = types[next])
            }
            2 -> updateChannelCrossoverBand(isLeft, isLow) { band ->
                val allowed = if (band.filterType == CrossoverFilterType.LINKWITZ_RILEY) {
                    listOf(CrossoverSlope.DB_12, CrossoverSlope.DB_24, CrossoverSlope.DB_48)
                } else {
                    CrossoverSlope.entries
                }
                val idx = allowed.indexOf(band.slope).coerceAtLeast(0)
                val next = (idx + if (steps > 0) 1 else -1).mod(allowed.size)
                band.copy(slope = allowed[next])
            }
            3 -> updateChannelCrossoverBand(isLeft, isLow) { it.copy(bypass = !it.bypass) }
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
                repeat(abs(steps)) { f *= factor }
                band.copy(frequencyHz = f.coerceIn(20f, 20000f))
            }
            EqParamField.GAIN -> band.copy(gainDb = band.gainDb + steps * 0.5f)
            EqParamField.Q -> band.copy(q = band.q + steps * 0.1f)
        }
        current[bandIndex] = updated.validated()
        setBands(current)
    }

    // --- Strictly Separated Per-Channel DSP Mutations ---

    fun updateChannelL(block: (ChannelDspConfig) -> ChannelDspConfig) = updateChannel(true, block)
    fun updateChannelR(block: (ChannelDspConfig) -> ChannelDspConfig) = updateChannel(false, block)

    fun updateChannel(isLeft: Boolean, block: (ChannelDspConfig) -> ChannelDspConfig) {
        updateDsp { state ->
            if (isLeft) state.copy(channelL = block(state.channelL))
            else state.copy(channelR = block(state.channelR))
        }
    }

    fun toggleChannelEqBand(isLeft: Boolean, index: Int) {
        updateChannel(isLeft) { ch ->
            val updated = ch.eqBands.mapIndexed { i, b -> if (i == index) b.copy(enabled = !b.enabled) else b }
            ch.copy(eqBands = updated)
        }
    }

    fun updateChannelEqBand(isLeft: Boolean, index: Int, block: (EqBandConfig) -> EqBandConfig) {
        updateChannel(isLeft) { ch ->
            val updated = ch.eqBands.mapIndexed { i, b -> if (i == index) block(b) else b }
            ch.copy(eqBands = updated)
        }
    }

    fun updateChannelCrossoverBandSelector(isLeft: Boolean, band: CrossoverBand) {
        updateChannel(isLeft) { it.copy(activeBand = band) }
    }

    fun updateChannelCrossoverBypass(isLeft: Boolean, bypass: Boolean) {
        updateChannel(isLeft) { it.copy(crossover = it.crossover.copy(bypass = bypass)) }
    }

    fun updateChannelCrossoverBand(isLeft: Boolean, isLow: Boolean, block: (CrossoverBandConfig) -> CrossoverBandConfig) {
        updateChannel(isLeft) { ch ->
            val xo = ch.crossover
            ch.copy(
                crossover = if (isLow) xo.copy(low = block(xo.low)) else xo.copy(high = block(xo.high))
            )
        }
    }

    fun updateChannelLimiter(isLeft: Boolean, block: (LimiterConfig) -> LimiterConfig) {
        updateChannel(isLeft) { ch -> ch.copy(limiter = block(ch.limiter)) }
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
                _statusBannerMessage.value = "Preset '$savedName' saved locally (L/R separate)."
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
            audioEngine.selectSource(AudioSourceType.DEVICE_INPUT)
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
        if (audioEngine.engineState.value.status == AudioEngineStatus.READY) {
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
}
