package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
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
    val context = LocalContext.current

    // Register permissions launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.setStorageSource(true, context)
            } else {
                viewModel.setStorageSource(false, context)
            }
        }
    )

    // Permission Rationale dialog
    var showPermissionRequestDialog by remember { mutableStateOf(false) }

    // Check storage permissions reactively
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.EXPLORER && viewModel.useExternalStorage && !viewModel.hasStoragePermission(context)) {
            showPermissionRequestDialog = true
        }
    }

    if (showPermissionRequestDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRequestDialog = false },
            title = { Text("Storage Permission Required") },
            text = { 
                Text("To load and save your workspace files and folders in your device system storage, Nova Code Editor requires Storage Permissions.") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRequestDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        } else {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRequestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Web Browser Preview Dialog
    if (viewModel.showWebPreview) {
        WebPreviewDialog(
            url = viewModel.webPreviewUrl,
            onDismiss = { viewModel.showWebPreview = false }
        )
    }

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
        // Use statusBarsPadding to avoid overlapping network/battery/clock icons
        val screenModifier = Modifier
            .padding(innerPadding)
            .statusBarsPadding()
            
        when (currentScreen) {
            Screen.EDITOR -> EditorScreen(viewModel = viewModel, modifier = screenModifier)
            Screen.EXPLORER -> ExplorerScreen(viewModel = viewModel, modifier = screenModifier)
            Screen.CONSOLE -> ConsoleScreen(viewModel = viewModel, modifier = screenModifier)
            Screen.SETTINGS -> SettingsScreen(viewModel = viewModel, modifier = screenModifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewDialog(
    url: String,
    onDismiss: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize(),
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Browser Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close Preview")
                        }
                        
                        // Address Bar Look-alike
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(19.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Secure Localhost",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = url.replace("127.0.0.1", "localhost"),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Localhost")
                        }
                    }
                    
                    // WebView
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                webViewRef = this
                                loadUrl(url)
                            }
                        },
                        update = { webView ->
                            webViewRef = webView
                        }
                    )
                }
            }
        }
    )
}
