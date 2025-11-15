package com.example.myapplication.testing.mockcontext

import android.content.Context
import android.util.Log
import com.example.myapplication.context.PhoneStateContextProvider
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mock PhoneStateContextProvider for testing
 */
object MockPhoneStateContextProvider {
    private const val TAG = "MockPhoneStateContextProvider"
    
    private var mockState: PhoneStateContextProvider.PhoneState? = null
    
    /**
     * Set mock phone state
     */
    fun setMockState(state: PhoneStateContextProvider.PhoneState) {
        mockState = state
        Log.d(TAG, "Set mock phone state")
    }
    
    /**
     * Get mock phone state (or default if not set)
     */
    fun getPhoneState(context: Context): PhoneStateContextProvider.PhoneState {
        return mockState ?: PhoneStateContextProvider.PhoneState(
            batteryLevel = 75,
            isCharging = false,
            isLowBattery = false,
            networkType = PhoneStateContextProvider.NetworkType.WIFI,
            isDoNotDisturb = false,
            timeOfDay = PhoneStateContextProvider.TimeOfDay.AFTERNOON,
            currentTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
            dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(Date())
        )
    }
    
    /**
     * Set default scenario states
     */
    fun setDefaultScenario(scenarioType: String) {
        val now = Calendar.getInstance()
        mockState = when (scenarioType) {
            "youtube_long_session" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 65,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.AFTERNOON,
                    currentTime = "14:30:00",
                    dayOfWeek = "Tuesday"
                )
            }
            "tiktok_late_night" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 35,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = true,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.LATE_NIGHT,
                    currentTime = "23:45:00",
                    dayOfWeek = "Friday"
                )
            }
            "critical_binge", "repeat_offender" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 45,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.AFTERNOON,
                    currentTime = "15:30:00",
                    dayOfWeek = "Wednesday"
                )
            }
            "extended_morning" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 80,
                    isCharging = true,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.MORNING,
                    currentTime = "06:30:00",
                    dayOfWeek = "Monday"
                )
            }
            "app_hopping_binge" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 60,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.EVENING,
                    currentTime = "19:15:00",
                    dayOfWeek = "Thursday"
                )
            }
            "relapse_pattern", "multiple_sessions_same_app" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 55,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.AFTERNOON,
                    currentTime = "16:20:00",
                    dayOfWeek = "Tuesday"
                )
            }
            "weekend_binge" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 70,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.AFTERNOON,
                    currentTime = "14:00:00",
                    dayOfWeek = "Saturday"
                )
            }
            "low_battery_usage" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 15,
                    isCharging = false,
                    isLowBattery = true,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.EVENING,
                    currentTime = "20:00:00",
                    dayOfWeek = "Wednesday"
                )
            }
            "work_vs_leisure" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 65,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.AFTERNOON,
                    currentTime = "14:30:00",
                    dayOfWeek = "Tuesday"
                )
            }
            "late_night_escalation" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 25,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = true,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.LATE_NIGHT,
                    currentTime = "00:15:00",
                    dayOfWeek = "Saturday"
                )
            }
            "minimal_usage" -> {
                PhoneStateContextProvider.PhoneState(
                    batteryLevel = 75,
                    isCharging = false,
                    isLowBattery = false,
                    networkType = PhoneStateContextProvider.NetworkType.WIFI,
                    isDoNotDisturb = false,
                    timeOfDay = PhoneStateContextProvider.TimeOfDay.AFTERNOON,
                    currentTime = "15:00:00",
                    dayOfWeek = "Tuesday"
                )
            }
            else -> null
        }
    }
    
    /**
     * Get current mock data for display
     */
    fun getCurrentMockData(): PhoneStateContextProvider.PhoneState? {
        return mockState
    }
    
    /**
     * Reset to default state
     */
    fun reset() {
        mockState = null
    }
}

