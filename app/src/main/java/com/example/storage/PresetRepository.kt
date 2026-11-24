package com.example.storage

import com.example.dsp.DspState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DlmsPreset(
    val id: Long,
    val name: String,
    val isFactoryDefault: Boolean,
    val dspState: DspState,
    val updatedAt: Long
)

class PresetRepository(private val presetDao: PresetDao) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val dspAdapter = moshi.adapter(DspState::class.java)

    val presets: Flow<List<DlmsPreset>> = presetDao.observeAll().map { entities ->
        entities.mapNotNull { entity ->
            decodeState(entity.dspJson)?.let { state ->
                DlmsPreset(
                    id = entity.id,
                    name = entity.name,
                    isFactoryDefault = entity.isFactoryDefault,
                    dspState = state,
                    updatedAt = entity.updatedAt
                )
            }
        }
    }

    suspend fun ensureDefaultPreset() {
        val existing = presetDao.getByName(DEFAULT_PRESET_NAME)
        if (existing == null || decodeState(existing.dspJson) == null) {
            val neutral = DspState.neutralDefault()
            presetDao.upsert(
                PresetEntity(
                    id = existing?.id ?: 0,
                    name = DEFAULT_PRESET_NAME,
                    isFactoryDefault = true,
                    dspJson = encodeState(neutral)
                )
            )
        }
    }

    suspend fun savePreset(name: String, state: DspState): Result<String> {
        val trimmed = name.trim().uppercase()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Preset name cannot be empty."))
        if (trimmed.length > 24) return Result.failure(IllegalArgumentException("Preset name max 24 characters."))
        val existing = presetDao.getByName(trimmed)
        if (existing?.isFactoryDefault == true) {
            return Result.failure(IllegalArgumentException("Cannot overwrite factory DEFAULT preset. Save with a new name."))
        }
        val json = encodeState(state.validated())
        presetDao.upsert(
            PresetEntity(
                id = existing?.id ?: 0,
                name = trimmed,
                isFactoryDefault = false,
                dspJson = json,
                updatedAt = System.currentTimeMillis()
            )
        )
        return Result.success(trimmed)
    }

    suspend fun renamePreset(id: Long, newName: String): Result<String> {
        val trimmed = newName.trim().uppercase()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Preset name cannot be empty."))
        if (trimmed.length > 24) return Result.failure(IllegalArgumentException("Preset name max 24 characters."))
        val collision = presetDao.getByName(trimmed)
        if (collision != null && collision.id != id) {
            return Result.failure(IllegalArgumentException("A preset named '$trimmed' already exists."))
        }
        val updated = presetDao.rename(id, trimmed, System.currentTimeMillis())
        return if (updated > 0) Result.success(trimmed)
        else Result.failure(IllegalStateException("Factory DEFAULT preset cannot be renamed."))
    }

    suspend fun deletePreset(id: Long): Result<Unit> {
        val deleted = presetDao.deleteById(id)
        return if (deleted > 0) Result.success(Unit)
        else Result.failure(IllegalStateException("Factory DEFAULT preset cannot be deleted."))
    }

    suspend fun resetAllUserPresets() {
        presetDao.deleteAllUserPresets()
        ensureDefaultPreset()
    }

    fun encodeState(state: DspState): String = dspAdapter.toJson(state.validated())

    fun decodeState(json: String): DspState? = try {
        if (!json.contains("channelL") || !json.contains("channelR")) null
        else dspAdapter.fromJson(json)?.validated()
    } catch (_: Exception) {
        null
    }

    companion object {
        const val DEFAULT_PRESET_NAME = "DEFAULT"
    }
}
