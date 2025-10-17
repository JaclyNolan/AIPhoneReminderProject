# Android Screenshot AI Companion - Copilot Instructions

This Android app creates an AI companion (Ralsei from Deltarune) that observes user activity through automated screenshots and provides contextual dialogue responses via system overlays.

## Architecture Overview

### Three-Layer Pipeline
1. **Screenshot Layer** (`ScreenshotController`): MediaProjection → persistent VirtualDisplay (created once) → ImageReader → JPEG bytes with configurable scale/quality
2. **Analysis Layer** (`AnalyzerAgent`): Batches 3 screenshots → OpenAI Vision API → structured JSON response (batch_summary, frames[], safety_flags, screen_description)
3. **Dialogue Layer** (`ChatManager` + `EnhancedMemoryManager`): Decision scoring system determines if/how Ralsei responds → `DialogueQueue` for overlay display with emotion-based portraits

### Core Singletons & Services
- **`MainForegroundService`**: Owns screenshot loop coroutine, initializes ChatManager/EnhancedMemoryManager on `onCreate()`, handles BroadcastReceiver for pause/resume/stop, waits for persistent display readiness before starting loop
- **`ScreenshotController`**: Creates persistent VirtualDisplay ONCE on `startProjection()`, reuses ImageReader for all captures, handles MediaProjection callback cleanup
- **`AnalyzerAgent`**: Enqueues screenshots individually, auto-flushes when queue reaches 3, sends batch to Vision API, parses JSON response, gates scenes by relevance score, triggers ChatManager developer payload
- **`ChatManager`**: Calculates DecisionScore from (UserActivityWeight × 0.5) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2) + RepeatPenalty; compares against short/long thresholds from prefs; persists chat history to SharedPreferences
- **`EnhancedMemoryManager`**: Four-tier memory: SceneTimeline (max 100 chronological entries), CondensedMemories (versioned facts), RecentIntents (20-item rolling buffer, 30min window), DialogueSummaries (periodic compression)
- **`OverlayDialogueController`**: System overlay via WindowManager, portrait selected by `emotionToRelativePath(emotion)` mapping to "portrait/ralsei/{emotion}.png"

## Critical Development Patterns

### Configuration Management (PrefsHelper + State Binding)
```kotlin
// Compose state bound to SharedPreferences - BOTH updated on change
var imageScale by remember { mutableStateOf(prefs.getImageScale()) }
onScaleChange = { newScale -> 
    imageScale = newScale              // Update UI state
    prefs.setImageScale(newScale)      // Persist immediately
}
```
**All prefs coerced to valid ranges:** `imageScale` 0.1-1.0, `imageQuality` 0-100, `batchSize` 1-10, `interval` ≥1000ms. See `PrefsHelper.kt` for all getters/setters.

### API Key Resolution (EnvLoader + PrefsHelper)
Resolution order: `prefs.getOpenAIApiKey()` → `EnvLoader.getOpenAIApiKey(context)` from `openai.env` asset → hardcoded fallback. Pattern used in both AnalyzerAgent and ChatManager.

### Coroutine Lifecycle & Thread Safety
```kotlin
// Services use IO dispatcher with SupervisorJob
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// UI updates require Main context switch
withContext(Dispatchers.Main) { updateNotification() }

// Atomic flags prevent race conditions in batch processing
private val flushing = AtomicBoolean(false)
if (!flushing.compareAndSet(false, true)) return  // Guard against concurrent flushes
```

### Batch Processing Flow (AnalyzerAgent)
1. **Enqueue**: `enqueueImageBytes(ctx, bytes)` adds to synchronized mutableList
2. **Auto-flush**: When queue size ≥ 3, locks queue, removes first 3 entries
3. **API call**: Constructs Vision API request with system prompt + 3 base64 images
4. **JSON extraction**: Parses `output.content[].text` or `output_text` field → structured JSON
5. **Relevance gate**: `relevanceScore = confidence × 0.7 + 0.3`; must be ≥0.4 to add to SceneTimeline
6. **Safety check**: If `safety_flags.suicidal > 0.5` or `selfharm > 0.5`, adds high-priority CondensedMemory
7. **Developer payload**: Bundles batch_summary + timeline + recent_intents + condensed_memories → ChatManager

**JSON response schema (AnalyzerAgent):**
```json
{
  "tick_id": "t_YYYYMMDD_HHMMSS",
  "frames": [{"id":1,"scene_type":"...","primary_activity":"...","activity_conf":0.0-1.0,"text_snips":[]}],
  "batch_summary": {"scene_type":"...","dominant_activity":"...","repeat_count":1-3,"velocity":"slow/medium/fast","micro_emotion_shift":"neutral/positive/negative/mixed","confidence":0.0-1.0},
  "screen_description": {"summary_text":"...","keywords":[],"topic_inferred":"...","confidence":0.88},
  "safety_flags": {"suicidal":0.0-1.0,"selfharm":0.0-1.0,"nsfw":0.0-1.0},
  "justification": "string (≤18 words)"
}
```

### Decision Scoring System (ChatManager)
**Formula:** `DecisionScore = (UserActivityWeight × 0.5) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2) + RepeatPenalty`

**Repeat Penalties:**
- 2-3× same intent in 30min window: `INTENT_REPEAT_PENALTY_2X = -0.12`
- 4+× same intent: `INTENT_REPEAT_PENALTY_4X = -0.22`

**Response thresholds (configurable via prefs):**
- `< shortThreshold` (default 0.5): Stay quiet (`shouldResponse=false`)
- `shortThreshold ≤ score < longThreshold` (default 0.7): Short response
- `≥ longThreshold`: Medium-long response

**Chat API response schema:**
```json
{
  "calculation": {"user_activity_weight":{"score":0.6,"reasons":"..."}, "emotional_resonance":{"score":0.4,"reasons":"..."}, "ralsei_activity_importance":{"score":0.6}, "repeat_penalty":{"score":-0.12,"reasons":"..."}, "final_calculation":"..."},
  "decision_score": 0.58,
  "should_respond": true,
  "reasoning": "Internal logic string",
  "intent_category": "comfort|observe|question|teach|...",
  "response": [{"text":"...","emotion":"happy|sad|surprised|...","thinking":"Ralsei's emotional reflection"}],
  "save_to_memory": false,
  "new_memory_entry": null
}
```

### Memory Architecture (EnhancedMemoryManager)
**Four tiers (all persisted to SharedPreferences as JSON):**
1. **SceneTimeline**: Immutable chronological entries (`timestamp`, `sceneLabel`, `shortText`, `confidence`); max 100, FIFO eviction
2. **CondensedMemories**: Important facts/emotions (`timestamp`, `content`, `confidence`, `source`); versioned by `condensedVersion` counter
3. **RecentIntents**: Last 20 assistant intents (`timestamp`, `intent`, `phrasingHash`, `length`); filtered to 30min window on read
4. **DialogueSummaries**: Periodic chat history compression (strings)

**Initialize once per service lifetime:** `EnhancedMemoryManager.initialize(ctx)` in `MainForegroundService.onCreate()`

### Persistent VirtualDisplay Pattern (ScreenshotController)
```kotlin
// Created ONCE on startProjection(), reused for all captures
private fun setupPersistentVirtualDisplay() {
    if (persistentDisplayCreated) return  // Guard against duplicate creation
    // Creates ImageReader + VirtualDisplay, stores dimensions
    // Sets persistentDisplayCreated = true
}

// takeScreenshot() reuses existing ImageReader - NO display recreation
fun takeScreenshot(notify: Boolean) {
    mainHandler.postDelayed({
        val image = currentImageReader?.acquireLatestImage()  // Reuse persistent reader
        // Process bitmap, convert to JPEG bytes with scale/quality from prefs
    }, 300)  // 300ms delay for display stabilization
}
```

**Service waits for display readiness:**
```kotlin
// MainForegroundService.onStartCommand after startProjection()
var waited = 0L
while (!screenshotController!!.isPersistentDisplayReady() && waited < 5000) {
    delay(200)
    waited += 200
}
```

### Emotion → Portrait Mapping (DialogueUI)
```kotlin
// emotionToRelativePath in DialogueTypes.kt
fun emotionToRelativePath(emotion: String?): String? {
    val e = emotion?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    return "portrait/ralsei/${e}.png"  // Maps to assets/portrait/ralsei/happy.png, etc.
}
```
**To add new emotions:** 1) Add PNG to `app/src/main/assets/portrait/ralsei/{emotion}.png`, 2) Update model prompts to output new emotion names, 3) No code changes needed (dynamic mapping)

### Compose UI State Pattern
**Root orchestrator:** `ScreenshotApp.kt` receives all state from `MainActivity`, passes callbacks to child composables. Activities use `remember { mutableStateOf() }` bound to prefs.

**Permission launchers:**
```kotlin
val screenshotPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result -> /* start service with projection data */ }
```

**Advanced settings reload pattern:**
```kotlin
// MainActivity tracks AdvancedActivity lifecycle to reload prefs
val advancedLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { /* reload all prefs from PrefsHelper */ }
```

## Critical File Locations

### Service & Core Control
- **`MainForegroundService.kt`**: Service lifecycle, BroadcastReceiver for pause/resume/stop (`ServiceActions` constants), ChatManager + EnhancedMemoryManager init, screenshot loop with 5s persistent display readiness poll
- **`ServiceActions.kt`**: Broadcast action constants (`ACTION_PAUSE_SCREENSHOT`, `ACTION_RESUME_SCREENSHOT`, `ACTION_STOP_SERVICE`, `EXTRA_RESULT_CODE`, `EXTRA_DATA`)
- **`ScreenshotController.kt`**: MediaProjection lifecycle, persistent VirtualDisplay creation (once), ImageReader reuse, MediaProjection.Callback for cleanup
- **`ScreenshotPauseController.kt`**: Manages screenshot pausing during API calls to prevent overlapping captures

### AI Pipeline
- **`AnalyzerAgent.kt`**: Batch processing (3-frame queue), Vision API calls with system prompt, JSON extraction from `output_text` or `output.content[].text`, relevance gating (≥0.4), safety flag detection, developer payload construction
- **`ChatManager.kt`**: Conversational AI, DecisionScore calculation, history persistence to `chat_prefs`, dynamic prompt with response thresholds, `buildDynamicPrompt(shortThreshold, longThreshold)`, intent tracking
- **`EnhancedMemoryManager.kt`**: Multi-tier memory (SceneTimeline, CondensedMemories, RecentIntents, DialogueSummaries), versioned updates (`condensedVersion`), 30min intent window filtering, max 100 timeline entries

### UI & Dialogue
- **`ui/ScreenshotApp.kt`**: Root composable orchestrating all screens (main, advanced, chat, memory log), receives all state from MainActivity, passes callbacks to child composables
- **`ui/ChatScreen.kt`**: Chat UI with message input, Ralsei response rendering with emotion portraits, developer debug message toggle
- **`ui/MemoryLog.kt`**: Display of SceneTimeline and CondensedMemories with timestamps
- **`ui/DialogueUI.kt`**: Overlay dialogue rendering with typewriter effect, auto-advance control, touch-to-advance, portrait display via `emotionToRelativePath()`
- **`ui/DialogueQueue.kt`**: Reactive state management for dialogue entries using `StateFlow`, enqueue/dequeue/clear operations
- **`ui/DialogueTypes.kt`**: `DialogueEntry` data class, `emotionToRelativePath()` function, `DEFAULT_RALSEI_PATH` constant
- **`ui/Advanced.kt`**: Settings UI (interval, scale, quality, batch size, thresholds, dark theme toggle, auto-advance toggle, custom prompts)

### Configuration & Utilities
- **`PrefsHelper.kt`**: All SharedPreferences access with type-safe bounds, coercion logic for valid ranges, backwards compatibility for `show_analyzer_debug` → `show_developer_debug`
- **`EnvLoader.kt`**: Asset-based env var loading (`openai.env` file), regex parsing for `KEY=value` or `KEY="value"` or `KEY='value'` patterns
- **`ResponseLogger.kt`**: Logs all OpenAI API requests/responses to SharedPreferences for debugging, tracks token usage
- **`NotificationHelper.kt`**: Foreground service notification management
- **`MyApplication.kt`**: Application class tracking activity lifecycle to exclude internal app screens from screenshot monitoring

### Build Configuration
- **`gradle/libs.versions.toml`**: Dependency versions (AGP 8.12.3, Kotlin 2.0.21, Compose BOM 2024.09.00)
- **`app/build.gradle.kts`**: Android SDK config (minSdk 24, targetSdk 34), Compose compiler settings

## Build & Debug Commands

### Build & Install
```powershell
# Build debug APK
./gradlew assembleDebug

# Install and run on connected device
./gradlew installDebug

# Clean build (if encountering cache issues)
./gradlew clean assembleDebug
```

### Logcat Filtering
```powershell
# Core pipeline logs
adb logcat -s "MainForegroundService:D" "ScreenshotController:D" "AnalyzerAgent:D" "ChatManager:D" "*:E"

# Memory system logs
adb logcat -s "EnhancedMemoryManager:D" "AnalyzerAgent:D" "*:E"

# UI and dialogue logs
adb logcat -s "DialogueUI:D" "DialogueQueue:D" "OverlayDialogueController:D" "*:E"

# API request debugging
adb logcat -s "AnalyzerAgent:D" "ChatManager:D" "ResponseLogger:D" "*:E"
```

### Permission Debugging
```powershell
# Check MediaProjection permission
adb shell appops get com.example.myapplication SYSTEM_ALERT_WINDOW

# Check system alert window permission  
adb shell dumpsys package com.example.myapplication | findstr "SYSTEM_ALERT_WINDOW"

# Grant permissions manually (for testing)
adb shell pm grant com.example.myapplication android.permission.SYSTEM_ALERT_WINDOW
```

**Common permission locations:**
- MediaProjection: Settings → Apps → Special app access → Screen capture
- System Alert Window: Settings → Apps → Special app access → Display over other apps
- Notifications: Settings → Apps → {App name} → Notifications

## Common Patterns to Preserve

1. **Preference Synchronization**: Always update BOTH `mutableStateOf` state AND `prefs.set*()` in same callback — never update only one
2. **Batch Processing Invariants**: AnalyzerAgent accumulates exactly 3 screenshots before flushing; changing batch size requires updating JSON `frames[]` array parsing
3. **Response Thresholds**: ChatManager dynamic prompt uses prefs thresholds (short: 0.5, long: 0.7) — changes affect DecisionScore interpretation
4. **Memory Versioning**: EnhancedMemoryManager increments `condensedVersion` on updates; dialogue summaries compress periodically (not yet implemented in current code)
5. **Persistent Display Pattern**: ScreenshotController creates VirtualDisplay ONCE in `setupPersistentVirtualDisplay()` — never recreate on each capture
6. **Error Handling**: Wrap OpenAI calls in try-catch with logging; failed analyses don't break pipeline (logged but continue)
7. **Emotion Mapping**: Portrait selection via `emotionToRelativePath()` — add new emotions by adding PNG assets to `assets/portrait/ralsei/{emotion}.png`; no code changes needed
8. **Thread Safety**: Use `AtomicBoolean` for concurrency flags (`flushing`, `running`), `synchronized()` blocks for shared collections, `MainHandler.post{}` for UI thread operations
9. **Service Lifecycle**: ChatManager and EnhancedMemoryManager MUST be initialized in `MainForegroundService.onCreate()` before screenshot loop starts
10. **API Key Fallback Chain**: Always check prefs first, then EnvLoader, then hardcoded default — pattern used consistently in AnalyzerAgent and ChatManager