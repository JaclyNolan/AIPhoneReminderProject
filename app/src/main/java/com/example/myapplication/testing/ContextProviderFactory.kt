package com.example.myapplication.testing

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.CharacterProfiles
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.context.*
import com.example.myapplication.memory.EnhancedMemoryManager
import com.example.myapplication.testing.mockcontext.*

/**
 * Context Provider Factory System
 * 
 * Allows switching between real and mock context providers for testing.
 * Each provider has a toggle that can be controlled from DebugScreen.
 */
object ContextProviderFactory {
    private const val TAG = "ContextProviderFactory"
    private const val PREFS_NAME = "context_provider_factory"
    private const val KEY_APP_USAGE_MOCK = "app_usage_mock"
    private const val KEY_USER_BEHAVIOR_MOCK = "user_behavior_mock"
    private const val KEY_MEMORY_MOCK = "memory_mock"
    private const val KEY_PHONE_STATE_MOCK = "phone_state_mock"
    private const val KEY_CHAT_HISTORY_MOCK = "chat_history_mock"
    private const val KEY_USER_PREFS_MOCK = "user_prefs_mock"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // App Usage Provider
    fun isAppUsageMock(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_APP_USAGE_MOCK, false)
    }
    
    fun setAppUsageMock(context: Context, useMock: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_APP_USAGE_MOCK, useMock).apply()
        android.util.Log.d(TAG, "AppUsage mock mode: $useMock")
    }
    
    fun getAppUsageStats(context: Context, intervalMinutes: Int = 60): List<UsagePatternDetector.AppUsageData> {
        return if (isAppUsageMock(context)) {
            MockAppUsageContextProvider.getUsageStats(context, intervalMinutes)
        } else {
            AppUsageContextProvider.getUsageStats(context, intervalMinutes)
        }
    }
    
    // User Bad Behavior Provider
    fun isUserBadBehaviorMock(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USER_BEHAVIOR_MOCK, false)
    }
    
    fun setUserBadBehaviorMock(context: Context, useMock: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USER_BEHAVIOR_MOCK, useMock).apply()
        android.util.Log.d(TAG, "UserBadBehavior mock mode: $useMock")
    }
    
    fun getUserBadBehaviors(context: Context): List<UserBadBehaviorContextProvider.UserBadBehavior> {
        return if (isUserBadBehaviorMock(context)) {
            MockUserBadBehaviorContextProvider.getBadBehaviors(context)
        } else {
            UserBadBehaviorContextProvider.getBadBehaviors(context)
        }
    }
    
    // Memory Provider
    fun isMemoryMock(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MEMORY_MOCK, false)
    }
    
    fun setMemoryMock(context: Context, useMock: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MEMORY_MOCK, useMock).apply()
        android.util.Log.d(TAG, "Memory mock mode: $useMock")
    }
    
    fun getSceneTimeline(context: Context, limitMinutes: Int? = null): List<EnhancedMemoryManager.SceneTimelineEntry> {
        return if (isMemoryMock(context)) {
            MockMemoryContextProvider.getSceneTimeline(context, limitMinutes)
        } else {
            MemoryContextProvider.getSceneTimeline(context, limitMinutes)
        }
    }
    
    fun getFormattedMemoryContext(context: Context): String {
        return if (isMemoryMock(context)) {
            MockMemoryContextProvider.getFormattedMemoryContext(context)
        } else {
            MemoryContextProvider.getFormattedMemoryContext(context)
        }
    }
    
    // Phone State Provider
    fun isPhoneStateMock(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PHONE_STATE_MOCK, false)
    }
    
    fun setPhoneStateMock(context: Context, useMock: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PHONE_STATE_MOCK, useMock).apply()
        android.util.Log.d(TAG, "PhoneState mock mode: $useMock")
    }
    
    fun getPhoneState(context: Context): PhoneStateContextProvider.PhoneState {
        return if (isPhoneStateMock(context)) {
            MockPhoneStateContextProvider.getPhoneState(context)
        } else {
            PhoneStateContextProvider.getPhoneState(context)
        }
    }
    
    // Chat History Provider
    fun isChatHistoryMock(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CHAT_HISTORY_MOCK, false)
    }
    
    fun setChatHistoryMock(context: Context, useMock: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CHAT_HISTORY_MOCK, useMock).apply()
        android.util.Log.d(TAG, "ChatHistory mock mode: $useMock")
    }
    
    fun getChatHistory(context: Context, maxMessages: Int = 20, maxSummaries: Int = 3): String {
        return if (isChatHistoryMock(context)) {
            MockChatHistoryContextProvider.getChatHistory(context, maxMessages, maxSummaries)
        } else {
            ChatHistoryContextProvider.getChatHistory(context, maxMessages, maxSummaries)
        }
    }
    
    fun getCondensedChatHistory(context: Context, maxPairs: Int = 10): String {
        return if (isChatHistoryMock(context)) {
            MockChatHistoryContextProvider.getCondensedChatHistory(context, maxPairs)
        } else {
            ChatHistoryContextProvider.getCondensedChatHistory(context, maxPairs)
        }
    }
    
    // User Prefs Provider
    fun isUserPrefsMock(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USER_PREFS_MOCK, false)
    }
    
    fun setUserPrefsMock(context: Context, useMock: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USER_PREFS_MOCK, useMock).apply()
        android.util.Log.d(TAG, "UserPrefs mock mode: $useMock")
    }
    
    fun getCharacterProfile(context: Context): CharacterProfiles.CharacterProfile {
        return if (isUserPrefsMock(context)) {
            MockUserPrefsContextProvider.getCharacterProfile(context)
        } else {
            UserPrefsContextProvider.getCharacterProfile(context)
        }
    }
    
    /**
     * Get all current mock states for display
     */
    fun getAllMockStates(context: Context): Map<String, Boolean> {
        return mapOf(
            "AppUsage" to isAppUsageMock(context),
            "UserBadBehavior" to isUserBadBehaviorMock(context),
            "Memory" to isMemoryMock(context),
            "PhoneState" to isPhoneStateMock(context),
            "ChatHistory" to isChatHistoryMock(context),
            "UserPrefs" to isUserPrefsMock(context)
        )
    }
    
    /**
     * Reset all toggles to real providers
     */
    fun resetAllToReal(context: Context) {
        setAppUsageMock(context, false)
        setUserBadBehaviorMock(context, false)
        setMemoryMock(context, false)
        setPhoneStateMock(context, false)
        setChatHistoryMock(context, false)
        setUserPrefsMock(context, false)
        android.util.Log.d(TAG, "Reset all providers to real")
    }
}

