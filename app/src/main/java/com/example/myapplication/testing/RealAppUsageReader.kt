package com.example.myapplication.testing

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.myapplication.context.UsagePatternDetector
import java.util.Calendar

/**
 * Reads real app usage data from Android's UsageStatsManager API
 * 
 * Requires PACKAGE_USAGE_STATS permission
 */
object RealAppUsageReader {
    private const val TAG = "RealAppUsageReader"
    
    /**
     * Query real app usage statistics from Android API
     * 
     * @param context Application context
     * @param intervalMinutes How far back to query (default: last 60 minutes)
     * @return List of AppUsageData with real usage statistics
     */
    fun queryUsageStats(context: Context, intervalMinutes: Int = 60): List<UsagePatternDetector.AppUsageData> {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager == null) {
                Log.e(TAG, "UsageStatsManager not available")
                return emptyList()
            }
            
            // Query usage stats for the specified interval
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (intervalMinutes * 60 * 1000)
            
            Log.d(TAG, "Querying usage stats from ${Calendar.getInstance().apply { timeInMillis = startTime }.time} to ${Calendar.getInstance().apply { timeInMillis = endTime }.time}")
            
            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            )
            
            if (usageStatsList.isNullOrEmpty()) {
                Log.w(TAG, "No usage stats found. Check if PACKAGE_USAGE_STATS permission is granted.")
                return emptyList()
            }
            
            Log.d(TAG, "Found ${usageStatsList.size} usage stats entries")
            
            // Convert to AppUsageData
            val appUsageDataList = usageStatsList
                .filter { it.totalTimeInForeground > 0 } // Only apps that were actually used
                .map { usageStats ->
                    val displayName = getAppDisplayName(context, usageStats.packageName)
                    
                    UsagePatternDetector.AppUsageData(
                        packageName = usageStats.packageName,
                        displayName = displayName,
                        lastTimeUsed = usageStats.lastTimeUsed,
                        totalTimeInForeground = usageStats.totalTimeInForeground,
                        lastTimeForegroundServiceUsed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            usageStats.lastTimeForegroundServiceUsed
                        } else {
                            null
                        },
                        queryInterval = Pair(startTime, endTime)
                    )
                }
                .sortedByDescending { it.lastTimeUsed } // Most recent first
            
            Log.d(TAG, "Converted to ${appUsageDataList.size} AppUsageData entries")
            appUsageDataList.take(10).forEach {
                Log.d(TAG, "  ${it.displayName}: ${it.totalTimeInForeground / 1000}s, last used: ${Calendar.getInstance().apply { timeInMillis = it.lastTimeUsed }.time}")
            }
            
            return appUsageDataList
        } catch (e: Exception) {
            Log.e(TAG, "Error querying usage stats", e)
            return emptyList()
        }
    }
    
    /**
     * Get app display name from package name
     */
    private fun getAppDisplayName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
    }
    
    /**
     * Check if usage stats permission is granted
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (1000 * 60) // Last minute
            
            val usageStatsList = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            // If we get a non-null list, permission is granted
            usageStatsList != null
        } catch (e: Exception) {
            false
        }
    }
}

