package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

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
    onIntervalChange: (Long) -> Unit,
    onScaleChange: (Float) -> Unit,
    onQualityChange: (Int) -> Unit,
    onOpenAIEnabledChange: (Boolean) -> Unit,
    onOpenAIApiKeyChange: (String) -> Unit,
    onOpenAIBatchSizeChange: (Int) -> Unit,
    onSaveScreenshotsChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClose) { Text("Back") }
        }

        // Save screenshots toggle
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = saveScreenshots, onCheckedChange = onSaveScreenshotsChange)
            Text("Save screenshots to device", style = MaterialTheme.typography.bodyMedium)
        }

        Text("Screenshot interval: ${interval / 1000} s", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = interval.toFloat(),
            onValueChange = { onIntervalChange(it.toLong()) },
            valueRange = 1000f..30000f,
            steps = 29,
            enabled = !isScreenshotting
        )

        Text("Image scale: ${String.format(Locale.US, "%.2f", imageScale)}x", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = imageScale,
            onValueChange = { onScaleChange(it) },
            valueRange = 0.1f..1.0f,
            steps = 90
        )

        Text("Image quality: ${imageQuality}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = imageQuality.toFloat(),
            onValueChange = { onQualityChange(it.coerceIn(0f,100f).toInt()) },
            valueRange = 0f..100f,
            steps = 100
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = openAIEnabled, onCheckedChange = onOpenAIEnabledChange)
            Text("Send screenshots to OpenAI for analysis", style = MaterialTheme.typography.bodyMedium)
        }

        if (openAIEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = openAIApiKey ?: "", onValueChange = onOpenAIApiKeyChange, label = { Text("OpenAI API Key") })
                Spacer(modifier = Modifier.size(8.dp))
                Text("Batch size: ${openAIBatchSize}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = openAIBatchSize.toFloat(),
                    onValueChange = { onOpenAIBatchSizeChange(it.coerceIn(1f,10f).toInt()) },
                    valueRange = 1f..10f,
                    steps = 9
                )
            }
        }
    }
}
