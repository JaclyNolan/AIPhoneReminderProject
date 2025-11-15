package com.example.myapplication.testing.mockcontext

import android.content.Context
import android.util.Log
import com.example.myapplication.context.UsagePatternDetector
import com.example.myapplication.testing.MockAppUsageStats
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mock AppUsageContextProvider for testing
 * 
 * Returns predefined usage scenarios instead of real Android UsageStats data
 */
object MockAppUsageContextProvider {
    private const val TAG = "MockAppUsageContextProvider"
    
    // Current scenario data
    private var currentScenario: String? = null
    private var customData: List<UsagePatternDetector.AppUsageData>? = null
    
    /**
     * Set scenario type for mock data generation
     */
    fun setScenario(scenarioType: String) {
        currentScenario = scenarioType
        customData = null
        Log.d(TAG, "Set scenario: $scenarioType")
    }
    
    /**
     * Set custom usage data directly
     */
    fun setCustomData(data: List<UsagePatternDetector.AppUsageData>) {
        customData = data
        currentScenario = null
        Log.d(TAG, "Set custom data: ${data.size} entries")
    }
    
    /**
     * Get mock usage stats
     */
    fun getUsageStats(context: Context, intervalMinutes: Int = 60): List<UsagePatternDetector.AppUsageData> {
        return if (customData != null) {
            // Use custom data if provided
            customData!!.filter { 
                val now = System.currentTimeMillis()
                it.lastTimeUsed >= (now - intervalMinutes * 60 * 1000)
            }
        } else if (currentScenario != null) {
            // Use scenario-based data
            MockAppUsageStats.generateRealisticScenario(currentScenario!!)
        } else {
            // Default: empty or generate a basic scenario
            emptyList()
        }
    }
    
    /**
     * Get current mock data for display
     */
    fun getCurrentMockData(): List<UsagePatternDetector.AppUsageData> {
        return customData ?: (currentScenario?.let { 
            MockAppUsageStats.generateRealisticScenario(it) 
        } ?: emptyList())
    }
    
    /**
     * Reset to default state
     */
    fun reset() {
        currentScenario = null
        customData = null
    }
}

