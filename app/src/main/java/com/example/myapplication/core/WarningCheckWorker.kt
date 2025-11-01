package com.example.myapplication.core

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.myapplication.ui.DialogueEntry
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.agents.PatternAgent
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
 * Runs every 5 minutes (configurable) to detect extended app sessions and other violations.
 * Uses WorkManager for battery-efficient, reliable periodic execution.
 * 
 * When a violation is detected:
 * - Checks urgency threshold
 * - Generates character response via PersonalityAgent
 * - Displays appropriate UI (DialogueQueue for 4-6, overlay for 7-10)
 */
class WarningCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "WarningCheckWorker"
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "WarningCheckWorker: Starting violation check")
            
            // Initialize managers
            EnhancedMemoryManager.initialize(applicationContext)
            ChatManager.initialize(applicationContext)
            
            // Get active character and threshold
            val prefs = PrefsHelper(applicationContext)
            val activeCharacter = prefs.getActiveCharacter()
            val urgencyThreshold = prefs.getWarningUrgencyThreshold()
            
            // Check for violations
            val violation = PatternAgent.checkViolations(applicationContext)
            
            if (violation == null) {
                Log.d(TAG, "No violations detected")
                return Result.success()
            }
            
            Log.d(TAG, "Violation detected: ${violation.appName}, urgency=${violation.urgency}, threshold=$urgencyThreshold")
            
            // Only proceed if urgency meets threshold
            if (violation.urgency < urgencyThreshold) {
                Log.d(TAG, "Violation urgency (${violation.urgency}) below threshold ($urgencyThreshold), skipping")
                return Result.success()
            }
            
            // Generate character response
            val response = PersonalityAgent.respondToPattern(
                applicationContext,
                activeCharacter,
                violation
            )
            
            if (response.isBlank()) {
                Log.w(TAG, "PersonalityAgent returned empty response")
                return Result.success() // Don't fail, just skip this check
            }
            
            // Display UI based on urgency
            withContext(Dispatchers.Main) {
                when (violation.urgency) {
                    in 4..6 -> {
                        // Medium urgency: High-priority dialogue bubble
                        Log.d(TAG, "Showing high-priority dialogue for urgency ${violation.urgency}")
                        val entry = DialogueEntry(
                            speaker = "Ralsei",
                            text = response,
                            relativePath = emotionToRelativePath("concerned")
                        )
                        // Use enqueueFront to prioritize warnings over commentary
                        DialogueQueue.enqueueFront(listOf(entry))
                    }
                    in 7..10 -> {
                        // High urgency: Soft intervention overlay
                        Log.d(TAG, "Showing soft intervention overlay for urgency ${violation.urgency}")
                        SoftInterventionOverlay.show(applicationContext, response, violation)
                    }
                    else -> {
                        // Low urgency (shouldn't happen due to threshold check above)
                        Log.d(TAG, "Low urgency violation (${violation.urgency}), showing normal dialogue")
                        val entry = DialogueEntry(
                            speaker = "Ralsei",
                            text = response,
                            relativePath = emotionToRelativePath("normal")
                        )
                        DialogueQueue.enqueue(listOf(entry))
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
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Need network for LLM calls
                .build()
            
            val request = PeriodicWorkRequestBuilder<WarningCheckWorker>(
                5, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "warning_check_work",
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already scheduled
                request
            )
            
            Log.d("WarningCheckWorker", "Scheduled periodic warning checks (every 5 minutes)")
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

