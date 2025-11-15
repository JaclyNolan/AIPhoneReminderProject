# Ralsei AI Screentime Coach

An Android AI companion that monitors device activity through automated screenshots and provides contextual interventions through character-driven dialogue. Built with a modular architecture inspired by the Model-Context-Protocol (MCP) pattern: context providers feed data to decision-making agents, which invoke intervention tools.

## Project Status

**Phase 1 (Commentary Bot)**: ✅ Complete and functional  
**Phase 2 (Warning System)**: 🚧 In active development

## Features

### Core Functionality
- **Automated Screenshot Monitoring**: Persistent MediaProjection virtual display captures screenshots at configurable intervals
- **AI-Powered Vision Analysis**: Batch processes screenshots (3-frame batches) via OpenAI Vision API with structured output
- **Multi-Tier Memory System**: Four-layer memory architecture (SceneTimeline, CondensedMemories, RecentIntents, DialogueSummaries)
- **Interactive Chat**: Direct chat interface with Ralsei using conversational AI with full memory context
- **System Overlay Dialogue**: Character dialogue bubbles with emotion-based portraits over other apps
- **Decision Scoring System**: Intelligent response generation based on activity weight, emotional resonance, and anti-repetition penalties
- **Pattern Detection**: Rule-based detection of extended app usage sessions (30+ minutes)
- **Character-Driven Responses**: LLM-generated interventions using CharacterProfiles personality system

### Configuration Options
- Screenshot intervals, image quality, batch processing
- Response thresholds (range: -1.0 to 2.0)
- Custom AI prompts for analyzer and chat
- Mock mode for offline testing with deterministic responses

## Architecture

### MVP Refactored Design

The app follows a **strict context provider → processor → agent → tools** architecture with **two independent pipelines**.

#### Phase 1: Commentary Bot Pipeline (Real-time)

```mermaid
graph TB
    subgraph ContextProviders1["CONTEXT PROVIDERS"]
        CP2[ChatHistoryContextProvider]
        CP4[PhoneStateContextProvider]
        CP5[MemoryContextProvider]
    end
    
    subgraph ContextProcessors1["CONTEXT PROCESSORS"]
        P1[ScreenshotAnalyzer<br/>Vision API]
    end
    
    subgraph Agents1["DECISION LAYER"]
        A1[ChatManager<br/>Commentary Bot]
    end
    
    subgraph Tools1["INTERVENTION"]
        T1[DialogueTool<br/>Overlay]
    end
    
    Screenshots[Screenshots] --> P1
    
    P1 --> A1
    
    CP2 --> A1
    CP4 --> A1
    CP5 --> A1
    
    A1 --> T1
    
    style ContextProviders1 fill:#e1f5ff
    style ContextProcessors1 fill:#d4edda
    style Agents1 fill:#fff4e1
    style Tools1 fill:#ffe1f5
```

#### Phase 2: Warning System Pipeline (Periodic, 5min)

```mermaid
graph TB
    subgraph ContextProviders2["CONTEXT PROVIDERS"]
        CP1[AppUsageContextProvider]
        CP3[UserBadBehaviorContextProvider]
        CP5[MemoryContextProvider]
        CP2[ChatHistoryContextProvider]
        CP4[PhoneStateContextProvider]
    end
    
    subgraph ContextProcessors2["CONTEXT PROCESSORS"]
        P2[UsagePatternAgent<br/>LLM-based Context]
    end
    
    subgraph Agents2["DECISION LAYER"]
        A2[PersonalityAgent<br/>Decision Maker]
    end
    
    subgraph Tools2["INTERVENTION"]
        T1[DialogueTool<br/>Overlay]
        T2[SoftInterventionTool<br/>Dim screen]
    end
    
    Check[WarningCheckWorker<br/>5min periodic] --> P2
    
    CP1 --> P2
    CP3 --> P2
    CP5 --> P2
    
    P2 --> A2
    
    CP2 --> A2
    CP4 --> A2
    CP5 --> A2
    
    A2 --> T1
    A2 --> T2
    
    style ContextProviders2 fill:#e1f5ff
    style ContextProcessors2 fill:#d4edda
    style Agents2 fill:#fff4e1
    style Tools2 fill:#ffe1f5
```

#### Layer 1: Context Providers (Data Sources)

Single-responsibility components that gather and expose raw data:

| Provider | Responsibility | Feeds Into | Returns |
|----------|---|---|---|
| **`AppUsageContextProvider`** | General app usage timeline (broader 1-2 hour view) | UsagePatternAgent | List of app usage entries (app, duration, timestamp) |
| **`ChatHistoryContextProvider`** | Intelligent extraction of conversation history | ChatManager, PersonalityAgent | Optimized token-efficient chat history (20 most recent user/assistant messages + 3 recent analyzer summaries) |
| **`UserBadBehaviorContextProvider`** | User-defined problematic behaviors | UsagePatternAgent | List of bad behaviors (descriptions, app associations, severity) |
| **`PhoneStateContextProvider`** | Device state (battery, network, time) | ChatManager, PersonalityAgent | Battery level, network status, time of day, device state |
| **`MemoryContextProvider`** | Memories & SceneTimeline access | ChatManager, PersonalityAgent | CondensedMemories, RecentIntents, DialogueSummaries, SceneTimeline |

#### Layer 2: Context Processors (Data Analysis)

Transform raw data into structured context:

- **`ScreenshotAnalyzer`** (unchanged)
  - Input: Screenshots only
  - Output: `SceneTimelineEntry` (scene, activity, confidence, summary)
  - Stores: Adds to EnhancedMemoryManager.SceneTimeline
  - **New: Provides "Recent App Timeline"** → Recent 5-10 entries from SceneTimeline with detailed context (higher fidelity than AppUsageContextProvider)

- **`UsagePatternAgent`** (NEW - renamed from UsagePatternDetector, now a context processor)
  - Input: 4 context sources:
    1. **Recent App Timeline** (from ScreenshotAnalyzer sceneTimeline) - detailed, high-fidelity last 5-10 entries
    2. **AppUsageTimeline** (from AppUsageContextProvider) - broader timeline, last 1-2 hours
    3. **UserBadBehaviors** (from UserBadBehaviorContextProvider) - user-defined problematic patterns
    4. **SceneTimeline** (from MemoryContextProvider) - full historical timeline for pattern matching
  - **Purpose**: Purely objective parsing of raw context into natural language scenario
  - **Output**: `UsagePatternAnalysis` (natural language description of current usage pattern and concern indicators)
  - **NOT a decision-maker**: No urgency calculation, no determining whether to intervene
  - **Role**: Like ScreenshotAnalyzer, it only describes what's happening, not what should be done
  - Example output:
    ```
    "Kris has been on YouTube for 47 minutes (since 2:30pm). 
    Recent activity: rapid scrolling through Shorts, 4 app switches total.
    User previously noted: 'YouTube Shorts makes me lose sleep.'
    Similar pattern: 45min YouTube session last Tuesday at 2pm."
    ```

#### Layer 3: Agents (Decision Layer)

LLM-based agents that use processed context to make decisions:

##### **ChatManager (Commentary Bot Pipeline)**
- **Isolated Pipeline**: Screenshot → ScreenshotAnalyzer → ChatManager → DialogueTool
- **Input**: Developer payload from ScreenshotAnalyzer
- **Decision**: DecisionScore formula (unchanged)
- **Output**: Dialogue response to DialogueTool
- **NO connection to other agents**

##### **PersonalityAgent (NEW - now receives context from UsagePatternAgent)**
- **Purpose**: Make decisions and generate character responses based on context provided by processors
- **Input sources**:
  1. From **ScreenshotAnalyzer**: Developer payload (for Commentary Bot pipeline)
  2. From **UsagePatternAgent**: Natural language scenario describing usage patterns and concerns
- **Decision Logic**: 
  - For Phase 1 (Commentary): Uses ScreenshotAnalyzer context + DecisionScore to decide on response
  - For Phase 2 (Warning): Uses UsagePatternAgent context to determine urgency and generate concerned response
- **Output**: Character-driven dialogue with emotion, urgency assessment, intervention decision
- **Responsibility**: 
  - Interprets context (is this a problem?)
  - Decides urgency (0-10)
  - Chooses intervention type (dialogue, screen dimming, both)
  - Generates Ralsei's character response

#### Layer 4: Intervention Tools

Execute actions based on agent decisions:

- **`DialogueTool`**: Display dialogue overlay (receives input from both ChatManager and UsagePatternAgent)
- **`SoftInterventionTool`**: Screen dimming for high urgency (triggered by UsagePatternAgent when urgency ≥ 7)

### Data Flow: Two Independent Pipelines

#### Phase 1 (Commentary Bot - Screenshot Pipeline)
```
Screenshot → ScreenshotAnalyzer (Vision API)
  ↓
  Stores: SceneTimelineEntry to EnhancedMemoryManager
  ↓
  Builds Developer Payload (batch_summary, recent_memories, recent_intents, timeline_buffer)
  ↓
ChatManager (Decision Score calculation)
  ↓
  YES (score above threshold) → DialogueTool → Overlay
  NO (score below threshold) → Silent
```

#### Phase 2 (Warning System - Pattern Detection Pipeline)
```
Periodic Check (5min via WorkManager)
  ↓
UsagePatternAgent (Context Processor - Objective Analysis)
  ← Receives 4 context sources:
    1. Recent App Timeline (from ScreenshotAnalyzer sceneTimeline)
    2. AppUsageTimeline (from AppUsageContextProvider)
    3. UserBadBehaviors (from UserBadBehaviorContextProvider)
    4. SceneTimeline (from MemoryContextProvider for historical patterns)
  ↓
  Outputs: Natural language scenario description (what is happening, what are the concerns)
  ↓
PersonalityAgent (Decision Layer - Makes Judgment)
  ← Receives: 
    1. UsagePatternAgent context output
    2. ChatHistory (from ChatHistoryContextProvider)
    3. PhoneState (from PhoneStateContextProvider)
    4. Memories (from MemoryContextProvider)
  ↓
  Decides: Is this a violation? What's the urgency (0-10)? Should we intervene?
  ↓
  IF intervention needed:
    → PersonalityAgent generates response (emotion, dialogue)
    → DialogueTool displays response
    → SoftInterventionTool dims screen if urgency ≥ 7
```

**Key Difference**: 
- **UsagePatternAgent** (context processor): Objective pattern analysis, NO chat history
- **PersonalityAgent** (decision agent): Receives UsagePatternAgent output + conversational context (ChatHistory, Memories) to make judgment

## Prerequisites

- **Android 7.0 (API 24)** or higher
- **OpenAI API key** for Vision API and LLM responses
- **Java 11+** (Android Studio bundled JDK recommended)

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

### 3. Build and Install (Windows PowerShell)

```powershell
# Set JAVA_HOME for JDK 11+
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Build and install
.\gradlew assembleDebug
.\gradlew installDebug

# Clear app data before testing (optional)
adb shell pm clear com.example.myapplication
```

## Required Permissions

The app requires several sensitive permissions:

- **Media Projection**: For capturing screenshots (runtime consent dialog)
- **System Alert Window**: For displaying overlay dialogue bubbles
- **Foreground Service**: For continuous background operation
- **Notifications**: For service status notifications (Android 13+)

## Configuration

### Screenshot Settings
- **Interval**: Time between screenshots (minimum 1000ms, default 10000ms)
- **Image Scale**: Reduce image size for faster processing (0.1-1.0, default 0.4)
- **Image Quality**: JPEG compression quality (0-100%, default 70%)
- **Save Screenshots**: Toggle local storage of captured images (default: true)

### AI Analysis Settings
- **Batch Size**: Number of images per Vision API request (fixed at 3)
- **API Key**: OpenAI API key (stored in SharedPreferences or `openai.env` asset)
- **Custom Analyzer Prompt**: Override default Vision API system prompt
- **Custom Chat Prompt**: Override default Ralsei conversational AI prompt

### Response Behavior Settings
- **Short Response Threshold**: Minimum DecisionScore for short responses (-1.0 to 2.0, default -1.0)
- **Long Response Threshold**: Minimum DecisionScore for detailed responses (-1.0 to 2.0, default 0.71)
- **Anti-Repetition**: Automatic penalties when similar intents repeat within 30 minutes
  - 2-3 repeats: -0.12 penalty
  - 4+ repeats: -0.22 penalty

### Testing Settings
- **Mock Mode**: Enable deterministic LLM responses for offline testing
- **Developer Debug**: Show internal AI processing messages in chat UI

## Usage

### Initial Setup
1. Launch the app and grant required permissions (MediaProjection, System Alert Window)
2. Configure your OpenAI API key in Advanced settings
3. Adjust screenshot interval and image quality as needed
4. Start the screenshot service from MainActivity

### Interacting with Ralsei

**Automatic Dialogue (Phase 1)**:
- Ralsei observes your activity and responds based on DecisionScore thresholds
- High emotional resonance or significant activity changes trigger responses
- Anti-repetition system prevents spammy interactions
- Safety detection for concerning content

**Pattern Detection (Phase 2)**:
- Periodic checks every 5 minutes via WorkManager
- Detects extended app usage sessions (30+ minutes)
- Urgency scale 0-10 determines intervention type
- High urgency (≥7) triggers soft intervention (screen dimming)

**Direct Chat**:
- Use the Chat tab to have conversations with full memory context
- Ralsei can reference recent screen activity from SceneTimeline
- Access to condensed memories and recent intents
- Chat history persisted across app sessions

**Memory Review**:
- Check the Memory Log to see what Ralsei remembers
- SceneTimeline: Chronological activity observations
- CondensedMemories: Important facts and emotional moments

**Response Logs**:
- View all OpenAI API requests/responses with token usage tracking
- Useful for debugging and monitoring API costs

### Activity Exclusions

The app automatically excludes internal screens from screenshot monitoring via `MyApplication.kt` activity lifecycle tracking:
- MainActivity, ChatActivity, AdvancedActivity, MemoryLogActivity, ResponseLogActivity, DebugActivity

This prevents recursive self-observation and maintains privacy during app configuration.

## Development

### Project Structure

```
app/src/main/java/com/example/myapplication/
├── context/                     # Context providers & processors
│   ├── AppUsageContextProvider.kt    # Raw app usage data
│   ├── ChatHistoryContextProvider.kt # NEW: Chat history extraction
│   ├── UserBadBehaviorContextProvider.kt # NEW: User-defined behaviors
│   ├── PhoneStateContextProvider.kt  # Device state data
│   ├── UserPrefsContextProvider.kt   # User preferences
│   ├── ScreenshotAnalyzer.kt         # Vision API batch processor
│   └── UsagePatternAgent.kt          # NEW: LLM-based context processor
├── agents/                      # Decision-making components
│   ├── ChatManager.kt           # Conversational AI + DecisionScore
│   └── PersonalityAgent.kt      # Character-aware decision maker + LLM responses
├── tools/                       # Intervention tool components
│   ├── DialogueTool.kt          # Overlay dialogue display
│   ├── SoftInterventionTool.kt  # Screen dimming intervention
│   └── NotificationTool.kt      # System notifications
├── memory/                      # Memory management components
│   ├── EnhancedMemoryManager.kt # Four-tier memory architecture
│   └── MemoryManager.kt         # Legacy memory (MemoryEntry)
├── testing/                     # Test infrastructure
│   ├── TestAgent.kt             # Test scenario orchestration
│   ├── MockLLMClient.kt         # Deterministic test responses
│   ├── LLMClientFactory.kt      # Mock/Real client factory
│   └── WarningSystemTestHelper.kt
├── ui/                          # Compose UI components
│   ├── ScreenshotApp.kt         # Main UI orchestrator
│   ├── ChatScreen.kt            # Chat interface
│   ├── MemoryLog.kt             # Memory display
│   ├── DialogueUI.kt            # Overlay dialogue with typewriter
│   ├── DialogueQueue.kt         # Reactive dialogue state (StateFlow)
│   ├── DialogueTypes.kt         # DialogueEntry + emotionToRelativePath()
│   ├── Advanced.kt              # Settings screens
│   └── theme/                   # Material Design 3 theme
├── MyApplication.kt             # Application class + activity tracking
├── MainActivity.kt              # Main settings and control interface
├── ChatActivity.kt              # Direct chat interface
├── MemoryLogActivity.kt         # Memory viewer
├── AdvancedActivity.kt          # Advanced configuration
├── DebugActivity.kt             # Debug tools
├── MainForegroundService.kt     # Core background service
├── ScreenshotController.kt      # MediaProjection + VirtualDisplay
├── OverlayDialogueController.kt # System overlay manager
├── PrefsHelper.kt               # Centralized SharedPreferences
├── LLMClient.kt                 # Shared Mistral/OpenAI API client
├── CharacterProfiles.kt         # Ralsei personality definitions
├── EnvLoader.kt                 # Environment configuration
├── ResponseLogger.kt            # API request/response logging
├── NotificationHelper.kt        # Foreground service notifications
├── ServiceActions.kt            # Broadcast action constants
└── WarningCheckWorker.kt        # WorkManager periodic checks
```

### Critical Development Patterns

#### 1. Preference Synchronization ⚠️
```kotlin
// ALWAYS update BOTH Compose state AND SharedPreferences
imageScale = newScale          // Compose UI state
prefs.setImageScale(newScale)  // Persist to SharedPreferences
```
**Why**: Service and UI have separate lifecycles. Missing persistence → silent desyncs.

#### 2. Persistent VirtualDisplay Pattern ⚠️
```kotlin
// Created ONCE in startProjection() - NEVER recreate
if (persistentDisplayCreated) return  // Guard

fun takeScreenshot() {
    val image = currentImageReader?.acquireLatestImage()  // Reuse existing
}
```
**Why**: Recreation takes ~2s and prompts MediaProjection permission dialog.

#### 3. Singleton Initialization ⚠️
```kotlin
// MUST run in MainForegroundService.onCreate() BEFORE screenshot loop
ChatManager.initialize(applicationContext)
EnhancedMemoryManager.initialize(applicationContext)
```
**Why**: Services outlive Activities. Initialize once at service startup, not per-Activity.

#### 4. Thread Safety Patterns
```kotlin
// Services use IO dispatcher + SupervisorJob
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// UI updates require Main dispatcher
withContext(Dispatchers.Main) { updateUI() }

// Atomic flags prevent race conditions
private val flushing = AtomicBoolean(false)
if (!flushing.compareAndSet(false, true)) return
```

#### 5. LLM API Integration
```kotlin
// All LLM calls use shared LLMClient
val messages = listOf(
    LLMClient.Message("system", systemPrompt),
    LLMClient.Message("user", userPrompt)
)
val response = LLMClient.callOpenAI(context, messages, model = "mistral-medium-latest")
// Response includes: content, promptTokens, completionTokens, totalTokens
```

#### 6. Testing with Mock LLM
```kotlin
// Use LLMClientFactory for testability
LLMClientFactory.setMockMode(true)  // Enable deterministic responses
val client = LLMClientFactory.getClient()  // Returns MockLLMClient or RealLLMClient
val response = client.callOpenAI(context, messages)

// Test scenarios: TestAgent.runScenario(context, scenario, clearFirst = true)
```

**Common Issues**:
- **MediaProjection permission denied**: Check Settings → Apps → Special app access → Screen capture
- **Overlay not showing**: Ensure System Alert Window permission granted
- **OpenAI API errors**: Verify API key in Advanced settings or `openai.env` file
- **Persistent display not ready**: Service waits up to 5 seconds; check `ScreenshotController` logs
- **Batch processing stuck**: Check `AnalyzerAgent` logs for queue size and flushing status
- **LLM response issues**: Enable Developer Debug mode to see internal processing messages

### Build Verification ⚠️

**ALWAYS build the project after making code changes to verify compilation success.**

After editing Kotlin/Java source files, manifest, or Gradle files:
1. Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew assembleDebug`
2. Check for compilation errors in the output
3. Fix any errors before presenting results to the user
4. Only report success after BUILD SUCCESSFUL confirmation

**Do not wait for the user to report compilation errors - catch them yourself!**

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

- **Min SDK**: API 24 (Android 7.0) | **Target SDK**: API 36 (Android 14)
- **Kotlin**: 2.0.21 | **AGP**: 8.12.3 | **Java**: 11
- **Jetpack**: Compose (Material3), Coroutines, WorkManager
- **No external libs**: Uses stdlib `HttpURLConnection` and `org.json`
- **Key Dependencies**: 
  - Jetpack Compose BOM 2024.09.00
  - Kotlin Coroutines
  - AndroidX Core KTX
  - WorkManager (periodic pattern checks)

## Current Development Focus

Phase 2 completion:
- ✅ UsagePatternDetector (30min detection, formerly PatternAgent)
- ✅ ScreenshotAnalyzer (Vision API processing, formerly AnalyzerAgent)
- ✅ PersonalityAgent (CharacterProfiles)
- ✅ WarningCheckWorker (WorkManager)
- ✅ TestAgent + MockLLMClient
- ✅ Modular architecture refactor (context providers, processors, agents, tools)
- 🚧 SoftInterventionOverlay (urgency ≥7 screen dimming UI)
- 🚧 App/screen context awareness (prevent misinterpretation of internal screens)
- 🚧 Pattern analysis payload (session duration, activity streaks, concern flags)

## Contributing

See `.github/copilot-instructions.md` and `.cursor/rules/general-rules.mdc` for comprehensive architecture documentation, development patterns, and AI agent guidance.

## License

[Add your license here]

## Acknowledgments

- Ralsei character from **Deltarune** by Toby Fox
- OpenAI Vision API for screenshot analysis
- Android MediaProjection API for screen capture
- Inspired by Model-Context-Protocol (MCP) architecture pattern
