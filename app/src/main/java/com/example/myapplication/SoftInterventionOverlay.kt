package com.example.myapplication

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.myapplication.agents.PatternAgent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.TimeUnit

/**
 * SoftInterventionOverlay: Full-screen dimmed overlay for high-urgency warnings (urgency 7-10)
 * 
 * Shows:
 * - Dimmed background (80% opacity black overlay)
 * - Character response dialogue
 * - Countdown timer (default 5 minutes)
 * - Two action buttons: "Take a break" and "5 min more"
 * 
 * Pauses screenshots while displayed and resumes when dismissed.
 */
object SoftInterventionOverlay {
    private const val TAG = "SoftInterventionOverlay"
    private const val DEFAULT_TIMER_MINUTES = 5L
    
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var attached = false
    private var countdownTimer: CountDownTimer? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Show the soft intervention overlay
     * 
     * @param context Application context
     * @param response Character response text
     * @param violation Pattern violation that triggered this intervention
     */
    fun show(context: Context, response: String, violation: PatternAgent.PatternViolation) {
        if (!Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }
        
        if (attached) {
            Log.w(TAG, "Overlay already showing, dismissing old one first")
            dismiss(context)
        }
        
        try {
            // Pause screenshots
            ScreenshotPauseController.requestPause(context, "SoftInterventionOverlay")
            Log.d(TAG, "Screenshots paused for soft intervention")
            
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Create ComposeView
            composeView = ComposeView(context)
            
            // Setup lifecycle owner (similar to OverlayDialogueController pattern)
            overlayLifecycleOwner = OverlayLifecycleOwner()
            setViewTreeLifecycleOwnerReflectively(composeView as View, overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwnerReflectively(composeView as View, overlayLifecycleOwner)
            setViewTreeViewModelStoreOwnerReflectively(composeView as View, overlayLifecycleOwner)
            
            overlayLifecycleOwner?.moveToState(Lifecycle.State.CREATED)
            composeView?.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            
            // Set Compose content
            composeView?.setContent {
                MyApplicationTheme(darkTheme = PrefsHelper(context).isDarkTheme()) {
                    SoftInterventionUI(
                        response = response,
                        violation = violation,
                        onTakeBreak = { dismiss(context) },
                        onFiveMore = {
                            dismiss(context)
                            // TODO: Log extension request for future pattern learning
                            Log.d(TAG, "User requested 5 minute extension")
                        }
                    )
                }
            }
            
            // Window parameters for full-screen overlay
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                /* windowType */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            
            windowManager?.addView(composeView, layoutParams)
            attached = true
            overlayLifecycleOwner?.moveToState(Lifecycle.State.RESUMED)
            
            Log.d(TAG, "Soft intervention overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show soft intervention overlay", e)
            // Clean up on error
            try {
                ScreenshotPauseController.requestResume(context, "SoftInterventionOverlay")
            } catch (_: Exception) {}
        }
    }
    
    /**
     * Dismiss the overlay
     */
    fun dismiss(context: Context) {
        if (!attached) return
        
        try {
            // Stop countdown timer
            countdownTimer?.cancel()
            countdownTimer = null
            
            // Move lifecycle to DESTROYED
            overlayLifecycleOwner?.moveToState(Lifecycle.State.DESTROYED)
            
            // Remove view
            composeView?.let { view ->
                clearViewTreeOwnersReflectively(view)
                windowManager?.removeViewImmediate(view)
            }
            
            composeView = null
            overlayLifecycleOwner = null
            attached = false
            
            // Resume screenshots
            ScreenshotPauseController.requestResume(context, "SoftInterventionOverlay")
            Log.d(TAG, "Soft intervention overlay dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing overlay", e)
        }
    }
    
    /**
     * Check if overlay is currently showing
     */
    fun isShowing(): Boolean = attached
    
    // Compose UI for the overlay
    @Composable
    private fun SoftInterventionUI(
        response: String,
        violation: PatternAgent.PatternViolation,
        onTakeBreak: () -> Unit,
        onFiveMore: () -> Unit
    ) {
        var timeRemaining by remember { mutableStateOf(DEFAULT_TIMER_MINUTES * 60 * 1000L) }
        
        // Start countdown timer
        LaunchedEffect(Unit) {
            countdownTimer = object : CountDownTimer(DEFAULT_TIMER_MINUTES * 60 * 1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeRemaining = millisUntilFinished
                }
                
                override fun onFinish() {
                    timeRemaining = 0
                    onTakeBreak() // Auto-dismiss when timer finishes
                }
            }.apply { start() }
        }
        
        // Cleanup timer on dispose
        DisposableEffect(Unit) {
            onDispose {
                countdownTimer?.cancel()
                countdownTimer = null
            }
        }
        
        // Format timer display
        val minutes = (timeRemaining / 60_000).toInt()
        val seconds = ((timeRemaining % 60_000) / 1000).toInt()
        val timerText = String.format("%d:%02d", minutes, seconds)
        
        // Full-screen dimmed background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
        ) {
            // Content card in center
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Character portrait placeholder (could add actual portrait later)
                    Text(
                        text = "😟", // Placeholder - could show Ralsei portrait
                        fontSize = 64.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Character response
                    Text(
                        text = response,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp),
                        lineHeight = 24.sp
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Timer display
                    Text(
                        text = timerText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Text(
                        text = "Time remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onFiveMore,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("5 min more")
                        }
                        
                        Button(
                            onClick = onTakeBreak,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Take a break")
                        }
                    }
                }
            }
        }
    }
    
    // Lifecycle owner for overlay (same pattern as OverlayDialogueController)
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val vmStore = ViewModelStore()
        
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry = savedStateController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = vmStore
        
        fun moveToState(state: Lifecycle.State) {
            try {
                if (state == Lifecycle.State.CREATED) {
                    savedStateController.performRestore(null)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "SavedState restore failed", t)
            }
            
            registry.currentState = state
            
            if (state == Lifecycle.State.DESTROYED) {
                try {
                    val out = android.os.Bundle()
                    savedStateController.performSave(out)
                } catch (t: Throwable) {
                    Log.w(TAG, "SavedState save failed", t)
                }
                try {
                    viewModelStore.clear()
                } catch (t: Throwable) {
                    Log.w(TAG, "ViewModelStore clear failed", t)
                }
            }
        }
    }
    
    // Reflection helpers (same pattern as OverlayDialogueController)
    private fun setViewTreeLifecycleOwnerReflectively(view: View, owner: LifecycleOwner?) {
        try {
            val cls = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val method = cls.getMethod("set", View::class.java, LifecycleOwner::class.java)
            method.invoke(null, view, owner)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set ViewTreeLifecycleOwner", t)
        }
    }
    
    private fun setViewTreeSavedStateRegistryOwnerReflectively(view: View, owner: SavedStateRegistryOwner?) {
        try {
            val cls = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            val method = cls.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
            method.invoke(null, view, owner)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set ViewTreeSavedStateRegistryOwner", t)
        }
    }
    
    private fun setViewTreeViewModelStoreOwnerReflectively(view: View, owner: ViewModelStoreOwner?) {
        try {
            val cls = Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
            val method = cls.getMethod("set", View::class.java, ViewModelStoreOwner::class.java)
            method.invoke(null, view, owner)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set ViewTreeViewModelStoreOwner", t)
        }
    }
    
    private fun clearViewTreeOwnersReflectively(view: View) {
        setViewTreeLifecycleOwnerReflectively(view, null)
        setViewTreeSavedStateRegistryOwnerReflectively(view, null)
        setViewTreeViewModelStoreOwnerReflectively(view, null)
    }
}

