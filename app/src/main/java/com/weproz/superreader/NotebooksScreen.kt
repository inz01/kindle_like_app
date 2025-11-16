// NotebooksScreen.kt

package com.weproz.superreader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.withTranslation
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.weproz.superreader.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min
import android.graphics.Color as AndroidColor

// Data classes for selection state and sorting
data class SelectionState(
    val selectedNotebooks: Set<Int> = emptySet(),
    val isSelectionMode: Boolean = false,
)

enum class SortOption {
    DATE_MODIFIED, NAME_A_Z, NAME_Z_A
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebooksScreen(
    navController: NavController,
    onShowThemeDialog: (Boolean) -> Unit,
    currentTheme: AppTheme,
    isFocusModeActive: Boolean = false,
    focusModeManager: FocusModeManager? = null,
    onFocusModeChange: (Boolean) -> Unit = {},
    onShowFocusModeInfo: () -> Unit = {},
    onShowFocusModeConfirm: () -> Unit = {},
    notebooksViewModel: NotebooksViewModel = viewModel(),
    shouldShowBottomBar: Boolean = false,
) {
    val activity = LocalActivity.current
    val notebooks by notebooksViewModel.notebooks.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectionState by remember { mutableStateOf(SelectionState()) }
    var showTopBarMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var sortOption by remember { mutableStateOf(SortOption.DATE_MODIFIED) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var notebookToDelete by remember { mutableStateOf<Notebook?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var notebookToRename by remember { mutableStateOf<Notebook?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Back press handler
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Handle back press for search mode and dialogs
    DisposableEffect(
        isSearchActive,
        showCreateDialog,
        showDeleteConfirmDialog,
        showBulkDeleteConfirmDialog,
        showRenameDialog
    ) {
        val callback = {
            when {
                isSearchActive -> {
                    isSearchActive = false
                    searchQuery = ""
                }

                showCreateDialog -> showCreateDialog = false
                showDeleteConfirmDialog -> showDeleteConfirmDialog = false
                showBulkDeleteConfirmDialog -> showBulkDeleteConfirmDialog = false
                showRenameDialog -> showRenameDialog = false
                selectionState.isSelectionMode -> selectionState = SelectionState()
                else -> backPressedDispatcher?.onBackPressed()
            }
        }

        val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                callback()
            }
        }

        backPressedDispatcher?.addCallback(lifecycleOwner, backCallback)

        onDispose {
            backCallback.remove()
        }
    }

    val filteredNotebooks = if (searchQuery.isNotBlank()) {
        notebooks.filter { notebook ->
            notebook.title.contains(searchQuery, ignoreCase = true)
        }
    } else {
        notebooks
    }

    val sortedNotebooks = when (sortOption) {
        SortOption.DATE_MODIFIED -> filteredNotebooks.sortedByDescending { it.lastModified }
        SortOption.NAME_A_Z -> filteredNotebooks.sortedBy { it.title }
        SortOption.NAME_Z_A -> filteredNotebooks.sortedByDescending { it.title }
    }

    val hasNotebooks = sortedNotebooks.isNotEmpty()

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun validateNotebookTitle(title: String): String? {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            return "Notebook title cannot be empty"
        }
        if (notebooks.any { it.title == trimmedTitle && it.id != notebookToRename?.id }) {
            return "A notebook with this name already exists"
        }
        return null
    }


    fun deleteNotebook(notebook: Notebook) {
        coroutineScope.launch {
            try {
                notebooksViewModel.deleteNotebook(notebook)
                showMessage("Notebook deleted")
            } catch (_: Exception) {
                showMessage("Failed to delete notebook")
            }
        }
    }

    fun renameNotebook(notebook: Notebook, newTitle: String) {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isNotBlank()) {
            val validationError = validateNotebookTitle(trimmedTitle)
            if (validationError != null) {
                showMessage(validationError)
                return
            }
            coroutineScope.launch {
                try {
                    val updatedNotebook =
                        notebook.copy(
                            title = trimmedTitle,
                            lastModified = System.currentTimeMillis()
                        )
                    notebooksViewModel.updateNotebook(updatedNotebook)
                    showMessage("Notebook renamed")
                } catch (_: Exception) {
                    showMessage("Failed to rename notebook")
                }
            }
        }
    }

    fun shareNotebookAsPdf(notebook: Notebook) {
        coroutineScope.launch {
            try {
                val pdfFile = convertNotebookToPdf(context, notebook)
                shareFileViaBluetoothOnly(context, pdfFile, "Notebook: ${notebook.title}")
                showMessage("Sharing '${notebook.title}' as PDF via Bluetooth...")
            } catch (e: Exception) {
                showMessage("Failed to share notebook: ${e.message ?: "Make sure Bluetooth is enabled"}")
            }
        }
    }

    fun shareSelectedNotebooksAsPdf() {
        val selectedNotebooks =
            sortedNotebooks.filter { selectionState.selectedNotebooks.contains(it.id) }
        if (selectedNotebooks.isEmpty()) return

        coroutineScope.launch {
            showMessage("Creating ZIP archive...")
            try {
                // 1. Create a list of temporary PDF files
                val tempPdfs = selectedNotebooks.map { notebook ->
                    convertNotebookToPdf(context, notebook)
                }

                // 2. Create a single ZIP file to hold them
                val zipFile = File(context.cacheDir, "Notebooks_${System.currentTimeMillis()}.zip")
                ZipOutputStream(FileOutputStream(zipFile)).use { zipOutputStream ->
                    tempPdfs.forEach { pdfFile ->
                        // Add each PDF to the ZIP archive with shorter names
                        val shortName = "Notebook_${pdfFile.nameWithoutExtension.take(20)}.pdf"
                        val entry = ZipEntry(shortName)
                        zipOutputStream.putNextEntry(entry)
                        FileInputStream(pdfFile).use { fileInputStream ->
                            fileInputStream.copyTo(zipOutputStream)
                        }
                        zipOutputStream.closeEntry()
                    }
                }

                // 3. Share the single ZIP file
                shareFileViaBluetoothOnly(
                    context,
                    zipFile,
                    "Notebooks_${System.currentTimeMillis()}"
                )
                selectionState = SelectionState()

                // 4. Clean up the temporary PDFs
                tempPdfs.forEach { it.delete() }

            } catch (e: Exception) {
                showMessage("Error creating ZIP file: ${e.message}")
            }
        }
    }

    fun showBulkDeleteConfirmation() {
        if (selectionState.selectedNotebooks.isNotEmpty()) {
            showBulkDeleteConfirmDialog = true
        }
    }

    fun deleteSelectedNotebooks() {
        val selectedNotebooks =
            sortedNotebooks.filter { selectionState.selectedNotebooks.contains(it.id) }
        if (selectedNotebooks.isNotEmpty()) {
            coroutineScope.launch {
                selectedNotebooks.forEach { notebook ->
                    try {
                        notebooksViewModel.deleteNotebook(notebook)
                    } catch (_: Exception) {
                        // Handle failures silently
                    }
                }
                showMessage("${selectedNotebooks.size} notebooks deleted")
                selectionState = SelectionState()
                showBulkDeleteConfirmDialog = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSearchActive) {
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                    placeholder = "Search notebooks..."
                )
            } else {
                val headerColor = if (currentTheme == AppTheme.SEPIA) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                }
                Surface(color = headerColor, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectionState.isSelectionMode)
                                "${selectionState.selectedNotebooks.size} selected"
                            else "Notebooks",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )

                        if (selectionState.isSelectionMode) {
                            IconButton(onClick = { showBulkDeleteConfirmation() }) {
                                Icon(Icons.Default.Delete, "Delete Selected")
                            }
                            IconButton(onClick = { shareSelectedNotebooksAsPdf() }) {
                                Icon(Icons.Default.Share, "Share via Bluetooth")
                            }
                            TextButton(onClick = { selectionState = SelectionState() }) {
                                Text("Cancel")
                            }
                        } else {
                            // --- THIS IS THE FIX: Swapped Search and Focus buttons ---
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, "Search")
                            }

                            IconButton(
                                onClick = {
                                    if (activity != null) {
                                        if (!isFocusModeActive) {
                                            // Show confirmation dialog instead of directly enabling
                                            onShowFocusModeConfirm()
                                        } else {
                                            // For disabling, show a disable confirmation
                                            focusModeManager?.disableFocusMode(activity)
                                            onFocusModeChange(false)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Psychology,
                                    "Focus Mode",
                                    tint = if (isFocusModeActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, "Sort Options")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Date Modified") },
                                        onClick = {
                                            sortOption = SortOption.DATE_MODIFIED
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name A-Z") },
                                        onClick = {
                                            sortOption = SortOption.NAME_A_Z
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name Z-A") },
                                        onClick = {
                                            sortOption = SortOption.NAME_Z_A
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                            Box {
                                IconButton(
                                    onClick = { showTopBarMenu = true },
                                    enabled = hasNotebooks
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert, "More Options",
                                        tint = if (hasNotebooks) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTopBarMenu,
                                    onDismissRequest = { showTopBarMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Select") },
                                        onClick = {
                                            selectionState = SelectionState(isSelectionMode = true)
                                            showTopBarMenu = false
                                        },
                                        enabled = hasNotebooks
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Select All") },
                                        onClick = {
                                            val allIds = sortedNotebooks.map { it.id }.toSet()
                                            selectionState = SelectionState(
                                                selectedNotebooks = allIds,
                                                isSelectionMode = true
                                            )
                                            showTopBarMenu = false
                                        },
                                        enabled = hasNotebooks
                                    )
                                }
                            }
                            IconButton(onClick = { onShowThemeDialog(true) }) {
                                Icon(Icons.Default.Palette, "Choose Theme")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selectionState.isSelectionMode) {

                val isTabletDevice = isTablet()
                val fabBottomPadding = if (shouldShowBottomBar) {
                    if (isTabletDevice) 60.dp else 20.dp
                } else {
                    16.dp
                }
                val fabSize = if (isTabletDevice) 60.dp else 50.dp // Smaller on mobile
                val iconSize = if (isTabletDevice) 27.dp else 22.dp // Smaller icon on mobile


                val fabContainerColor = when (currentTheme) {
                    AppTheme.SEPIA -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val fabContentColor = when (currentTheme) {
                    AppTheme.SEPIA -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }

                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor,
                    modifier = Modifier
                        .padding(bottom = fabBottomPadding) // Exact positioning for bottom bar
                        .size(fabSize) // RESTORE PROPER FAB SIZ
                ) {
                    Icon(
                        Icons.Default.Create,
                        contentDescription = "New Note",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    ) { paddingValues ->
        if (sortedNotebooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No notebooks found for '$searchQuery'"
                    else "Create a notebook by clicking on the pen."
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sortedNotebooks) { notebook ->
                    NotebookCard(
                        notebook = notebook,
                        isSelected = selectionState.selectedNotebooks.contains(notebook.id),
                        isSelectionMode = selectionState.isSelectionMode,
                        onSelectToggle = { selected ->
                            val newSelected = if (selected) {
                                selectionState.selectedNotebooks + notebook.id
                            } else {
                                selectionState.selectedNotebooks - notebook.id
                            }
                            selectionState = selectionState.copy(selectedNotebooks = newSelected)
                        },
                        onClick = {
                            if (selectionState.isSelectionMode) {
                                val newSelected =
                                    if (selectionState.selectedNotebooks.contains(notebook.id)) {
                                        selectionState.selectedNotebooks - notebook.id
                                    } else {
                                        selectionState.selectedNotebooks + notebook.id
                                    }
                                selectionState =
                                    selectionState.copy(selectedNotebooks = newSelected)
                            } else {
                                navController.navigate("note_editor/${notebook.title}/${notebook.id}")
                            }
                        },
                        onRename = {
                            notebookToRename = notebook
                            newTitle = notebook.title
                            showRenameDialog = true
                        },
                        onShare = { shareNotebookAsPdf(notebook) },
                        onDelete = {
                            notebookToDelete = notebook
                            showDeleteConfirmDialog = true
                        },
                        currentTheme = currentTheme
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNotebookDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                val trimmedTitle = title.trim()
                if (trimmedTitle.isBlank()) {
                    showMessage("Notebook title cannot be empty")
                    return@CreateNotebookDialog
                }
                val validationError = validateNotebookTitle(trimmedTitle)
                if (validationError != null) {
                    showMessage(validationError)
                    return@CreateNotebookDialog
                }
                notebooksViewModel.addNotebook(trimmedTitle)
                showCreateDialog = false
            }
        )
    }
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Notebook") },
            text = { Text("Are you sure you want to delete '${notebookToDelete?.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        notebookToDelete?.let { deleteNotebook(it) }
                        showDeleteConfirmDialog = false
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBulkDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmDialog = false },
            title = { Text("Delete Selected Notebooks") },
            text = { Text("Are you sure you want to delete ${selectionState.selectedNotebooks.size} selected notebooks? This action cannot be undone.") },
            confirmButton = { Button(onClick = { deleteSelectedNotebooks() }) { Text("Delete") } },
            dismissButton = {
                TextButton(onClick = {
                    showBulkDeleteConfirmDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Notebook") },
            text = {
                TextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Notebook Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        notebookToRename?.let { renameNotebook(it, newTitle) }
                        showRenameDialog = false
                    },
                    enabled = newTitle.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun NotebookCard(
    notebook: Notebook,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    currentTheme: AppTheme,
) {
    var showCardMenu by remember { mutableStateOf(false) }
    // Trim and format the title for display
    val displayTitle = remember(notebook.title) {
        val trimmedTitle = notebook.title.trim()
        if (trimmedTitle.length > 10) {
            "${trimmedTitle.substring(0, 10)}..."
        } else {
            trimmedTitle
        }
    }

    // FIXED: Card color logic
    val cardColor = when (currentTheme) {
        AppTheme.SEPIA -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                cardColor
            }
        )
    ) {
        Box {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White) // This sets the white background
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // FIX: Draw white background first to ensure it's captured in thumbnail
                        drawRect(color = Color.White, size = size)

                        if (notebook.gridType != GridType.NONE) {
                            drawGridBackgroundPreview(notebook.gridType)
                        }

                        if (!notebook.content.isNullOrEmpty()) {
                            val previewPaths =
                                notebook.content.map { convertPathDataToDrawingPath(it) }
                            val totalBounds = android.graphics.RectF()
                            previewPaths.forEach {
                                val pathBounds = android.graphics.RectF()
                                it.path.asAndroidPath().computeBounds(pathBounds, true)
                                if (!pathBounds.isEmpty) {
                                    totalBounds.union(pathBounds)
                                }
                            }
                            if (totalBounds.width() > 0f && totalBounds.height() > 0f) {
                                val padding = 10.dp.toPx()
                                val availableWidth = size.width - 2 * padding
                                val availableHeight = size.height - 2 * padding
                                val scaleX = availableWidth / totalBounds.width()
                                val scaleY = availableHeight / totalBounds.height()
                                val scale = min(scaleX, scaleY)
                                val scaledWidth = totalBounds.width() * scale
                                val scaledHeight = totalBounds.height() * scale
                                val translateX =
                                    padding + (availableWidth - scaledWidth) / 2f - totalBounds.left * scale
                                val translateY =
                                    padding + (availableHeight - scaledHeight) / 2f - totalBounds.top * scale
                                withTransform({
                                    translate(left = translateX, top = translateY)
                                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                                }) {
                                    previewPaths.forEach { drawThePathPreview(it) }
                                }
                            }
                        }
                    }

                    if (notebook.content.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Empty notebook",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = displayTitle, // Use the formatted title here
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Modified: ${notebook.lastModified.toFormattedDateString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            IconButton(
                                onClick = { showCardMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Card Options",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showCardMenu,
                                onDismissRequest = { showCardMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        onRename()
                                        showCardMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.TextFields, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share as PDF via Bluetooth") },
                                    onClick = {
                                        onShare()
                                        showCardMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        onDelete()
                                        showCardMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectToggle,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                )
            }
        }
    }
}

//@Composable
//fun CustomSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
//    Surface(
//        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
//        shadowElevation = 4.dp
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(56.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            IconButton(onClick = onClose) {
//                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
//            }
//            TextField(
//                value = query,
//                onValueChange = onQueryChange,
//                placeholder = { Text("Search notebooks...") },
//                singleLine = true,
//                colors = TextFieldDefaults.colors(
//                    focusedContainerColor = Color.Transparent,
//                    unfocusedContainerColor = Color.Transparent,
//                    disabledContainerColor = Color.Transparent,
//                    focusedIndicatorColor = Color.Transparent,
//                    unfocusedIndicatorColor = Color.Transparent,
//                ),
//                modifier = Modifier.weight(1f)
//            )
//            if (query.isNotEmpty()) {
//                IconButton(onClick = { onQueryChange("") }) {
//                    Icon(Icons.Default.Close, "Clear search")
//                }
//            }
//        }
//    }
//}


private fun convertNotebookToPdf(context: Context, notebook: Notebook): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    // Shorten the filename to avoid Bluetooth sharing issues - FIXED for individual shares
    val shortTitle =
        notebook.title.replace(" ", "_").replace("[^a-zA-Z0-9_]".toRegex(), "").take(15)
    val fileName = "NB_${shortTitle}_$timeStamp.pdf"

    val filesDir = context.cacheDir // Use cache dir for temporary files
    val pdfFile = File(filesDir, fileName)

    try {
        val document = PdfDocument()

        // A4 size in points (595 x 842)
        val pageWidth = 595
        val pageHeight = 842
        val margin = 50f

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        // Draw header information
        canvas.drawText("Notebook: ${notebook.title}", margin, margin, paint)
        canvas.drawText(
            "Created: ${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date(notebook.lastModified))
            }", margin, margin + 20f, paint
        )

        // Calculate available drawing area
        val drawingStartY = margin + 50f
        val drawingHeight = pageHeight - drawingStartY - margin
        val drawingWidth = pageWidth - 2 * margin

        // Define the full drawing area bounds
        val fullDrawingBounds = android.graphics.RectF(
            margin,
            drawingStartY,
            margin + drawingWidth,
            drawingStartY + drawingHeight
        )

        // Draw the full background grid first
        drawFullGridOnPdfCanvas(canvas, notebook.gridType, fullDrawingBounds)

        // Draw the actual canvas content if available
        notebook.content?.let { pathDataList ->
            if (pathDataList.isNotEmpty()) {
                // Convert path data to drawing paths
                val drawingPaths = pathDataList.map { pathData ->
                    convertPathDataToDrawingPathForPdf(pathData)
                }

                // Calculate bounds of all paths to scale properly
                val totalBounds = android.graphics.RectF()
                drawingPaths.forEach { drawingPath ->
                    val pathBounds = android.graphics.RectF()
                    drawingPath.path.asAndroidPath().computeBounds(pathBounds, true)
                    if (!pathBounds.isEmpty) {
                        totalBounds.union(pathBounds)
                    }
                }

                if (!totalBounds.isEmpty && totalBounds.width() > 0 && totalBounds.height() > 0) {
                    // Calculate scale to fit within PDF page
                    val scaleX = drawingWidth / totalBounds.width()
                    val scaleY = drawingHeight / totalBounds.height()
                    val scale = min(scaleX, scaleY) * 0.9f // 90% scale to add some padding

                    // Calculate translation to center the drawing
                    val scaledWidth = totalBounds.width() * scale
                    val scaledHeight = totalBounds.height() * scale
                    val translateX = margin + (drawingWidth - scaledWidth) / 2f
                    val translateY = drawingStartY + (drawingHeight - scaledHeight) / 2f

                    // Save canvas state
                    canvas.withTranslation(
                        translateX - totalBounds.left * scale,
                        translateY - totalBounds.top * scale
                    ) {

                        // Apply transformation
                        scale(scale, scale)

                        // Draw all paths on top of the grid
                        drawingPaths.forEach { drawingPath ->
                            drawPathOnPdfCanvas(this, drawingPath)
                        }

                        // Restore canvas state
                    }
                } else {
                    // If no valid bounds, show message
                    canvas.drawText(
                        "No drawing content available",
                        margin,
                        drawingStartY + 30f,
                        paint
                    )
                }
            } else {
                canvas.drawText("Empty notebook - No drawing content", margin, margin + 50f, paint)
            }
        } ?: run {
            canvas.drawText("No content available", margin, margin + 50f, paint)
        }

        document.finishPage(page)

        val fileOutputStream = FileOutputStream(pdfFile)
        document.writeTo(fileOutputStream)
        document.close()
        fileOutputStream.close()

    } catch (_: Exception) {
        // Fallback: create a simple text PDF if drawing fails
        try {
            pdfFile.writeText(
                """
                Notebook: ${notebook.title}
                Created: ${
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(
                        Date(
                            notebook.lastModified
                        )
                    )
                }
                Content: This notebook contains ${notebook.content?.size ?: 0} drawing paths.
                The drawing could not be rendered in PDF format.
            """.trimIndent()
            )
        } catch (_: Exception) {
            // Final fallback
            pdfFile.writeText("Notebook: ${notebook.title}")
        }
    }
    return pdfFile
}


// Helper function to draw full grid background on PDF canvas
private fun drawFullGridOnPdfCanvas(
    canvas: android.graphics.Canvas,
    gridType: GridType,
    drawingBounds: android.graphics.RectF,
) {
    val gridPaint = Paint().apply {
        color = AndroidColor.LTGRAY
        alpha = 128 // Semi-transparent
        strokeWidth = 0.5f
    }

    val gridSize = 20f // Grid spacing

    when (gridType) {
        GridType.SQUARE -> {
            // Draw vertical lines across entire drawing area
            var x = drawingBounds.left
            while (x <= drawingBounds.right) {
                canvas.drawLine(x, drawingBounds.top, x, drawingBounds.bottom, gridPaint)
                x += gridSize
            }
            // Draw horizontal lines across entire drawing area
            var y = drawingBounds.top
            while (y <= drawingBounds.bottom) {
                canvas.drawLine(drawingBounds.left, y, drawingBounds.right, y, gridPaint)
                y += gridSize
            }
        }

        GridType.DOT -> {
            // Draw dots across entire drawing area
            var x = drawingBounds.left
            while (x <= drawingBounds.right) {
                var y = drawingBounds.top
                while (y <= drawingBounds.bottom) {
                    canvas.drawCircle(x, y, 1f, gridPaint)
                    y += gridSize
                }
                x += gridSize
            }
        }

        GridType.RULED -> {
            // Draw ruled lines across entire drawing area
            var y = drawingBounds.top
            while (y <= drawingBounds.bottom) {
                canvas.drawLine(drawingBounds.left, y, drawingBounds.right, y, gridPaint)
                y += gridSize * 1.5f // Slightly more spacing for ruled lines
            }
        }

        GridType.NONE -> {
            // No grid - just draw white background across entire area
            val backgroundPaint = Paint().apply {
                color = AndroidColor.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRect(drawingBounds, backgroundPaint)
        }
    }
}

// Helper function to draw paths on PDF canvas
private fun drawPathOnPdfCanvas(canvas: android.graphics.Canvas, drawingPath: DrawingPath) {
    val paint = Paint().apply {
        color = drawingPath.color.toPdfArgb()
        strokeWidth = drawingPath.strokeWidth
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    // Apply pen type specific styles
    when (drawingPath.penType) {
        PenType.HIGHLIGHTER -> {
            paint.alpha = 76 // Semi-transparent for highlighter
            paint.strokeWidth = drawingPath.strokeWidth * 2f // Thicker for highlighter effect
        }

        PenType.SKETCH -> {
            paint.alpha = 128 // Semi-transparent for sketch
        }

        else -> {
            paint.alpha = 255 // Opaque for regular brush
        }
    }

    canvas.drawPath(drawingPath.path.asAndroidPath(), paint)
}

// Helper function to convert Color to ARGB for PDF
private fun Color.toPdfArgb(): Int {
    return AndroidColor.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

// Separate function for PDF conversion
private fun convertPathDataToDrawingPathForPdf(pathData: PathData): DrawingPath {
    val path = Path()
    if (pathData.points.isNotEmpty()) {
        path.moveTo(pathData.points.first().first, pathData.points.first().second)
        pathData.points.drop(1).forEach { point ->
            path.lineTo(point.first, point.second)
        }
    }

    val color = Color(
        red = AndroidColor.red(pathData.color) / 255f,
        green = AndroidColor.green(pathData.color) / 255f,
        blue = AndroidColor.blue(pathData.color) / 255f,
        alpha = AndroidColor.alpha(pathData.color) / 255f
    )

    return DrawingPath(
        path = path,
        color = color,
        strokeWidth = pathData.strokeWidth,
        penType = pathData.penType
    )
}

private fun shareFileViaBluetoothOnly(context: Context, file: File, subject: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "zip") "application/zip" else "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject.take(50)) // Limit subject length
            putExtra(Intent.EXTRA_TEXT, "Sharing file: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.android.bluetooth")
        }

        // Try with Bluetooth package first, fallback to generic share
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Fallback: remove package restriction and try generic share
            intent.setPackage(null)
            context.startActivity(Intent.createChooser(intent, "Share via"))
        }

    } catch (_: ActivityNotFoundException) {
        throw Exception("Bluetooth sharing not available.")
    } catch (e: Exception) {
        throw Exception("Failed to share via Bluetooth: ${e.message}")
    }
}

@Composable
fun CreateNotebookDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Notebook") },
        text = {
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Notebook Title") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun Long.toFormattedDateString(): String {
    val date = Date(this)
    val format = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return format.format(date)
}

private fun convertPathDataToDrawingPath(pathData: PathData): DrawingPath {
    val path = Path()
    if (pathData.points.isNotEmpty()) {
        path.moveTo(pathData.points.first().first, pathData.points.first().second)
        pathData.points.drop(1).forEach { point ->
            path.lineTo(point.first, point.second)
        }
    }

    val color = Color(
        red = AndroidColor.red(pathData.color) / 255f,
        green = AndroidColor.green(pathData.color) / 255f,
        blue = AndroidColor.blue(pathData.color) / 255f,
        alpha = AndroidColor.alpha(pathData.color) / 255f
    )

    return DrawingPath(
        path = path,
        color = color,
        strokeWidth = pathData.strokeWidth,
        penType = pathData.penType
    )
}

private fun DrawScope.drawThePathPreview(drawingPath: DrawingPath) {
    when (drawingPath.penType) {
        PenType.SKETCH -> {
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color.copy(alpha = 0.5f),
                style = Stroke(
                    width = drawingPath.strokeWidth / 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        PenType.HIGHLIGHTER -> {
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color.copy(alpha = 0.3f),
                style = Stroke(
                    width = drawingPath.strokeWidth / 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        else -> {
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color,
                style = Stroke(
                    width = drawingPath.strokeWidth / 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private fun DrawScope.drawGridBackgroundPreview(gridType: GridType) {
    val gridSize = 20f
    val strokeWidth = 0.5.dp.toPx()
    val lineColor = Color.LightGray.copy(alpha = 0.3f)

    when (gridType) {
        GridType.SQUARE -> {
            for (i in 0..size.width.toInt() step gridSize.toInt())
                drawLine(
                    lineColor,
                    Offset(i.toFloat(), 0f),
                    Offset(i.toFloat(), size.height),
                    strokeWidth
                )
            for (i in 0..size.height.toInt() step gridSize.toInt())
                drawLine(
                    lineColor,
                    Offset(0f, i.toFloat()),
                    Offset(size.width, i.toFloat()),
                    strokeWidth
                )
        }

        GridType.DOT -> {
            for (x in 0..size.width.toInt() step gridSize.toInt())
                for (y in 0..size.height.toInt() step gridSize.toInt())
                    drawCircle(lineColor, 1.dp.toPx(), Offset(x.toFloat(), y.toFloat()))
        }

        GridType.RULED -> {
            for (i in 0..size.height.toInt() step (gridSize * 2).toInt())
                drawLine(
                    lineColor,
                    Offset(0f, i.toFloat()),
                    Offset(size.width, i.toFloat()),
                    strokeWidth
                )
        }

        GridType.NONE -> {}
    }
}