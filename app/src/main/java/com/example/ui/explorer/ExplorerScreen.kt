package com.example.ui.explorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val fileList = viewModel.fileTreeList
    val currentDir = viewModel.currentDirectory
    val clipboardFile = viewModel.clipboardFile
    val isCut = viewModel.isCutOperation
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle(initialValue = emptyList())

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<File?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var fileActionMenuTarget by remember { mutableStateOf<File?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    title = {
                        Column {
                            Text("Project Explorer", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = currentDir.absolutePath.replace(currentDir.parentFile?.parent ?: "", ""),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        val parent = currentDir.parentFile
                        if (parent != null && parent.name != "files") {
                            IconButton(onClick = { viewModel.navigateUp() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Up Directory")
                            }
                        } else {
                            IconButton(onClick = {}, enabled = false) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }
                        IconButton(onClick = { showNewFileDialog = true }) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New File")
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = { showNewFileDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("explorer_new_file_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create File")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Contextual Clipboard Paste Bar
            AnimatedVisibility(visible = clipboardFile != null) {
                clipboardFile?.let { source ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isCut) Icons.Default.ContentCut else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ready to paste: ${source.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row {
                                Button(
                                    onClick = { viewModel.pasteFromClipboard() },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Paste Here", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.clipboardFile = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cancel Paste",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tabs or sections: Explorer File Tree vs Recent Files
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Section: Recent Files
                if (recentFiles.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Files",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { viewModel.clearRecentFilesHistory() },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    items(recentFiles) { recent ->
                        val file = File(recent.filePath)
                        if (file.exists()) {
                            ListItem(
                                headlineContent = { Text(recent.fileName, fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                                supportingContent = { Text(recent.filePath, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                modifier = Modifier
                                    .clickable { viewModel.openFileInEditor(file) }
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    }
                }

                // Section: Current Directory Directory Tree
                item {
                    Text(
                        text = "Files & Projects",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (fileList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "This folder is empty",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    items(fileList) { file ->
                        val isDir = file.isDirectory
                        val fileExt = file.extension.lowercase()
                        
                        val icon = when {
                            isDir -> Icons.Default.Folder
                            fileExt == "py" -> Icons.Default.IntegrationInstructions
                            fileExt == "js" -> Icons.Default.Javascript
                            fileExt == "json" -> Icons.Default.SettingsApplications
                            fileExt == "md" -> Icons.Default.Description
                            else -> Icons.Default.InsertDriveFile
                        }

                        val tint = when {
                            isDir -> Color(0xFFFBBF24) // Gold Folder
                            fileExt == "py" -> Color(0xFF38BDF8) // Python Cyan Blue
                            fileExt == "js" -> Color(0xFFEAB308) // JS Yellow
                            fileExt == "json" -> Color(0xFFA855F7) // JSON Purple
                            fileExt == "md" -> Color(0xFF34D399) // Markdown Green
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = file.name,
                                    fontFamily = if (isDir) FontFamily.SansSerif else FontFamily.Monospace,
                                    fontWeight = if (isDir) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            },
                            supportingContent = {
                                if (!isDir) {
                                    Text(
                                        "${file.length()} bytes • ${fileExt.uppercase()}",
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            leadingContent = {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                            },
                            trailingContent = {
                                IconButton(onClick = { fileActionMenuTarget = file }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "File Actions")
                                }
                            },
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        if (isDir) {
                                            viewModel.navigateToDirectory(file)
                                        } else {
                                            viewModel.openFileInEditor(file)
                                        }
                                    },
                                    onLongClick = {
                                        fileActionMenuTarget = file
                                    }
                                )
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // Context Action Dropdown Menu
    fileActionMenuTarget?.let { file ->
        val isDir = file.isDirectory
        AlertDialog(
            onDismissRequest = { fileActionMenuTarget = null },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Open Option
                    ListItem(
                        headlineContent = { Text(if (isDir) "Open Folder" else "Open in Editor") },
                        leadingContent = { Icon(Icons.Default.Launch, contentDescription = null) },
                        modifier = Modifier.clickable {
                            fileActionMenuTarget = null
                            if (isDir) {
                                viewModel.navigateToDirectory(file)
                            } else {
                                viewModel.openFileInEditor(file)
                            }
                        }
                    )
                    // Copy Option
                    ListItem(
                        headlineContent = { Text("Copy") },
                        leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        modifier = Modifier.clickable {
                            fileActionMenuTarget = null
                            viewModel.copyFileToClipboard(file)
                        }
                    )
                    // Cut Option
                    ListItem(
                        headlineContent = { Text("Cut") },
                        leadingContent = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                        modifier = Modifier.clickable {
                            fileActionMenuTarget = null
                            viewModel.cutFileToClipboard(file)
                        }
                    )
                    // Rename Option
                    ListItem(
                        headlineContent = { Text("Rename") },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                        modifier = Modifier.clickable {
                            fileActionMenuTarget = null
                            renameTargetFile = file
                            renameNewName = file.name
                            showRenameDialog = true
                        }
                    )
                    // Delete Option
                    ListItem(
                        headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            fileActionMenuTarget = null
                            viewModel.deleteFileInExplorer(file)
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { fileActionMenuTarget = null }) {
                    Text("Close")
                }
            }
        )
    }

    // New File Dialog
    if (showNewFileDialog) {
        var fileError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File Name with Extension:")
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { 
                            newFileName = it
                            fileError = null
                        },
                        singleLine = true,
                        placeholder = { Text("index.html") },
                        isError = fileError != null,
                        supportingText = {
                            if (fileError != null) {
                                Text(fileError!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Example: script.py, styles.css", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("new_file_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isBlank()) {
                            fileError = "File name cannot be empty"
                        } else if (!newFileName.contains(".") || newFileName.substringAfterLast(".").isEmpty()) {
                            fileError = "Please specify a file extension (e.g., .py, .html, .js)"
                        } else {
                            viewModel.createNewFileInExplorer(newFileName)
                            newFileName = ""
                            showNewFileDialog = false
                        }
                    },
                    modifier = Modifier.testTag("new_file_confirm")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Folder Name:")
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        singleLine = true,
                        placeholder = { Text("scripts") },
                        modifier = Modifier.fillMaxWidth().testTag("new_folder_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createNewFolderInExplorer(newFolderName)
                            newFolderName = ""
                            showNewFolderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("new_folder_confirm")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog && renameTargetFile != null) {
        val target = renameTargetFile!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New Name:")
                    OutlinedTextField(
                        value = renameNewName,
                        onValueChange = { renameNewName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameNewName.isNotBlank() && renameNewName != target.name) {
                            viewModel.renameFileInExplorer(target, renameNewName)
                            showRenameDialog = false
                        }
                    },
                    modifier = Modifier.testTag("rename_confirm")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
