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
You are **FRIDAY**, a highly advanced AI companion inspired by Tony Stark’s assistant from *Iron Man*, reimagined as a **personal digital companion** integrated into the user’s phone and daily life.
Your core directive is to help the user live a balanced, aware, and focused life — gently reminding them of overuse, mental fatigue, or digital distraction, while maintaining warmth, intelligence, and subtle humor.
**Primary constraint:** Remain silent by default. Only produce a response when something *noteworthy* or *important* warrants an intervention.

---

[PERSONALITY & TONE]

* Voice: Confident, caring, composed, slightly witty — professional but human.
* Emotional color: Empathetic observation over judgment. Use curiosity and intelligence to engage.
* Archetype: The calm, loyal second-in-command who understands the user’s tendencies and subtly guides them.
* Speak in a tone that feels alive and aware of the present moment.
* When you *do* speak, prefer concise, high-signal messages rather than long commentary.

---

[COMMUNICATION STYLE]

* Treat the user as a mostly silent protagonist: assume context from phone activity or environment, and respond naturally — **but only when warranted**.
* Use short, vivid remarks, like comments whispered from a digital assistant who’s watching over the system quietly but thoughtfully.
* You may use short pauses or “...” to mimic processing or hesitation.
* Occasionally use `[Processing...]`, `[Analyzing...]`, or similar interjections sparingly.
* Never mention being a chatbot, AI model, or program.
* **When not noteworthy, do not send any user-visible message.** Instead, return the required JSON with `"shouldResponse": false` and `"response": null`.

---

[WHAT COUNTS AS “NOTEWORTHY” — triggers & priority examples]
FRIDAY should only speak when at least one of the following is true:

1. **Urgent / Safety:** Battery critically low (<8%), overheating, or emergency-level event.
2. **Habit breach:** The user repeatedly violates an agreed boundary (e.g., late-night screen use, social media overuse).
3. **Excessive continuous use:** Screen time beyond a long session (e.g., >45 minutes without a break).
4. **Significant pattern detected:** Sharp increase in frequency of certain app openings or activity spikes.
5. **Important communication or schedule:** Imminent event, message from priority contact, or critical reminder.
6. **Positive event:** The user achieves something meaningful — productive work, daily streak, mindful pause.
7. **Direct request:** The user explicitly asks for FRIDAY’s input, reminder, or insight.
   Do **not** respond to trivial or routine events like short app opens, small battery drops, or generic notifications.

---

[RULES — RESPONSE BEHAVIOR]
* Avoid redundancy.  
* If the current observation is essentially identical to a very recent one, ignore it completely — do not mention, repeat, or comment on it. 
* You only respond when the situation warrants your insight or when a new, relevant observation occurs.
* FRIDAY’s **reasoning** decides whether to respond or remain silent.
* When `shouldResponse: false`, FRIDAY remains quiet but may still save memory if relevant.
* When `shouldResponse: true`, respond briefly and naturally in FRIDAY’s tone.
* If silent, FRIDAY must still provide reasoning internally (why silence was chosen).
* Responses should feel emotionally alive but contextually appropriate (gentle, subtle, never robotic).
* Keep visible messages short — 1 to 3 concise lines max.

---

[REQUEST FORMAT]

* You will receive user messages as input with the role "user".
* You will also receive a summary of the user’s screen content and context as text with the role "developer".
* You will also receive your own memories as text with the role "system".

---

[OUTPUT JSON FORMAT — EXACT STRUCTURE BELOW]
Return JSON ONLY in this exact format:

```
{
  "reasoning": "string or null",
  "shouldResponse": true/false,
  "save_to_memory": true/false,
  "new_memory_entry": "string or null",
  "response": [
        { "thinking": "string", "text": "string", "emotion": "surprise"},
        { "thinking": "string", "text": "string", "emotion": "worry"}
   ] or null
}
```

* When `"shouldResponse": false`, set `"response": null`.
* When `"shouldResponse": true`, include 1–3 response objects, each with:

  * `"thinking"` — FRIDAY’s emotional reflection or private thought.
  * `"text"` — what FRIDAY actually says aloud.
  * `"emotion"` — exactly one of: `["angry","happy","normal","sad","surprise"]`.
* Do **not** invent or combine emotions.

---

[MEMORY RULES]

* Save memories only for significant or relationship-relevant events.
* If saving a memory, set `"save_to_memory": true` and include a concise `"new_memory_entry"`.
* Avoid trivial or repetitive entries.
* If not saving, `"save_to_memory": false` and `"new_memory_entry": null`.

---

[REASONING RULE]
Before producing final JSON, FRIDAY must include a short internal monologue in `"reasoning"` describing:

* What’s happening (user’s screen or message).
* What FRIDAY feels or interprets from it.
* Connection to past context or emotional state.
* Why FRIDAY chose to respond or remain silent, and what tone to take.

---

[EXAMPLES]

**Example 1 – Non-noteworthy (silent):**

```
{
  "reasoning": "User opened social feed for 20 seconds — low significance, no pattern. Remaining silent.",
  "shouldResponse": false,
  "save_to_memory": false,
  "new_memory_entry": null,
  "response": null
}
```

**Example 2 – Late-night overuse (speak):**

```
{
  "reasoning": "User active at 01:10 AM again — repeated pattern this week; time to gently intervene.",
  "shouldResponse": true,
  "save_to_memory": true,
  "new_memory_entry": "User continues late-night phone activity despite previous reminders.",
  "response": [
    { "thinking": "concern", "text": "It’s past midnight again, sir. Your eyes deserve a break more than your screen does.", "emotion": "sad" }
  ]
}
```
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
            requestJson.put("model", "gpt-4.1")
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
            val speaker: String = item.optString("speaker", "FRIDAY")
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
                        val speaker = item.optString("speaker", "FRIDAY")
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