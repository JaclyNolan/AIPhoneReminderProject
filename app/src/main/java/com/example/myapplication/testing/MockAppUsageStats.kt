package com.example.myapplication.testing

import com.example.myapplication.agents.PatternAgent
import java.util.Calendar

/**
 * Mock AppUsageStats data generator for testing PatternAgent
 * 
 * Generates realistic mock data matching Android UsageStatsManager.queryUsageStats() format:
 * - android.app.usage.UsageStats structure
 * - Realistic timestamps and durations
 * - Common app package names
 */
object MockAppUsageStats {
    
    /**
     * Generate mock AppUsageStats matching Android API format
     * Based on UsageStatsManager.queryUsageStats() and UsageEvents
     */
    fun generateUsageStats(
        packageName: String,
        displayName: String,
        sessionStartTime: Long,
        sessionDurationMinutes: Int,
        isForeground: Boolean = true
    ): PatternAgent.AppUsageData {
        val sessionDurationMs = sessionDurationMinutes * 60 * 1000L
        val endTime = sessionStartTime + sessionDurationMs
        
        return PatternAgent.AppUsageData(
            packageName = packageName,
            displayName = displayName,
            lastTimeUsed = endTime,
            totalTimeInForeground = if (isForeground) sessionDurationMs else 0L,
            lastTimeForegroundServiceUsed = if (isForeground) endTime else null,
            queryInterval = Pair(sessionStartTime, endTime)
        )
    }
    
    /**
     * Generate realistic usage patterns
     */
    fun generateRealisticScenario(scenarioType: String): List<PatternAgent.AppUsageData> {
        val now = System.currentTimeMillis()
        
        return when (scenarioType) {
            "continuous_session" -> {
                // Single app, continuous usage for 45 minutes
                listOf(
                    generateUsageStats(
                        packageName = "com.google.android.youtube",
                        displayName = "YouTube",
                        sessionStartTime = now - (45 * 60 * 1000),
                        sessionDurationMinutes = 45
                    )
                )
            }
            
            "app_switching" -> {
                // Rapid switching between apps (procrastination pattern)
                listOf(
                    generateUsageStats(
                        packageName = "com.google.android.youtube",
                        displayName = "YouTube",
                        sessionStartTime = now - (18 * 60 * 1000),
                        sessionDurationMinutes = 5
                    ),
                    generateUsageStats(
                        packageName = "com.instagram.android",
                        displayName = "Instagram",
                        sessionStartTime = now - (13 * 60 * 1000),
                        sessionDurationMinutes = 3
                    ),
                    generateUsageStats(
                        packageName = "com.reddit.frontpage",
                        displayName = "Reddit",
                        sessionStartTime = now - (10 * 60 * 1000),
                        sessionDurationMinutes = 4
                    ),
                    generateUsageStats(
                        packageName = "com.zhiliaoapp.musically", // TikTok
                        displayName = "TikTok",
                        sessionStartTime = now - (6 * 60 * 1000),
                        sessionDurationMinutes = 6
                    )
                )
            }
            
            "late_night" -> {
                // Late night usage (sleep impact)
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 30)
                    set(Calendar.SECOND, 0)
                }
                val lateNightTime = calendar.timeInMillis
                
                listOf(
                    generateUsageStats(
                        packageName = "com.zhiliaoapp.musically",
                        displayName = "TikTok",
                        sessionStartTime = lateNightTime,
                        sessionDurationMinutes = 35
                    )
                )
            }
            
            "multiple_sessions" -> {
                // Multiple sessions of same app (escalation pattern)
                listOf(
                    generateUsageStats(
                        packageName = "com.google.android.youtube",
                        displayName = "YouTube",
                        sessionStartTime = now - (120 * 60 * 1000), // 2 hours ago
                        sessionDurationMinutes = 30
                    ),
                    generateUsageStats(
                        packageName = "com.google.android.youtube",
                        displayName = "YouTube",
                        sessionStartTime = now - (70 * 60 * 1000), // 1h 10min ago
                        sessionDurationMinutes = 20
                    ),
                    generateUsageStats(
                        packageName = "com.google.android.youtube",
                        displayName = "YouTube",
                        sessionStartTime = now - (40 * 60 * 1000), // 40min ago
                        sessionDurationMinutes = 40
                    )
                )
            }
            
            else -> emptyList()
        }
    }
    
    /**
     * Generate usage stats for specific time window
     */
    fun generateForTimeWindow(
        packageName: String,
        displayName: String,
        startTime: Long,
        endTime: Long
    ): PatternAgent.AppUsageData {
        val durationMs = endTime - startTime
        val durationMinutes = (durationMs / (60 * 1000)).toInt()
        
        return generateUsageStats(
            packageName = packageName,
            displayName = displayName,
            sessionStartTime = startTime,
            sessionDurationMinutes = durationMinutes
        )
    }
    
    /**
     * Common app package names for testing
     */
    object CommonApps {
        const val YOUTUBE = "com.google.android.youtube"
        const val INSTAGRAM = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val REDDIT = "com.reddit.frontpage"
        const val TWITTER = "com.twitter.android"
        const val FACEBOOK = "com.facebook.katana"
        const val WHATSAPP = "com.whatsapp"
        const val SNAPCHAT = "com.snapchat.android"
        const val CHROME = "com.android.chrome"
        const val SPOTIFY = "com.spotify.music"
    }
    
    /**
     * Get display name from package name
     */
    fun getDisplayName(packageName: String): String = when (packageName) {
        CommonApps.YOUTUBE -> "YouTube"
        CommonApps.INSTAGRAM -> "Instagram"
        CommonApps.TIKTOK -> "TikTok"
        CommonApps.REDDIT -> "Reddit"
        CommonApps.TWITTER -> "Twitter"
        CommonApps.FACEBOOK -> "Facebook"
        CommonApps.WHATSAPP -> "WhatsApp"
        CommonApps.SNAPCHAT -> "Snapchat"
        CommonApps.CHROME -> "Chrome"
        CommonApps.SPOTIFY -> "Spotify"
        else -> packageName.substringAfterLast('.')
    }
}

