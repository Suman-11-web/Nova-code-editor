package com.example.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.Screen

class SyntaxHighlightingTransformation(
    private val language: String,
    private val theme: EditorTheme
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = SyntaxHighlighter.highlight(text.text, language, theme)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val openTabs = viewModel.openTabs
    val activeTab = viewModel.activeTab
    val theme = viewModel.editorTheme
    val focusManager = LocalFocusManager.current

    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }

    var showHomeNewFileDialog by remember { mutableStateOf(false) }
    var homeNewFileName by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column(
                modifier = Modifier
                    .background(theme.background)
            ) {
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFFD0BCFF), Color(0xFF381E72))
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "NC",
                                style = TextStyle(
                                    color = Color(0xFF1C1B1F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Column {
                            Text(
                                text = "Nova Code Editor",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textColor
                                )
                            )
                            Text(
                                text = "v1.2.0",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = Color(0xFFD0BCFF).copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Undo
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = viewModel.canUndo(),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                        ) {
                            Icon(imageVector = Icons.Default.Undo, contentDescription = "Undo")
                        }
                        // Redo
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = viewModel.canRedo(),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                        ) {
                            Icon(imageVector = Icons.Default.Redo, contentDescription = "Redo")
                        }
                        // Search
                        IconButton(
                            onClick = { viewModel.isSearchActive = !viewModel.isSearchActive },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search & Replace")
                        }
                        // Save
                        IconButton(
                            onClick = { viewModel.saveCurrentFile() },
                            enabled = activeTab != null,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "Save File")
                        }
                        // Save As
                        IconButton(
                            onClick = {
                                if (activeTab != null) {
                                    saveAsName = activeTab.fileName
                                    showSaveAsDialog = true
                                }
                            },
                            enabled = activeTab != null,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                        ) {
                            Icon(imageVector = Icons.Default.SaveAs, contentDescription = "Save As")
                        }
                        // Format Code
                        IconButton(
                            onClick = { viewModel.formatActiveCode() },
                            enabled = activeTab != null,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                        ) {
                            Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = "Format Code")
                        }
                        // Run
                        Button(
                            onClick = { viewModel.runActiveCode() },
                            enabled = activeTab != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp).testTag("run_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run", fontSize = 14.sp)
                        }
                    }
                }

                // File Open Tabs Row
                if (openTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.lineNumbersBackground)
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        openTabs.forEach { tab ->
                            val isActive = activeTab?.id == tab.id
                            val tabBg = if (isActive) theme.background else theme.lineNumbersBackground
                            val tabTextCol = if (isActive) theme.textColor else theme.lineNumbersText
                            
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .background(tabBg, shape = MaterialTheme.shapes.small)
                                    .clickable { viewModel.selectTab(tab) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = tab.fileName + (if (tab.isUnsaved) " *" else ""),
                                    fontSize = 13.sp,
                                    color = tabTextCol,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Tab",
                                    tint = tabTextCol.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { viewModel.closeTab(tab) }
                                )
                            }
                        }
                        
                        // New Draft Tab Button
                        IconButton(
                            onClick = { showHomeNewFileDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Tab",
                                tint = theme.textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (openTabs.isEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("New File") },
                    icon = { Icon(Icons.Default.Add, contentDescription = "New") },
                    onClick = { showHomeNewFileDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("new_draft_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(innerPadding)
        ) {
            // Search and Replace overlay
            AnimatedVisibility(
                visible = viewModel.isSearchActive,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.lineNumbersBackground)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = viewModel.searchText,
                                onValueChange = { viewModel.searchText = it },
                                placeholder = { Text("Search text...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier.weight(1f).height(50.dp),
                                textStyle = TextStyle(fontSize = 14.sp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                            )
                            OutlinedTextField(
                                value = viewModel.replaceText,
                                onValueChange = { viewModel.replaceText = it },
                                placeholder = { Text("Replace with...") },
                                leadingIcon = { Icon(Icons.Default.FindReplace, contentDescription = null) },
                                modifier = Modifier.weight(1f).height(50.dp),
                                textStyle = TextStyle(fontSize = 14.sp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    // Search & Replace logic
                                    val tab = activeTab
                                    if (tab != null && viewModel.searchText.isNotEmpty()) {
                                        val replaced = tab.content.replace(viewModel.searchText, viewModel.replaceText)
                                        viewModel.updateActiveTabContent(replaced)
                                    }
                                }
                            ) {
                                Text("Replace All", color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.isSearchActive = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (activeTab != null) {
                // Main code editing area
                val codeText = activeTab.content
                
                Row(modifier = Modifier.weight(1f)) {
                    val scrollState = rememberScrollState()
                    val lineCount = codeText.split("\n").size

                    // Line numbers column
                    if (viewModel.showLineNumbers) {
                        Column(
                            modifier = Modifier
                                .width(42.dp)
                                .fillMaxHeight()
                                .background(theme.lineNumbersBackground)
                                .verticalScroll(scrollState)
                                .padding(top = 12.dp, bottom = 12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (i in 1..lineCount) {
                                Text(
                                    text = "$i ",
                                    style = TextStyle(
                                        fontFamily = viewModel.getEditorFontFamily(),
                                        fontSize = viewModel.fontSize.sp,
                                        color = theme.lineNumbersText
                                    ),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }

                    // Main typing text area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(theme.background)
                            .verticalScroll(scrollState)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = viewModel.editorTextFieldValue,
                            onValueChange = { viewModel.updateEditorTextFieldValue(it) },
                            textStyle = TextStyle(
                                fontFamily = viewModel.getEditorFontFamily(),
                                fontSize = viewModel.fontSize.sp,
                                color = theme.textColor,
                                lineHeight = (viewModel.fontSize * 1.3).sp
                            ),
                            cursorBrush = SolidColor(theme.cursorColor),
                            visualTransformation = SyntaxHighlightingTransformation(activeTab.language, theme),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("code_editor_field"),
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.None
                            )
                        )
                    }
                }

                // Code Auto-Suggestions Row
                val suggestions = viewModel.getSuggestionsForCurrentWord()
                if (viewModel.enableAutoComplete && suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (theme.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suggestions:",
                            style = TextStyle(
                                fontFamily = viewModel.getEditorFontFamily(),
                                fontSize = 11.sp,
                                color = if (theme.isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        suggestions.forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (theme.isDark) Color(0xFF38BDF8).copy(alpha = 0.15f) else Color(0xFF0284C7).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (theme.isDark) Color(0xFF38BDF8).copy(alpha = 0.4f) else Color(0xFF0284C7).copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        viewModel.selectSuggestion(suggestion)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = suggestion,
                                    style = TextStyle(
                                        fontFamily = viewModel.getEditorFontFamily(),
                                        fontSize = 12.sp,
                                        color = if (theme.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // Auxiliary coding symbols row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.lineNumbersBackground)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val symbols = listOf("{", "}", "(", ")", "[", "]", ";", "TAB")
                    symbols.forEach { symbol ->
                        Box(
                            modifier = Modifier
                                  .widthIn(min = 44.dp)
                                  .height(40.dp)
                                  .background(
                                      if (theme.isDark) Color(0xFF49454F).copy(alpha = 0.4f)
                                      else Color(0xFFE0E0E0), 
                                      shape = RoundedCornerShape(4.dp)
                                  )
                                .clickable {
                                    if (symbol == "TAB") {
                                        viewModel.insertTextAtCursor("    ")
                                    } else {
                                        viewModel.insertTextAtCursor(symbol)
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = symbol,
                                color = theme.textColor,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            } else {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = "No Open Tabs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            text = "No open files",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Create a new file or select an existing one from the File Manager to start editing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                }
            }
        }
    }

    // Save As Dialog
    if (showSaveAsDialog) {
        var fileError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("Save As") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new file name with extension:")
                    OutlinedTextField(
                        value = saveAsName,
                        onValueChange = { 
                            saveAsName = it
                            fileError = null
                        },
                        singleLine = true,
                        isError = fileError != null,
                        supportingText = {
                            if (fileError != null) {
                                Text(fileError!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Example: main.py, index.html", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_as_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveAsName.isBlank()) {
                            fileError = "File name cannot be empty"
                        } else if (!saveAsName.contains(".") || saveAsName.substringAfterLast(".").isEmpty()) {
                            fileError = "Please specify a file extension (e.g., .py, .html, .js)"
                        } else {
                            viewModel.saveCurrentFileAs(saveAsName)
                            showSaveAsDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_as_confirm")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New File Dialog on Home page (EditorScreen)
    if (showHomeNewFileDialog) {
        var fileError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { 
                showHomeNewFileDialog = false
                homeNewFileName = ""
            },
            title = { Text("Create New File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new file name with extension:")
                    OutlinedTextField(
                        value = homeNewFileName,
                        onValueChange = { 
                            homeNewFileName = it
                            fileError = null
                        },
                        singleLine = true,
                        isError = fileError != null,
                        supportingText = {
                            if (fileError != null) {
                                Text(fileError!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Example: main.py, index.html, script.js", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("home_new_file_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (homeNewFileName.isBlank()) {
                            fileError = "File name cannot be empty"
                        } else if (!homeNewFileName.contains(".") || homeNewFileName.substringAfterLast(".").isEmpty()) {
                            fileError = "Please specify a file extension (e.g., .py, .html, .js)"
                        } else {
                            viewModel.createNewFileInExplorer(homeNewFileName)
                            homeNewFileName = ""
                            showHomeNewFileDialog = false
                        }
                    },
                    modifier = Modifier.testTag("home_new_file_confirm")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    homeNewFileName = ""
                    showHomeNewFileDialog = false 
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
