package com.example.myapplication

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * LLMClient: Shared API client for OpenAI/Mistral API calls
 * 
 * Extracted from ChatManager to enable reuse by PersonalityAgent and other components.
 * Handles:
 * - API key/endpoint retrieval
 * - HTTP request/response
 * - Response parsing (Mistral Chat Completions format)
 * - Token usage extraction
 * - Markdown code block stripping
 */
object LLMClient {
    private const val TAG = "LLMClient"
    
    /**
     * Message structure for API requests
     */
    data class Message(
        val role: String,  // "system", "user", "assistant"
        val content: String
    )
    
    /**
     * Response structure with content and token usage
     */
    data class LLMResponse(
        val content: String,
        val promptTokens: Int? = null,
        val completionTokens: Int? = null,
        val totalTokens: Int? = null
    )
    
    /**
     * Call the LLM API with a list of messages
     * 
     * Note: This is a blocking call. Call from a background thread or coroutine.
     * 
     * @param context Application context
     * @param messages List of messages (system, user, assistant roles)
     * @param model Model name (default: "mistral-medium-latest")
     * @param temperature Temperature setting (default: 0.9)
     * @param topP Top-p setting (default: 0.9)
     * @return LLMResponse with content and token usage, or null if error
     */
    fun callOpenAI(
        context: Context,
        messages: List<Message>,
        model: String = "mistral-medium-latest",
        temperature: Double = 0.9,
        topP: Double = 0.9
    ): LLMResponse? {
        try {
            // Get API key and endpoint
            val prefs = PrefsHelper(context)
            var apiKey = prefs.getOpenAIApiKey()?.takeIf { it.isNotBlank() }
            if (apiKey.isNullOrBlank()) {
                val envKey = EnvLoader.getOpenAIApiKey(context)
                if (!envKey.isNullOrBlank()) apiKey = envKey
            }
            if (apiKey.isNullOrBlank()) {
                Log.w(TAG, "OpenAI API key not set; skipping API call")
                return null
            }
            val endpoint = prefs.getOpenAIEndpoint()
            
            // Build request JSON
            val messagesArray = JSONArray()
            for (msg in messages) {
                val msgObj = JSONObject()
                msgObj.put("role", msg.role)
                msgObj.put("content", msg.content)
                messagesArray.put(msgObj)
            }
            
            val requestJson = JSONObject()
            requestJson.put("model", model)
            requestJson.put("temperature", temperature)
            requestJson.put("top_p", topP)
            requestJson.put("messages", messagesArray)
            
            Log.d(TAG, "LLM API call: model=$model, messages=${messages.size}")
            
            val payload = requestJson.toString().toByteArray(Charsets.UTF_8)
            val requestJsonString = requestJson.toString()
            
            // Make HTTP request
            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    doOutput = true
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 60_000
                    readTimeout = 60_000
                }
                connection.outputStream.use { it.write(payload) }
                val code = connection.responseCode
                val respStream = if (code in 200..299) connection.inputStream else connection.errorStream
                val respBytes = respStream?.use { readAllBytes(it) }
                val respText = respBytes?.let { String(it) } ?: ""
                
                Log.d(TAG, "LLM API response code=$code, body=${respText.take(500)}")
                
                if (respText.isNotBlank() && code in 200..299) {
                    // Parse Mistral Chat Completions response format
                    val json = JSONObject(respText)
                    if (json.has("choices")) {
                        val choicesArr = json.getJSONArray("choices")
                        if (choicesArr.length() > 0) {
                            val firstChoice = choicesArr.getJSONObject(0)
                            if (firstChoice.has("message")) {
                                val message = firstChoice.getJSONObject("message")
                                val rawResult = message.optString("content", "").trim()
                                
                                if (rawResult.isNotEmpty()) {
                                    // Strip markdown code blocks
                                    val content = stripMarkdownCodeBlocks(rawResult)
                                    
                                    // Extract token usage
                                    val usageObj = json.optJSONObject("usage")
                                    val promptTokens = usageObj?.optInt("prompt_tokens", -1)?.takeIf { it >= 0 }
                                    val completionTokens = usageObj?.optInt("completion_tokens", -1)?.takeIf { it >= 0 }
                                    val totalTokens = usageObj?.optInt("total_tokens", -1)?.takeIf { it >= 0 }
                                    
                                    return LLMResponse(
                                        content = content,
                                        promptTokens = promptTokens,
                                        completionTokens = completionTokens,
                                        totalTokens = totalTokens
                                    )
                                }
                            }
                        }
                    }
                    Log.w(TAG, "Unexpected response format: $respText")
                } else {
                    Log.w(TAG, "API call failed with code $code: $respText")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error making HTTP request", e)
            } finally {
                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in callOpenAI", e)
        }
        
        return null
    }
    
    /**
     * Strip markdown code blocks from response
     * Handles formats like: ```json\n{...}\n``` or ```\n{...}\n```
     */
    private fun stripMarkdownCodeBlocks(text: String): String {
        var cleaned = text.trim()
        
        // Remove opening code block markers (```json or ```)
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").trim()
        }
        
        // Remove closing code block marker
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trim()
        }
        
        return cleaned
    }
    
    /**
     * Read all bytes from input stream
     */
    private fun readAllBytes(input: java.io.InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val data = ByteArray(4 * 1024)
        val bis = java.io.BufferedInputStream(input)
        var n: Int
        while (bis.read(data).also { n = it } != -1) {
            buffer.write(data, 0, n)
        }
        return buffer.toByteArray()
    }
}

