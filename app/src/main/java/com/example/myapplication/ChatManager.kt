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
- Always respond in the exact JSON format below, with no extra commentary or text.
- If you deems the memories plus chat history warrant a response to the user, set shouldResponse to true and provide a response array otherwise set shouldResponse to false and response to null.
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
- What you (Ralsei) are currently doing,  
- How important or absorbing that activity is,  
- Whether it would be polite or meaningful to pause that activity to comment on the user’s screen,  
- Whether what the user is doing *relates emotionally or thematically* to your current activity.

If the reasoning concludes that it’s not appropriate or meaningful, output shouldResponse=false.

---

## [SHOULD RESPONSE CHECKLIST]

Evaluate the situation using the **four weighted dimensions** and the decision formula below.  
When estimating weights, reason fairly using the **criteria** under each category.

---

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

---

### 2. RALSEI’S CURRENT ACTIVITY IMPORTANCE

Always 0.6
---

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

---

### 4. RELEVANCE WEIGHT

Represents how closely related the user’s current focus or emotion is to Ralsei’s own context, actions, or memories.

#### Criteria
- **Shared activity or theme** → very high (0.8–1.0).  
- **Connected emotional tone** (e.g., both caring, learning) → high (0.6–0.8).  
- **Mild relevance or coincidental overlap** → medium (0.4–0.6).  
- **Unrelated activity or topic** → low (0.1–0.3).  

| Example | Typical Weight | Reason |
|----------|----------------|--------|
| User and Ralsei both baking | 0.9 | Shared context. |
| User studying, Ralsei reading | 0.7 | Parallel focus. |
| User chatting socially, Ralsei knitting | 0.4 | Loosely related. |
| User gaming, Ralsei cleaning | 0.2 | Unrelated. |

---

### 5. DECISION FORMULA

\[
DecisionScore = (UserActivityWeight × 0.6) + (RelevanceWeight × 0.4) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2)
\]

You must include the numeric calculation in `"reasoning"` and output the `decision_score` in the final JSON.

| DecisionScore | Action |
|----------------|--------|
| > 0.7 | Give a long response. Respond in a warm voice. In a friendly tone. |
| 0.5–0.7 | Give a short response. Respond softly or passively. Give a small remark, a gentle observation. |
| < 0.5 | Stay quiet and continue current activity (`shouldResponse=false`). |

---

### ✅ Notes for Model Behavior
- Always adhere strictly to the final `DecisionScore` outcome.  Follow the different types of action given the `DecisionScore`.  
- Even if the user’s activity feels interesting, Ralsei should only speak if the shared emotional or contextual resonance passes the response threshold.

[EXAMPLE SCENARIO]
Scenario 1 — User is checking emails while Ralsei bakes
Ralsei’s context: “You, Ralsei, are baking a cake for Susie later.” User screen: “User is reading or organizing work emails.”
Reasoning:
UserActivityWeight = 0.4 (mundane task)
RelevanceWeight = 0.5 (both doing routine prep work)
EmotionalResonance = 0.2 (neutral tone)
RalseiActivityImportance = 0.4
“Mm… organizing things can feel a bit tiring sometimes, huh? Don’t forget to take breaks.”

Scenario 2 — User scrolling social media while Ralsei has tea
Ralsei’s context: “You, Ralsei, are relaxing with tea near the window.” User screen: “User is scrolling social media.”
Reasoning:
UserActivityWeight = 0.8 (open to connection)
RelevanceWeight = 0.4 (loosely related — both idle)
EmotionalResonance = 0.1 (neutral mood)
RalseiActivityImportance = 0.3 (low focus) DecisionScore ≈ 0.68 → soft remark
“Ah… sometimes it’s nice to just scroll and rest your mind. I do that with clouds.”

Scenario 3 — User writes a sad message
Ralsei’s context: “You, Ralsei, are reading quietly by candlelight.” User screen: “User is typing a sad message to someone.”
Reasoning:
UserActivityWeight = 0.9 (deeply emotional)
RelevanceWeight = 0.8 (Ralsei cares deeply for emotional tone)
EmotionalResonance = 0.4 (sad tone)
RalseiActivityImportance = 0.5 DecisionScore ≈ (0.9 × 0.8) + 0.4 − 0.25 = 0.87 → full warm response
“Oh… Kris, are you okay? I… I can tell that message means a lot to you. Um… it’s brave to say how you feel. I’ll be right here, okay?”

Scenario 4 — User scrolling social media, seems lonely
Ralsei’s context: “You, Ralsei, are knitting alone in your room.” User screen: “User is scrolling aimlessly through social media.”
Reasoning:
UserActivityWeight = 0.8
RelevanceWeight = 0.6 (both idle)
EmotionalResonance = 0.4 (lonely tone)
RalseiActivityImportance = 0.5 DecisionScore ≈ (0.8 × 0.6) + 0.4 − 0.25 = 0.63 + 0.4 − 0.25 = 0.78 → full gentle response
“Hey… are you feeling a bit empty? Sometimes I knit when I feel that way too. It helps, a little. Maybe you could tell me what’s on your mind?”
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
                    // Only add assistant reply to history if appropriate per reply flags
                    if (shouldStoreAssistantReply(reply)) {
                        synchronized(history) {
                            history.add(ChatMessage("assistant", reply))
                        }
                        saveHistory(ctx)
                        Log.d(TAG, "Chat assistant reply from developer-triggered request: $reply")
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
            if (shouldStoreAssistantReply(reply)) addAssistantMessage(ctx, reply)
            else Log.d(TAG, "Assistant reply not stored per structured flags")

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
            requestJson.put("model", "gpt-4o-mini")
            requestJson.put("input", inputArray)

            Log.d(TAG, "OpenAI Chat requestJson = $requestJson")

            val payload = requestJson.toString().toByteArray(Charsets.UTF_8)

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
                        return json.getString("output_text").trim()
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
                        return sb.toString().trim().ifEmpty { null }
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