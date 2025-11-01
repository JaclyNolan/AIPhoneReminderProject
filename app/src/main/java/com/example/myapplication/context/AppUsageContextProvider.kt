package com.example.myapplication.context

import android.content.Context
import android.util.Log
import com.example.myapplication.agents.PatternAgent
import com.example.myapplication.testing.RealAppUsageReader

/**
 * AppUsageContextProvider: Provides app usage statistics context
 * 
 * Single responsibility: Query Android UsageStats API for app usage data
 * Used by PatternAgent for violation detection
 */
object AppUsageContextProvider {
    private const val TAG = "AppUsageContextProvider"
    
    /**
     * Get app usage statistics for specified time interval
     * 
     * @param context Application context
     * @param intervalMinutes How far back to query (default: 60 minutes)
     * @return List of app usage data sorted by most recent first
     */
    fun getUsageStats(context: Context, intervalMinutes: Int = 60): List<PatternAgent.AppUsageData> {
        Log.d(TAG, "Querying usage stats for last $intervalMinutes minutes")
        return RealAppUsageReader.queryUsageStats(context, intervalMinutes)
    }
    
    /**
     * Get current foreground app (most recently used)
     * 
     * @param context Application context
     * @return Most recent app usage data, or null if none found
     */
    fun getCurrentApp(context: Context): PatternAgent.AppUsageData? {
        val stats = getUsageStats(context, intervalMinutes = 1)
        return stats.firstOrNull()
    }
    
    /**
     * Check if usage stats permission is granted
     * 
     * @param context Application context
     * @return True if permission granted, false otherwise
     */
    fun hasPermission(context: Context): Boolean {
        return RealAppUsageReader.hasUsageStatsPermission(context)
    }
    
    /**
     * Get session duration for specific app
     * 
     * @param context Application context
     * @param packageName Package name to check
     * @param intervalMinutes Time window to check
     * @return Total foreground time in milliseconds
     */
    fun getAppSessionDuration(context: Context, packageName: String, intervalMinutes: Int = 60): Long {
        val stats = getUsageStats(context, intervalMinutes)
        return stats
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }
}

