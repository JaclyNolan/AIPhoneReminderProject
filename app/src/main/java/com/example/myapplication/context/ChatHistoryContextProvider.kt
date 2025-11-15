package com.example.myapplication.context

import android.content.Context
import android.util.Log
import com.example.myapplication.agents.ChatManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * ChatHistoryContextProvider: Provides token-efficient chat history for LLM prompts
 * 
 * Single responsibility: Extract and format chat history optimized for LLM consumption
 * Used by PersonalityAgent for decision-making with conversation context
 */
object ChatHistoryContextProvider {
    private const val TAG = "ChatHistoryContextProvider"
    
    /**
     * Get optimized chat history for LLM prompts
     * 
     * @param context Application context
     * @param maxMessages Maximum user/assistant messages (default: 20)
     * @param maxSummaries Maximum analyzer summaries to include (default: 3)
     * @return Formatted string optimized for LLM input
     */
    fun getChatHistory(
        context: Context,
        maxMessages: Int = 20,
        maxSummaries: Int = 3
    ): String {
        try {
            val history = ChatManager.getHistorySnapshot()
            
            // Get recent user/assistant messages
            val recentMessages = history
                .filter { it.source == "user" || it.source == "assistant" }
                .takeLast(maxMessages)
            
            // Get recent analyzer summaries (commentary)
            val recentSummaries = history
                .filter { it.source == "commentary" }
                .takeLast(maxSummaries)
            
            return buildString {
                if (recentMessages.isNotEmpty()) {
                    appendLine("=== RECENT CONVERSATION ===")
                    recentMessages.forEach { msg ->
                        val role = when (msg.source) {
                            "user" -> "user"
                            "assistant" -> "assistant"
                            else -> "system"
                        }
                        appendLine("[${msg.timestamp}] $role: ${msg.text.take(300)}") // Truncate long messages
                    }
                    appendLine()
                }
                
                if (recentSummaries.isNotEmpty()) {
                    appendLine("=== RECENT OBSERVATIONS ===")
                    recentSummaries.forEach { summary ->
                        appendLine("[${summary.timestamp}] ${summary.text.take(200)}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat history", e)
            return ""
        }
    }
    
    /**
     * Get raw chat messages for other processors
     * 
     * @param context Application context
     * @return List of chat messages
     */
    fun getRawMessages(context: Context): List<ChatManager.ChatMessage> {
        return ChatManager.getHistorySnapshot()
    }
    
    /**
     * Get chat history filtered by source
     * 
     * @param context Application context
     * @param source Message source ("commentary", "warning", "user", "assistant")
     * @param limit Optional limit on number of messages
     * @return Filtered list of messages
     */
    fun getChatHistoryBySource(
        context: Context,
        source: String,
        limit: Int? = null
    ): List<ChatManager.ChatMessage> {
        val filtered = ChatManager.getHistorySnapshot().filter { it.source == source }
        return if (limit != null) {
            filtered.takeLast(limit)
        } else {
            filtered
        }
    }
    
    /**
     * Get condensed chat history with thinking fields extracted from structured responses
     * 
     * Only includes user chat messages and assistant responses with thinking/text fields.
     * 
     * @param context Application context
     * @param maxPairs Maximum user-assistant conversation pairs (default: 10)
     * @return Formatted string with conversation pairs and thinking context
     */
    fun getCondensedChatHistory(
        context: Context,
        maxPairs: Int = 10
    ): String {
        try {
            val history = ChatManager.getHistorySnapshot()
            
            // Filter to only user chat and assistant messages
            val filteredMessages = history.filter { msg ->
                (msg.role == "user" && msg.source == "user_chat") || 
                msg.role == "assistant"
            }
            
            // Take last N pairs (each pair = user message + assistant response)
            val recentMessages = filteredMessages.takeLast(maxPairs * 2)
            
            if (recentMessages.isEmpty()) {
                return ""
            }
            
            return buildString {
                appendLine("=== CONVERSATION ===")
                
                var i = 0
                while (i < recentMessages.size) {
                    val msg = recentMessages[i]
                    
                    if (msg.role == "user" && msg.source == "user_chat") {
                        // User message
                        appendLine("[${msg.timestamp}] User: ${msg.text}")
                        appendLine()
                        
                        // Look for next assistant response
                        if (i + 1 < recentMessages.size && recentMessages[i + 1].role == "assistant") {
                            val assistantMsg = recentMessages[i + 1]
                            
                            // Parse structured response if available
                            val parsedResponse = parseStructuredResponse(assistantMsg.text)
                            if (parsedResponse != null && parsedResponse.isNotEmpty()) {
                                // Only show assistant messages that have actual content (not null responses)
                                appendLine("[${assistantMsg.timestamp}] Assistant:")
                                parsedResponse.forEach { item ->
                                    if (item.thinking.isNotBlank()) {
                                        appendLine("  Thinking: ${item.thinking}")
                                    }
                                    if (item.text.isNotBlank()) {
                                        appendLine("  Response: ${item.text}")
                                    }
                                }
                                appendLine()
                            }
                            // If parsedResponse is null, it means response was null in JSON - skip showing it
                            i += 2 // Skip both user and assistant messages
                        } else {
                            i += 1
                        }
                    } else if (msg.role == "assistant") {
                        // Standalone assistant message (no preceding user message)
                        val parsedResponse = parseStructuredResponse(msg.text)
                        if (parsedResponse != null && parsedResponse.isNotEmpty()) {
                            // Only show assistant messages that have actual content
                            appendLine("[${msg.timestamp}] Assistant:")
                            parsedResponse.forEach { item ->
                                if (item.thinking.isNotBlank()) {
                                    appendLine("  Thinking: ${item.thinking}")
                                }
                                if (item.text.isNotBlank()) {
                                    appendLine("  Response: ${item.text}")
                                }
                            }
                            appendLine()
                        }
                        // If parsedResponse is null, skip showing this message (it was a "no response" decision)
                        i += 1
                    } else {
                        i += 1
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting condensed chat history", e)
            return ""
        }
    }
    
    /**
     * Parse structured JSON response and extract thinking/text fields
     * 
     * @param responseText Assistant message text (may be JSON or plain text)
     * @return List of ResponseItem with thinking and text, or null if not structured JSON or response is null
     */
    private fun parseStructuredResponse(responseText: String?): List<ResponseItem>? {
        if (responseText.isNullOrBlank()) return null
        
        try {
            val trimmed = responseText.trim()
            val obj = JSONObject(trimmed)
            
            // Check if this is a structured response with a "response" field
            if (!obj.has("response")) {
                // No response field - might be plain text or different structure
                return null
            }
            
            val responseField = obj.opt("response")
            
            // If response is null or JSONObject.NULL, this is a "no response" decision - skip it
            if (responseField == null || responseField == JSONObject.NULL) {
                return null
            }
            
            // Check if response is an array
            if (responseField is JSONArray) {
                val responseArray = responseField
                
                if (responseArray.length() > 0) {
                    val items = mutableListOf<ResponseItem>()
                    for (i in 0 until responseArray.length()) {
                        val item = responseArray.optJSONObject(i) ?: continue
                        val thinking = item.optString("thinking", "")
                        val text = item.optString("text", "")
                        
                        if (thinking.isNotBlank() || text.isNotBlank()) {
                            items.add(ResponseItem(thinking, text))
                        }
                    }
                    return items.takeIf { it.isNotEmpty() }
                }
            }
            
            // If response is a string (backward compatibility), return it as plain text
            if (responseField is String) {
                return listOf(ResponseItem("", responseField))
            }
        } catch (e: Exception) {
            // Not valid JSON or doesn't have expected structure - return null for plain text
        }
        
        return null
    }
    
    /**
     * Data class for parsed response items
     */
    private data class ResponseItem(
        val thinking: String,
        val text: String
    )
}







