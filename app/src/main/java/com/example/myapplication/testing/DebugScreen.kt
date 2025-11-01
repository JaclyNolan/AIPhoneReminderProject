package com.example.myapplication.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.myapplication.agents.PatternAgent
import kotlinx.coroutines.launch
import com.example.myapplication.testing.RealAppUsageReader

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
    
    var mockMode by remember { mutableStateOf(LLMClientFactory.isMockMode()) }
    var simulateDelay by remember { mutableStateOf(MockLLMClient.simulateDelay) }
    var recordMode by remember { mutableStateOf(MockLLMClient.recordMode) }
    
    var systemState by remember { mutableStateOf<TestAgent.SystemState?>(null) }
    var testResults by remember { mutableStateOf<List<TestAgent.TestResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedScenario by remember { mutableStateOf<TestScenario?>(null) }
    
    // Load initial state
    LaunchedEffect(Unit) {
        systemState = TestAgent.inspectState(context)
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
            // System State Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "System State",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        systemState?.let { state ->
                            StateRow("Timeline entries", state.timelineSize.toString())
                            StateRow("Current app", state.currentApp)
                            StateRow("Warnings today", state.warningCount.toString())
                            StateRow("Last urgency", state.lastWarningUrgency.toString())
                            StateRow("Mock mode", if (state.mockMode) "✅ ON" else "❌ OFF")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    systemState = TestAgent.inspectState(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh State")
                        }
                    }
                }
            }
            
            // Mock Mode Controls
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Mock Mode Controls",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mock API responses")
                            Switch(
                                checked = mockMode,
                                onCheckedChange = {
                                    mockMode = it
                                    LLMClientFactory.setMockMode(it)
                                }
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Simulate delay")
                            Switch(
                                checked = simulateDelay,
                                onCheckedChange = {
                                    simulateDelay = it
                                    MockLLMClient.simulateDelay = it
                                }
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Record real responses")
                            Switch(
                                checked = recordMode,
                                onCheckedChange = {
                                    recordMode = it
                                    MockLLMClient.recordMode = it
                                }
                            )
                        }
                        
                        if (recordMode && !mockMode) {
                            Text(
                                "⚠️ Recording enabled. Real API calls will be saved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            
            // Test Scenarios
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Test Scenarios",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isRunning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Running scenario...")
                            }
                        } else {
                            TestScenarios.getAll().forEach { scenario ->
                                ScenarioButton(
                                    scenario = scenario,
                                    onClick = {
                                        selectedScenario = scenario
                                        isRunning = true
                                        scope.launch {
                                            val result = TestAgent.runScenario(context, scenario)
                                            testResults = listOf(result) + testResults
                                            systemState = TestAgent.inspectState(context)
                                            isRunning = false
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            Button(
                                onClick = {
                                    isRunning = true
                                    testResults = emptyList()
                                    scope.launch {
                                        val results = TestAgent.runAllScenarios(context)
                                        testResults = results
                                        systemState = TestAgent.inspectState(context)
                                        isRunning = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Run All Scenarios")
                            }
                        }
                    }
                }
            }
            
            // Test Results
            if (testResults.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Test Results",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val passed = testResults.count { it.success }
                            val total = testResults.size
                            Text(
                                "Passed: $passed/$total",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (passed == total) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                items(testResults) { result ->
                    TestResultCard(result)
                }
            }
            
            // Manual PatternAgent Test Section
            item {
                ManualPatternAgentTestSection()
            }
            
            // Utilities
            item {
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
                                    systemState = TestAgent.inspectState(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Data")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = {
                                MockLLMClient.clearSavedResponses(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear Saved Responses")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualPatternAgentTestSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var response by remember { mutableStateOf<PatternAgent.PatternViolation?>(null) }
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
                "Test PatternAgent Now",
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
                            
                            // Call PatternAgent
                            val violation = PatternAgent.checkViolations(context, patternContext)
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
                Text("Test PatternAgent Now")
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

