package com.example.myapplication.context

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.myapplication.PrefsHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * UserBadBehaviorContextProvider: Provides user-defined problematic behaviors
 * 
 * Single responsibility: Load and expose user-defined bad behaviors from storage
 * Used by UsagePatternContextProvider for pattern matching
 */
object UserBadBehaviorContextProvider {
    private const val TAG = "UserBadBehaviorContextProvider"
    private const val PREFS_KEY_BAD_BEHAVIORS = "user_bad_behaviors"
    
    data class UserBadBehavior(
        val id: String,
        val description: String,
        val createdAt: Long,
        val associatedApps: List<String> = emptyList()
    )
    
    /**
     * Get all user-defined bad behaviors
     * 
     * @param context Application context
     * @return List of bad behaviors
     */
    fun getBadBehaviors(context: Context): List<UserBadBehavior> {
        return try {
            val prefsHelper = PrefsHelper(context)
            val jsonString = prefsHelper.getSharedPreferences().getString(PREFS_KEY_BAD_BEHAVIORS, null)
            
            if (jsonString.isNullOrBlank()) {
                Log.d(TAG, "No bad behaviors defined")
                return emptyList()
            }
            
            val jsonArray = JSONArray(jsonString)
            val behaviors = mutableListOf<UserBadBehavior>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                behaviors.add(
                    UserBadBehavior(
                        id = obj.getString("id"),
                        description = obj.getString("description"),
                        createdAt = obj.getLong("createdAt"),
                        associatedApps = if (obj.has("associatedApps")) {
                            val appsArray = obj.getJSONArray("associatedApps")
                            (0 until appsArray.length()).map { appsArray.getString(it) }
                        } else {
                            emptyList()
                        }
                    )
                )
            }
            
            Log.d(TAG, "Loaded ${behaviors.size} bad behaviors")
            behaviors
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bad behaviors", e)
            emptyList()
        }
    }
    
    /**
     * Save bad behaviors to storage
     * 
     * @param context Application context
     * @param behaviors List of behaviors to save
     */
    fun saveBadBehaviors(context: Context, behaviors: List<UserBadBehavior>) {
        try {
            val jsonArray = JSONArray()
            behaviors.forEach { behavior ->
                val obj = JSONObject().apply {
                    put("id", behavior.id)
                    put("description", behavior.description)
                    put("createdAt", behavior.createdAt)
                    put("associatedApps", JSONArray(behavior.associatedApps))
                }
                jsonArray.put(obj)
            }
            
            val prefsHelper = PrefsHelper(context)
            prefsHelper.getSharedPreferences().edit()
                .putString(PREFS_KEY_BAD_BEHAVIORS, jsonArray.toString())
                .apply()
            
            Log.d(TAG, "Saved ${behaviors.size} bad behaviors")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bad behaviors", e)
        }
    }
    
    /**
     * Add a new bad behavior
     * 
     * @param context Application context
     * @param description Behavior description
     * @param associatedApps Optional list of associated app names
     * @return Created behavior
     */
    fun addBadBehavior(
        context: Context,
        description: String,
        associatedApps: List<String> = emptyList()
    ): UserBadBehavior {
        val behavior = UserBadBehavior(
            id = UUID.randomUUID().toString(),
            description = description,
            createdAt = System.currentTimeMillis(),
            associatedApps = associatedApps
        )
        
        val existing = getBadBehaviors(context).toMutableList()
        existing.add(behavior)
        saveBadBehaviors(context, existing)
        
        return behavior
    }
}

