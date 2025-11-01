package com.example.myapplication.tools

import android.content.Context
import android.util.Log
import com.example.myapplication.OverlayDialogueController
import com.example.myapplication.ui.DialogueEntry

/**
 * DialogueTool: Display character dialogue overlay
 * 
 * Single responsibility: Show character speech bubbles on screen
 * Wraps OverlayDialogueController for modular tool interface
 */
object DialogueTool {
    private const val TAG = "DialogueTool"
    private var controller: OverlayDialogueController? = null
    
    /**
     * Initialize the dialogue tool with context
     * Must be called before use
     * 
     * @param context Application context
     */
    fun initialize(context: Context) {
        if (controller == null) {
            controller = OverlayDialogueController(context)
            Log.d(TAG, "DialogueTool initialized")
        }
    }
    
    /**
     * Show a single dialogue message
     * 
     * @param message Text to display
     * @param emotion Character emotion (e.g., "neutral", "happy", "concerned")
     * @param source Message source ("commentary", "warning", "user", "assistant")
     */
    fun show(
        message: String,
        emotion: String = "neutral",
        source: String = "commentary"
    ) {
        if (controller == null) {
            Log.e(TAG, "DialogueTool not initialized. Call initialize() first.")
            return
        }
        
        Log.d(TAG, "Showing dialogue: emotion=$emotion, source=$source")
        
        val entry = DialogueEntry(
            text = message,
            relativePath = com.example.myapplication.ui.emotionToRelativePath(emotion)
        )
        
        // OverlayDialogueController uses DialogueQueue internally
        com.example.myapplication.ui.DialogueQueue.enqueue(entry)
    }
    
    /**
     * Show multiple dialogue messages in sequence
     * 
     * @param dialogues List of dialogue entries
     * @param source Message source ("commentary", "warning", "user", "assistant")
     */
    fun showMultiple(
        dialogues: List<DialogueEntry>,
        source: String = "commentary"
    ) {
        if (controller == null) {
            Log.e(TAG, "DialogueTool not initialized. Call initialize() first.")
            return
        }
        
        Log.d(TAG, "Showing ${dialogues.size} dialogues, source=$source")
        
        // OverlayDialogueController uses DialogueQueue internally
        com.example.myapplication.ui.DialogueQueue.enqueue(dialogues)
    }
    
    /**
     * Clear all queued dialogues
     */
    fun clear() {
        com.example.myapplication.ui.DialogueQueue.clear()
        Log.d(TAG, "Dialogue queue cleared")
    }
    
    /**
     * Show dialogue if not currently showing
     * 
     * @param message Text to display
     * @param emotion Character emotion
     * @param source Message source
     * @return True if shown, false if already showing dialogue
     */
    fun showIfIdle(
        message: String,
        emotion: String = "neutral",
        source: String = "commentary"
    ): Boolean {
        val dialogueController = controller
        if (dialogueController == null) {
            Log.e(TAG, "DialogueTool not initialized. Call initialize() first.")
            return false
        }
        
        // Check if dialogue is already showing
        // For now, always show (OverlayDialogueController handles queueing)
        show(message, emotion, source)
        return true
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        controller?.destroy()
        controller = null
        Log.d(TAG, "DialogueTool cleaned up")
    }
}

