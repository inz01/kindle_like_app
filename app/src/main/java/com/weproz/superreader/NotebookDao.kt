// NotebookDao.kt

package com.weproz.superreader

import androidx.room.Dao // Database access object
import androidx.room.Delete // Delete operation
import androidx.room.Insert // Insert operation
import androidx.room.OnConflictStrategy // Conflict resolution
import androidx.room.Query // Custom query
import androidx.room.Update // Update operation
import kotlinx.coroutines.flow.Flow // Reactive data
@Dao // Database access interface
interface NotebookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) // Insert or replace
    suspend fun insert(notebook: Notebook) // Add notebook

    @Update // Update operation
    suspend fun update(notebook: Notebook) // Modify notebook

    @Delete // Delete operation
    suspend fun delete(notebook: Notebook) // Remove notebook

    @Query("SELECT * FROM notebooks ORDER BY lastModified DESC") // Get all notebooks
    fun getAllNotebooks(): Flow<List<Notebook>> // Return notebook list


    // ✅ NEW: A function to get a single notebook
    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun getNotebookById(id: Int): Flow<Notebook?>

}