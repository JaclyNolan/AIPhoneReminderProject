package com.example.myapplication.ui

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
jimport androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.example.myapplication.R
import kotlinx.coroutines.delay

// DialogueUI: displays a portrait (on top) and an outlined dialogue box below it, with a Typewriter effect.
// Now accepts List<DialogueEntry> where DialogueEntry contains (speaker, text, emotion).

@Composable
fun DialogueUI(
    parts: List<DialogueEntry> = emptyList(),
    modifier: Modifier = Modifier,
    portraitSize: Dp = 56.dp,
    lineHeight: TextUnit = 20.sp,
    charDelayMs: Long = 30L,
    commaPauseMs: Long = 120L,
    punctuationPauseMs: Long = 350L,
    playSound: Boolean = true,
    requireAdvance: Boolean = false,
    advanceSignal: Int = 0,
    allowTouchAdvance: Boolean = true,
    touchSkipsWhenTyping: Boolean = true,
    onFinishedAll: (() -> Unit)? = null
) {
    // NOTE: Local 'parts' support removed; always operate in queue mode.

    // Observe the global queue and use its head as the active entry
    val queueSnapshot by DialogueQueue.state.collectAsState()
    val current = queueSnapshot.firstOrNull()

    // Tone generator for blip sound (shared across entries)
    val tone = remember { if (playSound) ToneGenerator(AudioManager.STREAM_MUSIC, 50) else null }
    DisposableEffect(tone) { onDispose { tone?.release() } }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Height for 3 lines
        val density = LocalDensity.current
        val lineHeightDp = with(density) { lineHeight.toDp() }
        val boxHeight = lineHeightDp * 3

        if (current != null) {
            // Measure and split long text: if it exceeds 3 lines, replace the head with two entries (firstPart, remaining)
            var alreadySplit by remember(current) { mutableStateOf(false) }
            Text(
                text = current.text,
                style = TextStyle(lineHeight = lineHeight, color = Color.Transparent),
                maxLines = Int.MAX_VALUE,
                onTextLayout = { layout ->
                    if (!alreadySplit && layout.lineCount > 3) {
                        val endIndex = layout.getLineEnd(2, visibleEnd = true)
                        val cutIndex = findCutIndex(current.text, endIndex)
                        val firstPart = current.text.substring(0, cutIndex).trimEnd()
                        val remaining = current.text.substring(cutIndex).trimStart()

                        // Replace the head of the queue with firstPart followed by remaining
                        DialogueQueue.replaceHeadWith(listOf(current.copy(text = firstPart), current.copy(text = remaining)))

                        alreadySplit = true
                    }
                },
                modifier = Modifier.height(0.dp)
            )

            // show the (possibly split) head of the queue; after replaceHeadWith the queue will update and recomposition will show the updated head
            val active = queueSnapshot.firstOrNull()

            TypewriterDialogueBlock(
                entry = active ?: current,
                portraitSize = portraitSize,
                boxHeight = boxHeight,
                lineHeight = lineHeight,
                charDelayMs = charDelayMs,
                commaPauseMs = commaPauseMs,
                punctuationPauseMs = punctuationPauseMs,
                playSound = playSound,
                tone = tone,
                requireAdvance = requireAdvance,
                advanceSignal = advanceSignal,
                lastAdvanceSignalRef = { /* no local lastAdvanceSignal tracking when using queue-only mode */ },
                onRequestAdvance = {
                    // Dequeue the shown entry and invoke finished callback if queue becomes empty
                    DialogueQueue.dequeue()
                    if (DialogueQueue.state.value.isEmpty()) onFinishedAll?.invoke()
                },
                allowTouchAdvance = allowTouchAdvance,
                touchSkipsWhenTyping = touchSkipsWhenTyping
            )
        }
    }

    // Observe external advanceSignal but actual handling is inside TypewriterDialogueBlock (kept for compatibility)
}

@Composable
private fun StaticDialogueBlock(entry: DialogueEntry, portraitSize: Dp, boxHeight: Dp, lineHeight: TextUnit) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
        // show speaker tag
        if (!entry.speaker.isNullOrBlank()) {
            Text(text = entry.speaker, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }

        val candidateNames = emotionCandidateNames(entry.emotion ?: "normal")
        val resId = candidateNames.map { name ->
            val resourceName = name.replace('&', '_').replace(Regex("[^a-z0-9_]+"), "_")
            ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
        }.firstOrNull { it != 0 } ?: R.drawable.ic_launcher_foreground
        val painter: Painter = painterResource(id = resId)
        Image(painter = painter, contentDescription = "portrait", modifier = Modifier.size(portraitSize))
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).height(boxHeight).padding(8.dp)) {
            val dark = isSystemInDarkTheme()
            val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
            Text(text = entry.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = lineHeight), color = textColor)
        }
    }
}

@Composable
private fun TypewriterDialogueBlock(
    entry: DialogueEntry,
    portraitSize: Dp,
    boxHeight: Dp,
    lineHeight: TextUnit,
    charDelayMs: Long,
    commaPauseMs: Long,
    punctuationPauseMs: Long,
    playSound: Boolean,
    tone: ToneGenerator?,
    requireAdvance: Boolean,
    advanceSignal: Int,
    lastAdvanceSignalRef: (Int) -> Unit,
    onRequestAdvance: () -> Unit,
    allowTouchAdvance: Boolean,
    touchSkipsWhenTyping: Boolean
) {
    val ctx = LocalContext.current
    val candidateNames = emotionCandidateNames(entry.emotion ?: "normal")
    val resId = candidateNames.map { name ->
        val resourceName = name.replace('&', '_').replace(Regex("[^a-z0-9_]+"), "_")
        ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
    }.firstOrNull { it != 0 } ?: 0

    val bitmapPainterOrNull = remember(candidateNames) {
        val assetPaths = mutableListOf<String>()
        val emotionRaw = entry.emotion ?: "normal"
        assetPaths.add("portrait/ralsei/$emotionRaw.png")
        assetPaths.add("portrait/$emotionRaw.png")
        assetPaths.add("ralsei_$emotionRaw.png")

        var foundBmp: android.graphics.Bitmap? = null
        var i = 0
        while (i < assetPaths.size && foundBmp == null) {
            val p = assetPaths[i]
            try {
                ctx.assets.open(p).use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) foundBmp = decoded
                }
            } catch (_: Exception) {
            }
            i++
        }
        foundBmp?.asImageBitmap()?.let { BitmapPainter(it) }
    }

    val painter: Painter = if (resId != 0) painterResource(id = resId) else bitmapPainterOrNull ?: painterResource(id = R.drawable.ic_launcher_foreground)

    // typing state
    var typedCount by remember { mutableStateOf(0) }
    var isTyping by remember { mutableStateOf(true) }
    var localAdvanceSignal by remember { mutableStateOf(advanceSignal) }

    // portrait pop animation when typing starts
    val pop by animateFloatAsState(targetValue = if (isTyping) 1.03f else 1f, animationSpec = tween(durationMillis = 180))

    // Clicking behavior
    val clickableModifier = if (allowTouchAdvance) Modifier.clickable {
        if (isTyping && touchSkipsWhenTyping) {
            typedCount = entry.text.length
            isTyping = false
        } else if (!isTyping) {
            onRequestAdvance()
        }
    } else Modifier

    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
        // speaker
        if (!entry.speaker.isNullOrBlank()) {
            Text(text = entry.speaker, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }

        Image(painter = painter, contentDescription = "portrait", modifier = Modifier.size(portraitSize).scale(pop))
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).height(boxHeight + 8.dp * 2).padding(8.dp).then(clickableModifier)) {
            val dark = isSystemInDarkTheme()
            val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
            val visible = remember(typedCount, entry.text) { entry.text.take(typedCount) }
            Text(text = visible, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = lineHeight), color = textColor)

            if (!isTyping) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp)) { BlinkingAdvanceIndicator() }
            }
        }
    }

    // typing coroutine
    LaunchedEffect(entry.text) {
        typedCount = 0
        isTyping = true
        localAdvanceSignal = advanceSignal
        lastAdvanceSignalRef(localAdvanceSignal)

        var i = 0
        val len = entry.text.length
        while (i < len) {
            val ch = entry.text[i]
            typedCount = i + 1
            if (playSound && !ch.isWhitespace()) {
                try { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 40) } catch (_: Exception) {}
            }

            val pause = when (ch) { ',' -> commaPauseMs; '.', '!', '?', '\u2014', ';' -> punctuationPauseMs; else -> charDelayMs }

            var waited = 0L
            val step = 20L
            while (waited < pause) {
                delay(step)
                waited += step
                if (advanceSignal != localAdvanceSignal) {
                    localAdvanceSignal = advanceSignal
                    if (requireAdvance) { typedCount = len; i = len; break }
                }
            }

            i++
        }

        isTyping = false
        if (!requireAdvance) { delay(400); onRequestAdvance() }
    }

    // external advance handling
    LaunchedEffect(advanceSignal) {
        if (advanceSignal == localAdvanceSignal) return@LaunchedEffect
        localAdvanceSignal = advanceSignal
        if (isTyping) { typedCount = entry.text.length; isTyping = false } else onRequestAdvance()
    }
}

@Composable
private fun BlinkingAdvanceIndicator() {
    Box(modifier = Modifier.size(10.dp).background(Color.Transparent)) {
        // simple static indicator (kept minimal to avoid unused animation variable)
        Box(modifier = Modifier.size(8.dp).background(Color.White, shape = CircleShape))
    }
}

// helper to find the best cut index (prefer sentence end before maxIndex; else word boundary; else maxIndex)
private fun findCutIndex(text: String, maxIndex: Int): Int {
    val capped = maxIndex.coerceIn(0, text.length)
    if (capped <= 0) return 0
    // look for sentence-ending punctuation before or at capped
    val punctChars = listOf('.', '!', '?', ';')
    for (i in capped - 1 downTo 0) {
        val ch = text[i]
        if (ch in punctChars) return i + 1 // include punctuation
    }
    // no sentence end found: look for last whitespace before capped
    for (i in capped - 1 downTo 0) {
        if (text[i].isWhitespace()) return i
    }
    // fallback
    return capped
}

// Pure helper: generate candidate drawable names from emotion string (no Compose APIs)
private fun emotionCandidateNames(emotion: String): List<String> {
    val sanitized = emotion
    val base = sanitized.ifBlank { "normal" }
    val candidates = mutableListOf<String>()
    if (base.contains("ralsei")) {
        candidates.add(base)
    } else {
        candidates.add("ralsei_$base")
        candidates.add("portrait_ralsei_$base")
        candidates.add("portrait_$base")
        candidates.add(base)
    }
    candidates.add("ralsei")
    candidates.add("portrait_ralsei")
    return candidates
}
