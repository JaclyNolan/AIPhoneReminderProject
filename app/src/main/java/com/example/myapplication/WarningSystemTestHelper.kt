package com.example.myapplication

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import com.example.myapplication.agents.ChatManager
import com.example.myapplication.agents.PatternAgent
import com.example.myapplication.agents.PersonalityAgent
import com.example.myapplication.memory.EnhancedMemoryManager

/**
 * Test Helper for Warning System
 * 
 * Makes it easier to manually test violation detection without waiting for natural sessions.
 * Add these functions to your test code or call from Android Studio's "Evaluate Expression".
 * 
 * Usage in Android Studio:
 * 1. Set breakpoint in MainActivity
 * 2. Right-click → Evaluate Expression
 * 3. Paste: WarningSystemTestHelper.createTestSession(context, "YouTube", 35)
 * 4. Continue execution
 */
object WarningSystemTestHelper {
    private const val TAG = "WarningSystemTestHelper"
    
    /**
     * Create test timeline entries to simulate an extended session
     * 
     * @param context Application context
     * @param appName App name (sceneLabel) to simulate
     * @param durationMinutes How many minutes to simulate
     * @param intervalSeconds Interval between entries (default 10 seconds)
     */
    fun createTestSession(
        context: Context,
        appName: String,
        durationMinutes: Int,
        intervalSeconds: Int = 10
    ) {
        EnhancedMemoryManager.initialize(context)
        
        val now = System.currentTimeMillis()
        val intervalMs = intervalSeconds * 1000L
        val numEntries = (durationMinutes * 60) / intervalSeconds
        
        Log.d(TAG, "Creating $numEntries test entries for $appName over $durationMinutes minutes")
        
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        
        for (i in 0 until numEntries) {
            val entryTime = now - ((numEntries - i) * intervalMs)
            val timestamp = timestampFormat.format(Date(entryTime))
            
            val entry = EnhancedMemoryManager.SceneTimelineEntry(
                timestamp = timestamp,
                sceneLabel = appName,
                shortText = "Test session entry $i",
                confidence = 0.8
            )
            
            EnhancedMemoryManager.addSceneTimelineEntry(context, entry)
        }
        
        Log.d(TAG, "Test session created: $numEntries entries for $appName")
    }
    
    /**
     * Test PatternAgent violation detection
     * 
     * @param context Application context
     * @return String result message (violation details or "No violation")
     */
    fun testViolationDetection(context: Context): String {
        EnhancedMemoryManager.initialize(context)
        ChatManager.initialize(context)
        
        val violation = PatternAgent.checkViolations(context)
        
        val result = if (violation != null) {
            buildString {
                appendLine("✅ Violation detected!")
                appendLine("  App: ${violation.appName}")
                appendLine("  Urgency: ${violation.urgency}/10")
                appendLine("  Context:")
                violation.context.lines().forEach { line ->
                    appendLine("    $line")
                }
            }
        } else {
            "❌ No violation detected"
        }
        
        Log.d(TAG, result)
        return result
    }
    
    /**
     * Clear test data (timeline and chat history)
     * 
     * @param context Application context
     */
    fun clearTestData(context: Context) {
        EnhancedMemoryManager.initialize(context)
        EnhancedMemoryManager.clear(context)
        ChatManager.clearHistory(context)
        Log.d(TAG, "Test data cleared")
    }
    
    /**
     * Trigger warning check manually (for testing WarningCheckWorker logic)
     * 
     * @param context Application context
     */
    suspend fun triggerWarningCheck(context: Context) {
        val prefs = PrefsHelper(context)
        val activeCharacter = prefs.getActiveCharacter()
        val urgencyThreshold = prefs.getWarningUrgencyThreshold()
        
        val violation = PatternAgent.checkViolations(context)
        
        if (violation != null && violation.urgency >= urgencyThreshold) {
            Log.d(TAG, "⚠️ Triggering warning: urgency=${violation.urgency}, threshold=$urgencyThreshold")
            
            val response = PersonalityAgent.respondToPattern(context, activeCharacter, violation)
            Log.d(TAG, "Response generated: ${response.take(100)}...")
            
            // Note: Actual UI display (DialogueQueue/Overlay) happens in WarningCheckWorker
            // This just tests the logic without the UI
        } else {
            Log.d(TAG, "No warning triggered (violation=${violation != null}, urgency=${violation?.urgency}, threshold=$urgencyThreshold)")
        }
    }
}

