package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.console.ConsoleScreen
import com.example.ui.editor.EditorScreen
import com.example.ui.explorer.ExplorerScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }

    // Intercept physical hardware keyboard events for real desktop-class coding shortcuts
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_S -> {
                    viewModel.saveCurrentFile()
                    return true
                }
                KeyEvent.KEYCODE_N -> {
                    viewModel.createNewDraftTab()
                    return true
                }
                KeyEvent.KEYCODE_R -> {
                    viewModel.runActiveCode()
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    if (viewModel.canUndo()) {
                        viewModel.undo()
                    }
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    if (viewModel.canRedo()) {
                        viewModel.redo()
                    }
                    return true
                }
                KeyEvent.KEYCODE_F -> {
                    viewModel.isSearchActive = !viewModel.isSearchActive
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun MainAppScaffold(viewModel: AppViewModel = viewModel()) {
    val currentScreen = viewModel.currentScreen

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    // Navigation item: Editor
                    NavigationBarItem(
                        selected = currentScreen == Screen.EDITOR,
                        onClick = { viewModel.navigateTo(Screen.EDITOR) },
                        icon = { Icon(Icons.Default.Code, contentDescription = "Editor") },
                        label = { Text("Editor") },
                        modifier = Modifier.testTag("nav_editor_tab")
                    )
                    // Navigation item: File Explorer
                    NavigationBarItem(
                        selected = currentScreen == Screen.EXPLORER,
                        onClick = { viewModel.navigateTo(Screen.EXPLORER) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                        label = { Text("Explorer") },
                        modifier = Modifier.testTag("nav_explorer_tab")
                    )
                    // Navigation item: Console
                    NavigationBarItem(
                        selected = currentScreen == Screen.CONSOLE,
                        onClick = { viewModel.navigateTo(Screen.CONSOLE) },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = "Console") },
                        label = { Text("Console") },
                        modifier = Modifier.testTag("nav_console_tab")
                    )
                    // Navigation item: Settings
                    NavigationBarItem(
                        selected = currentScreen == Screen.SETTINGS,
                        onClick = { viewModel.navigateTo(Screen.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)
        when (currentScreen) {
            Screen.EDITOR -> EditorScreen(viewModel = viewModel, modifier = screenModifier)
            Screen.EXPLORER -> ExplorerScreen(viewModel = viewModel, modifier = screenModifier)
            Screen.CONSOLE -> ConsoleScreen(viewModel = viewModel, modifier = screenModifier)
            Screen.SETTINGS -> SettingsScreen(viewModel = viewModel, modifier = screenModifier)
        }
    }
}
