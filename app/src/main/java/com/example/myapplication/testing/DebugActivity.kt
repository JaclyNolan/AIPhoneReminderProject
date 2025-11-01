package com.example.myapplication.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui.theme.MyApplicationTheme

/**
 * Debug Activity for testing the warning system
 * 
 * Provides UI to run test scenarios, toggle mock mode, and inspect state.
 * Launch from Advanced settings or from adb shell.
 */
class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                DebugScreen()
            }
        }
    }
}

