package com.example.data

import kotlinx.coroutines.flow.Flow

class NovaRepository(private val novaDao: NovaDao) {
    val recentFiles: Flow<List<RecentFile>> = novaDao.getRecentFiles()
    val editorTabs: Flow<List<EditorTab>> = novaDao.getEditorTabs()

    suspend fun insertRecentFile(recentFile: RecentFile) {
        novaDao.insertRecentFile(recentFile)
    }

    suspend fun deleteRecentFileByPath(filePath: String) {
        novaDao.deleteRecentFileByPath(filePath)
    }

    suspend fun clearRecentFiles() {
        novaDao.clearRecentFiles()
    }

    suspend fun insertEditorTab(tab: EditorTab): Long {
        return novaDao.insertEditorTab(tab)
    }

    suspend fun updateEditorTab(tab: EditorTab) {
        novaDao.updateEditorTab(tab)
    }

    suspend fun deleteEditorTab(tab: EditorTab) {
        novaDao.deleteEditorTab(tab)
    }

    suspend fun clearAllEditorTabs() {
        novaDao.clearAllEditorTabs()
    }

    // Version Snapshots
    fun getSnapshotsForFile(filePath: String): Flow<List<VersionSnapshot>> {
        return novaDao.getSnapshotsForFile(filePath)
    }

    suspend fun getSnapshotsForFileDirect(filePath: String): List<VersionSnapshot> {
        return novaDao.getSnapshotsForFileDirect(filePath)
    }

    suspend fun insertSnapshot(snapshot: VersionSnapshot): Long {
        return novaDao.insertSnapshot(snapshot)
    }

    suspend fun deleteSnapshotsForFile(filePath: String) {
        novaDao.deleteSnapshotsForFile(filePath)
    }

    suspend fun clearAllSnapshots() {
        novaDao.clearAllSnapshots()
    }
}
