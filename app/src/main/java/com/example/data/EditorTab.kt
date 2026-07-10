package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "editor_tabs")
data class EditorTab(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String?, // Null if unsaved draft
    val fileName: String,
    val content: String,
    val cursorPosition: Int = 0,
    val isUnsaved: Boolean = false,
    val tabOrder: Int = 0,
    val isActive: Boolean = false,
    val language: String = "python"
)
