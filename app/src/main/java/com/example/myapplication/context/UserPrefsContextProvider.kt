package com.example.myapplication.context

import android.content.Context
import android.util.Log
import com.example.myapplication.CharacterProfiles
import com.example.myapplication.PrefsHelper

/**
 * UserPrefsContextProvider: Provides user preferences context
 * 
 * Single responsibility: Read SharedPreferences for user settings
 * Used by agents for personalized behavior
 */
object UserPrefsContextProvider {
    private const val TAG = "UserPrefsContextProvider"
    
    data class UserPreferences(
        val activeCharacter: String,
        val characterProfile: CharacterProfiles.CharacterProfile,
        val warningUrgencyThreshold: Int,
        val shortResponseThreshold: Float,
        val longResponseThreshold: Float,
        val isOpenAIAnalysisEnabled: Boolean,
        val openAIBatchSize: Int,
        val autoAdvanceDialogues: Boolean,
        val isDarkTheme: Boolean,
        val showDeveloperDebug: Boolean
    )
    
    /**
     * Get complete user preferences snapshot
     * 
     * @param context Application context
     * @return UserPreferences with all settings
     */
    fun getUserPreferences(context: Context): UserPreferences {
        val prefs = PrefsHelper(context)
        val characterId = prefs.getActiveCharacter()
        
        return UserPreferences(
            activeCharacter = characterId,
            characterProfile = CharacterProfiles.getProfile(characterId),
            warningUrgencyThreshold = prefs.getWarningUrgencyThreshold(),
            shortResponseThreshold = prefs.getShortResponseThreshold(),
            longResponseThreshold = prefs.getLongResponseThreshold(),
            isOpenAIAnalysisEnabled = prefs.isOpenAIAnalysisEnabled(),
            openAIBatchSize = prefs.getOpenAIBatchSize(),
            autoAdvanceDialogues = prefs.getAutoAdvanceDialogues(),
            isDarkTheme = prefs.isDarkTheme(),
            showDeveloperDebug = prefs.getShowDeveloperDebug()
        )
    }
    
    /**
     * Get active character ID
     * 
     * @param context Application context
     * @return Character ID (e.g., "ralsei")
     */
    fun getActiveCharacter(context: Context): String {
        return PrefsHelper(context).getActiveCharacter()
    }
    
    /**
     * Get character profile for active character
     * 
     * @param context Application context
     * @return CharacterProfile with personality and urgency examples
     */
    fun getCharacterProfile(context: Context): CharacterProfiles.CharacterProfile {
        val characterId = getActiveCharacter(context)
        return CharacterProfiles.getProfile(characterId)
    }
    
    /**
     * Get warning urgency threshold
     * 
     * @param context Application context
     * @return Minimum urgency (0-10) to trigger warnings
     */
    fun getWarningUrgencyThreshold(context: Context): Int {
        return PrefsHelper(context).getWarningUrgencyThreshold()
    }
    
    /**
     * Get response decision thresholds
     * 
     * @param context Application context
     * @return Pair of (shortThreshold, longThreshold)
     */
    fun getResponseThresholds(context: Context): Pair<Float, Float> {
        val prefs = PrefsHelper(context)
        return Pair(
            prefs.getShortResponseThreshold(),
            prefs.getLongResponseThreshold()
        )
    }
    
    /**
     * Get OpenAI API key
     * 
     * @param context Application context
     * @return API key or null if not set
     */
    fun getOpenAIApiKey(context: Context): String? {
        return PrefsHelper(context).getOpenAIApiKey()
    }
    
    /**
     * Get OpenAI endpoint URL
     * 
     * @param context Application context
     * @return Endpoint URL
     */
    fun getOpenAIEndpoint(context: Context): String {
        return PrefsHelper(context).getOpenAIEndpoint()
    }
    
    /**
     * Get OpenAI batch size
     * 
     * @param context Application context
     * @return Number of screenshots per batch (1-10)
     */
    fun getOpenAIBatchSize(context: Context): Int {
        return PrefsHelper(context).getOpenAIBatchSize()
    }
    
    /**
     * Get custom OpenAI prompt
     * 
     * @param context Application context
     * @return Custom prompt or null if using default
     */
    fun getOpenAIPrompt(context: Context): String? {
        return PrefsHelper(context).getOpenAIPrompt()
    }
    
    /**
     * Check if OpenAI analysis is enabled
     * 
     * @param context Application context
     * @return True if enabled, false otherwise
     */
    fun isOpenAIAnalysisEnabled(context: Context): Boolean {
        return PrefsHelper(context).isOpenAIAnalysisEnabled()
    }
    
    /**
     * Check if auto-advance dialogues is enabled
     * 
     * @param context Application context
     * @return True if enabled, false otherwise
     */
    fun isAutoAdvanceDialogues(context: Context): Boolean {
        return PrefsHelper(context).getAutoAdvanceDialogues()
    }
    
    /**
     * Check if dark theme is enabled
     * 
     * @param context Application context
     * @return True if dark theme, false for light theme
     */
    fun isDarkTheme(context: Context): Boolean {
        return PrefsHelper(context).isDarkTheme()
    }
    
    /**
     * Check if developer debug mode is enabled
     * 
     * @param context Application context
     * @return True if showing developer messages, false otherwise
     */
    fun isShowDeveloperDebug(context: Context): Boolean {
        return PrefsHelper(context).getShowDeveloperDebug()
    }
    
    /**
     * Get screenshot interval
     * 
     * @param context Application context
     * @return Interval in milliseconds
     */
    fun getScreenshotInterval(context: Context): Long {
        return PrefsHelper(context).getInterval()
    }
    
    /**
     * Get image scale factor
     * 
     * @param context Application context
     * @return Scale factor (0.1-1.0)
     */
    fun getImageScale(context: Context): Float {
        return PrefsHelper(context).getImageScale()
    }
    
    /**
     * Get image quality
     * 
     * @param context Application context
     * @return Quality (0-100)
     */
    fun getImageQuality(context: Context): Int {
        return PrefsHelper(context).getImageQuality()
    }
}


