package com.example.myapplication.testing.mockcontext

import android.content.Context
import android.util.Log
import com.example.myapplication.agents.ChatManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mock ChatHistoryContextProvider for testing
 */
object MockChatHistoryContextProvider {
    private const val TAG = "MockChatHistoryContextProvider"
    
    private var mockMessages: List<ChatManager.ChatMessage> = emptyList()
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    /**
     * Set mock chat history
     */
    fun setMockHistory(messages: List<ChatManager.ChatMessage>) {
        mockMessages = messages
        Log.d(TAG, "Set ${messages.size} mock chat messages")
    }
    
    /**
     * Get mock chat history (formatted)
     */
    fun getChatHistory(
        context: Context,
        maxMessages: Int = 20,
        maxSummaries: Int = 3
    ): String {
        val recentMessages = mockMessages
            .filter { it.source == "user" || it.source == "assistant" }
            .takeLast(maxMessages)
        
        val recentSummaries = mockMessages
            .filter { it.source == "commentary" }
            .takeLast(maxSummaries)
        
        return buildString {
            if (recentMessages.isNotEmpty()) {
                appendLine("=== RECENT CONVERSATION (MOCK) ===")
                recentMessages.forEach { msg ->
                    val role = when (msg.source) {
                        "user" -> "user"
                        "assistant" -> "assistant"
                        else -> "system"
                    }
                    appendLine("[${msg.timestamp}] $role: ${msg.text.take(300)}")
                }
                appendLine()
            }
            
            if (recentSummaries.isNotEmpty()) {
                appendLine("=== RECENT OBSERVATIONS (MOCK) ===")
                recentSummaries.forEach { summary ->
                    appendLine("[${summary.timestamp}] ${summary.text.take(200)}")
                }
            }
        }
    }
    
    /**
     * Get raw mock messages
     */
    fun getRawMessages(context: Context): List<ChatManager.ChatMessage> {
        return mockMessages
    }
    
    /**
     * Add a mock warning message (for escalation testing)
     */
    fun addMockWarning(
        content: String,
        urgency: Int = 5,
        minutesAgo: Int = 30
    ) {
        val timestamp = dateFormat.format(Date(System.currentTimeMillis() - (minutesAgo * 60 * 1000)))
        val message = ChatManager.ChatMessage(
            role = "assistant",
            text = content,
            timestamp = timestamp,
            source = "warning",
            urgency = urgency,
            characterId = "ralsei"
        )
        mockMessages = mockMessages + message
        Log.d(TAG, "Added mock warning: urgency=$urgency")
    }
    
    /**
     * Get current mock data for display
     */
    fun getCurrentMockData(): List<ChatManager.ChatMessage> {
        return mockMessages
    }
    
    /**
     * Set default scenario chat history
     */
    fun setDefaultScenario(scenarioType: String) {
        val now = System.currentTimeMillis()
        mockMessages = when (scenarioType) {
            "youtube_long_session" -> {
                listOf(
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Hey Kris, I noticed you've been on YouTube for a while. Everything okay?",
                        timestamp = dateFormat.format(Date(now - (30 * 60 * 1000))),
                        source = "warning",
                        urgency = 5,
                        characterId = "ralsei"
                    )
                )
            }
            "tiktok_late_night" -> {
                emptyList() // First offense
            }
            "critical_binge" -> {
                // 3 escalating warnings
                listOf(
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Hey Kris, you've been watching YouTube for a while now. Want to take a break?",
                        timestamp = dateFormat.format(Date(now - (60 * 60 * 1000))),
                        source = "warning",
                        urgency = 5,
                        characterId = "ralsei"
                    ),
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Kris, you're still watching? It's been over an hour. This isn't good for you.",
                        timestamp = dateFormat.format(Date(now - (30 * 60 * 1000))),
                        source = "warning",
                        urgency = 7,
                        characterId = "ralsei"
                    ),
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Kris, please stop. You've been on YouTube for 90 minutes. This is really concerning.",
                        timestamp = dateFormat.format(Date(now - (5 * 60 * 1000))),
                        source = "warning",
                        urgency = 9,
                        characterId = "ralsei"
                    )
                )
            }
            "repeat_offender" -> {
                // Multiple ignored warnings
                listOf(
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Hey Kris, I noticed you've been on YouTube for a while.",
                        timestamp = dateFormat.format(Date(now - (50 * 60 * 1000))),
                        source = "warning",
                        urgency = 5,
                        characterId = "ralsei"
                    ),
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Kris, you're still watching. I already warned you about this.",
                        timestamp = dateFormat.format(Date(now - (30 * 60 * 1000))),
                        source = "warning",
                        urgency = 6,
                        characterId = "ralsei"
                    ),
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Kris, please listen. You've ignored my warnings twice now. This needs to stop.",
                        timestamp = dateFormat.format(Date(now - (10 * 60 * 1000))),
                        source = "warning",
                        urgency = 8,
                        characterId = "ralsei"
                    )
                )
            }
            "extended_morning", "app_hopping_binge", "multiple_sessions_same_app", 
            "weekend_binge", "low_battery_usage", "work_vs_leisure", "relapse_pattern" -> {
                // First or second warning depending on scenario
                when (scenarioType) {
                    "relapse_pattern" -> {
                        listOf(
                            ChatManager.ChatMessage(
                                role = "assistant",
                                text = "Hey Kris, you were on YouTube earlier and now you're back. Everything okay?",
                                timestamp = dateFormat.format(Date(now - (50 * 60 * 1000))),
                                source = "warning",
                                urgency = 5,
                                characterId = "ralsei"
                            )
                        )
                    }
                    "multiple_sessions_same_app" -> {
                        listOf(
                            ChatManager.ChatMessage(
                                role = "assistant",
                                text = "Kris, this is your third YouTube session today. That's a lot.",
                                timestamp = dateFormat.format(Date(now - (20 * 60 * 1000))),
                                source = "warning",
                                urgency = 6,
                                characterId = "ralsei"
                            )
                        )
                    }
                    else -> {
                        listOf(
                            ChatManager.ChatMessage(
                                role = "assistant",
                                text = "Hey Kris, I noticed some concerning usage patterns. Want to talk about it?",
                                timestamp = dateFormat.format(Date(now - (30 * 60 * 1000))),
                                source = "warning",
                                urgency = 5,
                                characterId = "ralsei"
                            )
                        )
                    }
                }
            }
            "late_night_escalation" -> {
                // Multiple warnings, late night
                listOf(
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Kris, it's getting late and you're still on TikTok.",
                        timestamp = dateFormat.format(Date(now - (60 * 60 * 1000))),
                        source = "warning",
                        urgency = 6,
                        characterId = "ralsei"
                    ),
                    ChatManager.ChatMessage(
                        role = "assistant",
                        text = "Kris, it's past midnight and you're still scrolling. Please stop.",
                        timestamp = dateFormat.format(Date(now - (20 * 60 * 1000))),
                        source = "warning",
                        urgency = 8,
                        characterId = "ralsei"
                    )
                )
            }
            "minimal_usage" -> {
                emptyList() // No warnings for minimal usage
            }
            else -> emptyList()
        }
    }
    
    /**
     * Get condensed chat history (matching real provider format)
     */
    fun getCondensedChatHistory(
        context: Context,
        maxPairs: Int = 10
    ): String {
        // Filter to only user chat and assistant messages
        val filteredMessages = mockMessages.filter { msg ->
            (msg.role == "user" && msg.source == "user_chat") || 
            msg.role == "assistant"
        }
        
        val recentMessages = filteredMessages.takeLast(maxPairs * 2)
        
        if (recentMessages.isEmpty()) {
            return ""
        }
        
        return buildString {
            appendLine("=== CONVERSATION (MOCK) ===")
            
            var i = 0
            while (i < recentMessages.size) {
                val msg = recentMessages[i]
                
                if (msg.role == "user" && msg.source == "user_chat") {
                    appendLine("[${msg.timestamp}] User: ${msg.text}")
                    appendLine()
                    
                    if (i + 1 < recentMessages.size && recentMessages[i + 1].role == "assistant") {
                        val assistantMsg = recentMessages[i + 1]
                        appendLine("[${assistantMsg.timestamp}] Assistant:")
                        appendLine("  Response: ${assistantMsg.text}")
                        appendLine()
                        i += 2
                    } else {
                        i += 1
                    }
                } else if (msg.role == "assistant") {
                    appendLine("[${msg.timestamp}] Assistant:")
                    appendLine("  Response: ${msg.text}")
                    appendLine()
                    i += 1
                } else {
                    i += 1
                }
            }
        }
    }
    
    /**
     * Reset to default state
     */
    fun reset() {
        mockMessages = emptyList()
    }
}

