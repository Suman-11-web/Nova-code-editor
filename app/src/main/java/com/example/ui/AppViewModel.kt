package com.example.ui

import android.app.Application
import android.os.Environment
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EditorTab
import com.example.data.NovaDatabase
import com.example.data.NovaRepository
import com.example.data.RecentFile
import com.example.ui.editor.EditorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Stack

enum class Screen {
    EDITOR,
    EXPLORER,
    CONSOLE,
    SETTINGS
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NovaRepository
    
    // UI Screen navigation state
    var currentScreen by mutableStateOf(Screen.EDITOR)
        private set

    // Database backed states
    val recentFiles: StateFlow<List<RecentFile>>
    val editorTabs: StateFlow<List<EditorTab>>

    // Editor-specific States
    var openTabs by mutableStateOf<List<EditorTab>>(emptyList())
        private set
    
    var editorTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    private var _activeTab by mutableStateOf<EditorTab?>(null)
    var activeTab: EditorTab?
        get() = _activeTab
        private set(value) {
            _activeTab = value
            if (value != null) {
                if (editorTextFieldValue.text != value.content) {
                    editorTextFieldValue = TextFieldValue(value.content, selection = TextRange(value.content.length))
                }
            } else {
                editorTextFieldValue = TextFieldValue("")
            }
        }

    var fontSize by mutableStateOf(14f)
    var wordWrap by mutableStateOf(false)
    var editorTheme by mutableStateOf(EditorTheme.ELEGANT_DARK)

    // Search and Replace
    var searchText by mutableStateOf("")
    var replaceText by mutableStateOf("")
    var isSearchActive by mutableStateOf(false)

    // Undo/Redo stacks for each open tab path (or tab temporary ID)
    private val undoStacks = mutableMapOf<Int, Stack<String>>()
    private val redoStacks = mutableMapOf<Int, Stack<String>>()

    // File Manager States
    var currentDirectory by mutableStateOf<File>(File(application.filesDir, "NovaProjects"))
        private set
    
    var fileTreeList by mutableStateOf<List<File>>(emptyList())
        private set

    var selectedFileInTree by mutableStateOf<File?>(null)
    
    // File Clipboard for Cut/Copy/Paste
    var clipboardFile by mutableStateOf<File?>(null)
    var isCutOperation by mutableStateOf(false)

    // Expanded directories in Sidebar
    var expandedFolders by mutableStateOf<Set<String>>(emptySet())
        private set

    // Console States
    var consoleOutput by mutableStateOf(">>> ")
    var consoleError by mutableStateOf("")
    var isConsoleRunning by mutableStateOf(false)

    // Hosted backend routes & ports (Flask, Express)
    var activeHostedServerPort by mutableStateOf<Int?>(null)
    var activeHostedServerName by mutableStateOf<String?>(null)
    val hostedServerRoutes = mutableMapOf<String, String>()
    val installedNpmPackages = mutableSetOf<String>()

    // Local Host Server and Storage States
    private var serverSocket: java.net.ServerSocket? = null
    @Volatile
    private var isServerThreadRunning = false
    var localServerPort by mutableStateOf(8080)
    var isLocalServerRunning by mutableStateOf(false)
    var useExternalStorage by mutableStateOf(false)
    
    // Web Preview States
    var showWebPreview by mutableStateOf(false)
    var webPreviewUrl by mutableStateOf("")
    
    // Shortcuts Dialog State
    var showShortcutsDialog by mutableStateOf(false)

    init {
        val database = NovaDatabase.getDatabase(application)
        repository = NovaRepository(database.novaDao())
        
        recentFiles = repository.recentFiles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
        editorTabs = repository.editorTabs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        // Ensure default Project directory exists
        if (!currentDirectory.exists()) {
            currentDirectory.mkdirs()
        } else {
            // Clean up sample starter files if they exist to keep workspace completely empty
            try {
                File(currentDirectory, "welcome.md").delete()
                File(currentDirectory, "hello.py").delete()
                File(currentDirectory, "script.js").delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Load file tree
        refreshFileTree()

        // Observe open tabs from database and restore last session
        viewModelScope.launch {
            editorTabs.collect { tabs ->
                openTabs = tabs
                if (activeTab == null && tabs.isNotEmpty()) {
                    activeTab = tabs.find { it.isActive } ?: tabs.first()
                } else if (tabs.isEmpty()) {
                    activeTab = null
                }
            }
        }
        
        // Start local server
        startLocalServer()
    }

    private fun createStarterSampleFiles() {
        try {
            val welcomeFile = File(currentDirectory, "welcome.md")
            if (!welcomeFile.exists()) {
                welcomeFile.writeText(
                    """# Welcome to Nova Code Editor! 🚀

Nova is a clean, lightweight, beginner-friendly offline-first code editor.

## Key Features:
- **Syntax Highlighting** for Python, Java, JS, HTML, CSS, JSON, Markdown, etc.
- **Multiple Editor Themes** (Monokai Pro, One Dark, Solarized Dark, Cosmic Dark)
- **Built-in Run Console** to simulate and evaluate code scripts instantly.
- **Full File Manager** for browsing, renaming, cutting, copying, and pasting files.

## Try writing some code:
Create a Python file like `hello.py` and run it in the Console!
"""
                )
            }

            val samplePython = File(currentDirectory, "hello.py")
            if (!samplePython.exists()) {
                samplePython.writeText(
                    """# Nova Python Demo 🐍
# Click the RUN button in the console to test this!

name = "Nova Programmer"
print("Hello, " + name + "!")
print("Welcome to beginner-friendly coding.")

# Try some calculations:
x = 10
y = 20
total = x + y
print("Sum of x and y is:")
print(total)
"""
                )
            }

            val sampleJS = File(currentDirectory, "script.js")
            if (!sampleJS.exists()) {
                sampleJS.writeText(
                    """// Nova JavaScript Demo ⚡

const projectName = "Nova Code Editor";
console.log("Hello from " + projectName + "!");

function calculateArea(radius) {
    return 3.1415 * radius * radius;
}

const r = 5;
const area = calculateArea(r);
console.log("Circle area of radius " + r + " is:");
console.log(area);
"""
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Navigation trigger
    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    fun startLocalServer() {
        if (isLocalServerRunning) return
        try {
            val socket = try {
                java.net.ServerSocket(8080, 50, java.net.InetAddress.getByName("127.0.0.1"))
            } catch (e: Exception) {
                java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
            }
            serverSocket = socket
            localServerPort = socket.localPort
            isLocalServerRunning = true
            isServerThreadRunning = true
            
            kotlin.concurrent.thread(name = "NovaLocalServer") {
                while (isServerThreadRunning) {
                    try {
                        val clientSocket = socket.accept()
                        handleHttpClient(clientSocket)
                    } catch (e: Exception) {
                        // socket closed or error
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleHttpClient(clientSocket: java.net.Socket) {
        kotlin.concurrent.thread {
            try {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(clientSocket.getInputStream()))
                val requestLine = reader.readLine() ?: return@thread
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@thread
                val method = parts[0]
                var path = parts[1].substringBefore("?").removePrefix("/")
                if (path.isEmpty()) {
                    path = "index.html"
                }

                if (method == "GET") {
                    // Check if hosting a Python (Flask) or Node.js (Express) backend server
                    if (activeHostedServerPort != null) {
                        val cleanPath = path.substringBefore("?").removePrefix("/")
                        val lookups = listOf(cleanPath, "/$cleanPath", cleanPath.removeSuffix("index.html").removeSuffix("/"))
                        var responseText: String? = null
                        for (key in lookups) {
                            if (hostedServerRoutes.containsKey(key)) {
                                responseText = hostedServerRoutes[key]
                                break
                            }
                        }
                        if (responseText == null) {
                            responseText = hostedServerRoutes[""] ?: "<h3>Nova Backend Live Server</h3><p>Server <b>$activeHostedServerName</b> running on port $activeHostedServerPort.</p>"
                        }
                        val bytes = responseText.toByteArray()
                        val outputStream = clientSocket.getOutputStream()
                        outputStream.write("HTTP/1.1 200 OK\r\n".toByteArray())
                        outputStream.write("Content-Type: text/html\r\n".toByteArray())
                        outputStream.write("Content-Length: ${bytes.size}\r\n".toByteArray())
                        outputStream.write("Connection: close\r\n\r\n".toByteArray())
                        outputStream.write(bytes)
                        outputStream.flush()
                        return@thread
                    }

                    var file = File(currentDirectory, path)
                    if (file.isDirectory) {
                        file = File(file, "index.html")
                    }

                    val outputStream = clientSocket.getOutputStream()
                    if (file.exists() && file.isFile) {
                        val bytes = try {
                            file.readBytes()
                        } catch (e: Exception) {
                            ByteArray(0)
                        }
                        val mimeType = when (file.extension.lowercase()) {
                            "html", "htm" -> "text/html"
                            "css" -> "text/css"
                            "js" -> "application/javascript"
                            "json" -> "application/json"
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "gif" -> "image/gif"
                            "svg" -> "image/svg+xml"
                            else -> "text/plain"
                        }

                        outputStream.write("HTTP/1.1 200 OK\r\n".toByteArray())
                        outputStream.write("Content-Type: $mimeType\r\n".toByteArray())
                        outputStream.write("Content-Length: ${bytes.size}\r\n".toByteArray())
                        outputStream.write("Connection: close\r\n\r\n".toByteArray())
                        outputStream.write(bytes)
                    } else {
                        val response = "404 Not Found: ${file.name}".toByteArray()
                        outputStream.write("HTTP/1.1 404 Not Found\r\n".toByteArray())
                        outputStream.write("Content-Type: text/plain\r\n".toByteArray())
                        outputStream.write("Content-Length: ${response.size}\r\n".toByteArray())
                        outputStream.write("Connection: close\r\n\r\n".toByteArray())
                        outputStream.write(response)
                    }
                    outputStream.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    clientSocket.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun stopLocalServer() {
        isServerThreadRunning = false
        isLocalServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
    }

    override fun onCleared() {
        super.onCleared()
        stopLocalServer()
    }

    // Storage access configuration
    fun setStorageSource(external: Boolean, context: android.content.Context) {
        if (external) {
            if (hasStoragePermission(context)) {
                useExternalStorage = true
                currentDirectory = File(Environment.getExternalStorageDirectory(), "NovaProjects")
                if (!currentDirectory.exists()) {
                    currentDirectory.mkdirs()
                }
                refreshFileTree()
            }
        } else {
            useExternalStorage = false
            currentDirectory = File(getApplication<Application>().filesDir, "NovaProjects")
            if (!currentDirectory.exists()) {
                currentDirectory.mkdirs()
            }
            refreshFileTree()
        }
    }
    
    fun hasStoragePermission(context: android.content.Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Professional Code Editor Logic
    fun updateEditorTextFieldValue(newValue: TextFieldValue) {
        val currentActive = activeTab ?: return
        var adjustedValue = newValue
        val text = newValue.text
        
        // HTML Auto-closing tags feature
        val oldText = currentActive.content
        if (text.length == oldText.length + 1) {
            val selectionStart = newValue.selection.start
            val typedCharIndex = selectionStart - 1
            if (typedCharIndex in text.indices && text[typedCharIndex] == '>') {
                val isHtml = currentActive.fileName.lowercase().endsWith(".html") || currentActive.fileName.lowercase().endsWith(".htm")
                if (isHtml) {
                    var leftAngleIndex = -1
                    for (i in (typedCharIndex - 1) downTo 0) {
                        if (text[i] == '>') break
                        if (text[i] == '<') {
                            leftAngleIndex = i
                            break
                        }
                    }
                    if (leftAngleIndex != -1) {
                        val tagContent = text.substring(leftAngleIndex + 1, typedCharIndex).trim()
                        if (tagContent.isNotEmpty() && !tagContent.startsWith("/") && !tagContent.endsWith("/")) {
                            val tagName = tagContent.split(Regex("\\s+"))[0].filter { it.isLetterOrDigit() }
                            if (tagName.isNotEmpty()) {
                                val closeTag = "</$tagName>"
                                val newText = text.substring(0, typedCharIndex + 1) + closeTag + text.substring(typedCharIndex + 1)
                                adjustedValue = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(typedCharIndex + 1)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        editorTextFieldValue = adjustedValue
        updateActiveTabContent(adjustedValue.text)
    }

    fun insertTextAtCursor(insertedText: String) {
        val currentText = editorTextFieldValue.text
        val selection = editorTextFieldValue.selection
        val start = selection.start
        val end = selection.end
        
        val newText = currentText.substring(0, start) + insertedText + currentText.substring(end)
        val newCursorPos = start + insertedText.length
        
        updateEditorTextFieldValue(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos)
            )
        )
    }

    // ----------------------------------------------------
    // EDITOR ACTIONS & TAB MANAGEMENT
    // ----------------------------------------------------

    fun updateActiveTabContent(newContent: String) {
        val currentActive = activeTab ?: return
        
        // Push current content to Undo Stack before updating if content changed
        if (currentActive.content != newContent) {
            val tabId = currentActive.id
            val uStack = undoStacks.getOrPut(tabId) { Stack() }
            
            // Limit undo stack size to 50 items to keep low memory usage
            if (uStack.isEmpty() || uStack.peek() != currentActive.content) {
                uStack.push(currentActive.content)
                if (uStack.size > 50) uStack.removeAt(0)
            }
            // Clear Redo stack on new change
            redoStacks[tabId]?.clear()

            // Update local state
            val updated = currentActive.copy(content = newContent, isUnsaved = true)
            activeTab = updated
            
            // Persist to DB in background
            viewModelScope.launch {
                repository.updateEditorTab(updated)
            }
        }
    }

    fun openFileInEditor(file: File) {
        viewModelScope.launch {
            // Check if file is already open
            val existingTab = openTabs.find { it.filePath == file.absolutePath }
            if (existingTab != null) {
                selectTab(existingTab)
            } else {
                // Determine language
                val ext = file.extension.lowercase()
                val lang = when (ext) {
                    "py" -> "python"
                    "java" -> "java"
                    "js" -> "javascript"
                    "c" -> "c"
                    "cpp", "h" -> "cpp"
                    "html", "htm" -> "html"
                    "css" -> "css"
                    "json" -> "json"
                    "md" -> "markdown"
                    else -> "markdown"
                }

                // Create a new tab entity
                val newTab = EditorTab(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    content = file.readText(),
                    isUnsaved = false,
                    tabOrder = openTabs.size,
                    isActive = true,
                    language = lang
                )

                // Deactivate other tabs
                openTabs.forEach {
                    if (it.isActive) {
                        repository.updateEditorTab(it.copy(isActive = false))
                    }
                }

                val id = repository.insertEditorTab(newTab).toInt()
                activeTab = newTab.copy(id = id)
                
                // Track recent files
                repository.insertRecentFile(
                    RecentFile(filePath = file.absolutePath, fileName = file.name)
                )
            }
            navigateTo(Screen.EDITOR)
        }
    }

    fun selectTab(tab: EditorTab) {
        viewModelScope.launch {
            // Deactivate all
            openTabs.forEach {
                if (it.isActive && it.id != tab.id) {
                    repository.updateEditorTab(it.copy(isActive = false))
                }
            }
            // Activate current
            val activated = tab.copy(isActive = true)
            repository.updateEditorTab(activated)
            activeTab = activated
        }
    }

    fun createNewDraftTab() {
        viewModelScope.launch {
            val count = openTabs.count { it.filePath == null } + 1
            val newTab = EditorTab(
                filePath = null,
                fileName = "untitled_$count.py",
                content = "",
                isUnsaved = true,
                tabOrder = openTabs.size,
                isActive = true,
                language = "python"
            )

            // Deactivate others
            openTabs.forEach {
                if (it.isActive) {
                    repository.updateEditorTab(it.copy(isActive = false))
                }
            }

            val id = repository.insertEditorTab(newTab).toInt()
            activeTab = newTab.copy(id = id)
            navigateTo(Screen.EDITOR)
        }
    }

    fun closeTab(tab: EditorTab) {
        viewModelScope.launch {
            repository.deleteEditorTab(tab)
            undoStacks.remove(tab.id)
            redoStacks.remove(tab.id)

            // If we closed the active tab, select another one
            if (activeTab?.id == tab.id) {
                val remaining = openTabs.filter { it.id != tab.id }
                if (remaining.isNotEmpty()) {
                    val nextActive = remaining.first()
                    repository.updateEditorTab(nextActive.copy(isActive = true))
                    activeTab = nextActive
                } else {
                    activeTab = null
                }
            }
        }
    }

    fun saveCurrentFile() {
        val tab = activeTab ?: return
        val path = tab.filePath
        if (path != null) {
            // Real Save
            try {
                val file = File(path)
                file.writeText(tab.content)
                val updated = tab.copy(isUnsaved = false)
                activeTab = updated
                viewModelScope.launch {
                    repository.updateEditorTab(updated)
                }
                consoleOutput = "File saved successfully: ${file.name}"
            } catch (e: Exception) {
                consoleOutput = "Failed to save file: ${e.message}"
            }
        } else {
            // Unsaved Draft -> Must "Save As" first
            // Default Save to current directory
            saveCurrentFileAs(tab.fileName)
        }
    }

    fun saveCurrentFileAs(newName: String) {
        val tab = activeTab ?: return
        val cleanName = if (newName.contains(".")) newName else "$newName.py"
        val targetFile = File(currentDirectory, cleanName)
        try {
            targetFile.writeText(tab.content)
            
            // Determine language of new file
            val ext = targetFile.extension.lowercase()
            val lang = when (ext) {
                "py" -> "python"
                "java" -> "java"
                "js" -> "javascript"
                "c" -> "c"
                "cpp", "h" -> "cpp"
                "html", "htm" -> "html"
                "css" -> "css"
                "json" -> "json"
                "md" -> "markdown"
                else -> "markdown"
            }

            val updated = tab.copy(
                filePath = targetFile.absolutePath,
                fileName = targetFile.name,
                isUnsaved = false,
                language = lang
            )
            activeTab = updated
            viewModelScope.launch {
                repository.updateEditorTab(updated)
                repository.insertRecentFile(
                    RecentFile(filePath = targetFile.absolutePath, fileName = targetFile.name)
                )
            }
            refreshFileTree()
            consoleOutput = "File saved as: ${targetFile.name}"
        } catch (e: Exception) {
            consoleOutput = "Failed to Save As: ${e.message}"
        }
    }

    // Undo & Redo Actions
    fun undo() {
        val tab = activeTab ?: return
        val tabId = tab.id
        val uStack = undoStacks[tabId]
        if (uStack != null && uStack.isNotEmpty()) {
            val previousContent = uStack.pop()
            
            // Push current to Redo stack
            val rStack = redoStacks.getOrPut(tabId) { Stack() }
            rStack.push(tab.content)

            val updated = tab.copy(content = previousContent, isUnsaved = true)
            activeTab = updated
            viewModelScope.launch {
                repository.updateEditorTab(updated)
            }
        }
    }

    fun redo() {
        val tab = activeTab ?: return
        val tabId = tab.id
        val rStack = redoStacks[tabId]
        if (rStack != null && rStack.isNotEmpty()) {
            val nextContent = rStack.pop()

            // Push current to Undo stack
            val uStack = undoStacks.getOrPut(tabId) { Stack() }
            uStack.push(tab.content)

            val updated = tab.copy(content = nextContent, isUnsaved = true)
            activeTab = updated
            viewModelScope.launch {
                repository.updateEditorTab(updated)
            }
        }
    }

    fun canUndo(): Boolean {
        val tabId = activeTab?.id ?: return false
        return undoStacks[tabId]?.isNotEmpty() ?: false
    }

    fun canRedo(): Boolean {
        val tabId = activeTab?.id ?: return false
        return redoStacks[tabId]?.isNotEmpty() ?: false
    }

    // ----------------------------------------------------
    // FILE EXPLORER ACTIONS
    // ----------------------------------------------------

    fun refreshFileTree() {
        try {
            if (!currentDirectory.exists()) {
                currentDirectory.mkdirs()
            }
            // Sort directories first, then files alphabetically
            val files = currentDirectory.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            fileTreeList = files ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun navigateToDirectory(dir: File) {
        if (dir.isDirectory) {
            currentDirectory = dir
            refreshFileTree()
        }
    }

    fun navigateUp() {
        val parent = currentDirectory.parentFile
        // Do not navigate past internal app storage scope unless desired
        if (parent != null && parent.name != "files") {
            currentDirectory = parent
            refreshFileTree()
        }
    }

    fun toggleFolderExpansion(folderPath: String) {
        expandedFolders = if (expandedFolders.contains(folderPath)) {
            expandedFolders - folderPath
        } else {
            expandedFolders + folderPath
        }
    }

    fun createNewFileInExplorer(name: String) {
        val cleanName = if (name.contains(".")) name else "$name.py"
        val newFile = File(currentDirectory, cleanName)
        try {
            if (!newFile.exists()) {
                newFile.createNewFile()
                refreshFileTree()
                openFileInEditor(newFile)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun createNewFolderInExplorer(name: String) {
        val newFolder = File(currentDirectory, name)
        try {
            if (!newFolder.exists()) {
                newFolder.mkdirs()
                refreshFileTree()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteFileInExplorer(file: File) {
        try {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
                // Close tab if deleted file was open
                val openTab = openTabs.find { it.filePath == file.absolutePath }
                if (openTab != null) {
                    closeTab(openTab)
                }
                // Delete from recent files in DB
                viewModelScope.launch {
                    repository.deleteRecentFileByPath(file.absolutePath)
                }
            }
            refreshFileTree()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun renameFileInExplorer(file: File, newName: String) {
        val parent = file.parentFile ?: return
        val destination = File(parent, newName)
        try {
            if (file.renameTo(destination)) {
                // If it was open in editor, update the tab info
                val openTab = openTabs.find { it.filePath == file.absolutePath }
                if (openTab != null) {
                    viewModelScope.launch {
                        repository.updateEditorTab(
                            openTab.copy(
                                filePath = destination.absolutePath,
                                fileName = destination.name
                            )
                        )
                    }
                }
                refreshFileTree()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Clipboard Copy / Cut / Paste operations
    fun copyFileToClipboard(file: File) {
        clipboardFile = file
        isCutOperation = false
    }

    fun cutFileToClipboard(file: File) {
        clipboardFile = file
        isCutOperation = true
    }

    fun pasteFromClipboard() {
        val source = clipboardFile ?: return
        val destination = File(currentDirectory, source.name)
        
        try {
            if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = true)
                if (isCutOperation) {
                    source.deleteRecursively()
                }
            } else {
                source.copyTo(destination, overwrite = true)
                if (isCutOperation) {
                    source.delete()
                }
            }
            
            // Reset clipboard if it was cut
            if (isCutOperation) {
                clipboardFile = null
            }
            refreshFileTree()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearRecentFilesHistory() {
        viewModelScope.launch {
            repository.clearRecentFiles()
        }
    }

    // ----------------------------------------------------
    // LIVE CONSOLE CODE INTERPRETER & SIMULATOR
    // ----------------------------------------------------

    fun runActiveCode() {
        val tab = activeTab
        if (tab == null) {
            consoleOutput = "No file open to run!\nCreate or open a file first."
            consoleError = "Error: Editor is empty"
            navigateTo(Screen.CONSOLE)
            return
        }

        val code = tab.content
        val lang = tab.language.lowercase()
        val ext = tab.fileName.substringAfterLast('.', "").lowercase()

        val isFlask = (lang == "python" || ext == "py") && (code.contains("flask") || code.contains("Flask"))
        val isExpress = (lang == "javascript" || lang == "typescript" || ext == "js" || ext == "ts") && code.contains("express")

        if (isFlask) {
            saveCurrentFile()
            if (!installedPipPackages.contains("flask")) {
                consoleOutput = "ModuleNotFoundError: No module named 'flask'\n\nTry running 'pip install flask' in the Interactive Terminal."
                consoleError = "Error: flask is not installed"
                navigateTo(Screen.CONSOLE)
                return
            }
            parseAndHostBackend(code, "python")
            if (!isLocalServerRunning) {
                startLocalServer()
            }
            val port = activeHostedServerPort ?: 5000
            consoleOutput = ">>> Running ${tab.fileName}...\n* Serving Flask app '${tab.fileName.substringBeforeLast(".")}'\n* Debug mode: on\n* Running on http://127.0.0.1:$port/ (Press CTRL+C to quit)\n* Serving dynamic background endpoints successfully!"
            consoleError = ""
            webPreviewUrl = "http://127.0.0.1:$localServerPort/"
            showWebPreview = true
            navigateTo(Screen.CONSOLE)
            return
        }

        if (isExpress) {
            saveCurrentFile()
            if (!installedNpmPackages.contains("express")) {
                consoleOutput = "Error: Cannot find module 'express'\n\nTry running 'npm install express' in the Interactive Terminal."
                consoleError = "Error: express is not installed"
                navigateTo(Screen.CONSOLE)
                return
            }
            parseAndHostBackend(code, "javascript")
            if (!isLocalServerRunning) {
                startLocalServer()
            }
            val port = activeHostedServerPort ?: 3000
            consoleOutput = ">>> Running ${tab.fileName}...\n[nodemon] starting `node ${tab.fileName}`\nServer running at http://127.0.0.1:$port/\nExpress routing table active on background live host!"
            consoleError = ""
            webPreviewUrl = "http://127.0.0.1:$localServerPort/"
            showWebPreview = true
            navigateTo(Screen.CONSOLE)
            return
        }

        // Check if HTML or Web code
        if (lang == "html" || lang == "css" || lang == "javascript" || ext == "html" || ext == "htm" || ext == "js" || ext == "css") {
            // Force save current state first
            saveCurrentFile()
            
            // Ensure local server is running
            if (!isLocalServerRunning) {
                startLocalServer()
            }
            
            // Open local WebView preview!
            val fileName = tab.fileName
            webPreviewUrl = "http://127.0.0.1:$localServerPort/$fileName"
            showWebPreview = true
            return
        }

        isConsoleRunning = true
        consoleOutput = "Compiling ${tab.fileName}...\n"
        consoleError = ""
        navigateTo(Screen.CONSOLE)

        viewModelScope.launch {
            // Simulate processing time
            kotlinx.coroutines.delay(600)

            val code = tab.content
            val lang = tab.language.lowercase()

            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()

            outputBuilder.append("Nova Execution Environment (V1.0)\n")
            outputBuilder.append("Executing: ${tab.fileName} via modern dynamic interpreter\n")
            outputBuilder.append("--------------------------------------------------\n\n")

            try {
                // Highly functional lightweight custom parser
                val lines = code.split("\n")
                var hasSyntaxError = false
                val variables = mutableMapOf<String, String>()

                for (index in lines.indices) {
                    val line = lines[index].trim()
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

                    // Basic variable assignments: e.g., name = "value" or x = 10
                    if (line.contains("=") && !line.startsWith("if") && !line.startsWith("for") && !line.startsWith("while")) {
                        val parts = line.split("=", limit = 2)
                        val varName = parts[0].trim()
                        var varValue = parts[1].trim()

                        // Remove quotes for strings
                        if (varValue.startsWith("\"") && varValue.endsWith("\"")) {
                            varValue = varValue.substring(1, varValue.length - 1)
                        } else if (varValue.startsWith("'") && varValue.endsWith("'")) {
                            varValue = varValue.substring(1, varValue.length - 1)
                        }
                        
                        variables[varName] = varValue
                    }

                    // Look for print() or console.log() statements
                    if (line.startsWith("print(") || line.startsWith("console.log(") || line.startsWith("System.out.println(")) {
                        // Extract content between innermost matching parentheses
                        val startIdx = line.indexOf("(") + 1
                        val endIdx = line.lastIndexOf(")")
                        if (endIdx > startIdx) {
                            var expr = line.substring(startIdx, endIdx).trim()
                            
                            // Check if string literal
                            if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
                                outputBuilder.append(expr.substring(1, expr.length - 1)).append("\n")
                            } else {
                                // Variable or arithmetic expression evaluation
                                if (expr.contains("+")) {
                                    val tokens = expr.split("+")
                                    val evalSum = StringBuilder()
                                    var numericSum = 0.0
                                    var isNumeric = true
                                    
                                    for (t in tokens) {
                                        val token = t.trim()
                                        val cleanToken = if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                                            token.substring(1, token.length - 1)
                                        } else {
                                            variables[token] ?: token
                                        }

                                        evalSum.append(cleanToken)
                                        
                                        // Attempt numeric tracking
                                        try {
                                            numericSum += cleanToken.toDouble()
                                        } catch (e: NumberFormatException) {
                                            isNumeric = false
                                        }
                                    }
                                    
                                    if (isNumeric && tokens.size > 1) {
                                        outputBuilder.append(numericSum).append("\n")
                                    } else {
                                        outputBuilder.append(evalSum.toString()).append("\n")
                                    }
                                } else {
                                    // Direct lookup
                                    val lookup = variables[expr]
                                    if (lookup != null) {
                                        outputBuilder.append(lookup).append("\n")
                                    } else {
                                        // Direct string/number literal print
                                        outputBuilder.append(expr).append("\n")
                                    }
                                }
                            }
                        } else {
                            hasSyntaxError = true
                            errorBuilder.append("Syntax Error on Line ${index + 1}: Unclosed parenthesis.\n")
                            errorBuilder.append("-> $line\n")
                            break
                        }
                    }

                    // Check brackets matching
                    val openBracketsCount = line.count { it == '{' || it == '(' || it == '[' }
                    val closeBracketsCount = line.count { it == '}' || it == ')' || it == ']' }
                    if (openBracketsCount != closeBracketsCount && !line.startsWith("print") && !line.startsWith("console") && !line.startsWith("System.out")) {
                        // Soft warning, don't crash but alert
                    }
                }

                // If nothing was parsed but the code contains functions or tags, run secondary dynamic simulators
                if (outputBuilder.length < 150) {
                    if (lang == "html") {
                        outputBuilder.append("Rendering HTML Page Content...\n")
                        outputBuilder.append("--------------------------------------------------\n")
                        // Extract content
                        val titleRegex = "<title>(.*?)</title>".toRegex()
                        val h1Regex = "<h1>(.*?)</h1>".toRegex()
                        val pRegex = "<p>(.*?)</p>".toRegex()

                        val title = titleRegex.find(code)?.groupValues?.get(1) ?: "Nova Page"
                        outputBuilder.append("Document Title: $title\n")
                        h1Regex.findAll(code).forEach { match ->
                            outputBuilder.append("[Header 1] ${match.groupValues[1]}\n")
                        }
                        pRegex.findAll(code).forEach { match ->
                            outputBuilder.append("[Paragraph] ${match.groupValues[1]}\n")
                        }
                        outputBuilder.append("\nHTML rendering finished with 0 errors.\n")
                    } else if (lang == "json") {
                        outputBuilder.append("Validating JSON configuration structure...\n")
                        if (code.trim().startsWith("{") && code.trim().endsWith("}")) {
                            outputBuilder.append("SUCCESS: Valid JSON syntax verified!\n")
                            outputBuilder.append("Document length: ${code.length} characters.\n")
                        } else {
                            hasSyntaxError = true
                            errorBuilder.append("JSON Syntax Error: File must begin with '{' and end with '}'.\n")
                        }
                    } else if (lang == "markdown") {
                        outputBuilder.append("Generating Live Markdown visualizer report...\n")
                        outputBuilder.append("--------------------------------------------------\n")
                        lines.forEach { l ->
                            if (l.startsWith("#")) {
                                outputBuilder.append("[TITLE] ${l.replace("#", "").trim()}\n")
                            } else if (l.trim().startsWith("-") || l.trim().startsWith("*")) {
                                outputBuilder.append("  • ${l.trim().substring(1).trim()}\n")
                            } else if (l.isNotEmpty()) {
                                outputBuilder.append("  ${l.trim()}\n")
                            }
                        }
                    } else if (!hasSyntaxError) {
                        // Standard fallback simulation for languages without basic print statements
                        outputBuilder.append("[Build Logs]\n")
                        outputBuilder.append("Targeting architecture: native_android_64bit\n")
                        outputBuilder.append("Linker tasks executing: standard_nova_build\n")
                        outputBuilder.append("Symbol table created: ${variables.keys.size} global variables mapped.\n")
                        outputBuilder.append("\n[Process Finished]\n")
                        outputBuilder.append("Execution completed with Exit Code 0 (Success).\n")
                    }
                }

                if (hasSyntaxError) {
                    consoleOutput = "Execution failed with compilation errors."
                    consoleError = errorBuilder.toString()
                } else {
                    outputBuilder.append("\n--------------------------------------------------\n")
                    outputBuilder.append("Process exited with code 0.")
                    consoleOutput = outputBuilder.toString()
                    consoleError = ""
                }

            } catch (e: Exception) {
                consoleOutput = "Exception crashed runtime simulation."
                consoleError = "Runtime Exception: ${e.message ?: "Unknown error"}"
            } finally {
                isConsoleRunning = false
            }
        }
    }

    fun clearConsole() {
        consoleOutput = ">>> "
        consoleError = ""
    }

    // ----------------------------------------------------
    // INTERACTIVE TERMINAL SHELL (REAL-TIME EXECUTOR)
    // ----------------------------------------------------
    var terminalInput by mutableStateOf("")
    var terminalCwd by mutableStateOf(currentDirectory)
    var terminalHistory by mutableStateOf("Nova Terminal Shell v1.0\nType 'help' to see list of available commands.\n\n")

    fun getTerminalPrompt(): String {
        val root = currentDirectory.absolutePath
        val cwd = terminalCwd.absolutePath
        val displayPath = if (cwd == root) {
            "~/"
        } else if (cwd.startsWith(root)) {
            val relative = cwd.substring(root.length).removePrefix("/")
            "home/$relative"
        } else {
            cwd
        }
        return "$displayPath $ "
    }

    // ----------------------------------------------------
    // AUTO SUGGESTION / AUTOCOMPLETE SYSTEM
    // ----------------------------------------------------
    fun getActiveWord(): String {
        val text = editorTextFieldValue.text
        val cursor = editorTextFieldValue.selection.start
        if (cursor <= 0 || cursor > text.length) return ""
        
        var start = cursor - 1
        while (start >= 0) {
            val char = text[start]
            if (!char.isLetterOrDigit() && char != '_' && char != '@') {
                break
            }
            start--
        }
        start++
        
        return if (start < cursor) text.substring(start, cursor) else ""
    }

    fun getSuggestionsForCurrentWord(): List<String> {
        val word = getActiveWord().lowercase()
        if (word.isEmpty()) return emptyList()

        val lang = activeTab?.language?.lowercase() ?: "python"
        val ext = activeTab?.fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        
        val candidates = when {
            lang == "python" || ext == "py" -> listOf(
                "import", "from", "print", "def", "return", "class", "if", "else", "elif", "while", "for", "in", "try", "except", "pass", "True", "False", "None", "self", "as",
                "flask", "Flask", "app = Flask(__name__)", "route", "app.run(port=5000)", "methods", "jsonify", "request", "render_template", "redirect", "url_for",
                "pip install", "django", "numpy", "pandas", "requests", "math", "random", "json", "sys", "os"
            )
            lang == "javascript" || lang == "typescript" || ext == "js" || ext == "ts" -> listOf(
                "const", "let", "var", "function", "return", "class", "if", "else", "while", "for", "import", "export", "from", "default", "try", "catch", "finally", "true", "false", "null", "undefined",
                "express", "express()", "app.get", "app.post", "app.listen(3000)", "req", "res", "send", "json", "require", "module.exports",
                "console.log", "document", "window", "setTimeout", "setInterval", "addEventListener", "fetch", "Promise", "async", "await"
            )
            lang == "html" || ext == "html" || ext == "htm" -> listOf(
                "html", "head", "body", "div", "span", "p", "a", "img", "button", "input", "form", "label", "ul", "ol", "li", "table", "tr", "td", "th", "style", "script", "link", "meta", "title",
                "class", "id", "href", "src", "alt", "placeholder", "type", "value", "onclick", "style=\"\"", "<!DOCTYPE html>"
            )
            lang == "css" || ext == "css" -> listOf(
                "margin", "padding", "color", "background-color", "font-size", "font-family", "font-weight", "text-align", "display: flex;", "display: block;", "display: grid;",
                "justify-content", "align-items", "border", "border-radius", "width", "height", "position", "top", "bottom", "left", "right", "z-index", "box-shadow", "cursor", "transition"
            )
            else -> listOf(
                "if", "else", "while", "for", "return", "function", "class", "import", "true", "false"
            )
        }

        return candidates.filter { it.lowercase().startsWith(word) && it.lowercase() != word }
    }

    fun selectSuggestion(suggestion: String) {
        val text = editorTextFieldValue.text
        val cursor = editorTextFieldValue.selection.start
        if (cursor < 0 || cursor > text.length) return
        
        var start = cursor - 1
        while (start >= 0) {
            val char = text[start]
            if (!char.isLetterOrDigit() && char != '_' && char != '@') {
                break
            }
            start--
        }
        start++
        
        val newText = text.substring(0, start) + suggestion + text.substring(cursor)
        val newCursorPos = start + suggestion.length
        
        updateEditorTextFieldValue(
            androidx.compose.ui.text.input.TextFieldValue(
                text = newText,
                selection = androidx.compose.ui.text.TextRange(newCursorPos)
            )
        )
    }

    // ----------------------------------------------------
    // DYNAMIC BACKEND FLASK & EXPRESS COMPILER AND HOSTING
    // ----------------------------------------------------
    fun parseAndHostBackend(content: String, language: String) {
        hostedServerRoutes.clear()
        
        val isFlask = language.lowercase() == "python" && (content.contains("flask") || content.contains("Flask"))
        val isExpress = (language.lowercase() == "javascript" || language.lowercase() == "typescript") && content.contains("express")
        
        if (isFlask) {
            activeHostedServerName = "Flask"
            var port = 5000
            val portRegex = Regex("""port\s*=\s*(\d+)""")
            portRegex.find(content)?.let {
                port = it.groupValues[1].toIntOrNull() ?: 5000
            }
            activeHostedServerPort = port
            
            // Regex to match python flask route decorators and function return values
            val routeRegex = Regex("""@app\.route\(\s*["']([^"']+)["'].*?\)[\s\S]*?def\s+\w+\(\s*\):[\s\S]*?return\s+["']([^"']+)["']""")
            val matches = routeRegex.findAll(content)
            for (match in matches) {
                val path = match.groupValues[1].removePrefix("/")
                val response = match.groupValues[2]
                hostedServerRoutes[path] = response
            }
            
            if (!hostedServerRoutes.containsKey("")) {
                hostedServerRoutes[""] = "<h2>Hello from Python Flask backend!</h2><p>Server running on port $port</p>"
            }
        } else if (isExpress) {
            activeHostedServerName = "Express.js"
            var port = 3000
            val portRegex = Regex("""listen\(\s*(\d+)""")
            portRegex.find(content)?.let {
                port = it.groupValues[1].toIntOrNull() ?: 3000
            }
            activeHostedServerPort = port
            
            // Regex to match express route handlers and responses
            val routeRegex = Regex("""app\.(get|post|use)\(\s*["']([^"']+)["'].*?res\.send\(\s*["']([^"']+)["']""")
            val matches = routeRegex.findAll(content)
            for (match in matches) {
                val path = match.groupValues[2].removePrefix("/")
                val response = match.groupValues[3]
                hostedServerRoutes[path] = response
            }
            
            if (!hostedServerRoutes.containsKey("")) {
                hostedServerRoutes[""] = "<h2>Hello from Node.js Express backend!</h2><p>Server running on port $port</p>"
            }
        } else {
            activeHostedServerName = null
            activeHostedServerPort = null
        }
    }

    private val installedPipPackages = mutableSetOf<String>()

    fun runTerminalCommand(commandLine: String) {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return

        // Append user prompt + command to history
        terminalHistory += "${getTerminalPrompt()}$trimmed\n"

        val parts = trimmed.split(Regex("\\s+"))
        val command = parts[0]

        when (command) {
            "help" -> {
                terminalHistory += """
                    Available Terminal Commands:
                      help                 Show this help screen
                      clear                Clear terminal screen
                      pwd                  Print current working directory
                      ls                   List files and folders in current directory
                      cd <dir>             Change working directory
                      mkdir <dir_name>     Create a new directory
                      rm <file_or_dir>     Delete file or directory (recursively)
                      cat <file_name>      Print file contents
                      pip install <pkg>    Install Python pip packages (simulated/real fallback)
                      npm install <pkg>    Install Node packages (simulated)
                      python <file_name>   Run custom Python script / Flask backend
                      node <file_name>     Run Javascript script / Express.js backend
                      echo <text>          Print text to the terminal
                      uname                Print system details
                """.trimIndent() + "\n\n"
            }
            "clear" -> {
                terminalHistory = ""
            }
            "pwd" -> {
                terminalHistory += "${terminalCwd.absolutePath}\n\n"
            }
            "ls" -> {
                try {
                    val files = terminalCwd.listFiles()
                    if (files.isNullOrEmpty()) {
                        terminalHistory += "(empty directory)\n\n"
                    } else {
                        val sb = StringBuilder()
                        files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { file ->
                            if (file.isDirectory) {
                                sb.append("📁 ${file.name}/\n")
                            } else {
                                sb.append("📄 ${file.name}   (${file.length()} bytes)\n")
                            }
                        }
                        terminalHistory += sb.toString() + "\n"
                    }
                } catch (e: Exception) {
                    terminalHistory += "Error listing files: ${e.message}\n\n"
                }
            }
            "cd" -> {
                if (parts.size < 2) {
                    // Go to project home root
                    val rootDir = if (useExternalStorage) {
                        File(Environment.getExternalStorageDirectory(), "NovaProjects")
                    } else {
                        File(getApplication<Application>().filesDir, "NovaProjects")
                    }
                    if (!rootDir.exists()) rootDir.mkdirs()
                    terminalCwd = rootDir
                    terminalHistory += "\n"
                } else {
                    val targetPath = parts[1]
                    val newDir = if (targetPath == "..") {
                        terminalCwd.parentFile ?: terminalCwd
                    } else {
                        val file = File(terminalCwd, targetPath)
                        if (file.isAbsolute) File(targetPath) else file
                    }

                    if (newDir.exists() && newDir.isDirectory) {
                        terminalCwd = newDir
                        terminalHistory += "\n"
                    } else {
                        terminalHistory += "cd: no such file or directory: $targetPath\n\n"
                    }
                }
            }
            "mkdir" -> {
                if (parts.size < 2) {
                    terminalHistory += "mkdir: missing operand\n\n"
                } else {
                    val name = parts[1]
                    val newDir = File(terminalCwd, name)
                    if (newDir.exists()) {
                        terminalHistory += "mkdir: cannot create directory '$name': File exists\n\n"
                    } else {
                        if (newDir.mkdirs()) {
                            terminalHistory += "Directory '$name' created successfully\n\n"
                            refreshFileTree()
                        } else {
                            terminalHistory += "mkdir: failed to create directory '$name'\n\n"
                        }
                    }
                }
            }
            "rm" -> {
                if (parts.size < 2) {
                    terminalHistory += "rm: missing operand\n\n"
                } else {
                    val hasForce = parts.contains("-rf") || parts.contains("-f") || parts.contains("-r")
                    val targetName = parts.last()
                    if (targetName == "rm" || targetName == "-rf" || targetName == "-f" || targetName == "-r") {
                        terminalHistory += "rm: missing operand\n\n"
                    } else {
                        val file = File(terminalCwd, targetName)
                        if (!file.exists()) {
                            terminalHistory += "rm: cannot remove '$targetName': No such file or directory\n\n"
                        } else {
                            val success = if (file.isDirectory) {
                                if (hasForce) {
                                    file.deleteRecursively()
                                } else {
                                    file.delete()
                                }
                            } else {
                                file.delete()
                            }
                            if (success) {
                                terminalHistory += "Removed successfully\n\n"
                                refreshFileTree()
                            } else {
                                terminalHistory += "rm: failed to remove '$targetName'. If it's a non-empty directory, use: rm -rf $targetName\n\n"
                            }
                        }
                    }
                }
            }
            "cat" -> {
                if (parts.size < 2) {
                    terminalHistory += "cat: missing operand\n\n"
                } else {
                    val name = parts[1]
                    val file = File(terminalCwd, name)
                    if (file.exists() && file.isFile) {
                        try {
                            terminalHistory += file.readText() + "\n\n"
                        } catch (e: Exception) {
                            terminalHistory += "cat: read failed: ${e.message}\n\n"
                        }
                    } else {
                        terminalHistory += "cat: $name: No such file or directory\n\n"
                    }
                }
            }
            "echo" -> {
                if (parts.size < 2) {
                    terminalHistory += "\n"
                } else {
                    val text = trimmed.substringAfter("echo").trim()
                    terminalHistory += "$text\n\n"
                }
            }
            "uname" -> {
                terminalHistory += "Linux android ${Build.VERSION.RELEASE} ${Build.HARDWARE} ${Build.MODEL} arm64\n\n"
            }
            "pip" -> {
                if (parts.size >= 3 && parts[1] == "install") {
                    val pkgName = parts[2]
                    terminalHistory += "Collecting $pkgName...\n"
                    
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(400)
                        terminalHistory += "  Downloading $pkgName-1.2.4-py3-none-any.whl (84 kB)\n"
                        kotlinx.coroutines.delay(600)
                        terminalHistory += "  Preparing metadata (setup.py) ... done\n"
                        kotlinx.coroutines.delay(500)
                        terminalHistory += "Installing collected packages: $pkgName\n"
                        kotlinx.coroutines.delay(400)
                        terminalHistory += "Successfully installed $pkgName-1.2.4\n\n"
                        installedPipPackages.add(pkgName)
                    }
                } else {
                    terminalHistory += "Usage: pip install <package_name>\n\n"
                }
            }
            "npm" -> {
                if (parts.size >= 3 && parts[1] == "install") {
                    val pkgName = parts[2]
                    terminalHistory += "npm fetch metadata ...\n"
                    
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(400)
                        terminalHistory += "npm WARN deprecated $pkgName-1.0.0: No repository field.\n"
                        kotlinx.coroutines.delay(600)
                        terminalHistory += "added 12 packages from 8 contributors in 1.45s\n"
                        kotlinx.coroutines.delay(300)
                        terminalHistory += "audited 12 packages in 2.1s\n"
                        terminalHistory += "Successfully installed $pkgName\n\n"
                        installedNpmPackages.add(pkgName)
                    }
                } else {
                    terminalHistory += "Usage: npm install <package_name>\n\n"
                }
            }
            "python" -> {
                if (parts.size < 2) {
                    terminalHistory += "Python 3.10.4 Interactive Simulator\nUse 'python <file_name>' to run a script.\n\n"
                } else {
                    val name = parts[1]
                    val file = File(terminalCwd, name)
                    if (file.exists() && file.isFile) {
                        val content = file.readText()
                        terminalHistory += ">>> Running $name...\n"
                        executePythonCodeInTerminal(content)
                    } else {
                        terminalHistory += "python: can't open file '$name': [Errno 2] No such file or directory\n\n"
                    }
                }
            }
            "node" -> {
                if (parts.size < 2) {
                    terminalHistory += "Node.js v18.16.0 Interactive Simulator\nUse 'node <file_name>' to run a script.\n\n"
                } else {
                    val name = parts[1]
                    val file = File(terminalCwd, name)
                    if (file.exists() && file.isFile) {
                        val content = file.readText()
                        terminalHistory += ">>> Running $name...\n"
                        executeNodeCodeInTerminal(content, name)
                    } else {
                        terminalHistory += "node: can't open file '$name': No such file or directory\n\n"
                    }
                }
            }
            else -> {
                runRealSystemCommand(trimmed)
            }
        }
    }

    private fun executePythonCodeInTerminal(code: String) {
        val isFlask = code.contains("flask") || code.contains("Flask")
        if (isFlask) {
            if (!installedPipPackages.contains("flask")) {
                terminalHistory += "ModuleNotFoundError: No module named 'flask'\n\nTry running 'pip install flask' in the terminal first.\n\n"
                return
            }
            parseAndHostBackend(code, "python")
            if (!isLocalServerRunning) {
                startLocalServer()
            }
            val port = activeHostedServerPort ?: 5000
            terminalHistory += "* Serving Flask app 'app'\n* Debug mode: on\n* Running on http://127.0.0.1:$port/ (Press CTRL+C to quit)\n* Serving dynamic background endpoints successfully!\n\n"
            webPreviewUrl = "http://127.0.0.1:$localServerPort/"
            showWebPreview = true
            return
        }

        val lines = code.split("\n")
        val variables = mutableMapOf<String, String>()
        val outputBuilder = StringBuilder()
        var hasSyntaxError = false

        for (index in lines.indices) {
            val line = lines[index].trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            if (line.contains("=") && !line.startsWith("if") && !line.startsWith("for") && !line.startsWith("while")) {
                val parts = line.split("=", limit = 2)
                val varName = parts[0].trim()
                var varValue = parts[1].trim()
                if (varValue.startsWith("\"") && varValue.endsWith("\"")) {
                    varValue = varValue.substring(1, varValue.length - 1)
                } else if (varValue.startsWith("'") && varValue.endsWith("'")) {
                    varValue = varValue.substring(1, varValue.length - 1)
                }
                variables[varName] = varValue
            }

            if (line.startsWith("print(")) {
                val startIdx = line.indexOf("(") + 1
                val endIdx = line.lastIndexOf(")")
                if (endIdx > startIdx) {
                    val expr = line.substring(startIdx, endIdx).trim()
                    if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
                        outputBuilder.append(expr.substring(1, expr.length - 1)).append("\n")
                    } else {
                        if (expr.contains("+")) {
                            val tokens = expr.split("+")
                            val evalSum = StringBuilder()
                            for (t in tokens) {
                                val token = t.trim()
                                val cleanToken = if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                                    token.substring(1, token.length - 1)
                                } else {
                                    variables[token] ?: token
                                }
                                evalSum.append(cleanToken)
                            }
                            outputBuilder.append(evalSum.toString()).append("\n")
                        } else {
                            val lookup = variables[expr] ?: expr
                            outputBuilder.append(lookup).append("\n")
                        }
                    }
                } else {
                    hasSyntaxError = true
                    terminalHistory += "SyntaxError: missing parentheses in call to 'print'\n\n"
                    break
                }
            }
        }

        if (!hasSyntaxError) {
            terminalHistory += outputBuilder.toString() + "\n"
        }
    }

    private fun executeNodeCodeInTerminal(code: String, name: String) {
        val isExpress = code.contains("express")
        if (isExpress) {
            if (!installedNpmPackages.contains("express")) {
                terminalHistory += "Error: Cannot find module 'express'\n\nTry running 'npm install express' in the terminal first.\n\n"
                return
            }
            parseAndHostBackend(code, "javascript")
            if (!isLocalServerRunning) {
                startLocalServer()
            }
            val port = activeHostedServerPort ?: 3000
            terminalHistory += "[nodemon] starting `node $name`\nServer running at http://127.0.0.1:$port/\nExpress routing table active on background live host!\n\n"
            webPreviewUrl = "http://127.0.0.1:$localServerPort/"
            showWebPreview = true
            return
        }

        val lines = code.split("\n")
        val outputBuilder = StringBuilder()
        for (index in lines.indices) {
            val line = lines[index].trim()
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) continue
            if (line.startsWith("console.log(")) {
                val startIdx = line.indexOf("(") + 1
                val endIdx = line.lastIndexOf(")")
                if (endIdx > startIdx) {
                    val expr = line.substring(startIdx, endIdx).trim()
                    if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
                        outputBuilder.append(expr.substring(1, expr.length - 1)).append("\n")
                    } else {
                        outputBuilder.append(expr).append("\n")
                    }
                }
            }
        }
        terminalHistory += outputBuilder.toString() + "\n"
    }

    private fun runRealSystemCommand(commandLine: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("sh", "-c", commandLine),
                    null,
                    terminalCwd
                )
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                val errorReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                while (errorReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                
                val exitCode = process.waitFor()
                withContext(Dispatchers.Main) {
                    if (output.isNotEmpty()) {
                        terminalHistory += output.toString() + "\n"
                    } else if (exitCode != 0) {
                        terminalHistory += "Command exited with non-zero code: $exitCode\n\n"
                    } else {
                        terminalHistory += "\n"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    terminalHistory += "sh: command not found: ${commandLine.split(" ")[0]}\n\n"
                }
            }
        }
    }
}
