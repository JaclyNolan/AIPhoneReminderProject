package com.example.myapplication.tools

import android.content.Context
import android.util.Log
import com.example.myapplication.SoftInterventionOverlay

/**
 * SoftInterventionTool: Full-screen dimmed intervention for high urgency
 * 
 * Single responsibility: Display screen dimming with character dialogue
 * Wraps SoftInterventionOverlay for modular tool interface
 */
object SoftInterventionTool {
    private const val TAG = "SoftInterventionTool"
    
    /**
     * Activate soft intervention overlay
     * 
     * @param context Application context
     * @param message Character's response message
     * @param violation Pattern violation that triggered this intervention
     */
    fun activate(
        context: Context,
        message: String,
        violation: com.example.myapplication.agents.PatternAgent.PatternViolation
    ) {
        if (violation.urgency < 7) {
            Log.w(TAG, "Soft intervention typically used for urgency >= 7. Got urgency=${violation.urgency}")
        }
        
        Log.d(TAG, "Activating soft intervention: urgency=${violation.urgency}, app=${violation.appName}")
        
        SoftInterventionOverlay.show(
            context = context,
            response = message,
            violation = violation
        )
    }
    
    /**
     * Dismiss soft intervention overlay
     * 
     * @param context Application context
     */
    fun dismiss(context: Context) {
        Log.d(TAG, "Dismissing soft intervention")
        SoftInterventionOverlay.dismiss(context)
    }
    
    /**
     * Check if soft intervention is currently active
     * 
     * @return True if overlay is showing, false otherwise
     */
    fun isActive(): Boolean {
        return SoftInterventionOverlay.isShowing()
    }
    
    /**
     * Activate if urgency threshold met
     * 
     * @param context Application context
     * @param message Character's response message
     * @param violation Pattern violation to evaluate
     * @param threshold Minimum urgency to activate (default: 7)
     * @return True if activated, false if threshold not met
     */
    fun activateIfUrgent(
        context: Context,
        message: String,
        violation: com.example.myapplication.agents.PatternAgent.PatternViolation,
        threshold: Int = 7
    ): Boolean {
        return if (violation.urgency >= threshold) {
            activate(context, message, violation)
            true
        } else {
            Log.d(TAG, "Urgency ${violation.urgency} below threshold $threshold, not activating")
            false
        }
    }
}

