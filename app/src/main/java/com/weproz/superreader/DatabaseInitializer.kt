// DatabaseInitializer.kt
package com.weproz.superreader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object DatabaseInitializer {
    private var isInitialized = false

    suspend fun initializeIfNeeded(
        context: Context,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ) {
        if (isInitialized) return

        val dictionaryDao = AppDatabase.getDatabase(context).dictionaryDao()

        // Check if we already have words
        val existingCount = withContext(Dispatchers.IO) {
            dictionaryDao.getWordCount()
        }

        if (existingCount > 0) {
            isInitialized = true
            return
        }

        onProgress(0, 0, "Starting database initialization...")

        // Load from WordNet only
        try {
            onProgress(0, 0, "Loading WordNet database...")
            val wordNetParser = WordNetParser(context)
            val wordNetWords = withContext(Dispatchers.IO) {
                wordNetParser.parseWordNetData()
            }

            if (wordNetWords.isNotEmpty()) {
                onProgress(0, wordNetWords.size, "Inserting ${wordNetWords.size} words...")

                // Insert in small batches with progress updates
                val totalWords = wordNetWords.size
                wordNetWords.chunked(50).forEachIndexed { batchIndex, chunk ->
                    withContext(Dispatchers.IO) {
                        chunk.forEach { word ->
                            dictionaryDao.insertWord(word)
                        }
                    }

                    val currentProgress = minOf((batchIndex + 1) * 50, totalWords)
                    onProgress(currentProgress, totalWords, "Loading words...")

                    // Small delay to prevent ANR and show smooth progress
                    delay(5)
                }

                onProgress(totalWords, totalWords, "Database ready!")
                println("✅ Loaded ${wordNetWords.size} words from WordNet")
            } else {
                // No fallback - just empty database
                onProgress(0, 0, "No words found in WordNet files")
                println("❌ No words found in WordNet files")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress(0, 0, "Error loading WordNet database")
            println("❌ Error loading WordNet: ${e.message}")
        }

        isInitialized = true
        delay(500) // Small delay before completing
    }
}