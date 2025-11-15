package com.example.myapplication.testing.mockcontext

import android.content.Context
import android.util.Log
import com.example.myapplication.memory.EnhancedMemoryManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mock MemoryContextProvider for testing
 */
object MockMemoryContextProvider {
    private const val TAG = "MockMemoryContextProvider"
    
    private var mockTimeline: List<EnhancedMemoryManager.SceneTimelineEntry> = emptyList()
    private var mockMemories: List<EnhancedMemoryManager.CondensedMemoryItem> = emptyList()
    private var mockSummaries: List<String> = emptyList()
    
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    
    /**
     * Set mock scene timeline
     */
    fun setMockTimeline(entries: List<EnhancedMemoryManager.SceneTimelineEntry>) {
        mockTimeline = entries
        Log.d(TAG, "Set ${entries.size} mock timeline entries")
    }
    
    /**
     * Set mock condensed memories
     */
    fun setMockMemories(memories: List<EnhancedMemoryManager.CondensedMemoryItem>) {
        mockMemories = memories
        Log.d(TAG, "Set ${memories.size} mock memories")
    }
    
    /**
     * Set mock dialogue summaries
     */
    fun setMockSummaries(summaries: List<String>) {
        mockSummaries = summaries
        Log.d(TAG, "Set ${summaries.size} mock summaries")
    }
    
    /**
     * Get scene timeline (mock or real)
     */
    fun getSceneTimeline(context: Context, limitMinutes: Int? = null): List<EnhancedMemoryManager.SceneTimelineEntry> {
        val allEntries = mockTimeline
        
        if (limitMinutes == null) return allEntries
        
        // Filter to entries within time limit
        val cutoffTime = System.currentTimeMillis() - (limitMinutes * 60 * 1000)
        return allEntries.filter { entry ->
            try {
                val timestamp = isoDateFormat.parse(entry.timestamp)?.time ?: 0L
                timestamp >= cutoffTime
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Get condensed memories (mock)
     */
    fun getCondensedMemories(context: Context): List<EnhancedMemoryManager.CondensedMemoryItem> {
        return mockMemories
    }
    
    /**
     * Get dialogue summaries (mock)
     */
    fun getDialogueSummaries(context: Context): List<String> {
        return mockSummaries
    }
    
    /**
     * Format ISO 8601 timestamp to natural language (matching real provider)
     */
    private fun formatTimestampToNaturalLanguage(timestamp: String): String {
        try {
            val date = isoDateFormat.parse(timestamp) ?: return timestamp
            
            val now = Date()
            val calendar = Calendar.getInstance()
            calendar.time = now
            
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
            return timestamp
        }
    }
    
    /**
     * Get formatted memory context (mock) - matching real provider format
     */
    fun getFormattedMemoryContext(context: Context): String {
        val scenes = getSceneTimeline(context, limitMinutes = 60)
        val memories = getCondensedMemories(context)
        val summaries = getDialogueSummaries(context)
        
        return buildString {
            appendLine("=== MEMORY CONTEXT (MOCK) ===")
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
            if (summaries.isNotEmpty()) {
                appendLine("Dialogue Summaries:")
                summaries.forEach {
                    appendLine("  $it")
                }
            }
        }
    }
    
    /**
     * Get current mock data for display
     */
    fun getCurrentMockData(): MockMemoryData {
        return MockMemoryData(
            timeline = mockTimeline,
            memories = mockMemories,
            summaries = mockSummaries
        )
    }
    
    /**
     * Generate mock timeline for test scenario
     */
    fun generateMockTimelineForScenario(scenarioType: String): List<EnhancedMemoryManager.SceneTimelineEntry> {
        val now = System.currentTimeMillis()
        val entries = mutableListOf<EnhancedMemoryManager.SceneTimelineEntry>()
        
        when (scenarioType) {
            "youtube_long_session" -> {
                // Generate entries for 45 minutes of YouTube
                for (i in 0..44) {
                    val timestamp = now - ((44 - i) * 60 * 1000) // Last 45 minutes
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "YouTube",
                            shortText = "Watching YouTube Shorts, rapid scrolling",
                            confidence = 0.85
                        )
                    )
                }
            }
            "tiktok_late_night" -> {
                // Generate entries for 35 minutes of TikTok late at night
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 30)
                    set(Calendar.SECOND, 0)
                }
                val startTime = calendar.timeInMillis
                
                for (i in 0..34) {
                    val timestamp = startTime + (i * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "TikTok",
                            shortText = "Scrolling through TikTok videos",
                            confidence = 0.90
                        )
                    )
                }
            }
            "critical_binge" -> {
                // 90 minutes of YouTube
                for (i in 0..89) {
                    val timestamp = now - ((89 - i) * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "YouTube",
                            shortText = "Continuous YouTube watching, no breaks",
                            confidence = 0.95
                        )
                    )
                }
            }
            "repeat_offender" -> {
                // 45 minutes with escalation context
                for (i in 0..44) {
                    val timestamp = now - ((44 - i) * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "YouTube",
                            shortText = "Continued usage after warnings",
                            confidence = 0.88
                        )
                    )
                }
            }
            "extended_morning" -> {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 6)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val startTime = calendar.timeInMillis
                
                for (i in 0..59) {
                    val timestamp = startTime + (i * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "TikTok",
                            shortText = "Early morning scrolling",
                            confidence = 0.85
                        )
                    )
                }
            }
            "app_hopping_binge" -> {
                // Mixed app timeline
                val apps = listOf(
                    Pair("YouTube", 5),
                    Pair("Instagram", 3),
                    Pair("Reddit", 4),
                    Pair("TikTok", 6)
                )
                var timeOffset = 18
                apps.forEach { (app, minutes) ->
                    for (i in 0 until minutes) {
                        val timestamp = now - (timeOffset * 60 * 1000)
                        entries.add(
                            EnhancedMemoryManager.SceneTimelineEntry(
                                timestamp = isoDateFormat.format(Date(timestamp)),
                                sceneLabel = app,
                                shortText = "Rapid app switching pattern",
                                confidence = 0.75
                            )
                        )
                        timeOffset--
                    }
                }
            }
            "relapse_pattern" -> {
                // First session
                for (i in 0..34) {
                    val timestamp = now - ((99 - i) * 60 * 1000) // 1 hour ago
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "YouTube",
                            shortText = "First session",
                            confidence = 0.80
                        )
                    )
                }
                // Second session (after pause)
                for (i in 0..14) {
                    val timestamp = now - ((24 - i) * 60 * 1000) // 10 min ago
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "YouTube",
                            shortText = "Relapse after brief pause",
                            confidence = 0.85
                        )
                    )
                }
            }
            "multiple_sessions_same_app" -> {
                // Three sessions over 2 hours
                val session1Start = now - (120 * 60 * 1000)
                val session2Start = now - (70 * 60 * 1000)
                val session3Start = now - (40 * 60 * 1000)
                
                listOf(30, 20, 40).forEachIndexed { idx, duration ->
                    val startTime = when (idx) {
                        0 -> session1Start
                        1 -> session2Start
                        else -> session3Start
                    }
                    for (i in 0 until duration) {
                        val timestamp = startTime + (i * 60 * 1000)
                        entries.add(
                            EnhancedMemoryManager.SceneTimelineEntry(
                                timestamp = isoDateFormat.format(Date(timestamp)),
                                sceneLabel = "YouTube",
                                shortText = "Multiple session escalation",
                                confidence = 0.82
                            )
                        )
                    }
                }
            }
            "weekend_binge" -> {
                for (i in 0..59) {
                    val timestamp = now - ((59 - i) * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "TikTok",
                            shortText = "Weekend extended usage",
                            confidence = 0.80
                        )
                    )
                }
            }
            "low_battery_usage" -> {
                for (i in 0..49) {
                    val timestamp = now - ((49 - i) * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "YouTube",
                            shortText = "Usage despite low battery",
                            confidence = 0.83
                        )
                    )
                }
            }
            "work_vs_leisure" -> {
                for (i in 0..59) {
                    val timestamp = now - ((59 - i) * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "Chrome",
                            shortText = "Extended browser usage",
                            confidence = 0.70
                        )
                    )
                }
            }
            "late_night_escalation" -> {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 30)
                    set(Calendar.SECOND, 0)
                }
                val startTime = calendar.timeInMillis
                
                for (i in 0..44) {
                    val timestamp = startTime + (i * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "TikTok",
                            shortText = "Late night after warnings",
                            confidence = 0.90
                        )
                    )
                }
            }
            "minimal_usage" -> {
                for (i in 0..24) {
                    val timestamp = now - ((24 - i) * 60 * 1000)
                    entries.add(
                        EnhancedMemoryManager.SceneTimelineEntry(
                            timestamp = isoDateFormat.format(Date(timestamp)),
                            sceneLabel = "YouTube",
                            shortText = "Normal usage",
                            confidence = 0.75
                        )
                    )
                }
            }
        }
        
        return entries
    }
    
    /**
     * Reset to default state
     */
    fun reset() {
        mockTimeline = emptyList()
        mockMemories = emptyList()
        mockSummaries = emptyList()
    }
    
    data class MockMemoryData(
        val timeline: List<EnhancedMemoryManager.SceneTimelineEntry>,
        val memories: List<EnhancedMemoryManager.CondensedMemoryItem>,
        val summaries: List<String>
    )
}

