package com.example.myapplication.ui

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.SoundPool
import android.content.res.AssetFileDescriptor
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import com.example.myapplication.R
import kotlinx.coroutines.delay
import java.io.File

private val DIALOGUE_BOX_SHAPE = RoundedCornerShape(8.dp)
private val DIALOGUE_BOX_PADDING = 8.dp
private val DIALOGUE_BOX_BORDER_WIDTH = 1.dp
private const val DIALOGUE_LOG_TAG = "DialogueUI"
private val INITIAL_DIALOGUES_INJECTED = java.util.concurrent.atomic.AtomicBoolean(false)

@Composable
fun DialogueUI(
    initialDialogues: List<DialogueEntry> = emptyList(),
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
    autoAdvance: Boolean = true,
    onFinishedAll: (() -> Unit)? = null
) {
    val queueSnapshot by DialogueQueue.state.collectAsState()
    val current = queueSnapshot.firstOrNull()

    var lastShownEntry by remember { mutableStateOf<DialogueEntry?>(null) }
    if (current != null) lastShownEntry = current
    val displayed = current ?: lastShownEntry

    LaunchedEffect(initialDialogues) {
        if (!INITIAL_DIALOGUES_INJECTED.get() && initialDialogues.isNotEmpty() && DialogueQueue.snapshot()
                .isEmpty()
        ) {
            DialogueQueue.enqueue(initialDialogues)
            INITIAL_DIALOGUES_INJECTED.set(true)
        }
    }

    val tone = remember { if (playSound) ToneGenerator(AudioManager.STREAM_MUSIC, 50) else null }
    DisposableEffect(tone) { onDispose { tone?.release() } }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val density = LocalDensity.current
        val lineHeightDp = with(density) { lineHeight.toDp() }
        val boxHeight = lineHeightDp * 3
        val fullBoxHeight = boxHeight + DIALOGUE_BOX_PADDING * 2

        if (current != null) {
            var alreadySplit by remember(current) { mutableStateOf(false) }
            SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                val paddingPx = with(density) { DIALOGUE_BOX_PADDING.roundToPx() }
                val borderPx = with(density) { DIALOGUE_BOX_BORDER_WIDTH.roundToPx() }
                val availableWidth =
                    (constraints.maxWidth - paddingPx * 2 - borderPx * 2).coerceAtLeast(0)

                val measurables = subcompose("textMeasure") {
                    Text(
                        text = current.text,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = lineHeight),
                        maxLines = Int.MAX_VALUE,
                        modifier = Modifier.alpha(0f),
                        onTextLayout = { layout ->
                            if (!alreadySplit && layout.lineCount > 3) {
                                val endIndex = layout.getLineEnd(2, visibleEnd = true)
                                val cutIndex = findCutIndex(current.text, endIndex)
                                val firstPart = current.text.substring(0, cutIndex).trimEnd()
                                val remaining = current.text.substring(cutIndex).trimStart()
                                DialogueQueue.replaceHeadWith(
                                    listOf(
                                        current.copy(text = firstPart),
                                        current.copy(text = remaining)
                                    )
                                )
                                alreadySplit = true
                            }
                        }
                    )
                }

                measurables.first().measure(Constraints(maxWidth = availableWidth, minWidth = 0))
                layout(width = 0, height = 0) {}
            }
        }

        if (displayed != null) {
            TypewriterDialogueBlock(
                entry = displayed,
                portraitSize = portraitSize,
                fullBoxHeight = fullBoxHeight,
                lineHeight = lineHeight,
                charDelayMs = charDelayMs,
                commaPauseMs = commaPauseMs,
                punctuationPauseMs = punctuationPauseMs,
                playSound = playSound,
                tone = tone,
                requireAdvance = requireAdvance,
                autoAdvance = autoAdvance,
                advanceSignal = advanceSignal,
                lastAdvanceSignalRef = { /* no-op */ },
                onRequestAdvance = {
                    DialogueQueue.dequeue()
                    if (DialogueQueue.state.value.isEmpty()) onFinishedAll?.invoke()
                },
                allowTouchAdvance = allowTouchAdvance,
                touchSkipsWhenTyping = touchSkipsWhenTyping,
                dialogueShape = DIALOGUE_BOX_SHAPE,
                dialoguePadding = DIALOGUE_BOX_PADDING
            )
        }
    }
    // Observe external advanceSignal but actual handling is inside TypewriterDialogueBlock (kept for compatibility)
}

@Composable
private fun StaticDialogueBlock(
    entry: DialogueEntry,
    portraitSize: Dp,
    boxHeight: Dp,
    lineHeight: TextUnit
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
    ) {
        // show speaker tag
        if (entry.speaker.isNotBlank()) {
            Text(
                text = entry.speaker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        val candidateNames = relativePathCandidateNames(entry.relativePath)

        val resId = candidateNames.map { name ->
            val resourceName = name.replace('&', '_').replace(Regex("[^a-z0-9_]+"), "_")
            ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
        }.firstOrNull { it != 0 } ?: R.drawable.ic_launcher_foreground
        val painter: Painter = painterResource(id = resId)
        Image(
            painter = painter,
            contentDescription = "portrait",
            modifier = Modifier.size(portraitSize)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), shape = DIALOGUE_BOX_SHAPE)
                .border(
                    DIALOGUE_BOX_BORDER_WIDTH,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    DIALOGUE_BOX_SHAPE
                )
                .height(boxHeight)
                .padding(DIALOGUE_BOX_PADDING)
        ) {
            val dark = isSystemInDarkTheme()
            val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = lineHeight),
                color = textColor
            )
        }
    }
}

@Composable
private fun TypewriterDialogueBlock(
    entry: DialogueEntry,
    portraitSize: Dp,
    fullBoxHeight: Dp,
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
    touchSkipsWhenTyping: Boolean,
    dialogueShape: RoundedCornerShape,
    dialoguePadding: Dp,
    autoAdvance: Boolean
) {
    val autoAdvanceState by rememberUpdatedState(newValue = autoAdvance)

    val ctx = LocalContext.current
    // If the speaker is FRIDAY and playSound is enabled, use the WAV asset as SFX
    val fridaySfx = remember(entry.speaker, playSound) {
        if (playSound && entry.speaker.equals("FRIDAY", ignoreCase = true)) {
            SfxPlayer(ctx, "sound_effect/snd_txtund.wav")
        } else null
    }
    DisposableEffect(fridaySfx) { onDispose { fridaySfx?.release() } }

    val candidateNames = relativePathCandidateNames(entry.relativePath)
    val resId = candidateNames.map { name ->
        val resourceName = name.replace('&', '_').replace(Regex("[^a-z0-9_]+"), "_")
        ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
    }.firstOrNull { it != 0 } ?: 0

    val bitmapPainterOrNull = remember(entry.relativePath) {
        val assetPaths = mutableListOf<String>()
        val rel = entry.relativePath
        // Prefer explicit relativePath if provided and appears to be under portrait/friday
        if (!rel.isNullOrBlank()) {
            if (rel.startsWith("portrait/friday/")) assetPaths.add(rel) else assetPaths.add("portrait/friday/${File(rel).name}")
        }
        // Next, try canonical portrait/friday/<base>.png
        val base = try { File(rel ?: DEFAULT_FRIDAY_PATH).nameWithoutExtension } catch (_: Exception) { "normal" }
        assetPaths.add("portrait/friday/$base.png")
        // Last resort: default
        assetPaths.add(DEFAULT_FRIDAY_PATH)

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

    val painter: Painter = if (resId != 0) painterResource(id = resId) else bitmapPainterOrNull
        ?: painterResource(id = R.drawable.ic_launcher_foreground)

    var typedCount by remember { mutableStateOf(0) }
    var isTyping by remember { mutableStateOf(true) }
    var localAdvanceSignal by remember { mutableStateOf(advanceSignal) }
    var shouldCancelTyping by remember { mutableStateOf(false) }

    val pop by animateFloatAsState(
        targetValue = if (isTyping) 1.03f else 1f,
        animationSpec = tween(durationMillis = 180)
    )

    val clickableModifier = if (allowTouchAdvance) Modifier.clickable {
        if (isTyping && touchSkipsWhenTyping) {
            typedCount = entry.text.length
            isTyping = false
            shouldCancelTyping = true
        } else if (!isTyping) {
            onRequestAdvance()
        }
    } else Modifier

    Column(modifier = Modifier.fillMaxWidth()) {
        if (entry.speaker.isNotBlank()) {
            Text(
                text = entry.speaker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Image(
            painter = painter, contentDescription = "portrait", modifier = Modifier
                .size(portraitSize)
                .scale(pop)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), shape = dialogueShape)
                .border(
                    DIALOGUE_BOX_BORDER_WIDTH,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    dialogueShape
                )
                .height(fullBoxHeight)
                .then(clickableModifier)
                .padding(dialoguePadding)
        ) {
            val dark = isSystemInDarkTheme()
            val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
            val visible = remember(typedCount, entry.text) { entry.text.take(typedCount) }
            Text(
                text = visible,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = lineHeight),
                color = textColor
            )

            if (!isTyping) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 2.dp)
                ) { BlinkingAdvanceIndicator() }
            }
        }
    }

    LaunchedEffect(entry.text) {
        typedCount = 0
        isTyping = true
        shouldCancelTyping = false
        localAdvanceSignal = advanceSignal
        lastAdvanceSignalRef(localAdvanceSignal)

        var i = 0
        val len = entry.text.length
        while (i < len && !shouldCancelTyping) {
            val ch = entry.text[i]
            typedCount = i + 1
            if (playSound && !ch.isWhitespace()) {
                try {
                    // Play FRIDAY asset if available, fallback to ToneGenerator otherwise
                    if (fridaySfx != null) fridaySfx.play() else tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                } catch (_: Exception) {
                }
            }

            val pause = when (ch) {
                ',' -> commaPauseMs; '.', '!', '?', '\u2014', ';' -> punctuationPauseMs; else -> charDelayMs
            }

            var waited = 0L
            val step = 20L
            while (waited < pause && !shouldCancelTyping) {
                delay(step)
                waited += step
                if (advanceSignal != localAdvanceSignal) {
                    localAdvanceSignal = advanceSignal
                    if (requireAdvance) {
                        typedCount = len; i = len; break
                    }
                }
            }

            i++
        }

        isTyping = false
        if (!requireAdvance && autoAdvanceState && !shouldCancelTyping) {
            delay(400); onRequestAdvance()
        }
    }

    LaunchedEffect(advanceSignal) {
        if (advanceSignal == localAdvanceSignal) return@LaunchedEffect
        localAdvanceSignal = advanceSignal
        if (isTyping) {
            typedCount = entry.text.length; isTyping = false
        } else onRequestAdvance()
    }
}

@Composable
private fun BlinkingAdvanceIndicator() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color.White, shape = CircleShape)
        )
    }
}

private fun findCutIndex(text: String, maxIndex: Int): Int {
    val capped = maxIndex.coerceIn(0, text.length)
    if (capped <= 0) return 0
    val punctChars = listOf('.', '!', '?', ';')
    for (i in capped - 1 downTo 0) {
        val ch = text[i]
        if (ch in punctChars) return i + 1
    }
    for (i in capped - 1 downTo 0) {
        if (text[i].isWhitespace()) return i
    }
    return capped
}

// Simple SoundPool-backed player that loads an asset file and plays it
private class SfxPlayer(private val ctx: android.content.Context, private val assetPath: String) {
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(4).build()
    private var soundId: Int = 0
    private var loaded = false

    init {
        try {
            val afd: AssetFileDescriptor = ctx.assets.openFd(assetPath)
            soundId = soundPool.load(afd, 1)
            soundPool.setOnLoadCompleteListener { _, id, status ->
                if (status == 0 && id == soundId) loaded = true
            }
        } catch (t: Throwable) {
            Log.w("DialogueUI", "Failed to load sfx asset: $assetPath", t)
        }
    }

    fun play() {
        try {
            if (loaded && soundId != 0) {
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
            }
        } catch (t: Throwable) {
            Log.w("DialogueUI", "Failed to play sfx", t)
        }
    }

    fun release() {
        try {
            soundPool.release()
        } catch (_: Exception) {}
    }
}
