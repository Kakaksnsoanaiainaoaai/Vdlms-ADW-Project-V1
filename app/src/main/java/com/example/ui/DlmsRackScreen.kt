package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioEngineState
import com.example.audio.AudioEngineStatus
import com.example.dsp.AudioTelemetry
import com.example.dsp.DspState
import com.example.hardware.DisplayPage
import com.example.hardware.HardwareKey
import com.example.hardware.HardwareUiState
import com.example.ui.components.HardwareLcdBezel
import com.example.ui.components.HardwarePushButton
import com.example.ui.components.HardwareRoundDirectionKey
import com.example.ui.components.HardwareStatusLed
import com.example.ui.components.RackMountingEar
import com.example.ui.components.RotaryEncoderKnob
import com.example.ui.screens.DisplayCrossoverScreen
import com.example.ui.screens.DisplayHomeScreen
import com.example.ui.screens.DisplayInputScreen
import com.example.ui.screens.DisplayMenuScreen
import com.example.ui.screens.DisplayOutputScreen
import com.example.ui.screens.DisplayPresetsScreen
import com.example.ui.screens.DisplaySettingsScreen
import com.example.ui.screens.LcdSystemStatusBar
import com.example.ui.screens.MediaYouTubeScreen
import com.example.ui.theme.LocalDlmsPalette
import com.example.ui.theme.SpaceGroteskFamily
import com.example.viewmodel.DlmsViewModel
import kotlinx.coroutines.delay

@Composable
fun DlmsRackScreen(viewModel: DlmsViewModel) {
    val palette = LocalDlmsPalette.current
    val context = LocalContext.current

    val dspState by viewModel.dspState.collectAsStateWithLifecycle()
    val hwState by viewModel.hardwareState.collectAsStateWithLifecycle()
    val audioState by viewModel.audioEngineState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val activePresetName by viewModel.activePresetName.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val youtubeState by viewModel.youtubeState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startAudioEngine()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    val startAudioWithPermission: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startAudioEngine()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = hwState.currentPage != DisplayPage.HOME || hwState.isEditingParam) {
        viewModel.navigateBack()
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(3500L)
            viewModel.clearBannerMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.chassisBg, palette.chassisSurface, palette.chassisBg)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { viewModel.registerInteraction() })
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight

            if (isLandscape) {
                LandscapeRackLayout(
                    dspState = dspState,
                    hwState = hwState,
                    audioState = audioState,
                    telemetry = telemetry,
                    viewModel = viewModel,
                    displayContent = {
                        DisplayViewport(
                            viewModel = viewModel,
                            dspState = dspState,
                            hwState = hwState,
                            audioState = audioState,
                            telemetry = telemetry,
                            settings = settings,
                            capabilities = capabilities,
                            presets = presets,
                            activePresetName = activePresetName,
                            youtubeState = youtubeState,
                            onStartAudio = startAudioWithPermission
                        )
                    }
                )
            } else {
                PortraitRackLayout(
                    dspState = dspState,
                    hwState = hwState,
                    audioState = audioState,
                    telemetry = telemetry,
                    viewModel = viewModel,
                    displayContent = {
                        DisplayViewport(
                            viewModel = viewModel,
                            dspState = dspState,
                            hwState = hwState,
                            audioState = audioState,
                            telemetry = telemetry,
                            settings = settings,
                            capabilities = capabilities,
                            presets = presets,
                            activePresetName = activePresetName,
                            youtubeState = youtubeState,
                            onStartAudio = startAudioWithPermission
                        )
                    }
                )
            }
        }

        // Transient Hardware Toast Banner
        AnimatedVisibility(
            visible = bannerMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, start = 24.dp, end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.lcdBg.copy(alpha = 0.95f))
                    .border(1.dp, palette.lcdSecondary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = bannerMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.lcdSecondary
                )
            }
        }
    }
}

@Composable
private fun LandscapeRackLayout(
    dspState: DspState,
    hwState: HardwareUiState,
    audioState: AudioEngineState,
    telemetry: AudioTelemetry,
    viewModel: DlmsViewModel,
    displayContent: @Composable () -> Unit
) {
    val palette = LocalDlmsPalette.current

    Row(modifier = Modifier.fillMaxSize()) {
        RackMountingEar()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // Top Chassis Silkscreen Header + Real Hardware LEDs
            ChassisTopSilkscreen(
                dspState = dspState,
                audioState = audioState,
                telemetry = telemetry
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Main Rack Faceplate: Left Hardware Controls | Center LCD | Right Nav & Rotary Encoder
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT HARDWARE PANEL (Channel Select + Mute + Media)
                Column(
                    modifier = Modifier
                        .width(126.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.chassisSurface.copy(alpha = 0.7f))
                        .border(1.dp, palette.buttonBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(5.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CH SELECT",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.silkscreenText.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HardwarePushButton(
                            label = "IN L",
                            isActive = hwState.currentPage == DisplayPage.INPUT_L,
                            ledColor = if (dspState.inputL.muted) palette.meterRed else palette.lcdPrimary,
                            onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_IN_L) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        HardwarePushButton(
                            label = "IN R",
                            isActive = hwState.currentPage == DisplayPage.INPUT_R,
                            ledColor = if (dspState.inputR.muted) palette.meterRed else palette.lcdPrimary,
                            onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_IN_R) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HardwarePushButton(
                        label = "X-OVER",
                        isActive = hwState.currentPage == DisplayPage.CROSSOVER,
                        ledColor = palette.lcdSecondary,
                        onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_XOVER) },
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HardwarePushButton(
                            label = "OUT L",
                            isActive = hwState.currentPage == DisplayPage.OUTPUT_L,
                            ledColor = if (dspState.outputL.muted) palette.meterRed else palette.meterGreen,
                            onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_OUT_L) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        HardwarePushButton(
                            label = "OUT R",
                            isActive = hwState.currentPage == DisplayPage.OUTPUT_R,
                            ledColor = if (dspState.outputR.muted) palette.meterRed else palette.meterGreen,
                            onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_OUT_R) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val muteLit = when (hwState.currentPage) {
                            DisplayPage.INPUT_L -> dspState.inputL.muted
                            DisplayPage.INPUT_R -> dspState.inputR.muted
                            DisplayPage.OUTPUT_L -> dspState.outputL.muted
                            DisplayPage.OUTPUT_R -> dspState.outputR.muted
                            else -> dspState.outputL.muted && dspState.outputR.muted
                        }
                        HardwarePushButton(
                            label = "MUTE",
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            isActive = muteLit,
                            ledColor = palette.meterRed,
                            onClick = { viewModel.onHardwareKey(HardwareKey.MUTE) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        HardwarePushButton(
                            label = "YT",
                            icon = Icons.Default.PlayCircle,
                            isActive = hwState.currentPage == DisplayPage.MEDIA_YOUTUBE,
                            ledColor = palette.lcdSecondary,
                            onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_MEDIA) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // CENTER RECESSED LCD/TFT DISPLAY
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    displayContent()
                }

                // RIGHT HARDWARE PANEL (Nav Cluster + Virtual Rotary Encoder)
                Column(
                    modifier = Modifier
                        .width(152.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.chassisSurface.copy(alpha = 0.7f))
                        .border(1.dp, palette.buttonBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HardwarePushButton(
                            label = "MENU",
                            icon = Icons.Default.Menu,
                            isActive = hwState.currentPage == DisplayPage.MENU,
                            onClick = { viewModel.onHardwareKey(HardwareKey.MENU) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        HardwarePushButton(
                            label = "BACK",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = { viewModel.onHardwareKey(HardwareKey.BACK) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    RotaryEncoderKnob(
                        angleDeg = hwState.encoderAngleDeg,
                        isEditing = hwState.isEditingParam,
                        onRotateSteps = { steps, delta -> viewModel.onEncoderTurn(steps, delta) },
                        onPushEnter = { viewModel.onEncoderPush() },
                        sizeDp = 76.dp
                    )

                    // D-Pad + ENTER
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HardwareRoundDirectionKey(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Left",
                                onClick = { viewModel.onHardwareKey(HardwareKey.LEFT) },
                                testTag = "hw_btn_left",
                                sizeDp = 34.dp
                            )
                            HardwareRoundDirectionKey(
                                icon = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Up",
                                onClick = { viewModel.onHardwareKey(HardwareKey.UP) },
                                testTag = "hw_btn_up",
                                sizeDp = 34.dp
                            )
                            HardwareRoundDirectionKey(
                                icon = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Down",
                                onClick = { viewModel.onHardwareKey(HardwareKey.DOWN) },
                                testTag = "hw_btn_down",
                                sizeDp = 34.dp
                            )
                            HardwareRoundDirectionKey(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Right",
                                onClick = { viewModel.onHardwareKey(HardwareKey.RIGHT) },
                                testTag = "hw_btn_right",
                                sizeDp = 34.dp
                            )
                        }
                        HardwarePushButton(
                            label = "ENTER",
                            isActive = hwState.isEditingParam,
                            ledColor = palette.lcdSecondary,
                            onClick = { viewModel.onHardwareKey(HardwareKey.ENTER) },
                            compact = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        RackMountingEar()
    }
}

@Composable
private fun PortraitRackLayout(
    dspState: DspState,
    hwState: HardwareUiState,
    audioState: AudioEngineState,
    telemetry: AudioTelemetry,
    viewModel: DlmsViewModel,
    displayContent: @Composable () -> Unit
) {
    val palette = LocalDlmsPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChassisTopSilkscreen(
            dspState = dspState,
            audioState = audioState,
            telemetry = telemetry
        )

        // Center LCD Display inside Rack Bezel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            displayContent()
        }

        // Bottom Hardware Control Deck
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.chassisSurface)
                .border(1.dp, palette.buttonBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Channel Select + Mute + Media
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HardwarePushButton(
                    label = "IN L",
                    isActive = hwState.currentPage == DisplayPage.INPUT_L,
                    ledColor = if (dspState.inputL.muted) palette.meterRed else palette.lcdPrimary,
                    onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_IN_L) },
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
                HardwarePushButton(
                    label = "IN R",
                    isActive = hwState.currentPage == DisplayPage.INPUT_R,
                    ledColor = if (dspState.inputR.muted) palette.meterRed else palette.lcdPrimary,
                    onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_IN_R) },
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
                HardwarePushButton(
                    label = "XOVER",
                    isActive = hwState.currentPage == DisplayPage.CROSSOVER,
                    ledColor = palette.lcdSecondary,
                    onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_XOVER) },
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
                HardwarePushButton(
                    label = "OUT L",
                    isActive = hwState.currentPage == DisplayPage.OUTPUT_L,
                    ledColor = if (dspState.outputL.muted) palette.meterRed else palette.meterGreen,
                    onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_OUT_L) },
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
                HardwarePushButton(
                    label = "OUT R",
                    isActive = hwState.currentPage == DisplayPage.OUTPUT_R,
                    ledColor = if (dspState.outputR.muted) palette.meterRed else palette.meterGreen,
                    onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_OUT_R) },
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Nav Buttons + D-Pad + Rotary Encoder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        HardwarePushButton(
                            label = "MENU",
                            icon = Icons.Default.Menu,
                            isActive = hwState.currentPage == DisplayPage.MENU,
                            onClick = { viewModel.onHardwareKey(HardwareKey.MENU) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        HardwarePushButton(
                            label = "BACK",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = { viewModel.onHardwareKey(HardwareKey.BACK) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        HardwarePushButton(
                            label = "MEDIA",
                            icon = Icons.Default.PlayCircle,
                            isActive = hwState.currentPage == DisplayPage.MEDIA_YOUTUBE,
                            ledColor = palette.lcdSecondary,
                            onClick = { viewModel.onHardwareKey(HardwareKey.QUICK_MEDIA) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val muteLit = when (hwState.currentPage) {
                            DisplayPage.INPUT_L -> dspState.inputL.muted
                            DisplayPage.INPUT_R -> dspState.inputR.muted
                            DisplayPage.OUTPUT_L -> dspState.outputL.muted
                            DisplayPage.OUTPUT_R -> dspState.outputR.muted
                            else -> dspState.outputL.muted && dspState.outputR.muted
                        }
                        HardwarePushButton(
                            label = "MUTE",
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            isActive = muteLit,
                            ledColor = palette.meterRed,
                            onClick = { viewModel.onHardwareKey(HardwareKey.MUTE) },
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        HardwareRoundDirectionKey(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Left",
                            onClick = { viewModel.onHardwareKey(HardwareKey.LEFT) },
                            testTag = "hw_btn_left",
                            sizeDp = 36.dp
                        )
                        HardwareRoundDirectionKey(
                            icon = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Up",
                            onClick = { viewModel.onHardwareKey(HardwareKey.UP) },
                            testTag = "hw_btn_up",
                            sizeDp = 36.dp
                        )
                        HardwareRoundDirectionKey(
                            icon = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Down",
                            onClick = { viewModel.onHardwareKey(HardwareKey.DOWN) },
                            testTag = "hw_btn_down",
                            sizeDp = 36.dp
                        )
                        HardwareRoundDirectionKey(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Right",
                            onClick = { viewModel.onHardwareKey(HardwareKey.RIGHT) },
                            testTag = "hw_btn_right",
                            sizeDp = 36.dp
                        )
                    }
                    HardwarePushButton(
                        label = "ENTER",
                        isActive = hwState.isEditingParam,
                        ledColor = palette.lcdSecondary,
                        onClick = { viewModel.onHardwareKey(HardwareKey.ENTER) },
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                RotaryEncoderKnob(
                    angleDeg = hwState.encoderAngleDeg,
                    isEditing = hwState.isEditingParam,
                    onRotateSteps = { steps, delta -> viewModel.onEncoderTurn(steps, delta) },
                    onPushEnter = { viewModel.onEncoderPush() },
                    sizeDp = 82.dp
                )
            }
        }
    }
}

@Composable
private fun ChassisTopSilkscreen(
    dspState: DspState,
    audioState: AudioEngineState,
    telemetry: AudioTelemetry
) {
    val palette = LocalDlmsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(palette.rackEarBg)
            .border(1.dp, palette.buttonBorder.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DLMS-202X // 2-WAY DSP",
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = palette.silkscreenText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HardwareStatusLed(
                label = "PWR",
                isLit = true,
                litColor = palette.lcdPrimary
            )
            HardwareStatusLed(
                label = "AUD",
                isLit = audioState.status == AudioEngineStatus.READY,
                litColor = palette.meterGreen
            )
            HardwareStatusLed(
                label = "IN-L",
                isLit = telemetry.inputL.rmsDb > -48f && !dspState.inputL.muted,
                litColor = if (telemetry.inputL.clipped) palette.meterRed else palette.meterGreen
            )
            HardwareStatusLed(
                label = "IN-R",
                isLit = telemetry.inputR.rmsDb > -48f && !dspState.inputR.muted,
                litColor = if (telemetry.inputR.clipped) palette.meterRed else palette.meterGreen
            )
            HardwareStatusLed(
                label = "OUT-L",
                isLit = telemetry.outputL.rmsDb > -48f && !dspState.outputL.muted,
                litColor = if (telemetry.outputL.limiterActive) palette.meterYellow else palette.meterGreen
            )
            HardwareStatusLed(
                label = "OUT-R",
                isLit = telemetry.outputR.rmsDb > -48f && !dspState.outputR.muted,
                litColor = if (telemetry.outputR.limiterActive) palette.meterYellow else palette.meterGreen
            )
        }
    }
}

@Composable
private fun DisplayViewport(
    viewModel: DlmsViewModel,
    dspState: DspState,
    hwState: HardwareUiState,
    audioState: AudioEngineState,
    telemetry: AudioTelemetry,
    settings: com.example.storage.SettingsState,
    capabilities: com.example.audio.AudioHardwareCapabilities,
    presets: List<com.example.storage.DlmsPreset>,
    activePresetName: String,
    youtubeState: com.example.youtube.YouTubeUiState,
    onStartAudio: () -> Unit
) {
    HardwareLcdBezel(
        brightness = settings.displayBrightness,
        isDimmed = hwState.isDisplayDimmed,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LcdSystemStatusBar(
                dspEnabled = dspState.dspEnabled,
                audioState = audioState,
                activePresetName = activePresetName,
                pageTitle = hwState.currentPage.title,
                onToggleDsp = { viewModel.toggleMasterDsp() }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (hwState.currentPage) {
                    DisplayPage.HOME -> DisplayHomeScreen(
                        dspState = dspState,
                        telemetry = telemetry,
                        audioState = audioState,
                        meterStyle = settings.meterStyle,
                        onStartAudio = onStartAudio,
                        onStopAudio = { viewModel.stopAudioEngine() },
                        onOpenPage = { viewModel.navigateTo(it) }
                    )

                    DisplayPage.MENU -> DisplayMenuScreen(
                        selectedIndex = hwState.menuIndex,
                        onSelectEntry = { viewModel.navigateTo(it) }
                    )

                    DisplayPage.INPUT_L -> DisplayInputScreen(
                        channelLabel = "INPUT L",
                        config = dspState.inputL,
                        telemetry = telemetry.inputL,
                        hwState = hwState,
                        meterStyle = settings.meterStyle,
                        onSelectSubTab = { viewModel.setInputSubTab(it) },
                        onFocusParam = { viewModel.selectAndEditParameter(it) },
                        onUpdateConfig = { viewModel.updateInputL(it) },
                        onSelectEqBand = { viewModel.selectEqBand(it) },
                        onSelectEqField = { viewModel.selectEqField(it) },
                        onToggleEqBand = { viewModel.toggleInputEqBand(true, it) },
                        onUpdateEqBand = { idx, blk -> viewModel.updateInputEqBand(true, idx, blk) }
                    )

                    DisplayPage.INPUT_R -> DisplayInputScreen(
                        channelLabel = "INPUT R",
                        config = dspState.inputR,
                        telemetry = telemetry.inputR,
                        hwState = hwState,
                        meterStyle = settings.meterStyle,
                        onSelectSubTab = { viewModel.setInputSubTab(it) },
                        onFocusParam = { viewModel.selectAndEditParameter(it) },
                        onUpdateConfig = { viewModel.updateInputR(it) },
                        onSelectEqBand = { viewModel.selectEqBand(it) },
                        onSelectEqField = { viewModel.selectEqField(it) },
                        onToggleEqBand = { viewModel.toggleInputEqBand(false, it) },
                        onUpdateEqBand = { idx, blk -> viewModel.updateInputEqBand(false, idx, blk) }
                    )

                    DisplayPage.CROSSOVER -> DisplayCrossoverScreen(
                        crossover = dspState.crossover,
                        hwState = hwState,
                        onSelectBandTab = { viewModel.setCrossoverBandTab(it) },
                        onFocusParam = { viewModel.selectAndEditParameter(it) },
                        onToggleGlobalBypass = { viewModel.updateCrossoverBypass(it) },
                        onUpdateBand = { isLow, blk -> viewModel.updateCrossoverBand(isLow, blk) }
                    )

                    DisplayPage.OUTPUT_L -> DisplayOutputScreen(
                        channelLabel = "OUTPUT L",
                        config = dspState.outputL,
                        telemetry = telemetry.outputL,
                        hwState = hwState,
                        meterStyle = settings.meterStyle,
                        onSelectSubTab = { viewModel.setOutputSubTab(it) },
                        onFocusParam = { viewModel.selectAndEditParameter(it) },
                        onUpdateConfig = { viewModel.updateOutputL(it) },
                        onSelectEqBand = { viewModel.selectEqBand(it) },
                        onSelectEqField = { viewModel.selectEqField(it) },
                        onToggleEqBand = { viewModel.toggleOutputEqBand(true, it) },
                        onUpdateEqBand = { idx, blk -> viewModel.updateOutputEqBand(true, idx, blk) },
                        onUpdateLimiter = { viewModel.updateOutputLimiter(true, it) }
                    )

                    DisplayPage.OUTPUT_R -> DisplayOutputScreen(
                        channelLabel = "OUTPUT R",
                        config = dspState.outputR,
                        telemetry = telemetry.outputR,
                        hwState = hwState,
                        meterStyle = settings.meterStyle,
                        onSelectSubTab = { viewModel.setOutputSubTab(it) },
                        onFocusParam = { viewModel.selectAndEditParameter(it) },
                        onUpdateConfig = { viewModel.updateOutputR(it) },
                        onSelectEqBand = { viewModel.selectEqBand(it) },
                        onSelectEqField = { viewModel.selectEqField(it) },
                        onToggleEqBand = { viewModel.toggleOutputEqBand(false, it) },
                        onUpdateEqBand = { idx, blk -> viewModel.updateOutputEqBand(false, idx, blk) },
                        onUpdateLimiter = { viewModel.updateOutputLimiter(false, it) }
                    )

                    DisplayPage.PRESETS -> DisplayPresetsScreen(
                        presets = presets,
                        activePresetName = activePresetName,
                        onLoadPreset = { viewModel.loadPreset(it) },
                        onSavePreset = { viewModel.saveCurrentPreset(it) },
                        onRenamePreset = { p, n -> viewModel.renamePreset(p, n) },
                        onDeletePreset = { viewModel.deletePreset(it) },
                        onResetDefault = { viewModel.resetToDefaultPreset() }
                    )

                    DisplayPage.MEDIA_YOUTUBE -> MediaYouTubeScreen(
                        ytState = youtubeState,
                        onQueryChange = { viewModel.updateYouTubeSearchQuery(it) },
                        onSubmitQuery = { viewModel.submitYouTubeQuery(it) },
                        onVideoDetected = { viewModel.onYouTubeVideoDetected(it) },
                        onCanGoBackChanged = { viewModel.onYouTubeCanGoBackChanged(it) },
                        onSetViewMode = { viewModel.setYouTubeViewMode(it) },
                        onGoYouTubeHome = { viewModel.goYouTubeHome() },
                        onPlaybackStatus = { viewModel.onYouTubePlaybackStatus(it) },
                        onProgress = { cur, dur -> viewModel.onYouTubeProgress(cur, dur) },
                        onError = { viewModel.onYouTubeError(it) },
                        onToggleMute = { viewModel.setYouTubeMuted(it) },
                        onSetVolume = { viewModel.setYouTubeVolume(it) },
                        onBackToDlms = { viewModel.navigateTo(DisplayPage.HOME) }
                    )

                    DisplayPage.SETTINGS -> DisplaySettingsScreen(
                        settings = settings,
                        capabilities = capabilities,
                        audioState = audioState,
                        onSelectTheme = { viewModel.setTheme(it) },
                        onSetBrightness = { viewModel.setBrightness(it) },
                        onSelectTimeout = { viewModel.setTimeout(it) },
                        onSelectMeterStyle = { viewModel.setMeterStyle(it) },
                        onSelectInputDevice = { viewModel.setInputDevice(it) },
                        onSelectOutputDevice = { viewModel.setOutputDevice(it) },
                        onSelectSampleRate = { viewModel.setSampleRate(it) },
                        onSelectBufferMultiplier = { viewModel.setBufferMultiplier(it) },
                        onRefreshDevices = { viewModel.refreshAudioCapabilities() },
                        onResetApp = { viewModel.resetApplication() }
                    )
                }
            }
        }
    }
}
