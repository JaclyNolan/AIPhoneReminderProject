package com.example.myapplication

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.core.MainForegroundService
import com.example.myapplication.core.WarningCheckWorker
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class FloatingControlOverlay(private val context: Context) {
    private val TAG = "FloatingControlOverlay"
    private val prefs by lazy { PrefsHelper(context) }
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    var onDismissListener: (() -> Unit)? = null
    
    private var composeView: ComposeView? = null
    private var attached = false
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var prefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val sharedPrefs by lazy { context.getSharedPreferences("screenshot_prefs", Context.MODE_PRIVATE) }
    
    private var removeZoneOverlayView: ComposeView? = null
    private var removeZoneOverlayParams: WindowManager.LayoutParams? = null
    private var removeZoneOverlayOwner: OverlayLifecycleOwner? = null
    private var isRemoveZoneVisible = false
    
    private val screenHeight: Int by lazy {
        context.resources.displayMetrics.heightPixels
    }
    private val screenWidth: Int by lazy {
        context.resources.displayMetrics.widthPixels
    }
    private val removeZoneHeight: Int by lazy {
        (screenHeight * 0.1f).toInt()
    }
    
    private var totalDragDistance = 0f
    private var isDragging = false
    private val dragThreshold = 10 * context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    
    private fun snapToEdge(currentX: Int, currentY: Int) {
        val params = layoutParams ?: return
        val screenWidth = context.resources.displayMetrics.widthPixels
        val widgetWidth = 80.dp.toPx(context).toInt()
        val targetX = if (currentX < screenWidth / 2) {
            0
        } else {
            screenWidth - widgetWidth
        }
        
        if (targetX == params.x) {
            prefs.setFloatingOverlayPosition(params.x, params.y)
            return
        }
        
        val startX = params.x.toFloat()
        val animator = ValueAnimator.ofFloat(startX, targetX.toFloat()).apply {
            duration = 200
            addUpdateListener { anim ->
                params.x = (anim.animatedValue as Float).toInt()
                try {
                    composeView?.let { windowManager.updateViewLayout(it, params) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update overlay position during snap", e)
                }
            }
        }
        animator.start()
        
        handler.postDelayed({
            prefs.setFloatingOverlayPosition(params.x, params.y)
            Log.d(TAG, "Widget snapped to edge at (${params.x}, ${params.y})")
        }, 200)
    }
    
    fun show() {
        if (attached) {
            Log.d(TAG, "Overlay already attached")
            return
        }
        
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot draw overlays: SYSTEM_ALERT_WINDOW not granted")
            return
        }
        
        try {
            composeView = ComposeView(context)
            
            if (overlayLifecycleOwner == null) overlayLifecycleOwner = OverlayLifecycleOwner()
            setViewTreeLifecycleOwnerReflectively(composeView!!, overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwnerReflectively(composeView!!, overlayLifecycleOwner)
            setViewTreeViewModelStoreOwnerReflectively(composeView!!, overlayLifecycleOwner)
            
            overlayLifecycleOwner?.moveToState(Lifecycle.State.CREATED)
            
            composeView?.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            
            val savedX = prefs.getFloatingOverlayPositionX()
            val savedY = prefs.getFloatingOverlayPositionY()
            
            val defaultX = context.resources.displayMetrics.widthPixels - 80.dp.toPx(context).toInt()
            val defaultY = (screenHeight / 2) - 100
            
            val initialX = if (savedX != 0) savedX else defaultX
            val initialY = if (savedY != 0) savedY else defaultY
            
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }
            
            setOverlayContent()
            
            if (prefListener == null) {
                prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "is_screenshotting") {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            setOverlayContent()
                            Log.d(TAG, "Overlay UI refreshed due to screenshotting state change")
                        }
                    }
                }
            }
            try { sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
            
            windowManager.addView(composeView, layoutParams)
            attached = true
            
            overlayLifecycleOwner?.moveToState(Lifecycle.State.RESUMED)
            
            Log.d(TAG, "Floating overlay attached at ($initialX, $initialY)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating overlay", e)
        }
    }
    
    fun hide() {
        if (!attached) return
        
        hideRemoveZoneHighlight()
        
        try {
            try { sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
            
            overlayLifecycleOwner?.moveToState(Lifecycle.State.DESTROYED)
            
            composeView?.let { view ->
                clearViewTreeOwnersReflectively(view)
                windowManager.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing floating overlay", e)
        } finally {
            attached = false
            composeView = null
            layoutParams = null
            overlayLifecycleOwner = null
            Log.d(TAG, "Floating overlay removed")
        }
    }
    
    private fun showRemoveZoneHighlight() {
        if (isRemoveZoneVisible || !attached) return
        
        try {
            removeZoneOverlayView = ComposeView(context)
            
            if (removeZoneOverlayOwner == null) removeZoneOverlayOwner = OverlayLifecycleOwner()
            setViewTreeLifecycleOwnerReflectively(removeZoneOverlayView!!, removeZoneOverlayOwner)
            setViewTreeSavedStateRegistryOwnerReflectively(removeZoneOverlayView!!, removeZoneOverlayOwner)
            setViewTreeViewModelStoreOwnerReflectively(removeZoneOverlayView!!, removeZoneOverlayOwner)
            
            removeZoneOverlayOwner?.moveToState(Lifecycle.State.CREATED)
            
            removeZoneOverlayView?.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            
            removeZoneOverlayView?.setContent {
                MyApplicationTheme(darkTheme = prefs.isDarkTheme()) {
                    RemoveZoneHighlightUI()
                }
            }
            
            removeZoneOverlayParams = WindowManager.LayoutParams(
                screenWidth,
                removeZoneHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = 0
                y = 0
                alpha = 0f
            }
            
            windowManager.addView(removeZoneOverlayView, removeZoneOverlayParams)
            isRemoveZoneVisible = true
            
            removeZoneOverlayOwner?.moveToState(Lifecycle.State.RESUMED)
            
            val fadeAnimator = ValueAnimator.ofFloat(0f, 0.4f).apply {
                duration = 200
                addUpdateListener { anim ->
                    removeZoneOverlayParams?.alpha = anim.animatedValue as Float
                    try {
                        removeZoneOverlayView?.let { windowManager.updateViewLayout(it, removeZoneOverlayParams) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update remove zone overlay alpha", e)
                    }
                }
            }
            fadeAnimator.start()
            
            Log.d(TAG, "Remove zone highlight shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show remove zone highlight", e)
        }
    }
    
    private fun hideRemoveZoneHighlight() {
        if (!isRemoveZoneVisible) return
        
        try {
            val params = removeZoneOverlayParams
            if (params != null && params.alpha > 0f) {
                val fadeAnimator = ValueAnimator.ofFloat(params.alpha, 0f).apply {
                    duration = 200
                    addUpdateListener { anim ->
                        params.alpha = anim.animatedValue as Float
                        try {
                            removeZoneOverlayView?.let { windowManager.updateViewLayout(it, params) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update remove zone overlay alpha", e)
                        }
                    }
                }
                fadeAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        removeZoneOverlayView?.let { view ->
                            removeZoneOverlayOwner?.moveToState(Lifecycle.State.DESTROYED)
                            clearViewTreeOwnersReflectively(view)
                            try {
                                windowManager.removeViewImmediate(view)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error removing remove zone overlay", e)
                            }
                        }
                        isRemoveZoneVisible = false
                        removeZoneOverlayView = null
                        removeZoneOverlayParams = null
                        removeZoneOverlayOwner = null
                        Log.d(TAG, "Remove zone highlight hidden")
                    }
                })
                fadeAnimator.start()
            } else {
                removeZoneOverlayView?.let { view ->
                    removeZoneOverlayOwner?.moveToState(Lifecycle.State.DESTROYED)
                    clearViewTreeOwnersReflectively(view)
                    try {
                        windowManager.removeViewImmediate(view)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error removing remove zone overlay", e)
                    }
                }
                isRemoveZoneVisible = false
                removeZoneOverlayView = null
                removeZoneOverlayParams = null
                removeZoneOverlayOwner = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error hiding remove zone highlight", e)
            isRemoveZoneVisible = false
        }
    }
    
    private fun setOverlayContent() {
        composeView?.setContent {
            MyApplicationTheme(darkTheme = prefs.isDarkTheme()) {
                FloatingOverlayUI(
                    onScreenshotToggle = { handleScreenshotToggle() },
                    onWarningToggle = { handleWarningToggle() },
                    onOpenApp = { handleOpenApp() },
                    isScreenshotting = prefs.isScreenshotting(),
                    isWarningEnabled = prefs.isWarningSystemEnabled(),
                    onDragStart = {
                        totalDragDistance = 0f
                        isDragging = true
                    },
                    onDragDelta = { dx, dy ->
                        val params = layoutParams
                        if (params != null) {
                            totalDragDistance += kotlin.math.sqrt(dx * dx + dy * dy)
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            val newX = (params.x + dx).toInt().coerceIn(0, screenWidth - 80)
                            val newY = (params.y + dy).toInt().coerceIn(0, screenHeight - 200)
                            
                            params.x = newX
                            params.y = newY
                            
                            try {
                                windowManager.updateViewLayout(composeView!!, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update overlay position", e)
                            }
                            
                            if (totalDragDistance > dragThreshold && !isRemoveZoneVisible) {
                                showRemoveZoneHighlight()
                            }
                            
                            if (newY >= screenHeight - removeZoneHeight) {
                                Log.d(TAG, "Overlay dragged to remove zone, dismissing")
                                dismiss()
                            }
                        }
                    },
                    onDragEnd = {
                        val params = layoutParams
                        if (params != null && totalDragDistance > dragThreshold) {
                            val currentY = params.y
                            if (currentY < screenHeight - removeZoneHeight) {
                                snapToEdge(params.x, params.y)
                            }
                        }
                        totalDragDistance = 0f
                        isDragging = false
                        hideRemoveZoneHighlight()
                    }
                )
            }
        }
    }
    
    private fun dismiss() {
        prefs.setFloatingOverlayVisible(false)
        hide()
        onDismissListener?.invoke()
        Log.d(TAG, "Overlay dismissed and listener notified")
    }
    
    private fun handleScreenshotToggle() {
        val currentlyScreenshotting = prefs.isScreenshotting()
        if (currentlyScreenshotting) {
            val serviceIntent = Intent(context, MainForegroundService::class.java).apply {
                action = ServiceActions.ACTION_STOP_SERVICE
            }
            context.stopService(serviceIntent)
            prefs.setScreenshotting(false)
            Log.d(TAG, "Screenshot service stopped via overlay")
            setOverlayContent()
        } else {
            val intent = Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            context.startActivity(intent)
            Log.d(TAG, "Requesting screenshot permission via overlay")
        }
    }
    
    private fun handleWarningToggle() {
        val currentlyEnabled = prefs.isWarningSystemEnabled()
        val newState = !currentlyEnabled
        
        prefs.setWarningSystemEnabled(newState)
        
        if (newState) {
            WarningCheckWorker.schedulePeriodicCheck(context)
            Log.d(TAG, "Warning system enabled via overlay")
        } else {
            WarningCheckWorker.cancelPeriodicCheck(context)
            Log.d(TAG, "Warning system disabled via overlay")
        }
        
        setOverlayContent()
    }
    
    private fun handleOpenApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
    
    private fun setViewTreeLifecycleOwnerReflectively(view: View, owner: LifecycleOwner?) {
        try {
            val cls = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val method = cls.getMethod("set", View::class.java, LifecycleOwner::class.java)
            method.invoke(null, view, owner)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set ViewTreeLifecycleOwner reflectively", t)
        }
    }
    
    private fun setViewTreeSavedStateRegistryOwnerReflectively(view: View, owner: SavedStateRegistryOwner?) {
        try {
            val cls = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            val method = cls.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
            method.invoke(null, view, owner)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set ViewTreeSavedStateRegistryOwner reflectively", t)
        }
    }
    
    private fun setViewTreeViewModelStoreOwnerReflectively(view: View, owner: ViewModelStoreOwner?) {
        try {
            val cls = Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
            val method = cls.getMethod("set", View::class.java, ViewModelStoreOwner::class.java)
            method.invoke(null, view, owner)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set ViewTreeViewModelStoreOwner reflectively", t)
        }
    }
    
    private fun clearViewTreeOwnersReflectively(view: View) {
        setViewTreeLifecycleOwnerReflectively(view, null)
        setViewTreeSavedStateRegistryOwnerReflectively(view, null)
        setViewTreeViewModelStoreOwnerReflectively(view, null)
    }
    
    private inner class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
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
                    val out = Bundle()
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
    
    fun destroy() {
        hide()
    }
}

private fun androidx.compose.ui.unit.Dp.toPx(context: Context): Float {
    return this.value * context.resources.displayMetrics.density
}

@Composable
private fun FloatingOverlayUI(
    onScreenshotToggle: () -> Unit,
    onWarningToggle: () -> Unit,
    onOpenApp: () -> Unit,
    isScreenshotting: Boolean,
    isWarningEnabled: Boolean,
    onDragStart: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    
    Surface(
        modifier = Modifier
            .width(64.dp)
            .wrapContentHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        onDragDelta(dragAmount.x, dragAmount.y)
                        change.consume()
                    },
                    onDragEnd = {
                        onDragEnd()
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .width(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(horizontal = 24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(3.dp)
                    )
            )
            
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingButton(
                    icon = Icons.Default.CameraAlt,
                    onClick = onScreenshotToggle,
                    isActive = isScreenshotting,
                    activeColor = MaterialTheme.colorScheme.tertiary,
                    label = "Screenshot"
                )
                
                FloatingButton(
                    icon = Icons.Default.Notifications,
                    onClick = onWarningToggle,
                    isActive = isWarningEnabled,
                    activeColor = MaterialTheme.colorScheme.primary,
                    label = "Warning"
                )
                
                FloatingButton(
                    icon = Icons.Default.Home,
                    onClick = onOpenApp,
                    isActive = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    label = "App"
                )
            }
        }
    }
}

@Composable
private fun RemoveZoneHighlightUI() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Drag here to remove",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Drag here to remove",
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun FloatingButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isActive: Boolean,
    activeColor: Color,
    label: String
) {
    val buttonColor = if (isActive) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .background(
                color = buttonColor.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .border(1.dp, buttonColor.copy(alpha = 0.5f), CircleShape)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = buttonColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

