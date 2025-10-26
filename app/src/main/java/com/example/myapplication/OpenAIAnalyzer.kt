@file:Suppress("unused")

package com.example.myapplication

import android.content.Context
import androidx.core.net.toUri
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
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Batch uploader that collects image bytes (ByteArray) and sends them in batches
 * to the configured OpenAI Responses endpoint using JSON payloads with base64-embedded images.
 *
 * Each image becomes its own `input` entry with a short prompt asking what's in the image.
 */
object OpenAIAnalyzer {
    private const val TAG = "OpenAIAnalyzer"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // queue now holds raw image bytes
    private val queue = mutableListOf<ByteArray>()
    private val flushing = AtomicBoolean(false)

    // Get the analyzer prompt from active character configuration
    private fun getAnalyzerPrompt(): String = Characters.ACTIVE.analyzerSystemPrompt

    // Backwards-compatible: read bytes from URI and delegate
    fun enqueueImage(ctx: Context, uriString: String) {
        val appCtx = ctx.applicationContext
        scope.launch {
            val input = openStreamForUri(appCtx, uriString)
            if (input == null) {
                Log.w(TAG, "enqueueImage: could not open $uriString")
                return@launch
            }
            try {
                val bytes = readAllBytes(input)
                enqueueImageBytes(appCtx, bytes)
            } catch (e: Exception) {
                Log.e(TAG, "enqueueImage read failed", e)
            } finally {
                try {
                    input.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    // New API: enqueue raw image bytes (e.g., compressed JPEG bytes)
    fun enqueueImageBytes(ctx: Context, imageBytes: ByteArray) {
        val appCtx = ctx.applicationContext
        scope.launch {
            synchronized(queue) {
                queue.add(imageBytes)
            }
            maybeFlush(appCtx)
        }
    }

    private fun queueSize(): Int = synchronized(queue) { queue.size }

    private suspend fun getPrefs(ctx: Context): PrefsHelper {
        return withContext(Dispatchers.Default) { PrefsHelper(ctx.applicationContext) }
    }

    private fun maybeFlush(ctx: Context) {
        scope.launch {
            val prefs = getPrefs(ctx)
            val batchSize = prefs.getOpenAIBatchSize().coerceAtLeast(1)
            if (queueSize() >= batchSize) {
                flushBatch(ctx, batchSize)
            }
        }
    }

    private fun flushBatch(ctx: Context, batchSize: Int) {
        if (!flushing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val listToSend = mutableListOf<ByteArray>()
                synchronized(queue) {
                    val take = minOf(batchSize, queue.size)
                    repeat(take) { listToSend.add(queue.removeAt(0)) }
                }
                if (listToSend.isEmpty()) return@launch
                Log.d(TAG, "Flushing batch of ${listToSend.size} images to OpenAI (JSON/base64)")
                val prefs = getPrefs(ctx)
                // Try prefs first, then fall back to environment asset (openai.env in assets)
                var apiKey = prefs.getOpenAIApiKey()?.takeIf { it.isNotBlank() }
                if (apiKey.isNullOrBlank()) {
                    val envKey = EnvLoader.getOpenAIApiKey(ctx)
                    if (!envKey.isNullOrBlank()) apiKey = envKey
                }
                val endpoint = prefs.getOpenAIEndpoint()
                if (apiKey.isNullOrBlank()) {
                    Log.w(
                        TAG,
                        "OpenAI API key not set in prefs or openai.env; skipping analysis for this batch"
                    )
                    return@launch
                }

                // Determine prompt: prefs -> env -> default
                val promptFromPrefs = prefs.getOpenAIPrompt()?.takeIf { it.isNotBlank() }
                val promptFromEnv = EnvLoader.getOpenAIPrompt(ctx)?.takeIf { it.isNotBlank() }
                val chosenPrompt: String = when {
                    !promptFromPrefs.isNullOrBlank() -> promptFromPrefs
                    !promptFromEnv.isNullOrBlank() -> promptFromEnv
                    else -> getAnalyzerPrompt()
                }
                Log.d(TAG, "Using OpenAI prompt: ${chosenPrompt.take(120)}")

                sendBatchAsJson(ctx, listToSend, endpoint, apiKey, chosenPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing OpenAI batch", e)
            } finally {
                flushing.set(false)
            }
        }
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(4 * 1024)
        var n: Int
        val bis = BufferedInputStream(input)
        while (bis.read(data, 0, data.size).also { n = it } != -1) {
            buffer.write(data, 0, n)
        }
        return buffer.toByteArray()
    }

    private fun openStreamForUri(ctx: Context, uriString: String): InputStream? {
        val appCtx = ctx.applicationContext
        return try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://") || uriString.startsWith(
                    "/"
                )
            ) {
                val u = uriString.toUri()
                if (u.scheme == null || u.scheme == "file") {
                    // local file path
                    val path = if (u.scheme == "file") u.path else uriString
                    java.io.FileInputStream(path)
                } else {
                    appCtx.contentResolver.openInputStream(u)
                }
            } else {
                // treat as path
                java.io.FileInputStream(uriString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for $uriString", e)
            null
        }
    }

    private fun sendBatchAsJson(ctx: Context, imageByteList: List<ByteArray>, endpoint: String, apiKey: String, prompt: String) {
        var connection: HttpURLConnection? = null
        try {
            // Fetch current memory pool and build a compact memory string to include with each input
            val memoryList = MemoryManager.getAll(ctx)
            val maxEntries = 30
            val recent = if (memoryList.size > maxEntries) memoryList.takeLast(maxEntries) else memoryList

            val memoryBuilder = StringBuilder()
            // include current time so the model can reason about recency and avoid repeating recent memories
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(Date())
            memoryBuilder.append("CURRENT_TIME: ").append(currentTime).append('\n')
             if (recent.isNotEmpty()) {
                 memoryBuilder.append("MEMORIES:\n")
                 for (m in recent) {
                     // each MemoryEntry has timestamp and entry fields
                     memoryBuilder.append("[")
                         .append(m.timestamp)
                         .append("] ")
                         .append(m.entry.replace('\n', ' '))
                         .append('\n')
                 }
             }

            // Limit memory text size to avoid huge payloads
            val memoryText = memoryBuilder.toString().take(18_000)

            // 🔹 Log the memory text before sending
            Log.d("MemoryDebug", "Sending memory:\n$memoryText")

            // Build request JSON: input is an array of entries
            val inputArray = JSONArray()
            // Insert the prompt as a single system message at the start of the input array
            // and include the CURRENT_TIME + MEMORIES inside the same system message so the model
            // receives prompt + memories as system-level context.
            if (prompt.isNotBlank()) {
                val systemObj = JSONObject()
                systemObj.put("role", "system")
                val sysContent = JSONArray()

                // system prompt first
                val sysText = JSONObject()
                sysText.put("type", "input_text")
                sysText.put("text", prompt)
                sysContent.put(sysText)

                // then memory text (if present) so it's available as system context
                if (memoryText.isNotBlank()) {
                    val memSys = JSONObject()
                    memSys.put("type", "input_text")
                    memSys.put("text", memoryText)
                    sysContent.put(memSys)
                }

                systemObj.put("content", sysContent)
                inputArray.put(systemObj)
            }
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

            if (inputArray.length() == 0) {
                Log.w(TAG, "No images to send")
                return
            }

            val requestJson = JSONObject()
            requestJson.put("model", "gpt-4.1-mini")
            requestJson.put("input", inputArray)

            val payload = requestJson.toString().toByteArray(Charsets.UTF_8)

            Log.d(TAG, "OpenAI Analyzer requestJson = $requestJson")


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
//            Log.d(TAG, "OpenAI JSON response code=$code, body=${respText.take(10000)}")

            // Parse JSON output
            try {
                if (respText.isNotBlank()) {
                    val json = JSONObject(respText)

                    // Responses API can wrap outputs; look for structured JSON
                    var structured: JSONObject? = null

                    if (json.has("output_text")) {
                        // Model may return JSON string as output_text
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
                        val summary = structured.optString("summary")
                        val saveToMemory = structured.optBoolean("save_to_memory", false)
                        val newMemoryEntry = if (!structured.isNull("new_memory_entry"))
                            structured.optString("new_memory_entry") else null
                        // New flag: developer may simply indicate whether the chat model should respond
                        val shouldResponse = structured.optBoolean("shouldResponse", false)

                        Log.d(TAG, "Summary: $summary")
                        Log.d(TAG, "Save to memory: $saveToMemory")
                        Log.d(TAG, "New memory entry: $newMemoryEntry")
                        Log.d(TAG, "shouldResponse: $shouldResponse")

                        if (saveToMemory && !newMemoryEntry.isNullOrEmpty()) {
                            addMemoryEntry(ctx, newMemoryEntry)
                        }

                        if (shouldResponse) {
                            // No textual response provided, but developer requests a response.
                            // Build a concise suggestion that includes the summary and the new memory entry so the chat model
                            // can craft a response in character and reference the memory when appropriate.
                            val suggestionSb = StringBuilder()
                            suggestionSb.append("Phone screen analyzer suggests responding to the user. Screen summary: ")
                            suggestionSb.append(summary.ifEmpty { "(no summary)" })
                            if (!newMemoryEntry.isNullOrEmpty()) {
                                suggestionSb.append(". New memory entry: ")
                                suggestionSb.append(newMemoryEntry)
                            }
                            val suggestion = suggestionSb.toString()
                            try {
                                ChatManager.handleDeveloperSuggestion(ctx, suggestion)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to forward developer shouldResponse to ChatManager", e)
                            }
                        }
                    } else {
                        Log.w(TAG, "No structured JSON found in model output: $respText")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse structured JSON from OpenAI", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending JSON batch to OpenAI", e)
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
