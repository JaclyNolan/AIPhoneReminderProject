package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import java.io.File

// New shared entry type used by DialogueUI: includes speaker (default Ralsei), text, and a
// relativePath pointing to the portrait image inside the app assets (e.g. "portrait/ralsei/normal.png").
data class DialogueEntry(
    val speaker: String = "Ralsei",
    val text: String,
    // relativePath points to an asset path (under assets/) or null to let UI fall back to system image.
    val relativePath: String? = null
)

// Default Ralsei portrait asset path used when explicitly requested.
const val DEFAULT_RALSEI_PATH = "portrait/ralsei/normal.png"

// Helper: convert an emotion string (from the model) into a canonical relative asset path.
// Returns null when emotion is null/blank so UI can fall back to the system default image.
fun emotionToRelativePath(emotion: String?): String? {
    val e = emotion?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    return "portrait/ralsei/${e}.png"
}

// Helper: given a relative asset path (or null), produce a prioritized list of drawable resource
// names to try when resolving a portrait from resources. This returns simple resource-style names
// (no path separators) that the UI may try to resolve from R.drawable.
fun relativePathCandidateNames(relativePath: String?): List<String> {
    val base = try {
        if (relativePath.isNullOrBlank()) File(DEFAULT_RALSEI_PATH).nameWithoutExtension else File(relativePath).nameWithoutExtension
    } catch (_: Exception) {
        "normal"
    }

    // sanitize to lowercase alpha numeric + underscore
    val sanitized = base.lowercase().replace(Regex("[^a-z0-9_]+"), "_").ifBlank { "normal" }
    val candidates = mutableListOf<String>()
    if (sanitized.contains("ralsei")) {
        // If filename already mentions ralsei, try it directly first
        candidates.add(sanitized)
        candidates.add("ralsei")
        candidates.add("portrait_ralsei")
    } else {
        candidates.add("ralsei_$sanitized")
        candidates.add("portrait_ralsei_$sanitized")
        candidates.add("portrait_$sanitized")
        candidates.add(sanitized)
        candidates.add("ralsei")
        candidates.add("portrait_ralsei")
    }
    return candidates
}