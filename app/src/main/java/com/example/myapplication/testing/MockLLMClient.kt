package com.example.myapplication.testing

import android.content.Context
import android.util.Log
import com.example.myapplication.LLMClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Mock LLM client for testing
 * 
 * Features:
 * - Returns canned responses instantly (no API calls)
 * - Can record real responses for replay
 * - Saves responses to app storage
 */
object MockLLMClient : ILLMClient {
    private const val TAG = "MockLLMClient"
    
    var recordMode = false  // If true, calls real API and saves response
    var simulateDelay = true  // Simulate realistic API latency
    
    private val savedResponses = mutableMapOf<String, LLMClient.LLMResponse>()
    
    /**
     * Default canned responses for common scenarios
     */
    private val cannedResponses = mapOf(
        "urgency_5" to LLMClient.LLMResponse(
            content = "Kris... you've been on YouTube for quite a while now. Um, 35 minutes? Maybe it's time for a little break?",
            promptTokens = 150,
            completionTokens = 30,
            totalTokens = 180
        ),
        "urgency_7" to LLMClient.LLMResponse(
            content = "Kris, I asked about this and you didn't listen. I need you to hear me. You've been on this for 50 minutes...",
            promptTokens = 180,
            completionTokens = 35,
            totalTokens = 215
        ),
        "urgency_9" to LLMClient.LLMResponse(
            content = "This is the third time. I'm really worried, Kris. What's going on? This isn't like you...",
            promptTokens = 200,
            completionTokens = 32,
            totalTokens = 232
        ),
        "default" to LLMClient.LLMResponse(
            content = "Hey Kris, I noticed you're spending a lot of time on this app. Everything okay?",
            promptTokens = 120,
            completionTokens = 25,
            totalTokens = 145
        )
    )
    
    override fun callOpenAI(
        context: Context,
        messages: List<LLMClient.Message>,
        model: String,
        temperature: Double,
        topP: Double
    ): LLMClient.LLMResponse? {
        // If recording mode, call real API and save
        if (recordMode) {
            Log.d(TAG, "Recording mode: calling real API...")
            val real = RealLLMClient.callOpenAI(context, messages, model, temperature, topP)
            if (real != null) {
                val key = generateKey(messages)
                savedResponses[key] = real
                saveResponseToFile(context, key, real)
                Log.d(TAG, "Saved real response with key: $key")
            }
            return real
        }
        
        // Simulate API delay for realism
        if (simulateDelay) {
            Thread.sleep(500) // Simulate 0.5s API call
        }
        
        // Try to find saved response
        val key = generateKey(messages)
        val saved = savedResponses[key] ?: loadResponseFromFile(context, key)
        if (saved != null) {
            Log.d(TAG, "Using saved response for key: $key")
            return saved
        }
        
        // Fallback to canned response based on urgency
        val urgency = extractUrgency(messages)
        val responseKey = when {
            urgency >= 9 -> "urgency_9"
            urgency >= 7 -> "urgency_7"
            urgency >= 5 -> "urgency_5"
            else -> "default"
        }
        
        Log.d(TAG, "Using canned response: $responseKey (urgency=$urgency)")
        return cannedResponses[responseKey]
    }
    
    /**
     * Generate unique key for request (for caching)
     */
    private fun generateKey(messages: List<LLMClient.Message>): String {
        // Use hash of user messages (ignoring system prompt variations)
        val userContent = messages.filter { it.role == "user" }
            .joinToString("|") { it.content.take(100) }
        return userContent.hashCode().toString()
    }
    
    /**
     * Extract urgency from messages (look for "URGENCY: X/10" pattern)
     */
    private fun extractUrgency(messages: List<LLMClient.Message>): Int {
        val urgencyRegex = Regex("""URGENCY:\s*(\d+)/10""")
        for (msg in messages) {
            val match = urgencyRegex.find(msg.content)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }
    
    /**
     * Save response to JSON file
     */
    private fun saveResponseToFile(context: Context, key: String, response: LLMClient.LLMResponse) {
        try {
            val dir = File(context.filesDir, "mock_responses")
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, "$key.json")
            val json = JSONObject().apply {
                put("content", response.content)
                put("promptTokens", response.promptTokens)
                put("completionTokens", response.completionTokens)
                put("totalTokens", response.totalTokens)
            }
            
            file.writeText(json.toString())
            Log.d(TAG, "Saved response to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save response", e)
        }
    }
    
    /**
     * Load response from JSON file
     */
    private fun loadResponseFromFile(context: Context, key: String): LLMClient.LLMResponse? {
        try {
            val file = File(context.filesDir, "mock_responses/$key.json")
            if (!file.exists()) return null
            
            val json = JSONObject(file.readText())
            return LLMClient.LLMResponse(
                content = json.getString("content"),
                promptTokens = json.optInt("promptTokens"),
                completionTokens = json.optInt("completionTokens"),
                totalTokens = json.optInt("totalTokens")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load response", e)
            return null
        }
    }
    
    /**
     * Clear all saved responses
     */
    fun clearSavedResponses(context: Context) {
        savedResponses.clear()
        val dir = File(context.filesDir, "mock_responses")
        dir.deleteRecursively()
        Log.d(TAG, "Cleared all saved responses")
    }
    
    /**
     * Add custom canned response
     */
    fun addCannedResponse(key: String, response: LLMClient.LLMResponse) {
        savedResponses[key] = response
        Log.d(TAG, "Added canned response: $key")
    }
}

