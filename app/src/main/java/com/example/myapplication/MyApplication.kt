package com.example.myapplication

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.Log
import com.example.myapplication.core.WarningCheckWorker
import com.example.myapplication.context.UsagePatternContextProvider
import com.example.myapplication.PrefsHelper

class MyApplication : Application() {
    private val TAG = "MyApplication"
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize warning system if enabled (independent of screenshot service)
        try {
            val prefs = PrefsHelper(this)
            if (prefs.isWarningSystemEnabled()) {
                Log.d(TAG, "Warning system enabled - scheduling periodic checks")
                WarningCheckWorker.schedulePeriodicCheck(this)
            } else {
                Log.d(TAG, "Warning system disabled - not scheduling")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize warning system", e)
        }
        
        // Clear stale usage pattern cache on app start to prevent immediate warnings
        try {
            UsagePatternContextProvider.clearCache(this)
            Log.d(TAG, "Cleared usage pattern cache on app start")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear usage pattern cache", e)
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            // Track number of started activities so we can detect when the app goes to background
            private var startedCount = 0
            // Track number of resumed activities so we can detect when the user is actually viewing an activity
            private var resumedCount = 0
            // Handler + runnable to debounce clearing of currentActivityClassName (avoid flicker on rotations)
            private val handler = Handler(Looper.getMainLooper())
            private var clearRunnable: Runnable? = null

            override fun onActivityResumed(activity: Activity) {
                // An activity moved to RESUMED (in the foreground)
                resumedCount++
                // Cancel any pending clear that was scheduled when resumedCount reached 0
                try { clearRunnable?.let { handler.removeCallbacks(it) } } catch (_: Exception) {}
                currentActivityClassName = activity::class.java.simpleName
            }
            override fun onActivityPaused(activity: Activity) {
                // Activity moved out of RESUMED state
                resumedCount = (resumedCount - 1).coerceAtLeast(0)
                // If no activities are resumed, the user is not actively viewing any activity -> clear current (debounced)
                if (resumedCount == 0) {
                    // schedule a short delay to avoid clearing during brief transitions (rotation)
                    try {
                        clearRunnable = Runnable {
                            // Double-check count before clearing
                            if (resumedCount == 0) currentActivityClassName = null
                        }
                        handler.postDelayed(clearRunnable!!, 300)
                    } catch (_: Exception) {
                        currentActivityClassName = null
                    }
                }
            }

            override fun onActivityStarted(activity: Activity) {
                // An activity became visible (increment started count)
                startedCount++
            }

            override fun onActivityStopped(activity: Activity) {
                // An activity is no longer visible (decrement started count)
                startedCount = (startedCount - 1).coerceAtLeast(0)
                // If no activities are started, the app is in background / user left the app -> clear current activity
                if (startedCount == 0) {
                    // Cancel any pending clear runnable and clear immediately
                    try { clearRunnable?.let { handler.removeCallbacks(it) } } catch (_: Exception) {}
                    currentActivityClassName = null
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                // Only clear the current activity when the activity is actually finishing, not on configuration changes
                // Also ensure we clear when the app is backgrounded (handled in onActivityStopped)
                try {
                    if (activity.isFinishing) {
                        if (currentActivityClassName == activity::class.java.simpleName) {
                            currentActivityClassName = null
                        }
                    }
                } catch (t: Throwable) {
                    // Be defensive: if isFinishing isn't available for some reason, clear conservatively
                    if (currentActivityClassName == activity::class.java.simpleName) {
                        currentActivityClassName = null
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        })
    }
    companion object {
        @Volatile
        var currentActivityClassName: String? = null
            private set
        fun isExcludedActivity(): Boolean {
            val excluded = setOf(
                "MainActivity",
                "ChatActivity",
                "ScreenshotApp",
                "ChatScreen"
            )
            return currentActivityClassName?.let { name ->
                excluded.any { it.equals(name, ignoreCase = true) }
            } ?: false
        }
    }
}
