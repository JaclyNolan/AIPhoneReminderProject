package com.example.myapplication.testing

import android.content.Context
import android.util.Log
import com.example.myapplication.agents.PersonalityAgent
import com.example.myapplication.context.UsagePatternContextProvider
import com.example.myapplication.testing.ContextProviderFactory
import com.example.myapplication.testing.mockcontext.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified test pipeline for UsagePatternContextProvider and PersonalityAgent
 * 
 * Runs both components in sequence and captures all inputs/outputs
 */
object UnifiedTestPipeline {
    private const val TAG = "UnifiedTestPipeline"
    
    data class TestInputs(
        val appUsage: List<com.example.myapplication.context.UsagePatternDetector.AppUsageData>,
        val userBadBehaviors: List<com.example.myapplication.context.UserBadBehaviorContextProvider.UserBadBehavior>,
        val memoryTimeline: List<com.example.myapplication.memory.EnhancedMemoryManager.SceneTimelineEntry>,
        val phoneState: com.example.myapplication.context.PhoneStateContextProvider.PhoneState,
        val chatHistory: String,
        val characterProfile: com.example.myapplication.CharacterProfiles.CharacterProfile
    )
    
    data class TestOutputs(
        val usagePatternAnalysis: String,  // Output from UsagePatternContextProvider
        val personalityDecision: PersonalityAgent.InterventionDecision  // Output from PersonalityAgent
    )
    
    data class TestResult(
        val inputs: TestInputs,
        val outputs: TestOutputs,
        val providerStates: Map<String, Boolean>  // Which providers are using mock
    )
    
    /**
     * Run unified test pipeline
     * 
     * @param context Application context
     * @param characterId Character ID for PersonalityAgent
     * @return TestResult with all inputs and outputs
     */
    suspend fun runTest(
        context: Context,
        characterId: String = "ralsei"
    ): TestResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== Starting Unified Test Pipeline ===")
            
            // Capture provider states (mock/real)
            val providerStates = ContextProviderFactory.getAllMockStates(context)
            
            // Step 1: Gather all inputs (via factory - respects mock/real toggles)
            Log.d(TAG, "Gathering inputs from context providers...")
            val appUsage = ContextProviderFactory.getAppUsageStats(context, intervalMinutes = 120)
            val userBadBehaviors = ContextProviderFactory.getUserBadBehaviors(context)
            val memoryTimeline = ContextProviderFactory.getSceneTimeline(context, limitMinutes = 60)
            val phoneState = ContextProviderFactory.getPhoneState(context)
            val chatHistory = ContextProviderFactory.getChatHistory(context)
            val characterProfile = ContextProviderFactory.getCharacterProfile(context)
            
            val inputs = TestInputs(
                appUsage = appUsage,
                userBadBehaviors = userBadBehaviors,
                memoryTimeline = memoryTimeline,
                phoneState = phoneState,
                chatHistory = chatHistory,
                characterProfile = characterProfile
            )
            
            Log.d(TAG, "Inputs gathered: ${appUsage.size} apps, ${userBadBehaviors.size} behaviors, ${memoryTimeline.size} timeline entries")
            
            // Step 2: Run UsagePatternContextProvider (force refresh for testing)
            Log.d(TAG, "Running UsagePatternContextProvider...")
            val usagePatternAnalysis = UsagePatternContextProvider.forceRefreshForTesting(context)
            Log.d(TAG, "UsagePatternContextProvider output: ${usagePatternAnalysis.take(100)}...")
            
            // Step 3: Run PersonalityAgent
            Log.d(TAG, "Running PersonalityAgent...")
            val personalityDecision = PersonalityAgent.makeDecision(context, characterId)
            Log.d(TAG, "PersonalityAgent decision: intervene=${personalityDecision.shouldIntervene}, urgency=${personalityDecision.urgency}")
            
            val outputs = TestOutputs(
                usagePatternAnalysis = usagePatternAnalysis,
                personalityDecision = personalityDecision
            )
            
            Log.d(TAG, "=== Test Pipeline Complete ===")
            
            return@withContext TestResult(
                inputs = inputs,
                outputs = outputs,
                providerStates = providerStates
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error running unified test pipeline", e)
            throw e
        }
    }
    
    /**
     * Get comprehensive provider data for display (both mock and real)
     */
    fun getProviderDataForDisplay(context: Context): ProviderDataDisplay {
        // Get mock data if enabled
        val appUsageMock = if (ContextProviderFactory.isAppUsageMock(context)) {
            MockAppUsageContextProvider.getCurrentMockData()
        } else null
        
        val userBadBehaviorMock = if (ContextProviderFactory.isUserBadBehaviorMock(context)) {
            MockUserBadBehaviorContextProvider.getCurrentMockData()
        } else null
        
        val memoryMock = if (ContextProviderFactory.isMemoryMock(context)) {
            MockMemoryContextProvider.getCurrentMockData()
        } else null
        
        val phoneStateMock = if (ContextProviderFactory.isPhoneStateMock(context)) {
            MockPhoneStateContextProvider.getCurrentMockData()
        } else null
        
        val chatHistoryMock = if (ContextProviderFactory.isChatHistoryMock(context)) {
            MockChatHistoryContextProvider.getCurrentMockData()
        } else null
        
        val userPrefsMock = if (ContextProviderFactory.isUserPrefsMock(context)) {
            MockUserPrefsContextProvider.getCurrentMockData()
        } else null
        
        // Get real data (always available for comparison)
        val appUsageReal = try {
            com.example.myapplication.context.AppUsageContextProvider.getUsageStats(context, intervalMinutes = 120)
        } catch (e: Exception) {
            emptyList()
        }
        
        val userBadBehaviorReal = try {
            com.example.myapplication.context.UserBadBehaviorContextProvider.getBadBehaviors(context)
        } catch (e: Exception) {
            emptyList()
        }
        
        val memoryReal = try {
            com.example.myapplication.context.MemoryContextProvider.getSceneTimeline(context, limitMinutes = 60)
        } catch (e: Exception) {
            emptyList()
        }
        
        val phoneStateReal = try {
            com.example.myapplication.context.PhoneStateContextProvider.getPhoneState(context)
        } catch (e: Exception) {
            null
        }
        
        val chatHistoryReal = try {
            com.example.myapplication.agents.ChatManager.getHistorySnapshot()
        } catch (e: Exception) {
            emptyList()
        }
        
        val userPrefsReal = try {
            com.example.myapplication.context.UserPrefsContextProvider.getCharacterProfile(context)
        } catch (e: Exception) {
            null
        }
        
        return ProviderDataDisplay(
            appUsage = AppUsageProviderData(
                mock = appUsageMock,
                real = if (appUsageReal.isNotEmpty()) appUsageReal else null,
                isUsingMock = ContextProviderFactory.isAppUsageMock(context)
            ),
            userBadBehavior = UserBadBehaviorProviderData(
                mock = userBadBehaviorMock,
                real = if (userBadBehaviorReal.isNotEmpty()) userBadBehaviorReal else null,
                isUsingMock = ContextProviderFactory.isUserBadBehaviorMock(context)
            ),
            memory = MemoryProviderData(
                mock = memoryMock,
                real = if (memoryReal.isNotEmpty()) memoryReal else null,
                isUsingMock = ContextProviderFactory.isMemoryMock(context)
            ),
            phoneState = PhoneStateProviderData(
                mock = phoneStateMock,
                real = phoneStateReal,
                isUsingMock = ContextProviderFactory.isPhoneStateMock(context)
            ),
            chatHistory = ChatHistoryProviderData(
                mock = chatHistoryMock,
                real = if (chatHistoryReal.isNotEmpty()) chatHistoryReal else null,
                isUsingMock = ContextProviderFactory.isChatHistoryMock(context)
            ),
            userPrefs = UserPrefsProviderData(
                mock = userPrefsMock,
                real = userPrefsReal,
                isUsingMock = ContextProviderFactory.isUserPrefsMock(context)
            )
        )
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun getMockDataForDisplay(context: Context): MockDataDisplay {
        val data = getProviderDataForDisplay(context)
        return MockDataDisplay(
            appUsage = data.appUsage.mock,
            userBadBehavior = data.userBadBehavior.mock,
            memory = data.memory.mock,
            phoneState = data.phoneState.mock,
            chatHistory = data.chatHistory.mock,
            userPrefs = data.userPrefs.mock
        )
    }
    
    /**
     * Comprehensive provider data with both mock and real
     */
    data class ProviderDataDisplay(
        val appUsage: AppUsageProviderData,
        val userBadBehavior: UserBadBehaviorProviderData,
        val memory: MemoryProviderData,
        val phoneState: PhoneStateProviderData,
        val chatHistory: ChatHistoryProviderData,
        val userPrefs: UserPrefsProviderData
    )
    
    /**
     * Provider-specific data structures
     */
    data class AppUsageProviderData(
        val mock: List<com.example.myapplication.context.UsagePatternDetector.AppUsageData>?,
        val real: List<com.example.myapplication.context.UsagePatternDetector.AppUsageData>?,
        val isUsingMock: Boolean
    )
    
    data class UserBadBehaviorProviderData(
        val mock: List<com.example.myapplication.context.UserBadBehaviorContextProvider.UserBadBehavior>?,
        val real: List<com.example.myapplication.context.UserBadBehaviorContextProvider.UserBadBehavior>?,
        val isUsingMock: Boolean
    )
    
    data class MemoryProviderData(
        val mock: MockMemoryContextProvider.MockMemoryData?,
        val real: List<com.example.myapplication.memory.EnhancedMemoryManager.SceneTimelineEntry>?,
        val isUsingMock: Boolean
    )
    
    data class PhoneStateProviderData(
        val mock: com.example.myapplication.context.PhoneStateContextProvider.PhoneState?,
        val real: com.example.myapplication.context.PhoneStateContextProvider.PhoneState?,
        val isUsingMock: Boolean
    )
    
    data class ChatHistoryProviderData(
        val mock: List<com.example.myapplication.agents.ChatManager.ChatMessage>?,
        val real: List<com.example.myapplication.agents.ChatManager.ChatMessage>?,
        val isUsingMock: Boolean
    )
    
    data class UserPrefsProviderData(
        val mock: MockUserPrefsContextProvider.MockPrefsData?,
        val real: com.example.myapplication.CharacterProfiles.CharacterProfile?,
        val isUsingMock: Boolean
    )
    
    /**
     * Legacy mock-only display (for backward compatibility)
     */
    data class MockDataDisplay(
        val appUsage: List<com.example.myapplication.context.UsagePatternDetector.AppUsageData>?,
        val userBadBehavior: List<com.example.myapplication.context.UserBadBehaviorContextProvider.UserBadBehavior>?,
        val memory: MockMemoryContextProvider.MockMemoryData?,
        val phoneState: com.example.myapplication.context.PhoneStateContextProvider.PhoneState?,
        val chatHistory: List<com.example.myapplication.agents.ChatManager.ChatMessage>?,
        val userPrefs: MockUserPrefsContextProvider.MockPrefsData?
    )
}

