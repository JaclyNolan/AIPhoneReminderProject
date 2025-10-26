package com.example.myapplication.ui

import java.io.File
import com.example.myapplication.Characters

// New shared entry type used by DialogueUI: includes speaker (default from active character), text, and a
// relativePath pointing to the portrait image inside the app assets (e.g. "portrait/ronaldo/normal.png").
data class DialogueEntry(
    val speaker: String = Characters.ACTIVE.name,
    val text: String,
    // relativePath points to an asset path (under assets/) or null to let UI fall back to system image.
    val relativePath: String? = null
)

// Default portrait asset path used when explicitly requested.
val DEFAULT_PORTRAIT_PATH: String
    get() = Characters.ACTIVE.defaultPortraitPath

// Legacy constant for Friday character (kept for backward compatibility if needed)
const val DEFAULT_FRIDAY_PATH = "portrait/friday/normal.png"

// Helper: convert an emotion string (from the model) into a canonical relative asset path.
// Returns null when emotion is null/blank so UI can fall back to the system default image.
fun emotionToRelativePath(emotion: String?): String? {
    val e = emotion?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    return "${Characters.ACTIVE.portraitFolder}/${e}.png"
}

// Helper: given a relative asset path (or null), produce a prioritized list of drawable resource
// names to try when resolving a portrait from resources. This returns simple resource-style names
// (no path separators) that the UI may try to resolve from R.drawable.
fun relativePathCandidateNames(relativePath: String?): List<String> {
    val base = try {
        if (relativePath.isNullOrBlank()) File(Characters.ACTIVE.defaultPortraitPath).nameWithoutExtension else File(relativePath).nameWithoutExtension
    } catch (_: Exception) {
        "normal"
    }

    // sanitize to lowercase alpha numeric + underscore
    val sanitized = base.lowercase().replace(Regex("[^a-z0-9_]+"), "_").ifBlank { "normal" }
    val candidates = mutableListOf<String>()
    val characterNameLower = Characters.ACTIVE.name.lowercase()
    if (sanitized.contains(characterNameLower)) {
        // If filename already mentions character name, try it directly first
        candidates.add(sanitized)
        candidates.add(characterNameLower)
        candidates.add("portrait_$characterNameLower")
    } else {
        candidates.add("${characterNameLower}_$sanitized")
        candidates.add("portrait_${characterNameLower}_$sanitized")
        candidates.add("portrait_$sanitized")
        candidates.add(sanitized)
        candidates.add(characterNameLower)
        candidates.add("portrait_$characterNameLower")
    }
    return candidates
}