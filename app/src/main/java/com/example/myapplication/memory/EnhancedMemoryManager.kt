package com.example.myapplication.memory

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * EnhancedMemoryManager: Implements the comprehensive memory architecture
 * - SceneTimeline (immutable chronological scene summaries)
 * - CondensedMemories (emotional/important facts, versioned)
 * - RecentIntents (rotating buffer of assistant intents)
 * - DialogueSummaries (periodic compression of chat history)
 */
object EnhancedMemoryManager {
    private const val TAG = "EnhancedMemoryManager"
    private const val PREFS_NAME = "enhanced_memory_prefs"
    private const val KEY_SCENE_TIMELINE = "scene_timeline"
    private const val KEY_CONDENSED_MEMORIES = "condensed_memories"
    private const val KEY_RECENT_INTENTS = "recent_intents"
    private const val KEY_DIALOGUE_SUMMARIES = "dialogue_summaries"
    private const val KEY_CONDENSED_VERSION = "condensed_version"

    // Configurable thresholds
    private const val RECENT_INTENT_WINDOW_MINUTES = 30
    private const val MAX_TIMELINE_ENTRIES = 100
    private const val MAX_INTENTS = 20

    private val sceneTimeline = mutableListOf<SceneTimelineEntry>()
    private val condensedMemories = mutableListOf<CondensedMemoryItem>()
    private val recentIntents = mutableListOf<RecentIntent>()
    private val dialogueSummaries = mutableListOf<String>()
    private var condensedVersion = 1
    private var initialized = false

    data class SceneTimelineEntry(
        val timestamp: String,
        val sceneLabel: String,
        val shortText: String,
        val confidence: Double
    ) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("ts", timestamp)
            obj.put("scene_label", sceneLabel)
            obj.put("short_text", shortText)
            obj.put("confidence", confidence)
            return obj
        }

        companion object {
            fun fromJson(obj: JSONObject): SceneTimelineEntry {
                return SceneTimelineEntry(
                    timestamp = obj.optString("ts", ""),
                    sceneLabel = obj.optString("scene_label", ""),
                    shortText = obj.optString("short_text", ""),
                    confidence = obj.optDouble("confidence", 0.5)
                )
            }
        }
    }

    data class CondensedMemoryItem(
        val timestamp: String,
        val content: String,
        val confidence: Double,
        val source: String
    ) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("ts", timestamp)
            obj.put("content", content)
            obj.put("confidence", confidence)
            obj.put("source", source)
            return obj
        }

        companion object {
            fun fromJson(obj: JSONObject): CondensedMemoryItem {
                return CondensedMemoryItem(
                    timestamp = obj.optString("ts", ""),
                    content = obj.optString("content", ""),
                    confidence = obj.optDouble("confidence", 0.5),
                    source = obj.optString("source", "unknown")
                )
            }
        }
    }

    data class RecentIntent(
        val timestamp: String,
        val intent: String,
        val phrasingHash: String,
        val length: String
    ) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("ts", timestamp)
            obj.put("intent", intent)
            obj.put("phrasing_hash", phrasingHash)
            obj.put("length", length)
            return obj
        }

        companion object {
            fun fromJson(obj: JSONObject): RecentIntent {
                return RecentIntent(
                    timestamp = obj.optString("ts", ""),
                    intent = obj.optString("intent", ""),
                    phrasingHash = obj.optString("phrasing_hash", ""),
                    length = obj.optString("length", "short")
                )
            }
        }
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun initialize(ctx: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            loadFromPrefs(ctx)
            initialized = true
        }
    }

    private fun loadFromPrefs(ctx: Context) {
        try {
            val p = prefs(ctx)

            // Load scene timeline
            val timelineJson = p.getString(KEY_SCENE_TIMELINE, null)
            if (timelineJson != null) {
                val arr = JSONArray(timelineJson)
                sceneTimeline.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) sceneTimeline.add(SceneTimelineEntry.fromJson(obj))
                }
            }

            // Load condensed memories
            val condensedJson = p.getString(KEY_CONDENSED_MEMORIES, null)
            if (condensedJson != null) {
                val arr = JSONArray(condensedJson)
                condensedMemories.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) condensedMemories.add(CondensedMemoryItem.fromJson(obj))
                }
            }

            // Load recent intents
            val intentsJson = p.getString(KEY_RECENT_INTENTS, null)
            if (intentsJson != null) {
                val arr = JSONArray(intentsJson)
                recentIntents.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) recentIntents.add(RecentIntent.fromJson(obj))
                }
            }

            // Load dialogue summaries
            val dialogueJson = p.getString(KEY_DIALOGUE_SUMMARIES, null)
            if (dialogueJson != null) {
                val arr = JSONArray(dialogueJson)
                dialogueSummaries.clear()
                for (i in 0 until arr.length()) {
                    dialogueSummaries.add(arr.optString(i, ""))
                }
            }

            condensedVersion = p.getInt(KEY_CONDENSED_VERSION, 1)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to load enhanced memory", e)
        }
    }

    private fun saveToPrefs(ctx: Context) {
        try {
            val p = prefs(ctx)
            p.edit {
                // Save scene timeline
                val timelineArr = JSONArray()
                for (entry in sceneTimeline) timelineArr.put(entry.toJson())
                putString(KEY_SCENE_TIMELINE, timelineArr.toString())

                // Save condensed memories
                val condensedArr = JSONArray()
                for (item in condensedMemories) condensedArr.put(item.toJson())
                putString(KEY_CONDENSED_MEMORIES, condensedArr.toString())

                // Save recent intents
                val intentsArr = JSONArray()
                for (intent in recentIntents) intentsArr.put(intent.toJson())
                putString(KEY_RECENT_INTENTS, intentsArr.toString())

                // Save dialogue summaries
                val dialogueArr = JSONArray()
                for (summary in dialogueSummaries) dialogueArr.put(summary)
                putString(KEY_DIALOGUE_SUMMARIES, dialogueArr.toString())

                putInt(KEY_CONDENSED_VERSION, condensedVersion)
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to save enhanced memory", e)
        }
    }

    // ========== SceneTimeline Methods ==========

    fun addSceneTimelineEntry(ctx: Context, entry: SceneTimelineEntry) {
        initialize(ctx)
        synchronized(sceneTimeline) {
            sceneTimeline.add(entry)
            // Trim if too large
            if (sceneTimeline.size > MAX_TIMELINE_ENTRIES) {
                sceneTimeline.removeAt(0)
            }
            saveToPrefs(ctx)
        }
    }

    fun getTimelineBuffer(ctx: Context, n: Int): List<SceneTimelineEntry> {
        initialize(ctx)
        synchronized(sceneTimeline) {
            return sceneTimeline.takeLast(n)
        }
    }

    fun getAllSceneTimeline(ctx: Context): List<SceneTimelineEntry> {
        initialize(ctx)
        synchronized(sceneTimeline) {
            return ArrayList(sceneTimeline)
        }
    }

    // ========== CondensedMemories Methods ==========

    fun addCondensedMemoryEntry(ctx: Context, content: String, confidence: Double = 0.7, source: String = "analyzer_v2") {
        initialize(ctx)
        if (content.isBlank()) return

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
        val item = CondensedMemoryItem(timestamp, content, confidence, source)

        synchronized(condensedMemories) {
            // Simple deduplication: avoid exact duplicate within last 10 entries
            val recent = condensedMemories.takeLast(10)
            if (recent.any { it.content == content }) {
                android.util.Log.d(TAG, "Duplicate condensed memory skipped: $content")
                return
            }

            condensedMemories.add(item)
            saveToPrefs(ctx)
        }
    }

    fun getRecentCondensedMemorySummary(ctx: Context, n: Int): String {
        initialize(ctx)
        synchronized(condensedMemories) {
            val recent = condensedMemories.takeLast(n)
            return recent.joinToString("\n") { "[${it.timestamp}] ${it.content} (conf=${it.confidence})" }
        }
    }

    fun getAllCondensedMemories(ctx: Context): List<CondensedMemoryItem> {
        initialize(ctx)
        synchronized(condensedMemories) {
            return ArrayList(condensedMemories)
        }
    }

    // ========== RecentIntents Methods ==========

    fun addRecentIntent(ctx: Context, intent: String, phrasingHash: String, length: String = "short") {
        initialize(ctx)
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
        val item = RecentIntent(timestamp, intent, phrasingHash, length)

        synchronized(recentIntents) {
            recentIntents.add(item)

            // Trim old intents beyond window
            val now = Date()
            recentIntents.removeAll { entry ->
                try {
                    val entryDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(entry.timestamp)
                    val ageMinutes = (now.time - entryDate.time) / 60000
                    ageMinutes > RECENT_INTENT_WINDOW_MINUTES
                } catch (e: Exception) {
                    false
                }
            }

            // Also enforce max size
            if (recentIntents.size > MAX_INTENTS) {
                recentIntents.removeAt(0)
            }

            saveToPrefs(ctx)
        }
    }

    fun getRecentIntents(ctx: Context, n: Int): List<RecentIntent> {
        initialize(ctx)
        synchronized(recentIntents) {
            return recentIntents.takeLast(n)
        }
    }

    fun countRecentIntentsByCategory(ctx: Context, intentCategory: String, windowMinutes: Int = 30): Int {
        initialize(ctx)
        synchronized(recentIntents) {
            val now = Date()
            return recentIntents.count { entry ->
                entry.intent == intentCategory && try {
                    val entryDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(entry.timestamp)
                    val ageMinutes = (now.time - entryDate.time) / 60000
                    ageMinutes <= windowMinutes
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    // ========== DialogueSummaries Methods ==========

    fun addDialogueSummary(ctx: Context, summary: String) {
        initialize(ctx)
        synchronized(dialogueSummaries) {
            dialogueSummaries.add(summary)
            saveToPrefs(ctx)
        }
    }

    fun getAllDialogueSummaries(ctx: Context): List<String> {
        initialize(ctx)
        synchronized(dialogueSummaries) {
            return ArrayList(dialogueSummaries)
        }
    }

    // ========== Utility Methods ==========

    fun clear(ctx: Context) {
        synchronized(this) {
            sceneTimeline.clear()
            condensedMemories.clear()
            recentIntents.clear()
            dialogueSummaries.clear()
            condensedVersion = 1
            prefs(ctx).edit { clear() }
        }
    }

    /**
     * Calculate simple string similarity (normalized Levenshtein distance)
     */
    fun stringSimilarity(s1: String, s2: String): Double {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(s1.toLowerCase(Locale.US), s2.toLowerCase(Locale.US))
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }
}
