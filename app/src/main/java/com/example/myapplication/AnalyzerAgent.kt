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
You are the Screenshot Analyzer. For each batch of screenshots return:
- frames[] minimal inference
- batch_summary {scene_type, dominant_activity, repeat_count, velocity, micro_emotion_shift, confidence}
- screen_description {summary_text, keywords[], topic_inferred, confidence}
  + This field should:
++ Be 1–3 sentences, human-readable.
++ Combine visual + text + behavioral data.
++ Include tone or inferred mood if possible.

- short justification (<= 18 words)
- safety_flags {suicidal, selfharm, nsfw}
Do not include base64 or raw image data in outputs. Be concise except for the .

Return JSON ONLY in this exact format:
{
  "tick_id": "t_YYYYMMDD_HHMMSS",
  "timestamp": "ISO8601 timestamp",
  "frames": [
    {"id":1,"scene_type":"string","primary_activity":"string","activity_conf":0.0-1.0,"text_snips":["..."]},
    {"id":2,"scene_type":"string","primary_activity":"string","activity_conf":0.0-1.0,"text_snips":["..."]},
    {"id":3,"scene_type":"string","primary_activity":"string","activity_conf":0.0-1.0,"text_snips":["..."]}
  ],
  "batch_summary":{
    "scene_type":"string",
    "dominant_activity":"string",
    "repeat_count":1-3,
    "velocity":"slow/medium/fast",
    "micro_emotion_shift":"neutral/positive/negative/mixed",
    "confidence":0.0-1.0
  },
  "screen_description": {
    "summary_text": "User scrolls quickly through a text-based social feed, glancing at short posts and memes. Their face looks calm but somewhat disengaged.",
    "keywords": ["scrolling", "social", "neutral mood", "habitual"],
    "topic_inferred": "idle social browsing",
    "confidence": 0.88
  },
  "safety_flags":{"suicidal":0.0-1.0,"selfharm":0.0-1.0,"nsfw":0.0-1.0},
  "justification":"string (short reason)"
}
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

            val inputArray = JSONArray()
            val systemObj = JSONObject()
            systemObj.put("role", "system")
            val sysContent = JSONArray()
            val sysText = JSONObject()
            sysText.put("type", "input_text")
            sysText.put("text", ANALYZER_SYSTEM_PROMPT)
            sysContent.put(sysText)

            if (contextText.isNotBlank()) {
                val ctxObj = JSONObject()
                ctxObj.put("type", "input_text")
                ctxObj.put("text", contextText)
                sysContent.put(ctxObj)
            }
            systemObj.put("content", sysContent)
            inputArray.put(systemObj)

            for (bytes in imageByteList) {
                val inputObj = JSONObject()
                inputObj.put("role", "user")
                val contentArray = JSONArray()
                val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val imageObj = JSONObject()
                imageObj.put("type", "input_image")
                imageObj.put("image_url", "data:image/jpeg;base64,$base64Image")
                contentArray.put(imageObj)
                inputObj.put("content", contentArray)
                inputArray.put(inputObj)
            }

            val requestJson = JSONObject()
            requestJson.put("model", "gpt-4.1-mini")
            requestJson.put("temperature", 0.6)
            requestJson.put("input", inputArray)

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

                if (json.has("output_text")) {
                    structured = JSONObject(json.getString("output_text"))
                } else if (json.has("output")) {
                    val outputArr = json.getJSONArray("output")
                    for (i in 0 until outputArr.length()) {
                        val outObj = outputArr.getJSONObject(i)
                        if (outObj.has("content")) {
                            val contentArr = outObj.getJSONArray("content")
                            for (j in 0 until contentArr.length()) {
                                val c = contentArr.getJSONObject(j)
                                if (c.optString("type") == "output_text" && c.has("text")) {
                                    structured = JSONObject(c.getString("text"))
                                    break
                                }
                            }
                        }
                        if (structured != null) break
                    }
                }

                if (structured != null) {
                    // Log the request and response to ResponseLogger
                    // Try to extract usage/token info from top-level response JSON (if provider includes it)
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
            val timestamp = response.optString("timestamp", "")
            val batchSummary = response.optJSONObject("batch_summary")
            val safetyFlags = response.optJSONObject("safety_flags")
            val justification = response.optString("justification", "")

            Log.d(TAG, "Analyzer result: tickId=$tickId, justification=$justification")

            if (batchSummary != null) {
                val sceneType = batchSummary.optString("scene_type", "unknown")
                val dominantActivity = batchSummary.optString("dominant_activity", "unknown")
                val confidence = batchSummary.optDouble("confidence", 0.5)

                // Relevance gate check (simplified)
                val relevanceScore = confidence * 0.7 + 0.3 // Simple formula
                if (relevanceScore >= 0.4) {
                    // Add to SceneTimeline
                    val sceneEntry = EnhancedMemoryManager.SceneTimelineEntry(
                        timestamp = timestamp.ifEmpty { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()) },
                        sceneLabel = sceneType,
                        shortText = "$dominantActivity (${batchSummary.optInt("repeat_count", 1)} frames)",
                        confidence = confidence
                    )
                    EnhancedMemoryManager.addSceneTimelineEntry(ctx, sceneEntry)

                    // Check safety flags
                    if (safetyFlags != null) {
                        val suicidalScore = safetyFlags.optDouble("suicidal", 0.0)
                        val selfharmScore = safetyFlags.optDouble("selfharm", 0.0)
                        if (suicidalScore > 0.5 || selfharmScore > 0.5) {
                            Log.w(TAG, "Safety flag triggered: suicidal=$suicidalScore, selfharm=$selfharmScore")
                            // Store high-priority safety concern
                            EnhancedMemoryManager.addCondensedMemoryEntry(ctx,
                                "SAFETY: detected concerning content (${if (suicidalScore > 0.5) "suicidal" else "selfharm"})",
                                confidence = 0.95,
                                source = "analyzer_safety"
                            )
                        }
                    }

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
            devPayload.put("batch_summary", analyzerResponse.optJSONObject("batch_summary"))
            devPayload.put("frames_sample", analyzerResponse.optJSONArray("frames"))

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
}
