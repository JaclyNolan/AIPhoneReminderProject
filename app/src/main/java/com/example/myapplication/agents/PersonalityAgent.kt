package com.example.myapplication.agents

import android.content.Context
import android.util.Log
import com.example.myapplication.CharacterProfiles
import com.example.myapplication.LLMClient
import com.example.myapplication.context.MemoryContextProvider
import com.example.myapplication.context.UserPrefsContextProvider
import com.example.myapplication.testing.LLMClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// Import from same package (agents)
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.agents.PatternAgent

/**
 * PersonalityAgent: Generates character-aware responses to pattern violations
 * 
 * Takes PatternViolation from PatternAgent and generates a personalized response
 * using the active character's personality profile. Uses LLMClient (or MockLLMClient 
 * in test mode) for API calls and CharacterProfiles for character consistency.
 * 
 * Now uses modular context providers:
 * - MemoryContextProvider for chat history
 * - UserPrefsContextProvider for character profile
 */
object PersonalityAgent {
    private const val TAG = "PersonalityAgent"
    
    // Date format for parsing ChatMessage timestamps
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    /**
     * Generate a character-aware response to a pattern violation
     * 
     * @param context Application context
     * @param characterId Character identifier (e.g., "ralsei")
     * @param violation Pattern violation detected by PatternAgent
     * @return Generated response text from the character
     */
    suspend fun respondToPattern(
        context: Context,
        characterId: String,
        violation: PatternAgent.PatternViolation
    ): String = withContext(Dispatchers.IO) {
        try {
            // Use context providers for data access
            val profile = UserPrefsContextProvider.getCharacterProfile(context)
            val chatHistory = MemoryContextProvider.getChatHistory(limitMessages = 10)
            
            // Find closest urgency example for tone reference
            val exampleResponse = profile.urgencyExamples.entries
                .minByOrNull { kotlin.math.abs(it.key - violation.urgency) }?.value
            
            // Urgency guidance for LLM
            val urgencyGuidance = when (violation.urgency) {
                in 0..3 -> "This is a casual observation. Just noticing what's happening."
                in 4..6 -> "This is concerning. Express worry in your way. Make it clear you're paying attention."
                in 7..8 -> "This is serious. Be firmer now. Show real concern in your character's voice."
                else -> "This is critical. You're genuinely worried. Kris needs to understand this is serious."
            }
            
            // Build system prompt with character personality
            val characterPrompt = CharacterProfiles.buildCharacterPrompt(characterId)
            
            // Build conversation context from recent chat history
            val recentConversation = chatHistory
                .takeLast(5)
                .joinToString("\n") { msg ->
                    val timeAgo = formatTimeAgo(parseTimestampToMillis(msg.timestamp))
                    "[$timeAgo] ${msg.role}: ${msg.text.take(200)}"  // Truncate long messages
                }
            
            // Build user prompt with violation context
            val userPrompt = buildString {
                appendLine("THE SITUATION:")
                appendLine(violation.context)
                appendLine()
                appendLine("URGENCY: ${violation.urgency}/10")
                appendLine(urgencyGuidance)
                appendLine()
                
                if (exampleResponse != null) {
                    appendLine("TONE REFERENCE:")
                    appendLine("\"$exampleResponse\"")
                    appendLine("Match this energy level, but respond to the current specific situation.")
                }
                appendLine()
                
                if (recentConversation.isNotEmpty()) {
                    appendLine("RECENT CONVERSATION:")
                    appendLine(recentConversation)
                    appendLine()
                }
                
                appendLine("Respond naturally as ${profile.name}. Let the urgency affect how concerned you are, but stay true to your personality.")
                appendLine("Keep your response concise - 2-4 sentences max.")
            }
            
            Log.d(TAG, "Generating response for violation: urgency=${violation.urgency}, app=${violation.appName}")
            
            // Build messages for LLM
            val messages = listOf(
                LLMClient.Message("system", characterPrompt),
                LLMClient.Message("user", userPrompt)
            )
            
            // Call LLM API (uses factory to support mock mode)
            val client = LLMClientFactory.getClient()
            val response = client.callOpenAI(context, messages)
            
            if (response == null || response.content.isBlank()) {
                Log.w(TAG, "LLM API returned empty response, using fallback")
                // Fallback response based on urgency
                val fallback = when {
                    violation.urgency >= 7 -> "Kris, I'm really worried. You've been on ${violation.appName} for a while now..."
                    violation.urgency >= 4 -> "Hey Kris, you've been on ${violation.appName} for quite a while. Everything okay?"
                    else -> "Kris, I noticed you're on ${violation.appName}. What are you up to?"
                }
                return@withContext fallback
            }
            
            val result = response.content.trim()
            
            // Save to chat history with warning source and urgency
            ChatManager.addAssistantMessage(
                context,
                result,
                source = "warning",
                urgency = violation.urgency,
                characterId = characterId
            )
            
            Log.d(TAG, "Generated warning response: ${result.take(100)}...")
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response to pattern violation", e)
            return@withContext "Kris, I'm worried about how much time you're spending on ${violation.appName}..."
        }
    }
    
    /**
     * Parse timestamp string to milliseconds
     * Handles both ISO format (from SceneTimeline) and simple format (from ChatMessage)
     */
    private fun parseTimestampToMillis(timestamp: String): Long {
        return try {
            // Try simple format first (ChatMessage format)
            dateFormat.parse(timestamp)?.time ?: run {
                // Fallback to ISO format
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                isoFormat.parse(timestamp)?.time ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse timestamp: $timestamp", e)
            System.currentTimeMillis()
        }
    }
    
    /**
     * Format time ago string
     */
    private fun formatTimeAgo(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            else -> "${minutes / 60}h ago"
        }
    }
}

