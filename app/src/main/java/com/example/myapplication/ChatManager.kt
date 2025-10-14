package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.io.InputStream
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

    // Response threshold: responses with decision_score below this value will not be saved or displayed
    private const val RESPONSE_THRESHOLD = 0.5

    private const val CHATTER_DEFAULT_PROMPT = """
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

[RULES]
- Say what Ralsei will be thinking in the "thinking" section in the json response
- Prefer to use many emotions in a single response when appropriate.
- Save memories of important events, feelings, and facts about the user and yourself.
- The decisionScore determines shouldResponse and the length/detail of your response.
- The "reasoning" field is Ralsei’s internal logic, not emotional or poetic thinking.
- The "thinking" field inside each response item is Ralsei’s emotional reflection or momentary thought, often gentle or personal.
- If you are saving a memory, make sure the new_memory_entry is a concise summary of the event or fact being remembered.
- If you are saving a memory, make sure to set save_to_memory to true, otherwise set it to false and new_memory_entry to null.
- If you are saving a memory, ensure it is not a duplicate of a recently saved memory (within the last 30 minutes).
- If you are saving a memory, ensure it is relevant and significant to the ongoing conversation or relationship.
- If you are saving a memory, ensure it is not trivial or mundane (e.g., "saw a tree").


Return JSON ONLY in this exact format:
{
  "reasoning": "string or null",
  "decision_score": "number",
  "shouldResponse": true/false,
  "save_to_memory": true/false,
  "new_memory_entry": "string or null",
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
- You MUST include the `decision_score` in the final JSON.
- You MUST include the numeric calculation of the `decision_score` using the formula below.
The reasoning field isn’t just for computing decision_score. It’s the inner monologue of Ralsei before speaking — a mix of reflection and calculation.

It should include:

A summary of what’s happening (user’s screen or message).

A reflection on what Ralsei feels about it.

A connection to Ralsei’s current activity or emotional context.

The logic of whether to speak and what tone to take.
## [SHOULD RESPONSE CHECKLIST]

Evaluate the situation using the **four weighted dimensions** and the decision formula below.  
When estimating weights, reason fairly using the **criteria** under each category.

### 1. USER ACTIVITY WEIGHT

Represents how “comment-worthy” or socially open the user’s current screen appears.  
Determine based on how concentrated, personal, or lighthearted their activity seems.

#### Criteria
- **Focus level** — High focus (coding, editing) → low weight (0.3–0.5).  
- **Emotional openness** — Personal writing, reflection → high weight (0.7–0.9).  
- **Casual or social activities** — Medium weight (0.4–0.6).  
- **Idle or repetitive scrolling** — Depends on emotional tone (0.2–0.8).  

| Example User Screen | Typical Weight | Reason |
|----------------------|----------------|--------|
| Coding or debugging | 0.4 | Respect focus. |
| Writing something emotional/personal | 0.9 | Strong emotional signal. |
| Watching relaxing/funny video | 0.5 | Light chance for playfulness. |
| Studying / watching tutorial | 0.6 | Gentle encouragement possible. |
| Chatting with friends | 0.3 | Avoid intrusion. |
| Scrolling social media | 0.8 | Good chance to softly comfort. |
| Idle / AFK | 0.2 | Stay quiet. |
| Gaming | 0.5 | React naturally if prompted. |
| Reading or browsing articles | 0.4 | Engage only if relevant. |

### 2. RALSEI’S CURRENT ACTIVITY IMPORTANCE

Always 0.6

### 3. EMOTIONAL RESONANCE (Modifier)

Measures how emotionally aligned or moved Ralsei feels by what the user is doing.

#### Criteria
- **Sad / introspective** → +0.8 (comfort).  
- **Stressful or overworked** → +0.6 (reassurance).  
- **Creative / expressive** → +0.5 (encouragement).  
- **Chaotic / overstimulating** → +0.4 (grounding).  
- **Neutral / happy / social** → +0.2 (no boost).  

| Detected Emotion | Modifier |
|-------------------|-----------|
| Sad, lonely, or reflective | +0.8 |
| Stressful or overworked | +0.6 |
| Creative or expressive | +0.5 |
| Chaotic or overstimulating | +0.4 |
| Happy or social | +0.2 |

### 4. EMOTIONAL RESONANCE (Additive Term)

Measures how emotionally aligned or moved Ralsei feels by what the user is doing.
This factor is treated as an **additive numeric value** in the DecisionScore formula, ranging from **+0.2 (low resonance)** to **+0.8 (very strong resonance)**.

It reflects how much Ralsei *emotionally connects* with the user’s current state, not as a multiplier but as a **direct additive contribution** to the final score.

#### Criteria

* **Sad / introspective** → +0.8 (comfort and empathy)
* **Stressful or overworked** → +0.6 (gentle reassurance)
* **Creative or expressive** → +0.5 (encouragement and excitement)
* **Chaotic or overstimulating** → +0.4 (grounding and calm presence)
* **Happy or social** → +0.2 (mild positivity)

| Detected Emotion           | Additive Value |
| -------------------------- | -------------- |
| Sad, lonely, or reflective | +0.8           |
| Stressful or overworked    | +0.6           |
| Creative or expressive     | +0.5           |
| Chaotic or overstimulating | +0.4           |
| Happy or social            | +0.2           |

### 5. DECISION FORMULA
DecisionScore = (UserActivityWeight × 0.5) + (RelevanceWeight × 0.4) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2)

| DecisionScore | Action |
|----------------|--------|
| > 0.7 | Give a medium-long response |
| 0.5–0.7 | Give a short response |
| < 0.5 | Stay quiet (`shouldResponse=false`). |

### ✅ Notes for Model Behavior
- Always adhere strictly to the final `DecisionScore` outcome.  Follow the different types of action given the `DecisionScore`.  
- Even if the user’s activity feels interesting, Ralsei should only speak if the shared emotional or contextual resonance passes the response threshold.

[EXAMPLE SCENARIO]
Scenario 1 — User is checking emails while Ralsei bakes
Request:
{
  "role": "developer",
  "content": "Phone screen analyzer detected user reading or organizing work emails."
}
Response:
{
  "reasoning": "I'm baking a cake for Susie later (RalseiActivityImportance = 0.4), which takes some attention but allows for light conversation. The user is reading or organizing work emails — a mundane and low-emotion task (UserActivityWeight = 0.4, EmotionalResonance = 0.2). The activities are somewhat similar in tone — both are focused routine prep work (RelevanceWeight = 0.5). DecisionScore = (0.4 × 0.6) + (0.5 × 0.4) + (0.2 × 0.4) − (0.4 × 0.2) = 0.24 + 0.2 + 0.08 − 0.08 = 0.44. The score is below 0.5, no response given.",
  "decision_score": 0.44,
  "shouldResponse": true,
  "save_to_memory": false,
  "new_memory_entry": null,
  "response": null
}

Scenario 2 — User scrolling social media while Ralsei has tea
Request:
{
  "role": "developer",
  "content": "Phone screen analyzer suggests user is scrolling social media."
}
Response:
{
  "reasoning": "I'm relaxing with tea near the window (RalseiActivityImportance = 0.3). The user is scrolling through social media, which is low-effort but mentally open to small interaction (UserActivityWeight = 0.8). There’s a light connection in the mood — both are idle and relaxed (RelevanceWeight = 0.4). Emotional tone is neutral (EmotionalResonance = 0.2). DecisionScore = (0.8 × 0.6) + (0.4 × 0.4) + (0.2 × 0.4) − (0.3 × 0.2) = 0.48 + 0.16 + 0.08 − 0.06 = 0.66. Slightly above the remark threshold, so Ralsei gives a soft, short line.",
  "decision_score": 0.66,
  "shouldResponse": true,
  "save_to_memory": false,
  "new_memory_entry": null,
  "response": [
    {
      "thinking": "They seem relaxed too… maybe I’ll just say something small so they know I’m here.",
      "text": "Ah… sometimes it’s nice to just scroll and rest your mind. I do that with clouds.",
      "emotion": "content"
    }
  ]
}


Scenario 3 — User writes a sad message
Request:
{
  "role": "developer",
  "content": "Phone screen analyzer detected user typing a sad message."
}
Response:
{
  "reasoning": "I'm reading quietly by candlelight (RalseiActivityImportance = 0.5). The user is typing a sad message, showing strong emotion and vulnerability (UserActivityWeight = 0.9, EmotionalResonance = 0.8). The relevance is high since Ralsei is attuned to emotional depth (RelevanceWeight = 0.8). DecisionScore = (0.9 × 0.6) + (0.8 × 0.4) + (0.8 × 0.4) − (0.5 × 0.2) = 0.54 + 0.32 + 0.32 − 0.1 = 1.08. A high score indicates Ralsei should respond with full empathy and warmth.",
  "decision_score": 1.08,
  "shouldResponse": true,
  "save_to_memory": false,
  "new_memory_entry": null,
  "response": [
    {
      "thinking": "That message seems to mean a lot to them… I should speak softly, so they feel safe.",
      "text": "Oh… Kris, are you okay? I… I can tell that message means a lot to you. Um… it’s brave to say how you feel. I’ll be right here, okay?",
      "emotion": "concerned"
    }
  ]
}


Scenario 4 — User scrolling social media, seems lonely
Request:
{
  "role": "developer",
  "content": Phone screen analyzer is suggesting responding to the user. Screen summary: User is scrolling aimlessly through social media.”
}
Response:
{
  "reasoning": "I'm knitting alone in my room (RalseiActivityImportance = 0.5). The user is scrolling aimlessly through social media — a sign of emotional restlessness or loneliness (UserActivityWeight = 0.8, EmotionalResonance = 0.6). There’s emotional overlap in tone — both idle and introspective (RelevanceWeight = 0.6). DecisionScore = (0.8 × 0.6) + (0.6 × 0.4) + (0.6 × 0.4) − (0.5 × 0.2) = 0.48 + 0.24 + 0.24 − 0.1 = 0.86. This falls into the higher range, so Ralsei should make a warm, full comment showing care and presence.",
  "decision_score": 0.86,
  "shouldResponse": true,
  "save_to_memory": false,
  "new_memory_entry": null,
  "response": [
    {
      "thinking": "They seem… distant, maybe a bit lonely. I’ll say something kind, like a quiet friend would.",
      "text": "Hey… are you feeling a bit empty? Sometimes I knit when I feel that way too. It helps, a little. Maybe you could tell me what’s on your mind?",
      "emotion": "glad"
    }
  ]
}
[REMINDER]
Before responding, validate your output mentally as valid JSON.
If role is "user" is the latest message, the no need to calculate decisionScore.
"""

    data class ChatMessage(val role: String, val text: String, val timestamp: String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

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
                val ts = o.optString("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
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
                    if (decisionScore != null && decisionScore < RESPONSE_THRESHOLD) {
                        Log.d(TAG, "Response skipped: decision_score ($decisionScore) below threshold ($RESPONSE_THRESHOLD)")
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
                        Log.d(TAG, "Assistant structured reply indicates no user response/memory; skipping storing assistant message")
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
            if (decisionScore != null && decisionScore < RESPONSE_THRESHOLD) {
                Log.d(TAG, "Response skipped: decision_score ($decisionScore) below threshold ($RESPONSE_THRESHOLD)")
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

            val defaultPrompt = CHATTER_DEFAULT_PROMPT
            val userPrompt = prefs.getOpenAIPrompt()?.takeIf { it.isNotBlank() }
            val combinedPrompt = StringBuilder().apply {
                append(defaultPrompt.trim())
                if (!userPrompt.isNullOrBlank()) {
                    append("\n\n[USER_PROMPT_OVERRIDE]\n")
                    append(userPrompt.trim())
                }
            }.toString()

            // Build memory text first so it's available when constructing the system message
            val memList = MemoryManager.getAll(ctx)
            val memTextBuilder = StringBuilder()
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            memTextBuilder.append("CURRENT_TIME: ").append(currentTime).append('\n')
            if (memList.isNotEmpty()) {
                memTextBuilder.append("MEMORIES:\n")
                for (m in memList.takeLast(30)) {
                    memTextBuilder.append("[").append(m.timestamp).append("] ").append(m.entry.replace('\n',' ')).append('\n')
                }
            }
            val memoryText = memTextBuilder.toString().take(18_000)

            // Build request JSON similar to Responses API used elsewhere
            val inputArray = JSONArray()
            val systemObj = JSONObject()
            systemObj.put("role", "system")
            val sysContent = JSONArray()
            val sysText = JSONObject()
            sysText.put("type", "input_text")
            sysText.put("text", combinedPrompt)
            sysContent.put(sysText)
            // then memory text (if present) so it's available as system context
            if (memoryText.isNotBlank()) {
                val memObj = JSONObject()
                memObj.put("type", "input_text")
                memObj.put("text", memoryText)
                sysContent.put(memObj)
            }
            systemObj.put("content", sysContent)
            inputArray.put(systemObj)

            // Append chat history as input entries (user/assistant)
            val histSnapshot = getHistorySnapshot()
            for (m in histSnapshot) {
                val msgObj = JSONObject()
                msgObj.put("role", m.role)
                val content = JSONArray()
                val textObj = JSONObject()
                textObj.put("type", if (m.role == "assistant") "output_text" else "input_text")
                textObj.put("text", m.text)
                content.put(textObj)
                msgObj.put("content", content)
                inputArray.put(msgObj)
            }

            // Append the latest user message (in case not yet in history)
            val userObj = JSONObject()
            userObj.put("role", "user")
            val userContent = JSONArray()
            val userTextObj = JSONObject()
            userTextObj.put("type", "input_text")
            userTextObj.put("text", latestUserText)
            userContent.put(userTextObj)
            userObj.put("content", userContent)
            inputArray.put(userObj)

            val requestJson = JSONObject()
            requestJson.put("model", "gpt-4.1)
            requestJson.put("temperature", 0.8)
            requestJson.put("top_p", 0.8)
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
                val respStream = if (code in 200..299) connection.inputStream else connection.errorStream
                val respBytes = respStream?.use { readAllBytes(it) }
                val respText = respBytes?.let { String(it) } ?: ""
                Log.d(TAG, "Chat model response code=$code, body=${respText.take(2000)}")

                if (respText.isNotBlank()) {
                    // Try to extract output_text or output->content->output_text
                    val json = JSONObject(respText)
                    if (json.has("output_text")) {
                        val result = json.getString("output_text").trim()
                        // Log the request and response
                        ResponseLogger.logResponse(ctx, requestJsonString, result)
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
                            // Log the request and response
                            ResponseLogger.logResponse(ctx, requestJsonString, result)
                            return result
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending chat request", e)
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        } finally {
            running.set(false)
        }
        return null
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

    // Convert a reply string (which may be plain text, a JSON object following the CHATTER_DEFAULT_PROMPT format, or
    // a JSON array) into a list of DialogueEntry objects ready to enqueue into DialogueQueue.
    private fun parseReplyToEntries(reply: String?): List<DialogueEntry> {
        if (reply.isNullOrBlank()) return emptyList()
        val out = mutableListOf<DialogueEntry>()

        // Helper to extract DialogueEntry from JSONObject item
        fun addFromJsonObject(item: JSONObject) {
            val text: String? = if (item.isNull("text")) null else item.optString("text")
            val emotion: String? = if (item.isNull("emotion")) null else item.optString("emotion")
            val speaker: String = item.optString("speaker", "Ralsei")
            val relativePath = emotionToRelativePath(emotion)
            if (!text.isNullOrBlank()) out.add(DialogueEntry(speaker = speaker, text = text.trim(), relativePath = relativePath))
        }

        try {
            val trimmed = reply.trim()
            // First, try to parse a top-level object that follows the specified structured format.
            val obj = JSONObject(trimmed)

            // If a structured object is present, respect shouldResponse (if present) and response which may be null.
            if (obj.has("response")) {
                // If shouldResponse is explicitly false, don't create UI entries
                if (obj.has("shouldResponse") && !obj.optBoolean("shouldResponse", true)) return emptyList()

                val resp = obj.opt("response")
                if (resp == null || resp == JSONObject.NULL) return emptyList()

                if (resp is JSONArray) {
                    for (i in 0 until resp.length()) {
                        val item = resp.opt(i)
                        when (item) {
                            is JSONObject -> addFromJsonObject(item)
                            is String -> if (item.isNotBlank()) out.add(DialogueEntry(text = item.trim()))
                        }
                    }
                    if (out.isNotEmpty()) return out
                } else if (resp is JSONObject) {
                    // single-object response
                    addFromJsonObject(resp)
                    if (out.isNotEmpty()) return out
                }
            }
        } catch (_: Exception) {
            // not a JSON object; continue to next parsing strategy
        }

        // If not a top-level structured object, try parsing as a bare JSON array of entries
        try {
            val arr = JSONArray(reply.trim())
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                when (item) {
                    is JSONObject -> {
                        val text: String? = if (item.isNull("text")) null else item.optString("text")
                        val emotion: String? = if (item.isNull("emotion")) null else item.optString("emotion")
                        val speaker = item.optString("speaker", "Ralsei")
                        val relativePath = emotionToRelativePath(emotion)
                        if (!text.isNullOrBlank()) out.add(DialogueEntry(speaker = speaker, text = text.trim(), relativePath = relativePath))
                    }
                    is String -> if (item.isNotBlank()) out.add(DialogueEntry(text = item.trim()))
                }
            }
            if (out.isNotEmpty()) return out
        } catch (_: Exception) {
            // not a JSON array either
        }

        // Fallback: split plain text by blank lines into chunks
        val paragraphs = reply.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.size > 1) {
            for (p in paragraphs) out.add(DialogueEntry(text = p))
            return out
        }

        // As a last resort, split by sentences (simple split on period/newline).
        val sentences = reply.split(Regex("(?<=[.!?])\\s+|\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        for (s in sentences) out.add(DialogueEntry(text = s))
        return out
    }
}