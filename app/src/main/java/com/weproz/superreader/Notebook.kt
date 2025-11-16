// Notebook.kt

package com.weproz.superreader

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "notebooks")
@TypeConverters(Converters::class) // Tell Room to use our new converter
data class Notebook(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val lastModified: Long = System.currentTimeMillis(),
    // ✅ NEW: A field to store the drawing data as text
    val content: List<PathData>? = null,
    // ✅ NEW: Add grid type to save canvas background
    val gridType: GridType = GridType.NONE,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Notebook

        if (id != other.id) return false
        if (title != other.title) return false
        if (content != other.content) return false
        if (lastModified != other.lastModified) return false
        if (gridType != other.gridType) return false // IMPORTANT: Include grid type

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + title.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        result = 31 * result + lastModified.hashCode()
        result = 31 * result + gridType.hashCode() // IMPORTANT: Include grid type
        return result
    }
}