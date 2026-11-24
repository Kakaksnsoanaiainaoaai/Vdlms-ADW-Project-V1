package com.example.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY isFactoryDefault DESC, updatedAt DESC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): PresetEntity?

    @Query("SELECT * FROM presets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PresetEntity): Long

    @Query("UPDATE presets SET name = :newName, updatedAt = :updatedAt WHERE id = :id AND isFactoryDefault = 0")
    suspend fun rename(id: Long, newName: String, updatedAt: Long): Int

    @Query("DELETE FROM presets WHERE id = :id AND isFactoryDefault = 0")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM presets WHERE isFactoryDefault = 0")
    suspend fun deleteAllUserPresets()
}
