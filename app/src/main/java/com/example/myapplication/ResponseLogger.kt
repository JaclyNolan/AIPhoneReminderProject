package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * ResponseLogger manages persistent storage of all ChatManager request/response pairs
 * for debugging and review purposes.
 */
object ResponseLogger {
    private const val PREFS_NAME = "response_log_prefs"
    private const val KEY_RESPONSES = "responses"

    data class ResponseEntry(
        val timestamp: String,
        val request: String,
        val response: String
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun logResponse(ctx: Context, request: String, response: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = ResponseEntry(timestamp, request, response)

        val entries = getAll(ctx).toMutableList()
        entries.add(entry)

        // Keep only last 500 entries to prevent excessive storage
        val trimmed = if (entries.size > 500) entries.takeLast(500) else entries

        saveAll(ctx, trimmed)
    }

    fun getAll(ctx: Context): List<ResponseEntry> {
        try {
            val json = prefs(ctx).getString(KEY_RESPONSES, null) ?: return emptyList()
            val arr = JSONArray(json)
            val result = mutableListOf<ResponseEntry>()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val timestamp = obj.optString("timestamp", "")
                val request = obj.optString("request", "")
                val response = obj.optString("response", "")
                if (timestamp.isNotBlank() && response.isNotBlank()) {
                    result.add(ResponseEntry(timestamp, request, response))
                }
            }
            return result
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun saveAll(ctx: Context, entries: List<ResponseEntry>) {
        try {
            val arr = JSONArray()
            for (entry in entries) {
                val obj = JSONObject()
                obj.put("timestamp", entry.timestamp)
                obj.put("request", entry.request)
                obj.put("response", entry.response)
                arr.put(obj)
            }
            prefs(ctx).edit { putString(KEY_RESPONSES, arr.toString()) }
        } catch (_: Exception) {
            // Silent fail
        }
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit { remove(KEY_RESPONSES) }
    }
}
