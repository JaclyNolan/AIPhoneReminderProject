package com.example.myapplication.core

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.agents.AnalyzerAgent
import com.example.myapplication.memory.EnhancedMemoryManager
import com.example.myapplication.PrefsHelper
import com.example.myapplication.NotificationHelper
import com.example.myapplication.OverlayDialogueController
import com.example.myapplication.MyApplication
import com.example.myapplication.ServiceActions
import com.example.myapplication.ScreenshotPauseController

class MainForegroundService : Service() {
    private val TAG = "MainForegroundService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var prefsHelper: PrefsHelper
    private lateinit var notificationHelper: NotificationHelper
    private var screenshotController: ScreenshotController? = null
    private var screenshotJob: Job? = null
    private var screenshotPaused = false
    private var overlayDialogueController: OverlayDialogueController? = null

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                ServiceActions.ACTION_PAUSE_SCREENSHOT -> {
                    Log.d(TAG, "Received pause screenshot request")
                    screenshotPaused = true
                }
                ServiceActions.ACTION_RESUME_SCREENSHOT -> {
                    Log.d(TAG, "Received resume screenshot request")
                    screenshotPaused = false
                }
                ServiceActions.ACTION_STOP_SERVICE -> {
                    Log.d(TAG, "Received stop service request via broadcast")
                    stopSelf()
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        prefsHelper = PrefsHelper(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)

        // === Initialize Modular Architecture ===
        
        // Initialize Memory System (agents layer)
        try {
            ChatManager.initialize(applicationContext)
            Log.d(TAG, "ChatManager initialized in MainForegroundService process")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize ChatManager", e)
        }

        try {
            EnhancedMemoryManager.initialize(applicationContext)
            Log.d(TAG, "EnhancedMemoryManager initialized in MainForegroundService process")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize EnhancedMemoryManager", e)
        }

        // Initialize Tools Layer
        try {
            com.example.myapplication.tools.DialogueTool.initialize(applicationContext)
            Log.d(TAG, "DialogueTool initialized")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize DialogueTool", e)
        }

        try {
            com.example.myapplication.tools.NotificationTool.initialize(applicationContext)
            Log.d(TAG, "NotificationTool initialized")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize NotificationTool", e)
        }

        // Note: Context Providers are stateless objects, no initialization needed
        Log.d(TAG, "Context Providers (AppUsage, Memory, PhoneState, UserPrefs) ready")

        // Schedule periodic warning checks using WorkManager
        try {
            WarningCheckWorker.schedulePeriodicCheck(applicationContext)
            Log.d(TAG, "WarningCheckWorker scheduled for periodic checks")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule WarningCheckWorker", e)
        }

        // Register control broadcasts
        val filter = IntentFilter().apply {
            addAction(ServiceActions.ACTION_PAUSE_SCREENSHOT)
            addAction(ServiceActions.ACTION_RESUME_SCREENSHOT)
            addAction(ServiceActions.ACTION_STOP_SERVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use explicit receiver export flag to satisfy platform checks for dynamic receivers
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: intentAction=${intent?.action}")

        // Bring service to foreground with a notification
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }

        // Initialize controller if not present
        if (screenshotController == null) screenshotController = ScreenshotController(applicationContext, notificationHelper)

        // Handle projection data if provided
        if (intent != null && intent.hasExtra(ServiceActions.EXTRA_RESULT_CODE) && intent.hasExtra(ServiceActions.EXTRA_DATA)) {
            val resultCode = intent.getIntExtra(ServiceActions.EXTRA_RESULT_CODE, -1)
            @Suppress("DEPRECATION") // Intentional for backward compatibility with Android < 13
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(ServiceActions.EXTRA_DATA, Intent::class.java) else intent.getParcelableExtra<Intent>(ServiceActions.EXTRA_DATA)
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                try {
                    screenshotController?.startProjection(resultCode, data)
                    prefsHelper.setScreenshotting(true)

                    // Validate interval
                    var interval = prefsHelper.getInterval()
                    val MIN_INTERVAL_MS = 1_000L
                    if (interval < MIN_INTERVAL_MS) interval = kotlin.math.max(MIN_INTERVAL_MS, interval)
                    val notify = prefsHelper.isNotifyEnabled()

                    // Wait for persistent display readiness then start loop and overlay
                    scope.launch {
                        val maxWaitMs = 5_000L
                        val pollInterval = 200L
                        var waited = 0L
                        while (!screenshotController!!.isPersistentDisplayReady() && waited < maxWaitMs) {
                            delay(pollInterval)
                            waited += pollInterval
                        }
                        if (screenshotController!!.isPersistentDisplayReady()) {
                            Log.d(TAG, "Persistent virtual display ready after "+waited+"ms; starting screenshot loop")
                            startScreenshotLoop(interval, notify)

                            // Initialize OverlayDialogueController so it's ready to display dialogues while screenshots run
                            if (overlayDialogueController == null) {
                                overlayDialogueController = OverlayDialogueController(applicationContext)
                                Log.d(TAG, "OverlayDialogueController initialized")
                            }
                        } else {
                            Log.e(TAG, "Persistent virtual display not ready after "+maxWaitMs+"ms; stopping service")
                            stopSelf()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while starting projection in controller", e)
                    stopSelf()
                }
            } else {
                Log.e(TAG, "Invalid projection data received; stopping service")
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startScreenshotLoop(intervalMs: Long, notify: Boolean) {
        screenshotJob?.cancel()
        screenshotJob = scope.launch {
            while (isActive) {
                try {
                    if (screenshotPaused) { delay(200); continue }
                    screenshotController?.takeScreenshot(notify)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during takeScreenshot", e)
                }
                delay(intervalMs)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotJob?.cancel()
        scope.cancel()
        try {
            screenshotController?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ScreenshotController", e)
        }
        prefsHelper.setScreenshotting(false)

        // Clean up overlay controller
        overlayDialogueController?.destroy()
        overlayDialogueController = null

        try { unregisterReceiver(controlReceiver) } catch (e: Exception) { Log.w(TAG, "Receiver unregister failed", e) }

        // Cancel warning checks when service stops
        try {
            WarningCheckWorker.cancelPeriodicCheck(applicationContext)
            Log.d(TAG, "WarningCheckWorker cancelled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel WarningCheckWorker", e)
        }

        Log.d(TAG, "MainForegroundService stopped and cleaned up.")
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "main_foreground_channel",
                "Main Foreground Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager?.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MainForegroundService::class.java).apply {
            action = ServiceActions.ACTION_STOP_SERVICE
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, flags)

        return NotificationCompat.Builder(this, "main_foreground_channel")
            .setContentTitle("Screenshot Service")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}