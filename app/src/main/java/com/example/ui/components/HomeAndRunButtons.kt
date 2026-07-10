package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.Screen

/**
 * Reusable Home Button that navigates the user back to the primary Editor screen.
 */
@Composable
fun HomeNavigationButton(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = { viewModel.navigateTo(Screen.EDITOR) },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = modifier.testTag("go_to_home_button")
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "Go to Home",
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "Home", fontSize = 14.sp)
    }
}

/**
 * Reusable Run Button to execute code from the active editor tab.
 */
@Composable
fun RunActionButton(
    viewModel: AppViewModel,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { viewModel.runActiveCode() },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier.testTag("action_run_button")
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Run Active Code",
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "Run Code", fontSize = 14.sp)
    }
}
