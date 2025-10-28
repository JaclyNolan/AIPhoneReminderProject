# Deltarune Companion - AI Agent Guidelines

Android AI companion app featuring Ralsei (from Deltarune) that monitors screen activity via automated screenshots and provides contextual dialogue through system overlays.

## Architecture: Three-Layer Pipeline

**Screenshot → Analysis → Dialogue**

1. **Screenshot Layer** (`ScreenshotController`): MediaProjection creates **persistent** VirtualDisplay (once), ImageReader reused for all captures, outputs JPEG with configurable scale/quality
2. **Analysis Layer** (`AnalyzerAgent`): Batches exactly 3 screenshots → OpenAI Vision API → structured JSON (batch_summary, frames[], safety_flags, screen_description)
3. **Dialogue Layer** (`ChatManager` + `EnhancedMemoryManager`): DecisionScore formula determines if/how Ralsei responds → `DialogueQueue` displays overlays with emotion-based portraits

## Core Components (Singletons & Services)

- **`MainForegroundService`**: Screenshot loop owner; initializes ChatManager/EnhancedMemoryManager in `onCreate()`; BroadcastReceiver for pause/resume/stop; polls 5s for persistent display readiness before starting
- **`ScreenshotController`**: Creates persistent VirtualDisplay **ONCE** on `startProjection()`; all captures reuse same ImageReader; handles MediaProjection.Callback cleanup
- **`AnalyzerAgent`**: Synchronized queue auto-flushes at 3 items → Vision API → parses JSON → gates by relevance ≥0.4 → safety checks → constructs developer payload for ChatManager
- **`ChatManager`**: DecisionScore = `(UserActivityWeight × 0.5) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2) + RepeatPenalty`; compares vs thresholds (default: stay quiet <0.5, short 0.5-0.7, long ≥0.7); persists history to SharedPreferences; **handles two message types: "developer" (from AnalyzerAgent) and "user" (from ChatScreen)**; user chat pauses screenshots with 60s timeout + 60s cooldown
- **`EnhancedMemoryManager`**: Four-tier memory (SceneTimeline max 100 FIFO, CondensedMemories versioned, RecentIntents 20-item 30min window, DialogueSummaries); must `initialize(ctx)` before use
- **`OverlayDialogueController`**: WindowManager system overlay; portrait via `emotionToRelativePath(emotion)` → `"portrait/ralsei/{emotion}.png"`
- **`MyApplication`**: ActivityLifecycleCallbacks tracks `currentActivityClassName` to exclude internal screens from monitoring (MainActivity, ChatActivity, ScreenshotApp, ChatScreen)

## Critical Patterns

### 1. Preference Synchronization (NEVER update only one)
```kotlin
// ALWAYS update BOTH Compose state AND SharedPreferences
var imageScale by remember { mutableStateOf(prefs.getImageScale()) }
onScaleChange = { newScale ->
    imageScale = newScale           // UI state
    prefs.setImageScale(newScale)   // Persist immediately
}
```
All prefs coerced to valid ranges in `PrefsHelper`: `imageScale` 0.1-1.0, `imageQuality` 0-100, `batchSize` 1-10, `interval` ≥1000ms.

### 2. Persistent VirtualDisplay (Created ONCE)
```kotlin
// ScreenshotController.setupPersistentVirtualDisplay()
private fun setupPersistentVirtualDisplay() {
    if (persistentDisplayCreated) return  // Guard duplicate creation
    // Create ImageReader + VirtualDisplay once, set persistentDisplayCreated = true
}

// takeScreenshot() REUSES existing reader - NO recreation
mainHandler.postDelayed({
    val image = currentImageReader?.acquireLatestImage()  // Reuse
    // Process bitmap → JPEG bytes with scale/quality from prefs
}, 300)  // 300ms delay for display stabilization
```
Service waits: `while (!screenshotController!!.isPersistentDisplayReady() && waited < 5000) { delay(200) }`

### 3. API Configuration & Provider Support
**API Key Resolution Chain:**
```kotlin
// Resolution: SharedPreferences → openai.env asset → hardcoded fallback
var apiKey = prefs.getOpenAIApiKey()?.takeIf { it.isNotBlank() }
if (apiKey.isNullOrBlank()) apiKey = EnvLoader.getOpenAIApiKey(context)
// Pattern used in AnalyzerAgent and ChatManager
```

**Supported Providers:**
- **OpenAI** (default): `https://api.openai.com/v1/chat/completions` - Vision API for screenshots, GPT-4 for chat
- **Mistral AI**: `https://api.mistral.ai/v1/chat/completions` - Alternative provider with similar API structure

**To add new API provider (e.g., Mistral):**
1. Update `PrefsHelper`: Add endpoint/key fields if needed
2. Modify `AnalyzerAgent` + `ChatManager` to use configurable endpoint from prefs
3. Ensure request format matches provider's expectations (message roles, model names)
4. Update error handling for provider-specific status codes

**See `.github/documents/how-to-connect-to-mistral-api.md` for Mistral integration details** (authentication, endpoints, rate limits, best practices).

### 4. Thread Safety & Coroutines
```kotlin
// Services use IO dispatcher with SupervisorJob
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// UI updates switch to Main
withContext(Dispatchers.Main) { updateNotification() }

// Atomic flags prevent race conditions
private val flushing = AtomicBoolean(false)
if (!flushing.compareAndSet(false, true)) return
```

**User Chat Screenshot Pause Pattern:**
```kotlin
// sendUserMessage() in ChatManager pauses screenshots for user chat
ScreenshotPauseController.requestPause(ctx, "ChatManager_UserChat")

// Timeout job (60s) - force resume if LLM doesn't respond
timeoutJob = scope.launch {
    delay(USER_CHAT_TIMEOUT_MS)
    ScreenshotPauseController.requestResume(ctx, "ChatManager_UserChat")
}

// After LLM responds, start cooldown (60s) - resets on new user message
userChatCooldownJob = scope.launch {
    delay(USER_CHAT_COOLDOWN_MS)
    ScreenshotPauseController.requestResume(ctx, "ChatManager_UserChat")
}
```

**Developer/Analyzer messages use separate pause source** (`ChatManager_Developer`) - no timeout/cooldown needed.

### 5. Batch Processing (AnalyzerAgent)
1. `enqueueImageBytes()` adds to synchronized mutableList
2. Auto-flush when size ≥ 3: locks, removes first 3 entries
3. Vision API request with system prompt + 3 base64 images
4. Parse JSON from `output.content[].text` or `output_text`
5. Gate: `relevanceScore = confidence × 0.7 + 0.3` must be ≥0.4 for SceneTimeline
6. Safety: if `suicidal > 0.5` or `selfharm > 0.5`, add high-priority CondensedMemory
7. Developer payload → ChatManager (batch_summary + timeline + intents + memories)

**Changing batch size from 3 requires updating JSON `frames[]` array parsing.**

### 6. DecisionScore System (ChatManager)
**Formula:** `(UserActivityWeight × 0.5) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2) + RepeatPenalty`

**Repeat Penalties:** 2-3× same intent in 30min: `-0.12`; 4+×: `-0.22`

**Thresholds (configurable via prefs):**
- `< shortThreshold` (0.5): Stay quiet (`shouldResponse=false`)
- `0.5 ≤ score < 0.7`: Short response
- `≥ 0.7`: Medium-long response

### 7. Memory Architecture (EnhancedMemoryManager)
Four tiers (all JSON in SharedPreferences):
1. **SceneTimeline**: Max 100 chronological entries, FIFO eviction
2. **CondensedMemories**: Important facts, versioned by `condensedVersion` counter
3. **RecentIntents**: Last 20, filtered to 30min window on read
4. **DialogueSummaries**: Periodic chat compression (strings)

**Must call `EnhancedMemoryManager.initialize(ctx)` in `MainForegroundService.onCreate()`** before screenshot loop.

### 8. Emotion Portrait Mapping
```kotlin
// DialogueTypes.kt - dynamic asset mapping, no code changes needed
fun emotionToRelativePath(emotion: String?): String? {
    val e = emotion?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    return "portrait/ralsei/${e}.png"
}
```
**Add new emotions:** Drop PNG into `app/src/main/assets/portrait/ralsei/{emotion}.png` + update model prompts to output emotion names.

### 9. Compose UI State (ScreenshotApp.kt)
Root orchestrator receives all state from `MainActivity`, passes callbacks to children. Activities bind `remember { mutableStateOf() }` to prefs.

**Permission launchers:**
```kotlin
val screenshotPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result -> /* start service with projection data */ }
```

**Advanced settings reload:** `MainActivity` tracks `AdvancedActivity` lifecycle to reload prefs from `PrefsHelper`.

## Key File Locations

### Service & Control
- `MainForegroundService.kt`: Service lifecycle, BroadcastReceiver (ServiceActions constants), singleton init, 5s display poll
- `ScreenshotController.kt`: MediaProjection, persistent VirtualDisplay (once), ImageReader reuse, cleanup callbacks
- `ScreenshotPauseController.kt`: Pauses screenshots during API calls

### AI Pipeline
- `AnalyzerAgent.kt`: 3-frame queue, Vision API, JSON extraction, relevance gating ≥0.4, safety flags, developer payload
- `ChatManager.kt`: DecisionScore calculation, dynamic prompt (`buildDynamicPrompt()`), history persistence (`chat_prefs`), intent tracking
- `EnhancedMemoryManager.kt`: Four-tier memory, versioning, 30min window filtering, max 100 timeline

### UI & Dialogue
- `ui/ScreenshotApp.kt`: Root orchestrator for all screens
- `ui/ChatScreen.kt`: Chat UI with emotion portraits, developer debug toggle
- `ui/MemoryLog.kt`: SceneTimeline + CondensedMemories display
- `ui/DialogueUI.kt`: Overlay with typewriter effect, auto-advance, touch-to-advance
- `ui/DialogueQueue.kt`: Reactive StateFlow for dialogue entries
- `ui/DialogueTypes.kt`: `DialogueEntry` data class, `emotionToRelativePath()`, `DEFAULT_RALSEI_PATH`
- `ui/Advanced.kt`: Settings (interval, scale, quality, thresholds, theme, auto-advance, custom prompts)

### Config & Utils
- `PrefsHelper.kt`: SharedPreferences with type-safe bounds, coercion, backwards compatibility (`show_analyzer_debug` → `show_developer_debug`)
- `EnvLoader.kt`: Asset `openai.env` parsing (regex for `KEY=value|"value"|'value'`)
- `ResponseLogger.kt`: Logs OpenAI API requests/responses to SharedPreferences, tracks tokens
- `MyApplication.kt`: Activity lifecycle tracking, `isExcludedActivity()` logic

### Documentation
- `.github/documents/how-to-connect-to-mistral-api.md`: Mistral AI integration guide (endpoints, authentication, rate limits, OCR capabilities)
- `.github/documents/analyzer-agent-optimization.md`: AnalyzerAgent JSON schema optimization (token efficiency, context extraction strategy, real-world examples)

### Build
- `gradle/libs.versions.toml`: AGP 8.12.3, Kotlin 2.0.21, Compose BOM 2024.09.00
- `app/build.gradle.kts`: minSdk 24, targetSdk 36, Java 11

## Build & Debug

### Build Commands (PowerShell)
```powershell
./gradlew assembleDebug       # Build APK
./gradlew installDebug        # Install to device
./gradlew clean assembleDebug # Clean build
```

### Logcat Filters
```powershell
# Core pipeline
adb logcat -s "MainForegroundService:D" "ScreenshotController:D" "AnalyzerAgent:D" "ChatManager:D" "*:E"

# Memory system
adb logcat -s "EnhancedMemoryManager:D" "AnalyzerAgent:D" "*:E"

# UI/dialogue
adb logcat -s "DialogueUI:D" "DialogueQueue:D" "OverlayDialogueController:D" "*:E"

# API debugging
adb logcat -s "AnalyzerAgent:D" "ChatManager:D" "ResponseLogger:D" "*:E"
```

### Permission Checks
```powershell
adb shell appops get com.example.myapplication SYSTEM_ALERT_WINDOW
adb shell dumpsys package com.example.myapplication | findstr "SYSTEM_ALERT_WINDOW"
```
**Grant manually (testing):** `adb shell pm grant com.example.myapplication android.permission.SYSTEM_ALERT_WINDOW`

**Permission locations:**
- MediaProjection: Settings → Apps → Special access → Screen capture
- Overlay: Settings → Apps → Special access → Display over other apps

## JSON Schemas

### AnalyzerAgent Vision API Response (Optimized)
**See `.github/documents/analyzer-agent-optimization.md` for comprehensive token-efficiency analysis and context extraction strategy.**

```json
{
  "tick_id": "t_YYYYMMDD_HHMMSS",
  "ts": "ISO8601",
  "batch": {
    "scene": "app_name",
    "activity": "scrolling|typing|reading|idle|switching",
    "confidence": 0.0-1.0,
    "velocity": "slow|medium|fast",
    "mood": "neutral|positive|negative|mixed"
  },
  "context": {
    "summary": "1-2 sentence description of what user is doing",
    "confidence": 0.0-1.0
  }
}
```

**Token Efficiency:** ~50% reduction vs original schema. Consolidates scene/activity detection with natural language summary to avoid redundant fields.

### ChatManager Response
```json
{
  "calculation": {"user_activity_weight":{"score":0.6,"reasons":"..."}, "emotional_resonance":{"score":0.4,"reasons":"..."}, "ralsei_activity_importance":{"score":0.6}, "repeat_penalty":{"score":-0.12,"reasons":"..."}, "final_calculation":"..."},
  "decision_score": 0.58,
  "should_respond": true,
  "reasoning": "...",
  "intent_category": "comfort|observe|question|teach|...",
  "response": [{"text":"...","emotion":"happy|sad|surprised|...","thinking":"..."}],
  "save_to_memory": false,
  "new_memory_entry": null
}
```

## Invariants to Preserve

1. **Dual State Updates**: Always update BOTH `mutableStateOf` AND `prefs.set*()` — never just one
2. **Persistent Display**: VirtualDisplay created ONCE in `setupPersistentVirtualDisplay()` — never recreate per capture
3. **Singleton Init**: ChatManager + EnhancedMemoryManager must `initialize(ctx)` in `MainForegroundService.onCreate()` before loop
4. **Batch Size**: AnalyzerAgent hardcoded to 3 frames; changing requires JSON parsing updates
5. **API Key Chain**: Check prefs → EnvLoader → fallback (pattern in AnalyzerAgent + ChatManager)
6. **Thread Safety**: `AtomicBoolean` for flags, `synchronized()` for collections, `MainHandler.post{}` for UI
7. **Memory Versioning**: `condensedVersion` increments on updates
8. **Emotion Mapping**: Add portraits to `assets/portrait/ralsei/{emotion}.png` + update prompts (no code changes)
9. **Error Handling**: Wrap OpenAI calls in try-catch with logging; failed analyses don't break pipeline
10. **Activity Exclusion**: `MyApplication.isExcludedActivity()` prevents self-observation during app usage