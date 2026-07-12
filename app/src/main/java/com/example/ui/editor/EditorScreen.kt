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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Premium upgrade popup/dialog states
    var showThemeSelectorDialog by remember { mutableStateOf(false) }
    var showVersionHistoryDialog by remember { mutableStateOf(false) }
    var selectedSnapshotForDiff by remember { mutableStateOf<com.example.data.VersionSnapshot?>(null) }
    var showQuickActionDialog by remember { mutableStateOf(false) }

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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textColor
                                )
                            )
                            Text(
                                text = "Pro Edition",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = Color(0xFF4ADE80),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Toolbar buttons with horizontal scroll so all premium tools fit beautifully
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Save
                        IconButton(
                            onClick = { viewModel.saveCurrentFile() },
                            enabled = activeTab != null,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "Save File", tint = Color(0xFF50FA7B))
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
                            Icon(imageVector = Icons.Default.SaveAs, contentDescription = "Save As", tint = Color(0xFF8BE9FD))
                        }
                        // Run (replacing Theme button)
                        IconButton(
                            onClick = { viewModel.runActiveCode() },
                            enabled = activeTab != null,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", tint = Color(0xFF50FA7B))
                        }
                        
                        // 3-Lines Options Menu (Hamburger Menu)
                        Box {
                            var showMoreMenu by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { showMoreMenu = true },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = theme.textColor)
                            ) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "More Options", tint = Color(0xFFFFB86C))
                            }
                            
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier
                                    .width(260.dp)
                                    .background(if (theme.isDark) Color(0xFF1E1F29) else Color(0xFFF8FAFC))
                            ) {
                                // Section Title: Edit Actions
                                Text(
                                    text = "EDIT ACTIONS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (theme.isDark) Color(0xFF6272A4) else Color.Gray,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                
                                // Undo
                                DropdownMenuItem(
                                    text = { Text("Undo", color = theme.textColor, fontSize = 14.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.undo()
                                    },
                                    enabled = viewModel.canUndo(),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Undo,
                                            contentDescription = "Undo",
                                            tint = if (viewModel.canUndo()) Color(0xFFBD93F9) else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                // Redo
                                DropdownMenuItem(
                                    text = { Text("Redo", color = theme.textColor, fontSize = 14.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.redo()
                                    },
                                    enabled = viewModel.canRedo(),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Redo,
                                            contentDescription = "Redo",
                                            tint = if (viewModel.canRedo()) Color(0xFF8BE9FD) else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                // Format Code
                                DropdownMenuItem(
                                    text = { Text("Format Code", color = theme.textColor, fontSize = 14.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.formatActiveCode()
                                    },
                                    enabled = activeTab != null,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = "Format Code",
                                            tint = Color(0xFF50FA7B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                // Clear Active Code (Premium feature)
                                DropdownMenuItem(
                                    text = { Text("Clear Active Code", color = Color(0xFFFF5555), fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        showMoreMenu = false
                                        if (activeTab != null) {
                                            viewModel.updateEditorTextFieldValue(androidx.compose.ui.text.input.TextFieldValue(""))
                                        }
                                    },
                                    enabled = activeTab != null,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Clear Active Code",
                                            tint = Color(0xFFFF5555),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                HorizontalDivider(color = if (theme.isDark) Color(0xFF44475A) else Color(0xFFE2E8F0))
                                
                                // Section Title: ADVANCED & PREMIUM
                                Text(
                                    text = "ADVANCED & PREMIUM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (theme.isDark) Color(0xFF6272A4) else Color.Gray,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                
                                // Split-Screen (Multi-Window)
                                DropdownMenuItem(
                                    text = { Text("Split Screen (Multi-Window)", color = theme.textColor, fontSize = 14.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        if (activeTab != null) {
                                            if (viewModel.isSplitScreenEnabled) {
                                                viewModel.isSplitScreenEnabled = false
                                            } else {
                                                viewModel.isSplitScreenEnabled = true
                                                viewModel.activeLeftTabId = activeTab.id
                                                viewModel.activeRightTabId = openTabs.find { it.id != activeTab.id }?.id ?: activeTab.id
                                                viewModel.selectedPane = 0
                                            }
                                        }
                                    },
                                    enabled = activeTab != null,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Splitscreen,
                                            contentDescription = "Split Screen",
                                            tint = if (viewModel.isSplitScreenEnabled) Color(0xFF50FA7B) else theme.textColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                // Search & Replace
                                DropdownMenuItem(
                                    text = { Text("Search & Replace", color = theme.textColor, fontSize = 14.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.isSearchActive = !viewModel.isSearchActive
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search & Replace",
                                            tint = Color(0xFFFF79C6),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                // Version History
                                DropdownMenuItem(
                                    text = { Text("Version History", color = theme.textColor, fontSize = 14.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        if (activeTab != null) {
                                            viewModel.loadVersionHistoryForActiveFile()
                                            selectedSnapshotForDiff = null
                                            showVersionHistoryDialog = true
                                        }
                                    },
                                    enabled = activeTab != null,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Version History",
                                            tint = Color(0xFFBD93F9),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                // Quick Action Shortcuts
                                DropdownMenuItem(
                                    text = { Text("Quick Action Shortcuts", color = theme.textColor, fontSize = 14.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        showQuickActionDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.FlashOn,
                                            contentDescription = "Quick Actions",
                                            tint = Color(0xFFF1FA8C),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                // Theme Selector
                                DropdownMenuItem(
                                    text = { Text("Editor Color Theme", color = theme.textColor, fontSize = 14.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        showThemeSelectorDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = "Editor Themes",
                                            tint = Color(0xFFFFB86C),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                
                                HorizontalDivider(color = if (theme.isDark) Color(0xFF44475A) else Color(0xFFE2E8F0))
                                
                                // Toggle Line Numbers Row
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Show Line Numbers", color = theme.textColor, fontSize = 14.sp)
                                            Switch(
                                                checked = viewModel.showLineNumbers,
                                                onCheckedChange = { viewModel.showLineNumbers = it },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.showLineNumbers = !viewModel.showLineNumbers
                                    }
                                )
                                
                                // Toggle Auto-Suggestions Row
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Auto-Suggestions", color = theme.textColor, fontSize = 14.sp)
                                            Switch(
                                                checked = viewModel.enableAutoComplete,
                                                onCheckedChange = { viewModel.enableAutoComplete = it },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.enableAutoComplete = !viewModel.enableAutoComplete
                                    }
                                )
                            }
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
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        openTabs.forEach { tab ->
                            val isActive = activeTab?.id == tab.id
                            val tabBg = if (isActive) theme.background else theme.lineNumbersBackground
                            val tabTextCol = if (isActive) theme.textColor else theme.lineNumbersText
                            
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .background(tabBg, shape = MaterialTheme.shapes.small)
                                    .clickable {
                                        if (viewModel.isSplitScreenEnabled) {
                                            if (viewModel.selectedPane == 0) {
                                                viewModel.activeLeftTabId = tab.id
                                            } else {
                                                viewModel.activeRightTabId = tab.id
                                            }
                                        }
                                        viewModel.selectTab(tab)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // File extension icon
                                val (tabIcon, tabIconTint) = com.example.ui.components.LanguageIconHelper.getIconAndColor(tab.fileName, false)
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = null,
                                    tint = tabIconTint,
                                    modifier = Modifier.size(14.dp)
                                )

                                Text(
                                    text = tab.fileName + (if (tab.isUnsaved) " *" else ""),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = tabTextCol,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color.Transparent, shape = CircleShape)
                                        .clickable { viewModel.closeTab(tab) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Tab",
                                        tint = tabTextCol.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // Add New File Button (Prompts for filename and extension)
                        IconButton(
                            onClick = { showHomeNewFileDialog = true },
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(32.dp)
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
                .imePadding()
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
                if (viewModel.isSplitScreenEnabled) {
                    // Split screen workspace
                    Column(modifier = Modifier.weight(1f)) {
                        // Split-Screen Toolbar controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.lineNumbersBackground)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Splitscreen, contentDescription = null, tint = Color(0xFF50FA7B), modifier = Modifier.size(16.dp))
                                Text("Split-Screen Workspace", fontSize = 12.sp, color = theme.textColor, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Toggle split layout vertical / horizontal
                                TextButton(
                                    onClick = { viewModel.isVerticalSplit = !viewModel.isVerticalSplit },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = if (viewModel.isVerticalSplit) Icons.Default.ViewWeek else Icons.Default.ViewStream,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (viewModel.isVerticalSplit) "Vertical" else "Horizontal", fontSize = 11.sp)
                                }

                                // Close Split View
                                TextButton(
                                    onClick = { viewModel.isSplitScreenEnabled = false },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Close Split", fontSize = 11.sp)
                                }
                            }
                        }

                        if (viewModel.isVerticalSplit) {
                            Row(modifier = Modifier.weight(1f)) {
                                viewModel.activeLeftTabId?.let { leftId ->
                                    EditorPane(
                                        tabId = leftId,
                                        viewModel = viewModel,
                                        theme = theme,
                                        isActive = viewModel.selectedPane == 0,
                                        onTap = {
                                            viewModel.selectedPane = 0
                                            viewModel.activeLeftTabId?.let { id ->
                                                viewModel.openTabs.find { it.id == id }?.let { t -> viewModel.selectTab(t) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Gray.copy(alpha = 0.5f)))
                                viewModel.activeRightTabId?.let { rightId ->
                                    EditorPane(
                                        tabId = rightId,
                                        viewModel = viewModel,
                                        theme = theme,
                                        isActive = viewModel.selectedPane == 1,
                                        onTap = {
                                            viewModel.selectedPane = 1
                                            viewModel.activeRightTabId?.let { id ->
                                                viewModel.openTabs.find { it.id == id }?.let { t -> viewModel.selectTab(t) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                viewModel.activeLeftTabId?.let { leftId ->
                                    EditorPane(
                                        tabId = leftId,
                                        viewModel = viewModel,
                                        theme = theme,
                                        isActive = viewModel.selectedPane == 0,
                                        onTap = {
                                            viewModel.selectedPane = 0
                                            viewModel.activeLeftTabId?.let { id ->
                                                viewModel.openTabs.find { it.id == id }?.let { t -> viewModel.selectTab(t) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.Gray.copy(alpha = 0.5f)))
                                viewModel.activeRightTabId?.let { rightId ->
                                    EditorPane(
                                        tabId = rightId,
                                        viewModel = viewModel,
                                        theme = theme,
                                        isActive = viewModel.selectedPane == 1,
                                        onTap = {
                                            viewModel.selectedPane = 1
                                            viewModel.activeRightTabId?.let { id ->
                                                viewModel.openTabs.find { it.id == id }?.let { t -> viewModel.selectTab(t) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Standard Single Editor Mode
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
                }

                // Code Auto-Suggestions Row (Sits above the quick action keys)
                val suggestions = viewModel.getSuggestionsForCurrentWord()
                if (viewModel.enableAutoComplete && suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (theme.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                            .border(width = 1.dp, color = if (theme.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suggestions:",
                            style = TextStyle(
                                fontFamily = viewModel.getEditorFontFamily(),
                                fontSize = 11.sp,
                                color = if (theme.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        suggestions.forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (theme.isDark) Color(0xFF38BDF8).copy(alpha = 0.12f) else Color(0xFF0284C7).copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (theme.isDark) Color(0xFF38BDF8).copy(alpha = 0.45f) else Color(0xFF0284C7).copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        viewModel.selectSuggestion(suggestion)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
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

                // Premium Coding Symbols Row with responsive scrolling and interactive standard snippets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (theme.isDark) Color(0xFF1E1B4B) else Color(0xFFEEF2F6))
                        .border(width = 1.dp, color = if (theme.isDark) Color(0xFF312E81) else Color(0xFFCBD5E1))
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val symbols = listOf(
                        "TAB", "()", "{}", "if-else", "for-loop", "print()", "class", "def",
                        "←", "→", "-", "_", "@", "#", "*", "\"", "'", ":", ";", "!", "?", "/", "&"
                    )
                    symbols.forEach { symbol ->
                        val isSnippet = symbol in listOf("if-else", "for-loop", "print()", "class", "def")
                        val containerBg = if (isSnippet) {
                            if (theme.isDark) Color(0xFF312E81) else Color(0xFFD0BCFF)
                        } else {
                            if (theme.isDark) Color(0xFF312E81).copy(alpha = 0.6f) else Color(0xFFE2E8F0)
                        }
                        val textCol = if (isSnippet) {
                            if (theme.isDark) Color(0xFF50FA7B) else Color(0xFF381E72)
                        } else {
                            theme.textColor
                        }

                        Box(
                            modifier = Modifier
                                .widthIn(min = 46.dp)
                                .height(42.dp)
                                .background(color = containerBg, shape = RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (theme.isDark) Color(0xFF4F46E5).copy(alpha = 0.5f) else Color(0xFF94A3B8),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    when (symbol) {
                                        "←" -> viewModel.moveCursorLeft()
                                        "→" -> viewModel.moveCursorRight()
                                        "if-else" -> {
                                            val lang = activeTab.language.lowercase()
                                            val snippet = if (lang == "python") "if condition:\n    pass\nelse:\n    pass" else "if (condition) {\n    \n} else {\n    \n}"
                                            viewModel.insertSymbolAtCursor(snippet)
                                        }
                                        "for-loop" -> {
                                            val lang = activeTab.language.lowercase()
                                            val snippet = if (lang == "python") "for i in range(10):\n    pass" else "for (let i = 0; i < 10; i++) {\n    \n}"
                                            viewModel.insertSymbolAtCursor(snippet)
                                        }
                                        "print()" -> {
                                            val lang = activeTab.language.lowercase()
                                            val snippet = when (lang) {
                                                "python" -> "print(\"\")"
                                                "kotlin" -> "println(\"\")"
                                                "java" -> "System.out.println(\"\");"
                                                else -> "console.log(\"\");"
                                            }
                                            viewModel.insertSymbolAtCursor(snippet)
                                        }
                                        "class" -> {
                                            val lang = activeTab.language.lowercase()
                                            val snippet = if (lang == "python") "class MyClass:\n    def __init__(self):\n        pass" else "class MyClass {\n    \n}"
                                            viewModel.insertSymbolAtCursor(snippet)
                                        }
                                        "def" -> {
                                            val lang = activeTab.language.lowercase()
                                            val snippet = if (lang == "python") "def my_function():\n    pass" else "function myFunction() {\n    \n}"
                                            viewModel.insertSymbolAtCursor(snippet)
                                        }
                                        else -> viewModel.insertSymbolAtCursor(symbol)
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = symbol,
                                color = textCol,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
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

    // Theme Customizer Dialog (Premium)
    if (showThemeSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showThemeSelectorDialog = false },
            title = { Text("Select Editor Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditorTheme.values().forEach { et ->
                        val isSelected = et == theme
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.editorTheme = et
                                    showThemeSelectorDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = et.background
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF50FA7B) else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = et.displayName,
                                        color = et.textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (et.isDark) "Dark Mode" else "Light Mode",
                                        color = et.commentColor,
                                        fontSize = 11.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF50FA7B))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeSelectorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Local Git-Style Version snapshots and colored Diff Viewer Dialog (Premium)
    if (showVersionHistoryDialog) {
        val snapshots = viewModel.versionSnapshotsList
        AlertDialog(
            onDismissRequest = { showVersionHistoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFBD93F9))
                    Text("Local Version snapshots", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Every edit or manual save creates a local snapshot. Select a point in history to view line-by-line diffs and restore previous content instantly.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.weight(1f)) {
                        // Left: Snapshots timeline
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .background(theme.lineNumbersBackground, shape = RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (snapshots.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No snapshots saved.", fontSize = 11.sp, color = theme.lineNumbersText)
                                }
                            } else {
                                snapshots.forEach { snapshot ->
                                    val isSelected = selectedSnapshotForDiff?.id == snapshot.id
                                    val formattedTime = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date(snapshot.timestamp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { selectedSnapshotForDiff = snapshot }
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = snapshot.triggerName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = theme.textColor
                                        )
                                        Text(
                                            text = formattedTime,
                                            fontSize = 9.sp,
                                            color = theme.lineNumbersText
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right: Live Diff panel
                        Column(
                            modifier = Modifier
                                .weight(1.9f)
                                .fillMaxHeight()
                                .background(theme.background, shape = RoundedCornerShape(8.dp))
                                .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            val snapshot = selectedSnapshotForDiff
                            if (snapshot == null) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Select a version to view diff.", fontSize = 11.sp, color = theme.lineNumbersText)
                                }
                            } else {
                                val diffLines = SyntaxHighlighter.computeLineDiff(snapshot.content, activeTab?.content ?: "")
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    diffLines.forEach { line ->
                                        when (line.type) {
                                            SyntaxHighlighter.DiffType.UNCHANGED -> {
                                                Text(
                                                    text = "   ${line.text}",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = theme.textColor.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(vertical = 1.dp)
                                                )
                                            }
                                            SyntaxHighlighter.DiffType.ADDED -> {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF15803D).copy(alpha = 0.15f))
                                                        .padding(vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = " + ${line.text}",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF4ADE80)
                                                    )
                                                }
                                            }
                                            SyntaxHighlighter.DiffType.DELETED -> {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF991B1B).copy(alpha = 0.15f))
                                                        .padding(vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = " - ${line.text}",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFFF87171)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    selectedSnapshotForDiff?.let { snap ->
                        Button(
                            onClick = {
                                viewModel.restoreSnapshot(snap)
                                showVersionHistoryDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48), contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore Selected", fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = { showVersionHistoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Customizable Developer Quick Actions / Fast Shortcuts Dialog (Premium)
    if (showQuickActionDialog) {
        AlertDialog(
            onDismissRequest = { showQuickActionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFF1FA8C))
                    Text("Developer Quick Actions", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Trigger multi-step operations to boost your coding productivity on mobile devices.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider(color = Color.Gray.copy(alpha = 0.2f))

                    // 1. Format + Save + Run Action Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.triggerQuickAction("format_save_run")
                                showQuickActionDialog = false
                            },
                        colors = CardDefaults.cardColors(containerColor = theme.lineNumbersBackground)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFF1FA8C).copy(alpha = 0.15f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF1FA8C))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Format, Save & Run", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textColor)
                                Text("Beautify active file, save to storage, and launch in Console instantly.", fontSize = 11.sp, color = theme.lineNumbersText)
                            }
                        }
                    }

                    // 2. Line numbers toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Show Line Numbers", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textColor)
                            Text("Toggle structural line indices", fontSize = 11.sp, color = theme.lineNumbersText)
                        }
                        Switch(
                            checked = viewModel.showLineNumbers,
                            onCheckedChange = { viewModel.showLineNumbers = it }
                        )
                    }

                    // 3. Auto Suggestions toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Auto-Suggestions", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textColor)
                            Text("Display real-time word suggestions", fontSize = 11.sp, color = theme.lineNumbersText)
                        }
                        Switch(
                            checked = viewModel.enableAutoComplete,
                            onCheckedChange = { viewModel.enableAutoComplete = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickActionDialog = false }) {
                    Text("Close")
                }
            }
        )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_as_input")
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_new_file_input")
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

@Composable
fun EditorPane(
    tabId: Int,
    viewModel: AppViewModel,
    theme: EditorTheme,
    isActive: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tab = viewModel.openTabs.find { it.id == tabId } ?: return
    val scrollState = rememberScrollState()
    val lineCount = tab.content.split("\n").size

    Column(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = if (isActive) 1.5.dp else 0.5.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
            )
            .clickable(enabled = !isActive) { onTap() }
    ) {
        // Pane Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isActive) theme.lineNumbersBackground else Color.DarkGray.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (isActive) Icons.Default.EditNote else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else theme.lineNumbersText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = tab.fileName,
                    color = if (isActive) theme.textColor else theme.textColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
            Text(
                text = if (isActive) "ACTIVE" else "TAP TO FOCUS",
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Line numbers column
            if (viewModel.showLineNumbers) {
                Column(
                    modifier = Modifier
                        .width(38.dp)
                        .fillMaxHeight()
                        .background(theme.lineNumbersBackground)
                        .verticalScroll(scrollState)
                        .padding(top = 8.dp, bottom = 8.dp),
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

            // Text/Editor area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(theme.background)
                    .verticalScroll(scrollState)
                    .padding(8.dp)
            ) {
                if (isActive) {
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
                        visualTransformation = SyntaxHighlightingTransformation(tab.language, theme),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("code_editor_field_split"),
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.None
                        )
                    )
                } else {
                    val annotated = SyntaxHighlighter.highlight(tab.content, tab.language, theme)
                    Text(
                        text = annotated,
                        style = TextStyle(
                            fontFamily = viewModel.getEditorFontFamily(),
                            fontSize = viewModel.fontSize.sp,
                            color = theme.textColor.copy(alpha = 0.85f),
                            lineHeight = (viewModel.fontSize * 1.3).sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
