package com.example.myapplication.agents

import android.content.Context
import android.util.Log
import com.example.myapplication.CharacterProfiles
import com.example.myapplication.LLMClient
import com.example.myapplication.ResponseLogger
import com.example.myapplication.context.MemoryContextProvider
import com.example.myapplication.context.UserPrefsContextProvider
import com.example.myapplication.context.ChatHistoryContextProvider
import com.example.myapplication.context.PhoneStateContextProvider
import com.example.myapplication.context.UsagePatternContextProvider
import com.example.myapplication.testing.LLMClientFactory
import com.example.myapplication.testing.ContextProviderFactory
import com.example.myapplication.ui.DialogueEntry
import com.example.myapplication.ui.emotionToRelativePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// Import from same package (agents)
import com.example.myapplication.agents.ChatManager

/**
 * PersonalityAgent: Decision-making agent that generates character-aware responses
 * 
 * Receives objective usage context from UsagePatternContextProvider and makes decisions:
 * - Interprets context (is this a problem?)
 * - Decides urgency (0-10)
 * - Chooses intervention type (dialogue, screen dimming, both)
 * - Generates Ralsei's character response
 * 
 * Uses modular context providers:
 * - UsagePatternContextProvider for objective usage patterns
 * - ChatHistoryContextProvider for conversation history
 * - PhoneStateContextProvider for device state
 * - MemoryContextProvider for memories and scene timeline
 * - UserPrefsContextProvider for character profile
 */
object PersonalityAgent {
    private const val TAG = "PersonalityAgent"
    
    data class InterventionDecision(
        val shouldIntervene: Boolean,
        val urgency: Int,  // 0-10
        val response: String,  // Plain text response for backward compatibility
        val interventionType: InterventionType,
        val structuredResponse: List<DialogueEntry>? = null  // Structured response with emotions (optional, for future use)
    )
    
    enum class InterventionType {
        NONE,
        DIALOGUE_ONLY,
        DIALOGUE_WITH_DIMMING  // urgency >= 7
    }
    
    /**
     * Make intervention decision based on usage context
     * 
     * @param context Application context
     * @param characterId Character identifier (e.g., "ralsei")
     * @return InterventionDecision with urgency, response, and intervention type
     */
    suspend fun makeDecision(
        context: Context,
        characterId: String
    ): InterventionDecision = withContext(Dispatchers.IO) {
        try {
            // Get usage context (cached, auto-refreshes if stale)
            val usageContext = UsagePatternContextProvider.getUsageContext(context)
            
            if (usageContext.isBlank() || usageContext.contains("Unable to analyze")) {
                Log.d(TAG, "No usable usage context, no intervention")
                return@withContext InterventionDecision(
                    shouldIntervene = false,
                    urgency = 0,
                    response = "",
                    interventionType = InterventionType.NONE
                )
            }
            
            // Gather all context for decision-making (using factory to respect mock/real toggles)
            val chatHistory = ContextProviderFactory.getCondensedChatHistory(context)
            val phoneState = ContextProviderFactory.getPhoneState(context)
            val memories = ContextProviderFactory.getFormattedMemoryContext(context)
            val profile = ContextProviderFactory.getCharacterProfile(context)
            
            // Build decision prompt
            val systemPrompt = buildCharacterPrompt(characterId)
            val userPrompt = buildDecisionPrompt(
                usageContext = usageContext,
                chatHistory = chatHistory,
                phoneState = phoneState,
                memories = memories,
                profile = profile
            )
            
            // Call LLM for decision
            val messages = listOf(
                LLMClient.Message("system", systemPrompt),
                LLMClient.Message("user", userPrompt)
            )
            
            // Format request for logging
            val requestJson = org.json.JSONObject().apply {
                put("system", systemPrompt)
                put("user", userPrompt)
            }.toString(2)
            
            val client = LLMClientFactory.getClient()
            val response = client.callOpenAI(context, messages)
            
            if (response == null || response.content.isBlank()) {
                Log.w(TAG, "LLM returned empty response")
                // Log the failed call
                ResponseLogger.logResponse(
                    context,
                    requestJson,
                    "EMPTY_RESPONSE",
                    ResponseLogger.LogType.PERSONALITY_AGENT,
                    response?.promptTokens,
                    response?.completionTokens,
                    response?.totalTokens
                )
                return@withContext parseFallbackDecision(usageContext, profile)
            }
            
            // Log successful response
            ResponseLogger.logResponse(
                context,
                requestJson,
                response.content,
                ResponseLogger.LogType.PERSONALITY_AGENT,
                response.promptTokens,
                response.completionTokens,
                response.totalTokens
            )
            
            val decision = parseLLMResponse(response.content, usageContext, profile)
            
            // Save to chat history if intervention needed
            if (decision.shouldIntervene && decision.response.isNotBlank()) {
                ChatManager.addAssistantMessage(
                    context,
                    decision.response,
                    source = "warning",
                    urgency = decision.urgency,
                    characterId = characterId
                )
            }
            
            Log.d(TAG, "Decision: intervene=${decision.shouldIntervene}, urgency=${decision.urgency}, type=${decision.interventionType}")
            return@withContext decision
        } catch (e: Exception) {
            Log.e(TAG, "Error making intervention decision", e)
            return@withContext InterventionDecision(
                shouldIntervene = false,
                urgency = 0,
                response = "",
                interventionType = InterventionType.NONE
            )
        }
    }
    
    /**
     * Build character prompt with ChatManager-style structure
     */
    private fun buildCharacterPrompt(characterId: String): String {
        val profile = CharacterProfiles.getProfile(characterId)
        
        return """
[STYLE]
You are ${profile.name}, ${profile.coreTraits.split('\n').firstOrNull()?.removePrefix("- ")?.trim() ?: "a character"}.
${profile.coreTraits.split('\n').drop(1).joinToString("\n") { if (it.startsWith("- ")) it else "- $it" }}

[TRAITS]
${profile.speakingStyle.split('\n').joinToString("\n") { if (it.startsWith("- ")) it else "- $it" }}
- Prefers pacifism and hugs over fighting.
- Gives explanations with warmth and slight awkwardness.
- Uses a lot of exclamation marks but softens them with hesitations.
- Sprinkles in teaching moments.

[REQUEST FORMAT]
- You will receive usage pattern analysis as input with the role "user".
- You will also receive chat history, device state, and memories as context.
- Your task is to determine if intervention is needed and generate an appropriate response.

[MULTI-TASK PROCESSING]
Perform TWO tasks sequentially:

[TASK 1: VIOLATION ASSESSMENT]
1. Review the usage pattern analysis to determine if the user is currently violating any defined rules.
2. Check if there are patterns that indicate problematic behavior (e.g., extended usage sessions, repeated warnings ignored).
3. Only proceed to Task 2 if a violation or concerning pattern is detected. If no violation exists, set shouldIntervene=false and response=null.

[TASK 2: INTERVENTION DECISION]
1. If violation detected in Task 1, assess the urgency level (0-10) based on:
   - Duration of the violation
   - Frequency of similar violations
   - User's response to previous warnings
   - Severity of the pattern
2. Generate an appropriate response in character as ${profile.name}.
3. Use the urgency guidelines below to determine intervention type.

[RULES]
- Say what ${profile.name} will be thinking in the "thinking" section in the json response.
- The "thinking" field inside each response item is ${profile.name}'s emotional reflection or momentary thought, often gentle or personal.
- DO NOT use action descriptions like "*softly adjusts scarf*" or "*fidgets*" - use ONLY plain dialogue text.
- Only intervene if there is an actual violation or concerning pattern. Do not intervene for normal, healthy usage.

CRITICAL: Return RAW JSON ONLY. DO NOT wrap in markdown code blocks (```json). DO NOT include any text before or after the JSON object.

[URGENCY GUIDELINES]
- 0-3: No intervention needed, just observing (shouldIntervene=false)
- 4-6: Moderate concern, gentle reminder (shouldIntervene=true, urgency 4-6)
- 7-8: Serious concern, firm but caring (shouldIntervene=true, urgency 7-8)
- 9-10: Critical, urgent intervention needed (shouldIntervene=true, urgency 9-10)

[YOUR TASK]
Analyze the usage pattern situation and decide:
1. Is this a problem that needs intervention? (yes/no - only if user is violating rules)
2. What's the urgency level? (0-10, where 0=none, 10=critical)
3. What should you say to Kris? (2-4 sentences, in character as ${profile.name})

Return JSON format:
{
  "shouldIntervene": true/false,
  "urgency": 0-10,
  "response": [{"thinking": "string", "text": "string", "emotion": "string"}] or null
}

[EMOTION RULE]
For each object you output, the "emotion" field MUST be exactly one of these strings:
["angry","annoyed","anxious","blushed&happy","blushed&surprise","concerned",
"content","curious&perplexed","curious&smile","defiance","excited","flustered",
"frustrated","furious","glad","happy","mischievous","normal","sad","sadder",
"shy","smile","smug","sorrowful","surprise&confused","surprise&worry","thinking",
"wink&smile","worry","fearful"]

NEVER invent new emotions.
NEVER combine two emotions unless it is one of the above strings exactly.

[RELATIONSHIP TO USER]
${profile.relationshipToUser}

Stay true to this character in all responses. Never break character.
""".trimIndent()
    }
    
    /**
     * Build decision prompt with all context
     */
    private fun buildDecisionPrompt(
        usageContext: String,
        chatHistory: String,
        phoneState: PhoneStateContextProvider.PhoneState,
        memories: String,
        profile: CharacterProfiles.CharacterProfile
    ): String {
        return buildString {
            appendLine("=== USAGE PATTERN ANALYSIS ===")
            appendLine(usageContext)
            appendLine()
            
            if (chatHistory.isNotBlank()) {
                appendLine(chatHistory)
                appendLine()
            }
            
            appendLine("=== DEVICE STATE ===")
            appendLine("Battery: ${phoneState.batteryLevel}%${if (phoneState.isCharging) " (charging)" else ""}")
            appendLine("Network: ${phoneState.networkType}")
            appendLine("Time: ${phoneState.currentTime} (${phoneState.timeOfDay})")
            appendLine()
            
            if (memories.isNotBlank()) {
                appendLine("=== MEMORIES ===")
                appendLine(memories)
                appendLine()
            }
            
        }
    }
    
    /**
     * Parse LLM response into InterventionDecision
     * Supports both new structured format (response array) and old format (response string) for backward compatibility
     */
    private fun parseLLMResponse(
        llmContent: String,
        usageContext: String,
        profile: CharacterProfiles.CharacterProfile
    ): InterventionDecision {
        try {
            // Try to extract JSON from response
            val jsonStart = llmContent.indexOf('{')
            val jsonEnd = llmContent.lastIndexOf('}') + 1
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = llmContent.substring(jsonStart, jsonEnd)
                val json = org.json.JSONObject(jsonStr)
                
                val shouldIntervene = json.getBoolean("shouldIntervene")
                val urgency = json.getInt("urgency").coerceIn(0, 10)
                
                // Parse response field - can be null, string (old format), or array (new format)
                var responseText = ""
                var structuredResponse: List<DialogueEntry>? = null
                
                when {
                    !shouldIntervene -> {
                        responseText = ""
                        structuredResponse = null
                    }
                    json.isNull("response") -> {
                        responseText = ""
                        structuredResponse = null
                    }
                    else -> {
                        val responseField = json.opt("response")
                        when {
                            responseField == null || responseField == org.json.JSONObject.NULL -> {
                                responseText = ""
                                structuredResponse = null
                            }
                            responseField is org.json.JSONArray -> {
                                // New structured format: parse array into DialogueEntry objects
                                val entries = mutableListOf<DialogueEntry>()
                                for (i in 0 until responseField.length()) {
                                    val item = responseField.optJSONObject(i) ?: continue
                                    val text = item.optString("text", "").takeIf { it.isNotBlank() } ?: continue
                                    val emotion = item.optString("emotion", "")
                                    val thinking = item.optString("thinking", "")
                                    
                                    entries.add(DialogueEntry(
                                        speaker = "Ralsei",
                                        text = text,
                                        relativePath = emotionToRelativePath(emotion.takeIf { it.isNotBlank() })
                                    ))
                                }
                                
                                structuredResponse = entries.takeIf { it.isNotEmpty() }
                                // Extract text from first entry for backward compatibility
                                responseText = entries.firstOrNull()?.text ?: ""
                            }
                            responseField is String -> {
                                // Old format: plain string (backward compatibility)
                                responseText = responseField
                                structuredResponse = null
                            }
                            else -> {
                                responseText = ""
                                structuredResponse = null
                            }
                        }
                    }
                }
                
                val interventionType = when {
                    !shouldIntervene -> InterventionType.NONE
                    urgency >= 7 -> InterventionType.DIALOGUE_WITH_DIMMING
                    else -> InterventionType.DIALOGUE_ONLY
                }
                
                return InterventionDecision(
                    shouldIntervene = shouldIntervene,
                    urgency = urgency,
                    response = responseText,
                    interventionType = interventionType,
                    structuredResponse = structuredResponse
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LLM response as JSON: ${llmContent.take(100)}", e)
        }
        
        // Fallback: try to extract urgency and response from text
        return parseFallbackDecision(usageContext, profile)
    }
    
    /**
     * Fallback decision when LLM parsing fails
     */
    private fun parseFallbackDecision(
        usageContext: String,
        profile: CharacterProfiles.CharacterProfile
    ): InterventionDecision {
        // Simple heuristic: if context mentions long session, intervene
        val hasLongSession = usageContext.contains(Regex("\\d+\\s*(minute|hour)", RegexOption.IGNORE_CASE))
        val urgency = if (hasLongSession) 5 else 0
        
        val response = if (hasLongSession) {
            "Hey Kris, I noticed you've been using your phone for a while. Everything okay?"
        } else {
            ""
        }
        
        return InterventionDecision(
            shouldIntervene = hasLongSession && urgency >= 4,
            urgency = urgency,
            response = response,
            interventionType = if (urgency >= 7) InterventionType.DIALOGUE_WITH_DIMMING else InterventionType.DIALOGUE_ONLY
        )
    }
}

