# Deltarune Companion - Android AI Screenshot Companion

An Android app that creates an AI companion (Ralsei from Deltarune) that observes your device activity through automated screenshots and provides contextual dialogue responses via system overlays.

## Features

- **Automated Screenshot Monitoring**: Captures screenshots at configurable intervals using MediaProjection API
- **AI-Powered Analysis**: Sends screenshots to OpenAI Vision API for intelligent content analysis
- **Memory System**: Stores user activity memories with timestamps for contextual awareness
- **Interactive Chat**: Direct chat interface with the AI companion
- **System Overlay Dialogue**: Character dialogue bubbles that appear over other apps
- **Configurable Settings**: Customizable screenshot intervals, image quality, batch processing, and AI prompts

## Architecture

### Core Components

- **MainForegroundService**: Long-running background service managing screenshot automation
- **ScreenshotController**: Handles MediaProjection API for persistent virtual display and screenshot capture
- **OpenAIAnalyzer**: Batch processes screenshots via OpenAI Vision API with Ralsei persona prompts
- **ChatManager**: Manages conversational AI with memory integration and dialogue generation
- **MemoryManager**: Persistent storage for user activity memories
- **OverlayDialogueController**: System overlay for displaying character dialogue bubbles

### Data Flow

1. Background service captures screenshots at configured intervals
2. Screenshots are batched and sent to OpenAI Vision API with character persona prompts
3. AI responses are parsed for memory storage and dialogue suggestions
4. DialogueQueue manages display of character interactions via system overlays
5. Users can directly chat with the companion through the chat interface

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
- **Interval**: Time between screenshots (configurable)
- **Image Scale**: Reduce image size for faster processing (0.1-1.0)
- **Image Quality**: JPEG compression quality (0-100%)
- **Save Screenshots**: Optional local storage of captured images

### AI Analysis Settings
- **Batch Size**: Number of images per OpenAI request (1-10)
- **Custom Prompts**: Override default Ralsei persona prompts
- **Memory Storage**: Automatic saving of significant user activities

### UI Settings
- **Dark/Light Theme**: Toggle app appearance
- **Auto-advance Dialogues**: Automatic progression of character dialogues
- **Developer Debug**: Show internal AI processing messages

## Usage

### Initial Setup
1. Launch the app and grant required permissions
2. Configure your OpenAI API key in Advanced settings
3. Adjust screenshot interval and image quality as needed
4. Start the screenshot service

### Interacting with Ralsei
- **Automatic Dialogue**: Ralsei will observe your activity and occasionally comment
- **Direct Chat**: Use the Chat tab to have conversations
- **Memory Review**: Check the Memory Log to see what Ralsei remembers

### Activity Exclusions
The app automatically excludes certain activities from screenshot monitoring:
- MainActivity (settings screen)
- ChatActivity (chat interface)
- Other internal app screens

## Development

### Project Structure

```
app/src/main/java/com/example/myapplication/
├── MyApplication.kt              # Application class with activity lifecycle tracking
├── MainActivity.kt               # Main settings and control interface
├── ChatActivity.kt               # Direct chat interface with AI
├── MemoryLogActivity.kt          # View stored memories
├── AdvancedActivity.kt           # Advanced configuration settings
├── MainForegroundService.kt      # Core background service
├── ScreenshotController.kt       # MediaProjection and screenshot logic
├── OpenAIAnalyzer.kt            # Batch processing for OpenAI Vision API
├── ChatManager.kt               # Conversational AI management
├── MemoryManager.kt             # Persistent memory storage
├── OverlayDialogueController.kt # System overlay dialogue display
├── PrefsHelper.kt               # Centralized preferences management
├── EnvLoader.kt                 # Environment configuration loader
├── NotificationHelper.kt        # Foreground service notifications
└── ui/                          # Compose UI components
    ├── ScreenshotApp.kt         # Main UI screens
    ├── ChatScreen.kt            # Chat interface
    ├── DialogueUI.kt            # Overlay dialogue components
    └── theme/                   # Material Design theme
```

### Key Development Patterns

**Configuration Management:**
```kotlin
// Immediate UI state + persistent storage pattern
onScaleChange = { newScale ->
    imageScale = newScale
    prefs.setImageScale(newScale)
}
```

**Coroutine Usage:**
```kotlin
// Background services use IO dispatcher with SupervisorJob
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// UI updates switch to Main context
withContext(Dispatchers.Main) { updateUI() }
```

**OpenAI Integration:**
```kotlin
// Fallback resolution: prefs → env file → default
var apiKey = prefs.getOpenAIApiKey()?.takeIf { it.isNotBlank() }
if (apiKey.isNullOrBlank()) {
    apiKey = EnvLoader.getOpenAIApiKey(context)
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

**Service Logs:**
```bash
adb logcat -s MainForegroundService ScreenshotController OpenAIAnalyzer ChatManager
```

**Common Issues:**
- MediaProjection permission denied: Check Settings → Apps → Special access
- OpenAI API errors: Verify API key in `openai.env` file
- Overlay not showing: Ensure System Alert Window permission is granted

## Privacy & Security

- Screenshots are processed locally and sent only to OpenAI for analysis
- No data is stored on external servers beyond OpenAI's processing
- API keys are stored locally and never transmitted except to OpenAI
- Memory data is stored locally in app private storage
- Users can exclude specific activities from monitoring

## License

This project is open source. Please ensure you comply with OpenAI's terms of service when using their API.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly on Android devices
5. Submit a pull request

## Acknowledgments

- Character design and personality based on Ralsei from Deltarune by Toby Fox
- Built with Android Jetpack Compose and Kotlin Coroutines
- AI analysis powered by OpenAI Vision API