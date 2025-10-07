package com.example.myapplication

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenshotService : Service() {
    private lateinit var prefsHelper: PrefsHelper
    private lateinit var screenshotManager: ScreenshotManager
    // Use IO dispatcher because takeScreenshot performs file I/O
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var screenshotJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "screenshot_channel"
        private const val TAG = "ScreenshotService"
        private const val ACTION_STOP_SERVICE = "com.example.myapplication.ACTION_STOP_SERVICE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val MIN_INTERVAL_MS = 1_000L
    }

    override fun onCreate() {
        super.onCreate()
        prefsHelper = PrefsHelper(applicationContext)
        val notificationHelper = NotificationHelper(applicationContext)
        screenshotManager = ScreenshotManager(applicationContext, notificationHelper)
        // Ensure memory is loaded at app startup
        try {
            MemoryManager.initialize(applicationContext)
            Log.d(TAG, "MemoryManager initialized")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize MemoryManager", e)
        }
        // OpenAIAnalyzer does not store Context; calls pass a Context when enqueuing images
        Log.d(TAG, "onCreate: ScreenshotService initialized (OpenAIAnalyzer will be used with explicit Context)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: intentAction=${intent?.action}")
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Stopping service from notification action.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Handle the case where the system restarts the service with a null intent
        if (intent == null) {
            Log.d(TAG, "onStartCommand: received null intent (possible restart).")
            if (this::screenshotManager.isInitialized && screenshotManager.isReady()) {
                Log.d(TAG, "Projection already active; ensuring loop is running")
                val interval = maxOf(prefsHelper.getInterval(), MIN_INTERVAL_MS)
                val notify = prefsHelper.isNotifyEnabled()
                startScreenshotLoop(interval, notify)
                return START_STICKY
            } else {
                Log.w(TAG, "No projection data available on restart; stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        Log.d(TAG, "Foreground service starting for media projection")
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "startForeground called (notificationPosted=true)")

        // At this point intent is non-null (we returned earlier if it was null). Use it directly.
        val safeIntent = intent
        val resultCode: Int = safeIntent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            safeIntent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            safeIntent.getParcelableExtra(EXTRA_DATA)
        }

        Log.d(TAG, "Received resultCode=$resultCode, dataPresent=${data != null}")

        // If projection is already ready, don't try to restart it — just ensure the loop runs
        if (screenshotManager.isReady()) {
            Log.d(TAG, "Projection already ready; skipping startProjection and (re)starting loop")
            val interval = maxOf(prefsHelper.getInterval(), MIN_INTERVAL_MS)
            val notify = prefsHelper.isNotifyEnabled()
            startScreenshotLoop(interval, notify)
            return START_STICKY
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                screenshotManager.startProjection(resultCode, data)
                prefsHelper.setScreenshotting(true)

                // Validate interval
                var interval = prefsHelper.getInterval()
                if (interval < MIN_INTERVAL_MS) {
                    Log.w(TAG, "Configured interval too small: $interval ms. Clamping to minimum $MIN_INTERVAL_MS ms")
                    interval = kotlin.math.max(MIN_INTERVAL_MS, interval)
                }
                val notify = prefsHelper.isNotifyEnabled()

                // Wait for the persistent virtual display to be ready before starting screenshots
                scope.launch {
                    val maxWaitMs = 5_000L
                    val pollInterval = 200L
                    var waited = 0L
                    Log.d(TAG, "Waiting up to ${maxWaitMs}ms for persistent virtual display to be ready")
                    while (!screenshotManager.isPersistentDisplayReady() && waited < maxWaitMs) {
                        delay(pollInterval)
                        waited += pollInterval
                    }
                    if (screenshotManager.isPersistentDisplayReady()) {
                        Log.d(TAG, "Persistent virtual display ready after ${waited}ms; starting screenshot loop")
                        startScreenshotLoop(interval, notify)
                        Log.d(TAG, "Media projection started successfully; loop started (interval=${interval}ms, notify=$notify)")
                    } else {
                        Log.e(TAG, "Persistent virtual display not ready after ${maxWaitMs}ms; stopping service")
                        // Make sure to stop the service from the main thread
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while starting media projection", e)
                stopSelf()
            }
        } else {
            Log.e(TAG, "Failed to start media projection: invalid intent data or user cancelled (resultCode=$resultCode, data=${data == null})")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startScreenshotLoop(intervalMs: Long, notify: Boolean) {
        screenshotJob?.cancel()
        screenshotJob = scope.launch {
            while (isActive) {
                try {
                    screenshotManager.takeScreenshot(notify)
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
            screenshotManager.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ScreenshotManager", e)
        }
        prefsHelper.setScreenshotting(false)
        Log.d(TAG, "Screenshot service stopped and projection cleaned up.")
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screenshot Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            if (manager != null) {
                manager.createNotificationChannel(channel)
            } else {
                Log.w(TAG, "NotificationManager is null when creating channel")
            }
        }

        val stopIntent = Intent(this, ScreenshotService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        // Project's minSdk guarantees FLAG_IMMUTABLE is available; include it unconditionally for safety.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screenshot Service")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
