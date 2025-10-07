package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PrefsHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screenshot_prefs", Context.MODE_PRIVATE)

    fun isScreenshotting(): Boolean = prefs.getBoolean("is_screenshotting", false)
    fun setScreenshotting(value: Boolean) = prefs.edit { putBoolean("is_screenshotting", value) }

    fun getInterval(): Long = prefs.getLong("interval", 10_000L)
    fun setInterval(value: Long) = prefs.edit { putLong("interval", value) }

    fun isNotifyEnabled(): Boolean = prefs.getBoolean("notify", false)
    fun setNotify(value: Boolean) = prefs.edit { putBoolean("notify", value) }

    // Image scaling factor: 1.0 = full-size, 0.5 = half-size, etc. Default 0.4
    fun getImageScale(): Float = prefs.getFloat("image_scale", 0.4f)
    fun setImageScale(value: Float) = prefs.edit { putFloat("image_scale", value.coerceIn(0.1f, 1.0f)) }

    // Image quality for compression (0-100). Default 70.
    fun getImageQuality(): Int = prefs.getInt("image_quality", 70)
    fun setImageQuality(value: Int) = prefs.edit { putInt("image_quality", value.coerceIn(0, 100)) }

    // OpenAI analysis settings
    fun isOpenAIAnalysisEnabled(): Boolean = prefs.getBoolean("openai_enabled", false)
    fun setOpenAIAnalysisEnabled(value: Boolean) = prefs.edit { putBoolean("openai_enabled", value) }

    fun getOpenAIApiKey(): String? = prefs.getString("openai_api_key", null)
    fun setOpenAIApiKey(value: String) = prefs.edit { putString("openai_api_key", value) }

    fun getOpenAIEndpoint(): String = prefs.getString("openai_endpoint", "https://api.openai.com/v1/responses") ?: "https://api.openai.com/v1/responses"
    fun setOpenAIEndpoint(value: String) = prefs.edit { putString("openai_endpoint", value) }

    // Custom OpenAI prompt for vision analysis (stored in prefs). If unset, EnvLoader or default will be used.
    fun getOpenAIBatchSize(): Int = prefs.getInt("openai_batch_size", 3)
    fun setOpenAIBatchSize(value: Int) = prefs.edit { putInt("openai_batch_size", value.coerceIn(1, 10)) }

    fun getOpenAIPrompt(): String? = prefs.getString("openai_prompt", null)
    fun setOpenAIPrompt(value: String) = prefs.edit { putString("openai_prompt", value) }

    // Debug toggle: whether to show developer messages in the chat UI
    // Backwards compatible with the old "show_analyzer_debug" key.
    fun getShowDeveloperDebug(): Boolean = prefs.getBoolean("show_developer_debug", prefs.getBoolean("show_analyzer_debug", false))
    fun setShowDeveloperDebug(value: Boolean) {
        prefs.edit {
            putBoolean("show_developer_debug", value)
            putBoolean("show_analyzer_debug", value) // write old key for compatibility
        }
    }

    // New preference: whether to save screenshots to device storage. Default true to preserve existing behavior.
    fun getSaveScreenshots(): Boolean = prefs.getBoolean("save_screenshots", true)
    fun setSaveScreenshots(value: Boolean) = prefs.edit { putBoolean("save_screenshots", value) }

    // Theme preference: whether the app should use dark theme
    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)
    fun setDarkTheme(value: Boolean) = prefs.edit { putBoolean("dark_theme", value) }
}
