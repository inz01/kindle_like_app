// MainScreen.kt

package com.weproz.superreader

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.weproz.superreader.ui.theme.AppTheme
import com.weproz.superreader.ui.theme.SuperReaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Bottom navigation item
data class BottomNavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String,
)

private const val ACTION_LOCK_TASK_MODE_CHANGED = "android.app.action.LOCK_TASK_MODE_CHANGED"

@Composable
fun MainScreen(
    currentTheme: AppTheme = AppTheme.LIGHT,
    onThemeChanged: (AppTheme) -> Unit = {},
) {
    var appTheme by remember { mutableStateOf(currentTheme) }
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    val activity = LocalActivity.current
    val focusModeManager = remember { FocusModeManager(context) }
    var isFocusModeActive by remember { mutableStateOf(focusModeManager.isFocusModeActive) }
    var showFocusModeInfo by remember { mutableStateOf(false) }
    var showFocusModeConfirm by remember { mutableStateOf(false) }
    var showGrantPermissionDialog by remember { mutableStateOf(false) }

    // Use rememberLauncherForActivityResult for permission handling
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val act = activity
        focusModeManager.onPermissionResult(act) // this will pin
        isFocusModeActive = focusModeManager.isFocusModeActive
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Function to sync focus mode state
    fun syncFocusModeState() {
        val act = activity
        if (act != null) {
            val isPinned = focusModeManager.isAppPinned(act)
            isFocusModeActive = isPinned
            focusModeManager.updateFocusModeState(isPinned)

            // If unpinned but WiFi is still disabled, re-enable it
            if (!isPinned) {
                focusModeManager.ensureWifiEnabled()
            }
        }
    }

    LaunchedEffect(Unit) {
        val act = activity
        if (act != null && focusModeManager.isOverlayPermissionGranted() && !isFocusModeActive) {
            focusModeManager.autoEnableOnStartIfNeeded(act)
            isFocusModeActive = focusModeManager.isFocusModeActive
            showFocusModeConfirm = false
        } else if (act != null && !focusModeManager.isOverlayPermissionGranted()) {
            showFocusModeInfo = true // ask for permission on first run
        }
    }

    // Save theme when it changes
    LaunchedEffect(appTheme) {
        if (appTheme != currentTheme) {
            themeManager.saveTheme(appTheme)
            onThemeChanged(appTheme)
        }
    }

    // Handle the focus mode manager callbacks
    LaunchedEffect(focusModeManager) {
        focusModeManager.onReadyForEnable = {
            // This is called when permission is already granted
            showFocusModeConfirm = true
        }
        focusModeManager.onPermissionGranted = {
            // This is called when permission is just granted
            showFocusModeConfirm = true
        }
        focusModeManager.onPermissionRequired = { activity ->
            // This is called when permission is needed (new install or not granted)
            showGrantPermissionDialog = true
        }
    }

    // Improved lock task state monitoring
    DisposableEffect(activity) {
        val am = activity?.getSystemService(ActivityManager::class.java)

        fun updatePinnedState() {
            val state = am?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
            val wasPinned = isFocusModeActive
            val isPinned = state == ActivityManager.LOCK_TASK_MODE_PINNED ||
                    state == ActivityManager.LOCK_TASK_MODE_LOCKED

            isFocusModeActive = isPinned
            focusModeManager.updateFocusModeState(isPinned)

            // If app was unpinned (user used home button), ensure WiFi is re-enabled
            if (wasPinned && !isPinned) {
                focusModeManager.ensureWifiEnabled()
            }
        }

        // Initial sync
        updatePinnedState()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == ACTION_LOCK_TASK_MODE_CHANGED) {
                    updatePinnedState()
                }
            }
        }
        val filter = IntentFilter(ACTION_LOCK_TASK_MODE_CHANGED)

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    // Monitor app lifecycle to detect when user returns to app after unpinning
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Always sync state when app comes to foreground
                    syncFocusModeState()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    // Optional: You can add cleanup here if needed
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Also sync state periodically as a fallback
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000) // Check every 2 seconds
            syncFocusModeState()
        }
    }

    SuperReaderTheme(appTheme = appTheme) {
        val navController = rememberNavController()
        var showThemeDialog by remember { mutableStateOf(false) }

        val items = listOf(
            BottomNavItem("Library", Icons.Default.AutoStories, "library"),
            BottomNavItem("Notebooks", Icons.Default.Create, "notebooks"),
            BottomNavItem("Dictionary", Icons.Default.Translate, "dictionary")
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val shouldShowBottomBar = currentRoute in items.map { it.route }

        // Apply focus mode back blocking
        FocusModeBackHandler(isFocusModeActive, snackbarHostState)

        Scaffold(
            bottomBar = {
                if (shouldShowBottomBar) {
                    val bottomBarColor = when (appTheme) {
                        AppTheme.SEPIA -> MaterialTheme.colorScheme.surface
                        AppTheme.DARK -> MaterialTheme.colorScheme.surfaceContainer
                        AppTheme.LIGHT -> MaterialTheme.colorScheme.surfaceContainer
                    }

                    NavigationBar(
                        containerColor = bottomBarColor,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        val currentDestination = navBackStackEntry?.destination
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, screen.label) },
                                label = { Text(screen.label) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->

                val bottomPadding = if (shouldShowBottomBar) {
                // Calculate exact padding needed: original bottom padding minus bottom bar height
                val bottomBarHeight = 50.dp
                val originalBottomPadding = innerPadding.calculateBottomPadding()
                maxOf(16.dp, originalBottomPadding - bottomBarHeight + 16.dp)
            } else {
                innerPadding.calculateBottomPadding()
            }
            NavHost(
                navController = navController,
                startDestination = "library",
                modifier = Modifier.padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = bottomPadding
                )
            ) {
                val showThemeDialogLambda = { show: Boolean -> showThemeDialog = show }

                // Create a shared focus mode handler function
                val onFocusModeButtonClick: (Activity?) -> Unit = { activity ->
                    if (activity != null) {
                        if (!isFocusModeActive) {
                            // Use checkAndPrepareFocusMode instead of directly enabling
                            focusModeManager.checkAndPrepareFocusMode(activity)
                            // The callbacks will handle showing the confirmation dialog
                        } else {
                            // For disabling, show a disable confirmation
                            focusModeManager.disableFocusMode(activity)
                            isFocusModeActive = false
                        }
                    }
                }


                composable("library") {
                    LibraryScreen(
                        navController = navController,
                        onShowThemeDialog = showThemeDialogLambda,
                        currentTheme = appTheme,
                        isFocusModeActive = isFocusModeActive,
                        focusModeManager = focusModeManager,
                        onFocusModeChange = { active ->
                            isFocusModeActive = active
                            if (!active) {
                                val activity = context as? Activity
                                focusModeManager.disableFocusMode(activity)
                            }
                        },
                        onShowFocusModeInfo = { showFocusModeInfo = true },
                        onShowFocusModeConfirm = {
                            // This will be called from the screen's focus mode button
                            val activity = context as? Activity
                            onFocusModeButtonClick(activity)
                        },
                        shouldShowBottomBar = shouldShowBottomBar // ADD THIS
                    )
                }
                composable("notebooks") {
                    NotebooksScreen(
                        navController = navController,
                        onShowThemeDialog = showThemeDialogLambda,
                        currentTheme = appTheme,
                        isFocusModeActive = isFocusModeActive,
                        focusModeManager = focusModeManager,
                        onFocusModeChange = { active ->
                            isFocusModeActive = active
                            if (!active) {
                                val activity = context as? Activity
                                focusModeManager.disableFocusMode(activity)
                            }
                        },
                        onShowFocusModeInfo = { showFocusModeInfo = true },
                        onShowFocusModeConfirm = {
                            // This will be called from the screen's focus mode button
                            val activity = context as? Activity
                            onFocusModeButtonClick(activity)
                        },
                        shouldShowBottomBar = shouldShowBottomBar
                    )
                }

                composable("dictionary") {
                    DictionaryScreen(
                        onShowThemeDialog = showThemeDialogLambda,
                        currentTheme = appTheme,
                        isFocusModeActive = isFocusModeActive,
                        focusModeManager = focusModeManager,
                        onFocusModeChange = { active ->
                            isFocusModeActive = active
                            if (!active) {
                                val activity = context as? Activity
                                focusModeManager.disableFocusMode(activity)
                            }
                        },
                        onShowFocusModeInfo = { showFocusModeInfo = true },
                        onShowFocusModeConfirm = {
                            // This will be called from the screen's focus mode button
                            val activity = context as? Activity
                            onFocusModeButtonClick(activity)
                        },
                        shouldShowBottomBar = shouldShowBottomBar
                    )
                }

                composable("reader/{uri}?type={type}&filename={filename}") { backStackEntry ->
                    val uriString = backStackEntry.arguments?.getString("uri")
                    val type = backStackEntry.arguments?.getString("type") ?: BookType.PDF.name
                    val filename = backStackEntry.arguments?.getString("filename") ?: "Reader"
                    if (uriString != null) {
                        ReaderScreen(
                            uriString,
                            BookType.valueOf(type),
                            navController,
                            filename,
                            isFocusModeActive = isFocusModeActive
                        )
                    }
                }
                composable("note_editor/{noteTitle}/{notebookId}") { backStackEntry ->
                    val noteTitle = backStackEntry.arguments?.getString("noteTitle") ?: "New Note"
                    val notebookIdString = backStackEntry.arguments?.getString("notebookId")
                    val notebookId = notebookIdString?.toIntOrNull()

                    val localContext = LocalContext.current
                    val notebookDao = AppDatabase.getDatabase(localContext).notebookDao()

                    NoteEditorScreen(
                        navController = navController,
                        noteTitle = noteTitle,
                        notebookId = notebookId,
                        notebookDao = notebookDao,
                    )
                }
            }
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                onThemeSelected = { newTheme ->
                    appTheme = newTheme
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false }
            )
        }

        if (showFocusModeInfo) {
            FocusModeInfoDialog(
                onDismiss = { showFocusModeInfo = false },
                onGrantPermission = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    overlayPermissionLauncher.launch(intent)
                    showFocusModeInfo = false
                }
            )
        }

        // Add Focus Mode Confirmation Dialog
        if (showFocusModeConfirm) {
            FocusModeConfirmationDialog(
                onConfirm = {
                    if (activity != null) {
                        if (focusModeManager.enableFocusMode(activity)) {
                            isFocusModeActive = true
                        } else {
                            showFocusModeInfo = true
                        }
                    }
                    showFocusModeConfirm = false
                },
                onDismiss = { showFocusModeConfirm = false }
            )
        }

        // NEW: Grant Permission Dialog
        if (showGrantPermissionDialog) {
            GrantPermissionDialog(
                onGrantPermission = {
                    if (activity != null) {
                        focusModeManager.requestOverlayPermission(activity)
                    }
                    showGrantPermissionDialog = false
                },
                onDismiss = { showGrantPermissionDialog = false }
            )
        }
    }
}

@Composable
fun FocusModeInfoDialog(
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Focus Mode Permission Required") },
        text = {
            Column {
                Text("To enable full focus mode, you need to grant the following permissions:")
                Text("• Display over other apps (for kiosk mode)")
                Text("• WiFi control (to disable internet)")
                Text("\nFocus mode will:")
                Text("• Disable WiFi internet")
                Text("• Prevent app switching (if supported)")
                Text("• Minimize distractions")
            }
        },
        confirmButton = {
            TextButton(onClick = onGrantPermission) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FocusModeConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable Focus Mode") },
        text = {
            Column {
                Text("Focus mode will:")
                Text("• Disable WiFi internet")
                Text("• Prevent app switching (if supported)")
                Text("• Minimize distractions")
                Text("\nTo exit focus mode, press the focus button again.")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ThemeSelectionDialog(
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "Light",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeSelected(AppTheme.LIGHT) }
                        .padding(vertical = 12.dp)
                )
                Text(
                    "Dark",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeSelected(AppTheme.DARK) }
                        .padding(vertical = 12.dp)
                )
                Text(
                    "Sepia",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeSelected(AppTheme.SEPIA) }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun GrantPermissionDialog(
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Required") },
        text = {
            Column {
                Text("To enable Focus Mode, you need to grant the \"Display over other apps\" permission.")
                Text("\nThis permission allows:")
                Text("• Full screen immersion")
                Text("• Preventing accidental app switching")
                Text("• Minimizing distractions")
                Text("\nYou'll be taken to settings to grant this permission.")
            }
        },
        confirmButton = {
            Button(onClick = onGrantPermission) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun FocusModeBackHandler(
    isFocusModeActive: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val coroutineScope = rememberCoroutineScope()
    BackHandler(enabled = isFocusModeActive) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Focus mode is active. Exit focus mode to go back.")
        }
    }
}