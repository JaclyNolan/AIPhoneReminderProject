package com.example.myapplication.context

import android.content.Context
import android.util.Log
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.memory.EnhancedMemoryManager

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
     * Get formatted memory context for LLM prompts
     * 
     * @param context Application context
     * @return Formatted string with all memory tiers
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
                appendLine("  [${it.timestamp}] ${it.sceneLabel}: ${it.shortText}")
            }
            appendLine()
            appendLine("Condensed Memories:")
            memories.forEach {
                appendLine("  [${it.timestamp}] ${it.content}")
            }
            appendLine()
            appendLine("Recent Intents:")
            intents.forEach {
                appendLine("  [${it.timestamp}] ${it.intent}")
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

