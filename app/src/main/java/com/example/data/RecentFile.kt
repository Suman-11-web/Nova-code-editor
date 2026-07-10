package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileName: String,
    val lastOpened: Long = System.currentTimeMillis()
)
