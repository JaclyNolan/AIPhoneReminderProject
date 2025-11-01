package com.example.myapplication.testing

import android.content.Context
import android.util.Log
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.agents.PatternAgent
import com.example.myapplication.agents.PersonalityAgent
import com.example.myapplication.memory.EnhancedMemoryManager
import com.example.myapplication.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * TestAgent: Orchestrates test scenarios for the warning system
 * 
 * Features:
 * - Loads and runs test scenarios
 * - Generates realistic timeline entries
 * - Simulates warnings with previous history
 * - Inspects system state
 */
object TestAgent {
    private const val TAG = "TestAgent"
    
    /**
     * Run a test scenario end-to-end
     * 
     * @param context Application context
     * @param scenario Test scenario to run
     * @param clearFirst Whether to clear existing data first (default true)
     * @return Test result with actual vs expected
     */
    suspend fun runScenario(
        context: Context,
        scenario: TestScenario,
        clearFirst: Boolean = true
    ): TestResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Running scenario: ${scenario.name} ===")
        Log.d(TAG, scenario.description)
        
        // Initialize systems
        EnhancedMemoryManager.initialize(context)
        ChatManager.initialize(context)
        
        // Clear previous test data if requested
        if (clearFirst) {
            EnhancedMemoryManager.clear(context)
            ChatManager.clearHistory(context)
            Log.d(TAG, "Cleared previous test data")
        }
        
        // Generate timeline entries
        Log.d(TAG, "Generating ${scenario.timelineEntries.size} timeline entries...")
        populateTimeline(context, scenario.timelineEntries)
        
        // Check for violations
        Log.d(TAG, "Checking for violations...")
        val violation = PatternAgent.checkViolations(context)
        
        val actualUrgency = violation?.urgency ?: 0
        val actualOutcome = when {
            violation == null -> TestScenario.OutcomeType.NO_WARNING
            actualUrgency >= 7 -> TestScenario.OutcomeType.SOFT_INTERVENTION
            actualUrgency >= 4 -> TestScenario.OutcomeType.DIALOGUE_BUBBLE
            else -> TestScenario.OutcomeType.NO_WARNING
        }
        
        val success = actualUrgency == scenario.expectedUrgency && actualOutcome == scenario.expectedOutcome
        
        val result = TestResult(
            scenarioId = scenario.id,
            scenarioName = scenario.name,
            success = success,
            expectedUrgency = scenario.expectedUrgency,
            actualUrgency = actualUrgency,
            expectedOutcome = scenario.expectedOutcome,
            actualOutcome = actualOutcome,
            violation = violation
        )
        
        Log.d(TAG, "=== Result: ${if (success) "✅ PASS" else "❌ FAIL"} ===")
        Log.d(TAG, "Expected urgency: ${scenario.expectedUrgency}, Actual: $actualUrgency")
        Log.d(TAG, "Expected outcome: ${scenario.expectedOutcome}, Actual: $actualOutcome")
        
        return@withContext result
    }
    
    /**
     * Run all test scenarios
     */
    suspend fun runAllScenarios(context: Context): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        for (scenario in TestScenarios.getAll()) {
            val result = runScenario(context, scenario, clearFirst = true)
            results.add(result)
        }
        
        val passed = results.count { it.success }
        val total = results.size
        Log.d(TAG, "=== All scenarios complete: $passed/$total passed ===")
        
        return results
    }
    
    /**
     * Populate timeline with test entries
     */
    private fun populateTimeline(context: Context, entries: List<TestScenario.TimelineEntry>) {
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val now = System.currentTimeMillis()
        
        for (entry in entries) {
            val entryTime = now - (entry.minutesAgo * 60 * 1000)
            val timestamp = timestampFormat.format(Date(entryTime))
            
            val sceneEntry = EnhancedMemoryManager.SceneTimelineEntry(
                timestamp = timestamp,
                sceneLabel = entry.appName,
                shortText = entry.activity,
                confidence = 0.85
            )
            
            EnhancedMemoryManager.addSceneTimelineEntry(context, sceneEntry)
        }
        
        Log.d(TAG, "Populated timeline with ${entries.size} entries")
    }
    
    /**
     * Simulate a previous warning (for escalation testing)
     */
    fun simulatePreviousWarning(
        context: Context,
        appName: String,
        urgency: Int = 5,
        hoursAgo: Int = 0,
        minutesAgo: Int = 30
    ) {
        ChatManager.initialize(context)
        
        val warningText = "Previous warning about $appName usage"
        ChatManager.addAssistantMessage(
            context,
            warningText,
            source = "warning",
            urgency = urgency,
            characterId = "ralsei"
        )
        
        Log.d(TAG, "Simulated previous warning: urgency=$urgency, $minutesAgo min ago")
    }
    
    /**
     * Generate a custom realistic session
     */
    fun generateCustomSession(
        context: Context,
        appName: String,
        durationMinutes: Int,
        withBreaks: Boolean = false
    ) {
        val entries = if (withBreaks) {
            // Generate session with small breaks
            val segments = listOf(
                Pair(durationMinutes / 2, durationMinutes),
                Pair(durationMinutes / 4, durationMinutes / 2 - 5)
            )
            
            segments.flatMap { (duration, startMinutesAgo) ->
                List(duration * 6) { i ->  // 10-second intervals
                    TestScenario.TimelineEntry(
                        appName = appName,
                        minutesAgo = startMinutesAgo - (i / 6),
                        activity = "using app"
                    )
                }
            }
        } else {
            // Continuous session
            List(durationMinutes * 6) { i ->
                TestScenario.TimelineEntry(
                    appName = appName,
                    minutesAgo = durationMinutes - (i / 6),
                    activity = "using app"
                )
            }
        }
        
        populateTimeline(context, entries)
        Log.d(TAG, "Generated custom session: $appName, $durationMinutes min, breaks=$withBreaks")
    }
    
    /**
     * Inspect current system state
     */
    fun inspectState(context: Context): SystemState {
        EnhancedMemoryManager.initialize(context)
        ChatManager.initialize(context)
        
        val timeline = EnhancedMemoryManager.getAllSceneTimeline(context)
        val history = ChatManager.getHistorySnapshot()
        val warnings = history.filter { it.source == "warning" }
        
        return SystemState(
            timelineSize = timeline.size,
            currentApp = timeline.lastOrNull()?.sceneLabel ?: "none",
            warningCount = warnings.size,
            lastWarningUrgency = warnings.lastOrNull()?.urgency ?: 0,
            mockMode = LLMClientFactory.isMockMode()
        )
    }
    
    data class TestResult(
        val scenarioId: String,
        val scenarioName: String,
        val success: Boolean,
        val expectedUrgency: Int,
        val actualUrgency: Int,
        val expectedOutcome: TestScenario.OutcomeType,
        val actualOutcome: TestScenario.OutcomeType,
        val violation: PatternAgent.PatternViolation?
    )
    
    data class SystemState(
        val timelineSize: Int,
        val currentApp: String,
        val warningCount: Int,
        val lastWarningUrgency: Int,
        val mockMode: Boolean
    )
    
    /**
     * Build PatternAgentContext from manual test inputs (LEGACY - for custom scenarios)
     * For use in DebugScreen manual testing
     * 
     * @param context Application context
     * @param appName App name (e.g., "YouTube")
     * @param sessionDurationMinutes Duration in minutes
     * @param currentTimeString Time in "HH:mm" format (e.g., "23:30")
     * @param userBehaviorStrings List of user-defined bad behaviors
     * @param additionalChatHistory Additional chat messages to include
     * @param additionalSummaries Additional screen summaries to include
     * @return PatternAgentContext ready for testing
     */
    fun buildManualContext(
        context: android.content.Context,
        appName: String,
        sessionDurationMinutes: Int,
        currentTimeString: String,
        userBehaviorStrings: List<String>,
        additionalChatHistory: List<ChatManager.ChatMessage> = emptyList(),
        additionalSummaries: List<PatternAgent.ScreenSummary> = emptyList()
    ): PatternAgent.PatternAgentContext {
        // Parse time
        val parts = currentTimeString.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val currentTimeMillis = calendar.timeInMillis
        
        // Generate mock AppUsageStats
        val appUsageData = MockAppUsageStats.generateUsageStats(
            packageName = "com.example.${appName.lowercase().replace(" ", "")}",
            displayName = appName,
            sessionStartTime = currentTimeMillis - (sessionDurationMinutes * 60 * 1000),
            sessionDurationMinutes = sessionDurationMinutes
        )
        
        // Convert user behavior strings to UserBadBehavior objects
        val userBehaviors = userBehaviorStrings.mapIndexed { index, description ->
            PatternAgent.UserBadBehavior(
                id = "behavior_$index",
                description = description.trim(),
                createdAt = System.currentTimeMillis()
            )
        }
        
        // Get existing chat history
        ChatManager.initialize(context)
        val chatHistory = try {
            ChatManager.getHistorySnapshot() + additionalChatHistory
        } catch (e: Exception) {
            additionalChatHistory // Fallback to just additional if initialization fails
        }
        
        // Build context
        return PatternAgent.PatternAgentContext(
            appUsageStats = listOf(appUsageData),
            chatHistory = chatHistory,
            userDefinedBehaviors = userBehaviors,
            recentSummaries = additionalSummaries
        )
    }
    
    /**
     * Build PatternAgentContext from REAL device data
     * This mimics the automatic 5-minute check but can be triggered on-demand
     * 
     * @param context Application context
     * @param usageIntervalMinutes How far back to query app usage (default: 60 minutes)
     * @return PatternAgentContext with real data from device
     */
    fun buildRealContext(
        context: android.content.Context,
        usageIntervalMinutes: Int = 60
    ): PatternAgent.PatternAgentContext {
        android.util.Log.d(TAG, "Building real context from device data...")
        
        // 1. Query real app usage from Android API
        val appUsageStats = RealAppUsageReader.queryUsageStats(context, usageIntervalMinutes)
        android.util.Log.d(TAG, "Retrieved ${appUsageStats.size} app usage entries")
        
        // 2. Get chat history
        ChatManager.initialize(context)
        val chatHistory = ChatManager.getHistorySnapshot()
        android.util.Log.d(TAG, "Retrieved ${chatHistory.size} chat history entries")
        
        // 3. Get user-defined bad behaviors from SharedPreferences
        val userBehaviors = getUserDefinedBehaviors(context)
        android.util.Log.d(TAG, "Retrieved ${userBehaviors.size} user-defined behaviors")
        
        // 4. Get recent screen summaries from EnhancedMemoryManager
        EnhancedMemoryManager.initialize(context)
        val recentSummaries = getRecentScreenSummaries(context, limitMinutes = 30)
        android.util.Log.d(TAG, "Retrieved ${recentSummaries.size} recent screen summaries")
        
        // Build full context
        return PatternAgent.PatternAgentContext(
            appUsageStats = appUsageStats,
            chatHistory = chatHistory,
            userDefinedBehaviors = userBehaviors,
            recentSummaries = recentSummaries
        )
    }
    
    /**
     * Get user-defined bad behaviors from SharedPreferences
     * TODO: Implement proper storage for user-defined behaviors
     */
    private fun getUserDefinedBehaviors(context: android.content.Context): List<PatternAgent.UserBadBehavior> {
        // For now, return empty list
        // In future: Read from SharedPreferences where onboarding screen saves them
        return emptyList()
    }
    
    /**
     * Get recent screen summaries from EnhancedMemoryManager
     */
    private fun getRecentScreenSummaries(
        context: android.content.Context,
        limitMinutes: Int = 30
    ): List<PatternAgent.ScreenSummary> {
        try {
            val timeline = EnhancedMemoryManager.getAllSceneTimeline(context)
            val cutoffTime = System.currentTimeMillis() - (limitMinutes * 60 * 1000)
            
            return timeline
                .mapNotNull { entry ->
                    try {
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                        val timestamp = format.parse(entry.timestamp)?.time ?: return@mapNotNull null
                        
                        if (timestamp >= cutoffTime) {
                            PatternAgent.ScreenSummary(
                                timestamp = timestamp,
                                appName = entry.sceneLabel,
                                summary = entry.shortText,
                                confidence = entry.confidence
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting screen summaries", e)
            return emptyList()
        }
    }
}

