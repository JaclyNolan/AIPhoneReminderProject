# Deltarune Companion - Android AI Screenshot Companion

An Android app that creates an AI companion (Ralsei from Deltarune) that observes your device activity through automated screenshots and provides contextual dialogue responses via system overlays.

## Features

- **Automated Screenshot Monitoring**: Captures screenshots at configurable intervals using persistent MediaProjection virtual display
- **AI-Powered Analysis**: Batch processes screenshots (3-frame batches) via OpenAI Vision API with structured output
- **Multi-Tier Memory System**: Four-layer memory architecture (SceneTimeline, CondensedMemories, RecentIntents, DialogueSummaries)
- **Interactive Chat**: Direct chat interface with Ralsei using conversational AI with full memory context
- **System Overlay Dialogue**: Character dialogue bubbles with emotion-based portraits over other apps
- **Decision Scoring System**: Intelligent response generation based on user activity weight, emotional resonance, and anti-repetition penalties
- **Configurable Settings**: Customizable screenshot intervals, image quality, batch processing, response thresholds, and AI prompts

## Architecture

### Three-Layer Pipeline

The app uses a sophisticated three-layer architecture for observing and responding to user activity:

1. **Screenshot Layer** (`ScreenshotController`)
   - Uses Android MediaProjection API for screen capture
   - Creates a **persistent VirtualDisplay** (once) that's reused for all captures
   - Configurable image scaling (0.1-1.0) and JPEG quality (0-100%)
   - 300ms delay after capture for display stabilization

2. **Analysis Layer** (`AnalyzerAgent`)
   - Batches exactly 3 screenshots before sending to OpenAI Vision API
   - Structured JSON output: scene_type, dominant_activity, confidence, safety_flags, screen_description
   - **Relevance gating**: Only scenes with confidence ≥0.4 are stored in memory
   - **Safety detection**: Flags concerning content (suicidal/self-harm indicators)

3. **Dialogue Layer** (`ChatManager` + `EnhancedMemoryManager`)
   - **Decision Scoring**: `(UserActivityWeight × 0.5) + (EmotionalResonance × 0.4) − (RalseiActivityImportance × 0.2) + RepeatPenalty`
   - Response thresholds: Stay quiet (<0.5), short response (0.5-0.7), long response (≥0.7)
   - **Anti-repetition**: Penalties applied when similar intents repeat within 30-minute window
   - Emotion-based portrait selection via dynamic asset mapping

### Core Components

- **MainForegroundService**: Long-running service managing screenshot automation loop, initializes ChatManager and EnhancedMemoryManager
- **ScreenshotController**: Handles persistent MediaProjection virtual display (created once, reused for all captures)
- **AnalyzerAgent**: Batch processor with 3-frame queue, sends to Vision API, parses structured JSON responses
- **ChatManager**: Conversational AI with decision scoring, dynamic prompt construction, chat history persistence
- **EnhancedMemoryManager**: Four-tier memory system with versioned storage
  - **SceneTimeline**: Last 100 chronological scene observations
  - **CondensedMemories**: Important facts/emotions (versioned)
  - **RecentIntents**: Last 20 assistant intents (30-minute rolling window)
  - **DialogueSummaries**: Periodic chat history compression
- **OverlayDialogueController**: System overlay window manager for dialogue bubbles with typewriter effect

### Data Flow

1. Service captures screenshots at configured intervals (reusing persistent virtual display)
2. Screenshots enqueued individually; auto-flush when 3 frames accumulated
3. Batch sent to OpenAI Vision API with Ralsei analyzer system prompt
4. Structured JSON response parsed: frames analyzed, safety flags checked, relevance gated
5. Passing scenes added to SceneTimeline; developer payload constructed
6. ChatManager receives payload, calculates DecisionScore with memory context
7. If score exceeds threshold, generates response with emotion classification
8. DialogueQueue enqueues entries; OverlayDialogueController displays with portraits
9. User can directly chat through ChatActivity with full memory integration

## Prerequisites

- Android 7.0 (API level 24) or higher
- OpenAI API key for screenshot analysis functionality

## Setup

### 1. Clone the Repository

```bash
git clone https://github.com/JaclyNolan/DeltaruneCompanionProject.git
cd DeltaruneCompanionProject
```

### 2. Configure OpenAI API Key

Create `app/src/main/assets/openai.env` file (DO NOT commit this file):

```env
# Required: Your OpenAI API key
OPENAI_API_KEY=sk-your-actual-api-key-here

# Optional: Custom prompts for AI analysis
OPENAI_PROMPT="Your custom prompt for image analysis"
```

See `app/src/main/assets/openai.env.example` for reference.

### 3. Build and Install

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug
```

## Required Permissions

The app requires several sensitive permissions for full functionality:

- **Media Projection**: For capturing screenshots
- **System Alert Window**: For displaying overlay dialogue bubbles
- **Foreground Service**: For continuous background operation
- **Notifications**: For service status notifications

## Configuration

### Screenshot Settings
- **Interval**: Time between screenshots (minimum 1000ms, default 10000ms)
- **Image Scale**: Reduce image size for faster processing and lower API costs (0.1-1.0, default 0.4)
- **Image Quality**: JPEG compression quality (0-100%, default 70%)
- **Save Screenshots**: Toggle local storage of captured images (default: true)

### AI Analysis Settings
- **Batch Size**: Number of images per OpenAI Vision request (1-10, fixed at 3 for current analyzer prompt)
- **API Key**: OpenAI API key (stored in SharedPreferences or `openai.env` asset file)
- **Custom Analyzer Prompt**: Override default Vision API system prompt (optional)
- **Custom Chat Prompt**: Override default Ralsei conversational AI prompt (optional)

### Response Behavior Settings
- **Short Response Threshold**: Minimum DecisionScore for short responses (0.0-1.0, default 0.5)
- **Long Response Threshold**: Minimum DecisionScore for detailed responses (0.0-1.0, default 0.7)
- **Anti-Repetition**: Automatic penalties when similar intents repeat within 30 minutes
  - 2-3 repeats: -0.12 penalty
  - 4+ repeats: -0.22 penalty

### UI Settings
- **Dark/Light Theme**: Toggle app appearance (default: dark)
- **Auto-Advance Dialogues**: Automatic progression after typewriter effect completes (default: true)
- **Developer Debug**: Show internal AI processing messages in chat UI (default: false)

## Usage

### Initial Setup
1. Launch the app and grant required permissions
2. Configure your OpenAI API key in Advanced settings
3. Adjust screenshot interval and image quality as needed
4. Start the screenshot service

### Interacting with Ralsei
- **Automatic Dialogue**: Ralsei observes your activity and responds based on DecisionScore thresholds
  - High emotional resonance or significant activity changes trigger responses
  - Anti-repetition system prevents spammy interactions
  - Safety detection for concerning content
- **Direct Chat**: Use the Chat tab to have conversations with full memory context
  - Ralsei can reference recent screen activity from SceneTimeline
  - Access to condensed memories and recent intents
  - Chat history persisted across app sessions
- **Memory Review**: Check the Memory Log to see what Ralsei remembers
  - SceneTimeline: Chronological activity observations
  - CondensedMemories: Important facts and emotional moments
- **Response Logs**: View all OpenAI API requests/responses with token usage tracking

### Activity Exclusions
The app automatically excludes internal screens from screenshot monitoring via `MyApplication.kt` activity lifecycle tracking:
- MainActivity (settings screen)
- ChatActivity (chat interface)
- AdvancedActivity (advanced settings)
- MemoryLogActivity (memory viewer)
- ResponseLogActivity (API log viewer)

This prevents recursive self-observation and maintains privacy during app configuration.

## Development

## Development

### Project Structure

```
app/src/main/java/com/example/myapplication/
├── MyApplication.kt              # Application class with activity lifecycle tracking
├── MainActivity.kt               # Main settings and control interface (Compose UI)
├── ChatActivity.kt               # Direct chat interface with AI
├── MemoryLogActivity.kt          # View stored memories (SceneTimeline + CondensedMemories)
├── AdvancedActivity.kt           # Advanced configuration settings
├── MainForegroundService.kt      # Core background service with screenshot loop
├── ScreenshotController.kt       # MediaProjection + persistent VirtualDisplay
├── ScreenshotPauseController.kt  # Pause management during API calls
├── AnalyzerAgent.kt              # Batch processing for OpenAI Vision API
├── ChatManager.kt                # Conversational AI with DecisionScore system
├── EnhancedMemoryManager.kt      # Four-tier memory architecture
├── OverlayDialogueController.kt  # System overlay dialogue display
├── PrefsHelper.kt                # Centralized SharedPreferences management
├── EnvLoader.kt                  # Environment configuration from assets
├── ResponseLogger.kt             # API request/response logging with token tracking
├── NotificationHelper.kt         # Foreground service notifications
├── ServiceActions.kt             # Broadcast action constants
└── ui/                           # Compose UI components
    ├── ScreenshotApp.kt          # Main UI orchestrator
    ├── ChatScreen.kt             # Chat interface with emotion portraits
    ├── MemoryLog.kt              # Memory display UI
    ├── DialogueUI.kt             # Overlay dialogue with typewriter effect
    ├── DialogueQueue.kt          # Reactive dialogue state management (StateFlow)
    ├── DialogueTypes.kt          # DialogueEntry data class + emotionToRelativePath()
    ├── Advanced.kt               # Settings screens
    ├── ResponseLogActivity.kt    # API log viewer
    └── theme/                    # Material Design 3 theme
```

### Key Development Patterns

**Preference Synchronization:**
```kotlin
// CRITICAL: Always update BOTH Compose state AND SharedPreferences
onScaleChange = { newScale ->
    imageScale = newScale           // Update UI state
    prefs.setImageScale(newScale)   // Persist immediately
}
```

**API Key Resolution Chain:**
```kotlin
// Resolution order: SharedPreferences → openai.env asset → hardcoded fallback
var apiKey = prefs.getOpenAIApiKey()?.takeIf { it.isNotBlank() }
if (apiKey.isNullOrBlank()) {
    apiKey = EnvLoader.getOpenAIApiKey(context)
}
```

**Coroutine & Thread Safety:**
```kotlin
// Services use IO dispatcher with SupervisorJob
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// UI updates must switch to Main dispatcher
withContext(Dispatchers.Main) { updateUI() }

// Atomic flags prevent race conditions
private val flushing = AtomicBoolean(false)
if (!flushing.compareAndSet(false, true)) return
```

**Persistent VirtualDisplay Pattern:**
```kotlin
// CRITICAL: VirtualDisplay created ONCE on startProjection()
// Subsequent screenshots reuse the same ImageReader - never recreate
private fun setupPersistentVirtualDisplay() {
    if (persistentDisplayCreated) return  // Guard against duplicate creation
    // Create ImageReader + VirtualDisplay once
}

fun takeScreenshot() {
    // Reuse existing currentImageReader - no display recreation
    val image = currentImageReader?.acquireLatestImage()
}
```

**Memory Initialization:**
```kotlin
// MUST initialize in MainForegroundService.onCreate() before screenshot loop
ChatManager.initialize(applicationContext)
EnhancedMemoryManager.initialize(applicationContext)
```

**Emotion Portrait Mapping:**
```kotlin
// Add new emotions: just add PNG to assets/portrait/ralsei/{emotion}.png
// Function automatically maps emotion string to asset path
fun emotionToRelativePath(emotion: String?): String? {
    val e = emotion?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    return "portrait/ralsei/${e}.png"
}
```

### Testing

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedDebugAndroidTest
```

### Debugging

**Logcat Filtering by Subsystem:**
```bash
# Core pipeline (screenshot → analysis → dialogue)
adb logcat -s "MainForegroundService:D" "ScreenshotController:D" "AnalyzerAgent:D" "ChatManager:D" "*:E"

# Memory system
adb logcat -s "EnhancedMemoryManager:D" "AnalyzerAgent:D" "*:E"

# UI and overlays
adb logcat -s "DialogueUI:D" "DialogueQueue:D" "OverlayDialogueController:D" "*:E"

# API requests with token tracking
adb logcat -s "AnalyzerAgent:D" "ChatManager:D" "ResponseLogger:D" "*:E"
```

**Permission Debugging:**
```bash
# Check system alert window permission status
adb shell appops get com.example.myapplication SYSTEM_ALERT_WINDOW

# Check all app permissions
adb shell dumpsys package com.example.myapplication | findstr "permission"

# Manually grant overlay permission (testing only)
adb shell pm grant com.example.myapplication android.permission.SYSTEM_ALERT_WINDOW
```

**Common Issues:**
- **MediaProjection permission denied**: Check Settings → Apps → Special app access → Screen capture
- **Overlay not showing**: Ensure System Alert Window permission granted (Settings → Apps → Special app access → Display over other apps)
- **OpenAI API errors**: 
  - Verify API key in Advanced settings or `openai.env` file
  - Check ResponseLogger for detailed request/response logs
  - Ensure sufficient API credits/quota
- **Persistent display not ready**: Service waits up to 5 seconds; check `ScreenshotController` logs for initialization errors
- **Batch processing stuck**: Check `AnalyzerAgent` logs for queue size and flushing status; verify AtomicBoolean flags
- **Dialogue not advancing**: Check `auto_advance_dialogues` preference and `DialogueQueue` state in logs

## Privacy & Security

- **Screenshot Processing**: Images processed locally, sent only to OpenAI Vision API (user-configured endpoint)
- **No External Storage**: Memory data stored exclusively in app-private SharedPreferences
- **API Key Security**: Keys stored locally in encrypted SharedPreferences or `openai.env` asset (excluded from version control)
- **Activity Exclusions**: Internal app screens automatically excluded from monitoring via lifecycle tracking
- **Safety Detection**: Built-in safety flag detection for concerning content (suicidal/self-harm indicators)
- **Data Retention**: 
  - SceneTimeline: Maximum 100 entries (FIFO eviction)
  - RecentIntents: 30-minute rolling window, maximum 20 entries
  - Chat history: Persisted locally, no external sync
- **Optional Screenshot Storage**: Toggle saving screenshots to device storage (default: enabled)

## Technical Details

- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **Build Tools**: AGP 8.12.3, Kotlin 2.0.21, Compose BOM 2024.09.00
- **Key Dependencies**: 
  - Jetpack Compose (Material3)
  - Kotlin Coroutines
  - AndroidX Core KTX
- **Required Permissions**:
  - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`
  - `SYSTEM_ALERT_WINDOW` (overlay dialogues)
  - `POST_NOTIFICATIONS` (Android 13+)
  - MediaProjection (runtime permission via user consent dialog)

## Contributing

See `.github/copilot-instructions.md` for comprehensive architecture documentation, development patterns, and AI agent guidance.

## License

[Add your license here]

## Acknowledgments

- Ralsei character from **Deltarune** by Toby Fox
- OpenAI Vision API for screenshot analysis
- Android MediaProjection API for screen capture