package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.myapplication.ui.MemoryLogScreen
import com.example.myapplication.ui.theme.MyApplicationTheme

class MemoryLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val prefs = PrefsHelper(ctx)
            MyApplicationTheme(darkTheme = prefs.isDarkTheme()) {
                val scope = rememberCoroutineScope()
                var memories by rememberSaveable { mutableStateOf(listOf<MemoryEntry>()) }

                // Load memories on first composition
                LaunchedEffect(Unit) {
                    memories = withContext(Dispatchers.IO) { getMemory(ctx) }
                }

                fun refresh() {
                    scope.launch(Dispatchers.IO) {
                        val list = getMemory(ctx)
                        memories = list
                    }
                }

                MemoryLogScreen(
                    memories = memories,
                    onBack = { finish() },
                    onClear = {
                        scope.launch(Dispatchers.IO) {
                            clearMemory(ctx)
                            refresh()
                        }
                    },
                    onDelete = { entry ->
                        scope.launch(Dispatchers.IO) {
                            deleteMemory(ctx, entry)
                            refresh()
                        }
                    }
                )
            }
        }
    }
}
