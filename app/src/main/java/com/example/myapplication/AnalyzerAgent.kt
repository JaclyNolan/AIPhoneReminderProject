package com.example.myapplication

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AnalyzerAgent: Processes batches of 3 screenshots (6s window) with structured output.
 * Outputs: batch_summary, frames[], justification, confidence, safety_flags
 */
object AnalyzerAgent {
    private const val TAG = "AnalyzerAgent"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = mutableListOf<ByteArray>()
    private val flushing = AtomicBoolean(false)

    private const val ANALYZER_SYSTEM_PROMPT = """
You are the Screenshot Analyzer. Analyze 3 consecutive screenshots and extract maximum context with minimal output tokens.

CRITICAL RULES:
1. Return ONLY valid JSON (no markdown, no extra text)
2. Use EXACT field names and structure below
3. Compress text: use short phrases, skip articles/connectors
4. Report raw confidence (0.0-1.0), not percentage

CONTEXT EXTRACTION:
- Compare frame transitions to detect: scrolling, typing, reading, switching apps, idle
- Infer user mood/state from: activity type, pace, content consumed
- Identify app + primary activity + velocity

OUTPUT FORMAT (strip all markdown before JSON):
{
  "tick_id": "t_YYYYMMDD_HHMMSS",
  "ts": "ISO8601",
  "batch": {
    "scene": "app_name",
    "activity": "scrolling|typing|reading|idle|switching",
    "confidence": 0.0-1.0,
    "velocity": "slow|medium|fast",
    "mood": "neutral|positive|negative|mixed"
  },
  "context": {
    "summary": "1-2 sentence description of what user is doing across all 3 screenshots",
    "confidence": 0.0-1.0
  }
}

EXAMPLES:
Frame 1-3: Gmail inbox scrolling then reading email
→ {"tick_id":"t_20251028_143022", "ts":"2025-10-28T14:30:22Z", "batch":{"scene":"Gmail","activity":"reading","confidence":0.95,"velocity":"slow","mood":"neutral"}, "context":{"summary":"User browsing work emails, opened message about project deadline","confidence":0.9}}

Frame 1-3: TikTok feed, rapid swiping
→ {"tick_id":"t_20251028_143025", "ts":"2025-10-28T14:30:25Z", "batch":{"scene":"TikTok","activity":"scrolling","confidence":0.92,"velocity":"fast","mood":"mixed"}, "context":{"summary":"User rapidly scrolling through entertainment videos, spending 2-3 seconds per video","confidence":0.88}}

Frame 1-3: Settings menu, navigating WiFi
→ {"tick_id":"t_20251028_143028", "ts":"2025-10-28T14:30:28Z", "batch":{"scene":"Settings","activity":"switching","confidence":0.87,"velocity":"medium","mood":"neutral"}, "context":{"summary":"User troubleshooting network connection in WiFi settings","confidence":0.85}}
"""

    fun enqueueImageBytes(ctx: Context, imageBytes: ByteArray) {
        scope.launch {
            synchronized(queue) {
                queue.add(imageBytes)
            }
            maybeFlush(ctx)
        }
    }

    private fun queueSize(): Int = synchronized(queue) { queue.size }

    private fun maybeFlush(ctx: Context) {
        scope.launch {
            // Always batch 3 frames
            if (queueSize() >= 3) {
                flushBatch(ctx)
            }
        }
    }

    private fun flushBatch(ctx: Context) {
        if (!flushing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val listToSend = mutableListOf<ByteArray>()
                synchronized(queue) {
                    val take = minOf(3, queue.size)
                    repeat(take) { listToSend.add(queue.removeAt(0)) }
                }
                if (listToSend.isEmpty()) return@launch

                val prefs = PrefsHelper(ctx)
                var apiKey = prefs.getOpenAIApiKey()?.takeIf { it.isNotBlank() }
                if (apiKey.isNullOrBlank()) {
                    apiKey = EnvLoader.getOpenAIApiKey(ctx)
                }
                if (apiKey.isNullOrBlank()) {
                    Log.w(TAG, "API key not set; skipping analysis")
                    return@launch
                }
                val endpoint = prefs.getOpenAIEndpoint()

                sendBatchAsJson(ctx, listToSend, endpoint, apiKey)
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing batch", e)
            } finally {
                flushing.set(false)
            }
        }
    }

    private fun sendBatchAsJson(ctx: Context, imageByteList: List<ByteArray>, endpoint: String, apiKey: String) {
        var connection: HttpURLConnection? = null

        // Pause screenshots before making API request
        ScreenshotPauseController.requestPause(ctx, "AnalyzerAgent")

        try {
            // Initialize EnhancedMemoryManager before using
            EnhancedMemoryManager.initialize(ctx)

            val timelineBuffer = EnhancedMemoryManager.getTimelineBuffer(ctx, 5)
            val recentMemories = EnhancedMemoryManager.getRecentCondensedMemorySummary(ctx, 3)

            val contextBuilder = StringBuilder()
            contextBuilder.append("RECENT_TIMELINE:\n")
            for (entry in timelineBuffer) {
                contextBuilder.append("- [${entry.timestamp}] ${entry.sceneLabel}: ${entry.shortText}\n")
            }
            if (recentMemories.isNotEmpty()) {
                contextBuilder.append("\nRECENT_MEMORIES:\n")
                contextBuilder.append(recentMemories)
            }
            val contextText = contextBuilder.toString().take(8000)

            // Build Mistral Chat Completions format
            val messagesArray = JSONArray()

            // System message with prompt and context
            val systemContent = StringBuilder()
            systemContent.append(ANALYZER_SYSTEM_PROMPT)
            if (contextText.isNotBlank()) {
                systemContent.append("\n\n")
                systemContent.append(contextText)
            }

            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", systemContent.toString())
            messagesArray.put(systemMsg)

            // User message with images
            val userMsg = JSONObject()
            userMsg.put("role", "user")

            val contentArray = JSONArray()

            // Add text instruction
            val textContent = JSONObject()
            textContent.put("type", "text")
            textContent.put("text", "Analyze these ${imageByteList.size} screenshots and return the structured JSON response.")
            contentArray.put(textContent)

            // Add images
            for (bytes in imageByteList) {
                val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val imageContent = JSONObject()
                imageContent.put("type", "image_url")
                val imageUrl = JSONObject()
                imageUrl.put("url", "data:image/jpeg;base64,$base64Image")
                imageContent.put("image_url", imageUrl)
                contentArray.put(imageContent)
            }

            userMsg.put("content", contentArray)
            messagesArray.put(userMsg)

            val requestJson = JSONObject()
            requestJson.put("model", "mistral-small-latest")  // Mistral's small model for vision analysis
            requestJson.put("temperature", 0.6)
            requestJson.put("messages", messagesArray)

            val payload = requestJson.toString().toByteArray(Charsets.UTF_8)
            val requestJsonString = requestJson.toString()
            Log.d(TAG, "Analyzer request size: ${payload.size} bytes")

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

            if (respText.isNotBlank()) {
                val json = JSONObject(respText)
                var structured: JSONObject? = null

                // Parse Mistral Chat Completions response format
                if (json.has("choices")) {
                    val choicesArr = json.getJSONArray("choices")
                    if (choicesArr.length() > 0) {
                        val firstChoice = choicesArr.getJSONObject(0)
                        if (firstChoice.has("message")) {
                            val message = firstChoice.getJSONObject("message")
                            val content = message.optString("content", "")
                            if (content.isNotBlank()) {
                                // Strip markdown code blocks before parsing JSON
                                val cleanedContent = stripMarkdownCodeBlocks(content)
                                structured = JSONObject(cleanedContent)
                            }
                        }
                    }
                }

                if (structured != null) {
                    // Log the request and response to ResponseLogger
                    // Extract token usage from Mistral response
                    val usageObj = json.optJSONObject("usage")
                    val promptTokens = usageObj?.optInt("prompt_tokens", -1)?.takeIf { it >= 0 }
                    val completionTokens = usageObj?.optInt("completion_tokens", -1)?.takeIf { it >= 0 }
                    val totalTokens = usageObj?.optInt("total_tokens", -1)?.takeIf { it >= 0 }

                    if (promptTokens != null || completionTokens != null || totalTokens != null) {
                        ResponseLogger.logResponse(ctx, requestJsonString, structured.toString(), ResponseLogger.LogType.ANALYZER_AGENT,
                            promptTokens, completionTokens, totalTokens)
                    } else {
                        ResponseLogger.logResponse(ctx, requestJsonString, structured.toString(), ResponseLogger.LogType.ANALYZER_AGENT)
                    }

                    processAnalyzerResponse(ctx, structured)
                } else {
                    Log.w(TAG, "No structured JSON found in analyzer output")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in analyzer", e)
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
            // Resume screenshots after API request completes (success or failure)
            ScreenshotPauseController.requestResume(ctx, "AnalyzerAgent")
        }
    }

    private fun processAnalyzerResponse(ctx: Context, response: JSONObject) {
        try {
            val tickId = response.optString("tick_id", "")
            val ts = response.optString("ts", "")
            val batch = response.optJSONObject("batch")
            val context = response.optJSONObject("context")

            Log.d(TAG, "Analyzer result: tickId=$tickId")

            if (batch != null) {
                val scene = batch.optString("scene", "unknown")
                val activity = batch.optString("activity", "unknown")
                val confidence = batch.optDouble("confidence", 0.5)
                val contextSummary = context?.optString("summary", "") ?: ""

                // Relevance gate check
                val relevanceScore = confidence * 0.7 + 0.3
                if (relevanceScore >= 0.4) {
                    // Add to SceneTimeline with context summary
                    val sceneEntry = EnhancedMemoryManager.SceneTimelineEntry(
                        timestamp = ts.ifEmpty { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()) },
                        sceneLabel = scene,
                        shortText = if (contextSummary.isNotBlank()) contextSummary else "$activity",
                        confidence = confidence
                    )
                    EnhancedMemoryManager.addSceneTimelineEntry(ctx, sceneEntry)

                    // Build developer payload and send to Ralsei
                    buildAndSendDeveloperPayload(ctx, response)
                } else {
                    Log.d(TAG, "Scene filtered by relevance gate: relevanceScore=$relevanceScore")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing analyzer response", e)
        }
    }

    private fun buildAndSendDeveloperPayload(ctx: Context, analyzerResponse: JSONObject) {
        try {
            val timelineBuffer = EnhancedMemoryManager.getTimelineBuffer(ctx, 5)
            val recentIntents = EnhancedMemoryManager.getRecentIntents(ctx, 5)
            val condensedMemories = EnhancedMemoryManager.getRecentCondensedMemorySummary(ctx, 3)

            val devPayload = JSONObject()
            devPayload.put("tick_id", analyzerResponse.optString("tick_id", ""))
            devPayload.put("batch", analyzerResponse.optJSONObject("batch"))
            devPayload.put("context", analyzerResponse.optJSONObject("context"))

            val recentMemArray = JSONArray()
            for (line in condensedMemories.split("\n").take(3)) {
                if (line.isNotBlank()) recentMemArray.put(line)
            }
            devPayload.put("recent_memories", recentMemArray)

            val intentsArray = JSONArray()
            for (intent in recentIntents) {
                val iObj = JSONObject()
                iObj.put("ts", intent.timestamp)
                iObj.put("intent", intent.intent)
                iObj.put("phrasing_hash", intent.phrasingHash)
                iObj.put("length", intent.length)
                intentsArray.put(iObj)
            }
            devPayload.put("recent_intents", intentsArray)

            val timelineArray = JSONArray()
            for (entry in timelineBuffer) {
                timelineArray.put("${entry.timestamp}: ${entry.sceneLabel} - ${entry.shortText}")
            }
            devPayload.put("timeline_buffer", timelineArray)

            // Trend summary (simplified)
            val trendSummary = JSONObject()
            if (timelineBuffer.size >= 3) {
                val lastThree = timelineBuffer.takeLast(3)
                val allSameScene = lastThree.all { it.sceneLabel == lastThree[0].sceneLabel }
                if (allSameScene) {
                    trendSummary.put("user_state", "repeating_activity")
                    trendSummary.put("repeat_scene", lastThree[0].sceneLabel)
                }
            }
            devPayload.put("trend_summary", trendSummary)

            // Send to ChatManager
            val suggestionText = "Developer payload: ${devPayload}"
            ChatManager.handleDeveloperSuggestion(ctx, suggestionText)
        } catch (e: Exception) {
            Log.e(TAG, "Error building developer payload", e)
        }
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(4 * 1024)
        val bis = BufferedInputStream(input)
        var n: Int
        while (bis.read(data).also { n = it } != -1) {
            buffer.write(data, 0, n)
        }
        return buffer.toByteArray()
    }

    /**
     * Strip markdown code blocks from JSON response
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
}
