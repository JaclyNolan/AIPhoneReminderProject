package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp

// Small compatibility types and wrapper so existing code using `DialogueLine` and
// `TypewriterDialogue` continues to work.

data class DialogueLine(
    val speaker: String? = null,
    val text: String
)

// New shared entry type used by DialogueUI: includes speaker (default Ralsei), text, and emotion
data class DialogueEntry(
    val speaker: String = "Ralsei",
    val text: String,
    val emotion: String? = "normal"
)

@Composable
fun TypewriterDialogue(
    modifier: Modifier = Modifier,
    lines: List<DialogueLine>,
    portraitSize: Dp = 56.dp,
    lineHeight: TextUnit = 20.sp,
    charDelayMs: Long = 36L,
    commaPauseMs: Long = 120L,
    punctuationPauseMs: Long = 320L,
    playSound: Boolean = true,
    requireAdvance: Boolean = false,
    advanceSignal: Int = 0,
    allowTouchAdvance: Boolean = true,
    touchSkipsWhenTyping: Boolean = true,
    onFinishedAll: (() -> Unit)? = null
) {
    // Map DialogueLine to DialogueEntry expected by DialogueUI.
    // Speaker defaults to Ralsei unless specified in DialogueLine.
    val parts = lines.map { line ->
        DialogueEntry(speaker = line.speaker ?: "Ralsei", text = line.text, emotion = "normal")
    }

    DialogueUI(
        parts = parts,
        modifier = modifier,
        portraitSize = portraitSize,
        lineHeight = lineHeight,
        charDelayMs = charDelayMs,
        commaPauseMs = commaPauseMs,
        punctuationPauseMs = punctuationPauseMs,
        playSound = playSound,
        requireAdvance = requireAdvance,
        advanceSignal = advanceSignal,
        allowTouchAdvance = allowTouchAdvance,
        touchSkipsWhenTyping = touchSkipsWhenTyping,
        onFinishedAll = onFinishedAll
    )
}
