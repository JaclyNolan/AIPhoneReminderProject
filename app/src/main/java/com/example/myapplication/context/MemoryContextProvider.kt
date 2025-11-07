package com.example.myapplication.context

import android.content.Context
import android.util.Log
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.memory.EnhancedMemoryManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * MemoryContextProvider: Provides memory system context
 * 
 * Single responsibility: Read from four-tier memory architecture
 * (SceneTimeline, CondensedMemories, RecentIntents, DialogueSummaries)
 * Used by agents for context-aware decision making
 */
object MemoryContextProvider {
    private const val TAG = "MemoryContextProvider"
    
    /**
     * Get scene timeline entries (chronological scene summaries)
     * 
     * @param context Application context (required for EnhancedMemoryManager)
     * @param limitMinutes Optional time limit (last N minutes)
     * @return List of scene timeline entries
     */
    fun getSceneTimeline(context: Context, limitMinutes: Int? = null): List<EnhancedMemoryManager.SceneTimelineEntry> {
        // EnhancedMemoryManager requires context parameter
        val allEntries = EnhancedMemoryManager.getAllSceneTimeline(context)
        
        if (limitMinutes == null) return allEntries
        
        // Filter to entries within time limit
        val cutoffTime = System.currentTimeMillis() - (limitMinutes * 60 * 1000)
        return allEntries.filter { entry ->
            try {
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                    .parse(entry.timestamp)?.time ?: 0L
                timestamp >= cutoffTime
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Get condensed memories (emotional/important facts)
     * 
     * @param context Application context (required for EnhancedMemoryManager)
     * @return List of condensed memory items
     */
    fun getCondensedMemories(context: Context): List<EnhancedMemoryManager.CondensedMemoryItem> {
        return EnhancedMemoryManager.getAllCondensedMemories(context)
    }
    
    /**
     * Get recent intents (rotating buffer of assistant intents)
     * 
     * @param context Application context (required for EnhancedMemoryManager)
     * @return List of recent intents
     */
    fun getRecentIntents(context: Context): List<EnhancedMemoryManager.RecentIntent> {
        // Note: EnhancedMemoryManager doesn't have getAllRecentIntents, returns empty list
        return emptyList()
    }
    
    /**
     * Get dialogue summaries (periodic chat history compression)
     * 
     * @param context Application context (required for EnhancedMemoryManager)
     * @return List of dialogue summary strings
     */
    fun getDialogueSummaries(context: Context): List<String> {
        return EnhancedMemoryManager.getAllDialogueSummaries(context)
    }
    
    /**
     * Get chat history
     * 
     * @param limitMessages Optional limit on number of recent messages
     * @return List of chat messages
     */
    fun getChatHistory(limitMessages: Int? = null): List<ChatManager.ChatMessage> {
        val history = ChatManager.getHistorySnapshot()
        return if (limitMessages != null) {
            history.takeLast(limitMessages)
        } else {
            history
        }
    }
    
    /**
     * Get chat history filtered by source
     * 
     * @param source Message source ("commentary", "warning", "user", "assistant")
     * @param limitMessages Optional limit on number of messages
     * @return Filtered list of chat messages
     */
    fun getChatHistoryBySource(source: String, limitMessages: Int? = null): List<ChatManager.ChatMessage> {
        val filtered = ChatManager.getHistorySnapshot().filter { it.source == source }
        return if (limitMessages != null) {
            filtered.takeLast(limitMessages)
        } else {
            filtered
        }
    }
    
    /**
     * Get recent warning history (warning messages only)
     * 
     * @param limitMessages Number of recent warnings (default: 10)
     * @return List of warning messages
     */
    fun getRecentWarnings(limitMessages: Int = 10): List<ChatManager.ChatMessage> {
        return getChatHistoryBySource("warning", limitMessages)
    }
    
    /**
     * Format ISO 8601 timestamp to natural language
     * 
     * @param timestamp ISO 8601 timestamp string (yyyy-MM-dd'T'HH:mm:ssXXX)
     * @return Natural language timestamp (e.g., "At 10:30 AM", "At 2:15 PM yesterday")
     */
    private fun formatTimestampToNaturalLanguage(timestamp: String): String {
        try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val date = isoFormat.parse(timestamp) ?: return timestamp // Fallback to original if parse fails
            
            val now = Date()
            val calendar = Calendar.getInstance()
            calendar.time = now
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val entryCalendar = Calendar.getInstance()
            entryCalendar.time = date
            
            // Format time as "HH:MM AM/PM"
            val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
            val timeStr = timeFormat.format(date)
            
            // Determine relative time
            val daysDiff = ((now.time - date.time) / (1000 * 60 * 60 * 24)).toInt()
            
            return when {
                // Today
                entryCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                entryCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) -> {
                    "At $timeStr"
                }
                // Yesterday
                daysDiff == 1 -> {
                    "At $timeStr yesterday"
                }
                // This week (within 7 days)
                daysDiff in 2..7 -> {
                    val dayFormat = SimpleDateFormat("EEEE", Locale.US)
                    val dayName = dayFormat.format(date)
                    "At $timeStr on $dayName"
                }
                // Older
                else -> {
                    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
                    val dateStr = dateFormat.format(date)
                    "At $timeStr on $dateStr"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to format timestamp: $timestamp", e)
            return timestamp // Fallback to original timestamp
        }
    }
    
    /**
     * Get formatted memory context for LLM prompts
     * 
     * @param context Application context
     * @return Formatted string with all memory tiers using natural language timestamps
     */
    fun getFormattedMemoryContext(context: Context): String {
        val scenes = getSceneTimeline(context, limitMinutes = 60)
        val memories = getCondensedMemories(context)
        val intents = getRecentIntents(context)
        val summaries = getDialogueSummaries(context)
        
        return buildString {
            appendLine("=== MEMORY CONTEXT ===")
            appendLine()
            appendLine("Recent Scenes (last hour):")
            scenes.forEach { 
                val naturalTime = formatTimestampToNaturalLanguage(it.timestamp)
                appendLine("  $naturalTime, user is ${it.sceneLabel.lowercase()}: ${it.shortText}")
            }
            appendLine()
            appendLine("Condensed Memories:")
            memories.forEach {
                val naturalTime = formatTimestampToNaturalLanguage(it.timestamp)
                appendLine("  $naturalTime, ${it.content}")
            }
            appendLine()
            appendLine("Recent Intents:")
            intents.forEach {
                val naturalTime = formatTimestampToNaturalLanguage(it.timestamp)
                appendLine("  $naturalTime, ${it.intent}")
            }
            appendLine()
            if (summaries.isNotEmpty()) {
                appendLine("Dialogue Summaries:")
                summaries.forEach {
                    appendLine("  $it")
                }
            }
        }
    }
}

