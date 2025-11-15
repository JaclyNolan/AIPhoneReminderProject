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
import com.example.myapplication.core.MainForegroundService
import com.example.myapplication.core.WarningCheckWorker

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var prefs: PrefsHelper
    private var floatingOverlay: FloatingControlOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PrefsHelper(this)
        
        // Always show floating overlay when app opens (reset visibility so it appears even if dismissed)
        prefs.setFloatingOverlayVisible(true)
        try {
            floatingOverlay = FloatingControlOverlay(this).apply {
                onDismissListener = {
                    floatingOverlay = null
                    Log.d(TAG, "Floating overlay dismissed - reference cleared")
                }
            }
            floatingOverlay?.show()
            Log.d(TAG, "Floating overlay shown")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show floating overlay", e)
        }
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
            var autoAdvance by remember { mutableStateOf(prefs.getAutoAdvanceDialogues()) }
            var isWarningSystemEnabled by remember { mutableStateOf(prefs.isWarningSystemEnabled()) }

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

            // Launcher used to open AdvancedActivity and refresh autoAdvance after it closes (in case user changed the setting)
            val advancedLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    // Reload the preference from storage so changes are immediately reflected
                    autoAdvance = prefs.getAutoAdvanceDialogues()
                }
            }

            val screenCaptureLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.let {
                        val serviceIntent = Intent(this, MainForegroundService::class.java).apply {
                            putExtra(ServiceActions.EXTRA_RESULT_CODE, result.resultCode)
                            putExtra(ServiceActions.EXTRA_DATA, it)
                        }
                        Log.d(TAG, "Launching MainForegroundService with projection data")
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
                        val serviceIntent = Intent(this, MainForegroundService::class.java)
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
                        advancedLauncher.launch(intent)
                    },
                    onOpenResponseLog = {
                        val intent = Intent(this, com.example.myapplication.ui.ResponseLogActivity::class.java)
                        startActivity(intent)
                    },
                    onOpenDebug = {
                        val intent = Intent(this, com.example.myapplication.testing.DebugActivity::class.java)
                        startActivity(intent)
                    },
                    // pass theme state and toggle callback so the UI can control theme
                    currentDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        isDarkTheme = !isDarkTheme
                        prefs.setDarkTheme(isDarkTheme)
                    },
                    autoAdvance = autoAdvance,
                    isWarningSystemEnabled = isWarningSystemEnabled,
                    onWarningSystemChange = { enabled ->
                        isWarningSystemEnabled = enabled
                        prefs.setWarningSystemEnabled(enabled)
                        if (enabled) {
                            WarningCheckWorker.schedulePeriodicCheck(this)
                            Log.d(TAG, "Warning system enabled")
                        } else {
                            WarningCheckWorker.cancelPeriodicCheck(this)
                            Log.d(TAG, "Warning system disabled")
                        }
                    }
                 )
             }
         }
     }
     
    override fun onResume() {
        super.onResume()
        if (floatingOverlay == null) {
            try {
                floatingOverlay = FloatingControlOverlay(this).apply {
                    onDismissListener = {
                        floatingOverlay = null
                        Log.d(TAG, "Floating overlay dismissed - reference cleared")
                    }
                }
                floatingOverlay?.show()
                Log.d(TAG, "Floating overlay recreated on resume")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to recreate floating overlay on resume", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        floatingOverlay?.destroy()
        floatingOverlay = null
        Log.d(TAG, "MainActivity destroyed, floating overlay cleaned up")
    }
}
