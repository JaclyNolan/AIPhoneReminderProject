package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.myapplication.ui.MemoryLogScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.memory.EnhancedMemoryManager
import com.example.myapplication.memory.MemoryEntry
import com.example.myapplication.memory.getMemory
import com.example.myapplication.memory.clearMemory
import com.example.myapplication.memory.deleteMemory

class MemoryLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val prefs = PrefsHelper(ctx)
            MyApplicationTheme(darkTheme = prefs.isDarkTheme()) {
                val scope = rememberCoroutineScope()
                var memories by rememberSaveable { mutableStateOf(listOf<MemoryEntry>()) }
                var sceneTimeline by remember { mutableStateOf(listOf<EnhancedMemoryManager.SceneTimelineEntry>()) }
                var condensedMemories by remember { mutableStateOf(listOf<EnhancedMemoryManager.CondensedMemoryItem>()) }
                var recentIntents by remember { mutableStateOf(listOf<EnhancedMemoryManager.RecentIntent>()) }
                var dialogueSummaries by remember { mutableStateOf(listOf<String>()) }

                // Load all memory types on first composition
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        memories = getMemory(ctx)
                        EnhancedMemoryManager.initialize(ctx)
                        sceneTimeline = EnhancedMemoryManager.getAllSceneTimeline(ctx)
                        condensedMemories = EnhancedMemoryManager.getAllCondensedMemories(ctx)
                        recentIntents = EnhancedMemoryManager.getRecentIntents(ctx, 100)
                        dialogueSummaries = EnhancedMemoryManager.getAllDialogueSummaries(ctx)
                    }
                }

                fun refresh() {
                    scope.launch(Dispatchers.IO) {
                        memories = getMemory(ctx)
                        sceneTimeline = EnhancedMemoryManager.getAllSceneTimeline(ctx)
                        condensedMemories = EnhancedMemoryManager.getAllCondensedMemories(ctx)
                        recentIntents = EnhancedMemoryManager.getRecentIntents(ctx, 100)
                        dialogueSummaries = EnhancedMemoryManager.getAllDialogueSummaries(ctx)
                    }
                }

                MemoryLogScreen(
                    memories = memories,
                    sceneTimeline = sceneTimeline,
                    condensedMemories = condensedMemories,
                    recentIntents = recentIntents,
                    dialogueSummaries = dialogueSummaries,
                    onBack = { finish() },
                    onClear = {
                        scope.launch(Dispatchers.IO) {
                            clearMemory(ctx)
                            refresh()
                        }
                    },
                    onClearEnhanced = {
                        scope.launch(Dispatchers.IO) {
                            EnhancedMemoryManager.clear(ctx)
                            refresh()
                        }
                    },
                    onDelete = { entry ->
                        scope.launch(Dispatchers.IO) {
                            deleteMemory(ctx, entry)
                            refresh()
                        }
                    },
                    onRefresh = { refresh() }
                )
            }
        }
    }
}
