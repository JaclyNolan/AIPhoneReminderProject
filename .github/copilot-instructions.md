# Ralsei AI Screentime Coach - Agent Instructions

Android AI companion monitoring device activity via automated screenshots with character-driven interventions. Built with **modular architecture**: Context Providers → Agents (decision-making) → Tools (interventions).

## ⚠️ Build Verification - CRITICAL

**ALWAYS build the project after making code changes to verify compilation success.**

After editing Kotlin/Java source files, manifest, or Gradle files:
1. Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew assembleDebug`
2. Check for compilation errors in the output
3. Fix any errors before presenting results to the user
4. Only report success after BUILD SUCCESSFUL confirmation

**Do not wait for the user to report compilation errors - catch them yourself!**

## Architecture: Three-Layer System

### Layer 1: Context Providers (Data Gathering)
Stateless objects providing specific data types:
- **`AppUsageContextProvider`**: Android UsageStats API (session durations, foreground time)
- **`MemoryContextProvider`**: Scene timeline, chat history, condensed memories
- **`PhoneStateContextProvider`**: Battery, network, time of day
- **`UserPrefsContextProvider`**: Thresholds, character profile, user settings

### Layer 2: Agents (Decision-Making)
Components using context to make decisions:
- **`ScreenshotAnalyzer`** (formerly AnalyzerAgent): Batches 3 screenshots → Vision API → structured scene JSON
- **`ChatManager`**: DecisionScore formula for response triggers:
  ```
  (ActivityWeight × 0.7) + (EmotionalResonance × 0.6) − (RalseiActivity × 0.2) + RepeatPenalty
  ```
  Thresholds: None (<shortThreshold), Short (short→long), Long (≥longThreshold, default 0.71)
- **`UsagePatternDetector`** (formerly PatternAgent): Rule-based 30+ min session detection → urgency 0-10
- **`PersonalityAgent`**: LLM-generated character responses using `CharacterProfiles` + urgency examples

### Layer 3: Tools (Interventions)
Execute specific actions:
- **`DialogueTool`**: System overlay with emotion-based portraits + typewriter effect
- **`NotificationTool`**: System notifications for alerts
- **`SoftInterventionTool`**: Screen dimming (urgency ≥7) - 🚧 stub exists

### Data Flow

**Phase 1 (Commentary Bot - ✅ Complete)**:
```
Screenshot → ScreenshotAnalyzer → EnhancedMemoryManager → ChatManager (DecisionScore) → DialogueTool
```

**Phase 2 (Warning System - ✅ Core Complete)**:
```
WarningCheckWorker (5min) → UsagePatternDetector → PersonalityAgent → DialogueTool/NotificationTool
```

**Shared State**: `ChatManager.history` with `source`: `"commentary"` | `"warning"` | `"user"` | `"assistant"`

## Critical Development Patterns

### 1. Preference Synchronization ⚠️
```kotlin
// ALWAYS update BOTH Compose state AND SharedPreferences
imageScale = newScale          // Compose UI state
prefs.setImageScale(newScale)  // Persist via PrefsHelper
```
**Why**: Service and UI have separate lifecycles. Missing persistence → silent desyncs between MainActivity and MainForegroundService.

### 2. Persistent VirtualDisplay Pattern ⚠️
```kotlin
// Created ONCE in ScreenshotController.startProjection() - NEVER recreate
if (persistentDisplayCreated) return  // Guard in setupPersistentVirtualDisplay()

fun takeScreenshot() {
    val image = currentImageReader?.acquireLatestImage()  // Reuse existing
}
```
**Why**: Recreation takes ~2s and re-prompts MediaProjection permission dialog. MediaProjection has single-use lifecycle.

### 3. Singleton Initialization ⚠️
```kotlin
// MUST run in MainForegroundService.onCreate() BEFORE screenshot loop starts
ChatManager.initialize(applicationContext)
EnhancedMemoryManager.initialize(applicationContext)
DialogueTool.initialize(applicationContext)
NotificationTool.initialize(applicationContext)
// Context Providers are stateless objects - no initialization needed
```
**Why**: Services outlive Activities. Initialize once at service startup, not per-Activity. UI components accessing these singletons before initialization → crash.

### 4. Thread Safety Patterns
```kotlin
// Services use IO dispatcher + SupervisorJob
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// UI updates require Main dispatcher
withContext(Dispatchers.Main) { updateUI() }

// Atomic flags prevent race conditions in batch processing
private val flushing = AtomicBoolean(false)
if (!flushing.compareAndSet(false, true)) return
```
**Why**: ScreenshotAnalyzer queues 3-frame batches. Multiple screenshots arriving simultaneously need atomic queue access.

### 5. LLM API Integration
```kotlin
// All LLM calls use shared LLMClient (or MockLLMClient in test mode)
val messages = listOf(
    LLMClient.Message("system", systemPrompt),
    LLMClient.Message("user", userPrompt)
)
val response = LLMClient.callOpenAI(context, messages, model = "mistral-medium-latest")
// Response: LLMClient.Response(content, promptTokens, completionTokens, totalTokens)
```
**Why**: Centralized token tracking and error handling. `ResponseLogger` logs all API calls for debugging.

### 6. Testing with Mock LLM
```kotlin
// Use LLMClientFactory for testability
LLMClientFactory.setMockMode(true)  // Enable deterministic responses
val client = LLMClientFactory.getClient()  // Returns MockLLMClient or RealLLMClient
val response = client.callOpenAI(context, messages)

// Test scenarios via TestAgent
TestAgent.runScenario(context, scenario, clearFirst = true)
TestAgent.runAllScenarios(context)  // Run full suite
```
**Why**: Offline testing without API costs. Mock responses based on prompt keywords.

### 7. Activity Exclusions
```kotlin
// MyApplication tracks current activity via lifecycle callbacks
if (MyApplication.isExcludedActivity()) {
    Log.d(TAG, "Skipping screenshot - internal activity detected")
    return
}
```
**Why**: Prevents recursive self-observation. Excludes: `MainActivity`, `ChatActivity`, `ScreenshotApp`, `ChatScreen`.

### 8. Screenshot Pause Management
```kotlin
// Centralized pause controller prevents overlapping pause/resume from multiple sources
ScreenshotPauseController.requestPause(context, "ChatManager")  // 60s timeout
ScreenshotPauseController.requestResume(context, "ChatManager")
// Other sources: "AnalyzerAgent", "OverlayDialogueController"
```
**Why**: Multiple components need to pause screenshots (user chat, LLM processing, overlay display). Tracks pause reasons to prevent premature resume.

## Common Workflows

### Build & Install (Windows PowerShell)
```powershell
# Set JAVA_HOME for JDK 11+
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Build and install
.\gradlew assembleDebug; .\gradlew installDebug

# Clear data before testing
adb shell pm clear com.example.myapplication
```

### Debugging with Logcat
```bash
# Core pipeline (screenshot → analysis → dialogue)
adb logcat -s "MainForegroundService:D" "ScreenshotController:D" "ScreenshotAnalyzer:D" "ChatManager:D" "*:E"

# Warning system (pattern detection → response)
adb logcat -s "UsagePatternDetector:D" "PersonalityAgent:D" "WarningCheckWorker:D" "*:E"

# Memory system
adb logcat -s "EnhancedMemoryManager:D" "*:E"
```

### Permission Debugging
```bash
# Check overlay permission
adb shell appops get com.example.myapplication SYSTEM_ALERT_WINDOW

# Manually grant (testing only)
adb shell pm grant com.example.myapplication android.permission.SYSTEM_ALERT_WINDOW
```

## Key Components

| Component | Purpose | Location | Type |
|-----------|---------|----------|------|
| **Core Services** ||||
| `MainForegroundService` | Orchestrates screenshot loop, initializes singletons | `core/MainForegroundService.kt` | Service |
| `ScreenshotController` | Persistent VirtualDisplay, MediaProjection lifecycle | `core/ScreenshotController.kt` | Controller |
| `WarningCheckWorker` | WorkManager periodic checks (5min intervals) | `core/WarningCheckWorker.kt` | CoroutineWorker |
| **Context Providers** | (Layer 1 - Data Gathering) |||
| `ScreenshotAnalyzer` | Batch 3-frame Vision API processing | `context/ScreenshotAnalyzer.kt` | object |
| `AppUsageContextProvider` | Android UsageStats API queries | `context/AppUsageContextProvider.kt` | object |
| `MemoryContextProvider` | Scene timeline, chat history access | `context/MemoryContextProvider.kt` | object |
| `PhoneStateContextProvider` | Battery, network, time context | `context/PhoneStateContextProvider.kt` | object |
| `UserPrefsContextProvider` | User preferences, character profiles | `context/UserPrefsContextProvider.kt` | object |
| `UsagePatternDetector` | Rule-based violation detection | `context/UsagePatternDetector.kt` | object |
| **Agents** | (Layer 2 - Decision-Making) |||
| `ChatManager` | DecisionScore calculation, conversation history | `agents/ChatManager.kt` | object |
| `PersonalityAgent` | Character-aware LLM responses | `agents/PersonalityAgent.kt` | object |
| **Tools** | (Layer 3 - Interventions) |||
| `DialogueTool` | System overlay dialogue display | `tools/DialogueTool.kt` | object |
| `NotificationTool` | System notifications | `tools/NotificationTool.kt` | object |
| `SoftInterventionTool` | Screen dimming (🚧 stub) | `tools/SoftInterventionTool.kt` | object |
| **Memory & Storage** ||||
| `EnhancedMemoryManager` | Four-tier memory architecture | `memory/EnhancedMemoryManager.kt` | object |
| `PrefsHelper` | Centralized SharedPreferences | `PrefsHelper.kt` | class |
| **UI & Application** ||||
| `MyApplication` | Activity lifecycle tracking for exclusions | `MyApplication.kt` | Application |
| `DialogueQueue` | Reactive dialogue state (StateFlow) | `ui/DialogueQueue.kt` | object |
| `OverlayDialogueController` | System overlay window management | `OverlayDialogueController.kt` | class |
| **Testing Infrastructure** ||||
| `TestAgent` | Test scenario orchestration | `testing/TestAgent.kt` | object |
| `MockLLMClient` | Deterministic test responses | `testing/MockLLMClient.kt` | class |
| `LLMClientFactory` | Mock/Real client factory | `testing/LLMClientFactory.kt` | object |
| **API & Utilities** ||||
| `LLMClient` | Shared Mistral/OpenAI API client | `LLMClient.kt` | object |
| `CharacterProfiles` | Ralsei personality + urgency examples | `CharacterProfiles.kt` | object |
| `ResponseLogger` | API request/response logging | `ResponseLogger.kt` | object |
| `ScreenshotPauseController` | Centralized pause state management | `ScreenshotPauseController.kt` | object |

## Data Structures

### Core Data Types
```kotlin
// ChatMessage with source tracking for dual-pipeline system
data class ChatMessage(
    val role: String,        // "user" | "assistant"
    val text: String,        // Message content (or JSON response)
    val timestamp: String,   // "yyyy-MM-dd HH:mm:ss"
    val source: String,      // "commentary" | "warning" | "user" | "assistant"
    val urgency: Int = 0,    // 0-10 scale (for warnings)
    val characterId: String = "ralsei"
)

// PatternViolation from UsagePatternDetector
data class PatternViolation(
    val appName: String,         // e.g., "YouTube"
    val appDisplayName: String,  // Same as appName (extensible for future)
    val urgency: Int,            // 0-10 scale
    val context: String          // Natural language for LLM
)

// LLM API Message format
data class Message(
    val role: String,    // "system" | "user" | "assistant"
    val content: String  // Prompt text
)
```

### Timestamp Formats
- **SceneTimeline**: ISO 8601 (`yyyy-MM-dd'T'HH:mm:ssXXX`)
- **ChatMessage**: Simple (`yyyy-MM-dd HH:mm:ss`)
- **System time**: `System.currentTimeMillis()` (Long)

### Memory Tiers (EnhancedMemoryManager)
1. **SceneTimeline**: Last 100 chronological observations
2. **CondensedMemories**: Important facts (versioned JSON)
3. **RecentIntents**: Last 20 assistant intents (30min window)
4. **DialogueSummaries**: Periodic chat compressions

## Common Gotchas

1. **Forgetting singleton initialization**: Initialize `ChatManager`, `EnhancedMemoryManager`, `DialogueTool`, and `NotificationTool` in `MainForegroundService.onCreate()`, NOT in Activities
2. **UI updates from IO thread**: Always `withContext(Dispatchers.Main)` for UI changes
3. **Recreating VirtualDisplay**: Reuse `currentImageReader`, never call `setupPersistentVirtualDisplay()` twice
4. **Preference desync**: Update both Compose state AND SharedPreferences via `PrefsHelper`
5. **Race conditions**: Use `AtomicBoolean` for concurrent operations (e.g., `ScreenshotAnalyzer.flushing`)
6. **Hardcoded emotion paths**: Use `emotionToRelativePath(emotion)` for dynamic asset mapping
7. **Missing context providers import**: `ScreenshotAnalyzer` and `UsagePatternDetector` are in `context/` package, NOT `agents/`
8. **PatternAgent renamed**: Now called `UsagePatternDetector` - update imports when refactoring
9. **AnalyzerAgent renamed**: Now called `ScreenshotAnalyzer` - located in `context/` package

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
