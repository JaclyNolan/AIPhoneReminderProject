package com.example.myapplication.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ResponseLogger
import com.example.myapplication.ui.theme.MyApplicationTheme

class ResponseLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                ResponseLogScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponseLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var chatResponses by remember { mutableStateOf<List<ResponseLogger.ResponseEntry>>(emptyList()) }
    var analyzerResponses by remember { mutableStateOf<List<ResponseLogger.ResponseEntry>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showRequest by remember { mutableStateOf(false) }
    var showResponse by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }

    // Load responses on composition
    LaunchedEffect(Unit) {
        chatResponses = ResponseLogger.getByType(context, ResponseLogger.LogType.CHAT_MANAGER)
        analyzerResponses = ResponseLogger.getByType(context, ResponseLogger.LogType.ANALYZER_AGENT)
    }

    fun refresh() {
        chatResponses = ResponseLogger.getByType(context, ResponseLogger.LogType.CHAT_MANAGER)
        analyzerResponses = ResponseLogger.getByType(context, ResponseLogger.LogType.ANALYZER_AGENT)
    }

    val currentResponses = if (selectedTab == 0) chatResponses else analyzerResponses
    val currentLogType = if (selectedTab == 0) ResponseLogger.LogType.CHAT_MANAGER else ResponseLogger.LogType.ANALYZER_AGENT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Response Log") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showClearDialog = true },
                        enabled = currentResponses.isNotEmpty()
                    ) {
                        Text("Clear ${if (selectedTab == 0) "Chat" else "Analyzer"}")
                    }
                    TextButton(
                        onClick = { refresh() }
                    ) {
                        Text("Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab selector
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ChatManager")
                            Text(
                                text = "${chatResponses.size} logs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("AnalyzerAgent")
                            Text(
                                text = "${analyzerResponses.size} logs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            // Toggle controls
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = showRequest,
                            onCheckedChange = { showRequest = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Show Request",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = showResponse,
                            onCheckedChange = { showResponse = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Show Response",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Response list
            if (currentResponses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ${if (selectedTab == 0) "ChatManager" else "AnalyzerAgent"} responses logged yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(currentResponses, key = { it.timestamp + it.response.hashCode() }) { entry ->
                        ResponseEntryCard(
                            entry = entry,
                            showRequest = showRequest,
                            showResponse = showResponse
                        )
                    }
                }
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear ${if (selectedTab == 0) "ChatManager" else "AnalyzerAgent"} Log") },
            text = { Text("Are you sure you want to clear all ${if (selectedTab == 0) "ChatManager" else "AnalyzerAgent"} logged responses? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ResponseLogger.clearByType(context, currentLogType)
                        refresh()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ResponseEntryCard(entry: ResponseLogger.ResponseEntry, showRequest: Boolean, showResponse: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Timestamp
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // Token usage display (if any)
            if (entry.promptTokens != null || entry.completionTokens != null || entry.totalTokens != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tokens: " + listOfNotNull(
                        entry.promptTokens?.let { "prompt=$it" },
                        entry.completionTokens?.let { "completion=$it" },
                        entry.totalTokens?.let { "total=$it" }
                    ).joinToString(", "),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Request section
            if (showRequest && entry.request.isNotBlank()) {
                Text(
                    text = "REQUEST:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatJson(entry.request),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Response section
            if (showResponse) {
                Text(
                    text = "RESPONSE:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatJson(entry.response),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Format JSON string with proper indentation for better readability
 * This implementation uses org.json.JSONTokener.nextValue() to unwrap
 * quoted JSON string literals (e.g. "{\"key\":\"val\"}") so
 * the displayed text won't contain backslash-escaped quotes.
 */
private fun formatJson(jsonString: String, depth: Int = 0): String {
    if (depth > 5) return jsonString // prevent pathological recursion
    val s = jsonString.trim()
    return try {
        val tok = org.json.JSONTokener(s)
        val value = tok.nextValue()
        when (value) {
            is org.json.JSONObject -> value.toString(2)
            is org.json.JSONArray -> value.toString(2)
            is String -> {
                // The content was a quoted string. It may itself contain JSON
                // with escaped characters; recurse to attempt pretty-printing.
                formatJson(value, depth + 1)
            }
            else -> s
        }
    } catch (_: Exception) {
        // Fallback: try previous approach of JSONObject/JSONArray parsing directly
        try {
            val obj = org.json.JSONObject(s)
            obj.toString(2)
        } catch (_: Exception) {
            try {
                val arr = org.json.JSONArray(s)
                arr.toString(2)
            } catch (_: Exception) {
                // If nothing parses, try to extract inner JSON snippet (best-effort)
                val start = s.indexOfFirst { it == '{' || it == '[' }
                val end = s.indexOfLast { it == '}' || it == ']' }
                if (start >= 0 && end > start) {
                    val inner = s.substring(start, end + 1)
                    try {
                        val tok2 = org.json.JSONTokener(inner)
                        val v2 = tok2.nextValue()
                        return when (v2) {
                            is org.json.JSONObject -> v2.toString(2)
                            is org.json.JSONArray -> v2.toString(2)
                            is String -> v2
                            else -> s
                        }
                    } catch (_: Exception) {
                        return s
                    }
                }
                s
            }
        }
    }
}
