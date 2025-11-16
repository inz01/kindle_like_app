// LibraryScreen.kt

package com.weproz.superreader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.weproz.superreader.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val PREFS_NAME = "SuperReaderPrefs"
private const val KEY_FOLDER_URI = "folder_uri"

data class LibrarySelectionState(
    val selectedBooks: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
)

enum class LibrarySortOption {
    DATE_MODIFIED, NAME_A_Z, NAME_Z_A
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    onShowThemeDialog: (Boolean) -> Unit,
    currentTheme: AppTheme,
    isFocusModeActive: Boolean = false,
    focusModeManager: FocusModeManager? = null,
    onFocusModeChange: (Boolean) -> Unit = {},
    onShowFocusModeInfo: () -> Unit = {},
    libraryViewModel: LibraryViewModel = viewModel(),
    onShowFocusModeConfirm: () -> Unit = {},
    shouldShowBottomBar: Boolean = false,
) {
    val activity = LocalActivity.current
    val libraryUiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectionState by remember { mutableStateOf(LibrarySelectionState()) }
    var showTopBarMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(LibrarySortOption.NAME_A_Z) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var bookToRename by remember { mutableStateOf<Book?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var showSingleDeleteConfirmDialog by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                prefs.edit { putString(KEY_FOLDER_URI, uri.toString()) }
                libraryViewModel.loadBooksFromFolder(uri)
            }
        }
    )

    val multipleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                libraryViewModel.addBooksFromUris(
                    uris,
                    context
                ) { addedCount, duplicateNames, addedBookNames ->
                    coroutineScope.launch {
                        when {
                            addedCount > 0 && duplicateNames.isNotEmpty() -> {
                                val duplicateMessage = if (duplicateNames.size == 1) {
                                    "'${duplicateNames.first()}' already exists"
                                } else {
                                    "${duplicateNames.size} books already exist: ${
                                        duplicateNames.take(3).joinToString(", ")
                                    }${if (duplicateNames.size > 3) "..." else ""}"
                                }

                                val addedMessage = if (addedCount == 1) {
                                    "'${addedBookNames.first()}' added"
                                } else {
                                    "$addedCount books added: ${
                                        addedBookNames.take(3).joinToString(", ")
                                    }${if (addedBookNames.size > 3) "..." else ""}"
                                }

                                showMessage("$addedMessage. $duplicateMessage")
                            }

                            addedCount > 0 -> {
                                val message = if (addedCount == 1) {
                                    "'${addedBookNames.first()}' added"
                                } else {
                                    "$addedCount books added: ${
                                        addedBookNames.take(3).joinToString(", ")
                                    }${if (addedBookNames.size > 3) "..." else ""}"
                                }
                                showMessage(message)
                            }

                            duplicateNames.isNotEmpty() -> {
                                val duplicateMessage = if (duplicateNames.size == 1) {
                                    "'${duplicateNames.first()}' already exists"
                                } else {
                                    "${duplicateNames.size} books already exist: ${
                                        duplicateNames.take(3).joinToString(", ")
                                    }${if (duplicateNames.size > 3) "..." else ""}"
                                }
                                showMessage(duplicateMessage)
                            }

                            else -> showMessage("No books added")
                        }
                    }
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        val savedUriString = prefs.getString(KEY_FOLDER_URI, null)
        if (savedUriString != null) {
            libraryViewModel.loadBooksFromFolder(savedUriString.toUri())
        }
    }

    val filteredBooks = when (val state = libraryUiState) {
        is LibraryUiState.Success -> {
            val books = if (searchQuery.isBlank()) {
                state.books
            } else {
                state.books.filter { book ->
                    book.name.contains(searchQuery, ignoreCase = true)
                }
            }

            when (sortOption) {
                LibrarySortOption.DATE_MODIFIED -> books.sortedByDescending { it.lastOpened }
                LibrarySortOption.NAME_A_Z -> books.sortedBy { it.name }
                LibrarySortOption.NAME_Z_A -> books.sortedByDescending { it.name }
            }
        }

        else -> emptyList()
    }

    val hasBooks = filteredBooks.isNotEmpty()

    fun showBulkDeleteConfirmation() {
        if (selectionState.selectedBooks.isNotEmpty()) {
            showBulkDeleteConfirmDialog = true
        }
    }

    fun deleteSelectedBooks() {
        val selectedBooks =
            filteredBooks.filter { selectionState.selectedBooks.contains(it.uri.toString()) }
        if (selectedBooks.isNotEmpty()) {
            coroutineScope.launch {
                libraryViewModel.deleteBooks(selectedBooks)
                showMessage("${selectedBooks.size} books deleted")
                selectionState = LibrarySelectionState()
                showBulkDeleteConfirmDialog = false
            }
        }
    }

    fun renameBook(book: Book, newTitle: String) {
        if (newTitle.isNotBlank()) {
            coroutineScope.launch {
                libraryViewModel.renameBook(book, newTitle)
                showMessage("Book renamed to '$newTitle'")
            }
        }
    }

    fun shareBookViaBluetooth(book: Book) {
        coroutineScope.launch {
            try {
                shareFileViaBluetoothOnly(context, book.uri, "Book: ${book.name}")
                showMessage("Sharing '${book.name}' via Bluetooth...")
            } catch (e: Exception) {
                showMessage("Failed to share book: ${e.message ?: "Make sure Bluetooth is enabled"}")
            }
        }
    }

    fun shareSelectedBooksViaBluetooth() {
        val selectedBooks =
            filteredBooks.filter { selectionState.selectedBooks.contains(it.uri.toString()) }
        if (selectedBooks.isEmpty()) return

        coroutineScope.launch {
            showMessage("Creating ZIP archive...")
            try {
                val timestamp = System.currentTimeMillis()
                val zipFile = File(context.cacheDir, "Books_$timestamp.zip")

                ZipOutputStream(FileOutputStream(zipFile)).use { zipOutputStream ->
                    selectedBooks.forEach { book ->
                        try {
                            context.contentResolver.openInputStream(book.uri)?.use { inputStream ->
                                val originalName = book.name
                                val entry = ZipEntry(originalName)
                                zipOutputStream.putNextEntry(entry)
                                inputStream.copyTo(zipOutputStream)
                                zipOutputStream.closeEntry()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                shareFileViaBluetoothOnly(context, zipFile, "Books_Collection_$timestamp.zip")
                selectionState = LibrarySelectionState()
                showMessage("Shared ${selectedBooks.size} books via Bluetooth")

            } catch (e: Exception) {
                showMessage("Error creating ZIP file: ${e.message}")
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
                    placeholder = "Search library..."
                )
            } else {
//                val headerColor = if (currentTheme == AppTheme.SEPIA) {
//                    MaterialTheme.colorScheme.surface
//                } else {
//                    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
//                }
                Surface(
                    color = getHeaderColor(currentTheme),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectionState.isSelectionMode)
                                "${selectionState.selectedBooks.size} selected"
                            else "Library",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )

                        if (selectionState.isSelectionMode) {
                            IconButton(onClick = { showBulkDeleteConfirmation() }) {
                                Icon(Icons.Default.Delete, "Delete Selected")
                            }
                            IconButton(onClick = { shareSelectedBooksViaBluetooth() }) {
                                Icon(Icons.Default.Share, "Share via Bluetooth")
                            }
                            TextButton(onClick = { selectionState = LibrarySelectionState() }) {
                                Text("Cancel")
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, "Search")
                            }

                            IconButton(
                                onClick = {
                                    if (activity != null) {
                                        if (!isFocusModeActive) {
                                            onShowFocusModeConfirm()
                                        } else {
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
                                            sortOption = LibrarySortOption.DATE_MODIFIED
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name A-Z") },
                                        onClick = {
                                            sortOption = LibrarySortOption.NAME_A_Z
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name Z-A") },
                                        onClick = {
                                            sortOption = LibrarySortOption.NAME_Z_A
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                            Box {
                                IconButton(
                                    onClick = { showTopBarMenu = true },
                                    enabled = hasBooks
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert, "More Options",
                                        tint = if (hasBooks) MaterialTheme.colorScheme.onSurface
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
                                            selectionState =
                                                LibrarySelectionState(isSelectionMode = true)
                                            showTopBarMenu = false
                                        },
                                        enabled = hasBooks
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Select All") },
                                        onClick = {
                                            val allUris =
                                                filteredBooks.map { it.uri.toString() }.toSet()
                                            selectionState = LibrarySelectionState(
                                                selectedBooks = allUris,
                                                isSelectionMode = true
                                            )
                                            showTopBarMenu = false
                                        },
                                        enabled = hasBooks
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
            if (!selectionState.isSelectionMode &&
                (libraryUiState is LibraryUiState.Success || libraryUiState is LibraryUiState.Loading)
            ) {
                val fabContainerColor = when (currentTheme) {
                    AppTheme.SEPIA -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val fabContentColor = when (currentTheme) {
                    AppTheme.SEPIA -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }

                val isTabletDevice = isTablet()

                val fabBottomPadding = if (shouldShowBottomBar) {
                    if (isTabletDevice) 60.dp else 20.dp
                } else {
                    16.dp
                }
                val fabSize = if (isTabletDevice) 60.dp else 50.dp // Smaller on mobile
                val iconSize = if (isTabletDevice) 27.dp else 22.dp // Smaller icon on mobile


                FloatingActionButton(
                    onClick = {
                        multipleFilePickerLauncher.launch(
                            arrayOf("application/pdf", "application/epub+zip")
                        )
                    },
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor,
                    modifier = Modifier
                        .padding(bottom = fabBottomPadding) // Exact positioning for bottom bar
                        .size(fabSize) // RESTORE PROPER FAB SIZE
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Books",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (libraryUiState) {
                is LibraryUiState.NoFolderSelected -> {
                    SelectFolderScreen {
                        folderPickerLauncher.launch(null)
                    }
                }

                is LibraryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is LibraryUiState.Success -> {
                    if (filteredBooks.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (searchQuery.isBlank())
                                    "No books found in this folder."
                                else
                                    "No results for '$searchQuery'"
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { folderPickerLauncher.launch(null) }) {
                                Text("Choose a Different Folder")
                            }
                        }
                    } else {
                        BookGrid(
                            books = filteredBooks,
                            navController = navController,
                            selectionState = selectionState,
                            onSelectToggle = { bookUri, selected ->
                                val newSelected = if (selected) {
                                    selectionState.selectedBooks + bookUri
                                } else {
                                    selectionState.selectedBooks - bookUri
                                }
                                selectionState = selectionState.copy(selectedBooks = newSelected)
                            },
                            onRename = { book ->
                                bookToRename = book
                                newTitle = book.name
                                showRenameDialog = true
                            },
                            onDelete = { book ->
                                bookToDelete = book
                                showSingleDeleteConfirmDialog = true
                            },
                            onShare = { book ->
                                shareBookViaBluetooth(book)
                            },
                            libraryViewModel = libraryViewModel,
                            currentTheme = currentTheme
                        )
                    }
                }

                is LibraryUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("An error occurred. The selected folder might not be accessible.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { folderPickerLauncher.launch(null) }) {
                            Text("Select Another Folder")
                        }
                    }
                }
            }
        }
    }

    if (showBulkDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmDialog = false },
            title = { Text("Delete Selected Books") },
            text = { Text("Are you sure you want to delete ${selectionState.selectedBooks.size} selected books? This action cannot be undone.") },
            confirmButton = {
                Button(onClick = { deleteSelectedBooks() }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Book") },
            text = {
                TextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Book Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        bookToRename?.let { renameBook(it, newTitle) }
                        showRenameDialog = false
                    },
                    enabled = newTitle.isNotBlank()
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

    if (showSingleDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSingleDeleteConfirmDialog = false },
            title = { Text("Delete Book") },
            text = { Text("Are you sure you want to delete '${bookToDelete?.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        bookToDelete?.let { book ->
                            coroutineScope.launch {
                                libraryViewModel.deleteBook(book)
                                showMessage("Book '${book.name}' deleted")
                            }
                        }
                        showSingleDeleteConfirmDialog = false
                        bookToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSingleDeleteConfirmDialog = false
                        bookToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BookGrid(
    books: List<Book>,
    navController: NavController,
    selectionState: LibrarySelectionState,
    onSelectToggle: (String, Boolean) -> Unit,
    onRename: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onShare: (Book) -> Unit,
    libraryViewModel: LibraryViewModel,
    currentTheme: AppTheme,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books, key = { it.uri.toString() }) { book ->
            BookThumbnail(
                book = book,
                navController = navController,
                isSelected = selectionState.selectedBooks.contains(book.uri.toString()),
                isSelectionMode = selectionState.isSelectionMode,
                onSelectToggle = { selected ->
                    onSelectToggle(book.uri.toString(), selected)
                },
                onRename = { onRename(book) },
                onDelete = { onDelete(book) },
                onShare = { onShare(book) },
                libraryViewModel = libraryViewModel,
                currentTheme = currentTheme
            )
        }
    }
}

@Composable
fun BookThumbnail(
    book: Book,
    navController: NavController,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onSelectToggle: (Boolean) -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    libraryViewModel: LibraryViewModel,
    currentTheme: AppTheme,
) {
    var showCardMenu by remember { mutableStateOf(false) }

    val displayName = if (book.name.length > 10) {
        val nameWithoutExtension = book.name.substringBeforeLast(".")
        val extension = book.name.substringAfterLast(".", "")
        val truncatedName = if (nameWithoutExtension.length > 10) {
            "${nameWithoutExtension.take(10)}..."
        } else {
            nameWithoutExtension
        }
        if (extension.isNotEmpty()) "$truncatedName.$extension" else truncatedName
    } else {
        book.name
    }

    fun Long.toFormattedDateString(): String {
        val date = java.util.Date(this)
        val format = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return format.format(date)
    }

    // FIXED: Card color logic
    val cardColor = when (currentTheme) {
        AppTheme.SEPIA -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        onClick = {
            if (isSelectionMode) {
                onSelectToggle(!isSelected)
            } else {
                libraryViewModel.updateLastOpened(book.uri)
                val encodedUri =
                    URLEncoder.encode(book.uri.toString(), StandardCharsets.UTF_8.name())
                val encodedFilename = Uri.encode(book.name)
                navController.navigate("reader/$encodedUri?type=${book.type.name}&filename=$encodedFilename")
            }
        },
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
                // Book thumbnail/image area - FIXED: Keep white background for thumbnails
                Box(
                    modifier = Modifier
                        .height(140.dp)
                        .fillMaxWidth()
                        .background(Color.White), // ALWAYS WHITE for thumbnails
                    contentAlignment = Alignment.Center
                ) {
                    val localThumbnail = book.thumbnail
                    if (localThumbnail != null) {
                        Image(
                            bitmap = localThumbnail.asImageBitmap(),
                            contentDescription = book.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = "Book Icon",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Book info area - FIXED: Use card color for info section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(cardColor) // Use card color for info background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Header row with book name and three dots menu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (!isSelectionMode) {
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
                                            Icon(
                                                Icons.Default.TextFields,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share via Bluetooth") },
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

                        // Spacer(modifier = Modifier.height(1.dp))

                        Text(
                            text = "Last Opened: ${book.lastOpened.toFormattedDateString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(1.dp))

                        if (book.pageCount > 0) {
                            Text(
                                text = "${book.pageCount} pages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
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

// Copy any SAF content:// Uri to a cache file, so Bluetooth can read it
private fun copyUriToCacheFile(context: Context, source: Uri): File {
    val cr = context.contentResolver

    // Try to get a filename from the provider
    var name = runCatching {
        cr.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // Fallback extension from MIME
    val mime = cr.getType(source) ?: "*/*"
    val fallbackExt = when (mime.lowercase()) {
        "application/pdf" -> "pdf"
        "application/epub+zip" -> "epub"
        "application/zip" -> "zip"
        else -> "bin"
    }

    if (name.isNullOrBlank()) {
        name = "shared_${System.currentTimeMillis()}.$fallbackExt"
    } else if (!name.contains('.')) {
        name = "$name.$fallbackExt"
    }

    // Sanitize name
    val safeName = name.replace("[^A-Za-z0-9._-]".toRegex(), "_")
    val outFile = File(context.cacheDir, safeName)

    cr.openInputStream(source).use { input ->
        FileOutputStream(outFile).use { output ->
            requireNotNull(input) { "Cannot open source URI" }
            input.copyTo(output)
        }
    }
    return outFile
}

// Overload used by per-card share (content Uri). Always convert to a FileProvider Uri.
private fun shareFileViaBluetoothOnly(context: Context, uri: Uri, subject: String) {
    val cached = copyUriToCacheFile(context, uri)
    shareFileViaBluetoothOnly(context, cached, subject)
}

// Overload used when you already have a File (e.g., zip in multi-select)
private fun shareFileViaBluetoothOnly(context: Context, file: File, subject: String) {
    val btPackage = "com.android.bluetooth"
    val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val cr = context.contentResolver

    // Explicitly grant read permission — some Bluetooth stacks ignore ClipData alone
    context.grantUriPermission(btPackage, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun sendWithMime(mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, subject.take(50))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(cr, "Shared File", fileUri)
            setPackage(btPackage) // strictly Bluetooth only
        }

        // Ensure BT can handle this type
        val canHandle = context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        if (!canHandle) throw ActivityNotFoundException("Bluetooth cannot handle MIME: $mime")

        context.startActivity(intent)
    }

    val ext = file.extension.lowercase(Locale.ROOT)
    try {
        when (ext) {
            "pdf" -> sendWithMime("application/pdf")
            "zip" -> sendWithMime("application/zip")
            "epub" -> {
                // Many BT stacks don’t accept application/epub+zip — use "*/*" for compatibility
                try {
                    sendWithMime("*/*")
                } catch (_: Exception) {
                    // Final fallback to octet-stream
                    sendWithMime("application/octet-stream")
                }
            }

            else -> sendWithMime("*/*")
        }
    } catch (_: ActivityNotFoundException) {
        throw Exception("Bluetooth sharing not available.")
    }
}

@Composable
fun SelectFolderScreen(onSelectFolder: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome to Super Reader", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "To get started, please select your main book folder.", textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onSelectFolder) {
                Text("Select Book Folder")
            }
        }
    }
}

@Composable
private fun getHeaderColor(currentTheme: AppTheme): Color {
    return when (currentTheme) {
        AppTheme.SEPIA -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    }
}

@Composable
fun isTablet(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.smallestScreenWidthDp >= 600
}