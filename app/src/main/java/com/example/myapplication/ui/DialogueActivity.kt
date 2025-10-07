package com.example.myapplication.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.PrefsHelper

// Explicit imports for types provided by DialogueTypes.kt
import com.example.myapplication.ui.DialogueLine
import com.example.myapplication.ui.TypewriterDialogue

/**
 * Simple Activity that demonstrates the TypewriterDialogue composable.
 * - Listens for Z / Enter / Space key presses and increments an `advanceSignal` state
 *   that is passed to the composable to request advancing or instant-completing the current line.
 */
class DialogueActivity : ComponentActivity() {
    // A small state object that the activity updates when input is received.
    // The composable reads advanceSignalState.value and will react when it changes.
    private val advanceSignalState = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val ctx = LocalContext.current
            val prefs = PrefsHelper(ctx)
            MyApplicationTheme(darkTheme = prefs.isDarkTheme()) {

            }
        }
    }

    // Capture key presses like Z / Enter / Space / DPAD / Gamepad A
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    advanceSignalState.value = advanceSignalState.value + 1
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
