package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "version_snapshots")
data class VersionSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerName: String
)
