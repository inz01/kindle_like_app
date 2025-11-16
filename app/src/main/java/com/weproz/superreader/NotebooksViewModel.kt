// NotebooksViewModel.kt

package com.weproz.superreader

import android.app.Application // Application context
import androidx.lifecycle.AndroidViewModel // App ViewModel
import androidx.lifecycle.viewModelScope // ViewModel coroutine scope
import kotlinx.coroutines.flow.SharingStarted // Flow sharing strategy
import kotlinx.coroutines.flow.StateFlow // State flow
import kotlinx.coroutines.flow.stateIn // Convert to StateFlow
import kotlinx.coroutines.launch // Launch coroutine

class NotebooksViewModel(application: Application) : AndroidViewModel(application) { // Notebooks ViewModel

    private val notebookDao = AppDatabase.getDatabase(application).notebookDao() // Database access

    val notebooks: StateFlow<List<Notebook>> = notebookDao.getAllNotebooks() // Get all notebooks
        .stateIn( // Convert to StateFlow
            scope = viewModelScope, // ViewModel scope
            started = SharingStarted.WhileSubscribed(5000L), // Share while subscribed
            initialValue = emptyList() // Initial empty list
        )

    fun addNotebook(title: String) { // Add new notebook
        viewModelScope.launch { // Launch coroutine
            notebookDao.insert(Notebook(title = title)) // Insert notebook
        }
    }

    suspend fun deleteNotebook(notebook: Notebook) {
        notebookDao.delete(notebook)
    }

    suspend fun updateNotebook(notebook: Notebook) {
        notebookDao.update(notebook)
    }
}