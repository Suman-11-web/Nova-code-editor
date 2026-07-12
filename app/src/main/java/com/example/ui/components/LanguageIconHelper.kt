package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object LanguageIconHelper {
    fun getIconAndColor(fileName: String, isDirectory: Boolean = false): Pair<ImageVector, Color> {
        if (isDirectory) {
            return Icons.Default.Folder to Color(0xFFFBBF24) // Gold Folder
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "py" -> Icons.Default.IntegrationInstructions to Color(0xFF38BDF8) // Python Cyan Blue
            "js", "ts" -> Icons.Default.Javascript to Color(0xFFEAB308) // JS/TS Yellow
            "html", "htm" -> Icons.Default.DataObject to Color(0xFFF97316) // HTML Orange
            "css" -> Icons.Default.Palette to Color(0xFF3B82F6) // CSS Blue
            "json" -> Icons.Default.SettingsApplications to Color(0xFFA855F7) // JSON Purple
            "md" -> Icons.Default.Description to Color(0xFF34D399) // Markdown Green
            "java", "kt", "kts", "cpp", "c", "h", "cs", "go", "rs", "sh" -> Icons.Default.Terminal to Color(0xFFEC4899) // General Code Pink/Magenta
            else -> Icons.Default.InsertDriveFile to Color(0xFF94A3B8) // Slate Gray
        }
    }
}
