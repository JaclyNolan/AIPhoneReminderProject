package com.example.myapplication.core

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.myapplication.ui.DialogueEntry
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.agents.PersonalityAgent
import com.example.myapplication.memory.EnhancedMemoryManager
import com.example.myapplication.OverlayDialogueController
import com.example.myapplication.SoftInterventionOverlay
import com.example.myapplication.PrefsHelper
import com.example.myapplication.ui.DialogueQueue
import com.example.myapplication.ui.emotionToRelativePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WarningCheckWorker: Periodic background worker that checks for pattern violations
 * 
 * Runs every 5 minutes (configurable) to check usage patterns and make intervention decisions.
 * Uses WorkManager for battery-efficient, reliable periodic execution.
 * 
 * Flow:
 * 1. Requests usage context from UsagePatternContextProvider (cached, auto-refreshes if stale)
 * 2. PersonalityAgent makes decision with full context (ChatHistory, PhoneState, Memories)
 * 3. Displays appropriate UI based on intervention type:
 *    - DIALOGUE_ONLY: DialogueQueue (urgency 4-6)
 *    - DIALOGUE_WITH_DIMMING: SoftInterventionOverlay (urgency 7-10)
 */
class WarningCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "WarningCheckWorker"
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "WarningCheckWorker: Starting violation check")
            
            // Check if warning system is still enabled (prevents stale WorkManager jobs from executing)
            val prefs = PrefsHelper(applicationContext)
            if (!prefs.isWarningSystemEnabled()) {
                Log.d(TAG, "Warning system disabled - skipping check")
                return Result.success() // Don't fail, just skip
            }
            
            // Initialize managers
            EnhancedMemoryManager.initialize(applicationContext)
            ChatManager.initialize(applicationContext)
            
            // Get active character and threshold
            val activeCharacter = prefs.getActiveCharacter()
            val urgencyThreshold = prefs.getWarningUrgencyThreshold()
            
            // Make intervention decision (uses UsagePatternContextProvider + PersonalityAgent)
            val decision = PersonalityAgent.makeDecision(applicationContext, activeCharacter)
            
            if (!decision.shouldIntervene) {
                Log.d(TAG, "No intervention needed")
                return Result.success()
            }
            
            Log.d(TAG, "Intervention decision: urgency=${decision.urgency}, type=${decision.interventionType}, threshold=$urgencyThreshold")
            
            // Only proceed if urgency meets threshold
            if (decision.urgency < urgencyThreshold) {
                Log.d(TAG, "Urgency (${decision.urgency}) below threshold ($urgencyThreshold), skipping")
                return Result.success()
            }
            
            if (decision.response.isBlank()) {
                Log.w(TAG, "PersonalityAgent returned empty response")
                return Result.success() // Don't fail, just skip this check
            }
            
            // Display UI based on intervention type
            withContext(Dispatchers.Main) {
                when (decision.interventionType) {
                    PersonalityAgent.InterventionType.NONE -> {
                        Log.d(TAG, "No intervention type specified")
                    }
                    PersonalityAgent.InterventionType.DIALOGUE_ONLY -> {
                        // Medium urgency: High-priority dialogue bubble
                        Log.d(TAG, "Showing dialogue for urgency ${decision.urgency}")
                        
                        // Use structured response if available, otherwise fall back to manual emotion
                        val entries = if (decision.structuredResponse != null && decision.structuredResponse.isNotEmpty()) {
                            // Use structured response with emotions from LLM
                            decision.structuredResponse
                        } else {
                            // Fallback: create entry with urgency-based emotion
                            val emotion = when {
                                decision.urgency >= 7 -> "concerned"
                                decision.urgency >= 4 -> "worried"
                                else -> "normal"
                            }
                            listOf(DialogueEntry(
                                speaker = "Ralsei",
                                text = decision.response,
                                relativePath = emotionToRelativePath(emotion)
                            ))
                        }
                        // Use enqueueFront to prioritize warnings over commentary
                        DialogueQueue.enqueueFront(entries)
                    }
                    PersonalityAgent.InterventionType.DIALOGUE_WITH_DIMMING -> {
                        // High urgency: Soft intervention overlay (need to create violation object for backward compatibility)
                        Log.d(TAG, "Showing soft intervention overlay for urgency ${decision.urgency}")
                        // TODO: Update SoftInterventionOverlay to not require PatternViolation
                        // For now, create minimal violation object
                        val dummyViolation = com.example.myapplication.context.UsagePatternDetector.PatternViolation(
                            appName = "Current App",
                            appDisplayName = "Current App",
                            urgency = decision.urgency,
                            context = decision.response
                        )
                        SoftInterventionOverlay.show(applicationContext, decision.response, dummyViolation)
                    }
                }
            }
            
            Log.d(TAG, "Warning check completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in WarningCheckWorker", e)
            // Return retry result so WorkManager can retry if needed
            Result.retry()
        }
    }
    
    companion object {
        /**
         * Schedule periodic warning checks every 5 minutes
         * 
         * @param context Application context
         */
        fun schedulePeriodicCheck(context: Context) {
            // Check if warning system is enabled before scheduling
            val prefs = PrefsHelper(context)
            if (!prefs.isWarningSystemEnabled()) {
                Log.d("WarningCheckWorker", "Warning system disabled - not scheduling")
                return
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Need network for LLM calls
                .build()
            
            // Add initial delay (3 minutes) to prevent immediate execution on app start
            val request = PeriodicWorkRequestBuilder<WarningCheckWorker>(
                5, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(3, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            
            // Use REPLACE to ensure fresh settings are applied
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "warning_check_work",
                ExistingPeriodicWorkPolicy.REPLACE, // Replace existing to update settings
                request
            )
            
            Log.d("WarningCheckWorker", "Scheduled periodic warning checks (every 5 minutes, initial delay 3 minutes)")
        }
        
        /**
         * Cancel periodic warning checks
         * 
         * @param context Application context
         */
        fun cancelPeriodicCheck(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("warning_check_work")
            Log.d("WarningCheckWorker", "Cancelled periodic warning checks")
        }
    }
}

