package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DlmsRackScreen
import com.example.ui.theme.DlmsHardwareTheme
import com.example.viewmodel.DlmsViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: DlmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val settings by viewModel.settingsState.collectAsStateWithLifecycle()
            DlmsHardwareTheme(themeStyle = settings.theme) {
                DlmsRackScreen(viewModel = viewModel)
            }
        }
    }
}
