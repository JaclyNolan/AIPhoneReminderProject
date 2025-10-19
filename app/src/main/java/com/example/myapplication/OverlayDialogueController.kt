package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.PrefsHelper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.myapplication.ui.DialogueEntry
import com.example.myapplication.ui.DialogueQueue
import kotlinx.coroutines.*
import org.json.JSONArray

class OverlayDialogueController(private val context: Context) {
    private val TAG = "OverlayDialogueController"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Read app theme preference so overlay uses same dark/light mode
    private val prefs by lazy { PrefsHelper(context) }
    // SharedPreferences object and listener so we can update overlay when theme preference changes
    private val sharedPrefs by lazy { context.getSharedPreferences("screenshot_prefs", Context.MODE_PRIVATE) }
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var attached = false

    // Composite owner used for the ComposeView when running as a window overlay
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Load persisted last response (if any) directly from SharedPreferences and make it the active queue so overlay can recover
        try {
            val prefs: SharedPreferences = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            val s = prefs.getString("last_parsed_response", null)
            if (!s.isNullOrBlank()) {
                val arr = JSONArray(s)
                val persisted = mutableListOf<DialogueEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val speaker = o.optString("speaker", "FRIDAY")
                    val text = o.optString("text", "")
                    val rel = if (o.has("relativePath")) o.optString("relativePath") else null
                    if (text.isNotBlank()) persisted.add(DialogueEntry(speaker = speaker, text = text, relativePath = rel))
                }
                if (persisted.isNotEmpty()) {
                    DialogueQueue.clear()
                    DialogueQueue.enqueue(persisted)
                    Log.d(TAG, "Loaded persisted last parsed response (${persisted.size} entries) into DialogueQueue")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load persisted parsed response on OverlayDialogueController start", e)
        }
        // Observe the DialogueQueue and show/hide overlay when entries appear/disappear
        scope.launch {
            DialogueQueue.state.collect { list ->
                handleQueueChange(list)
            }
        }
    }

    private fun handleQueueChange(list: List<DialogueEntry>) {
        val hasItems = list.isNotEmpty()
        // DEBUG: Always show overlay regardless of activity for debugging
        val forceShowOverlay = true // Set to true to always show overlay for debugging
        Log.d("Overlay", MyApplication.isExcludedActivity().toString());
        if (hasItems) {
            if (!forceShowOverlay && MyApplication.isExcludedActivity()) {
                Log.d(TAG, "Queue has items but current activity is excluded; skipping overlay")
                return
            }
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot draw overlays: SYSTEM_ALERT_WINDOW not granted")
                return
            }
            if (!attached) {
                attachOverlay()
            }
            sendPauseIntent()
        } else {
            if (attached) {
                removeOverlay()
            }
            sendResumeIntent()
        }
    }

    private fun attachOverlay() {
        try {
            sendPauseIntent()
            if (composeView == null) composeView = ComposeView(context)

            // Ensure a lifecycle+saved-state+viewmodel owner is available for Compose (required for lifecycle-aware APIs)
            if (overlayLifecycleOwner == null) overlayLifecycleOwner = OverlayLifecycleOwner()
            // Set all three view-tree owners before the view is attached
            setViewTreeLifecycleOwnerReflectively(composeView as View, overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwnerReflectively(composeView as View, overlayLifecycleOwner)
            setViewTreeViewModelStoreOwnerReflectively(composeView as View, overlayLifecycleOwner)

            // Let the owner perform a restore step prior to adding the view
            overlayLifecycleOwner?.moveToState(Lifecycle.State.CREATED)

            // Use a safe composition strategy for views added to WindowManager
            composeView?.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            // Set initial Compose content
            setOverlayContent()

            // Register SharedPreferences listener to update theme live
            if (prefListener == null) {
                prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "dark_theme") {
                        try {
                            scope.launch(Dispatchers.Main) { setOverlayContent() }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to refresh overlay content on theme change", t)
                        }
                    }
                }
            }
            try { sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
             val layoutParams = WindowManager.LayoutParams(
                 WindowManager.LayoutParams.MATCH_PARENT,
                 WindowManager.LayoutParams.WRAP_CONTENT,
                 /* windowType */
                 run {
                     @Suppress("DEPRECATION")
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                     else WindowManager.LayoutParams.TYPE_PHONE
                 },
                 (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                         or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN),
                 PixelFormat.TRANSLUCENT
             ).apply {
                 gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                 x = 0
                 y = 32
             }
             windowManager?.addView(composeView as View, layoutParams)
             attached = true

             // Move lifecycle to RESUMED so Compose can run lifecycle-aware APIs
             overlayLifecycleOwner?.moveToState(Lifecycle.State.RESUMED)

             Log.d(TAG, "Overlay attached")
             // DEBUG: Remove excluded activity check for always-on overlay
             scope.launch {
                 while (attached) {
                     delay(300)
                     // No excluded activity check for debugging
                 }
             }
         } catch (e: Exception) {
             Log.e(TAG, "Failed to attach overlay", e)
         }
     }

    // Extracted helper to (re)apply the overlay compose content so theme changes recompose it
    private fun setOverlayContent() {
        try {
            composeView?.setContent {
                MyApplicationTheme(darkTheme = prefs.isDarkTheme()) {
                    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .background(color = bgColor, shape = RoundedCornerShape(10.dp))
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            color = bgColor,
                            tonalElevation = 2.dp
                        ) {
                            com.example.myapplication.ui.DialogueUI(
                                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(8.dp),
                                allowTouchAdvance = true,
                                touchSkipsWhenTyping = true,
                                autoAdvance = true,
                                onFinishedAll = {
                                    DialogueQueue.clear()
                                }
                            )
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "setOverlayContent failed", t)
        }
    }

     private fun removeOverlay() {
         try {
             // Move lifecycle to DESTROYED so Compose can clean up (this will also save saved-state and clear ViewModels)
             overlayLifecycleOwner?.moveToState(Lifecycle.State.DESTROYED)

             composeView?.let { v ->
                 // Clear all view-tree owners to avoid leaks
                 clearViewTreeOwnersReflectively(v)
                 // Unregister prefs listener to avoid leaks
                 try { sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
                 windowManager?.removeViewImmediate(v)
             }
         } catch (e: Exception) {
             Log.w(TAG, "Error removing overlay view", e)
         } finally {
             attached = false
             composeView = null
             overlayLifecycleOwner = null
             Log.d(TAG, "Overlay removed")
         }
     }

    private fun sendPauseIntent() {
        try {
            val i = android.content.Intent(ServiceActions.ACTION_PAUSE_SCREENSHOT)
            i.setPackage(context.packageName)
            context.sendBroadcast(i)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send pause intent", e)
        }
    }

    private fun sendResumeIntent() {
        try {
            val i = android.content.Intent(ServiceActions.ACTION_RESUME_SCREENSHOT)
            i.setPackage(context.packageName)
            context.sendBroadcast(i)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send resume intent", e)
        }
    }

    fun destroy() {
        scope.cancel()
        try { removeOverlay() } catch (_: Exception) {}
    }

    // Composite LifecycleOwner + SavedStateRegistryOwner + ViewModelStoreOwner for overlay ComposeView
    private inner class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val vmStore = ViewModelStore()

        override val lifecycle: Lifecycle
            get() = registry

        // SavedStateRegistryOwner requires a property named `savedStateRegistry`
        override val savedStateRegistry = savedStateController.savedStateRegistry

        // ViewModelStoreOwner requires a property named `viewModelStore`
        override val viewModelStore: ViewModelStore
            get() = vmStore

        /**
         * Move the lifecycle and perform saved-state callbacks at appropriate transitions.
         * - When moving to CREATED, attempt to restore saved state.
         * - When moving to DESTROYED, save state and clear the ViewModelStore.
         */
        fun moveToState(state: Lifecycle.State) {
            try {
                if (state == Lifecycle.State.CREATED) {
                    // Best-effort restore (no Bundle available here)
                    savedStateController.performRestore(null)
                }
            } catch (t: Throwable) {
                Log.w(this@OverlayDialogueController.TAG, "SavedState restore failed", t)
            }

            registry.currentState = state

            if (state == Lifecycle.State.DESTROYED) {
                try {
                    val out = Bundle()
                    savedStateController.performSave(out)
                } catch (t: Throwable) {
                    Log.w(this@OverlayDialogueController.TAG, "SavedState save failed", t)
                }
                try {
                    viewModelStore.clear()
                } catch (t: Throwable) {
                    Log.w(this@OverlayDialogueController.TAG, "ViewModelStore clear failed", t)
                }
            }
        }
    }

    // Reflection helpers to set/clear the view-tree owners without requiring the compile-time symbols.
    private fun setViewTreeLifecycleOwnerReflectively(view: View, owner: LifecycleOwner?) {
        try {
            val cls = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val method = cls.getMethod("set", View::class.java, LifecycleOwner::class.java)
            method.invoke(null, view, owner)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set ViewTreeLifecycleOwner reflectively", t)
        }
    }

    private fun clearViewTreeLifecycleOwnerReflectively(view: View) {
        try {
            val cls = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val method = cls.getMethod("set", View::class.java, LifecycleOwner::class.java)
            method.invoke(null, view, null)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to clear ViewTreeLifecycleOwner reflectively", t)
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

    private fun clearViewTreeSavedStateRegistryOwnerReflectively(view: View) {
        try {
            val cls = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            val method = cls.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
            method.invoke(null, view, null)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to clear ViewTreeSavedStateRegistryOwner reflectively", t)
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

    private fun clearViewTreeViewModelStoreOwnerReflectively(view: View) {
        try {
            val cls = Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
            val method = cls.getMethod("set", View::class.java, ViewModelStoreOwner::class.java)
            method.invoke(null, view, null)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to clear ViewTreeViewModelStoreOwner reflectively", t)
        }
    }

    private fun clearViewTreeOwnersReflectively(view: View) {
        clearViewTreeLifecycleOwnerReflectively(view)
        clearViewTreeSavedStateRegistryOwnerReflectively(view)
        clearViewTreeViewModelStoreOwnerReflectively(view)
    }
}