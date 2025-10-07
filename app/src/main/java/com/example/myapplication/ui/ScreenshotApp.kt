package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Suppress("UNUSED_PARAMETER")
@Composable
fun ScreenshotApp(
    isProjecting: Boolean,
    isScreenshotting: Boolean,
    interval: Long,
    notifyPref: Boolean,
    imageScale: Float,
    imageQuality: Int,
    openAIEnabled: Boolean,
    openAIApiKey: String?,
    openAIBatchSize: Int,
    onStartProjection: () -> Unit,
    onIntervalChange: (Long) -> Unit,
    onNotifyChange: (Boolean) -> Unit,
    onScaleChange: (Float) -> Unit,
    onQualityChange: (Int) -> Unit,
    onOpenAIEnabledChange: (Boolean) -> Unit,
    onOpenAIApiKeyChange: (String) -> Unit,
    onOpenAIBatchSizeChange: (Int) -> Unit,
    onStartScreenshots: () -> Unit,
    onStopScreenshots: () -> Unit,
    onOpenMemoryLogs: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    // New params to allow toggling the app theme from this screen
    currentDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    // Overall layout: black background, centered small Card for title/tip, buttons moved below the card,
    // and the dialogue box anchored at the bottom to be the main eye attraction.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Minimal Card (only title and small tip) so the main focus is the dialogue at the bottom
            Card(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Screen Capture",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Tip: Allow screen capture when prompted. The service runs in foreground while capturing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Buttons moved out of the Card: provide a separate area for controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onOpenAdvanced) {
                        Text("Open Advanced")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    // Toggle theme button
                    Button(onClick = onToggleTheme) {
                        Text(if (currentDarkTheme) "Switch to Light" else "Switch to Dark")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        onCheckedChange = { onNotifyChange(it) },
                        checked = notifyPref
                    )
                    Text("Show notification on screenshot", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProjecting) {
                        Button(
                            onClick = if (!isScreenshotting) onStartScreenshots else onStopScreenshots,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isScreenshotting) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(if (!isScreenshotting) "Start Screenshots" else "Stop Screenshots")
                        }
                    }
                    if (!isProjecting) {
                        Button(
                            onClick = onStartProjection,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Request Screen Capture Permission")
                        }
                    }
                }

                Row {
                    Button(onClick = onOpenMemoryLogs) {
                        Text("Open Memory Log")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = onOpenChat) {
                        Text("Open Chat")
                    }
                }
            }
        }

        // Dialogue box at the bottom — central visual element
        // Use the existing TypewriterDialogue composable. Provide a small sample dialogue.
        val sampleLines = listOf(
            DialogueLine(speaker = "System", text = "Screenshot tool active. Tap text to skip or advance. Screenshot tool active. Tap text to skip or advance."),
            DialogueLine(speaker = "Guide", text = "This dialogue sits at the bottom so it's the main eye attraction."),
            DialogueLine(text = "Adjust settings above to change behavior.")
        )

        TypewriterDialogue(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            lines = sampleLines,
            charDelayMs = 36L,
            commaPauseMs = 120L,
            punctuationPauseMs = 320L,
            playSound = false, // keep demo silent in this app area
            allowTouchAdvance = true,
            touchSkipsWhenTyping = true,
            onFinishedAll = { /* no-op for demo */ }
        )
    }
}