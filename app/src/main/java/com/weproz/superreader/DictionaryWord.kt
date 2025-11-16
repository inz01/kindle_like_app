// DictionaryWord.kt

package com.weproz.superreader

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_words")
data class DictionaryWord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val word: String,
    val meaning: String,
    val example: String = "",
    val pronunciation: String = "",
    val partOfSpeech: String = "Vocabulary",
    val isCustom: Boolean = false,
    val dateAdded: String = "",
    val wordType: String = "general"
)