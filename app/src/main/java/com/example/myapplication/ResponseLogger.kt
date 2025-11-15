package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * ResponseLogger manages persistent storage of request/response pairs
 * from both ChatManager and AnalyzerAgent for debugging and review purposes.
 */
object ResponseLogger {
    private const val PREFS_NAME = "response_log_prefs"
    private const val KEY_CHAT_RESPONSES = "chat_responses"
    private const val KEY_ANALYZER_RESPONSES = "analyzer_responses"
    private const val KEY_UPCP_RESPONSES = "upcp_responses"
    private const val KEY_PA_RESPONSES = "pa_responses"

    enum class LogType {
        CHAT_MANAGER,
        ANALYZER_AGENT,
        USAGE_PATTERN_CONTEXT_PROVIDER,
        PERSONALITY_AGENT
    }

    data class ResponseEntry(
        val timestamp: String,
        val request: String,
        val response: String,
        val logType: LogType,
        val promptTokens: Int? = null,
        val completionTokens: Int? = null,
        val totalTokens: Int? = null
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Log a response from ChatManager with optional token usage.
     */
    fun logResponse(ctx: Context, request: String, response: String, logType: LogType = LogType.CHAT_MANAGER,
                    promptTokens: Int? = null, completionTokens: Int? = null, totalTokens: Int? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = ResponseEntry(timestamp, request, response, logType, promptTokens, completionTokens, totalTokens)

        val entries = getByType(ctx, logType).toMutableList()
        entries.add(entry)

        // Keep only last 500 entries to prevent excessive storage
        val trimmed = if (entries.size > 500) entries.takeLast(500) else entries

        saveByType(ctx, trimmed, logType)
    }

    /**
     * Backwards-compatible simple overload (no tokens) for existing callers
     */
    fun logResponse(ctx: Context, request: String, response: String) {
        logResponse(ctx, request, response, LogType.CHAT_MANAGER, null, null, null)
    }

    /**
     * Get all responses regardless of type
     */
    fun getAll(ctx: Context): List<ResponseEntry> {
        val chatEntries = getByType(ctx, LogType.CHAT_MANAGER)
        val analyzerEntries = getByType(ctx, LogType.ANALYZER_AGENT)
        val upcpEntries = getByType(ctx, LogType.USAGE_PATTERN_CONTEXT_PROVIDER)
        val paEntries = getByType(ctx, LogType.PERSONALITY_AGENT)
        return (chatEntries + analyzerEntries + upcpEntries + paEntries).sortedBy { it.timestamp }
    }

    /**
     * Get responses by specific type
     */
    fun getByType(ctx: Context, logType: LogType): List<ResponseEntry> {
        val key = when (logType) {
            LogType.CHAT_MANAGER -> KEY_CHAT_RESPONSES
            LogType.ANALYZER_AGENT -> KEY_ANALYZER_RESPONSES
            LogType.USAGE_PATTERN_CONTEXT_PROVIDER -> KEY_UPCP_RESPONSES
            LogType.PERSONALITY_AGENT -> KEY_PA_RESPONSES
        }

        try {
            val json = prefs(ctx).getString(key, null) ?: return emptyList()
            val arr = JSONArray(json)
            val result = mutableListOf<ResponseEntry>()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val timestamp = obj.optString("timestamp", "")
                val request = obj.optString("request", "")
                val response = obj.optString("response", "")
                val prompt = if (obj.has("prompt_tokens")) obj.optInt("prompt_tokens", -1).takeIf { it >= 0 } else null
                val completion = if (obj.has("completion_tokens")) obj.optInt("completion_tokens", -1).takeIf { it >= 0 } else null
                val total = if (obj.has("total_tokens")) obj.optInt("total_tokens", -1).takeIf { it >= 0 } else null
                if (timestamp.isNotBlank() && response.isNotBlank()) {
                    result.add(ResponseEntry(timestamp, request, response, logType, prompt, completion, total))
                }
            }
            return result
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun saveByType(ctx: Context, entries: List<ResponseEntry>, logType: LogType) {
        try {
            val arr = JSONArray()
            for (entry in entries) {
                val obj = JSONObject()
                obj.put("timestamp", entry.timestamp)
                obj.put("request", entry.request)
                obj.put("response", entry.response)
                entry.promptTokens?.let { obj.put("prompt_tokens", it) }
                entry.completionTokens?.let { obj.put("completion_tokens", it) }
                entry.totalTokens?.let { obj.put("total_tokens", it) }
                arr.put(obj)
            }
            val key = when (logType) {
                LogType.CHAT_MANAGER -> KEY_CHAT_RESPONSES
                LogType.ANALYZER_AGENT -> KEY_ANALYZER_RESPONSES
                LogType.USAGE_PATTERN_CONTEXT_PROVIDER -> KEY_UPCP_RESPONSES
                LogType.PERSONALITY_AGENT -> KEY_PA_RESPONSES
            }
            prefs(ctx).edit { putString(key, arr.toString()) }
        } catch (_: Exception) {
            // Silent fail
        }
    }

    /**
     * Clear all logs
     */
    fun clear(ctx: Context) {
        prefs(ctx).edit {
            remove(KEY_CHAT_RESPONSES)
            remove(KEY_ANALYZER_RESPONSES)
            remove(KEY_UPCP_RESPONSES)
            remove(KEY_PA_RESPONSES)
        }
    }

    /**
     * Clear logs by type
     */
    fun clearByType(ctx: Context, logType: LogType) {
        val key = when (logType) {
            LogType.CHAT_MANAGER -> KEY_CHAT_RESPONSES
            LogType.ANALYZER_AGENT -> KEY_ANALYZER_RESPONSES
            LogType.USAGE_PATTERN_CONTEXT_PROVIDER -> KEY_UPCP_RESPONSES
            LogType.PERSONALITY_AGENT -> KEY_PA_RESPONSES
        }
        prefs(ctx).edit { remove(key) }
    }
}
