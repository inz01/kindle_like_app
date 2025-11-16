package com.weproz.superreader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.weproz.superreader.ui.theme.AppTheme
import com.weproz.superreader.ui.theme.SuperReaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val PREFS_NAME = "SuperReaderPrefs"
private const val KEY_INSTALL_TIME = "install_time"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clear ALL data on fresh install
        clearAllDataOnFreshInstall()

        enableEdgeToEdge()
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            var appTheme by remember { mutableStateOf(AppTheme.LIGHT) }
            val context = LocalContext.current
            val themeManager = remember { ThemeManager(context) }

            // Load saved theme
            LaunchedEffect(Unit) {
                appTheme = themeManager.themeFlow.first()
            }

            if (showSplash) {
                // FORCE the app theme and DISABLE device theme influence
                SuperReaderTheme(appTheme = appTheme) {
                    // Use DisposableEffect to force status bar colors
                    val view = LocalView.current
                    if (!view.isInEditMode) {
                        DisposableEffect(Unit) {
                            val window = (view.context as Activity).window

                            WindowCompat.getInsetsController(
                                window,
                                view
                            ).isAppearanceLightStatusBars =
                                appTheme != AppTheme.DARK

                            onDispose { }
                        }
                    }

                    EnhancedSplashScreenWithDelay(
                        appTheme = appTheme,
                        onLoadingComplete = { showSplash = false }
                    )
                }
            } else {
                // Show main app
                SuperReaderTheme(appTheme = appTheme) {
                    AppContent(
                        currentTheme = appTheme,
                        onThemeChanged = { newTheme ->
                            appTheme = newTheme
                        }
                    )
                }
            }
        }
    }

    private fun clearAllDataOnFreshInstall() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentInstallTime = getAppInstallTime(this)
        val storedInstallTime = prefs.getLong(KEY_INSTALL_TIME, 0L)

        if (storedInstallTime == 0L) {
            // First run - store current install time
            prefs.edit().apply {
                putLong(KEY_INSTALL_TIME, currentInstallTime)
                apply()
            }
        } else if (storedInstallTime != currentInstallTime) {
            // Fresh install detected - CLEAR EVERYTHING
            clearAllAppData(this)

            // Store new install time
            prefs.edit().apply {
                putLong(KEY_INSTALL_TIME, currentInstallTime)
                apply()
            }
        }
    }

    private fun clearAllAppData(context: Context) {
        try {
            // Clear SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit { clear() }

            // Clear other SharedPreferences files if any
            context.getSharedPreferences("SuperReaderPrefs", MODE_PRIVATE).edit { clear() }
            context.getSharedPreferences("theme_prefs", MODE_PRIVATE).edit { clear() }

            // Clear database
            clearDatabase(context)

            // Clear cache
            clearCache(context)

            // Revoke all URI permissions
            revokeAllUriPermissions(context)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearDatabase(context: Context) {
        try {
            // Close database if open
            val database = AppDatabase.getDatabase(context)
            database.close()

            // Delete database files
            context.deleteDatabase("super_reader_database")

            // Also try to delete any journal files
            val journalFile = context.getDatabasePath("super_reader_database-journal")
            if (journalFile.exists()) {
                journalFile.delete()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearCache(context: Context) {
        try {
            // Clear app cache directory
            context.cacheDir.deleteRecursively()

            // Clear external cache if exists
            context.externalCacheDir?.deleteRecursively()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun revokeAllUriPermissions(context: Context) {
        try {
            val persistedUriPermissions = context.contentResolver.persistedUriPermissions
            persistedUriPermissions.forEach { permission ->
                context.contentResolver.releasePersistableUriPermission(
                    permission.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAppInstallTime(context: Context): Long {
        return try {
            val pm = context.packageManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                ).firstInstallTime
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0).firstInstallTime
            }
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}

@Composable
fun EnhancedSplashScreenWithDelay(
    appTheme: AppTheme,
    onLoadingComplete: () -> Unit,
) {
    EnhancedSplashScreen(appTheme = appTheme)
    LaunchedEffect(Unit) {
        delay(1500)
        onLoadingComplete()
    }
}

@Composable
fun AppContent(
    currentTheme: AppTheme = AppTheme.LIGHT,
    onThemeChanged: (AppTheme) -> Unit = {},
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        DatabaseInitializer.initializeIfNeeded(
            context = context,
            onProgress = { _, _, _ -> } // Ignore progress updates
        )
    }
    MainScreen(
        currentTheme = currentTheme,
        onThemeChanged = onThemeChanged
    )
}