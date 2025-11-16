// DictionaryDao.kt
package com.weproz.superreader

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_words ORDER BY word ASC")
    fun getAllWords(): Flow<List<DictionaryWord>>

    @Query("SELECT * FROM dictionary_words WHERE word LIKE '%' || :query || '%'")
    fun searchWords(query: String): Flow<List<DictionaryWord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: DictionaryWord)

    @Update
    suspend fun updateWord(word: DictionaryWord)

    @Query("DELETE FROM dictionary_words WHERE id = :id")
    suspend fun deleteWord(id: Int)

    @Query("SELECT * FROM dictionary_words WHERE id = :id")
    suspend fun getWordById(id: Int): DictionaryWord?

    @Query("SELECT COUNT(*) FROM dictionary_words")
    suspend fun getWordCount(): Int
}