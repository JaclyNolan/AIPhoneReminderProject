package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.ScreenshotApp

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var prefs: PrefsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PrefsHelper(this)
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            // App-wide theme state controlled here so UI can toggle dark/light at runtime
            var isDarkTheme by remember { mutableStateOf(prefs.isDarkTheme()) }
            var isScreenshotting by remember { mutableStateOf(prefs.isScreenshotting()) }
            var interval by remember { mutableLongStateOf(prefs.getInterval()) }
            var notifyPref by remember { mutableStateOf(prefs.isNotifyEnabled()) }
            var imageScale by remember { mutableStateOf(prefs.getImageScale()) }
            var imageQuality by remember { mutableStateOf(prefs.getImageQuality()) }
            var openAIEnabled by remember { mutableStateOf(prefs.isOpenAIAnalysisEnabled()) }
            var openAIApiKey by remember { mutableStateOf(prefs.getOpenAIApiKey() ?: "") }
            var openAIBatchSize by remember { mutableStateOf(prefs.getOpenAIBatchSize()) }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    if (!isGranted) {
                        Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                        notifyPref = false
                        prefs.setNotify(false)
                    }
                }
            )

            val screenCaptureLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.let {
                        val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                            putExtra(ScreenshotService.EXTRA_RESULT_CODE, result.resultCode)
                            putExtra(ScreenshotService.EXTRA_DATA, it)
                        }
                        Log.d(TAG, "Launching ScreenshotService with projection data")
                        ContextCompat.startForegroundService(this, serviceIntent)
                        isScreenshotting = true
                    }
                } else {
                    Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
                }
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                ScreenshotApp(
                    isProjecting = isScreenshotting,
                    isScreenshotting = isScreenshotting,
                    interval = interval,
                    notifyPref = notifyPref,
                    imageScale = imageScale,
                    imageQuality = imageQuality,
                    openAIEnabled = openAIEnabled,
                    openAIApiKey = openAIApiKey,
                    openAIBatchSize = openAIBatchSize,
                    onStartProjection = {
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    },
                    onIntervalChange = { newInterval ->
                        interval = newInterval
                        prefs.setInterval(newInterval)
                    },
                    onNotifyChange = { newValue ->
                        if (newValue) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notifyPref = true
                                    prefs.setNotify(true)
                                }
                            } else {
                                notifyPref = true
                                prefs.setNotify(true)
                            }
                        } else {
                            notifyPref = false
                            prefs.setNotify(false)
                        }
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
                    onStartScreenshots = {},
                    onStopScreenshots = {
                        Log.d(TAG, "Stopping screenshot service")
                        val serviceIntent = Intent(this, ScreenshotService::class.java)
                        stopService(serviceIntent)
                        isScreenshotting = false
                    },
                    onOpenMemoryLogs = {
                        val intent = Intent(this, MemoryLogActivity::class.java)
                        startActivity(intent)
                    },
                    onOpenChat = {
                        val intent = Intent(this, ChatActivity::class.java)
                        startActivity(intent)
                    },
                    onOpenAdvanced = {
                        val intent = Intent(this, AdvancedActivity::class.java)
                        startActivity(intent)
                    },
                    // pass theme state and toggle callback so the UI can control theme
                    currentDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        isDarkTheme = !isDarkTheme
                        prefs.setDarkTheme(isDarkTheme)
                    }
                 )
             }
         }
     }
 }
