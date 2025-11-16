// FocusModeManager.kt
package com.weproz.superreader

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

class FocusModeManager(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    var isFocusModeActive = false
        private set

    // Callbacks for different scenarios
    var onPermissionGranted: (() -> Unit)? = null
    var onReadyForEnable: (() -> Unit)? = null
    var onPermissionRequired: ((Activity) -> Unit)? = null

    // Public method to update the focus mode state
    fun updateFocusModeState(active: Boolean) {
        isFocusModeActive = active
    }

    fun isAppPinned(activity: Activity?): Boolean {
        return try {
            if (activity == null) return false
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val state = am.lockTaskModeState
            state == ActivityManager.LOCK_TASK_MODE_PINNED || state == ActivityManager.LOCK_TASK_MODE_LOCKED
        } catch (e: Exception) {
            Log.e("FocusMode", "Error checking pinned state: ${e.message}")
            false
        }
    }

    fun ensureWifiEnabled() {
        try {
            @Suppress("DEPRECATION")
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
                Log.d("FocusMode", "WiFi re-enabled after unpinning")
            }
        } catch (e: Exception) {
            Log.e("FocusMode", "Error ensuring WiFi enabled: ${e.message}")
        }
    }

    fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun checkAndPrepareFocusMode(activity: Activity): Boolean {
        return try {
            // Check if we have overlay permission
            if (!Settings.canDrawOverlays(context)) {
                // Permission not granted, notify that permission is required and pass the activity
                onPermissionRequired?.invoke(activity)
                false
            } else {
                // Permission already granted, notify that we're ready to enable
                onReadyForEnable?.invoke()
                true
            }
        } catch (e: SecurityException) {
            Log.e("FocusMode", "Permission denied: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e("FocusMode", "Error checking focus mode: ${e.message}")
            false
        }
    }

    fun enableFocusMode(activity: Activity): Boolean {
        return try {
            if (!Settings.canDrawOverlays(context)) {
                Log.e("FocusMode", "Cannot enable focus mode: Overlay permission not granted")
                return false
            }
            if (isAlreadyPinned(activity)) {
                isFocusModeActive = true
                return true
            }
            actuallyEnableFocusMode(activity)
            true
        } catch (e: SecurityException) {
            Log.e("FocusMode", "Permission denied: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e("FocusMode", "Error enabling focus mode: ${e.message}")
            false
        }
    }

    private fun actuallyEnableFocusMode(activity: Activity?) {
        // Disable WiFi
        @Suppress("DEPRECATION")
        wifiManager.isWifiEnabled = false

        // Start screen pinning
        startKioskMode(activity)

        isFocusModeActive = true
        Log.d("FocusMode", "Focus mode enabled with screen pinning")
    }

    fun disableFocusMode(activity: Activity? = null) {
        try {
            // Stop screen pinning
            stopKioskMode(activity)

            // Re-enable WiFi
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = true

            isFocusModeActive = false
            Log.d("FocusMode", "Focus mode disabled")
        } catch (e: Exception) {
            Log.e("FocusMode", "Error disabling focus mode: ${e.message}")
        }
    }

    private fun startKioskMode(activity: Activity?) {
        try {
            activity?.startLockTask()
            Log.d("FocusMode", "Screen pinning started")
        } catch (e: SecurityException) {
            Log.e("FocusMode", "Cannot start screen pinning: ${e.message}")
        } catch (e: Exception) {
            Log.e("FocusMode", "Error starting screen pinning: ${e.message}")
        }
    }

    private fun stopKioskMode(activity: Activity? = null) {
        try {
            activity?.stopLockTask()
            Log.d("FocusMode", "Screen pinning stopped")
        } catch (e: Exception) {
            Log.e("FocusMode", "Cannot stop lock task: ${e.message}")
        }
    }

    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = "package:${context.packageName}".toUri()
        }
        activity.startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    // Call this method when returning from permission request
    fun onPermissionResult(activity: Activity?) {
        if (Settings.canDrawOverlays(context)) {
            try {
                actuallyEnableFocusMode(activity) // pins immediately
                onPermissionGranted?.invoke() // optional callback
            } catch (e: Exception) {
                Log.e("FocusMode", "Failed to enable after permission: ${e.message}")
            }
        }
    }

    private fun isAlreadyPinned(activity: Activity): Boolean {
        val am = activity.getSystemService(ActivityManager::class.java)
        return am?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED ||
                am?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
    }

    fun autoEnableOnStartIfNeeded(activity: Activity) {
        if (isAlreadyPinned(activity)) {
            isFocusModeActive = true
            return
        }
        try {
            activity.startLockTask() // first time may show the system prompt
            isFocusModeActive = true
        } catch (_: SecurityException) {
            // Needs user confirmation; nothing you can do programmatically on consumer devices
        }
    }

    companion object {
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    }
}