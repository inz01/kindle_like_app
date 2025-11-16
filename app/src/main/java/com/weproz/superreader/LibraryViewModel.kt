// LibraryViewModel.kt

package com.weproz.superreader

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "SuperReaderPrefs"
private const val KEY_ADDED_BOOKS = "added_books"
private const val KEY_LAST_OPENED_PREFIX = "last_opened_"
private const val KEY_SELECTED_FOLDER = "selected_folder_uri" // NEW: Store folder URI

sealed interface LibraryUiState {
    object NoFolderSelected : LibraryUiState
    object Loading : LibraryUiState
    data class Success(val books: List<Book>) : LibraryUiState
    object Error : LibraryUiState
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.NoFolderSelected)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // FIXED: Properly load persisted folder and books
    fun loadPersistedFolder() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading

            try {
                // Check if we have a persisted folder URI
                val folderUriString = prefs.getString(KEY_SELECTED_FOLDER, null)

                if (folderUriString != null) {
                    // We have a persisted folder, load books from it
                    val folderUri = folderUriString.toUri()
                    loadBooksFromFolder(folderUri)
                } else {
                    // Check if we have individual added books (from the add button)
                    val persistedBooks = prefs.getStringSet(KEY_ADDED_BOOKS, emptySet()) ?: emptySet()

                    if (persistedBooks.isNotEmpty()) {
                        // Load individual books without a main folder
                        loadIndividualBooks()
                    } else {
                        // No folder and no individual books, show welcome screen
                        _uiState.value = LibraryUiState.NoFolderSelected
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = LibraryUiState.Error
            }
        }
    }


    fun loadBooksFromFolder(folderUri: Uri) {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                // PERSIST the folder selection
                prefs.edit {
                    putString(KEY_SELECTED_FOLDER, folderUri.toString())
                }

                val bookList = mutableListOf<Book>()
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>().applicationContext
                    val contentResolver = context.contentResolver

                    // Take persistent permission for the folder
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            folderUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Permission might already be granted
                        e.printStackTrace()
                    }

                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                        folderUri,
                        DocumentsContract.getTreeDocumentId(folderUri)
                    )

                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    )

                    contentResolver.query(childrenUri, projection, null, null, null)
                        ?.use { cursor ->
                            val idColumn =
                                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val nameColumn =
                                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val lastModifiedColumn =
                                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                            while (cursor.moveToNext()) {
                                val docId = cursor.getString(idColumn)
                                val nameRaw = cursor.getString(nameColumn) ?: continue
                                val lastModified = cursor.getLong(lastModifiedColumn)
                                val name = extractFileNameFromDisplayName(nameRaw)
                                val docUri =
                                    DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)

                                val bookType = when {
                                    name.endsWith(".pdf", ignoreCase = true) -> BookType.PDF
                                    name.endsWith(".epub", ignoreCase = true) -> BookType.EPUB
                                    else -> BookType.OTHER
                                }

                                if (bookType != BookType.OTHER) {
                                    // Get persisted lastOpened timestamp
                                    val persistedLastOpened = prefs.getLong(
                                        "$KEY_LAST_OPENED_PREFIX$docUri",
                                        lastModified
                                    )

                                    // ALWAYS regenerate thumbnail for main folder books on every load
                                    var thumbnail: Bitmap? = null
                                    var pageCount = 0
                                    if (bookType == BookType.PDF) {
                                        try {
                                            val details = generatePdfDetails(context, docUri)
                                            thumbnail = details.first
                                            pageCount = details.second
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            // Continue even if thumbnail generation fails
                                        }
                                    }

                                    val book = Book(
                                        docUri,
                                        name,
                                        bookType,
                                        thumbnail,
                                        pageCount,
                                        lastModified,
                                        persistedLastOpened
                                    )
                                    bookList.add(book)
                                }
                            }
                        }

                    // Load persisted individual books (from add button) with proper URI permission handling
                    val persisted = prefs.getStringSet(KEY_ADDED_BOOKS, emptySet()) ?: emptySet()
                    val invalidUris = mutableSetOf<String>()

                    for (uriString in persisted) {
                        try {
                            val uri = uriString.toUri()
                            // Skip if already in main folder (avoid duplicates)
                            if (bookList.any { it.uri.toString() == uriString }) continue

                            // ✅ FIX: Verify URI permission before accessing
                            val hasPermission = try {
                                context.contentResolver.openInputStream(uri)?.use { true } ?: false
                            } catch (_: Exception) {
                                false
                            }

                            if (!hasPermission) {
                                invalidUris.add(uriString)
                                continue
                            }

                            val name =
                                getFileName(context, uri) ?: uri.lastPathSegment ?: uri.toString()
                            val type = when {
                                name.endsWith(".pdf", true) -> BookType.PDF
                                name.endsWith(".epub", true) -> BookType.EPUB
                                else -> BookType.OTHER
                            }
                            if (type == BookType.OTHER) continue

                            // Get persisted lastOpened timestamp
                            val persistedLastOpened = prefs.getLong(
                                "$KEY_LAST_OPENED_PREFIX$uriString",
                                System.currentTimeMillis()
                            )

                            // ALWAYS regenerate thumbnail for persisted books on every load
                            var thumbnail: Bitmap? = null
                            var pageCount = 0
                            if (type == BookType.PDF) {
                                try {
                                    val details = generatePdfDetails(context, uri)
                                    thumbnail = details.first
                                    pageCount = details.second
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // Continue even if thumbnail generation fails
                                }
                            }

                            val book = Book(
                                uri,
                                name,
                                type,
                                thumbnail,
                                pageCount,
                                System.currentTimeMillis(),
                                persistedLastOpened
                            )
                            bookList.add(book)
                        } catch (e: Exception) {
                            invalidUris.add(uriString)
                            e.printStackTrace()
                        }
                    }

                    // Remove invalid URIs from persistence
                    if (invalidUris.isNotEmpty()) {
                        val persistedSet =
                            prefs.getStringSet(KEY_ADDED_BOOKS, emptySet())?.toMutableSet()
                                ?: mutableSetOf()
                        persistedSet.removeAll(invalidUris)
                        prefs.edit { putStringSet(KEY_ADDED_BOOKS, persistedSet) }
                    }
                }
                val deduped = bookList.distinctBy { it.uri.toString() }
                val sorted = deduped.sortedByDescending { it.lastOpened }
                _uiState.update { LibraryUiState.Success(sorted) }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = LibraryUiState.Error
            }
        }
    }

    // NEW: Load individual books without a main folder
    private fun loadIndividualBooks() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                val bookList = mutableListOf<Book>()
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>().applicationContext
                    val persisted = prefs.getStringSet(KEY_ADDED_BOOKS, emptySet()) ?: emptySet()
                    val invalidUris = mutableSetOf<String>()

                    for (uriString in persisted) {
                        try {
                            val uri = uriString.toUri()

                            // Verify URI permission before accessing
                            val hasPermission = try {
                                context.contentResolver.openInputStream(uri)?.use { true } ?: false
                            } catch (_: Exception) {
                                false
                            }

                            if (!hasPermission) {
                                invalidUris.add(uriString)
                                continue
                            }

                            val name = getFileName(context, uri) ?: uri.lastPathSegment ?: uri.toString()
                            val type = when {
                                name.endsWith(".pdf", true) -> BookType.PDF
                                name.endsWith(".epub", true) -> BookType.EPUB
                                else -> BookType.OTHER
                            }
                            if (type == BookType.OTHER) continue

                            // Get persisted lastOpened timestamp
                            val persistedLastOpened = prefs.getLong(
                                "$KEY_LAST_OPENED_PREFIX$uriString",
                                System.currentTimeMillis()
                            )

                            // Generate thumbnail for PDFs
                            var thumbnail: Bitmap? = null
                            var pageCount = 0
                            if (type == BookType.PDF) {
                                try {
                                    val details = generatePdfDetails(context, uri)
                                    thumbnail = details.first
                                    pageCount = details.second
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            val book = Book(
                                uri,
                                name,
                                type,
                                thumbnail,
                                pageCount,
                                System.currentTimeMillis(),
                                persistedLastOpened
                            )
                            bookList.add(book)
                        } catch (e: Exception) {
                            invalidUris.add(uriString)
                            e.printStackTrace()
                        }
                    }

                    // Remove invalid URIs from persistence
                    if (invalidUris.isNotEmpty()) {
                        val persistedSet = prefs.getStringSet(KEY_ADDED_BOOKS, emptySet())?.toMutableSet() ?: mutableSetOf()
                        persistedSet.removeAll(invalidUris)
                        prefs.edit { putStringSet(KEY_ADDED_BOOKS, persistedSet) }
                    }
                }

                val sorted = bookList.sortedByDescending { it.lastOpened }
                _uiState.update { LibraryUiState.Success(sorted) }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = LibraryUiState.Error
            }
        }
    }

    // ✅ FIX: Add persistent URI permission when adding books
    fun addBooksFromUris(
        uris: List<Uri>,
        context: Context,
        onResult: (addedCount: Int, duplicateNames: List<String>, addedBookNames: List<String>) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val currentBooks = (uiState.value as? LibraryUiState.Success)?.books ?: emptyList()
                val currentUris = currentBooks.map { it.uri.toString() }.toSet()
                val currentNames = currentBooks.map { it.name.lowercase() }.toSet()

                val addedBooks = mutableListOf<Book>()
                val duplicateNames = mutableListOf<String>()
                val addedBookNames = mutableListOf<String>()

                uris.forEach { uri ->
                    try {
                        // ✅ FIX: Take persistent URI permission for added books
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            // Permission might already be granted or not available
                            e.printStackTrace()
                        }

                        val name =
                            getFileName(context, uri) ?: uri.lastPathSegment ?: "Unknown File"
                        if (currentUris.contains(uri.toString()) || currentNames.contains(name.lowercase())) {
                            duplicateNames.add(name)
                        } else {
                            val type = when {
                                name.endsWith(".pdf", true) -> BookType.PDF
                                name.endsWith(".epub", true) -> BookType.EPUB
                                else -> BookType.OTHER
                            }
                            if (type != BookType.OTHER) {
                                val (thumbnail, pageCount) = if (type == BookType.PDF) {
                                    generatePdfDetails(context, uri)
                                } else {
                                    Pair(null, 0)
                                }

                                // Persist the initial lastOpened timestamp
                                val currentTime = System.currentTimeMillis()
                                prefs.edit {
                                    putLong("$KEY_LAST_OPENED_PREFIX$uri", currentTime)
                                }

                                val book = Book(
                                    uri,
                                    name,
                                    type,
                                    thumbnail,
                                    pageCount,
                                    currentTime,
                                    currentTime
                                )
                                addedBooks.add(book)
                                addedBookNames.add(name)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Update persisted set
                if (addedBooks.isNotEmpty()) {
                    val persisted = prefs.getStringSet(KEY_ADDED_BOOKS, emptySet())?.toMutableSet()
                        ?: mutableSetOf()
                    addedBooks.forEach { book ->
                        persisted.add(book.uri.toString())
                    }
                    prefs.edit { putStringSet(KEY_ADDED_BOOKS, persisted) }

                    // Update UI state
                    _uiState.update { current ->
                        val currentBooks =
                            (current as? LibraryUiState.Success)?.books ?: emptyList()
                        val updatedBooks =
                            (currentBooks + addedBooks).distinctBy { it.uri.toString() }
                                .sortedByDescending { it.lastOpened }
                        LibraryUiState.Success(updatedBooks)
                    }
                }

                onResult(addedBooks.size, duplicateNames, addedBookNames)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(0, emptyList(), emptyList())
            }
        }
    }

    fun renameBook(book: Book, newTitle: String) {
        viewModelScope.launch {
            try {
                _uiState.update { current ->
                    when (current) {
                        is LibraryUiState.Success -> {
                            val updatedBooks = current.books.map {
                                if (it.uri.toString() == book.uri.toString()) {
                                    it.copy(name = newTitle)
                                } else {
                                    it
                                }
                            }
                            LibraryUiState.Success(updatedBooks)
                        }

                        else -> current
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            try {
                _uiState.update { current ->
                    when (current) {
                        is LibraryUiState.Success -> {
                            val updatedBooks =
                                current.books.filter { it.uri.toString() != book.uri.toString() }
                            LibraryUiState.Success(updatedBooks)
                        }

                        else -> current
                    }
                }

                // Remove from persisted sets
                val uriString = book.uri.toString()
                val persisted = prefs.getStringSet(KEY_ADDED_BOOKS, emptySet())?.toMutableSet()
                    ?: mutableSetOf()
                persisted.remove(uriString)
                prefs.edit {
                    putStringSet(KEY_ADDED_BOOKS, persisted)
                    remove("$KEY_LAST_OPENED_PREFIX$uriString")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteBooks(books: List<Book>) {
        viewModelScope.launch {
            try {
                val urisToRemove = books.map { it.uri.toString() }.toSet()

                _uiState.update { current ->
                    when (current) {
                        is LibraryUiState.Success -> {
                            val updatedBooks =
                                current.books.filter { it.uri.toString() !in urisToRemove }
                            LibraryUiState.Success(updatedBooks)
                        }

                        else -> current
                    }
                }

                // Remove from persisted sets
                val persisted = prefs.getStringSet(KEY_ADDED_BOOKS, emptySet())?.toMutableSet()
                    ?: mutableSetOf()
                prefs.edit {
                    urisToRemove.forEach { uri ->
                        persisted.remove(uri)
                        remove("$KEY_LAST_OPENED_PREFIX$uri")
                    }
                    putStringSet(KEY_ADDED_BOOKS, persisted)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val projection = arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME)
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = it.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                // fallback below
            } finally {
                cursor?.close()
            }
        }
        if (name == null) {
            val path = uri.path ?: return null
            val cut = path.lastIndexOf('/')
            name = if (cut != -1 && cut + 1 < path.length) {
                path.substring(cut + 1)
            } else {
                path
            }
        }
        return extractFileNameFromDisplayName(name)
    }

    private fun extractFileNameFromDisplayName(displayName: String?): String {
        if (displayName == null) return "Unknown"
        val last = displayName.substringAfterLast('/')
        return last.substringAfterLast(':')
    }

    private fun generatePdfDetails(context: Context, uri: Uri): Pair<Bitmap?, Int> {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return Pair(null, 0)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            renderer.openPage(0).use { page ->
                val bitmap = createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                Pair(bitmap, pageCount)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, 0)
        }
    }

    fun updateLastOpened(bookUri: Uri) {
        viewModelScope.launch {
            try {
                // Persist to SharedPreferences
                val uriString = bookUri.toString()
                prefs.edit {
                    putLong("$KEY_LAST_OPENED_PREFIX$uriString", System.currentTimeMillis())
                }

                // Update UI state
                _uiState.update { current ->
                    when (current) {
                        is LibraryUiState.Success -> {
                            val updatedBooks = current.books.map { book ->
                                if (book.uri.toString() == uriString) {
                                    book.copy(lastOpened = System.currentTimeMillis())
                                } else {
                                    book
                                }
                            }
                            LibraryUiState.Success(updatedBooks)
                        }

                        else -> current
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}