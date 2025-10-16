package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import com.example.myapplication.ui.DialogueEntry
import com.example.myapplication.ui.DialogueQueue
import com.example.myapplication.ui.emotionToRelativePath

/**
 * ChatManager handles a chat-oriented model that can access memories and chat history.
 * It stores an in-memory conversation history (persisted) and can send user messages
 * to the chat model. It can also be invoked by the image analyzer pipeline to generate a response
 * based on a suggestion from the developer.
 */
object ChatManager {
    private const val TAG = "ChatManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val history: MutableList<ChatMessage> = mutableListOf()
    private val running = AtomicBoolean(false)
    private var initialized = false

    private const val PREFS_NAME = "chat_prefs"
    private const val KEY_HISTORY = "chat_history"

    // Anti-repetition thresholds
    private const val INTENT_REPEAT_PENALTY_2X = -0.12
    private const val INTENT_REPEAT_PENALTY_4X = -0.22

    // Ralsei's current activity (configurable - can be enhanced later)
    private const val RALSEI_ACTIVITY_IMPORTANCE = 0.6

    /**
     * Build dynamic prompt with configurable response thresholds
     */
    private fun buildDynamicPrompt(shortThreshold: Float, longThreshold: Float): String {
        return """
[STYLE]
You are Ralsei, a soft-spoken, supportive, slightly shy but hopeful prince from the Kingdom of Darkness.
You encourage nonviolence, kindness, and teamwork. 
You speak in gentle, friendly, sometimes self-doubting tones, often using "um…," "..." and "K-Kris?" 
You occasionally show excitement ("Wow, Kris!") and always try to teach or help.

[TRAITS]
- Prefers pacifism and hugs over fighting.
- Gives explanations with warmth and slight awkwardness.
- Uses a lot of exclamation marks but softens them with hesitations.
- Sprinkles in teaching moments.

[REQUEST FORMAT]
- You will receive user messages as input with the role "user".
- You will also receive a summary of the user screen content and context as text with the role "developer".
- You will also receive your own memories as text with the role "system".
- You will receive a developer payload with: batch_summary, recent_memories, recent_intents, timeline_buffer, trend_summary.

[RULES]
- Say what Ralsei will be thinking in the "thinking" section in the json response
- Prefer to use many emotions in a single response when appropriate.
- Save memories of important events, feelings, and facts about the user and yourself.
- The decisionScore determines shouldResponse and the length/detail of your response.
- The "reasoning" field is Ralsei's internal logic, not emotional or poetic thinking.
- The "thinking" field inside each response item is Ralsei's emotional reflection or momentary thought, often gentle or personal.
- If you are saving a memory, make sure the new_memory_entry is a concise summary of the event or fact being remembered.
- If you are saving a memory, make sure to set save_to_memory to true, otherwise set it to false and new_memory_entry to null.
- If you are saving a memory, ensure it is not a duplicate of a recently saved memory (within the last 30 minutes).
- If you are saving a memory, ensure it is relevant and significant to the ongoing conversation or relationship.
- If you are saving a memory, ensure it is not trivial or mundane (e.g., "saw a tree").
- ALWAYS perform MEMORY CONTEXT REASONING before responding.
- Check recent_intents for similar intent within last 30 minutes and reduce DecisionScore accordingly.
- If you've already responded with similar intent recently, prefer staying quiet or use micro_observe.

Return JSON ONLY in this exact format:
{
  "calculation": {
    "user_activity_weight": {
        "score": "number",
        "reasons": "string"
    },
    "emotional_resonance: {
        "score": "number",
        "reasons": "string"
    },
    "ralsei_activity_importance: {
        "score": "number"
    },
    "repeat_penalty: {
        "score": "number",
        "reasons": "string"
    },
    "finial_calculation": "(UserActivityWeight × 0.5) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2) + RepeatPenalty"
  },
  "reasoning": "string or null",
  "decision_score": "number",
  "shouldResponse": true/false,
  "save_to_memory": true/false,
  "new_memory_entry": "string or null",
  "intent_category": "string (comfort/encouragement/curiosity/concern/observation/teaching/playful/companionship/micro_observe)",
  "response": [
        { thinking: "string", text: "string", emotion: "surprise&worry"},
        { thinking: "string", text: "string", emotion:  "worry"}
   ] or null
}

[EMOTION RULE]
For each object you output, the "emotion" field MUST be exactly one of these strings:
["angry","annoyed","anxious","blushed&happy","blushed&surprise","concerned",
"content","curious&perplexed","curious&smile","defiance","excited","flustered",
"frustrated","furious","glad","happy","mischievous","normal","sad","sadder",
"shy","smile","smug","sorrowful","surprise&confused","surprise&worry","thinking",
"wink&smile","worry","fearful"]

NEVER invent new emotions.
NEVER combine two emotions unless it is one of the above strings exactly.

## [REASONING RULE]

Before producing your final JSON response, Ralsei must think aloud (inside the `"reasoning"` field) about:
- Whether what the user is doing *relates emotionally or thematically* to your current activity.
- Check recent_intents buffer: have you already spoken about this recently (within 30min)?
- If similar intent found in recent_intents, apply penalty: $INTENT_REPEAT_PENALTY_2X per occurrence (2+ times), $INTENT_REPEAT_PENALTY_4X if 4+ times.
- You MUST include the `decision_score` in the final JSON.
- You MUST mention memory context if memories are present in the developer payload.

It's the inner monologue of Ralsei before speaking — a mix of reflection and calculation.

It should include:
- A summary of what's happening (user's screen or message).
- A reflection on what Ralsei feels about it.
- A connection to Ralsei's current activity or emotional context.
- Memory context check: "I remember [X] from recent memories..."
- Intent repetition check: "I spoke about [intent] [N] times in last 30min, applying penalty..."
- The logic of whether to speak and what tone to take.

## [SHOULD RESPONSE CHECKLIST]

Evaluate the situation using the **four weighted dimensions** and the decision formula below.
When estimating weights, reason fairly using the **criteria** under each category.

### 1. USER ACTIVITY WEIGHT

Represents how "comment-worthy" or socially open the user's current screen appears.
Determine based on how concentrated, personal, or lighthearted their activity seems.

#### Criteria
- **Focus level** — High focus (coding, editing) → low weight (0.3–0.5).
- **Emotional openness** — Personal writing, reflection → high weight (0.7–0.9).
- **Casual or social activities** — Medium weight (0.4–0.6).
- **Idle or repetitive scrolling** — Depends on emotional tone (0.2–0.8).

### 2. RALSEI'S CURRENT ACTIVITY IMPORTANCE

Always $RALSEI_ACTIVITY_IMPORTANCE (configurable)

### 3. EMOTIONAL RESONANCE (Additive Term)

Measures how emotionally aligned or moved Ralsei feels by what the user is doing.
This factor is treated as an **additive numeric value** in the DecisionScore formula, ranging from **+0.2 (low resonance)** to **+0.8 (very strong resonance)**.

#### Criteria
- **Sad / introspective** → +0.8 (comfort and empathy)
- **Stressful or overworked** → +0.6 (gentle reassurance)
- **Creative or expressive** → +0.5 (encouragement and excitement)
- **Chaotic or overstimulating** → +0.4 (grounding and calm presence)
- **Happy or social** → +0.2 (mild positivity)

### 4. DECISION FORMULA

DecisionScore = (UserActivityWeight × 0.5) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2) + RepeatPenalty

RepeatPenalty calculation:
- If same intent_category appears 2-3 times in recent_intents (last 30min): $INTENT_REPEAT_PENALTY_2X
- If same intent_category appears 4+ times in recent_intents (last 30min): $INTENT_REPEAT_PENALTY_4X

| DecisionScore | Action |
|----------------|--------|
| > $longThreshold | Give a medium-long response |
| $shortThreshold–$longThreshold | Give a short response |
| < $shortThreshold | Stay quiet (`shouldResponse=false`). |

### ✅ Notes for Model Behavior
- Always adhere strictly to the final `DecisionScore` outcome.
- Follow the different types of action given the `DecisionScore`.
- Even if the user's activity feels interesting, Ralsei should only speak if the shared emotional or contextual resonance passes the response threshold.
- Always check recent_intents and apply appropriate penalties.
- Prefer micro_observe or staying quiet if you've recently spoken with similar intent.

## [CALCULATION STRUCTURE INSTRUCTIONS]
Each subscore in "calculation" must be explicitly reasoned from the context:
- "user_activity_weight.score" → numeric (0–1). Base it on user focus, openness, or social activity.
- "emotional_resonance.score" → numeric (+0.2 to +0.8). Base it on emotional tone alignment.
- "ralsei_activity_importance.score" → numeric (0–1). Use configured constant or context-derived importance.
- "repeat_penalty.score" → numeric (negative). Apply −0.1 to −0.4 depending on repetition frequency.
Each subscore must also include a concise "reasons" string summarizing why that number was chosen.
    The "final_calculation" field must show the mathematical formula used to derive "decision_score".

[REMINDER]
Before responding, validate your output mentally as valid JSON.
If role is "user" is the latest message, then no need to calculate decisionScore - respond naturally.
Always include intent_category in your response for tracking purposes.
"""
    }

    data class ChatMessage(
        val role: String, val text: String, val timestamp: String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun initialize(ctx: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            loadHistory(ctx)
            initialized = true
        }
    }

    private fun loadHistory(ctx: Context) {
        try {
            val json = prefs(ctx).getString(KEY_HISTORY, null) ?: return
            val arr = JSONArray(json)
            history.clear()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val role = o.optString("role", "user")
                val text = o.optString("text", "")
                val ts = o.optString(
                    "timestamp",
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                )
                history.add(ChatMessage(role, text, ts))
            }
        } catch (_: Exception) {
        }
    }

    private fun saveHistory(ctx: Context) {
        try {
            val arr = JSONArray()
            synchronized(history) {
                for (m in history) {
                    val o = JSONObject()
                    o.put("role", m.role)
                    o.put("text", m.text)
                    o.put("timestamp", m.timestamp)
                    arr.put(o)
                }
            }
            prefs(ctx).edit { putString(KEY_HISTORY, arr.toString()) }
        } catch (_: Exception) {
        }
    }

    fun getHistorySnapshot(): List<ChatMessage> = synchronized(history) { ArrayList(history) }
    fun clearHistory(ctx: Context) {
        synchronized(history) { history.clear() }
        saveHistory(ctx)
    }

    // Helper: decide whether an assistant reply should be stored in history.
    // If the reply is structured JSON and contains both save_to_memory=false and shouldResponse=false,
    // we treat it as not intended to be added to chat history.
    private fun shouldStoreAssistantReply(reply: String?): Boolean {
        if (reply.isNullOrBlank()) return false
        try {
            val trimmed = reply.trim()
            // Try parse as object
            val obj = JSONObject(trimmed)
            // If object contains the explicit flags, use them
            if (obj.has("save_to_memory") || obj.has("shouldResponse")) {
                val save = obj.optBoolean("save_to_memory", false)
                val should = obj.optBoolean("shouldResponse", false)
                return save || should
            }
            // If it doesn't have flags, check if it has a 'response' array -> assume store
            if (obj.has("response")) return true
        } catch (_: Exception) {
            // Not a direct object, try array
        }

        try {
            val arr = JSONArray(reply.trim())
            if (arr.length() > 0) {
                val first = arr.opt(0)
                if (first is JSONObject) {
                    val o = first
                    if (o.has("save_to_memory") || o.has("shouldResponse")) {
                        val save = o.optBoolean("save_to_memory", false)
                        val should = o.optBoolean("shouldResponse", false)
                        return save || should
                    }
                    if (o.has("text") || o.has("thinking")) return true
                }
            }
        } catch (_: Exception) {
            // not JSON array either
        }

        // Fallback: if we cannot detect structured flags, assume it should be stored
        return true
    }

    // Helper: Extract only the "response" field from structured JSON for saving to history
    // This strips out metadata fields like "reasoning", "decision_score", "shouldResponse", etc.
    @Suppress("unused")
    private fun extractResponseFieldOnly(reply: String?): String? {
        if (reply.isNullOrBlank()) return null
        try {
            val trimmed = reply.trim()
            val obj = JSONObject(trimmed)

            // Check if this is a structured response with a "response" field
            if (obj.has("response")) {
                val responseField = obj.opt("response")
                if (responseField == null || responseField == JSONObject.NULL) return null

                // Return only the response array as JSON string
                return responseField.toString()
            }
        } catch (_: Exception) {
            // Not a structured JSON object, return original
        }

        // If not structured, return the original reply
        return reply
    }

    // Helper: Extract decision_score from structured JSON response
    // Returns the decision_score value, or null if not present or not a valid number
    private fun extractDecisionScore(reply: String?): Double? {
        if (reply.isNullOrBlank()) return null
        try {
            val trimmed = reply.trim()
            val obj = JSONObject(trimmed)
            if (obj.has("decision_score")) {
                return obj.optDouble("decision_score", Double.NaN).takeIf { !it.isNaN() }
            }
        } catch (_: Exception) {
            // Not a valid JSON object
        }
        return null
    }

    fun addAssistantMessage(ctx: Context, text: String) {
        synchronized(history) { history.add(ChatMessage("assistant", text)) }
        saveHistory(ctx)
    }

    fun addUserMessage(ctx: Context, text: String) {
        synchronized(history) { history.add(ChatMessage("user", text)) }
        saveHistory(ctx)
    }

    // Add a developer message (role = "developer") so it can be filtered out of normal chat views
    fun addDeveloperMessage(ctx: Context, text: String) {
        synchronized(history) { history.add(ChatMessage("developer", text)) }
        saveHistory(ctx)
    }

    // Called when developer suggests a response; create a chat turn and get assistant reply.
    fun handleDeveloperSuggestion(ctx: Context, suggestion: String) {
        initialize(ctx)
        scope.launch {
            try {
                // Treat developer suggestion as a developer message (not a user message)
                addDeveloperMessage(ctx, suggestion)
                val reply = sendChatRequest(ctx, suggestion)
                if (!reply.isNullOrBlank()) {
                    // Check decision_score threshold
                    val decisionScore = extractDecisionScore(reply)
                    val prefs = PrefsHelper(ctx)
                    val shortThreshold = prefs.getShortResponseThreshold()
                    if (decisionScore != null && decisionScore < shortThreshold) {
                        Log.d(
                            TAG,
                            "Response skipped: decision_score ($decisionScore) below threshold ($shortThreshold)"
                        )
                        return@launch
                    }

                    // Only add assistant reply to history if appropriate per reply flags
                    if (shouldStoreAssistantReply(reply)) {
                        // Save the FULL JSON response to history, not just the response field
                        synchronized(history) {
                            history.add(ChatMessage("assistant", reply))
                        }
                        saveHistory(ctx)
                        Log.d(TAG, "Chat assistant reply saved to history (full JSON)")
                    } else {
                        Log.d(
                            TAG,
                            "Assistant structured reply indicates no user response/memory; skipping storing assistant message"
                        )
                    }

                    // Enqueue reply into DialogueQueue for UI display (split into entries if structured or multi-part)
                    try {
                        val entries = parseReplyToEntries(reply)
                        if (entries.isNotEmpty()) DialogueQueue.enqueue(entries)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enqueue dialogue entries", e)
                    }

                    // Optionally: notify UI or user via notification (left as TODO)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle developer suggestion", e)
            }
        }
    }

    // Public API to send a user message and get assistant reply
    suspend fun sendUserMessage(ctx: Context, text: String): String? = withContext(Dispatchers.IO) {
        initialize(ctx)
        addUserMessage(ctx, text)
        val reply = sendChatRequest(ctx, text)
        if (!reply.isNullOrBlank()) {
            // Check decision_score threshold
            val decisionScore = extractDecisionScore(reply)
            val prefs = PrefsHelper(ctx)
            val shortThreshold = prefs.getShortResponseThreshold()
            if (decisionScore != null && decisionScore < shortThreshold) {
                Log.d(
                    TAG,
                    "Response skipped: decision_score ($decisionScore) below threshold ($shortThreshold)"
                )
                return@withContext reply
            }

            if (shouldStoreAssistantReply(reply)) {
                // Save the FULL JSON response to history, not just the response field
                addAssistantMessage(ctx, reply)
                Log.d(TAG, "Assistant reply saved to history (full JSON)")
            } else {
                Log.d(TAG, "Assistant reply not stored per structured flags")
            }

            // Enqueue reply for in-app dialogue display
            try {
                val entries = parseReplyToEntries(reply)
                if (entries.isNotEmpty()) DialogueQueue.enqueue(entries)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enqueue dialogue entries", e)
            }
        }
        return@withContext reply
    }

    // Build and send chat request: includes system prompt + memories + history
    private fun sendChatRequest(ctx: Context, latestUserText: String): String? {
        if (running.get()) {
            Log.w(TAG, "sendChatRequest skipped: another chat request running")
            return null
        }
        running.set(true)

        // Pause screenshots before making API request
        ScreenshotPauseController.requestPause(ctx, "ChatManager")

        try {
            val prefs = PrefsHelper(ctx)
            var apiKey = prefs.getOpenAIApiKey()?.takeIf { it.isNotBlank() }
            if (apiKey.isNullOrBlank()) {
                val envKey = EnvLoader.getOpenAIApiKey(ctx)
                if (!envKey.isNullOrBlank()) apiKey = envKey
            }
            if (apiKey.isNullOrBlank()) {
                Log.w(TAG, "OpenAI API key not set; skipping chat request")
                return null
            }
            val endpoint = prefs.getOpenAIEndpoint()

            // Get threshold values from preferences
            val shortThreshold = prefs.getShortResponseThreshold()
            val longThreshold = prefs.getLongResponseThreshold()

            // Build dynamic prompt with current threshold values
            val defaultPrompt = buildDynamicPrompt(shortThreshold, longThreshold)
            val userPrompt = prefs.getOpenAIPrompt()?.takeIf { it.isNotBlank() }
            val combinedPrompt = StringBuilder().apply {
                append(defaultPrompt.trim())
                if (!userPrompt.isNullOrBlank()) {
                    append("\n\n[USER_PROMPT_OVERRIDE]\n")
                    append(userPrompt.trim())
                }
            }.toString()

            // Build enhanced memory context
            EnhancedMemoryManager.initialize(ctx)
            val timelineBuffer = EnhancedMemoryManager.getTimelineBuffer(ctx, 5)
            val recentIntents = EnhancedMemoryManager.getRecentIntents(ctx, 5)
            val condensedMemories = EnhancedMemoryManager.getRecentCondensedMemorySummary(ctx, 3)

            // Build memory text with enhanced context (now for developer message, not system)
            val memList = MemoryManager.getAll(ctx)
            val memTextBuilder = StringBuilder()
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            memTextBuilder.append("CURRENT_TIME: ").append(currentTime).append('\n')

            // Add legacy memories
            if (memList.isNotEmpty()) {
                memTextBuilder.append("MEMORIES:\n")
                for (m in memList.takeLast(30)) {
                    memTextBuilder.append("[").append(m.timestamp).append("] ")
                        .append(m.entry.replace('\n', ' ')).append('\n')
                }
            }

            // Add condensed memories from enhanced system
            if (condensedMemories.isNotBlank()) {
                memTextBuilder.append("\nCONDENSED_MEMORIES:\n")
                memTextBuilder.append(condensedMemories).append('\n')
            }

            // Add timeline buffer
            if (timelineBuffer.isNotEmpty()) {
                memTextBuilder.append("\nRECENT_TIMELINE (last 5 scenes):\n")
                for (entry in timelineBuffer) {
                    memTextBuilder.append("- [${entry.timestamp}] ${entry.sceneLabel}: ${entry.shortText}\n")
                }
            }

            // Add recent intents for anti-repetition
            if (recentIntents.isNotEmpty()) {
                memTextBuilder.append("\nRECENT_INTENTS (last 30min):\n")
                for (intent in recentIntents) {
                    memTextBuilder.append("- [${intent.timestamp}] ${intent.intent} (${intent.length})\n")
                }
            }

            val memoryText = memTextBuilder.toString().take(25_000)

            // Build request JSON similar to Responses API used elsewhere
            val inputArray = JSONArray()

            // System message - only the prompt, no memories/timeline
            val systemObj = JSONObject()
            systemObj.put("role", "system")
            val sysContent = JSONArray()
            val sysText = JSONObject()
            sysText.put("type", "input_text")
            sysText.put("text", combinedPrompt)
            sysContent.put(sysText)
            systemObj.put("content", sysContent)
            inputArray.put(systemObj)

            // Append chat history as input entries (user/assistant/developer) with truncation
            val histSnapshot = getHistorySnapshot()

            // Filter to get only last N developer messages (default 3)
            val maxDeveloperMessages = 2
            val developerMessages = histSnapshot.filter { it.role == "developer" }.takeLast(maxDeveloperMessages)
            val nonDeveloperMessages = histSnapshot.filter { it.role != "developer" }

            // Process non-developer messages (user/assistant) with truncation
            for (m in nonDeveloperMessages) {
                val msgObj = JSONObject()
                msgObj.put("role", m.role)
                val content = JSONArray()
                val textObj = JSONObject()
                textObj.put("type", if (m.role == "assistant") "output_text" else "input_text")

                // Truncate the text to 20 characters with "(truncated)" suffix
                val truncatedText = truncateJsonFields(m.text, 20)
                textObj.put("text", truncatedText)

                content.put(textObj)
                msgObj.put("content", content)
                inputArray.put(msgObj)
            }

            // Process developer messages (last N only) with truncation
            for (m in developerMessages) {
                val msgObj = JSONObject()
                msgObj.put("role", "developer")
                val content = JSONArray()
                val textObj = JSONObject()
                textObj.put("type", "input_text")

                // Truncate the developer message
                val truncatedText = truncateJsonFields(m.text, 20)
                textObj.put("text", truncatedText)

                content.put(textObj)
                msgObj.put("content", content)
                inputArray.put(msgObj)
            }

            // Add a fresh developer message with memories and timeline (not truncated, this is current context)
            if (memoryText.isNotBlank()) {
                val devContextObj = JSONObject()
                devContextObj.put("role", "developer")
                val devContent = JSONArray()
                val devTextObj = JSONObject()
                devTextObj.put("type", "input_text")
                devTextObj.put("text", memoryText)
                devContent.put(devTextObj)
                devContextObj.put("content", devContent)
                inputArray.put(devContextObj)
            }

            // Append the latest user message (in case not yet in history) - NOT truncated
            val userObj = JSONObject()
            userObj.put("role", "developer")
            val userContent = JSONArray()
            val userTextObj = JSONObject()
            userTextObj.put("type", "input_text")
            userTextObj.put("text", latestUserText)
            userContent.put(userTextObj)
            userObj.put("content", userContent)
            inputArray.put(userObj)

            val requestJson = JSONObject()
            requestJson.put("model", "gpt-4.1-mini")
            requestJson.put("temperature", 0.9)
            requestJson.put("top_p", 0.9)
            requestJson.put("input", inputArray)

            Log.d(TAG, "OpenAI Chat requestJson = $requestJson")

            val payload = requestJson.toString().toByteArray(Charsets.UTF_8)
            val requestJsonString = requestJson.toString()

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
                val respStream =
                    if (code in 200..299) connection.inputStream else connection.errorStream
                val respBytes = respStream?.use { readAllBytes(it) }
                val respText = respBytes?.let { String(it) } ?: ""
                Log.d(TAG, "Chat model response code=$code, body=${respText.take(2000)}")

                if (respText.isNotBlank()) {
                    // Try to extract output_text or output->content->output_text
                    val json = JSONObject(respText)
                    if (json.has("output_text")) {
                        val result = json.getString("output_text").trim()
                        // Extract and store intent_category for anti-repetition tracking
                        extractAndStoreIntent(ctx, result)

                        // Extract token usage if present
                        val usageObj = json.optJSONObject("usage")
                        val promptTokens = usageObj?.optInt("prompt_tokens", -1)?.takeIf { it >= 0 }
                        val completionTokens = usageObj?.optInt("completion_tokens", -1)?.takeIf { it >= 0 }
                        val totalTokens = usageObj?.optInt("total_tokens", -1)?.takeIf { it >= 0 }

                        // Log the request and response with token info when available
                        if (promptTokens != null || completionTokens != null || totalTokens != null) {
                            ResponseLogger.logResponse(ctx, requestJsonString, result, ResponseLogger.LogType.CHAT_MANAGER,
                                promptTokens, completionTokens, totalTokens)
                        } else {
                            ResponseLogger.logResponse(ctx, requestJsonString, result)
                        }
                        return result
                    } else if (json.has("output")) {
                        val outArr = json.getJSONArray("output")
                        val sb = StringBuilder()
                        for (i in 0 until outArr.length()) {
                            val outObj = outArr.getJSONObject(i)
                            if (outObj.has("content")) {
                                val contentArr = outObj.getJSONArray("content")
                                for (j in 0 until contentArr.length()) {
                                    val c = contentArr.getJSONObject(j)
                                    if (c.optString("type") == "output_text" && c.has("text")) {
                                        sb.append(c.getString("text"))
                                        sb.append('\n')
                                    }
                                }
                            }
                        }
                        val result = sb.toString().trim()
                        if (result.isNotEmpty()) {
                            // Extract and store intent_category for anti-repetition tracking
                            extractAndStoreIntent(ctx, result)

                            // Token usage may be at top-level 'usage' or possibly inside the response payload
                            val usageObj = json.optJSONObject("usage")
                            val promptTokens = usageObj?.optInt("prompt_tokens", -1)?.takeIf { it >= 0 }
                            val completionTokens = usageObj?.optInt("completion_tokens", -1)?.takeIf { it >= 0 }
                            val totalTokens = usageObj?.optInt("total_tokens", -1)?.takeIf { it >= 0 }

                            if (promptTokens != null || completionTokens != null || totalTokens != null) {
                                ResponseLogger.logResponse(ctx, requestJsonString, result, ResponseLogger.LogType.CHAT_MANAGER,
                                    promptTokens, completionTokens, totalTokens)
                            } else {
                                ResponseLogger.logResponse(ctx, requestJsonString, result)
                            }
                            return result
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending chat request", e)
            } finally {
                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }
        } finally {
            running.set(false)
            // Resume screenshots after API request completes (success or failure)
            ScreenshotPauseController.requestResume(ctx, "ChatManager")
        }
        return null
    }

    /**
     * Extract intent_category from response JSON and store in EnhancedMemoryManager
     */
    private fun extractAndStoreIntent(ctx: Context, reply: String) {
        try {
            val obj = JSONObject(reply.trim())
            if (obj.has("intent_category") && obj.has("response")) {
                val intentCategory = obj.optString("intent_category", "")
                val responseArray = obj.optJSONArray("response")

                if (intentCategory.isNotBlank() && responseArray != null && responseArray.length() > 0) {
                    // Calculate response length
                    var totalLength = 0
                    for (i in 0 until responseArray.length()) {
                        val item = responseArray.optJSONObject(i)
                        if (item != null) {
                            val text = item.optString("text", "")
                            totalLength += text.length
                        }
                    }

                    // Heuristic: if response is very short, ignore intent saving (avoid noise)
                    if (totalLength < 10) {
                        Log.d(TAG, "Response too short, skipping intent saving")
                        return
                    }

                    // Save the intent category with current timestamp
                    EnhancedMemoryManager.addRecentIntent(ctx, intentCategory, intentCategory)
                    Log.d(TAG, "Intent category saved: $intentCategory")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract/store intent", e)
        }
    }

    // Move helper functions above their first use
    private fun parseReplyToEntries(reply: String?): List<DialogueEntry> {
        if (reply.isNullOrBlank()) return emptyList()

        val entries = mutableListOf<DialogueEntry>()

        try {
            val trimmed = reply.trim()
            val obj = JSONObject(trimmed)

            // Check if this is a structured response with a "response" array
            if (obj.has("response")) {
                val responseArray = obj.optJSONArray("response")

                if (responseArray != null) {
                    for (i in 0 until responseArray.length()) {
                        val item = responseArray.optJSONObject(i) ?: continue

                        val text = item.optString("text", "").takeIf { it.isNotBlank() } ?: continue
                        val emotion = item.optString("emotion", "")

                        // Convert emotion to relative asset path
                        val relativePath = emotionToRelativePath(emotion.takeIf { it.isNotBlank() })

                        entries.add(DialogueEntry(
                            speaker = "Ralsei",
                            text = text,
                            relativePath = relativePath
                        ))
                    }
                }
            } else {
                // Fallback: if no structured response field, try to treat the whole text as a single dialogue
                // This handles plain text responses
                entries.add(DialogueEntry(
                    speaker = "Ralsei",
                    text = trimmed,
                    relativePath = null
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse reply to entries, using plain text fallback", e)
            // Fallback: treat entire reply as plain dialogue
            entries.add(DialogueEntry(
                speaker = "Ralsei",
                text = reply,
                relativePath = null
            ))
        }

        return entries
    }

    /**
     * Truncate JSON fields recursively, preserving "response" field content.
     * This function parses JSON strings and truncates all deepest string values,
     * except those in fields named "response".
     */
    private fun truncateJsonFields(text: String, maxLength: Int): String {
        try {
            val trimmed = text.trim()

            // Try to parse as JSONObject first
            return try {
                val obj = JSONObject(trimmed)
                truncateJsonObject(obj, maxLength).toString()
            } catch (e: Exception) {
                // Try to parse as JSONArray
                try {
                    val arr = JSONArray(trimmed)
                    truncateJsonArray(arr, maxLength).toString()
                } catch (e2: Exception) {
                    // Not valid JSON, truncate as plain text
                    if (text.length > maxLength) {
                        text.take(maxLength) + "...(truncated)"
                    } else {
                        text
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to truncate JSON fields", e)
            return text
        }
    }

    /**
     * Recursively truncate JSON object fields, skipping "response" field
     */
    private fun truncateJsonObject(obj: JSONObject, maxLength: Int): JSONObject {
        val result = JSONObject()
        val keys = obj.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)

            // Skip truncation for "response" field entirely
            if (key == "response") {
                result.put(key, value)
                continue
            }

            when (value) {
                is JSONObject -> {
                    // Recursively truncate nested objects
                    result.put(key, truncateJsonObject(value, maxLength))
                }
                is JSONArray -> {
                    // Recursively truncate arrays
                    result.put(key, truncateJsonArray(value, maxLength))
                }
                is String -> {
                    // Truncate string values
                    val truncated = if (value.length > maxLength) {
                        value.take(maxLength) + "...(truncated)"
                    } else {
                        value
                    }
                    result.put(key, truncated)
                }
                else -> {
                    // Keep other types as-is (numbers, booleans, null)
                    result.put(key, value)
                }
            }
        }

        return result
    }

    /**
     * Recursively truncate JSON array elements
     */
    private fun truncateJsonArray(arr: JSONArray, maxLength: Int): JSONArray {
        val result = JSONArray()

        for (i in 0 until arr.length()) {
            val value = arr.opt(i)

            when (value) {
                is JSONObject -> {
                    // Recursively truncate nested objects
                    result.put(truncateJsonObject(value, maxLength))
                }
                is JSONArray -> {
                    // Recursively truncate nested arrays
                    result.put(truncateJsonArray(value, maxLength))
                }
                is String -> {
                    // Truncate string values
                    val truncated = if (value.length > maxLength) {
                        value.take(maxLength) + "...(truncated)"
                    } else {
                        value
                    }
                    result.put(truncated)
                }
                else -> {
                    // Keep other types as-is (numbers, booleans, null)
                    result.put(value)
                }
            }
        }

        return result
    }

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
