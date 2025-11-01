package com.example.myapplication.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.testing.DebugActivity
import java.util.Locale

/**
 * AdvancedScreen - Configuration panel for screenshot and AI settings
 *
 * Features:
 * - Screenshot interval control
 * - Image quality and scale adjustment
 * - OpenAI integration settings
 * - Batch processing configuration
 * - Auto-advance dialogue toggle
 */

@Composable
fun AdvancedScreen(
    interval: Long,
    imageScale: Float,
    imageQuality: Int,
    openAIEnabled: Boolean,
    openAIApiKey: String?,
    openAIBatchSize: Int,
    isScreenshotting: Boolean,
    saveScreenshots: Boolean,
    autoAdvance: Boolean,
    shortResponseThreshold: Float,
    longResponseThreshold: Float,
    warningUrgencyThreshold: Int,
    onIntervalChange: (Long) -> Unit,
    onScaleChange: (Float) -> Unit,
    onQualityChange: (Int) -> Unit,
    onOpenAIEnabledChange: (Boolean) -> Unit,
    onOpenAIApiKeyChange: (String) -> Unit,
    onOpenAIBatchSizeChange: (Int) -> Unit,
    onSaveScreenshotsChange: (Boolean) -> Unit,
    onAutoAdvanceChange: (Boolean) -> Unit,
    onShortResponseThresholdChange: (Float) -> Unit,
    onLongResponseThresholdChange: (Float) -> Unit,
    onWarningUrgencyThresholdChange: (Int) -> Unit,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        // Fixed header at the top
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onClose,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("✓ Done")
                }
            }
        }

        HorizontalDivider()

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Settings Section
            SettingsSection(title = "General") {
                ToggleSetting(
                    label = "Save screenshots to device",
                    checked = saveScreenshots,
                    onCheckedChange = onSaveScreenshotsChange
                )

                ToggleSetting(
                    label = "Auto-advance dialogues",
                    description = "Automatically advance when typing animation finishes",
                    checked = autoAdvance,
                    onCheckedChange = onAutoAdvanceChange
                )
            }

            // Screenshot Settings Section
            SettingsSection(title = "Screenshot Settings") {
                SliderSetting(
                    label = "Interval: ${interval / 1000}s",
                    value = interval.toFloat(),
                    onValueChange = { onIntervalChange(it.toLong()) },
                    valueRange = 1000f..30000f,
                    steps = 29,
                    enabled = !isScreenshotting
                )

                SliderSetting(
                    label = "Scale: ${String.format(Locale.US, "%.2f", imageScale)}x",
                    value = imageScale,
                    onValueChange = onScaleChange,
                    valueRange = 0.1f..1.0f,
                    steps = 90
                )

                SliderSetting(
                    label = "Quality: $imageQuality%",
                    value = imageQuality.toFloat(),
                    onValueChange = { onQualityChange(it.coerceIn(0f, 100f).toInt()) },
                    valueRange = 0f..100f,
                    steps = 100
                )
            }

            // OpenAI Settings Section
            SettingsSection(title = "AI Analysis") {
                ToggleSetting(
                    label = "Enable OpenAI analysis",
                    description = "Send screenshots to OpenAI for context understanding",
                    checked = openAIEnabled,
                    onCheckedChange = onOpenAIEnabledChange
                )

                if (openAIEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextField(
                            value = openAIApiKey ?: "",
                            onValueChange = onOpenAIApiKeyChange,
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        SliderSetting(
                            label = "Batch size: $openAIBatchSize",
                            description = "Number of screenshots to analyze together",
                            value = openAIBatchSize.toFloat(),
                            onValueChange = { onOpenAIBatchSizeChange(it.coerceIn(1f, 10f).toInt()) },
                            valueRange = 1f..10f,
                            steps = 9
                        )
                    }
                }
            }

            // Ralsei Behavior Settings Section
            SettingsSection(title = "Ralsei Response Behavior") {
                Text(
                    text = "Control how chatty Ralsei is based on decision scores",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                SliderSetting(
                    label = "Short response threshold: ${String.format(Locale.US, "%.2f", shortResponseThreshold)}",
                    description = "Minimum score for Ralsei to respond (lower = more chatty, higher = quieter)",
                    value = shortResponseThreshold,
                    onValueChange = onShortResponseThresholdChange,
                    valueRange = -1.0f..2.0f,
                    steps = 60
                )

                SliderSetting(
                    label = "Long response threshold: ${String.format(Locale.US, "%.2f", longResponseThreshold)}",
                    description = "Score needed for medium-long responses (below = short response)",
                    value = longResponseThreshold,
                    onValueChange = onLongResponseThresholdChange,
                    valueRange = -1.0f..2.0f,
                    steps = 60
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "ℹ️ Response Rules",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Below ${String.format(Locale.US, "%.2f", shortResponseThreshold)}: Stay quiet\n" +
                                   "• ${String.format(Locale.US, "%.2f", shortResponseThreshold)}–${String.format(Locale.US, "%.2f", longResponseThreshold)}: Short response\n" +
                                   "• Above ${String.format(Locale.US, "%.2f", longResponseThreshold)}: Medium-long response",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            // Warning System Settings Section
            SettingsSection(title = "Warning System") {
                Text(
                    text = "Control when pattern violations trigger warnings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                SliderSetting(
                    label = "Warning urgency threshold: $warningUrgencyThreshold",
                    description = "Minimum urgency (0-10) to trigger warnings. Lower values = more sensitive.",
                    value = warningUrgencyThreshold.toFloat(),
                    onValueChange = { onWarningUrgencyThresholdChange(it.toInt()) },
                    valueRange = 0f..10f,
                    steps = 10
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "ℹ️ Warning Levels",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 0-3: No warning\n" +
                                   "• 4-6: Dialogue bubble (high priority)\n" +
                                   "• 7-10: Soft intervention overlay\n" +
                                   "• Current threshold: $warningUrgencyThreshold (warnings at ${warningUrgencyThreshold}+)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                
                // Permissions Button
                Spacer(modifier = Modifier.height(8.dp))
                val context = LocalContext.current
                Button(
                    onClick = {
                        val intent = Intent(context, com.example.myapplication.PermissionsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("🔒 View Permissions")
                }
                
                // Debug Testing Button
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, DebugActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("🧪 Open Debug Screen")
                }
            }
            
            // Bottom spacer for comfortable scrolling
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            content()
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    description: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled
        )
    }
}
