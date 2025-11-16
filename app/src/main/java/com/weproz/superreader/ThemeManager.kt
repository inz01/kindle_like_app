// ThemeManager.kt

package com.weproz.superreader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weproz.superreader.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemeManager(private val context: Context) {
    companion object {
        private val THEME_KEY = stringPreferencesKey("app_theme")
    }

    val themeFlow: Flow<AppTheme> = context.dataStore.data
        .map { preferences ->
            when (preferences[THEME_KEY]) {
                "DARK" -> AppTheme.DARK
                "SEPIA" -> AppTheme.SEPIA
                else -> AppTheme.LIGHT
            }
        }

    suspend fun saveTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }
}