package com.example.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.ui.AppViewModel
import com.example.ui.components.HomeNavigationButton
import com.example.ui.components.RunActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val activeTab = viewModel.activeTab
    val consoleOutput = viewModel.consoleOutput
    val consoleError = viewModel.consoleError
    val isRunning = viewModel.isConsoleRunning

    val outputScrollState = rememberScrollState()
    val errorScrollState = rememberScrollState()

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Console Logs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Interactive Run Console", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    actions = {
                        // Run button on console
                        IconButton(
                            onClick = { viewModel.runActiveCode() },
                            enabled = activeTab != null && !isRunning,
                            modifier = Modifier.testTag("console_run_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Active Code",
                                tint = if (activeTab != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        // Clear button
                        IconButton(
                            onClick = { viewModel.clearConsole() },
                            modifier = Modifier.testTag("console_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Terminal logs",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        }
    ) { innerPadding ->
        var selectedTabIndex by remember { mutableStateOf(0) }
        val terminalScrollState = rememberScrollState()

        // Auto-scroll terminal to bottom when history changes
        LaunchedEffect(viewModel.terminalHistory) {
            terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF070A13)) // High-fidelity dark terminal black
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Run Output", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Interactive Terminal", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTabIndex == 0) {
                // --- TAB 1: RUN OUTPUT ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(12.dp)
                ) {
                    // Status Indicator Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = when {
                                            isRunning -> Color(0xFFEAB308) // Yellow
                                            consoleError.isNotEmpty() -> Color(0xFFEF4444) // Red
                                            else -> Color(0xFF22C55E) // Green
                                        },
                                        shape = MaterialTheme.shapes.extraSmall
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isRunning -> "Executing simulation..."
                                    consoleError.isNotEmpty() -> "Exited with errors"
                                    else -> "Terminal idle / Ready"
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (activeTab != null) {
                            Text(
                                text = "Source: ${activeTab.fileName}",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Loading indicator for compile step
                    if (isRunning) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFF1E293B)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Text Output Log View
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.5f)
                            .background(Color(0xFF0F172A), shape = MaterialTheme.shapes.small)
                            .padding(12.dp)
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(outputScrollState)
                            ) {
                                Text(
                                    text = consoleOutput,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color(0xFF38BDF8), // Tech cyan terminal output
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }

                    // Error Console log view (shows up if errors exist)
                    if (consoleError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ERROR LOGS & CRASH STACK",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.5f)
                                .background(Color(0xFF1E1111), shape = MaterialTheme.shapes.small)
                                .padding(12.dp)
                        ) {
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(errorScrollState)
                                ) {
                                    Text(
                                        text = consoleError,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = Color(0xFFFCA5A5), // Pale error red
                                            lineHeight = 18.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Quick Actions bottom bar
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeNavigationButton(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        RunActionButton(
                            viewModel = viewModel,
                            enabled = activeTab != null && !isRunning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // --- TAB 2: INTERACTIVE TERMINAL ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(12.dp)
                ) {
                    // Terminal output text history & prompt inside the same scrollable container!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0F172A), shape = MaterialTheme.shapes.small)
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(terminalScrollState)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = viewModel.terminalHistory,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color(0xFF38BDF8),
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                            
                            // In-line Input Prompt Row right inside the scrollable box!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = viewModel.getTerminalPrompt(),
                                    style = TextStyle(
                                        color = Color(0xFF22C55E), // green prompt text
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                BasicTextField(
                                    value = viewModel.terminalInput,
                                    onValueChange = { viewModel.terminalInput = it },
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Send
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (viewModel.terminalInput.isNotBlank()) {
                                                viewModel.runTerminalCommand(viewModel.terminalInput)
                                                viewModel.terminalInput = ""
                                            }
                                        }
                                    ),
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("terminal_input_field")
                                )
                                IconButton(
                                    onClick = {
                                        if (viewModel.terminalInput.isNotBlank()) {
                                            viewModel.runTerminalCommand(viewModel.terminalInput)
                                            viewModel.terminalInput = ""
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Execute Command",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
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
