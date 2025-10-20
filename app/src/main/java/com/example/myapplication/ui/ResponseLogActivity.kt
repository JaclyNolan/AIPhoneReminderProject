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
    var responses by remember { mutableStateOf<List<ResponseLogger.ResponseEntry>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showRequest by remember { mutableStateOf(false) }
    var showResponse by remember { mutableStateOf(false) }

    // Load responses on composition
    LaunchedEffect(Unit) {
        responses = ResponseLogger.getAll(context)
    }

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
                        enabled = responses.isNotEmpty()
                    ) {
                        Text("Clear All")
                    }
                    TextButton(
                        onClick = {
                            responses = ResponseLogger.getAll(context)
                        }
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
            if (responses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No responses logged yet",
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
                    items(responses, key = { it.timestamp + it.response.hashCode() }) { entry ->
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
            title = { Text("Clear Response Log") },
            text = { Text("Are you sure you want to clear all logged responses? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ResponseLogger.clear(context)
                        responses = emptyList()
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
                    text = entry.request,
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
                    text = entry.response,
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
