package com.example.myapplication.context

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.myapplication.LLMClient
import com.example.myapplication.ResponseLogger
import com.example.myapplication.context.UsagePatternDetector
import com.example.myapplication.context.UserBadBehaviorContextProvider
import com.example.myapplication.context.MemoryContextProvider
import com.example.myapplication.memory.EnhancedMemoryManager
import com.example.myapplication.testing.LLMClientFactory
import com.example.myapplication.testing.ContextProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * UsagePatternContextProvider: Enhanced caching context provider with LLM-based pattern analysis
 * 
 * Generates objective natural language descriptions of usage patterns by analyzing:
 * - Recent App Timeline (from ScreenshotAnalyzer sceneTimeline)
 * - AppUsageTimeline (from AppUsageContextProvider)
 * - UserBadBehaviors (from UserBadBehaviorContextProvider)
 * 
 * Caching Strategy:
 * - Stores last generated analysis with timestamp
 * - Regenerates only when cache is stale (>5 minutes old)
 * - Returns cached analysis immediately if fresh
 * 
 * This is NOT a decision-maker - it only describes what's happening objectively.
 * NO access to memories - purely objective pattern analysis.
 */
object UsagePatternContextProvider {
    private const val TAG = "UsagePatternContextProvider"
    private const val CACHE_PREFS_NAME = "usage_pattern_cache"
    private const val KEY_CACHED_ANALYSIS = "cached_analysis"
    private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    
    // ISO timestamp format
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    
    private const val SYSTEM_PROMPT = """
You are the Usage Pattern Analyzer. Your job is to DESCRIBE what's happening with app usage patterns objectively.

CRITICAL RULES:
1. Return ONLY JSON format: {"response": "your natural language description here"}
2. Describe WHAT is happening, not what SHOULD happen
3. Include specific numbers: app names, durations, session lengths
4. Mention user-defined concerns if relevant
5. NO urgency calculations, NO recommendations, NO judgments

FOCUS ON:
- Current app and session duration
- Recent activity patterns (scrolling speed, app switches, engagement level)
- User-defined behaviors that match current usage
- Objective facts only

EXAMPLE OUTPUT:
{"response": "Kris has been on YouTube for 47 minutes (since 2:30pm). Recent activity: rapid scrolling through Shorts, 4 app switches total. User previously noted: 'YouTube Shorts makes me lose sleep.'"}
"""
    
    /**
     * Get usage context (cached if fresh, regenerates if stale)
     * 
     * @param context Application context
     * @return Natural language description of current usage patterns
     */
    suspend fun getUsageContext(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val cache = getCachedAnalysis(context)
            
            if (cache != null && !isCacheStale(cache.timestamp)) {
                Log.d(TAG, "Returning cached analysis (age: ${getCacheAgeMs(cache.timestamp)}ms)")
                return@withContext cache.analysis
            }
            
            // Cache is stale or missing - regenerate
            Log.d(TAG, "Cache stale or missing, generating new analysis")
            val newAnalysis = generateAnalysis(context)
            
            // Cache the new analysis
            saveCachedAnalysis(context, newAnalysis)
            
            return@withContext newAnalysis
        } catch (e: Exception) {
            Log.e(TAG, "Error getting usage context", e)
            return@withContext "Unable to analyze usage patterns at this time."
        }
    }
    
    /**
     * Force refresh - generates new analysis regardless of cache
     * 
     * @param context Application context
     * @return Fresh natural language description
     */
    suspend fun forceRefresh(context: Context): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Force refreshing usage context")
            val newAnalysis = generateAnalysis(context)
            saveCachedAnalysis(context, newAnalysis)
            return@withContext newAnalysis
        } catch (e: Exception) {
            Log.e(TAG, "Error force refreshing usage context", e)
            return@withContext "Unable to analyze usage patterns at this time."
        }
    }
    
    /**
     * Generate new LLM-based analysis from context sources
     */
    private suspend fun generateAnalysis(context: Context): String {
        EnhancedMemoryManager.initialize(context)
        
        // Gather context from providers (using factory to respect mock/real toggles)
        val recentAppTimeline = getRecentAppTimeline(context)
        val appUsageTimeline = ContextProviderFactory.getAppUsageStats(context, intervalMinutes = 120)
        val userBadBehaviors = ContextProviderFactory.getUserBadBehaviors(context)
        
        // Build context string for LLM
        val contextPrompt = buildString {
            appendLine("=== RECENT APP TIMELINE ===")
            appendLine(recentAppTimeline.take(10).joinToString("\n") { entry ->
                "[${entry.timestamp}] ${entry.sceneLabel}: ${entry.shortText}"
            })
            appendLine()
            
            appendLine("=== APP USAGE STATS (LAST 2 HOURS) ===")
            appUsageTimeline.take(10).forEach { usage ->
                val minutes = (usage.totalTimeInForeground / 60_000).toInt()
                appendLine("${usage.displayName}: ${minutes} minutes")
            }
            appendLine()
            
            if (userBadBehaviors.isNotEmpty()) {
                appendLine("=== USER-DEFINED CONCERNS ===")
                userBadBehaviors.forEach { behavior ->
                    appendLine("- ${behavior.description}")
                }
            }
        }
        
        // Call LLM
        val messages = listOf(
            LLMClient.Message("system", SYSTEM_PROMPT),
            LLMClient.Message("user", "Analyze this usage pattern:\n\n$contextPrompt")
        )
        
        // Format request for logging
        val requestJson = org.json.JSONObject().apply {
            put("system", SYSTEM_PROMPT)
            put("user", "Analyze this usage pattern:\n\n$contextPrompt")
        }.toString(2)
        
        val client = LLMClientFactory.getClient()
        val response = client.callOpenAI(context, messages)
        
        if (response == null || response.content.isBlank()) {
            Log.w(TAG, "LLM returned empty response, using fallback")
            // Log the failed call
            ResponseLogger.logResponse(
                context,
                requestJson,
                "EMPTY_RESPONSE",
                ResponseLogger.LogType.USAGE_PATTERN_CONTEXT_PROVIDER,
                response?.promptTokens,
                response?.completionTokens,
                response?.totalTokens
            )
            return generateFallbackAnalysis(recentAppTimeline, appUsageTimeline)
        }
        
        // Log successful response
        ResponseLogger.logResponse(
            context,
            requestJson,
            response.content,
            ResponseLogger.LogType.USAGE_PATTERN_CONTEXT_PROVIDER,
            response.promptTokens,
            response.completionTokens,
            response.totalTokens
        )
        
        // Parse JSON response
        return parseJsonResponse(response.content.trim()) ?: generateFallbackAnalysis(recentAppTimeline, appUsageTimeline)
    }
    
    /**
     * Get recent app timeline from ScreenshotAnalyzer/EnhancedMemoryManager (using factory)
     */
    private fun getRecentAppTimeline(context: Context): List<EnhancedMemoryManager.SceneTimelineEntry> {
        return ContextProviderFactory.getSceneTimeline(context, limitMinutes = 60)
            .sortedByDescending { entry ->
                try {
                    isoDateFormat.parse(entry.timestamp)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
            .take(10)
    }
    
    /**
     * Force refresh for testing (bypasses cache)
     */
    suspend fun forceRefreshForTesting(context: Context): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Force refreshing for testing (bypassing cache)")
            val newAnalysis = generateAnalysis(context)
            return@withContext newAnalysis
        } catch (e: Exception) {
            Log.e(TAG, "Error force refreshing for testing", e)
            return@withContext "Unable to analyze usage patterns at this time."
        }
    }
    
    /**
     * Parse JSON response from LLM
     * Expected format: {"response": "text"}
     * Handles escaped quotes, multiline text, and partial JSON
     */
    private fun parseJsonResponse(content: String): String? {
        return try {
            // First, try to find and parse complete JSON object
            val jsonStart = content.indexOf('{')
            val jsonEnd = content.lastIndexOf('}') + 1
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = content.substring(jsonStart, jsonEnd)
                try {
                    val json = org.json.JSONObject(jsonStr)
                    val responseText = json.getString("response")
                    
                    if (responseText.isNotBlank()) {
                        Log.d(TAG, "Successfully parsed JSON response")
                        return responseText
                    }
                } catch (e: org.json.JSONException) {
                    // JSON parsing failed, try alternative extraction
                    Log.d(TAG, "JSON parsing failed, trying alternative extraction: ${e.message}")
                }
            }
            
            // Alternative: try to extract response field using regex (handles escaped quotes)
            // This regex handles: "response": "text with \"quotes\" inside"
            val responseMatch = Regex("\"response\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(content)
            if (responseMatch != null) {
                var extracted = responseMatch.groupValues[1]
                // Unescape common escape sequences
                extracted = extracted.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\r", "\r")
                Log.d(TAG, "Extracted response from partial JSON using regex")
                return extracted
            }
            
            Log.w(TAG, "Could not parse JSON response, content: ${content.take(100)}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing JSON response: ${e.message}, content: ${content.take(100)}")
            null
        }
    }
    
    /**
     * Fallback analysis when LLM fails
     */
    private fun generateFallbackAnalysis(
        recentTimeline: List<EnhancedMemoryManager.SceneTimelineEntry>,
        usageStats: List<UsagePatternDetector.AppUsageData>
    ): String {
        val fallbackText = if (recentTimeline.isEmpty()) {
            "No recent activity detected."
        } else {
            val mostRecent = recentTimeline.firstOrNull()
            val appName = mostRecent?.sceneLabel ?: "Unknown"
            
            // Estimate session duration
            val relevantEntries = recentTimeline.filter { it.sceneLabel == appName }
            val durationMinutes = if (relevantEntries.size > 1) {
                val firstTime = try {
                    isoDateFormat.parse(relevantEntries.last().timestamp)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                val lastTime = try {
                    isoDateFormat.parse(relevantEntries.first().timestamp)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                ((lastTime - firstTime) / 60_000).toInt() + (relevantEntries.size * 10) / 60 // Estimate with intervals
            } else {
                0
            }
            
            "$appName has been active for approximately $durationMinutes minutes based on recent activity."
        }
        
        // Return just the text (callers expect plain text, not JSON)
        return fallbackText
    }
    
    /**
     * Cache management
     */
    private data class CachedAnalysis(
        val analysis: String,
        val timestamp: Long
    )
    
    private fun getCachedAnalysis(context: Context): CachedAnalysis? {
        val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        val analysis = prefs.getString(KEY_CACHED_ANALYSIS, null)
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0)
        
        if (analysis == null || timestamp == 0L) {
            return null
        }
        
        return CachedAnalysis(analysis, timestamp)
    }
    
    private fun saveCachedAnalysis(context: Context, analysis: String) {
        val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CACHED_ANALYSIS, analysis)
            .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Saved cached analysis")
    }
    
    private fun isCacheStale(timestamp: Long): Boolean {
        val age = System.currentTimeMillis() - timestamp
        return age > CACHE_TTL_MS
    }
    
    private fun getCacheAgeMs(timestamp: Long): Long {
        return System.currentTimeMillis() - timestamp
    }
    
    /**
     * Clear cached analysis (useful on app start to prevent stale warnings)
     * 
     * @param context Application context
     */
    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_CACHED_ANALYSIS)
            .remove(KEY_CACHE_TIMESTAMP)
            .apply()
        Log.d(TAG, "Cleared usage pattern cache")
    }
}

