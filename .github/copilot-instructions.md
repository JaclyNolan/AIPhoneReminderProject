# Ralsei AI Screentime Coach - Agent Instructions

Android AI companion monitoring device activity via automated screenshots with character-driven interventions.

## Architecture Overview

**Dual-Pipeline System**: Commentary Bot (Phase 1) + Warning System (Phase 2)

### Phase 1: Commentary Bot (✅ Complete)
```
Screenshot → Vision Analysis → Memory Storage → DecisionScore → Optional Dialogue
```

**Pipeline**: `ScreenshotController` → `AnalyzerAgent` → `EnhancedMemoryManager` → `ChatManager` → `OverlayDialogueController`

- **ScreenshotController** (`core/`): Persistent `VirtualDisplay` created ONCE at service start, reused for all captures (never recreate!)
- **AnalyzerAgent** (`agents/`): Batches 3 screenshots → OpenAI Vision API → structured JSON with safety flags
- **EnhancedMemoryManager** (`memory/`): Four-tier memory (SceneTimeline, CondensedMemories, RecentIntents, DialogueSummaries)
- **ChatManager** (`agents/`): DecisionScore = `(ActivityWeight × 0.5) + (EmotionalResonance × 0.4) − (RalseiActivity × 0.2) + RepeatPenalty`
  - Response thresholds: Quiet (<0.5), Short (0.5-0.7), Long (≥0.7)
- **OverlayDialogueController**: System overlay with emotion portraits (`assets/portrait/ralsei/{emotion}.png`)

### Phase 2: Warning System (✅ Core Complete)
```
Periodic Check (5min) → Pattern Detection → Character Response → Tool Invocation
```

**Pipeline**: `WarningCheckWorker` → `PatternAgent` → `PersonalityAgent` → `DialogueTool`/`SoftInterventionTool`

- **WarningCheckWorker** (`core/`): WorkManager periodic checks (battery-efficient background processing)
- **PatternAgent** (`agents/`): Rule-based detection using `context/` providers (30+ min sessions → urgency 0-10)
- **PersonalityAgent** (`agents/`): LLM responses using `CharacterProfiles` personality + urgency examples
- **Context Providers** (`context/`): Stateless data accessors (`AppUsageContextProvider`, `MemoryContextProvider`, `PhoneStateContextProvider`, `UserPrefsContextProvider`)
- **Tools** (`tools/`): Action executors initialized in `MainForegroundService.onCreate()` (`DialogueTool`, `NotificationTool`, `SoftInterventionTool`)

**Shared State**: Both systems write to `ChatManager.history` with `source`: `"commentary"` | `"warning"` | `"user"` | `"assistant"`

## Critical Development Patterns

### 1. Build Verification (MANDATORY) ⚠️
```powershell
# ALWAYS compile after code changes - catch errors before user sees them
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
# Only report success after "BUILD SUCCESSFUL" confirmation
```
**Why**: Kotlin compilation errors aren't caught until build time. Verify immediately after editing source files.

### 2. Preference Synchronization ⚠️
```kotlin
// ALWAYS update BOTH Compose state AND SharedPreferences
imageScale = newScale          // Compose UI state
prefs.setImageScale(newScale)  // Persist to SharedPreferences
```
**Why**: Service and UI have separate lifecycles. Missing persistence → silent desyncs between MainActivity and MainForegroundService.

### 3. Persistent VirtualDisplay Pattern ⚠️
```kotlin
// Created ONCE in startProjection() - NEVER recreate
if (persistentDisplayCreated) return  // Guard in setupPersistentVirtualDisplay()

fun takeScreenshot() {
    val image = currentImageReader?.acquireLatestImage()  // Reuse existing ImageReader
}
```
**Why**: Recreation triggers ~2s delay + MediaProjection permission dialog. See `ScreenshotController.kt` for implementation.

### 4. Singleton Initialization ⚠️
```kotlin
// MUST run in MainForegroundService.onCreate() BEFORE screenshot loop
ChatManager.initialize(applicationContext)
EnhancedMemoryManager.initialize(applicationContext)
// Tools layer (DialogueTool, NotificationTool, SoftInterventionTool)
DialogueTool.initialize(applicationContext)
```
**Why**: Services outlive Activities. Initialize once at service startup (`MainForegroundService.onCreate()`), not in Activity lifecycle.

### 5. Thread Safety & Coroutines
```kotlin
// Services use IO dispatcher + SupervisorJob for independent coroutine failures
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// UI updates require Main dispatcher
withContext(Dispatchers.Main) { /* Update Compose state */ }

// Atomic flags prevent race conditions in concurrent operations
private val flushing = AtomicBoolean(false)
if (!flushing.compareAndSet(false, true)) return  // Example: AnalyzerAgent batch flushing
```

### 6. Modular Architecture (Phase 2)
```kotlin
// Context Providers: Stateless data accessors (no initialization needed)
val appUsage = AppUsageContextProvider.getAppUsageStats(context, windowMs)
val memories = MemoryContextProvider.getRecentScenes(context, count)

// Tools: Action executors initialized in MainForegroundService.onCreate()
DialogueTool.showDialogue(context, message, emotion)
SoftInterventionTool.triggerIntervention(context, urgency)
```
**Why**: Agents (`PatternAgent`, `PersonalityAgent`) consume from providers, invoke tools. Stateless providers enable parallel reads without locks.

### 7. LLM API Integration
```kotlin
// All LLM calls use shared LLMClient (Mistral/OpenAI with token tracking)
val messages = listOf(
    LLMClient.Message("system", systemPrompt),
    LLMClient.Message("user", userPrompt)
)
val response = LLMClient.callOpenAI(context, messages, model = "mistral-medium-latest")
// Response: content, promptTokens, completionTokens, totalTokens
```

### 8. Testing with Mock System
```kotlin
// Enable mock mode for offline deterministic testing
PrefsHelper(context).setMockMode(true)  // Persisted in SharedPreferences
// TestAgent uses ILLMClient interface for MockLLMClient/RealLLMClient abstraction
TestAgent.runScenario(context, TestScenario.FIRST_OFFENSE, clearFirst = true)
TestAgent.runAllScenarios(context)  // Full test suite
```
**Location**: `testing/TestAgent.kt`, `testing/MockLLMClient.kt`, `testing/DebugScreen.kt`

## Common Workflows

### Build & Install (Windows PowerShell)
```powershell
# Set JAVA_HOME for JDK 11+
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Build and install
.\gradlew assembleDebug; .\gradlew installDebug

# Clear app data before fresh testing
adb shell pm clear com.example.myapplication

# Clean build (if gradle cache issues)
.\gradlew clean assembleDebug
```

### Debugging with Logcat
```bash
# Core pipeline (screenshot → analysis → dialogue)
adb logcat -s "MainForegroundService:D" "ScreenshotController:D" "AnalyzerAgent:D" "ChatManager:D" "*:E"

# Warning system (pattern detection → response)
adb logcat -s "PatternAgent:D" "PersonalityAgent:D" "WarningCheckWorker:D" "*:E"

# Memory system
adb logcat -s "EnhancedMemoryManager:D" "*:E"

# Full system trace (all components)
adb logcat -s "MainForegroundService:D" "ScreenshotController:D" "AnalyzerAgent:D" "ChatManager:D" "PatternAgent:D" "PersonalityAgent:D" "WarningCheckWorker:D" "EnhancedMemoryManager:D" "*:E"
```

### Permission Debugging
```bash
# Check overlay permission (SYSTEM_ALERT_WINDOW)
adb shell appops get com.example.myapplication SYSTEM_ALERT_WINDOW

# Check all permissions
adb shell dumpsys package com.example.myapplication | findstr "permission"

# Manually grant overlay (testing only)
adb shell pm grant com.example.myapplication android.permission.SYSTEM_ALERT_WINDOW
```

### Testing Workflow
```kotlin
// 1. Enable mock mode in DebugActivity or via PrefsHelper
PrefsHelper(context).setMockMode(true)

// 2. Run test scenarios
TestAgent.runScenario(context, TestScenario.FIRST_OFFENSE, clearFirst = true)
TestAgent.runAllScenarios(context)

// 3. Check results in DebugActivity UI or logs
// Expected: First offense (30min) → urgency 2-3
//          Escalation (45min + warning) → urgency 5-7
//          Critical (60min + multiple) → urgency 8-10
```

## Key Components

| Component | Purpose | Location |
|-----------|---------|----------|
| `MainForegroundService` | Orchestrates screenshot loop, initializes singletons | `core/MainForegroundService.kt` |
| `ScreenshotController` | Persistent VirtualDisplay, MediaProjection | `core/ScreenshotController.kt` |
| `AnalyzerAgent` | Batch 3-frame Vision API processing | `agents/AnalyzerAgent.kt` |
| `ChatManager` | DecisionScore calculation, conversation history | `agents/ChatManager.kt` |
| `EnhancedMemoryManager` | Four-tier memory architecture | `memory/EnhancedMemoryManager.kt` |
| `PatternAgent` | Rule-based violation detection | `agents/PatternAgent.kt` |
| `PersonalityAgent` | Character-aware LLM responses | `agents/PersonalityAgent.kt` |
| `WarningCheckWorker` | WorkManager periodic checks | `core/WarningCheckWorker.kt` |
| `LLMClient` | Shared Mistral/OpenAI API client | `LLMClient.kt` |
| `CharacterProfiles` | Ralsei personality + urgency examples | `CharacterProfiles.kt` |
| `TestAgent` | Test scenario orchestration | `testing/TestAgent.kt` |
| `MockLLMClient` | Deterministic test responses | `testing/MockLLMClient.kt` |
| `PrefsHelper` | Centralized SharedPreferences | `PrefsHelper.kt` |
| `MyApplication` | Activity lifecycle tracking for exclusions | `MyApplication.kt` |
| **Context Providers** | Stateless data accessors (no init) | `context/` |
| `AppUsageContextProvider` | App usage stats via UsageStatsManager | `context/AppUsageContextProvider.kt` |
| `MemoryContextProvider` | Scene timeline & chat history access | `context/MemoryContextProvider.kt` |
| `PhoneStateContextProvider` | Device state (battery, network, time) | `context/PhoneStateContextProvider.kt` |
| `UserPrefsContextProvider` | User-defined bad behaviors | `context/UserPrefsContextProvider.kt` |
| **Tools** | Action executors (init in service) | `tools/` |
| `DialogueTool` | Show character dialogue overlays | `tools/DialogueTool.kt` |
| `NotificationTool` | Send system notifications | `tools/NotificationTool.kt` |
| `SoftInterventionTool` | Screen dimming for high urgency | `tools/SoftInterventionTool.kt` |

## Data Structures

### Timestamp Formats
- **SceneTimeline**: ISO 8601 (`yyyy-MM-dd'T'HH:mm:ssXXX`)
- **ChatMessage**: Simple (`yyyy-MM-dd HH:mm:ss`)

### PatternViolation
```kotlin
data class PatternViolation(
    val appName: String,         // e.g., "YouTube"
    val appDisplayName: String,  // Same as appName (extensible)
    val urgency: Int,            // 0-10 scale
    val context: String          // Natural language for LLM
)
```

## Common Gotchas

1. **Forgetting singleton initialization**: Initialize `ChatManager`, `EnhancedMemoryManager`, and tools in `MainForegroundService.onCreate()`, not Activity
2. **UI updates from IO thread**: Always `withContext(Dispatchers.Main)` for UI changes
3. **Recreating VirtualDisplay**: Reuse `currentImageReader`, never call `setupPersistentVirtualDisplay()` twice
4. **Preference desync**: Update both Compose state AND SharedPreferences (see `PrefsHelper` for all keys)
5. **Race conditions**: Use `AtomicBoolean` for concurrent operations (e.g., `AnalyzerAgent.flushing`)
6. **Hardcoded emotion paths**: Use `emotionToRelativePath(emotion)` for dynamic asset mapping (`portrait/ralsei/{emotion}.png`)
7. **Context provider misuse**: Never initialize context providers - they're stateless objects with static methods
8. **Tool initialization**: Tools (`DialogueTool`, `NotificationTool`, `SoftInterventionTool`) must be initialized in service `onCreate()`, not in agents

## Code Conventions

- **Singletons**: Use `object` for stateless components (`AnalyzerAgent`, `ChatManager`, `EnhancedMemoryManager`)
- **Async**: Prefer `suspend fun` over callbacks
- **Data**: Use `data class` for structured data (`PatternViolation`, `ChatMessage`, `TestResult`)
- **Logging**: `private const val TAG = "ClassName"`, levels: DEBUG (flow) / WARN (recoverable) / ERROR (exceptions)
- **Error handling**: Prefer nullable returns over exceptions for expected failures

## API Key Configuration

Resolution chain (first found wins):
1. SharedPreferences (`PrefsHelper.getOpenAIApiKey()`)
2. Asset file (`app/src/main/assets/openai.env`)
3. Hardcoded fallback (dev only)

**Never commit** `openai.env` (see `openai.env.example`).

## Testing Strategy

```kotlin
// Enable mock mode for offline deterministic testing
LLMClientFactory.setMockMode(true)

// Run test scenarios
TestAgent.runScenario(context, scenario, clearFirst = true)
TestAgent.runAllScenarios(context)  // Full test suite

// Expected outcomes:
// - First offense (30min) → urgency 2-3
// - Escalation (45min + warning) → urgency 5-7
// - Critical (60min + multiple) → urgency 8-10
```

## Activity Exclusions

`MyApplication.kt` lifecycle tracking auto-excludes internal screens:
- `MainActivity`, `ChatActivity`, `AdvancedActivity`, `MemoryLogActivity`, `ResponseLogActivity`, `DebugActivity`

Implementation: `MainForegroundService` checks `MyApplication.isExcludedActivity()` before each screenshot.

## Tech Stack

- **Min SDK**: API 24 (Android 7.0) | **Target SDK**: API 36 (Android 14)
- **Kotlin**: 2.0.21 | **AGP**: 8.12.3 | **Java**: 11
- **Jetpack**: Compose (Material3), Coroutines, WorkManager
- **No external libs**: Uses stdlib `HttpURLConnection` and `org.json`

## Current Development Focus

Phase 2 completion:
- ✅ PatternAgent (30min detection)
- ✅ PersonalityAgent (CharacterProfiles)
- ✅ WarningCheckWorker (WorkManager)
- ✅ TestAgent + MockLLMClient
- 🚧 SoftInterventionOverlay (urgency ≥7 screen dimming UI)
