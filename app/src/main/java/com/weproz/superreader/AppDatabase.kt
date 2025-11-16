// AppDatabase.kt
package com.weproz.superreader

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Notebook::class, DictionaryWord::class],
    version = 5, // Increment version
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "super_reader_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DictionaryDatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Database callback to pre-populate dictionary data
class DictionaryDatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // We handle initialization in MainActivity now
        // No fallback words - only WordNet data
    }
}