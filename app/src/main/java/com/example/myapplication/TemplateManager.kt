package com.example.myapplication

import kotlin.random.Random

/**
 * TemplateManager: Manages template banks per intent with paraphrase mechanism
 * to ensure diverse, non-repetitive responses
 */
object TemplateManager {
    private const val TAG = "TemplateManager"

    // Template banks organized by intent category
    private val templateBank = mapOf(
        "comfort" to listOf(
            "Oh… Kris, are you okay? I'm here if you need me.",
            "Um… you seem a bit down — I'm right here, okay?",
            "Still quiet, Kris? I'm still here if you want to say anything.",
            "I… I noticed you seem a little sad. Want to talk about it?",
            "Hey… if something's bothering you, I'm listening.",
            "Are you feeling alright? You can tell me, you know.",
            "Um… I'm worried about you. Is everything okay?",
            "Kris… you don't have to go through this alone."
        ),
        "encouragement" to listOf(
            "Wow, Kris! That's really impressive!",
            "You're doing great! Keep it up!",
            "I… I believe in you, Kris. You can do this!",
            "That's wonderful! I'm so proud of you!",
            "You're really talented at this, you know?",
            "Amazing work! I knew you could do it!",
            "See? I told you that you were capable!",
            "Keep going, Kris! You're making great progress!"
        ),
        "curiosity" to listOf(
            "Oh? What are you looking at, Kris?",
            "Um… that looks interesting. What is it?",
            "I'm curious… what's caught your attention?",
            "Ooh, what's that? Can you tell me about it?",
            "That seems neat! What are you doing?",
            "I wonder… is that something fun?",
            "What's going on there? It looks intriguing!",
            "Hmm… what are you working on?"
        ),
        "concern" to listOf(
            "Kris… are you sure that's a good idea?",
            "Um… maybe you should take a break?",
            "I'm a little worried… is this safe?",
            "Please be careful, okay?",
            "I… I don't want you to get hurt…",
            "Maybe we should think about this first?",
            "Are you feeling overwhelmed? It's okay to rest.",
            "I'm concerned… do you need help?"
        ),
        "observation" to listOf(
            "Oh, I see you're busy with that.",
            "Looks like you're focused on something.",
            "I'll just be here if you need me.",
            "Ah, I understand. Take your time.",
            "I see… that makes sense.",
            "Interesting… I'm watching too.",
            "Oh! That's what you're up to.",
            "I noticed that as well, actually."
        ),
        "teaching" to listOf(
            "Did you know? There's actually a better way to do that!",
            "Oh! Let me show you something helpful…",
            "I learned this recently — maybe it'll help you too!",
            "Um… if I may suggest something?",
            "There's a trick to this, actually!",
            "I can teach you how to make this easier!",
            "Oh, I remember reading about this…",
            "Here's something that might be useful!"
        ),
        "playful" to listOf(
            "Hehe… that's kind of funny, isn't it?",
            "Oh my! That surprised me a little!",
            "Wow, Kris, you're full of surprises!",
            "That made me smile, actually!",
            "Haha… I didn't expect that!",
            "You're silly sometimes, you know that?",
            "That's… quite something!",
            "Oh! That's amusing!"
        ),
        "companionship" to listOf(
            "I'm right here with you, Kris.",
            "We're in this together, okay?",
            "You're not alone. I'm here.",
            "I'll always be by your side.",
            "We can face this together.",
            "I'm glad we're doing this as a team.",
            "Remember, I'm here for you always.",
            "We make a good team, don't we?"
        ),
        "micro_observe" to listOf(
            "Hmm…",
            "I see.",
            "Oh.",
            "Ah…",
            "Okay.",
            "Right.",
            "Mhm.",
            "Alright."
        )
    )

    /**
     * Select a template with highest diversity score relative to recent intents
     */
    fun selectTemplate(intent: String, recentIntents: List<EnhancedMemoryManager.RecentIntent>): String {
        val templates = templateBank[intent] ?: return "I'm here, Kris."

        // Get recent phrasing hashes for this intent
        val recentHashes = recentIntents
            .filter { it.intent == intent }
            .map { it.phrasingHash }
            .toSet()

        // Find template with hash not in recent set
        val availableTemplates = templates.filter {
            computePhraseHash(it) !in recentHashes
        }

        return if (availableTemplates.isNotEmpty()) {
            availableTemplates.random()
        } else {
            // If all templates recently used, pick randomly and paraphrase
            val base = templates.random()
            paraphraseTemplate(base)
        }
    }

    /**
     * Compute a simple hash for a phrase to track usage
     */
    fun computePhraseHash(text: String): String {
        // Simple hash: first 3 words + length
        val normalized = text.toLowerCase().replace(Regex("[^a-z0-9 ]"), "")
        val words = normalized.split(Regex("\\s+")).take(3)
        return "${words.joinToString("_")}_${text.length}"
    }

    /**
     * Paraphrase a template to create a variant
     */
    private fun paraphraseTemplate(text: String): String {
        // Simple paraphrase rules
        var result = text

        // Synonym replacements
        val synonyms = mapOf(
            "Kris" to listOf("Kris", "friend", "you"),
            "okay" to listOf("okay", "alright", "fine"),
            "here" to listOf("here", "nearby", "present"),
            "see" to listOf("see", "notice", "observe"),
            "I'm" to listOf("I'm", "I am", "I'll be")
        )

        for ((word, replacements) in synonyms) {
            if (result.contains(word, ignoreCase = true) && Random.nextFloat() < 0.3f) {
                result = result.replace(word, replacements.random(), ignoreCase = true)
            }
        }

        // Add occasional softeners
        val softeners = listOf("um… ", "ah… ", "oh… ", "well… ", "")
        if (Random.nextFloat() < 0.2f && !result.startsWith("um", ignoreCase = true)) {
            result = softeners.random() + result
        }

        return result
    }

    /**
     * Get all available intent categories
     */
    fun getAvailableIntents(): List<String> {
        return templateBank.keys.toList()
    }

    /**
     * Check if an intent is valid
     */
    fun isValidIntent(intent: String): Boolean {
        return templateBank.containsKey(intent)
    }

    /**
     * Get paraphrase suggestions for anti-repetition
     */
    fun paraphraseIfNeeded(text: String, recentIntents: List<EnhancedMemoryManager.RecentIntent>): String {
        val textHash = computePhraseHash(text)
        val recentHashes = recentIntents.takeLast(3).map { it.phrasingHash }

        return if (textHash in recentHashes) {
            paraphraseTemplate(text)
        } else {
            text
        }
    }
}

