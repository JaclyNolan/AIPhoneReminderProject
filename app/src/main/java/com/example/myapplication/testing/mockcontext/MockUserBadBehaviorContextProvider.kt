package com.example.myapplication.testing.mockcontext

import android.content.Context
import android.util.Log
import com.example.myapplication.context.UserBadBehaviorContextProvider

/**
 * Mock UserBadBehaviorContextProvider for testing
 */
object MockUserBadBehaviorContextProvider {
    private const val TAG = "MockUserBadBehaviorContextProvider"
    
    private var mockBehaviors: List<UserBadBehaviorContextProvider.UserBadBehavior> = emptyList()
    
    /**
     * Set mock behaviors
     */
    fun setMockBehaviors(behaviors: List<UserBadBehaviorContextProvider.UserBadBehavior>) {
        mockBehaviors = behaviors
        Log.d(TAG, "Set ${behaviors.size} mock behaviors")
    }
    
    /**
     * Add a mock behavior
     */
    fun addMockBehavior(
        description: String,
        associatedApps: List<String> = emptyList()
    ) {
        val behavior = UserBadBehaviorContextProvider.UserBadBehavior(
            id = "mock_${System.currentTimeMillis()}",
            description = description,
            createdAt = System.currentTimeMillis(),
            associatedApps = associatedApps
        )
        mockBehaviors = mockBehaviors + behavior
        Log.d(TAG, "Added mock behavior: $description")
    }
    
    /**
     * Get mock behaviors
     */
    fun getBadBehaviors(context: Context): List<UserBadBehaviorContextProvider.UserBadBehavior> {
        return mockBehaviors
    }
    
    /**
     * Get current mock data for display
     */
    fun getCurrentMockData(): List<UserBadBehaviorContextProvider.UserBadBehavior> {
        return mockBehaviors
    }
    
    /**
     * Reset to default state
     */
    fun reset() {
        mockBehaviors = emptyList()
    }
    
    /**
     * Set default test scenario behaviors
     */
    fun setDefaultScenario(scenarioType: String) {
        when (scenarioType) {
            "youtube_long_session" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_youtube_1",
                        description = "YouTube Shorts makes me lose track of time and sleep worse",
                        createdAt = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000), // 7 days ago
                        associatedApps = listOf("YouTube")
                    )
                )
            }
            "tiktok_late_night" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_tiktok_1",
                        description = "TikTok keeps me up past midnight",
                        createdAt = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000), // 3 days ago
                        associatedApps = listOf("TikTok")
                    )
                )
            }
            "critical_binge", "repeat_offender" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_youtube_critical",
                        description = "YouTube Shorts makes me lose track of time and sleep worse",
                        createdAt = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("YouTube")
                    ),
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_youtube_escalation",
                        description = "I ignore warnings and keep watching",
                        createdAt = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("YouTube")
                    )
                )
            }
            "app_hopping_binge" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_hopping_1",
                        description = "Rapid app switching is a form of distraction",
                        createdAt = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("YouTube", "Instagram", "Reddit", "TikTok")
                    )
                )
            }
            "extended_morning" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_morning_1",
                        description = "Morning scrolling makes me late",
                        createdAt = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("TikTok")
                    )
                )
            }
            "relapse_pattern" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_relapse_1",
                        description = "YouTube Shorts makes me lose track of time and sleep worse",
                        createdAt = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("YouTube")
                    )
                )
            }
            "multiple_sessions_same_app" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_multiple_1",
                        description = "YouTube Shorts makes me lose track of time and sleep worse",
                        createdAt = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("YouTube")
                    )
                )
            }
            "weekend_binge" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_weekend_1",
                        description = "Weekend binges mess up my sleep schedule",
                        createdAt = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("TikTok")
                    )
                )
            }
            "low_battery_usage" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_battery_1",
                        description = "I use phone even when battery is low",
                        createdAt = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("YouTube")
                    )
                )
            }
            "work_vs_leisure" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_chrome_1",
                        description = "Chrome browsing wastes time",
                        createdAt = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("Chrome")
                    )
                )
            }
            "late_night_escalation" -> {
                mockBehaviors = listOf(
                    UserBadBehaviorContextProvider.UserBadBehavior(
                        id = "mock_late_night_esc_1",
                        description = "TikTok keeps me up past midnight",
                        createdAt = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000),
                        associatedApps = listOf("TikTok")
                    )
                )
            }
            "minimal_usage" -> {
                mockBehaviors = emptyList() // No bad behavior for negative test
            }
            else -> {
                mockBehaviors = emptyList()
            }
        }
    }
}

