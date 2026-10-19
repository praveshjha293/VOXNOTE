package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.RecordingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NotesMakerTheme
import com.example.ui.viewmodel.NotesMakerViewModel

enum class NavigationTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Default.Home, Icons.Outlined.Home),
    RECORDINGS("recordings", "Recordings", Icons.Default.Mic, Icons.Outlined.Mic),
    NOTES("notes", "Notes", Icons.Default.Description, Icons.Outlined.Description),
    SETTINGS("settings", "Settings", Icons.Default.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: NotesMakerViewModel = viewModel()
            val preferences by viewModel.preferences.collectAsState()

            val isDarkTheme = when (preferences.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            // Permissions setup
            val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Handled gracefully at feature invocation */ }

            LaunchedEffect(Unit) {
                val needed = permissionsToRequest.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (needed.isNotEmpty()) {
                    permissionLauncher.launch(needed.toTypedArray())
                }
            }

            NotesMakerTheme(darkTheme = isDarkTheme) {
                NotesMakerApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun NotesMakerApp(viewModel: NotesMakerViewModel) {
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var activeNoteIdForEditor by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiMessage by viewModel.uiMessage.collectAsState()

    // Display messages
    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiMessage()
        }
    }

    // Back handling
    BackHandler(enabled = activeNoteIdForEditor != null || currentTab != NavigationTab.HOME) {
        if (activeNoteIdForEditor != null) {
            activeNoteIdForEditor = null
        } else if (currentTab != NavigationTab.HOME) {
            currentTab = NavigationTab.HOME
        }
    }

    if (activeNoteIdForEditor != null) {
        NoteEditorScreen(
            noteId = activeNoteIdForEditor!!,
            viewModel = viewModel,
            onNavigateBack = { activeNoteIdForEditor = null }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.testTag("main_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = androidx.compose.ui.unit.Dp(2f)
                ) {
                    NavigationTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    NavigationTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToRecordings = { currentTab = NavigationTab.RECORDINGS },
                        onNavigateToNotes = { currentTab = NavigationTab.NOTES },
                        onNavigateToSettings = { currentTab = NavigationTab.SETTINGS },
                        onOpenNote = { noteId -> activeNoteIdForEditor = noteId }
                    )
                    NavigationTab.RECORDINGS -> RecordingsScreen(
                        viewModel = viewModel,
                        onOpenNote = { noteId -> activeNoteIdForEditor = noteId }
                    )
                    NavigationTab.NOTES -> NotesScreen(
                        viewModel = viewModel,
                        onOpenNote = { noteId -> activeNoteIdForEditor = noteId }
                    )
                    NavigationTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
