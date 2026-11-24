package com.example.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "presets",
    indices = [Index(value = ["name"], unique = true)]
)
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isFactoryDefault: Boolean = false,
    val dspJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)
