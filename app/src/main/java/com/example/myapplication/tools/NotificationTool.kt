package com.example.myapplication.tools

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * NotificationTool: Send background notifications
 * 
 * Single responsibility: Display system notifications
 * Wraps NotificationHelper functionality for modular tool interface
 */
object NotificationTool {
    private const val TAG = "NotificationTool"
    private const val CHANNEL_ID_GENERAL = "screenshot_channel"
    private const val CHANNEL_ID_WARNING = "warning_channel"
    private const val CHANNEL_ID_URGENT = "urgent_channel"
    
    private var initialized = false
    
    /**
     * Initialize notification channels
     * Must be called before use
     * 
     * @param context Application context
     */
    fun initialize(context: Context) {
        if (!initialized) {
            createNotificationChannels(context)
            initialized = true
            Log.d(TAG, "NotificationTool initialized")
        }
    }
    
    /**
     * Create notification channels for different priority levels
     */
    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // General channel (low priority)
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }
            
            // Warning channel (medium priority)
            val warningChannel = NotificationChannel(
                CHANNEL_ID_WARNING,
                "Warning Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Screen time warnings"
            }
            
            // Urgent channel (high priority)
            val urgentChannel = NotificationChannel(
                CHANNEL_ID_URGENT,
                "Urgent Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent screen time interventions"
                enableVibration(true)
            }
            
            manager.createNotificationChannel(generalChannel)
            manager.createNotificationChannel(warningChannel)
            manager.createNotificationChannel(urgentChannel)
        }
    }
    
    /**
     * Send a notification
     * 
     * @param context Application context
     * @param title Notification title
     * @param message Notification message
     * @param priority Priority level (0=low, 1=default, 2=high, 3=urgent)
     * @param notificationId Unique ID for this notification
     */
    fun send(
        context: Context,
        title: String,
        message: String,
        priority: Int = 1,
        notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    ) {
        if (!initialized) {
            Log.w(TAG, "NotificationTool not initialized. Initializing now...")
            initialize(context)
        }
        
        // Check permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                return
            }
        }
        
        // Select channel based on priority
        val channelId = when (priority) {
            0 -> CHANNEL_ID_GENERAL
            1 -> CHANNEL_ID_GENERAL
            2 -> CHANNEL_ID_WARNING
            else -> CHANNEL_ID_URGENT
        }
        
        // Select notification priority
        val notificationPriority = when (priority) {
            0 -> NotificationCompat.PRIORITY_LOW
            1 -> NotificationCompat.PRIORITY_DEFAULT
            2 -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_MAX
        }
        
        Log.d(TAG, "Sending notification: title='$title', priority=$priority, id=$notificationId")
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(notificationPriority)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
        
        // Add vibration for high priority
        if (priority >= 2) {
            builder.setVibrate(longArrayOf(0, 200, 100, 200))
        }
        
        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
    
    /**
     * Send a screenshot notification
     * 
     * @param context Application context
     */
    fun sendScreenshotNotification(context: Context) {
        send(
            context = context,
            title = "Screenshot taken",
            message = "A screenshot was captured.",
            priority = 0,
            notificationId = 1002
        )
    }
    
    /**
     * Send a warning notification
     * 
     * @param context Application context
     * @param appName App causing the warning
     * @param duration Duration spent on app
     * @param message Character's warning message
     */
    fun sendWarning(
        context: Context,
        appName: String,
        duration: String,
        message: String
    ) {
        send(
            context = context,
            title = "Screen time warning: $appName",
            message = "$duration - $message",
            priority = 2
        )
    }
    
    /**
     * Send an urgent intervention notification
     * 
     * @param context Application context
     * @param title Notification title
     * @param message Urgent message
     */
    fun sendUrgent(
        context: Context,
        title: String,
        message: String
    ) {
        send(
            context = context,
            title = title,
            message = message,
            priority = 3
        )
    }
    
    /**
     * Cancel a specific notification
     * 
     * @param context Application context
     * @param notificationId ID of notification to cancel
     */
    fun cancel(context: Context, notificationId: Int) {
        with(NotificationManagerCompat.from(context)) {
            cancel(notificationId)
        }
        Log.d(TAG, "Cancelled notification id=$notificationId")
    }
    
    /**
     * Cancel all notifications
     * 
     * @param context Application context
     */
    fun cancelAll(context: Context) {
        with(NotificationManagerCompat.from(context)) {
            cancelAll()
        }
        Log.d(TAG, "Cancelled all notifications")
    }
}

