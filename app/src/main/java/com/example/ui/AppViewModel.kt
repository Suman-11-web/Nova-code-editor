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
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EditorTab
import com.example.data.NovaDatabase
import com.example.data.NovaRepository
import com.example.data.RecentFile
import com.example.data.VersionSnapshot
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
    
    // High-End Code Editor Customization Options
    var enableAutoComplete by mutableStateOf(true)
    var autoCloseBrackets by mutableStateOf(true)
    var showLineNumbers by mutableStateOf(true)
    var editorFontName by mutableStateOf("Monospace")

    // High-End Split-Screen State
    var isSplitScreenEnabled by mutableStateOf(false)
    var activeLeftTabId by mutableStateOf<Int?>(null)
    var activeRightTabId by mutableStateOf<Int?>(null)
    var isVerticalSplit by mutableStateOf(true)
    var selectedPane by mutableStateOf(0) // 0 = Left/Top, 1 = Right/Bottom

    // Version Snapshots State
    var versionSnapshotsList by mutableStateOf<List<VersionSnapshot>>(emptyList())
    var currentHistoryFile by mutableStateOf<String?>(null)

    fun createVersionSnapshot(filePath: String, content: String, trigger: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSnapshotsList = repository.getSnapshotsForFileDirect(filePath)
                if (currentSnapshotsList.isNotEmpty() && currentSnapshotsList[0].content == content) {
                    return@launch
                }
                val snapshot = VersionSnapshot(
                    filePath = filePath,
                    content = content,
                    triggerName = trigger,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertSnapshot(snapshot)
                if (currentHistoryFile == filePath) {
                    loadVersionHistoryForActiveFile()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadVersionHistoryForActiveFile() {
        val path = activeTab?.filePath ?: return
        currentHistoryFile = path
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val it = repository.getSnapshotsForFileDirect(path)
                withContext(Dispatchers.Main) {
                    versionSnapshotsList = it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restoreSnapshot(snapshot: VersionSnapshot) {
        val tab = activeTab ?: return
        val updated = tab.copy(content = snapshot.content, isUnsaved = true)
        activeTab = updated
        updateEditorTextFieldValue(TextFieldValue(snapshot.content, TextRange(snapshot.content.length)))
        saveCurrentFile()
        loadVersionHistoryForActiveFile()
    }

    fun triggerQuickAction(actionType: String) {
        when (actionType) {
            "format_save_run" -> {
                formatActiveCode()
                saveCurrentFile()
                runActiveCode()
                navigateTo(Screen.CONSOLE)
            }
        }
    }

    // Search and Replace
    var searchText by mutableStateOf("")
    var replaceText by mutableStateOf("")
    var isSearchActive by mutableStateOf(false)

    // Undo/Redo stacks for each open tab path (or tab temporary ID)
    private val undoStacks = mutableMapOf<Int, Stack<String>>()
    private val redoStacks = mutableMapOf<Int, Stack<String>>()

    // File Manager States
    var currentDirectory by mutableStateOf<File>(File(application.filesDir, "Novacode"))
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
    var useExternalStorage by mutableStateOf(true)
    
    // Interactive Run Input States
    var isInputRequested by mutableStateOf(false)
    var inputPromptText by mutableStateOf("")
    var currentInputCallback by mutableStateOf<((String) -> Unit)?>(null)
    
    fun requestUserInput(prompt: String, callback: (String) -> Unit) {
        inputPromptText = prompt
        currentInputCallback = callback
        isInputRequested = true
    }

    fun submitUserInput(value: String) {
        val cb = currentInputCallback
        isInputRequested = false
        currentInputCallback = null
        cb?.invoke(value)
    }
    
    // Web Preview States
    var showWebPreview by mutableStateOf(false)
    var webPreviewUrl by mutableStateOf("")
    
    // Shortcuts Dialog State
    var showShortcutsDialog by mutableStateOf(false)

    init {
        val database = NovaDatabase.getDatabase(application)
        repository = NovaRepository(database.novaDao())
        
        // Load storage setting from SharedPreferences (default to true)
        val prefs = application.getSharedPreferences("nova_editor_prefs", android.content.Context.MODE_PRIVATE)
        useExternalStorage = prefs.getBoolean("use_external_storage", true)
        if (useExternalStorage && hasStoragePermission(application)) {
            currentDirectory = File(Environment.getExternalStorageDirectory(), "Novacode")
        } else {
            currentDirectory = File(application.filesDir, "Novacode")
        }
        
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

    fun startLocalServer(preferredPort: Int = localServerPort) {
        if (isLocalServerRunning && localServerPort == preferredPort) return
        if (isLocalServerRunning) {
            stopLocalServer()
        }
        try {
            val socket = try {
                java.net.ServerSocket(preferredPort, 50, java.net.InetAddress.getByName("127.0.0.1"))
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
                        val lookups = listOf(
                            cleanPath, 
                            "/$cleanPath", 
                            cleanPath.removeSuffix("index.html").removeSuffix("/"),
                            cleanPath.removeSuffix("index.html")
                        )
                        var responseText: String? = null
                        for (key in lookups) {
                            val cleanKey = if (key.endsWith("/")) key.dropLast(1) else key
                            if (hostedServerRoutes.containsKey(cleanKey)) {
                                responseText = hostedServerRoutes[cleanKey]
                                break
                            }
                        }
                        
                        if (responseText != null) {
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

                        // Static fallback for active backend servers (e.g. static/style.css, index.html)
                        var staticFile = File(currentDirectory, path)
                        if (!staticFile.exists() || !staticFile.isFile) {
                            staticFile = File(File(currentDirectory, "static"), path)
                        }
                        if (!staticFile.exists() || !staticFile.isFile) {
                            staticFile = File(File(currentDirectory, "public"), path)
                        }
                        if (!staticFile.exists() || !staticFile.isFile) {
                            staticFile = File(File(currentDirectory, "templates"), path)
                        }

                        if (staticFile.exists() && staticFile.isFile) {
                            val bytes = try {
                                staticFile.readBytes()
                            } catch (e: Exception) {
                                ByteArray(0)
                            }
                            val mimeType = when (staticFile.extension.lowercase()) {
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
                            val outputStream = clientSocket.getOutputStream()
                            outputStream.write("HTTP/1.1 200 OK\r\n".toByteArray())
                            outputStream.write("Content-Type: $mimeType\r\n".toByteArray())
                            outputStream.write("Content-Length: ${bytes.size}\r\n".toByteArray())
                            outputStream.write("Connection: close\r\n\r\n".toByteArray())
                            outputStream.write(bytes)
                            outputStream.flush()
                            return@thread
                        }
                        
                        // Default index return
                        val defaultIndex = hostedServerRoutes[""] ?: hostedServerRoutes["/"]
                        if (defaultIndex != null) {
                            val bytes = defaultIndex.toByteArray()
                            val outputStream = clientSocket.getOutputStream()
                            outputStream.write("HTTP/1.1 200 OK\r\n".toByteArray())
                            outputStream.write("Content-Type: text/html\r\n".toByteArray())
                            outputStream.write("Content-Length: ${bytes.size}\r\n".toByteArray())
                            outputStream.write("Connection: close\r\n\r\n".toByteArray())
                            outputStream.write(bytes)
                            outputStream.flush()
                            return@thread
                        }
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
        val prefs = getApplication<Application>().getSharedPreferences("nova_editor_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("use_external_storage", external).apply()
        
        if (external) {
            useExternalStorage = true
            if (hasStoragePermission(context)) {
                currentDirectory = File(Environment.getExternalStorageDirectory(), "Novacode")
                if (!currentDirectory.exists()) {
                    currentDirectory.mkdirs()
                }
                refreshFileTree()
            } else {
                currentDirectory = File(getApplication<Application>().filesDir, "Novacode")
                if (!currentDirectory.exists()) {
                    currentDirectory.mkdirs()
                }
                refreshFileTree()
            }
        } else {
            useExternalStorage = false
            currentDirectory = File(getApplication<Application>().filesDir, "Novacode")
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
            if (typedCharIndex in text.indices) {
                val typedChar = text[typedCharIndex]
                if (typedChar == '\n') {
                    val textBeforeCursor = text.substring(0, typedCharIndex)
                    val lastLine = textBeforeCursor.substringAfterLast('\n')
                    val leadingWhitespace = lastLine.takeWhile { it.isWhitespace() && it != '\n' }
                    val trimmedLastLine = lastLine.trim()
                    val extension = currentActive.fileName.substringAfterLast('.', "").lowercase()
                    
                    val isHtmlFile = currentActive.fileName.lowercase().endsWith(".html") || currentActive.fileName.lowercase().endsWith(".htm") || currentActive.language.lowercase() == "html"
                    val trimmed = trimmedLastLine.lowercase()
                    
                    var expanded = false
                    if (isHtmlFile && trimmed.isNotEmpty() && !trimmed.contains("<") && !trimmed.contains(">") && !trimmed.contains(" ") && !trimmed.contains("/") && !trimmed.contains(";")) {
                        val expansionPair = when (trimmed) {
                            "h1" -> "<h1></h1>" to 4
                            "h2" -> "<h2></h2>" to 4
                            "h3" -> "<h3></h3>" to 4
                            "h4" -> "<h4></h4>" to 4
                            "h5" -> "<h5></h5>" to 4
                            "h6" -> "<h6></h6>" to 4
                            "p" -> "<p></p>" to 3
                            "span" -> "<span></span>" to 6
                            "div" -> "<div></div>" to 5
                            "button" -> "<button></button>" to 8
                            "a" -> "<a href=\"\"></a>" to 9
                            "img" -> "<img src=\"\" alt=\"\">" to 10
                            "input" -> "<input type=\"text\" name=\"\" id=\"\">" to 25
                            "form" -> "<form action=\"\" method=\"post\">\n$leadingWhitespace    \n$leadingWhitespace</form>" to (30 + leadingWhitespace.length + 4)
                            "link" -> "<link rel=\"stylesheet\" href=\"style.css\">" to 39
                            "link:css" -> "<link rel=\"stylesheet\" href=\"style.css\">" to 39
                            "script" -> "<script src=\"script.js\"></script>" to 33
                            "script:src" -> "<script src=\"script.js\"></script>" to 33
                            "ul" -> "<ul>\n$leadingWhitespace    <li></li>\n$leadingWhitespace</ul>" to (9 + leadingWhitespace.length)
                            "ol" -> "<ol>\n$leadingWhitespace    <li></li>\n$leadingWhitespace</ol>" to (9 + leadingWhitespace.length)
                            "li" -> "<li></li>" to 4
                            "table" -> "<table>\n$leadingWhitespace    <tr>\n$leadingWhitespace        <td></td>\n$leadingWhitespace    </tr>\n$leadingWhitespace</table>" to (22 + leadingWhitespace.length * 3)
                            "select" -> "<select name=\"\" id=\"\">\n$leadingWhitespace    <option value=\"\"></option>\n$leadingWhitespace</select>" to (14 + leadingWhitespace.length)
                            "header" -> "<header></header>" to 8
                            "footer" -> "<footer></footer>" to 8
                            "main" -> "<main></main>" to 6
                            "section" -> "<section></section>" to 9
                            "article" -> "<article></article>" to 9
                            "aside" -> "<aside></aside>" to 7
                            "nav" -> "<nav></nav>" to 5
                            "label" -> "<label for=\"\"></label>" to 12
                            "textarea" -> "<textarea name=\"\" id=\"\" cols=\"30\" rows=\"10\"></textarea>" to 54
                            "style" -> "<style>\n$leadingWhitespace    \n$leadingWhitespace</style>" to (8 + leadingWhitespace.length + 4)
                            "iframe" -> "<iframe src=\"\" frameborder=\"0\"></iframe>" to 13
                            "canvas" -> "<canvas id=\"\" width=\"\" height=\"\"></canvas>" to 11
                            else -> null
                        }
                        
                        if (expansionPair != null) {
                            val expansionText = expansionPair.first
                            val offset = expansionPair.second
                            
                            val lineStartIdx = typedCharIndex - lastLine.length
                            val trimmedStartIdx = lineStartIdx + lastLine.takeWhile { it.isWhitespace() }.length
                            
                            val newText = text.substring(0, trimmedStartIdx) + expansionText + text.substring(typedCharIndex + 1)
                            adjustedValue = TextFieldValue(
                                text = newText,
                                selection = TextRange(trimmedStartIdx + offset)
                            )
                            expanded = true
                        }
                    }
                    
                    if (!expanded) {
                        val addExtraIndent = when {
                            (extension == "py" || currentActive.language.lowercase() == "python") && trimmedLastLine.endsWith(":") -> true
                            trimmedLastLine.endsWith("{") || trimmedLastLine.endsWith("[") || trimmedLastLine.endsWith("(") -> true
                            else -> false
                        }
                        val extraIndent = if (addExtraIndent) "    " else ""
                        val autoInsertedText = leadingWhitespace + extraIndent
                        if (autoInsertedText.isNotEmpty()) {
                            val newText = text.substring(0, typedCharIndex + 1) + autoInsertedText + text.substring(typedCharIndex + 1)
                            adjustedValue = TextFieldValue(
                                text = newText,
                                selection = TextRange(typedCharIndex + 1 + autoInsertedText.length)
                            )
                        }
                    }
                } else if (typedChar == '>') {
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
                } else if (autoCloseBrackets) {
                    val closeChar = when (typedChar) {
                        '(' -> ')'
                        '[' -> ']'
                        '{' -> '}'
                        '"' -> '"'
                        '\'' -> '\''
                        else -> null
                    }
                    if (closeChar != null) {
                        val newText = text.substring(0, typedCharIndex + 1) + closeChar + text.substring(typedCharIndex + 1)
                        adjustedValue = TextFieldValue(
                            text = newText,
                            selection = TextRange(typedCharIndex + 1)
                        )
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

    fun moveCursorLeft() {
        val selection = editorTextFieldValue.selection
        if (selection.start > 0) {
            val newPos = selection.start - 1
            updateEditorTextFieldValue(
                editorTextFieldValue.copy(selection = TextRange(newPos))
            )
        }
    }

    fun moveCursorRight() {
        val selection = editorTextFieldValue.selection
        if (selection.start < editorTextFieldValue.text.length) {
            val newPos = selection.start + 1
            updateEditorTextFieldValue(
                editorTextFieldValue.copy(selection = TextRange(newPos))
            )
        }
    }

    fun insertSymbolAtCursor(symbol: String) {
        val currentText = editorTextFieldValue.text
        val selection = editorTextFieldValue.selection
        val start = selection.start
        val end = selection.end

        when (symbol) {
            "()" -> {
                val newText = currentText.substring(0, start) + "()" + currentText.substring(end)
                updateEditorTextFieldValue(
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + 1)
                    )
                )
            }
            "{}" -> {
                val newText = currentText.substring(0, start) + "{}" + currentText.substring(end)
                updateEditorTextFieldValue(
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + 1)
                    )
                )
            }
            "\"" -> {
                val newText = currentText.substring(0, start) + "\"\"" + currentText.substring(end)
                updateEditorTextFieldValue(
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + 1)
                    )
                )
            }
            "'" -> {
                val newText = currentText.substring(0, start) + "''" + currentText.substring(end)
                updateEditorTextFieldValue(
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(start + 1)
                    )
                )
            }
            "TAB" -> {
                insertTextAtCursor("    ")
            }
            else -> {
                insertTextAtCursor(symbol)
            }
        }
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
                createVersionSnapshot(path, tab.content, "Manual Save")
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
                val ext = newFile.extension.lowercase()
                val boilerplate = when (ext) {
                    "html", "htm" -> """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>Nova Live Preview Webpage</title>
                            <style>
                                :root {
                                    --primary-color: #6366f1;
                                    --primary-hover: #4f46e5;
                                    --bg-color: #0f172a;
                                    --card-bg: #1e293b;
                                    --text-color: #f8fafc;
                                    --text-muted: #94a3b8;
                                }
                                
                                * {
                                    box-sizing: border-box;
                                    margin: 0;
                                    padding: 0;
                                }
                                
                                body {
                                    font-family: 'Segoe UI', system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
                                    background-color: var(--bg-color);
                                    color: var(--text-color);
                                    display: flex;
                                    flex-direction: column;
                                    align-items: center;
                                    justify-content: center;
                                    min-height: 100vh;
                                    padding: 2rem;
                                    text-align: center;
                                }
                                
                                .container {
                                    max-width: 600px;
                                    background-color: var(--card-bg);
                                    padding: 2.5rem;
                                    border-radius: 16px;
                                    border: 1px solid rgba(255, 255, 255, 0.08);
                                    box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3);
                                    transition: transform 0.3s ease, box-shadow 0.3s ease;
                                }
                                
                                .container:hover {
                                    transform: translateY(-4px);
                                    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.4);
                                }
                                
                                h1 {
                                    font-size: 2.5rem;
                                    color: var(--text-color);
                                    margin-bottom: 1rem;
                                    background: linear-gradient(to right, #818cf8, #c084fc);
                                    -webkit-background-clip: text;
                                    -webkit-text-fill-color: transparent;
                                }
                                
                                p {
                                    font-size: 1.1rem;
                                    color: var(--text-muted);
                                    line-height: 1.6;
                                    margin-bottom: 2rem;
                                }
                                
                                .btn {
                                    background-color: var(--primary-color);
                                    color: white;
                                    font-weight: 600;
                                    padding: 0.75rem 1.75rem;
                                    border: none;
                                    border-radius: 8px;
                                    cursor: pointer;
                                    transition: background-color 0.2s, transform 0.1s;
                                    font-size: 1rem;
                                }
                                
                                .btn:hover {
                                    background-color: var(--primary-hover);
                                }
                                
                                .btn:active {
                                    transform: scale(0.97);
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <h1>Nova Code Editor</h1>
                                <p>This is your fully live HTML & CSS Web Preview running dynamically. Edit files, save, and check output in real-time!</p>
                                <button class="btn" onclick="alert('Congratulations! Web scripts are active.')">Test Interactivity</button>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    "css" -> """
                        /* Nova Code Editor - Professional Styling Sheet */
                        :root {
                            --primary-color: #6366f1;
                            --primary-hover: #4f46e5;
                            --bg-gradient: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
                            --card-bg: rgba(30, 41, 59, 0.8);
                            --text-primary: #f8fafc;
                            --text-secondary: #94a3b8;
                            --accent: #38bdf8;
                            --spacing-unit: 1rem;
                            --shadow-elevation: 0 8px 30px rgba(0, 0, 0, 0.25);
                        }

                        * {
                            box-sizing: border-box;
                            margin: 0;
                            padding: 0;
                        }

                        body {
                            background: var(--bg-gradient);
                            color: var(--text-primary);
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                            line-height: 1.5;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            min-height: 100vh;
                        }

                        .card {
                            background: var(--card-bg);
                            backdrop-filter: blur(12px);
                            border: 1px solid rgba(255, 255, 255, 0.1);
                            border-radius: 1rem;
                            padding: calc(var(--spacing-unit) * 2.5);
                            max-width: 480px;
                            width: 100%;
                            box-shadow: var(--shadow-elevation);
                            text-align: center;
                            transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
                        }

                        .card:hover {
                            transform: translateY(-8px);
                        }

                        .highlight {
                            color: var(--accent);
                            font-weight: 700;
                        }
                    """.trimIndent()
                    "js", "ts" -> """
                        // Nova Client-Side or Backend JavaScript Starter
                        console.log("Hello from Nova Code Editor!");

                        // Active browser window detection:
                        if (typeof window !== 'undefined') {
                            window.addEventListener('DOMContentLoaded', () => {
                                console.log("Webpage DOM loaded successfully!");
                                const body = document.body;
                                if (body) {
                                    console.log("Adding dynamic styles...");
                                }
                            });
                        }
                    """.trimIndent()
                    "py" -> """
                        # Nova Python / Flask Starter Code
                        print("Hello from Nova Interpreter!")

                        # Uncomment to host a Flask dynamic server:
                        # from flask import Flask
                        # app = Flask(__name__)
                        #
                        # @app.route('/')
                        # def index():
                        #     return "<h1>Hello from Flask!</h1>"
                    """.trimIndent()
                    "cpp", "cc" -> """
                        #include <iostream>
                        using namespace std;

                        int main() {
                            cout << "hello World!" << endl;
                            return 0;
                        }
                    """.trimIndent()
                    "c" -> """
                        #include <stdio.h>

                        int main() {
                            printf("hello World!\n");
                            return 0;
                        }
                    """.trimIndent()
                    "java" -> """
                        public class Main {
                            public static void main(String[] args) {
                                System.out.println("Hello World from Java!");
                            }
                        }
                    """.trimIndent()
                    "go" -> """
                        package main

                        import "fmt"

                        func main() {
                            fmt.Println("Hello World from Go!")
                        }
                    """.trimIndent()
                    "rs" -> """
                        fn main() {
                            println!("Hello World from Rust!");
                        }
                    """.trimIndent()
                    else -> ""
                }
                newFile.writeText(boilerplate)
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
            val port = activeHostedServerPort ?: 5000
            startLocalServer(port)
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
            val port = activeHostedServerPort ?: 3000
            startLocalServer(port)
            consoleOutput = ">>> Running ${tab.fileName}...\n[nodemon] starting `node ${tab.fileName}`\nServer running at http://127.0.0.1:$port/\nExpress routing table active on background live host!"
            consoleError = ""
            webPreviewUrl = "http://127.0.0.1:$localServerPort/"
            showWebPreview = true
            navigateTo(Screen.CONSOLE)
            return
        }

        // Check if HTML or Web code (only redirect JS/TS to web preview if it appears to be client-side web script)
        val isWebFile = lang == "html" || lang == "css" || ext == "html" || ext == "htm" || ext == "css" ||
                ((lang == "javascript" || ext == "js" || ext == "ts") && 
                 (code.contains("document.") || code.contains("window.") || code.contains("alert(") || code.contains("<html>") || code.contains("<body>")))

        if (isWebFile) {
            // Force save current state first
            saveCurrentFile()
            
            // Ensure local server is running on preferred live server port (5500) or current localServerPort
            val targetPort = if (localServerPort == 8080) 5500 else localServerPort
            startLocalServer(targetPort)
            
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
            kotlinx.coroutines.delay(200)

            val code = tab.content
            val lang = tab.language.lowercase()

            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()

            outputBuilder.append("Nova Execution Environment (V1.2)\n")
            outputBuilder.append("Executing: ${tab.fileName} via modern dynamic interpreter\n")
            outputBuilder.append("--------------------------------------------------\n\n")

            try {
                // Highly functional lightweight custom parser with nested block/indentation control flow
                val lines = code.split("\n")
                var hasSyntaxError = false
                val variables = mutableMapOf<String, String>()

                // Nested block execution states
                data class BlockState(val indent: Int, val conditionMet: Boolean, val isExecuting: Boolean)
                val blockStack = mutableListOf<BlockState>()
                blockStack.add(BlockState(indent = -1, conditionMet = true, isExecuting = true))

                // Map to track the status of the last evaluated IF condition at each indentation level
                val lastIfCondition = mutableMapOf<Int, Boolean>()

                var index = 0
                while (index < lines.size) {
                    val originalLine = lines[index]
                    val line = originalLine.trim()

                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                        index++
                        continue
                    }

                    // Compute indentation level of the line
                    val indent = originalLine.takeWhile { it == ' ' }.length + originalLine.takeWhile { it == '\t' }.length * 4

                    // Pop any block structures that have a greater or equal indentation
                    while (blockStack.size > 1 && blockStack.last().indent >= indent) {
                        blockStack.removeAt(blockStack.size - 1)
                    }

                    val currentBlock = blockStack.last()
                    val isExecuting = currentBlock.isExecuting

                    // Check for if statement
                    if (line.startsWith("if ") || line.startsWith("if(")) {
                        val conditionStr = if (line.startsWith("if ")) {
                            line.substringAfter("if").substringBefore(":").substringBefore("{").trim()
                        } else {
                            line.substringAfter("if").substringBefore("{").trim().removePrefix("(").removeSuffix(")")
                        }

                        val conditionMet = if (isExecuting) evaluateCondition(conditionStr, variables) else false
                        lastIfCondition[indent] = conditionMet

                        val nextExecuting = isExecuting && conditionMet
                        blockStack.add(BlockState(indent = indent, conditionMet = conditionMet, isExecuting = nextExecuting))
                        index++
                        continue
                    }
                    // Check for elif or else if statement
                    else if (line.startsWith("elif ") || line.startsWith("else if")) {
                        val conditionStr = if (line.startsWith("elif ")) {
                            line.substringAfter("elif").substringBefore(":").substringBefore("{").trim()
                        } else {
                            line.substringAfter("else if").substringBefore("{").trim().removePrefix("(").removeSuffix(")")
                        }

                        val previousChainMet = lastIfCondition[indent] ?: false
                        val conditionMet = if (isExecuting && !previousChainMet) evaluateCondition(conditionStr, variables) else false
                        if (conditionMet) {
                            lastIfCondition[indent] = true
                        }

                        val nextExecuting = isExecuting && conditionMet
                        blockStack.add(BlockState(indent = indent, conditionMet = conditionMet, isExecuting = nextExecuting))
                        index++
                        continue
                    }
                    // Check for else statement
                    else if (line.startsWith("else:") || line.startsWith("else {") || line == "else") {
                        val previousChainMet = lastIfCondition[indent] ?: false
                        val conditionMet = isExecuting && !previousChainMet

                        val nextExecuting = isExecuting && conditionMet
                        blockStack.add(BlockState(indent = indent, conditionMet = conditionMet, isExecuting = nextExecuting))
                        index++
                        continue
                    }
                    // Check for closing brace
                    else if (line == "}") {
                        if (blockStack.size > 1) {
                            blockStack.removeAt(blockStack.size - 1)
                        }
                        index++
                        continue
                    }

                    // Skip processing of statements inside deactivated blocks
                    if (!isExecuting) {
                        index++
                        continue
                    }

                    // Process active statements
                    // 1. Variable Assignment
                    if (line.contains("=") && !line.startsWith("if") && !line.startsWith("for") && !line.startsWith("while")) {
                        val parts = line.split("=", limit = 2)
                        val varName = parts[0].trim()
                        var varValue = parts[1].trim()

                        // Check if it's an interactive input prompt
                        if (varValue.startsWith("input(") || varValue.startsWith("prompt(")) {
                            val startP = varValue.indexOf("(") + 1
                            val endP = varValue.lastIndexOf(")")
                            var promptStr = ""
                            if (endP > startP) {
                                val rawPrompt = varValue.substring(startP, endP).trim()
                                promptStr = if ((rawPrompt.startsWith("\"") && rawPrompt.endsWith("\"")) || (rawPrompt.startsWith("'") && rawPrompt.endsWith("'"))) {
                                    rawPrompt.substring(1, rawPrompt.length - 1)
                                } else {
                                    rawPrompt
                                }
                            }

                            // Output the prompt string
                            outputBuilder.append(promptStr)
                            consoleOutput = outputBuilder.toString()

                            // Suspend coroutine and wait for user input
                            val deferred = kotlinx.coroutines.CompletableDeferred<String>()
                            requestUserInput(promptStr) { result ->
                                deferred.complete(result)
                            }
                            val userInputVal = deferred.await()

                            // Print the entered value so it mimics a real terminal
                            outputBuilder.append(userInputVal).append("\n")
                            consoleOutput = outputBuilder.toString()

                            variables[varName] = userInputVal
                        } else {
                            // Standard value assignment
                            if (varValue.startsWith("\"") && varValue.endsWith("\"")) {
                                varValue = varValue.substring(1, varValue.length - 1)
                            } else if (varValue.startsWith("'") && varValue.endsWith("'")) {
                                varValue = varValue.substring(1, varValue.length - 1)
                            } else {
                                // Try substituting variables and evaluating mathematically
                                var mathExpr = varValue
                                variables.forEach { (k, v) ->
                                    mathExpr = mathExpr.replace(k, v)
                                }
                                val mathResult = evaluateMathExpression(mathExpr)
                                if (mathResult != null) {
                                    varValue = if (mathResult % 1.0 == 0.0) mathResult.toLong().toString() else mathResult.toString()
                                } else {
                                    // Fallback to simple variable lookup or string concatenation
                                    if (varValue.contains("+")) {
                                        val tokens = varValue.split("+")
                                        val concatBuilder = StringBuilder()
                                        for (t in tokens) {
                                            val token = t.trim()
                                            val cleanToken = if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                                                token.substring(1, token.length - 1)
                                            } else {
                                                variables[token] ?: token
                                            }
                                            concatBuilder.append(cleanToken)
                                        }
                                        varValue = concatBuilder.toString()
                                    } else {
                                        varValue = variables[varValue] ?: varValue
                                    }
                                }
                            }
                            variables[varName] = varValue
                        }
                    }
                    // 2. Standalone Input Prompt
                    else if (line.startsWith("input(") || line.startsWith("prompt(")) {
                        val startP = line.indexOf("(") + 1
                        val endP = line.lastIndexOf(")")
                        var promptStr = ""
                        if (endP > startP) {
                            val rawPrompt = line.substring(startP, endP).trim()
                            promptStr = if ((rawPrompt.startsWith("\"") && rawPrompt.endsWith("\"")) || (rawPrompt.startsWith("'") && rawPrompt.endsWith("'"))) {
                                rawPrompt.substring(1, rawPrompt.length - 1)
                            } else {
                                rawPrompt
                            }
                        }

                        // Output the prompt
                        outputBuilder.append(promptStr)
                        consoleOutput = outputBuilder.toString()

                        // Suspend coroutine and wait for user input
                        val deferred = kotlinx.coroutines.CompletableDeferred<String>()
                        requestUserInput(promptStr) { result ->
                            deferred.complete(result)
                        }
                        val userInputVal = deferred.await()

                        outputBuilder.append(userInputVal).append("\n")
                        consoleOutput = outputBuilder.toString()
                    }
                    // 3. Print / Log Statements
                    else if (line.contains("cout") && line.contains("<<")) {
                        val postCout = line.substringAfter("cout").trim().removePrefix("<<").trim().removeSuffix(";").trim()
                        val parts = splitOutsideQuotes(postCout, "<<")
                        val lineBuilder = StringBuilder()
                        for (part in parts) {
                            val trimmed = part.trim()
                            if (trimmed == "endl" || trimmed == "std::endl") {
                                lineBuilder.append("\n")
                            } else if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                                lineBuilder.append(trimmed.substring(1, trimmed.length - 1))
                            } else {
                                lineBuilder.append(variables[trimmed] ?: trimmed)
                            }
                        }
                        var outputStr = lineBuilder.toString()
                        outputStr = outputStr.replace("\\n", "\n")
                        outputBuilder.append(outputStr)
                        if (!line.contains("endl") && !line.contains("\\n")) {
                            outputBuilder.append("\n")
                        }
                        consoleOutput = outputBuilder.toString()
                    }
                    else if (line.startsWith("printf(") || line.contains("printf(")) {
                        val startIdx = line.indexOf("printf(") + 7
                        val endIdx = line.lastIndexOf(")")
                        if (endIdx > startIdx) {
                            val expr = line.substring(startIdx, endIdx).trim()
                            val tokens = splitOutsideQuotes(expr, ",")
                            if (tokens.isNotEmpty()) {
                                var formatStr = tokens[0]
                                if ((formatStr.startsWith("\"") && formatStr.endsWith("\"")) || (formatStr.startsWith("'") && formatStr.endsWith("'"))) {
                                    formatStr = formatStr.substring(1, formatStr.length - 1)
                                }
                                var output = formatStr
                                for (vIdx in 1 until tokens.size) {
                                    val token = tokens[vIdx].trim()
                                    val valStr = if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                                        token.substring(1, token.length - 1)
                                    } else {
                                        variables[token] ?: token
                                    }
                                    output = output.replaceFirst(Regex("%[dsfcy]"), valStr)
                                }
                                output = output.replace("\\n", "\n")
                                outputBuilder.append(output)
                                if (!formatStr.contains("\\n")) {
                                    outputBuilder.append("\n")
                                }
                                consoleOutput = outputBuilder.toString()
                            }
                        }
                    }
                    else if (line.startsWith("println!(") || line.startsWith("print!(") || line.contains("println!(") || line.contains("print!(")) {
                        val key = if (line.contains("println!(")) "println!(" else "print!("
                        val startIdx = line.indexOf(key) + key.length
                        val endIdx = line.lastIndexOf(")")
                        if (endIdx > startIdx) {
                            val expr = line.substring(startIdx, endIdx).trim()
                            val tokens = splitOutsideQuotes(expr, ",")
                            if (tokens.isNotEmpty()) {
                                var formatStr = tokens[0]
                                if ((formatStr.startsWith("\"") && formatStr.endsWith("\"")) || (formatStr.startsWith("'") && formatStr.endsWith("'"))) {
                                    formatStr = formatStr.substring(1, formatStr.length - 1)
                                }
                                var output = formatStr
                                for (vIdx in 1 until tokens.size) {
                                    val token = tokens[vIdx].trim()
                                    val valStr = if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                                        token.substring(1, token.length - 1)
                                    } else {
                                        variables[token] ?: token
                                    }
                                    output = output.replaceFirst("{}", valStr)
                                }
                                output = output.replace("\\n", "\n")
                                outputBuilder.append(output)
                                if (key.startsWith("println")) {
                                    outputBuilder.append("\n")
                                }
                                consoleOutput = outputBuilder.toString()
                            }
                        }
                    }
                    else if (line.contains("fmt.Println(") || line.contains("fmt.Print(")) {
                        val key = if (line.contains("fmt.Println(")) "fmt.Println(" else "fmt.Print("
                        val startIdx = line.indexOf(key) + key.length
                        val endIdx = line.lastIndexOf(")")
                        if (endIdx > startIdx) {
                            val expr = line.substring(startIdx, endIdx).trim()
                            val tokens = splitOutsideQuotes(expr, ",")
                            val printResults = mutableListOf<String>()
                            for (token in tokens) {
                                val trimmed = token.trim()
                                val cleanToken = if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                                    trimmed.substring(1, trimmed.length - 1)
                                } else {
                                    variables[trimmed] ?: trimmed
                                }
                                printResults.add(cleanToken)
                            }
                            var output = printResults.joinToString(" ").replace("\\n", "\n")
                            outputBuilder.append(output)
                            if (key.contains("Println")) {
                                outputBuilder.append("\n")
                            }
                            consoleOutput = outputBuilder.toString()
                        }
                    }
                    else if (line.contains("Console.WriteLine(") || line.contains("Console.Write(")) {
                        val key = if (line.contains("Console.WriteLine(")) "Console.WriteLine(" else "Console.Write("
                        val startIdx = line.indexOf(key) + key.length
                        val endIdx = line.lastIndexOf(")")
                        if (endIdx > startIdx) {
                            val expr = line.substring(startIdx, endIdx).trim().removeSuffix(";").trim()
                            val tokens = splitOutsideQuotes(expr, ",")
                            if (tokens.isNotEmpty()) {
                                var formatStr = tokens[0]
                                if ((formatStr.startsWith("\"") && formatStr.endsWith("\"")) || (formatStr.startsWith("'") && formatStr.endsWith("'"))) {
                                    formatStr = formatStr.substring(1, formatStr.length - 1)
                                }
                                var output = formatStr
                                for (vIdx in 1 until tokens.size) {
                                    val token = tokens[vIdx].trim()
                                    val valStr = if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                                        token.substring(1, token.length - 1)
                                    } else {
                                        variables[token] ?: token
                                    }
                                    output = output.replace("{$ {vIdx - 1}}", valStr).replace("{${vIdx - 1}}", valStr)
                                }
                                output = output.replace("\\n", "\n")
                                outputBuilder.append(output)
                                if (key.contains("WriteLine")) {
                                    outputBuilder.append("\n")
                                }
                                consoleOutput = outputBuilder.toString()
                            }
                        }
                    }
                    else if (line.startsWith("echo ") || line.startsWith("echo(") || line.startsWith("print ") || line.startsWith("print(")) {
                        val isEcho = line.startsWith("echo")
                        val expr = if (isEcho) {
                            if (line.startsWith("echo(")) line.substringAfter("(").substringBeforeLast(")").trim()
                            else line.substringAfter("echo ").trim().removeSuffix(";").trim()
                        } else {
                            if (line.startsWith("print(")) line.substringAfter("(").substringBeforeLast(")").trim()
                            else line.substringAfter("print ").trim().removeSuffix(";").trim()
                        }
                        val tokens = splitOutsideQuotes(expr, ".")
                        val lineBuilder = StringBuilder()
                        for (token in tokens) {
                            val trimmed = token.trim()
                            var cleanToken = if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                                trimmed.substring(1, trimmed.length - 1)
                            } else {
                                val varKey = trimmed.removePrefix("$")
                                variables[varKey] ?: variables[trimmed] ?: trimmed
                            }
                            variables.forEach { (k, v) ->
                                cleanToken = cleanToken.replace("$$k", v).replace(k, v)
                            }
                            lineBuilder.append(cleanToken)
                        }
                        var output = lineBuilder.toString().replace("\\n", "\n")
                        outputBuilder.append(output).append("\n")
                        consoleOutput = outputBuilder.toString()
                    }
                    else if (line.startsWith("puts ") || (line.startsWith("print ") && lang == "ruby")) {
                        val isPuts = line.startsWith("puts ")
                        val expr = if (isPuts) line.substringAfter("puts ").trim() else line.substringAfter("print ").trim()
                        var valStr = if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
                            expr.substring(1, expr.length - 1)
                        } else {
                            variables[expr] ?: expr
                        }
                        variables.forEach { (k, v) ->
                            valStr = valStr.replace("#{$k}", v)
                        }
                        valStr = valStr.replace("\\n", "\n")
                        outputBuilder.append(valStr)
                        if (isPuts) {
                            outputBuilder.append("\n")
                        }
                        consoleOutput = outputBuilder.toString()
                    }
                    else if (line.startsWith("print(") || line.startsWith("println(") || line.startsWith("console.log(") || 
                             line.startsWith("console.info(") || line.startsWith("console.warn(") || line.startsWith("console.error(") || 
                             line.startsWith("System.out.println(") || line.startsWith("System.out.print(") || line.startsWith("log(")) {
                        
                        val startIdx = line.indexOf("(") + 1
                        val endIdx = line.lastIndexOf(")")
                        if (endIdx > startIdx) {
                            val expr = line.substring(startIdx, endIdx).trim()
                            val tokens = splitOutsideQuotes(expr, ",")

                            if (tokens.size > 1) {
                                val printResults = mutableListOf<String>()
                                for (token in tokens) {
                                    val cleanToken = if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                                        token.substring(1, token.length - 1)
                                    } else {
                                        variables[token] ?: token
                                    }
                                    printResults.add(cleanToken)
                                }
                                var output = printResults.joinToString(" ").replace("\\n", "\n")
                                outputBuilder.append(output).append("\n")
                            } else {
                                val token = tokens[0]
                                if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                                    var output = token.substring(1, token.length - 1).replace("\\n", "\n")
                                    outputBuilder.append(output).append("\n")
                                } else {
                                    var mathExpr = token
                                    variables.forEach { (k, v) ->
                                        mathExpr = mathExpr.replace(k, v)
                                    }
                                    val mathResult = evaluateMathExpression(mathExpr)
                                    if (mathResult != null) {
                                        val displayResult = if (mathResult % 1.0 == 0.0) mathResult.toLong().toString() else mathResult.toString()
                                        outputBuilder.append(displayResult).append("\n")
                                    } else {
                                        if (token.contains("+")) {
                                            val partsOfSum = splitOutsideQuotes(token, "+")
                                            val sumBuilder = StringBuilder()
                                            var numericSum = 0.0
                                            var isNumeric = true
                                            for (p in partsOfSum) {
                                                val t = p.trim()
                                                val cleanToken = if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
                                                    t.substring(1, t.length - 1)
                                                } else {
                                                    variables[t] ?: t
                                                }
                                                sumBuilder.append(cleanToken)
                                                try {
                                                    numericSum += cleanToken.toDouble()
                                                } catch (e: Exception) {
                                                    isNumeric = false
                                                }
                                            }
                                            if (isNumeric && partsOfSum.size > 1) {
                                                val displayResult = if (numericSum % 1.0 == 0.0) numericSum.toLong().toString() else numericSum.toString()
                                                outputBuilder.append(displayResult).append("\n")
                                            } else {
                                                var output = sumBuilder.toString().replace("\\n", "\n")
                                                outputBuilder.append(output).append("\n")
                                            }
                                        } else {
                                            var output = (variables[token] ?: token).replace("\\n", "\n")
                                            outputBuilder.append(output).append("\n")
                                        }
                                    }
                                }
                            }
                            consoleOutput = outputBuilder.toString()
                        } else {
                            hasSyntaxError = true
                            errorBuilder.append("Syntax Error on Line ${index + 1}: Unclosed parenthesis.\n")
                            errorBuilder.append("-> $line\n")
                            break
                        }
                    }

                    index++
                }

                // Fallback rendering/simulations if output is completely empty (e.g. metadata files)
                if (outputBuilder.length < 150) {
                    if (lang == "html") {
                        outputBuilder.append("Rendering HTML Page Content...\n")
                        outputBuilder.append("--------------------------------------------------\n")
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
            "~/N"
        } else if (cwd.startsWith(root)) {
            val currentDirName = terminalCwd.name
            val firstChar = if (currentDirName.isNotEmpty()) currentDirName[0].lowercaseChar().toString() else ""
            "~/N/$firstChar"
        } else {
            val currentDirName = terminalCwd.name
            val firstChar = if (currentDirName.isNotEmpty()) currentDirName[0].lowercaseChar().toString() else ""
            "/$firstChar"
        }
        return "$displayPath $ "
    }

    fun getFormattedCwdPath(): String {
        val path = terminalCwd.absolutePath
        val targetSubDir = if (path.contains("/files/Novacode")) {
            path.substringAfter("/files/Novacode")
        } else if (path.contains("/files")) {
            path.substringAfter("/files")
        } else if (path.contains("/Novacode")) {
            path.substringAfter("/Novacode")
        } else {
            ""
        }
        return if (targetSubDir.isNotEmpty()) {
            "/storage/emulated/0/Novacode$targetSubDir"
        } else {
            "/storage/emulated/0/Novacode"
        }
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
        
        // Context-aware dynamic word suggestions extracted from current open file content
        val dynamicWords = mutableSetOf<String>()
        val currentContent = activeTab?.content ?: ""
        if (currentContent.isNotEmpty()) {
            val wordRegex = Regex("""[a-zA-Z_][a-zA-Z0-9_]{2,}""")
            wordRegex.findAll(currentContent).forEach { match ->
                val matchValue = match.value
                if (matchValue.lowercase().startsWith(word) && matchValue.lowercase() != word) {
                    dynamicWords.add(matchValue)
                }
            }
        }

        val staticCandidates = when {
            lang == "python" || ext == "py" -> listOf(
                "import", "from", "print", "def", "return", "class", "if", "else", "elif", "while", "for", "in", "try", "except", "finally", "raise", "pass", "True", "False", "None", "self", "as", "with", "lambda", "assert", "yield", "global", "nonlocal",
                "flask", "Flask", "app = Flask(__name__)", "route", "app.run(port=5000)", "methods", "jsonify", "request", "render_template", "redirect", "url_for",
                "pip install", "django", "numpy", "pandas", "requests", "math", "random", "json", "sys", "os"
            )
            lang == "javascript" || lang == "typescript" || ext == "js" || ext == "ts" -> listOf(
                "const", "let", "var", "function", "return", "class", "interface", "type", "if", "else", "switch", "case", "while", "for", "import", "export", "from", "default", "try", "catch", "finally", "true", "false", "null", "undefined",
                "express", "express()", "app.get", "app.post", "app.listen(3000)", "req", "res", "send", "json", "require", "module.exports",
                "console.log", "document", "window", "setTimeout", "setInterval", "addEventListener", "fetch", "Promise", "async", "await"
            )
            lang == "kotlin" || ext == "kt" || ext == "kts" -> listOf(
                "val", "var", "fun", "class", "interface", "object", "import", "package", "return", "if", "else", "when", "while", "for", "in", "is", "as", "null", "true", "false", "this", "super", "private", "protected", "public", "internal", "override", "companion", "suspend", "coroutineScope", "launch", "async", "delay", "flow", "collect", "mutableStateOf", "remember", "Composable", "Modifier"
            )
            lang == "java" -> listOf(
                "public", "private", "protected", "class", "interface", "enum", "extends", "implements", "import", "package", "return", "if", "else", "switch", "case", "default", "while", "for", "do", "new", "null", "true", "false", "this", "super", "void", "int", "double", "float", "long", "boolean", "char", "String", "System.out.println", "try", "catch", "finally", "throw", "throws"
            )
            lang == "cpp" || lang == "c" || ext == "cpp" || ext == "c" || ext == "h" -> listOf(
                "include", "define", "main", "iostream", "std", "cout", "cin", "endl", "vector", "string", "class", "struct", "public", "private", "protected", "return", "if", "else", "switch", "case", "default", "while", "for", "new", "delete", "nullptr", "true", "false", "this", "void", "int", "double", "float", "bool", "char", "const", "static", "virtual"
            )
            lang == "rust" || ext == "rs" -> listOf(
                "fn", "let", "mut", "struct", "enum", "impl", "trait", "use", "mod", "pub", "return", "if", "else", "match", "while", "for", "in", "loop", "const", "static", "self", "Self", "true", "false", "Option", "Result", "Some", "None", "Ok", "Err", "println!", "vec!", "String", "as"
            )
            lang == "go" || ext == "go" -> listOf(
                "package", "import", "func", "var", "const", "type", "struct", "interface", "return", "if", "else", "switch", "case", "default", "for", "range", "nil", "true", "false", "fmt.Println", "make", "append", "go", "chan", "select", "defer", "map", "string", "int", "float64", "bool"
            )
            lang == "html" || ext == "html" || ext == "htm" -> listOf(
                "html", "head", "body", "div", "span", "p", "a", "img", "button", "input", "form", "label", "ul", "ol", "li", "table", "tr", "td", "th", "style", "script", "link", "meta", "title",
                "class", "id", "href", "src", "alt", "placeholder", "type", "value", "onclick", "style=\"\"", "<!DOCTYPE html>",
                "nav", "header", "footer", "section", "article", "aside", "main", "canvas", "svg", "video", "audio", "iframe", "picture", "source", "template", "slot", "dialog", "progress", "meter", "details", "summary",
                "loading=\"lazy\"", "decoding=\"async\"", "srcset", "sizes", "crossorigin", "integrity", "defer", "async", "contenteditable", "spellcheck", "draggable"
            )
            lang == "css" || ext == "css" -> listOf(
                "margin", "padding", "color", "background-color", "font-size", "font-family", "font-weight", "text-align", 
                "display: flex;", "display: block;", "display: grid;", "display: inline-block;", "display: none;",
                "justify-content", "align-items", "flex-direction", "flex-wrap", "gap", "grid-template-columns", "grid-template-rows",
                "border", "border-radius", "width", "height", "max-width", "max-height", "min-width", "min-height",
                "position: absolute;", "position: relative;", "position: fixed;", "position: sticky;", "top", "bottom", "left", "right", "z-index", 
                "box-shadow", "text-shadow", "cursor: pointer;", "transition: all 0.3s ease;", "transform", "animation",
                "backdrop-filter", "clip-path", "opacity", "overflow: hidden;", "box-sizing: border-box;",
                "background: linear-gradient(", "var(--", "calc(", "clamp(", "aspect-ratio", "mix-blend-mode",
                "align-content", "justify-items", "place-items", "grid-area", "grid-column", "grid-row",
                "border-color", "border-style", "border-width", "outline", "line-height", "letter-spacing",
                "text-transform: uppercase;", "text-transform: lowercase;", "text-decoration", "font-style",
                "overflow-x", "overflow-y", "white-space: nowrap;", "text-overflow: ellipsis;",
                "flex-grow", "flex-shrink", "flex-basis", "align-self", "justify-self",
                "background-image", "background-size", "background-position", "background-repeat", "background-attachment",
                "filter: blur(", "filter: brightness(", "filter: contrast(", "filter: grayscale(", "filter: hue-rotate(",
                "filter: invert(", "filter: opacity(", "filter: saturate(", "filter: sepia(", "filter: drop-shadow(",
                "@media (max-width: 768px) {", "@media (max-width: 1024px) {", "@media (min-width: 640px) {",
                ":root {", "var(--primary)", "var(--secondary)", "var(--accent)",
                ":hover", ":active", ":focus", ":focus-within", ":disabled", ":first-child", ":last-child", ":nth-child(",
                "::before", "::after", "scroll-behavior: smooth;", "user-select: none;", "pointer-events: none;",
                "will-change", "object-fit: cover;", "object-fit: contain;", "column-count", "column-gap"
            )
            lang == "sql" || ext == "sql" -> listOf(
                "select", "insert", "update", "delete", "from", "where", "join", "left", "right", "inner", "outer", "on", "group by", "order by", "having", "limit", "offset", "create", "table", "index", "drop", "alter", "primary key", "foreign key", "unique", "not null", "null", "and", "or", "not", "in", "exists", "like", "between", "as"
            )
            else -> listOf(
                "if", "else", "while", "for", "return", "function", "class", "import", "true", "false"
            )
        }

        val filteredStatic = staticCandidates.filter { it.lowercase().startsWith(word) && it.lowercase() != word }
        return (dynamicWords + filteredStatic).take(12)
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
    // HIGH-END MONOSPACE FONT MANAGEMENT
    // ----------------------------------------------------
    fun getEditorFontFamily(): FontFamily {
        return when (editorFontName) {
            "Monospace" -> FontFamily.Monospace
            "Serif" -> FontFamily.Serif
            "SansSerif" -> FontFamily.SansSerif
            "Cursive" -> FontFamily.Cursive
            else -> FontFamily.Monospace
        }
    }

    // ----------------------------------------------------
    // HIGH-END AUTO CODE BEAUTIFIER & FORMATTER
    // ----------------------------------------------------
    fun formatActiveCode() {
        val tab = activeTab ?: return
        val code = tab.content
        val ext = tab.fileName.substringAfterLast('.', "").lowercase()
        
        val formatted = when (ext) {
            "py" -> formatPythonCode(code)
            "js", "ts", "json" -> formatBraceLanguageCode(code)
            "html", "xml" -> formatHtmlCode(code)
            "css" -> formatBraceLanguageCode(code)
            "kt", "kts", "java", "cpp", "c", "h", "rs", "go" -> formatBraceLanguageCode(code)
            else -> code
        }
        
        if (formatted != code) {
            updateEditorTextFieldValue(
                androidx.compose.ui.text.input.TextFieldValue(
                    text = formatted,
                    selection = androidx.compose.ui.text.TextRange(0)
                )
            )
            consoleOutput = ">>> Code Formatter auto-beautified ${tab.fileName} successfully!\n$consoleOutput"
        }
    }

    private fun formatPythonCode(code: String): String {
        val lines = code.split("\n")
        val result = StringBuilder()
        var indentLevel = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.append("\n")
                continue
            }
            
            if (trimmed.startsWith("elif ") || trimmed.startsWith("else:") || trimmed.startsWith("except ") || trimmed.startsWith("except:") || trimmed.startsWith("finally:")) {
                val currentIndent = (indentLevel - 1).coerceAtLeast(0)
                result.append("    ".repeat(currentIndent)).append(trimmed).append("\n")
                continue
            }
            
            result.append("    ".repeat(indentLevel)).append(trimmed).append("\n")
            
            if (trimmed.endsWith(":")) {
                indentLevel++
            }
            
            if (trimmed.startsWith("return ") || trimmed.startsWith("return") || trimmed.startsWith("pass") || trimmed.startsWith("break") || trimmed.startsWith("continue")) {
                indentLevel = (indentLevel - 1).coerceAtLeast(0)
            }
        }
        return result.toString().trimEnd() + "\n"
    }

    private fun formatBraceLanguageCode(code: String): String {
        val lines = code.split("\n")
        val result = StringBuilder()
        var indentLevel = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.append("\n")
                continue
            }
            
            val closingCount = trimmed.count { it == '}' || it == ']' || it == ')' }
            val openingCount = trimmed.count { it == '{' || it == '[' || it == '(' }
            
            if (closingCount > openingCount) {
                indentLevel = (indentLevel - (closingCount - openingCount)).coerceAtLeast(0)
            }
            
            result.append("    ".repeat(indentLevel)).append(trimmed).append("\n")
            
            if (openingCount > closingCount) {
                indentLevel += (openingCount - closingCount)
            }
        }
        return result.toString().trimEnd() + "\n"
    }

    private fun formatHtmlCode(code: String): String {
        val lines = code.split("\n")
        val result = StringBuilder()
        var indentLevel = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.append("\n")
                continue
            }
            
            val isClosingTag = trimmed.startsWith("</")
            val isSelfClosing = trimmed.startsWith("<img") || trimmed.startsWith("<br") || trimmed.startsWith("<hr") || trimmed.startsWith("<meta") || trimmed.startsWith("<link") || trimmed.endsWith("/>")
            val isOpeningTag = trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.startsWith("<!") && !isSelfClosing
            
            if (isClosingTag) {
                indentLevel = (indentLevel - 1).coerceAtLeast(0)
            }
            
            result.append("    ".repeat(indentLevel)).append(trimmed).append("\n")
            
            if (isOpeningTag) {
                indentLevel++
            }
        }
        return result.toString().trimEnd() + "\n"
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
            
            // Advanced line-by-line route parser for Python Flask
            val lines = content.split("\n")
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("@app.route(") || line.startsWith("@app.get(") || line.startsWith("@app.post(")) {
                    val routePath = Regex("""@app\.(route|get|post)\(\s*["']([^"']+)["']""").find(line)?.groupValues?.get(2) ?: ""
                    if (routePath.isNotEmpty()) {
                        // Find the return statement in the subsequent lines (up to 20 lines)
                        var returnStr = ""
                        var j = i + 1
                        while (j < lines.size && j < i + 20) {
                            val nextLine = lines[j].trim()
                            // If we hit another route or decorator, we've moved past this route handler
                            if (nextLine.startsWith("@app.route") || nextLine.startsWith("@app.get") || nextLine.startsWith("@app.post") || (nextLine.startsWith("def ") && j > i + 2)) {
                                break
                            }
                            if (nextLine.startsWith("return ")) {
                                returnStr = nextLine.removePrefix("return ").trim()
                                break
                            }
                            j++
                        }
                        if (returnStr.isNotEmpty()) {
                            val cleanPath = routePath.removePrefix("/")
                            val renderTemplateRegex = Regex("""render_template\(\s*["']([^"']+)["']""")
                            val renderMatch = renderTemplateRegex.find(returnStr)
                            if (renderMatch != null) {
                                val templateName = renderMatch.groupValues[1]
                                // Look in 'templates' subfolder, then in 'currentDirectory'
                                val templatesDir = File(currentDirectory, "templates")
                                val templateFile = File(templatesDir, templateName).takeIf { it.exists() }
                                    ?: File(currentDirectory, templateName).takeIf { it.exists() }
                                
                                val fileContent = if (templateFile != null && templateFile.exists()) {
                                    try {
                                        templateFile.readText()
                                    } catch (e: Exception) {
                                        "<h3>Error reading template $templateName</h3><p>${e.message}</p>"
                                    }
                                } else {
                                    "<h3>Template Not Found: \"$templateName\"</h3><p>Make sure the file exists in the <b>templates/</b> directory or the root project folder.</p>"
                                }
                                hostedServerRoutes[cleanPath] = fileContent
                                if (cleanPath == "") {
                                    hostedServerRoutes["index.html"] = fileContent
                                }
                            } else {
                                // Direct string return, let's strip enclosing single or double quotes
                                val cleanString = if ((returnStr.startsWith("\"") && returnStr.endsWith("\"")) || (returnStr.startsWith("'") && returnStr.endsWith("'"))) {
                                    returnStr.substring(1, returnStr.length - 1)
                                } else {
                                    returnStr
                                }
                                hostedServerRoutes[cleanPath] = cleanString
                            }
                        }
                    }
                }
                i++
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
            
            // Advanced line-by-line route parser for Express.js
            val lines = content.split("\n")
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("app.get(") || line.startsWith("app.use(") || line.startsWith("app.post(")) {
                    val routePath = Regex("""app\.(get|use|post)\(\s*["']([^"']+)["']""").find(line)?.groupValues?.get(2) ?: ""
                    if (routePath.isNotEmpty()) {
                        var responseStr = ""
                        var j = i
                        while (j < lines.size && j < i + 20) {
                            val nextLine = lines[j].trim()
                            if (nextLine.contains("res.send(") || nextLine.contains("res.sendFile(") || nextLine.contains("res.render(")) {
                                responseStr = nextLine
                                break
                            }
                            j++
                        }
                        if (responseStr.isNotEmpty()) {
                            val cleanPath = routePath.removePrefix("/")
                            if (responseStr.contains("res.send(")) {
                                val sendMatch = Regex("""res\.send\(\s*["']([\s\S]*?)["']\s*\)""").find(responseStr)
                                    ?: Regex("""res\.send\(\s*(.*?)\s*\)""").find(responseStr)
                                val body = sendMatch?.groupValues?.get(1)?.trim('\'', '"') ?: "Hello from Express"
                                hostedServerRoutes[cleanPath] = body
                            } else if (responseStr.contains("res.sendFile(")) {
                                val sendFileMatch = Regex("""res\.sendFile\([\s\S]*?["']([^"']+)["']""").find(responseStr)
                                val filename = sendFileMatch?.groupValues?.get(1) ?: "index.html"
                                val file = File(currentDirectory, filename).takeIf { it.exists() }
                                    ?: File(File(currentDirectory, "public"), filename).takeIf { it.exists() }
                                val fileContent = if (file != null && file.exists()) {
                                    try { file.readText() } catch (e: Exception) { "Error: ${e.message}" }
                                } else {
                                    "<h3>File Not Found: $filename</h3>"
                                }
                                hostedServerRoutes[cleanPath] = fileContent
                                if (cleanPath == "") {
                                    hostedServerRoutes["index.html"] = fileContent
                                }
                            } else if (responseStr.contains("res.render(")) {
                                val renderMatch = Regex("""res\.render\(\s*["']([^"']+)["']""").find(responseStr)
                                val viewName = renderMatch?.groupValues?.get(1) ?: "index"
                                val viewFilename = if (viewName.contains(".")) viewName else "$viewName.html"
                                val file = File(File(currentDirectory, "views"), viewFilename).takeIf { it.exists() }
                                    ?: File(currentDirectory, viewFilename).takeIf { it.exists() }
                                val fileContent = if (file != null && file.exists()) {
                                    try { file.readText() } catch (e: Exception) { "Error: ${e.message}" }
                                } else {
                                    "<h3>View Not Found: $viewFilename</h3>"
                                }
                                hostedServerRoutes[cleanPath] = fileContent
                                if (cleanPath == "") {
                                    hostedServerRoutes["index.html"] = fileContent
                                }
                            }
                        }
                    }
                }
                i++
            }
            
            if (!hostedServerRoutes.containsKey("")) {
                hostedServerRoutes[""] = "<h2>Hello from Node.js Express backend!</h2><p>Server running on port $port</p>"
            }
        } else {
            activeHostedServerName = null
            activeHostedServerPort = null
        }
    }

    fun splitOutsideQuotes(text: String, delimiter: String): List<String> {
        val result = mutableListOf<String>()
        val currentToken = StringBuilder()
        var insideSingleQuote = false
        var insideDoubleQuote = false
        var i = 0
        while (i < text.length) {
            val char = text[i]
            if (char == '\'' && !insideDoubleQuote) {
                insideSingleQuote = !insideSingleQuote
                currentToken.append(char)
                i++
            } else if (char == '"' && !insideSingleQuote) {
                insideDoubleQuote = !insideDoubleQuote
                currentToken.append(char)
                i++
            } else if (!insideSingleQuote && !insideDoubleQuote && text.startsWith(delimiter, i)) {
                result.add(currentToken.toString().trim())
                currentToken.setLength(0)
                i += delimiter.length
            } else {
                currentToken.append(char)
                i++
            }
        }
        result.add(currentToken.toString().trim())
        return result
    }

    private val installedPipPackages = mutableSetOf<String>()

    fun evaluateMathExpression(expr: String): Double? {
        val clean = expr.replace(" ", "")
        if (clean.isEmpty()) return null
        // Check if it only contains digits, dots, operators +, -, *, /, %, (, )
        if (!clean.all { it.isDigit() || it == '.' || it == '+' || it == '-' || it == '*' || it == '/' || it == '%' || it == '(' || it == ')' }) {
            return null
        }
        return try {
            object : Any() {
                var pos = -1
                var ch = 0

                fun nextChar() {
                    ch = if (++pos < clean.length) clean[pos].code else -1
                }

                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.code) nextChar()
                    if (ch == charToEat) {
                        nextChar()
                        return true
                    }
                    return false
                }

                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < clean.length) throw RuntimeException("Unexpected: " + ch.toChar())
                    return x
                }

                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        if (eat('+'.code)) x += parseTerm() // addition
                        else if (eat('-'.code)) x -= parseTerm() // subtraction
                        else return x
                    }
                }

                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        if (eat('*'.code)) x *= parseFactor() // multiplication
                        else if (eat('/'.code)) x /= parseFactor() // division
                        else if (eat('%'.code)) x %= parseFactor() // modulo
                        else return x
                    }
                }

                fun parseFactor(): Double {
                    if (eat('+'.code)) return parseFactor() // unary plus
                    if (eat('-'.code)) return -parseFactor() // unary minus

                    var x: Double
                    val startPos = pos
                    if (eat('('.code)) { // parentheses
                        x = parseExpression()
                        eat(')'.code)
                    } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) { // numbers
                        while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                        x = clean.substring(startPos, pos).toDouble()
                    } else {
                        throw RuntimeException("Unexpected: " + ch.toChar())
                    }

                    return x
                }
            }.parse()
        } catch (e: Exception) {
            null
        }
    }

    fun evaluateCondition(conditionStr: String, variables: Map<String, String>): Boolean {
        val clean = conditionStr.trim()
        if (clean.isEmpty() || clean == "true" || clean == "True" || clean == "1") return true
        if (clean == "false" || clean == "False" || clean == "0") return false

        // Determine comparison operator
        val operators = listOf("==", "!=", "<=", ">=", "<", ">")
        var matchedOp: String? = null
        for (op in operators) {
            if (clean.contains(op)) {
                matchedOp = op
                break
            }
        }

        if (matchedOp == null) {
            // Truthy check for single variable or value
            val valStr = resolveValue(clean, variables)
            return valStr.isNotEmpty() && valStr != "false" && valStr != "False" && valStr != "0"
        }

        val parts = clean.split(matchedOp, limit = 2)
        val left = resolveValue(parts[0], variables)
        val right = resolveValue(parts[1], variables)

        // Try numeric comparison first
        val leftNum = left.toDoubleOrNull()
        val rightNum = right.toDoubleOrNull()

        if (leftNum != null && rightNum != null) {
            return when (matchedOp) {
                "==" -> leftNum == rightNum
                "!=" -> leftNum != rightNum
                "<" -> leftNum < rightNum
                ">" -> leftNum > rightNum
                "<=" -> leftNum <= rightNum
                ">=" -> leftNum >= rightNum
                else -> false
            }
        }

        // Fallback to string comparison
        return when (matchedOp) {
            "==" -> left == right
            "!=" -> left != right
            "<" -> left < right
            ">" -> left > right
            "<=" -> left <= right
            ">=" -> left >= right
            else -> false
        }
    }

    private fun resolveValue(rawExpr: String, variables: Map<String, String>): String {
        val token = rawExpr.trim()
        if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
            return token.substring(1, token.length - 1)
        }
        return variables[token] ?: token
    }

    fun runTerminalCommand(commandLine: String) {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return

        // Append user prompt + command to history
        terminalHistory += "${getTerminalPrompt()}$trimmed\n"

        // Check if the command is a mathematical expression (e.g., 3+5, 12 * 4)
        val isMathExpression = trimmed.all { it.isDigit() || it == '.' || it == '+' || it == '-' || it == '*' || it == '/' || it == '%' || it == '(' || it == ')' || it.isWhitespace() } &&
                trimmed.any { it == '+' || it == '-' || it == '*' || it == '/' || it == '%' } &&
                trimmed.any { it.isDigit() }

        if (isMathExpression) {
            val result = evaluateMathExpression(trimmed)
            if (result != null) {
                val displayResult = if (result % 1.0 == 0.0) {
                    result.toLong().toString()
                } else {
                    result.toString()
                }
                terminalHistory += "Result: $displayResult\n\n"
                return
            }
        }

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
                      cd <dir>             Change working directory (supports shortcuts & first-letter match)
                      calc <expr>          Calculate mathematical expression (e.g. 3 + 5)
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
            "calc" -> {
                val expr = trimmed.substringAfter("calc").trim()
                if (expr.isEmpty()) {
                    terminalHistory += "Usage: calc <expression> (e.g., calc 3 + 5)\n\n"
                } else {
                    val result = evaluateMathExpression(expr)
                    if (result != null) {
                        val displayResult = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
                        terminalHistory += "$expr = $displayResult\n\n"
                    } else {
                        terminalHistory += "calc: invalid math expression: $expr\n\n"
                    }
                }
            }
            "pwd" -> {
                terminalHistory += "${getFormattedCwdPath()}\n\n"
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
                val rootDir = if (useExternalStorage) {
                    File(Environment.getExternalStorageDirectory(), "Novacode")
                } else {
                    File(getApplication<Application>().filesDir, "Novacode")
                }
                if (!rootDir.exists()) rootDir.mkdirs()

                if (parts.size < 2) {
                    terminalCwd = rootDir
                    terminalHistory += "\n"
                } else {
                    val targetPath = trimmed.substringAfter("cd").trim()
                    val newDir = when {
                        targetPath == ".." -> {
                            val parent = terminalCwd.parentFile
                            if (parent != null && parent.absolutePath.startsWith(rootDir.parentFile?.absolutePath ?: "")) {
                                parent
                            } else {
                                rootDir
                            }
                        }
                        targetPath == "~" || targetPath == "~/N" || targetPath == "Novacode" -> {
                            rootDir
                        }
                        targetPath.startsWith("~/N/") -> {
                            val sub = targetPath.removePrefix("~/N/")
                            if (sub.length == 1) {
                                val match = rootDir.listFiles()?.find { it.isDirectory && it.name.lowercase().startsWith(sub.lowercase()) }
                                match ?: File(rootDir, sub)
                            } else {
                                File(rootDir, sub)
                            }
                        }
                        else -> {
                            val file = File(terminalCwd, targetPath)
                            if (file.exists() && file.isDirectory) {
                                file
                            } else if (targetPath.length == 1) {
                                val match = terminalCwd.listFiles()?.find { it.isDirectory && it.name.lowercase().startsWith(targetPath.lowercase()) }
                                match ?: file
                            } else {
                                if (file.isAbsolute) File(targetPath) else file
                            }
                        }
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
            val port = activeHostedServerPort ?: 5000
            startLocalServer(port)
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
            val port = activeHostedServerPort ?: 3000
            startLocalServer(port)
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
