package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.editor.EditorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val currentTheme = viewModel.editorTheme
    val fontSize = viewModel.fontSize
    val wordWrap = viewModel.wordWrap

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings Icon",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Editor Configuration", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION: Editor Customization
            item {
                Text(
                    text = "Aesthetics & Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Theme picker selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syntax Highlighting Theme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EditorTheme.values().forEach { theme ->
                                val isSelected = currentTheme == theme
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.background)
                                        .clickable { viewModel.editorTheme = theme }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = theme.displayName.split(" ")[0],
                                            color = theme.textColor,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .size(6.dp)
                                                    .background(theme.cursorColor, RoundedCornerShape(3.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Font size slider control
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Editor Font Size", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("${fontSize.toInt()}sp", fontWeight = FontWeight.Bold)
                        }
                        
                        Slider(
                            value = fontSize,
                            onValueChange = { viewModel.fontSize = it },
                            valueRange = 10f..26f,
                            steps = 8,
                            modifier = Modifier.testTag("font_size_slider")
                        )
                        
                        // Live font preview box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(currentTheme.background, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "def greet():\n    print(\"Hello World!\")",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    color = currentTheme.textColor
                                )
                            )
                        }
                    }
                }
            }

            // Word wrap switch
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WrapText, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Enable Word Wrap", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Wrap long lines inside editor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = wordWrap,
                            onCheckedChange = { viewModel.wordWrap = it },
                            modifier = Modifier.testTag("word_wrap_switch")
                        )
                    }
                }
            }

            // SECTION: Keyboard Shortcuts
            item {
                Text(
                    text = "Desktop Keyboard Shortcuts",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ShortcutRow("Ctrl + N", "Create empty Python file")
                        ShortcutRow("Ctrl + S", "Save current open file")
                        ShortcutRow("Ctrl + R", "Run and test active code")
                        ShortcutRow("Ctrl + Z", "Undo last editor change")
                        ShortcutRow("Ctrl + Y", "Redo reverted editor change")
                        ShortcutRow("Ctrl + F", "Search and replace in file")
                    }
                }
            }

            // SECTION: About Nova Code Editor
            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nova Code Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version 1.0.0 (Native Release)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "A lightweight, secure, on-device development environment built strictly with Kotlin and Jetpack Compose. Nova makes beginner programming fun, fast, and fully accessible offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ShortcutRow(keys: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = keys,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Text(
            text = action,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
