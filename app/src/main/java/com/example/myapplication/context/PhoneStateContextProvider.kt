package com.example.myapplication.context

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * PhoneStateContextProvider: Provides phone system state context
 * 
 * Single responsibility: Query Android system state
 * (battery, network, time, DND status)
 * Used by agents for context-aware interventions
 */
object PhoneStateContextProvider {
    private const val TAG = "PhoneStateContextProvider"
    
    data class PhoneState(
        val batteryLevel: Int,
        val isCharging: Boolean,
        val isLowBattery: Boolean,
        val networkType: NetworkType,
        val isDoNotDisturb: Boolean,
        val timeOfDay: TimeOfDay,
        val currentTime: String,
        val dayOfWeek: String
    )
    
    enum class NetworkType {
        WIFI,
        CELLULAR,
        NONE
    }
    
    enum class TimeOfDay {
        MORNING,      // 5am-12pm
        AFTERNOON,    // 12pm-5pm
        EVENING,      // 5pm-9pm
        NIGHT,        // 9pm-5am
        LATE_NIGHT    // 12am-5am
    }
    
    /**
     * Get complete phone state snapshot
     * 
     * @param context Application context
     * @return PhoneState with all system information
     */
    fun getPhoneState(context: Context): PhoneState {
        return PhoneState(
            batteryLevel = getBatteryLevel(context),
            isCharging = isCharging(context),
            isLowBattery = isLowBattery(context),
            networkType = getNetworkType(context),
            isDoNotDisturb = isDoNotDisturb(context),
            timeOfDay = getTimeOfDay(),
            currentTime = getCurrentTime(),
            dayOfWeek = getDayOfWeek()
        )
    }
    
    /**
     * Get battery level percentage
     * 
     * @param context Application context
     * @return Battery level (0-100)
     */
    fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            
            if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                100 // Default to full if unable to read
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery level", e)
            100
        }
    }
    
    /**
     * Check if device is charging
     * 
     * @param context Application context
     * @return True if charging, false otherwise
     */
    fun isCharging(context: Context): Boolean {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            Log.e(TAG, "Error checking charging status", e)
            false
        }
    }
    
    /**
     * Check if battery is low (below 20%)
     * 
     * @param context Application context
     * @return True if battery below 20%, false otherwise
     */
    fun isLowBattery(context: Context): Boolean {
        return getBatteryLevel(context) < 20
    }
    
    /**
     * Get network connection type
     * 
     * @param context Application context
     * @return NetworkType (WIFI, CELLULAR, or NONE)
     */
    fun getNetworkType(context: Context): NetworkType {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager?.activeNetwork ?: return NetworkType.NONE
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
                
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                    else -> NetworkType.NONE
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager?.activeNetworkInfo
                when (networkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                    ConnectivityManager.TYPE_MOBILE -> NetworkType.CELLULAR
                    else -> NetworkType.NONE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network type", e)
            NetworkType.NONE
        }
    }
    
    /**
     * Check if Do Not Disturb mode is enabled
     * 
     * @param context Application context
     * @return True if DND enabled, false otherwise
     */
    fun isDoNotDisturb(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val filter = notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                filter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                        filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                        filter == NotificationManager.INTERRUPTION_FILTER_ALARMS
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking DND status", e)
            false
        }
    }
    
    /**
     * Get current time of day category
     * 
     * @return TimeOfDay enum value
     */
    fun getTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..16 -> TimeOfDay.AFTERNOON
            in 17..20 -> TimeOfDay.EVENING
            in 21..23 -> TimeOfDay.NIGHT
            else -> TimeOfDay.LATE_NIGHT
        }
    }
    
    /**
     * Get current time as formatted string
     * 
     * @return Current time (HH:mm:ss format)
     */
    fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        return formatter.format(Date())
    }
    
    /**
     * Get current day of week
     * 
     * @return Day name (e.g., "Monday")
     */
    fun getDayOfWeek(): String {
        val formatter = SimpleDateFormat("EEEE", Locale.US)
        return formatter.format(Date())
    }
    
    /**
     * Check if it's a good time for intervention
     * (not charging, not low battery, not late night)
     * 
     * @param context Application context
     * @return True if good time for intervention
     */
    fun isGoodTimeForIntervention(context: Context): Boolean {
        val state = getPhoneState(context)
        return !state.isCharging && 
               !state.isLowBattery && 
               state.timeOfDay != TimeOfDay.LATE_NIGHT
    }
}

