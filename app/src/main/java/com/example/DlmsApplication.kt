package com.example

import android.app.Application
import com.example.audio.AndroidAudioEngine
import com.example.audio.AudioDeviceEnumerator
import com.example.storage.DlmsDatabase
import com.example.storage.PresetRepository
import com.example.storage.SettingsDataStore

class DlmsApplication : Application() {
    lateinit var database: DlmsDatabase
        private set
    lateinit var presetRepository: PresetRepository
        private set
    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var audioEnumerator: AudioDeviceEnumerator
        private set
    lateinit var audioEngine: AndroidAudioEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = DlmsDatabase.getInstance(this)
        presetRepository = PresetRepository(database.presetDao())
        settingsDataStore = SettingsDataStore(this)
        audioEnumerator = AudioDeviceEnumerator(this)
        audioEngine = AndroidAudioEngine(this, audioEnumerator)
    }
}
