package com.example.myapplication.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.myapplication.context.UsagePatternDetector
import com.example.myapplication.testing.ContextProviderFactory
import com.example.myapplication.testing.UnifiedTestPipeline
import com.example.myapplication.testing.mockcontext.*
import kotlinx.coroutines.launch
import com.example.myapplication.testing.RealAppUsageReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug screen for testing the warning system
 * 
 * Features:
 * - Run test scenarios with one tap
 * - Toggle mock/real API mode
 * - Inspect current system state
 * - Clear test data
 * - View test results
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Provider mock toggles
    var appUsageMock by remember { mutableStateOf(ContextProviderFactory.isAppUsageMock(context)) }
    var userBadBehaviorMock by remember { mutableStateOf(ContextProviderFactory.isUserBadBehaviorMock(context)) }
    var memoryMock by remember { mutableStateOf(ContextProviderFactory.isMemoryMock(context)) }
    var phoneStateMock by remember { mutableStateOf(ContextProviderFactory.isPhoneStateMock(context)) }
    var chatHistoryMock by remember { mutableStateOf(ContextProviderFactory.isChatHistoryMock(context)) }
    var userPrefsMock by remember { mutableStateOf(ContextProviderFactory.isUserPrefsMock(context)) }
    
    // Test pipeline state
    var testResult by remember { mutableStateOf<UnifiedTestPipeline.TestResult?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var providerDataDisplay by remember { mutableStateOf<UnifiedTestPipeline.ProviderDataDisplay?>(null) }
    
    // Load initial state
    LaunchedEffect(Unit) {
        providerDataDisplay = UnifiedTestPipeline.getProviderDataForDisplay(context)
    }
    
    // Update provider data display when toggles change
    LaunchedEffect(appUsageMock, userBadBehaviorMock, memoryMock, phoneStateMock, chatHistoryMock, userPrefsMock) {
        providerDataDisplay = UnifiedTestPipeline.getProviderDataForDisplay(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🧪 Warning System Debug") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Context Provider Toggles
            item {
                ProviderTogglesSection(
                    appUsageMock = appUsageMock,
                    userBadBehaviorMock = userBadBehaviorMock,
                    memoryMock = memoryMock,
                    phoneStateMock = phoneStateMock,
                    chatHistoryMock = chatHistoryMock,
                    userPrefsMock = userPrefsMock,
                    onAppUsageChange = { 
                        appUsageMock = it
                        ContextProviderFactory.setAppUsageMock(context, it)
                    },
                    onUserBadBehaviorChange = { 
                        userBadBehaviorMock = it
                        ContextProviderFactory.setUserBadBehaviorMock(context, it)
                    },
                    onMemoryChange = { 
                        memoryMock = it
                        ContextProviderFactory.setMemoryMock(context, it)
                    },
                    onPhoneStateChange = { 
                        phoneStateMock = it
                        ContextProviderFactory.setPhoneStateMock(context, it)
                    },
                    onChatHistoryChange = { 
                        chatHistoryMock = it
                        ContextProviderFactory.setChatHistoryMock(context, it)
                    },
                    onUserPrefsChange = { 
                        userPrefsMock = it
                        ContextProviderFactory.setUserPrefsMock(context, it)
                    },
                    onResetAll = {
                        ContextProviderFactory.resetAllToReal(context)
                        appUsageMock = false
                        userBadBehaviorMock = false
                        memoryMock = false
                        phoneStateMock = false
                        chatHistoryMock = false
                        userPrefsMock = false
                    }
                )
            }
            
            // Context Provider Inputs Display (Detailed)
            item {
                ProviderInputsDisplaySection(providerDataDisplay = providerDataDisplay)
            }
            
            // Unified Test Pipeline
            item {
                UnifiedTestPipelineSection(
                    isRunning = isRunning,
                    testResult = testResult,
                    onRunTest = {
                        isRunning = true
                        scope.launch {
                            try {
                                testResult = UnifiedTestPipeline.runTest(context)
                            } catch (e: Exception) {
                                android.util.Log.e("DebugScreen", "Test failed", e)
                            } finally {
                                isRunning = false
                            }
                        }
                    }
                )
            }
            
            // Test Scenario Setup
            item {
                TestScenarioSetupSection(
                    context = context,
                    onScenarioSelected = { scenarioType ->
                        // Setup mock data based on scenario
                        setupScenario(context, scenarioType)
                        appUsageMock = ContextProviderFactory.isAppUsageMock(context)
                        userBadBehaviorMock = ContextProviderFactory.isUserBadBehaviorMock(context)
                        memoryMock = ContextProviderFactory.isMemoryMock(context)
                        phoneStateMock = ContextProviderFactory.isPhoneStateMock(context)
                        chatHistoryMock = ContextProviderFactory.isChatHistoryMock(context)
                        providerDataDisplay = UnifiedTestPipeline.getProviderDataForDisplay(context)
                    }
                )
            }
            
            // Test Results Display
            testResult?.let { result ->
                item {
                    TestResultsDisplaySection(result = result)
                }
            }
            
            // Utilities
            item {
                UtilitiesSection(context = context)
            }
        }
    }
}

@Composable
private fun ManualUsagePatternDetectorTestSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var response by remember { mutableStateOf<UsagePatternDetector.PatternViolation?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var contextInfo by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf<Boolean?>(null) }
    
    // Check permission on first load
    LaunchedEffect(Unit) {
        hasPermission = RealAppUsageReader.hasUsageStatsPermission(context)
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Test UsagePatternDetector Now",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Run the same check that happens automatically every 5 minutes, using REAL device data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Permission warning
            if (hasPermission == false) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "⚠️ Usage Stats Permission Required",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Go to Settings → Special app access → Usage access → Enable for this app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Test Now Button
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    response = null
                    contextInfo = null
                    scope.launch {
                        try {
                            // Build real context from device
                            val patternContext = TestAgent.buildRealContext(context, usageIntervalMinutes = 60)
                            
                            // Build info string
                            contextInfo = buildString {
                                appendLine("📊 Context Loaded:")
                                appendLine("• App Usage: ${patternContext.appUsageStats.size} apps")
                                appendLine("• Chat History: ${patternContext.chatHistory.size} messages")
                                appendLine("• User Behaviors: ${patternContext.userDefinedBehaviors.size} defined")
                                appendLine("• Screen Summaries: ${patternContext.recentSummaries.size} entries")
                                if (patternContext.appUsageStats.isNotEmpty()) {
                                    appendLine("\nTop Apps:")
                                    patternContext.appUsageStats.take(5).forEach { app ->
                                        val minutes = app.totalTimeInForeground / 60000
                                        appendLine("  • ${app.displayName}: ${minutes}min")
                                    }
                                }
                            }
                            
                            // Call UsagePatternDetector
                            val violation = UsagePatternDetector.checkViolations(context, patternContext)
                            response = violation
                            
                            if (violation == null) {
                                errorMessage = "No violation detected"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}\n${e.stackTraceToString().take(500)}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test UsagePatternDetector Now")
            }
            
            // Context Info
            contextInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = info,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // Error Message
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Response Display
            response?.let { violation ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Violation Detected ✓",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("App:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                violation.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Urgency:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${violation.urgency}/10",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    violation.urgency >= 7 -> MaterialTheme.colorScheme.error
                                    violation.urgency >= 4 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Context Narrative:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = violation.context,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ScenarioButton(scenario: TestScenario, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(scenario.name)
                Text(
                    "U:${scenario.expectedUrgency}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                scenario.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TestResultCard(result: TestAgent.TestResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.success)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    result.scenarioName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (result.success) "✅ PASS" else "❌ FAIL",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Urgency:", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Expected: ${result.expectedUrgency}, Actual: ${result.actualUrgency}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Outcome:", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${result.expectedOutcome} → ${result.actualOutcome}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            result.violation?.let { violation ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Context: ${violation.context.take(100)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// New UI Components for Unified Test Pipeline

@Composable
private fun ProviderTogglesSection(
    appUsageMock: Boolean,
    userBadBehaviorMock: Boolean,
    memoryMock: Boolean,
    phoneStateMock: Boolean,
    chatHistoryMock: Boolean,
    userPrefsMock: Boolean,
    onAppUsageChange: (Boolean) -> Unit,
    onUserBadBehaviorChange: (Boolean) -> Unit,
    onMemoryChange: (Boolean) -> Unit,
    onPhoneStateChange: (Boolean) -> Unit,
    onChatHistoryChange: (Boolean) -> Unit,
    onUserPrefsChange: (Boolean) -> Unit,
    onResetAll: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Context Provider Toggles",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Toggle each provider between Real and Mock data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            ProviderToggleRow("App Usage", appUsageMock, onAppUsageChange)
            ProviderToggleRow("User Bad Behavior", userBadBehaviorMock, onUserBadBehaviorChange)
            ProviderToggleRow("Memory", memoryMock, onMemoryChange)
            ProviderToggleRow("Phone State", phoneStateMock, onPhoneStateChange)
            ProviderToggleRow("Chat History", chatHistoryMock, onChatHistoryChange)
            ProviderToggleRow("User Prefs", userPrefsMock, onUserPrefsChange)
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onResetAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All to Real")
            }
        }
    }
}

@Composable
private fun ProviderToggleRow(label: String, isMock: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isMock) "Mock" else "Real",
                style = MaterialTheme.typography.bodySmall,
                color = if (isMock) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
        }
        Switch(checked = isMock, onCheckedChange = onToggle)
    }
}

@Composable
private fun ProviderInputsDisplaySection(providerDataDisplay: UnifiedTestPipeline.ProviderDataDisplay?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Context Provider Inputs (Detailed)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Click to expand and see full data for each provider",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            providerDataDisplay?.let { data ->
                // App Usage Provider
                DetailedProviderSection(
                    title = "App Usage",
                    isUsingMock = data.appUsage.isUsingMock,
                    summary = buildString {
                        val active = if (data.appUsage.isUsingMock) data.appUsage.mock else data.appUsage.real
                        val count = active?.size ?: 0
                        append("$count entries (${if (data.appUsage.isUsingMock) "Mock" else "Real"})")
                    },
                    mockContent = data.appUsage.mock?.let { formatAppUsageData(it) },
                    realContent = data.appUsage.real?.let { formatAppUsageData(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // User Bad Behavior Provider
                DetailedProviderSection(
                    title = "User Bad Behavior",
                    isUsingMock = data.userBadBehavior.isUsingMock,
                    summary = buildString {
                        val active = if (data.userBadBehavior.isUsingMock) data.userBadBehavior.mock else data.userBadBehavior.real
                        val count = active?.size ?: 0
                        append("$count behaviors (${if (data.userBadBehavior.isUsingMock) "Mock" else "Real"})")
                    },
                    mockContent = data.userBadBehavior.mock?.let { formatUserBadBehaviorData(it) },
                    realContent = data.userBadBehavior.real?.let { formatUserBadBehaviorData(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Memory Provider
                DetailedProviderSection(
                    title = "Memory",
                    isUsingMock = data.memory.isUsingMock,
                    summary = buildString {
                        if (data.memory.isUsingMock && data.memory.mock != null) {
                            append("${data.memory.mock.timeline.size} timeline, ${data.memory.mock.memories.size} memories (Mock)")
                        } else if (data.memory.real != null) {
                            append("${data.memory.real.size} timeline entries (Real)")
                        } else {
                            append("No data")
                        }
                    },
                    mockContent = data.memory.mock?.let { formatMemoryData(it) },
                    realContent = data.memory.real?.let { formatRealMemoryData(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Phone State Provider
                DetailedProviderSection(
                    title = "Phone State",
                    isUsingMock = data.phoneState.isUsingMock,
                    summary = buildString {
                        val state = if (data.phoneState.isUsingMock) data.phoneState.mock else data.phoneState.real
                        state?.let {
                            append("Battery: ${it.batteryLevel}%, ${it.networkType}, ${it.timeOfDay} (${if (data.phoneState.isUsingMock) "Mock" else "Real"})")
                        } ?: append("No data")
                    },
                    mockContent = data.phoneState.mock?.let { formatPhoneStateData(it) },
                    realContent = data.phoneState.real?.let { formatPhoneStateData(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Chat History Provider
                DetailedProviderSection(
                    title = "Chat History",
                    isUsingMock = data.chatHistory.isUsingMock,
                    summary = buildString {
                        val active = if (data.chatHistory.isUsingMock) data.chatHistory.mock else data.chatHistory.real
                        val count = active?.size ?: 0
                        append("$count messages (${if (data.chatHistory.isUsingMock) "Mock" else "Real"})")
                    },
                    mockContent = data.chatHistory.mock?.let { formatChatHistoryData(it) },
                    realContent = data.chatHistory.real?.let { formatChatHistoryData(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // User Prefs Provider
                DetailedProviderSection(
                    title = "User Prefs",
                    isUsingMock = data.userPrefs.isUsingMock,
                    summary = buildString {
                        if (data.userPrefs.isUsingMock && data.userPrefs.mock != null) {
                            append("Character: ${data.userPrefs.mock.characterId} (Mock)")
                        } else if (data.userPrefs.real != null) {
                            append("Character: ${data.userPrefs.real.name} (Real)")
                        } else {
                            append("No data")
                        }
                    },
                    mockContent = data.userPrefs.mock?.let { formatUserPrefsData(it) },
                    realContent = data.userPrefs.real?.let { formatRealUserPrefsData(it) }
                )
            } ?: Text("No provider data available", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailedProviderSection(
    title: String,
    isUsingMock: Boolean,
    summary: String,
    mockContent: String?,
    realContent: String?
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (isUsingMock) 
                            MaterialTheme.colorScheme.tertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            if (isUsingMock) "Mock" else "Real",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = if (isUsingMock)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
        
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isUsingMock && mockContent != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Mock Data:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mockContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            
            if (!isUsingMock && realContent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Real Data:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = realContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            
            // Show both if both available for comparison
            if (isUsingMock && realContent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Real Data (for comparison):", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = realContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// Formatting functions for each provider type
private fun formatAppUsageData(data: List<com.example.myapplication.context.UsagePatternDetector.AppUsageData>): String {
    return buildString {
        data.forEachIndexed { index, usage ->
            val minutes = (usage.totalTimeInForeground / 60_000).toInt()
            val hours = minutes / 60
            val mins = minutes % 60
            val duration = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            
            appendLine("${index + 1}. ${usage.displayName}")
            appendLine("   Package: ${usage.packageName}")
            appendLine("   Duration: $duration")
            appendLine("   Last Used: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(usage.lastTimeUsed))}")
            appendLine()
        }
    }
}

private fun formatUserBadBehaviorData(data: List<com.example.myapplication.context.UserBadBehaviorContextProvider.UserBadBehavior>): String {
    return buildString {
        data.forEachIndexed { index, behavior ->
            appendLine("${index + 1}. ${behavior.description}")
            if (behavior.associatedApps.isNotEmpty()) {
                appendLine("   Apps: ${behavior.associatedApps.joinToString(", ")}")
            }
            appendLine("   Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(behavior.createdAt))}")
            appendLine()
        }
    }
}

private fun formatMemoryData(data: MockMemoryContextProvider.MockMemoryData): String {
    return buildString {
        if (data.timeline.isNotEmpty()) {
            appendLine("Timeline Entries (${data.timeline.size}):")
            data.timeline.take(10).forEach { entry ->
                appendLine("  • [${entry.timestamp}] ${entry.sceneLabel}: ${entry.shortText}")
            }
            if (data.timeline.size > 10) {
                appendLine("  ... and ${data.timeline.size - 10} more")
            }
            appendLine()
        }
        if (data.memories.isNotEmpty()) {
            appendLine("Condensed Memories (${data.memories.size}):")
            data.memories.forEach { memory ->
                appendLine("  • [${memory.timestamp}] ${memory.content}")
            }
            appendLine()
        }
        if (data.summaries.isNotEmpty()) {
            appendLine("Dialogue Summaries (${data.summaries.size}):")
            data.summaries.forEach { summary ->
                appendLine("  • $summary")
            }
        }
    }
}

private fun formatRealMemoryData(data: List<com.example.myapplication.memory.EnhancedMemoryManager.SceneTimelineEntry>): String {
    return buildString {
        appendLine("Timeline Entries (${data.size}):")
        data.take(20).forEach { entry ->
            appendLine("  • [${entry.timestamp}] ${entry.sceneLabel}: ${entry.shortText}")
        }
        if (data.size > 20) {
            appendLine("  ... and ${data.size - 20} more")
        }
    }
}

private fun formatPhoneStateData(state: com.example.myapplication.context.PhoneStateContextProvider.PhoneState): String {
    return buildString {
        appendLine("Battery Level: ${state.batteryLevel}%")
        appendLine("Charging: ${if (state.isCharging) "Yes" else "No"}")
        appendLine("Low Battery: ${if (state.isLowBattery) "Yes" else "No"}")
        appendLine("Network: ${state.networkType}")
        appendLine("Do Not Disturb: ${if (state.isDoNotDisturb) "Enabled" else "Disabled"}")
        appendLine("Time of Day: ${state.timeOfDay}")
        appendLine("Current Time: ${state.currentTime}")
        appendLine("Day of Week: ${state.dayOfWeek}")
    }
}

private fun formatChatHistoryData(messages: List<com.example.myapplication.agents.ChatManager.ChatMessage>): String {
    return buildString {
        messages.take(20).forEach { msg ->
            appendLine("[${msg.timestamp}] ${msg.role} (${msg.source}): ${msg.text.take(200)}")
        }
        if (messages.size > 20) {
            appendLine("... and ${messages.size - 20} more messages")
        }
    }
}

private fun formatUserPrefsData(prefs: MockUserPrefsContextProvider.MockPrefsData): String {
    return buildString {
        appendLine("Character ID: ${prefs.characterId}")
        appendLine("Character Name: ${prefs.profile.name}")
        appendLine("Core Traits: ${prefs.profile.coreTraits.take(100)}...")
        appendLine("Speaking Style: ${prefs.profile.speakingStyle.take(100)}...")
    }
}

private fun formatRealUserPrefsData(profile: com.example.myapplication.CharacterProfiles.CharacterProfile): String {
    return buildString {
        appendLine("Character Name: ${profile.name}")
        appendLine("Core Traits: ${profile.coreTraits.take(100)}...")
        appendLine("Speaking Style: ${profile.speakingStyle.take(100)}...")
    }
}

@Composable
private fun UnifiedTestPipelineSection(
    isRunning: Boolean,
    testResult: UnifiedTestPipeline.TestResult?,
    onRunTest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Unified Test Pipeline",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Tests UsagePatternContextProvider → PersonalityAgent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onRunTest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRunning) "Running..." else "Run Test Pipeline")
            }
        }
    }
}

@Composable
private fun TestScenarioSetupSection(
    context: android.content.Context,
    onScenarioSelected: (String) -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Test Scenario Setup",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Quick setup for common test scenarios",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Critical Urgency Scenarios
            ScenarioCategory(
                title = "🔴 Critical Urgency (8-10)",
                scenarios = listOf(
                    ScenarioInfo("critical_binge", "90min Binge", "90-minute continuous session, 3 escalating warnings"),
                    ScenarioInfo("repeat_offender", "Repeat Offender", "User ignores multiple warnings"),
                    ScenarioInfo("late_night_escalation", "Late Night Escalation", "Late night + multiple warnings")
                ),
                onScenarioSelected = onScenarioSelected
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Escalation Patterns
            ScenarioCategory(
                title = "⚠️ Escalation Patterns",
                scenarios = listOf(
                    ScenarioInfo("multiple_sessions_same_app", "Multiple Sessions", "3 sessions totaling 90min over 2 hours"),
                    ScenarioInfo("relapse_pattern", "Relapse Pattern", "User stopped, started again quickly")
                ),
                onScenarioSelected = onScenarioSelected
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Pattern Detection
            ScenarioCategory(
                title = "🔍 Pattern Detection",
                scenarios = listOf(
                    ScenarioInfo("app_hopping_binge", "App Hopping", "Rapid switching between apps"),
                    ScenarioInfo("extended_morning", "Morning Usage", "Early morning usage affecting routine")
                ),
                onScenarioSelected = onScenarioSelected
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Context-Aware
            ScenarioCategory(
                title = "📱 Context-Aware",
                scenarios = listOf(
                    ScenarioInfo("weekend_binge", "Weekend Binge", "Extended weekend usage"),
                    ScenarioInfo("low_battery_usage", "Low Battery", "User continues despite low battery"),
                    ScenarioInfo("work_vs_leisure", "Work vs Leisure", "Long Chrome usage (ambiguous)")
                ),
                onScenarioSelected = onScenarioSelected
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Basic Scenarios
            ScenarioCategory(
                title = "📋 Basic Scenarios",
                scenarios = listOf(
                    ScenarioInfo("youtube_long_session", "YouTube 45min", "Standard 45-minute session"),
                    ScenarioInfo("tiktok_late_night", "TikTok Late Night", "Late night usage, first offense"),
                    ScenarioInfo("minimal_usage", "Minimal Usage", "25min (should NOT intervene)")
                ),
                onScenarioSelected = onScenarioSelected
            )
        }
    }
}

@Composable
private fun ScenarioCategory(
    title: String,
    scenarios: List<ScenarioInfo>,
    onScenarioSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            scenarios.forEach { scenario ->
                OutlinedButton(
                    onClick = { onScenarioSelected(scenario.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        Text(scenario.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            scenario.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private data class ScenarioInfo(
    val id: String,
    val name: String,
    val description: String
)

private fun setupScenario(context: android.content.Context, scenarioType: String) {
    // Determine which scenario name to use for app usage stats
    val appUsageScenario = when (scenarioType) {
        "youtube_long_session" -> "continuous_session"
        "tiktok_late_night" -> "late_night"
        "multiple_sessions_same_app" -> "multiple_sessions"
        "app_hopping_binge" -> "app_switching"
        else -> scenarioType
    }
    
    // Enable all mocks and set scenario data
    ContextProviderFactory.setAppUsageMock(context, true)
    MockAppUsageContextProvider.setScenario(appUsageScenario)
    
    ContextProviderFactory.setUserBadBehaviorMock(context, true)
    MockUserBadBehaviorContextProvider.setDefaultScenario(scenarioType)
    
    ContextProviderFactory.setMemoryMock(context, true)
    MockMemoryContextProvider.setMockTimeline(
        MockMemoryContextProvider.generateMockTimelineForScenario(scenarioType)
    )
    
    ContextProviderFactory.setPhoneStateMock(context, true)
    MockPhoneStateContextProvider.setDefaultScenario(scenarioType)
    
    ContextProviderFactory.setChatHistoryMock(context, true)
    MockChatHistoryContextProvider.setDefaultScenario(scenarioType)
}

@Composable
private fun TestResultsDisplaySection(result: UnifiedTestPipeline.TestResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Test Results",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // UsagePatternContextProvider Output
            ExpandableSection(
                title = "UsagePatternContextProvider Output",
                content = result.outputs.usagePatternAnalysis
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // PersonalityAgent Output
            Text("PersonalityAgent Decision", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            StateRow("Should Intervene", if (result.outputs.personalityDecision.shouldIntervene) "Yes" else "No")
            StateRow("Urgency", "${result.outputs.personalityDecision.urgency}/10")
            StateRow("Intervention Type", result.outputs.personalityDecision.interventionType.name)
            Spacer(modifier = Modifier.height(8.dp))
            ExpandableSection(
                title = "Response",
                content = result.outputs.personalityDecision.response
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Provider States
            Text("Provider States", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            result.providerStates.forEach { (provider, isMock) ->
                StateRow(provider, if (isMock) "Mock" else "Real")
            }
        }
    }
}

@Composable
private fun ExpandableSection(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }
        
        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = content,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun UtilitiesSection(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Utilities",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = {
                    scope.launch {
                        com.example.myapplication.memory.EnhancedMemoryManager.clear(context)
                        com.example.myapplication.agents.ChatManager.clearHistory(context)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear All Data")
            }
        }
    }
}

