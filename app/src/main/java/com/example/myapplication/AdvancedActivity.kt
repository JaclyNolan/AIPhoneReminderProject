package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.ui.AdvancedScreen

class AdvancedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val ctx = LocalContext.current
            val prefs = PrefsHelper(ctx)

            MyApplicationTheme(darkTheme = prefs.isDarkTheme()) {
                var interval by rememberSaveable { mutableStateOf(prefs.getInterval()) }
                var imageScale by rememberSaveable { mutableStateOf(prefs.getImageScale()) }
                var imageQuality by rememberSaveable { mutableStateOf(prefs.getImageQuality()) }
                var openAIEnabled by rememberSaveable { mutableStateOf(prefs.isOpenAIAnalysisEnabled()) }
                var openAIApiKey by rememberSaveable { mutableStateOf(prefs.getOpenAIApiKey() ?: "") }
                var openAIBatchSize by rememberSaveable { mutableStateOf(prefs.getOpenAIBatchSize()) }
                var isScreenshotting by rememberSaveable { mutableStateOf(prefs.isScreenshotting()) }
                var saveScreenshots by rememberSaveable { mutableStateOf(prefs.getSaveScreenshots()) }
                var autoAdvance by rememberSaveable { mutableStateOf(prefs.getAutoAdvanceDialogues()) }
                var shortResponseThreshold by rememberSaveable { mutableStateOf(prefs.getShortResponseThreshold()) }
                var longResponseThreshold by rememberSaveable { mutableStateOf(prefs.getLongResponseThreshold()) }

                AdvancedScreen(
                    interval = interval,
                    imageScale = imageScale,
                    imageQuality = imageQuality,
                    openAIEnabled = openAIEnabled,
                    openAIApiKey = openAIApiKey,
                    openAIBatchSize = openAIBatchSize,
                    isScreenshotting = isScreenshotting,
                    saveScreenshots = saveScreenshots,
                    autoAdvance = autoAdvance,
                    shortResponseThreshold = shortResponseThreshold,
                    longResponseThreshold = longResponseThreshold,
                    onIntervalChange = { newInterval ->
                        interval = newInterval
                        prefs.setInterval(newInterval)
                    },
                    onScaleChange = { newScale ->
                        imageScale = newScale
                        prefs.setImageScale(newScale)
                    },
                    onQualityChange = { newQuality ->
                        imageQuality = newQuality
                        prefs.setImageQuality(newQuality)
                    },
                    onOpenAIEnabledChange = { enabled ->
                        openAIEnabled = enabled
                        prefs.setOpenAIAnalysisEnabled(enabled)
                    },
                    onOpenAIApiKeyChange = { key ->
                        openAIApiKey = key
                        prefs.setOpenAIApiKey(key)
                    },
                    onOpenAIBatchSizeChange = { size ->
                        openAIBatchSize = size
                        prefs.setOpenAIBatchSize(size)
                    },
                    onSaveScreenshotsChange = { v ->
                        saveScreenshots = v
                        prefs.setSaveScreenshots(v)
                    },
                    onAutoAdvanceChange = { v ->
                        autoAdvance = v
                        prefs.setAutoAdvanceDialogues(v)
                    },
                    onShortResponseThresholdChange = { threshold ->
                        shortResponseThreshold = threshold
                        prefs.setShortResponseThreshold(threshold)
                    },
                    onLongResponseThresholdChange = { threshold ->
                        longResponseThreshold = threshold
                        prefs.setLongResponseThreshold(threshold)
                    },
                    onClose = {
                        // Indicate that preferences may have changed so caller can refresh (e.g., autoAdvance)
                        setResult(RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }
}