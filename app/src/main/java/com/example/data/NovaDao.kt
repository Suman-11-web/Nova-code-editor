package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NovaDao {
    // Recent Files
    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC LIMIT 20")
    fun getRecentFiles(): Flow<List<RecentFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(recentFile: RecentFile)

    @Query("DELETE FROM recent_files WHERE filePath = :filePath")
    suspend fun deleteRecentFileByPath(filePath: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearRecentFiles()

    // Editor Tabs
    @Query("SELECT * FROM editor_tabs ORDER BY tabOrder ASC")
    fun getEditorTabs(): Flow<List<EditorTab>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEditorTab(tab: EditorTab): Long

    @Update
    suspend fun updateEditorTab(tab: EditorTab)

    @Delete
    suspend fun deleteEditorTab(tab: EditorTab)

    @Query("DELETE FROM editor_tabs")
    suspend fun clearAllEditorTabs()

    // Version Snapshots
    @Query("SELECT * FROM version_snapshots WHERE filePath = :filePath ORDER BY timestamp DESC")
    fun getSnapshotsForFile(filePath: String): Flow<List<VersionSnapshot>>

    @Query("SELECT * FROM version_snapshots WHERE filePath = :filePath ORDER BY timestamp DESC")
    suspend fun getSnapshotsForFileDirect(filePath: String): List<VersionSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: VersionSnapshot): Long

    @Query("DELETE FROM version_snapshots WHERE filePath = :filePath")
    suspend fun deleteSnapshotsForFile(filePath: String)

    @Query("DELETE FROM version_snapshots")
    suspend fun clearAllSnapshots()
}
