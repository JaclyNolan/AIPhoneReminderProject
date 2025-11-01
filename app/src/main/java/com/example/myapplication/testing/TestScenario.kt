package com.example.myapplication.testing

import java.text.SimpleDateFormat
import java.util.*

/**
 * Test scenario definition for warning system testing
 */
data class TestScenario(
    val id: String,
    val name: String,
    val description: String,
    val timelineEntries: List<TimelineEntry>,
    val expectedUrgency: Int,
    val expectedOutcome: OutcomeType
) {
    enum class OutcomeType {
        NO_WARNING,           // Below threshold
        DIALOGUE_BUBBLE,      // Urgency 4-6
        SOFT_INTERVENTION     // Urgency 7-10
    }
    
    data class TimelineEntry(
        val appName: String,
        val minutesAgo: Int,        // How many minutes ago this entry was
        val activity: String = "using app"
    )
}

/**
 * Pre-defined test scenarios
 */
object TestScenarios {
    
    /**
     * Scenario 1: Short session (no warning)
     */
    val shortSession = TestScenario(
        id = "short_session",
        name = "Short Session (15 min)",
        description = "User watches YouTube for 15 minutes. No warning should trigger.",
        timelineEntries = generateContinuousSession("YouTube", 15),
        expectedUrgency = 0,
        expectedOutcome = TestScenario.OutcomeType.NO_WARNING
    )
    
    /**
     * Scenario 2: Extended session (first warning)
     */
    val firstWarning = TestScenario(
        id = "first_warning",
        name = "First Warning (35 min)",
        description = "User watches YouTube for 35 minutes. First warning at urgency 5.",
        timelineEntries = generateContinuousSession("YouTube", 35),
        expectedUrgency = 5,
        expectedOutcome = TestScenario.OutcomeType.DIALOGUE_BUBBLE
    )
    
    /**
     * Scenario 3: Long session (high urgency)
     */
    val longSession = TestScenario(
        id = "long_session",
        name = "Long Session (50 min)",
        description = "User watches YouTube for 50 minutes. High urgency warning.",
        timelineEntries = generateContinuousSession("YouTube", 50),
        expectedUrgency = 7,
        expectedOutcome = TestScenario.OutcomeType.SOFT_INTERVENTION
    )
    
    /**
     * Scenario 4: Very long session (critical)
     */
    val criticalSession = TestScenario(
        id = "critical_session",
        name = "Critical Session (70 min)",
        description = "User watches YouTube for 70 minutes. Critical urgency.",
        timelineEntries = generateContinuousSession("YouTube", 70),
        expectedUrgency = 9,
        expectedOutcome = TestScenario.OutcomeType.SOFT_INTERVENTION
    )
    
    /**
     * Scenario 5: App switching (no single long session)
     */
    val appSwitching = TestScenario(
        id = "app_switching",
        name = "App Switching",
        description = "User switches between apps. No single session exceeds threshold.",
        timelineEntries = listOf(
            // YouTube 15 min
            *generateContinuousSession("YouTube", 15).toTypedArray(),
            // Instagram 10 min
            *generateContinuousSession("Instagram", 10, startMinutesAgo = 25).toTypedArray(),
            // Back to YouTube 10 min
            *generateContinuousSession("YouTube", 10, startMinutesAgo = 15).toTypedArray()
        ),
        expectedUrgency = 0,
        expectedOutcome = TestScenario.OutcomeType.NO_WARNING
    )
    
    /**
     * Scenario 6: Escalation (multiple warnings)
     * This requires chat history with previous warnings
     */
    val escalation = TestScenario(
        id = "escalation",
        name = "Escalation (2nd warning)",
        description = "User continues after first warning. Urgency escalates.",
        timelineEntries = generateContinuousSession("YouTube", 52),
        expectedUrgency = 7, // Base 5-6 + 2 for previous warning
        expectedOutcome = TestScenario.OutcomeType.SOFT_INTERVENTION
    )
    
    /**
     * Get all scenarios
     */
    fun getAll(): List<TestScenario> = listOf(
        shortSession,
        firstWarning,
        longSession,
        criticalSession,
        appSwitching,
        escalation
    )
    
    /**
     * Helper: Generate continuous session entries
     */
    private fun generateContinuousSession(
        appName: String,
        durationMinutes: Int,
        startMinutesAgo: Int = durationMinutes,
        intervalSeconds: Int = 10
    ): List<TestScenario.TimelineEntry> {
        val entries = mutableListOf<TestScenario.TimelineEntry>()
        val numEntries = (durationMinutes * 60) / intervalSeconds
        
        for (i in 0 until numEntries) {
            val minutesAgo = startMinutesAgo - (i * intervalSeconds / 60)
            entries.add(
                TestScenario.TimelineEntry(
                    appName = appName,
                    minutesAgo = minutesAgo,
                    activity = "using app"
                )
            )
        }
        
        return entries
    }
}

