# Android Screenshot AI Companion - Copilot Instructions

This Android app creates an AI companion (Ralsei from Deltarune) that observes user activity through automated screenshots and provides contextual dialogue responses.

## Architecture Overview

**Core Components:**
- `MainForegroundService`: Long-running service managing screenshot automation and AI analysis
- `ScreenshotController`: Handles MediaProjection API for persistent virtual display and screenshot capture
- `OpenAIAnalyzer`: Batch processes screenshots via OpenAI Vision API, extracts memories and dialogue suggestions
- `ChatManager`: Manages conversational AI chat with memory integration and dialogue generation
- `MemoryManager`: Persistent storage for user activity memories with timestamp tracking
- `OverlayDialogueController`: System overlay for displaying character dialogue bubbles over other apps

**Data Flow:**
1. Service captures screenshots at configured intervals (excluding certain activities)
2. Screenshots batched and sent to OpenAI Vision with Ralsei persona prompt
3. AI responses parsed for memory storage and dialogue generation
4. DialogueQueue manages display of character interactions via overlay system

## Key Development Patterns

**Configuration Management:**
- `PrefsHelper`: Centralized SharedPreferences wrapper with type-safe getters/setters
- `EnvLoader`: Reads `openai.env` from assets for API keys (excluded from version control)
- Settings persist across app sessions with immediate UI state synchronization

**Compose UI State:**
- Activities use `remember`/`mutableStateOf` with immediate prefs persistence on change
- Example: `onScaleChange = { newScale -> imageScale = newScale; prefs.setImageScale(newScale) }`
- All preference changes trigger both UI state updates and persistent storage

**Coroutine Patterns:**
- Services use `CoroutineScope(Dispatchers.IO + SupervisorJob())` for background work
- UI operations switch contexts: `withContext(Dispatchers.Main) { updateUI() }`
- Atomic flags prevent concurrent operations: `AtomicBoolean` for batch processing

**OpenAI Integration:**
- API keys resolved: prefs → `openai.env` asset → fallback
- Custom prompts follow same resolution order
- Batch processing with configurable size (1-10 images per request)
- JSON responses parsed for memory extraction and dialogue generation

## Critical File Locations

**Configuration:**
- `app/src/main/assets/openai.env`: API keys and custom prompts (gitignored)
- `gradle/libs.versions.toml`: Version catalog for all dependencies
- `app/build.gradle.kts`: Compose BOM, Kotlin compiler config

**Core Services:**
- `MainForegroundService.kt`: Service lifecycle, screenshot scheduling, overlay management
- `ScreenshotController.kt`: MediaProjection setup, virtual display management
- `NotificationHelper.kt`: Foreground service notifications, permission handling

**AI Systems:**
- `OpenAIAnalyzer.kt`: Vision API integration, memory extraction, batch processing
- `ChatManager.kt`: Conversational AI with history persistence and dialogue parsing
- `MemoryManager.kt`: SQLite-like storage for user activity memories

## Development Commands

**Build & Run:**
```bash
./gradlew assembleDebug    # Build debug APK
./gradlew installDebug     # Install to connected device
```

**Testing:**
```bash
./gradlew testDebugUnitTest          # Unit tests
./gradlew connectedDebugAndroidTest  # Instrumented tests
```

**Permissions Required:**
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Screenshot capture
- `SYSTEM_ALERT_WINDOW`: Overlay dialogue display
- `POST_NOTIFICATIONS`: Service status notifications

## Debugging Tips

**Service Issues:**
- Check `adb logcat -s MainForegroundService ScreenshotController` for screenshot problems
- Verify MediaProjection permissions via Settings → Apps → Special access

**AI Analysis:**
- Monitor `OpenAIAnalyzer` logs for batch processing status
- Check `openai.env` asset loading in `EnvLoader` logs
- Validate JSON response parsing in `ChatManager` for dialogue extraction

**UI State:**
- Preference changes should immediately update both UI state and persistent storage
- Compose recomposition triggered by `mutableStateOf` changes
- Activity exclusions configured in `MyApplication.isExcludedActivity()`