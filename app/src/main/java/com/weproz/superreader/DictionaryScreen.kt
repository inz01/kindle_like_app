// DictionaryScreen.kt

package com.weproz.superreader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.weproz.superreader.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SortOptions {
    A_TO_Z, Z_TO_A, CUSTOM_FIRST // REMOVED RECENT_FIRST
}

data class DictionarySelectionState(
    val selectedWords: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    onShowThemeDialog: (Boolean) -> Unit,
    currentTheme: AppTheme,
    isFocusModeActive: Boolean,
    focusModeManager: FocusModeManager?,
    onFocusModeChange: (Boolean) -> Unit,
    onShowFocusModeInfo: () -> Unit,
    onShowFocusModeConfirm: () -> Unit,
    shouldShowBottomBar: Boolean = false,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val dictionaryDao = remember { AppDatabase.getDatabase(context).dictionaryDao() }

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf(emptyList<DictionaryWord>()) }
    var showWordDetail by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf<DictionaryWord?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var currentSortOption by remember { mutableStateOf(SortOptions.A_TO_Z) }
    var isSearchActive by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showInitialTabLoading by remember { mutableStateOf(true) }

    // NEW STATE VARIABLES
    var showTopBarMenu by remember { mutableStateOf(false) }
    var showDeleteCustomConfirmDialog by remember { mutableStateOf(false) }
    var showSingleDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectionState by remember { mutableStateOf(DictionarySelectionState()) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var renameMeaning by remember { mutableStateOf("") }
    var renameExample by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }  // Loading for CRUD

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    fun showMessage(message: String) {
        coroutineScope.launch {
            println("Message: $message")
        }
    }

    // Initial Loading screen for dictionary or when navigate from other tabs.
    LaunchedEffect(Unit) {
        showInitialTabLoading = true
        kotlinx.coroutines.delay(1200) // tweak duration if you want
        showInitialTabLoading = false
    }

    // Load words from database - OPTIMIZED SORTING
    LaunchedEffect(searchQuery, currentSortOption) {
        if (searchQuery.isEmpty()) {
            dictionaryDao.getAllWords().collect { wordList ->
                words = sortWords(wordList, currentSortOption)
                isLoading = false
            }
        } else {
            dictionaryDao.searchWords(searchQuery).collect { wordList ->
                words = sortWords(wordList, currentSortOption)
                isLoading = false
            }
        }
    }

    // When entering selection mode, set sort to CUSTOM_FIRST
    LaunchedEffect(selectionState.isSelectionMode) {
        if (selectionState.isSelectionMode) {
            currentSortOption = SortOptions.CUSTOM_FIRST
            // NEW: jump to top
            coroutineScope.launch { lazyListState.scrollToItem(0) }
        }
    }

    LaunchedEffect(words, currentSortOption, selectionState.isSelectionMode) {
        if (selectionState.isSelectionMode || currentSortOption == SortOptions.CUSTOM_FIRST) {
            lazyListState.scrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                    placeholder = "Search words..."
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
                            if (selectionState.isSelectionMode)
                                "${selectionState.selectedWords.size} selected"
                            else "Dictionary",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )

                        if (selectionState.isSelectionMode) {
                            IconButton(
                                onClick = {
                                    if (selectionState.selectedWords.isNotEmpty()) {
                                        showDeleteCustomConfirmDialog = true
                                    }
                                },
                                enabled = selectionState.selectedWords.isNotEmpty() && !isProcessing
                            ) {
                                Icon(Icons.Default.Delete, "Delete Selected")
                            }
                            TextButton(
                                onClick = {
                                    if (!isProcessing) {
                                        selectionState = DictionarySelectionState()
                                    }
                                },
                                enabled = !isProcessing
                            ) {
                                Text("Cancel")
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, "Search")
                            }

                            // Focus Button
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
                                        text = { Text("A to Z") },
                                        onClick = {
                                            currentSortOption = SortOptions.A_TO_Z
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Z to A") },
                                        onClick = {
                                            currentSortOption = SortOptions.Z_TO_A
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Custom First") },
                                        onClick = {
                                            currentSortOption = SortOptions.CUSTOM_FIRST
                                            showSortMenu = false
                                            coroutineScope.launch { lazyListState.scrollToItem(0) }
                                        }
                                    )
                                }
                            }

                            // THREE DOT MENU
                            Box {
                                IconButton(
                                    onClick = { showTopBarMenu = true },
                                    enabled = !isProcessing
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        "More Options",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTopBarMenu,
                                    onDismissRequest = { showTopBarMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Select Custom Words") },
                                        onClick = {
                                            // FIXED: Start with empty selection instead of selecting all
                                            selectionState = DictionarySelectionState(
                                                selectedWords = emptySet(),
                                                isSelectionMode = true
                                            )
                                            showTopBarMenu = false
                                        },
                                        enabled = words.any { it.isCustom } && !isProcessing
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Select All Custom") },
                                        onClick = {
                                            val allCustomIds = words.filter { it.isCustom }
                                                .map { it.id.toString() }.toSet()
                                            selectionState = DictionarySelectionState(
                                                selectedWords = allCustomIds,
                                                isSelectionMode = true
                                            )
                                            showTopBarMenu = false
                                        },
                                        enabled = words.any { it.isCustom } && !isProcessing
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh") },
                                        onClick = {
                                            showTopBarMenu = false
                                            isLoading = true
                                            coroutineScope.launch {
                                                try {
                                                    val updated = if (searchQuery.isBlank()) {
                                                        dictionaryDao.getAllWords().first()
                                                    } else {
                                                        dictionaryDao.searchWords(searchQuery)
                                                            .first()
                                                    }
                                                    words = sortWords(updated, currentSortOption)
                                                } finally {
                                                    isLoading = false
                                                }
                                            }
                                        },
                                        enabled = !isProcessing
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
            if (!isSearchActive && !isLoading && !selectionState.isSelectionMode) {
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
                    onClick = { showAddDialog = true },
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor,
                    modifier = Modifier
                        .padding(bottom = fabBottomPadding) // Exact positioning for bottom bar
                        .size(fabSize) // RESTORE PROPER FAB SIZE
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Word",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    ) { paddingValues ->

        BackHandler(enabled = isSearchActive) {
            if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
            }
        }

        // Handle back gesture for selection mode
        BackHandler(enabled = selectionState.isSelectionMode) {
            if (selectionState.isSelectionMode) {
                selectionState = DictionarySelectionState()
            }
        }

        // Show loading screen when data is loading
        if (isLoading || showInitialTabLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Loading dictionary...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        } else if (words.isEmpty()) {
            // Show empty state when no words found
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = "Empty dictionary",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "No words found for '$searchQuery'"
                        } else {
                            "Dictionary is empty"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "Try searching for something else"
                        } else {
                            "WordNet database not loaded\nAdd words using the + button"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    if (searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add word",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add \"$searchQuery\" to Dictionary")
                        }
                    }
                }
            }
        } else {
            // USE SIMPLE LAZYCOLUMN WITH CUSTOM SCROLLBAR
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    state = lazyListState
                ) {
                    items(words, key = { it.id }) { word ->
                        SimpleDictionaryWordCard(
                            word = word,
                            onClick = {
                                if (selectionState.isSelectionMode) {
                                    if (word.isCustom) {
                                        // Toggle selection only for custom
                                        val wordId = word.id.toString()
                                        val newSelected =
                                            if (selectionState.selectedWords.contains(wordId)) {
                                                selectionState.selectedWords - wordId
                                            } else {
                                                selectionState.selectedWords + wordId
                                            }
                                        selectionState =
                                            selectionState.copy(selectedWords = newSelected)
                                    }
                                } else {
                                    selectedWord = word
                                    showWordDetail = true
                                }
                            },
                            currentTheme = currentTheme,
                            isSelected = selectionState.selectedWords.contains(word.id.toString()),
                            isSelectionMode = selectionState.isSelectionMode,
                            onRename = { wordToRename ->
                                if (wordToRename.isCustom) {
                                    selectedWord = wordToRename
                                    renameText = wordToRename.word
                                    renameMeaning = wordToRename.meaning
                                    renameExample = wordToRename.example
                                    showRenameDialog = true
                                }
                            },
                            onDelete = { wordToDelete ->
                                if (wordToDelete.isCustom) {
                                    selectedWord = wordToDelete
                                    showSingleDeleteConfirmDialog = true
                                }
                            }
                        )
                    }
                }

                // IMPROVED GOOGLE PHOTOS STYLE SCROLLBAR
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            end = 4.dp,
                            top = 8.dp,
                            bottom = if (shouldShowBottomBar) 22.dp else 8.dp
                        )
                        .width(6.dp) // slightly thicker
                        .fillMaxHeight()
                ) {
                    val trackHeight = maxHeight
                    val thumbHeight = remember { 40.dp } // keep height unchanged
                    val maxOffset = trackHeight - thumbHeight

                    val scrollProgress by remember {
                        derivedStateOf {
                            if (lazyListState.layoutInfo.totalItemsCount > 0 && lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                                val firstVisible = lazyListState.firstVisibleItemIndex
                                val totalItems = lazyListState.layoutInfo.totalItemsCount
                                val viewportHeight =
                                    lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                                val firstVisibleItemOffset =
                                    lazyListState.firstVisibleItemScrollOffset
                                val firstVisibleItemHeight =
                                    lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size
                                        ?: 0

                                if (totalItems > 0 && viewportHeight > 0) {
                                    val itemHeight =
                                        firstVisibleItemHeight.toFloat().coerceAtLeast(1f)
                                    val scrollableRange = (totalItems * itemHeight) - viewportHeight
                                    val currentScroll =
                                        (firstVisible * itemHeight + firstVisibleItemOffset)
                                    if (scrollableRange <= 0f) 0f else (currentScroll / scrollableRange).coerceIn(
                                        0f,
                                        1f
                                    )
                                } else 0f
                            } else 0f
                        }
                    }

                    // Show if user is interacting OR y-axis scroll is greater than 2px
                    var isScrollbarInteracting by remember { mutableStateOf(false) }
                    val scrolledY by remember {
                        derivedStateOf {
                            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 2
                        }
                    }
                    val showScrollbar by remember(isScrollbarInteracting, scrolledY) {
                        derivedStateOf { isScrollbarInteracting || scrolledY }
                    }

                    AnimatedVisibility(
                        visible = showScrollbar,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                .pointerInput(Unit) {
                                    // Tap to jump
                                    detectTapGestures(
                                        onPress = {
                                            isScrollbarInteracting = true
                                            tryAwaitRelease()
                                            isScrollbarInteracting = false
                                        }
                                    ) { offset ->
                                        val thumbPx = thumbHeight.toPx()
                                        val usable = (size.height - thumbPx).coerceAtLeast(1f)
                                        val ratio =
                                            ((offset.y - thumbPx / 2f) / usable).coerceIn(0f, 1f)
                                        val total = lazyListState.layoutInfo.totalItemsCount
                                        if (total > 0) {
                                            val target = (ratio * (total - 1)).toInt()
                                            coroutineScope.launch {
                                                lazyListState.scrollToItem(
                                                    target.coerceIn(
                                                        0,
                                                        total - 1
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    // Hold and drag to scrub
                                    detectDragGestures(
                                        onDragStart = { isScrollbarInteracting = true },
                                        onDragCancel = { isScrollbarInteracting = false },
                                        onDragEnd = { isScrollbarInteracting = false }
                                    ) { change, _ ->
                                        change.consume()
                                        val thumbPx = thumbHeight.toPx()
                                        val usable = (size.height - thumbPx).coerceAtLeast(1f)
                                        val ratio =
                                            ((change.position.y - thumbPx / 2f) / usable).coerceIn(
                                                0f,
                                                1f
                                            )
                                        val total = lazyListState.layoutInfo.totalItemsCount
                                        if (total > 0) {
                                            val target = (ratio * (total - 1)).toInt()
                                            coroutineScope.launch {
                                                lazyListState.scrollToItem(
                                                    target.coerceIn(
                                                        0,
                                                        total - 1
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(thumbHeight) // unchanged
                                    .fillMaxWidth()
                                    .offset(y = (scrollProgress * maxOffset.value).dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }

        // GLOBAL LOADING OVERLAY FOR CRUD (including delete operations)
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Processing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // DIALOGS
        if (showAddDialog) {
            BackHandler(enabled = true) {
                if (!isProcessing) {
                    showAddDialog = false
                }
            }
            SimpleAddWordDialog(
                onDismiss = { showAddDialog = false },
                onWordAdded = { newWord ->
                    showAddDialog = false
                },
                dictionaryDao = dictionaryDao,
                onProcessing = { isProcessing = it }
            )
        }

        if (showWordDetail && selectedWord != null) {
            BackHandler(enabled = true) {
                showWordDetail = false
            }
            SimpleWordDetailDialog(
                word = selectedWord!!,
                onDismiss = { showWordDetail = false }
            )
        }

        if (showDeleteCustomConfirmDialog) {
            BackHandler(enabled = true) {
                if (!isProcessing) {
                    showDeleteCustomConfirmDialog = false
                }
            }
            AlertDialog(
                onDismissRequest = {
                    if (!isProcessing) {
                        showDeleteCustomConfirmDialog = false
                    }
                },
                title = { Text("Delete Selected Words") },
                text = {
                    Text("Are you sure you want to delete ${selectionState.selectedWords.size} selected custom words? This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isProcessing = true
                            coroutineScope.launch {
                                try {
                                    val selectedIds =
                                        selectionState.selectedWords.map { it.toInt() }
                                    // Delete all selected words in a single transaction
                                    selectedIds.forEach { id ->
                                        dictionaryDao.deleteWord(id)
                                    }

                                    // Wait for the database to update and refresh the list
                                    val updatedWords = dictionaryDao.getAllWords().first()
                                    words = sortWords(updatedWords, currentSortOption)

                                    showMessage("${selectedIds.size} custom words deleted")
                                    selectionState = DictionarySelectionState()
                                    showDeleteCustomConfirmDialog = false
                                } catch (e: Exception) {
                                    showMessage("Error deleting words: ${e.message}")
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deleting...")
                        } else {
                            Text("Delete")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (!isProcessing) {
                                showDeleteCustomConfirmDialog = false
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showSingleDeleteConfirmDialog && selectedWord != null && selectedWord!!.isCustom) {
            BackHandler(enabled = true) {
                if (!isProcessing) {
                    showSingleDeleteConfirmDialog = false
                    selectedWord = null
                }
            }
            AlertDialog(
                onDismissRequest = {
                    if (!isProcessing) {
                        showSingleDeleteConfirmDialog = false
                        selectedWord = null
                    }
                },
                title = { Text("Delete Custom Word") },
                text = {
                    Text("Are you sure you want to delete '${selectedWord?.word}'? This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isProcessing = true
                            selectedWord?.let { word ->
                                coroutineScope.launch {
                                    try {
                                        dictionaryDao.deleteWord(word.id)

                                        // Wait for the database to update and refresh the list
                                        val updatedWords = dictionaryDao.getAllWords().first()
                                        words = sortWords(updatedWords, currentSortOption)

                                        showMessage("'${word.word}' deleted")
                                        showSingleDeleteConfirmDialog = false
                                        selectedWord = null
                                    } catch (e: Exception) {
                                        showMessage("Error deleting word: ${e.message}")
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deleting...")
                        } else {
                            Text("Delete")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (!isProcessing) {
                                showSingleDeleteConfirmDialog = false
                                selectedWord = null
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRenameDialog && selectedWord != null) {
            // LOADING OVERLAY FOR RENAME DIALOG - SEPARATE FROM DIALOG CONTENT
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Updating word...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            AlertDialog(
                onDismissRequest = {
                    if (!isProcessing) {
                        showRenameDialog = false
                        selectedWord = null
                        renameText = ""
                        renameMeaning = ""
                        renameExample = ""
                    }
                },
                title = { Text("Edit Custom Word") },
                text = {
                    Column {
                        Text(
                            "Current: ${selectedWord?.word}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Word") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isProcessing
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameMeaning,
                            onValueChange = { renameMeaning = it },
                            label = { Text("Meaning") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            enabled = !isProcessing
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameExample,
                            onValueChange = { renameExample = it },
                            label = { Text("Example") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            enabled = !isProcessing
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameText.isNotBlank() && renameMeaning.isNotBlank() && selectedWord != null) {
                                isProcessing = true
                                coroutineScope.launch {
                                    try {
                                        val existingWords = dictionaryDao.getAllWords().first()
                                        val isDuplicate = existingWords.any {
                                            it.word.equals(
                                                renameText.trim(),
                                                ignoreCase = true
                                            ) && it.id != selectedWord!!.id
                                        }

                                        if (isDuplicate) {
                                            showMessage("A word with this name already exists!")
                                            isProcessing = false
                                        } else {
                                            val updatedWord = selectedWord!!.copy(
                                                word = renameText.trim(),
                                                meaning = renameMeaning.trim(),
                                                example = renameExample.trim()
                                            )
                                            dictionaryDao.updateWord(updatedWord)

                                            // Wait for the database to update and refresh the list
                                            val updatedWords = dictionaryDao.getAllWords().first()
                                            words = sortWords(updatedWords, currentSortOption)

                                            showMessage("Word updated")
                                            showRenameDialog = false
                                            selectedWord = null
                                            renameText = ""
                                            renameMeaning = ""
                                            renameExample = ""
                                            isProcessing = false
                                        }
                                    } catch (e: Exception) {
                                        showMessage("Error updating word: ${e.message}")
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        enabled = renameText.isNotBlank() && renameMeaning.isNotBlank() && !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...") // CHANGED FROM "Save" TO "Saving..."
                        } else {
                            Text("Save")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (!isProcessing) {
                                showRenameDialog = false
                                selectedWord = null
                                renameText = ""
                                renameMeaning = ""
                                renameExample = ""
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}


// SIMPLIFIED WORD CARD
@Composable
fun SimpleDictionaryWordCard(
    word: DictionaryWord,
    onClick: () -> Unit,
    currentTheme: AppTheme,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onRename: (DictionaryWord) -> Unit = {},
    onDelete: (DictionaryWord) -> Unit = {},
) {
    var showCardMenu by remember { mutableStateOf(false) }

    val cardColor = when (currentTheme) {
        AppTheme.SEPIA -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant // This now works properly
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                cardColor
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode && word.isCustom) {  // Only show checkbox for custom words
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (word.isCustom) {
                    Row {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "CUSTOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (!isSelectionMode) {
                            IconButton(
                                onClick = { showCardMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Word Options",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showCardMenu,
                                onDismissRequest = { showCardMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        onRename(word)
                                        showCardMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        onDelete(word)
                                        showCardMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Part of speech
            Text(
                text = word.partOfSpeech,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Meaning
            Text(
                text = word.meaning,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Example
            if (word.example.isNotEmpty()) {
                Text(
                    text = "Example: \"${word.example}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Date for custom words
            if (word.isCustom) {
                Text(
                    text = "Added ${word.dateAdded}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

// SIMPLIFIED ADD WORD DIALOG
@Composable
fun SimpleAddWordDialog(
    onDismiss: () -> Unit,
    onWordAdded: (DictionaryWord) -> Unit,
    dictionaryDao: DictionaryDao,
    onProcessing: (Boolean) -> Unit,  // Callback for loading state
) {
    var word by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }
    var showDuplicateAlert by remember { mutableStateOf(false) }
    var isDialogProcessing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // LOADING OVERLAY FOR DIALOG - SEPARATE FROM DIALOG CONTENT
    if (isDialogProcessing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Adding word...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDialogProcessing) {
                onDismiss()
            }
        },
        title = { Text("Add New Word") },
        text = {
            Column {
                if (showDuplicateAlert) {
                    Text(
                        text = "This word already exists in the dictionary!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = word,
                    onValueChange = {
                        word = it
                        showDuplicateAlert = false
                    },
                    label = { Text("Word") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showDuplicateAlert,
                    enabled = !isDialogProcessing
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    label = { Text("Meaning") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    enabled = !isDialogProcessing
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text("Example (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !isDialogProcessing
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (word.isNotEmpty() && meaning.isNotEmpty()) {
                        isDialogProcessing = true
                        onProcessing(true)
                        coroutineScope.launch {
                            val existingWords = dictionaryDao.getAllWords().first()
                            val isDuplicate = existingWords.any {
                                it.word.equals(word.trim(), ignoreCase = true)
                            }

                            if (isDuplicate) {
                                showDuplicateAlert = true
                                isDialogProcessing = false
                                onProcessing(false)
                            } else {
                                val newWord = DictionaryWord(
                                    word = word.trim(),
                                    meaning = meaning.trim(),
                                    example = example.trim(),
                                    isCustom = true,
                                    dateAdded = "today"
                                )
                                dictionaryDao.insertWord(newWord)
                                // INCREASED LOADING TIME FOR BETTER UX
                                kotlinx.coroutines.delay(800)
                                onWordAdded(newWord)
                                onDismiss()
                                isDialogProcessing = false
                                onProcessing(false)
                            }
                        }
                    }
                },
                enabled = word.isNotEmpty() && meaning.isNotEmpty() && !isDialogProcessing
            ) {
                if (isDialogProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adding...")
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (!isDialogProcessing) {
                        onDismiss()
                    }
                },
                enabled = !isDialogProcessing
            ) {
                Text("Cancel")
            }
        }
    )
}

// SIMPLIFIED WORD DETAIL DIALOG
@Composable
fun SimpleWordDetailDialog(
    word: DictionaryWord,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                word.word,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Meaning:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    word.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (word.example.isNotEmpty()) {
                    Text(
                        "Example:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "\"${word.example}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (word.isCustom) {
                    Text(
                        "✓ Custom word • Added ${word.dateAdded}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// CUSTOM SEARCH BAR (UNCHANGED)
@Composable
fun CustomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String = "Search words...",
) {

    BackHandler(enabled = true) {
        onClose()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f)
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, "Clear search")
                }
            }
        }
    }
}

private fun sortWords(words: List<DictionaryWord>, sortOption: SortOptions): List<DictionaryWord> {
    return when (sortOption) {
        SortOptions.A_TO_Z -> words.sortedBy { it.word.lowercase() }
        SortOptions.Z_TO_A -> words.sortedByDescending { it.word.lowercase() }
        SortOptions.CUSTOM_FIRST -> words.sortedWith(
            compareByDescending<DictionaryWord> { it.isCustom }.thenBy { it.word.lowercase() }
        )
    }
}

@Composable
private fun getHeaderColor(currentTheme: AppTheme): Color {
    return when (currentTheme) {
        AppTheme.SEPIA -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    }
}