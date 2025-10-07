@file:Suppress("unused")
package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * MemoryEntry represents a single memory with explicit timestamp fields.
 */
data class MemoryEntry(
    val timestamp: String,
    val entry: String
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", timestamp)
        obj.put("entry", entry)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): MemoryEntry {
            return MemoryEntry(
                timestamp = obj.optString("timestamp", ""),
                entry = obj.optString("entry", "")
            )
        }
    }
}

/**
 * Simple persistent MemoryManager using SharedPreferences + JSON
 */
object MemoryManager {
    private const val PREFS_NAME = "memory_prefs"
    private const val KEY_POOL = "memory_pool"

    // In-memory list (mutable). Keep it private; expose read-only copies.
    private val pool: MutableList<MemoryEntry> = mutableListOf()
    private var initialized = false

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Load memory from SharedPreferences JSON. Safe to call multiple times.
     */
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
            val json = prefs(ctx).getString(KEY_POOL, null) ?: return
            val arr = JSONArray(json)
            pool.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    pool.add(MemoryEntry.fromJson(obj))
                }
            }
        } catch (_: Exception) {
            // ignore parse errors and start with empty pool
        }
    }

    private fun saveToPrefs(ctx: Context) {
        try {
            val arr = JSONArray()
            for (m in pool) arr.put(m.toJson())
            prefs(ctx).edit { putString(KEY_POOL, arr.toString()) }
        } catch (_: Exception) {
            // ignore save errors
        }
    }

    /**
     * Add a new memory entry with the provided timestamp components.
     */
    fun addEntry(ctx: Context, entry: MemoryEntry) {
        synchronized(pool) {
            // Avoid storing empty entries
            if (entry.entry.isBlank()) return
            // Optionally avoid duplicate consecutive entries
            val last = pool.lastOrNull()
            if (last != null && last.entry == entry.entry) return
            pool.add(entry)
            saveToPrefs(ctx)
        }
    }

    /**
     * Convenience: create entry using current time and add it.
     */
    fun addEntryWithNow(ctx: Context, text: String) {
        val cal = Calendar.getInstance()
        val ts = String.format(
            Locale.US,
            "%04d-%02d-%02d %02d:%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
        val e = MemoryEntry(timestamp = ts, entry = text)
        addEntry(ctx, e)
    }

    /**
     * Return a snapshot copy of the current memory pool.
     */
    fun getAll(ctx: Context): List<MemoryEntry> {
        initialize(ctx)
        synchronized(pool) {
            return ArrayList(pool)
        }
    }

    /**
     * Clear memory pool (in-memory + persisted)
     */
    fun clear(ctx: Context) {
        synchronized(pool) {
            pool.clear()
            prefs(ctx).edit { remove(KEY_POOL) }
        }
    }

    /**
     * Delete a specific memory entry (first match) and persist
     */
    fun deleteEntry(ctx: Context, entry: MemoryEntry) {
        synchronized(pool) {
            val idx = pool.indexOfFirst { it.timestamp == entry.timestamp && it.entry == entry.entry }
            if (idx >= 0) {
                pool.removeAt(idx)
                prefs(ctx).edit { putString(KEY_POOL, JSONArray(pool.map { it.toJson() }).toString()) }
            }
        }
    }
}

// Top-level helper functions required by the user
fun addMemoryEntry(context: Context, entry: String) {
    MemoryManager.initialize(context)
    MemoryManager.addEntryWithNow(context, entry)
}

// Build and return a JSON array representation of the current memory pool
fun toJsonArray(context: Context): JSONArray {
    MemoryManager.initialize(context)
    val arr = JSONArray()
    val list = MemoryManager.getAll(context)
    for (m in list) arr.put(m.toJson())
    return arr
}


fun getMemory(context: Context): List<MemoryEntry> {
    MemoryManager.initialize(context)
    return MemoryManager.getAll(context)
}

fun clearMemory(context: Context) {
    MemoryManager.initialize(context)
    MemoryManager.clear(context)
}

fun deleteMemory(context: Context, entry: MemoryEntry) {
    MemoryManager.initialize(context)
    MemoryManager.deleteEntry(context, entry)
}
