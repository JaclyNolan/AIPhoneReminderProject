package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ScreenshotPauseController manages screenshot pause/resume requests with source tracking
 * to prevent overlapping pause/resume issues from multiple sources (ChatManager, AnalyzerAgent, OverlayDialogueController).
 *
 * Uses a Set to track unique sources instead of reference counting, so each source can call
 * requestPause() multiple times but screenshots only resume when that source calls requestResume().
 */
object ScreenshotPauseController {
    private const val TAG = "ScreenshotPauseController"

    // Track which sources currently have screenshots paused
    // When set is empty, screenshots are running
    // When set has items, screenshots are paused
    private val pausedSources = mutableSetOf<String>()

    // Lock for thread-safe access to pausedSources
    private val lock = Any()

    /**
     * Request to pause screenshots.
     * Can be called multiple times by the same source - only the first call matters.
     *
     * @param context Application context
     * @param source Identifier for the source requesting pause (e.g., "ChatManager", "AnalyzerAgent")
     */
    fun requestPause(context: Context, source: String) {
        synchronized(lock) {
            val wasEmpty = pausedSources.isEmpty()
            val wasAdded = pausedSources.add(source)

            if (wasAdded) {
                Log.d(TAG, "Pause requested by [$source], active sources: ${pausedSources.size} - $pausedSources")
            } else {
                Log.d(TAG, "Pause re-requested by [$source] (already paused), active sources: ${pausedSources.size}")
            }

            // Only send broadcast if this is the first pause request
            if (wasEmpty && pausedSources.isNotEmpty()) {
                sendPauseBroadcast(context)
            }
        }
    }

    /**
     * Request to resume screenshots.
     * Can be called multiple times by the same source - only the first call matters.
     * Screenshots will only resume when ALL sources have called resume.
     *
     * @param context Application context
     * @param source Identifier for the source requesting resume
     */
    fun requestResume(context: Context, source: String) {
        synchronized(lock) {
            val wasRemoved = pausedSources.remove(source)

            if (wasRemoved) {
                Log.d(TAG, "Resume requested by [$source], active sources: ${pausedSources.size} - $pausedSources")
            } else {
                Log.w(TAG, "Resume requested by [$source] but source was not in paused set, ignoring")
                return
            }

            // Only send broadcast if this was the last pause request
            if (pausedSources.isEmpty()) {
                sendResumeBroadcast(context)
            }
        }
    }

    /**
     * Force resume screenshots and clear all sources.
     * Use this for emergency cleanup or when you know all sources should be cleared.
     */
    fun forceResume(context: Context, reason: String) {
        synchronized(lock) {
            val hadSources = pausedSources.isNotEmpty()
            val sourcesCount = pausedSources.size
            val sourcesList = pausedSources.toList()
            pausedSources.clear()

            Log.d(TAG, "Force resume requested: $reason (cleared $sourcesCount sources: $sourcesList)")

            if (hadSources) {
                sendResumeBroadcast(context)
            }
        }
    }

    /**
     * Check if screenshots are currently paused
     */
    fun isPaused(): Boolean {
        synchronized(lock) {
            return pausedSources.isNotEmpty()
        }
    }

    /**
     * Get current number of sources that have paused screenshots (for debugging)
     */
    fun getPausedSourceCount(): Int {
        synchronized(lock) {
            return pausedSources.size
        }
    }

    /**
     * Get list of sources that have paused screenshots (for debugging)
     */
    fun getPausedSources(): List<String> {
        synchronized(lock) {
            return pausedSources.toList()
        }
    }

    private fun sendPauseBroadcast(context: Context) {
        try {
            val intent = Intent(ServiceActions.ACTION_PAUSE_SCREENSHOT)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            Log.d(TAG, "📸 ⏸️  Sent ACTION_PAUSE_SCREENSHOT broadcast")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send pause broadcast", e)
        }
    }

    private fun sendResumeBroadcast(context: Context) {
        try {
            val intent = Intent(ServiceActions.ACTION_RESUME_SCREENSHOT)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            Log.d(TAG, "📸 ▶️  Sent ACTION_RESUME_SCREENSHOT broadcast")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send resume broadcast", e)
        }
    }
}
