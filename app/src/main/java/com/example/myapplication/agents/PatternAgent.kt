package com.example.myapplication.agents

import android.content.Context
import android.util.Log
import com.example.myapplication.context.AppUsageContextProvider
import com.example.myapplication.context.MemoryContextProvider
import com.example.myapplication.memory.EnhancedMemoryManager
import java.text.SimpleDateFormat
import java.util.*

// Import ChatManager from same package
import com.example.myapplication.agents.ChatManager

/**
 * PatternAgent: Rule-based pattern detection for screen time violations
 * 
 * Detects extended app sessions, excessive app switching, and other behavioral patterns
 * that might indicate problematic usage. Uses rule-based logic (not LLM) for fast,
 * deterministic detection.
 * 
 * Now uses modular context providers for data access:
 * - AppUsageContextProvider for app usage stats
 * - MemoryContextProvider for scene timeline and chat history
 * 
 * Currently implements:
 * - Extended session detection (30+ minutes in same app)
 * 
 * Future rules can be added:
 * - Excessive app switching
 * - User-defined time limits per app
 * - Time-of-day restrictions
 */
object PatternAgent {
    private const val TAG = "PatternAgent"
    
    // ISO timestamp format used by EnhancedMemoryManager
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    
    data class PatternViolation(
        val appName: String,              // Display name (e.g., "YouTube")
        val appDisplayName: String,       // Same as appName for now (could be package name later)
        val urgency: Int,                // 0-10 scale
        val context: String               // Natural language description for LLM
    )
    
    /**
     * Raw context data structure holding all sources for pattern detection
     * This allows PatternAgent to receive data from multiple sources:
     * - AppUsageStats (Android API)
     * - Chat history
     * - User-defined bad behaviors
     * - Recent screen summaries
     */
    data class PatternAgentContext(
        val appUsageStats: List<AppUsageData>,
        val chatHistory: List<ChatManager.ChatMessage>,
        val userDefinedBehaviors: List<UserBadBehavior>,
        val recentSummaries: List<ScreenSummary>
    )
    
    /**
     * App usage data matching Android UsageStats API structure
     * Based on android.app.usage.UsageStats
     */
    data class AppUsageData(
        val packageName: String,
        val displayName: String,
        val lastTimeUsed: Long,               // System.currentTimeMillis()
        val totalTimeInForeground: Long,      // milliseconds
        val lastTimeForegroundServiceUsed: Long?,
        val queryInterval: Pair<Long, Long>   // (startTime, endTime)
    )
    
    /**
     * User-defined bad behavior in natural language
     * Example: "YouTube Shorts makes me lose track of time and sleep worse"
     */
    data class UserBadBehavior(
        val id: String,
        val description: String,
        val createdAt: Long
    )
    
    /**
     * Screen summary from AnalyzerAgent/EnhancedMemoryManager
     */
    data class ScreenSummary(
        val timestamp: Long,
        val appName: String,
        val summary: String,
        val confidence: Double
    )
    
    /**
     * Check for pattern violations based on raw context data
     * 
     * @param ctx Application context
     * @param rawContext Optional raw context with all data sources. If null, auto-populates from context providers
     * @return PatternViolation if detected, null otherwise
     */
    fun checkViolations(ctx: Context, rawContext: PatternAgentContext? = null): PatternViolation? {
        // Use context providers to gather data
        val context = rawContext ?: run {
            EnhancedMemoryManager.initialize(ctx)
            
            // Use MemoryContextProvider for chat history
            val chatHistory = MemoryContextProvider.getChatHistory()
            
            // Get scene timeline through provider (fallback for backward compatibility)
            val sceneTimeline = MemoryContextProvider.getSceneTimeline(ctx)
            
            // Convert existing SceneTimeline to AppUsageData format
            val appUsageStats = convertSceneTimelineToAppUsage(sceneTimeline)
            
            PatternAgentContext(
                appUsageStats = appUsageStats,
                chatHistory = chatHistory,
                userDefinedBehaviors = emptyList(), // TODO: Load from SharedPreferences via provider
                recentSummaries = emptyList() // TODO: Get from MemoryContextProvider
            )
        }
        
        // Get scene timeline for violation detection
        val sceneTimeline = MemoryContextProvider.getSceneTimeline(ctx)
        val chatHistory = context.chatHistory
        
        if (sceneTimeline.isEmpty()) {
            Log.d(TAG, "No scene timeline entries, skipping violation check")
            return null
        }
        
        // Get current app from most recent timeline entry
        val mostRecentEntry = sceneTimeline.lastOrNull() ?: return null
        val currentAppLabel = mostRecentEntry.sceneLabel
        
        if (currentAppLabel.isBlank()) {
            Log.d(TAG, "Most recent entry has blank sceneLabel, skipping")
            return null
        }
        
        // Calculate session duration for current app
        val sessionMinutes = calculateSessionDuration(currentAppLabel, sceneTimeline)
        
        // Rule 1: Extended session (30+ minutes)
        if (sessionMinutes >= 30) {
            val previousWarnings = countPreviousWarnings(currentAppLabel, chatHistory)
            val urgency = calculateUrgency(sessionMinutes, 30, previousWarnings)
            
            Log.d(TAG, "Violation detected: $currentAppLabel for $sessionMinutes minutes, urgency=$urgency, previousWarnings=$previousWarnings")
            
            return PatternViolation(
                appName = currentAppLabel,
                appDisplayName = currentAppLabel,  // Using display name for now
                urgency = urgency,
                context = buildContext(currentAppLabel, sessionMinutes, 30, previousWarnings, chatHistory)
            )
        }
        
        // Future: Add more rules here (excessive switching, user-defined limits)
        
        return null
    }
    
    /**
     * Calculate how long the user has been in the same app (within last hour)
     * Returns duration in minutes
     */
    private fun calculateSessionDuration(appLabel: String, timeline: List<EnhancedMemoryManager.SceneTimelineEntry>): Int {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (60 * 60 * 1000) // 1 hour window
        
        // Filter entries for this app within the last hour
        val relevantEntries = timeline.filter { entry ->
            entry.sceneLabel == appLabel && try {
                val entryTime = parseTimestamp(entry.timestamp)
                entryTime != null && entryTime >= oneHourAgo
            } catch (e: Exception) {
                false
            }
        }
        
        if (relevantEntries.isEmpty()) {
            return 0
        }
        
        // Get time range: first entry to last entry (for this app in this window)
        val firstEntryTime = parseTimestamp(relevantEntries.first().timestamp) ?: return 0
        val lastEntryTime = parseTimestamp(relevantEntries.last().timestamp) ?: return 0
        
        // Calculate duration: estimate based on entry span + average interval
        // Since screenshots happen every ~10 seconds (configurable), we estimate:
        // - Duration = (lastEntryTime - firstEntryTime) + average interval between entries
        // - Use ~10 seconds as baseline interval (matches typical screenshot interval)
        val timeSpan = lastEntryTime - firstEntryTime
        val estimatedIntervalMs = 10_000L // 10 seconds (average screenshot interval)
        val estimatedDuration = timeSpan + (estimatedIntervalMs * relevantEntries.size)
        
        return (estimatedDuration / 60_000).toInt() // Convert to minutes
    }
    
    /**
     * Calculate urgency based on overage and previous warnings
     * Formula: base urgency (0-5) + (previousWarnings * 2)
     * Capped at 10
     */
    private fun calculateUrgency(
        actualMinutes: Int,
        goalMinutes: Int,
        previousWarnings: Int
    ): Int {
        if (actualMinutes < goalMinutes) return 0
        
        val overage = actualMinutes - goalMinutes
        val overagePercent = overage.toDouble() / goalMinutes
        
        // Base urgency: 0-5 based on overage percentage
        // 0-50% over: urgency 1-2
        // 50-100% over: urgency 3-4
        // 100%+ over: urgency 5
        var baseUrgency = when {
            overagePercent < 0.5 -> 1
            overagePercent < 1.0 -> 3
            else -> 5
        }
        
        // Add urgency based on actual overage (minutes beyond threshold)
        // Every 15 minutes over adds 1 point
        baseUrgency += (overage / 15).coerceAtMost(3) // Cap at +3 for very long sessions
        
        // Add penalty for previous warnings
        val warningPenalty = previousWarnings * 2
        
        val totalUrgency = (baseUrgency + warningPenalty).coerceIn(0, 10)
        
        Log.d(TAG, "calculateUrgency: actual=$actualMinutes, goal=$goalMinutes, overage=$overage, base=$baseUrgency, warnings=$previousWarnings, total=$totalUrgency")
        
        return totalUrgency
    }
    
    /**
     * Build natural language context for LLM
     */
    private fun buildContext(
        appLabel: String,
        actualMinutes: Int,
        goalMinutes: Int,
        previousWarnings: Int,
        chatHistory: List<ChatManager.ChatMessage>
    ): String {
        val recentComments = chatHistory
            .filter { it.source == "commentary" && it.text.contains(appLabel, ignoreCase = true) }
            .takeLast(3)
        
        return buildString {
            appendLine("What's happening:")
            appendLine("Kris has been on $appLabel for $actualMinutes minutes.")
            appendLine("Typical sessions are around $goalMinutes minutes.")
            appendLine()
            
            if (recentComments.isNotEmpty()) {
                appendLine("What you've observed:")
                recentComments.forEach { msg ->
                    val timeAgo = formatTimeAgo(System.currentTimeMillis() - parseTimestampToMillis(msg.timestamp))
                    appendLine("- $timeAgo: \"${msg.text.take(100)}\"") // Truncate long messages
                }
                appendLine()
            }
            
            when (previousWarnings) {
                0 -> appendLine("This is your first time bringing this up seriously.")
                1 -> appendLine("You've already mentioned this once, but Kris didn't change their behavior.")
                else -> appendLine("This is the ${ordinal(previousWarnings + 1)} time you're addressing this. Your previous warnings were ignored.")
            }
        }
    }
    
    /**
     * Count previous warnings for this app (within today)
     */
    private fun countPreviousWarnings(appLabel: String, history: List<ChatManager.ChatMessage>): Int {
        val now = System.currentTimeMillis()
        val todayStart = now - (now % (24 * 60 * 60 * 1000)) // Start of today (midnight)
        
        return history.count { msg ->
            msg.source == "warning" &&
            parseTimestampToMillis(msg.timestamp) >= todayStart &&
            msg.text.contains(appLabel, ignoreCase = true)
        }
    }
    
    /**
     * Get app display name from package name (if needed in future)
     * Currently not used since we work with display names directly
     */
    @Suppress("unused")
    private fun getAppDisplayName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get app display name for $packageName", e)
            packageName
        }
    }
    
    /**
     * Parse ISO timestamp string to millis
     */
    private fun parseTimestampToMillis(isoTimestamp: String): Long {
        return try {
            // Try ISO format first
            isoDateFormat.parse(isoTimestamp)?.time ?: run {
                // Fallback: try simpler format (from ChatMessage)
                val simpleFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                simpleFormat.parse(isoTimestamp)?.time ?: 0L
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse timestamp: $isoTimestamp", e)
            0L
        }
    }
    
    /**
     * Parse ISO timestamp to Long (for comparison)
     */
    private fun parseTimestamp(isoTimestamp: String): Long? {
        return try {
            isoDateFormat.parse(isoTimestamp)?.time
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse timestamp: $isoTimestamp", e)
            null
        }
    }
    
    /**
     * Format time ago string
     */
    private fun formatTimeAgo(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            else -> {
                val hours = minutes / 60
                "$hours hour${if (hours > 1) "s" else ""} ago"
            }
        }
    }
    
    /**
     * Convert number to ordinal (1st, 2nd, 3rd, etc.)
     */
    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }
    
    /**
     * Convert SceneTimeline entries to AppUsageData format (for backward compatibility)
     * Groups timeline entries by app and calculates usage statistics
     */
    private fun convertSceneTimelineToAppUsage(timeline: List<EnhancedMemoryManager.SceneTimelineEntry>): List<AppUsageData> {
        if (timeline.isEmpty()) return emptyList()
        
        // Group entries by app
        val appGroups = timeline.groupBy { it.sceneLabel }
        
        return appGroups.map { (appName, entries) ->
            // Calculate total time in foreground
            val firstEntry = entries.firstOrNull()
            val lastEntry = entries.lastOrNull()
            
            val firstTime = firstEntry?.let { parseTimestamp(it.timestamp) } ?: System.currentTimeMillis()
            val lastTime = lastEntry?.let { parseTimestamp(it.timestamp) } ?: System.currentTimeMillis()
            
            val totalTime = lastTime - firstTime + (10_000 * entries.size) // Estimate based on entry count
            
            AppUsageData(
                packageName = "com.example.$appName", // Placeholder package name
                displayName = appName,
                lastTimeUsed = lastTime,
                totalTimeInForeground = totalTime,
                lastTimeForegroundServiceUsed = null,
                queryInterval = Pair(firstTime, lastTime)
            )
        }
    }
}

