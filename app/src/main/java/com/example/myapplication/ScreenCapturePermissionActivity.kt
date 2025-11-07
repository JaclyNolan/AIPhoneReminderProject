package com.example.myapplication

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.myapplication.core.MainForegroundService
import com.example.myapplication.ui.theme.MyApplicationTheme

/**
 * Lightweight transparent activity that only shows MediaProjection permission dialog
 * Used by FloatingControlOverlay to request screenshot permission without opening the full app
 */
class ScreenCapturePermissionActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ScreenCapturePermission"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme(darkTheme = true) { // Use dark theme for transparency
                // Invisible content - we only need the launcher
                val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                
                val screenCaptureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        result.data?.let {
                            val serviceIntent = Intent(this@ScreenCapturePermissionActivity, MainForegroundService::class.java).apply {
                                putExtra(ServiceActions.EXTRA_RESULT_CODE, result.resultCode)
                                putExtra(ServiceActions.EXTRA_DATA, it)
                            }
                            Log.d(TAG, "Launching MainForegroundService with projection data")
                            ContextCompat.startForegroundService(this@ScreenCapturePermissionActivity, serviceIntent)
                        }
                    } else {
                        Toast.makeText(this@ScreenCapturePermissionActivity, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
                    }
                    // Close this activity immediately after handling result
                    finish()
                }
                
                // Auto-launch permission dialog when activity starts
                LaunchedEffect(Unit) {
                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                }
            }
        }
    }
}

