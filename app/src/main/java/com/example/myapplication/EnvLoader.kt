package com.example.myapplication

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object EnvLoader {
    private const val TAG = "EnvLoader"
    private val POSSIBLE_KEYS = listOf("OPENAI_API_KEY", "OPENAI_KEY", "API_KEY")
    // keys to look for that may contain a custom prompt for OpenAI Vision
    private val POSSIBLE_PROMPT_KEYS = listOf("OPENAI_PROMPT", "OPENAI_VISION_PROMPT", "OPENAI_PROMPT_VISION")

    /**
     * Reads the asset file `openai.env` (if present) and looks for a key like OPENAI_API_KEY=...
     * Returns the value or null if not found.
     *
     * NOTE: Do not commit secrets to source control. Put `openai.env` in your local assets and
     * exclude it from version control. An example `openai.env.example` is included in assets.
     */
    fun getOpenAIApiKey(context: Context): String? {
        return try {
            val am = context.assets
            val name = "openai.env"
            val input = am.open(name)
            BufferedReader(InputStreamReader(input)).use { reader ->
                var line: String?
                val pattern = Regex("^\\s*([A-Za-z0-9_]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|(\\S+))\\s*")
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val match = pattern.find(trimmed) ?: continue
                    val key = match.groupValues[1]
                    val value = when {
                        match.groupValues[2].isNotEmpty() -> match.groupValues[2]
                        match.groupValues[3].isNotEmpty() -> match.groupValues[3]
                        match.groupValues[4].isNotEmpty() -> match.groupValues[4]
                        else -> ""
                    }
                    if (POSSIBLE_KEYS.contains(key)) {
                        return value.takeIf { it.isNotBlank() }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "openai.env not found or unreadable: ${e.message}")
            null
        }
    }

    /**
     * Reads OPENAI_PROMPT (or variants) from openai.env and returns the prompt string, or null.
     * This allows customizing the image-analysis prompt without rebuilding the app.
     */
    fun getOpenAIPrompt(context: Context): String? {
        return try {
            val am = context.assets
            val name = "openai.env"
            val input = am.open(name)
            BufferedReader(InputStreamReader(input)).use { reader ->
                var line: String?
                val pattern = Regex("^\\s*([A-Za-z0-9_]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|(\\S+))\\s*")
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val match = pattern.find(trimmed) ?: continue
                    val key = match.groupValues[1]
                    val value = when {
                        match.groupValues[2].isNotEmpty() -> match.groupValues[2]
                        match.groupValues[3].isNotEmpty() -> match.groupValues[3]
                        match.groupValues[4].isNotEmpty() -> match.groupValues[4]
                        else -> ""
                    }
                    if (POSSIBLE_PROMPT_KEYS.contains(key)) {
                        return value.takeIf { it.isNotBlank() }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "openai.env not found or unreadable (prompt): ${e.message}")
            null
        }
    }
}
