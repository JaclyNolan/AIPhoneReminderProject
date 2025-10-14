package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.myapplication.ui.DialogueEntry
import com.example.myapplication.ui.DEFAULT_RALSEI_PATH

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
    onOpenResponseLog: () -> Unit = {},
    // New params to allow toggling the app theme from this screen
    currentDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    autoAdvance: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 220.dp), // Space for dialogue at bottom
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            HeaderCard()

            // Main Control Section
            MainControlCard(
                isProjecting = isProjecting,
                isScreenshotting = isScreenshotting,
                notifyPref = notifyPref,
                onStartProjection = onStartProjection,
                onStartScreenshots = onStartScreenshots,
                onStopScreenshots = onStopScreenshots,
                onNotifyChange = onNotifyChange
            )

            // Navigation Buttons Section
            NavigationSection(
                onOpenMemoryLogs = onOpenMemoryLogs,
                onOpenChat = onOpenChat,
                onOpenResponseLog = onOpenResponseLog,
                onOpenAdvanced = onOpenAdvanced,
                currentDarkTheme = currentDarkTheme,
                onToggleTheme = onToggleTheme
            )
        }

        // Dialogue box at the bottom
        DialogueSection(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            autoAdvance = autoAdvance
        )
    }
}

@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Screen Capture AI",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Divider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            )

            Text(
                text = "Your AI companion watches and learns from your screen activity",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MainControlCard(
    isProjecting: Boolean,
    isScreenshotting: Boolean,
    notifyPref: Boolean,
    onStartProjection: () -> Unit,
    onStartScreenshots: () -> Unit,
    onStopScreenshots: () -> Unit,
    onNotifyChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status indicator
            StatusChip(isProjecting = isProjecting, isScreenshotting = isScreenshotting)

            // Main action button
            if (!isProjecting) {
                Button(
                    onClick = onStartProjection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Request Screen Capture Permission",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                Button(
                    onClick = if (!isScreenshotting) onStartScreenshots else onStopScreenshots,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isScreenshotting)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = if (!isScreenshotting) "Start Screenshots" else "Stop Screenshots",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Notification toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = notifyPref,
                    onCheckedChange = onNotifyChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "Show notification on each screenshot",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(isProjecting: Boolean, isScreenshotting: Boolean) {
    val (statusText, statusColor) = when {
        isScreenshotting -> "Active" to MaterialTheme.colorScheme.tertiary
        isProjecting -> "Ready" to MaterialTheme.colorScheme.secondary
        else -> "Inactive" to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = statusColor.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Status: $statusText",
            style = MaterialTheme.typography.labelLarge,
            color = statusColor,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NavigationSection(
    onOpenMemoryLogs: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenResponseLog: () -> Unit,
    onOpenAdvanced: () -> Unit,
    currentDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Navigation",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // AI Tools Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenMemoryLogs,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Memory Log", fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Chat", fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onOpenResponseLog,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Response Log", fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }

            // Settings Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAdvanced,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Advanced", fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (currentDarkTheme) "Light Mode" else "Dark Mode",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogueSection(
    modifier: Modifier = Modifier,
    autoAdvance: Boolean
) {
    val sampleEntries = listOf(
        DialogueEntry(
            speaker = "Ralsei",
            text = "Welcome! I'm here to observe and learn from your activities.",
            relativePath = "/portrait/ralsei/excited.png"
        ),
        DialogueEntry(
            speaker = "Ralsei",
            text = "Tap the text to skip or advance through messages.",
            relativePath = "/portrait/ralsei/smile.png"
        ),
        DialogueEntry(
            speaker = "System",
            text = "Configure settings above to customize behavior and permissions."
        )
    )

    DialogueUI(
        initialDialogues = sampleEntries,
        modifier = modifier,
        portraitSize = 64.dp,
        lineHeight = 20.sp,
        charDelayMs = 36L,
        commaPauseMs = 120L,
        punctuationPauseMs = 320L,
        playSound = false,
        allowTouchAdvance = true,
        touchSkipsWhenTyping = true,
        autoAdvance = autoAdvance,
        onFinishedAll = { }
    )
}