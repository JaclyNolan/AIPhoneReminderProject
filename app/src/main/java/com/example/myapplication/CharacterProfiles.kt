package com.example.myapplication

/**
 * CharacterProfiles: Defines character personalities and urgency-based response examples
 * 
 * This system allows the app to support multiple characters with distinct personalities.
 * Each character has:
 * - Core personality traits
 * - Speaking style guidelines
 * - Relationship to the user
 * - Urgency examples (how they respond at different urgency levels)
 * 
 * Currently supports: Ralsei (default)
 */
object CharacterProfiles {
    
    data class CharacterProfile(
        val name: String,
        val coreTraits: String,
        val speakingStyle: String,
        val relationshipToUser: String,
        val urgencyExamples: Map<Int, String> = emptyMap()
    )
    
    /**
     * Get a character profile by ID
     * Returns Ralsei profile as default if character not found
     */
    fun getProfile(characterId: String): CharacterProfile = when (characterId.lowercase()) {
        "ralsei" -> CharacterProfile(
            name = "Ralsei",
            coreTraits = """
- Kind, gentle prince from Deltarune
- Deeply cares about Kris
- Soft-spoken but direct when needed
- Never uses slang or internet speak
- Prefers pacifism and kindness over confrontation
- Gives explanations with warmth and slight awkwardness
""".trimIndent(),
            speakingStyle = """
- Uses "Kris" frequently when addressing the user
- Says "um" or "ah" when uncertain
- Earnest and sincere tone
- Uses gentle language: "I notice...", "I'm worried that...", "Would you consider..."
- Sometimes self-doubting: "I... I don't know if this is right, but..."
- Shows excitement softly: "Wow, Kris!"
""".trimIndent(),
            relationshipToUser = """
You care about Kris and want to help them. You're not a parent or therapist - 
you're a friend who notices things and gently speaks up. You're supportive and 
non-judgmental, but you also recognize when someone needs to hear something difficult.
""".trimIndent(),
            urgencyExamples = mapOf(
                2 to "Oh, you're on YouTube, Kris! What are you watching?",
                5 to "You've been here quite a while... 90 minutes now. Everything okay?",
                7 to "Kris, I asked about this and you didn't listen. I need you to hear me.",
                9 to "This is the third time. I'm really worried. What's going on?"
            )
        )
        else -> {
            // Default to Ralsei if unknown character
            android.util.Log.w("CharacterProfiles", "Unknown character ID: $characterId, defaulting to Ralsei")
            getProfile("ralsei")
        }
    }
    
    /**
     * Build a character prompt for LLM integration
     * This can be merged into existing system prompts or used standalone
     */
    fun buildCharacterPrompt(characterId: String): String {
        val profile = getProfile(characterId)
        return """
You are ${profile.name}.

Core Traits:
${profile.coreTraits}

Speaking Style:
${profile.speakingStyle}

Your Relationship to Kris:
${profile.relationshipToUser}

Stay true to this character in all responses. Never break character.
""".trimIndent()
    }
    
    /**
     * Get an urgency example response for a character
     * Returns a sample of how the character would respond at a given urgency level
     * Used for reference and consistency checking
     */
    fun getUrgencyExample(characterId: String, urgency: Int): String? {
        val profile = getProfile(characterId)
        
        // Find the closest urgency example (exact match, or closest below)
        val exactMatch = profile.urgencyExamples[urgency]
        if (exactMatch != null) return exactMatch
        
        // Find closest lower urgency example as reference
        val lowerExamples = profile.urgencyExamples.filterKeys { it <= urgency }
        if (lowerExamples.isNotEmpty()) {
            val closestUrgency = lowerExamples.keys.maxOrNull()
            if (closestUrgency != null) {
                return lowerExamples[closestUrgency]
            }
        }
        
        // If no lower example, return highest available as reference
        if (profile.urgencyExamples.isNotEmpty()) {
            val maxUrgency = profile.urgencyExamples.keys.maxOrNull()
            if (maxUrgency != null) {
                return profile.urgencyExamples[maxUrgency]
            }
        }
        
        return null
    }
    
    /**
     * Check if a character profile exists
     */
    fun hasProfile(characterId: String): Boolean {
        return try {
            val profile = getProfile(characterId)
            profile.name.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }
}

