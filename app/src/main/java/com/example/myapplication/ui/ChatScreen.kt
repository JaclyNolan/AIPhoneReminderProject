package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.ChatManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * ChatScreen - Interactive chat interface with Ralsei
 *
 * Features:
 * - Message history display with role-based formatting
 * - Assistant response parsing (thinking + text)
 * - Developer message toggle for debugging
 * - Text input with send functionality
 */

/**
 * Parse assistant JSON responses into structured (thinking, text) pairs
 * Handles both single response objects and response arrays
 */
private fun parseAssistantResponses(text: String): List<Pair<String?, String?>> {
    val result = mutableListOf<Pair<String?, String?>>()

    // Try parsing as top-level object with "response" field
    try {
        val root = JSONObject(text)
        if (root.has("response")) {
            val resp = root.getJSONArray("response")
            for (i in 0 until resp.length()) {
                val obj = resp.getJSONObject(i)
                val thinking = obj.optString("thinking").takeIf { it.isNotBlank() }
                val body = obj.optString("text").takeIf { it.isNotBlank() }
                result.add(Pair(thinking, body))
            }
            return result
        }
    } catch (_: Exception) {
        // Ignore and try as array below
    }

    // Try parsing as direct array
    try {
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val thinking = obj.optString("thinking").takeIf { it.isNotBlank() }
            val body = obj.optString("text").takeIf { it.isNotBlank() }
            result.add(Pair(thinking, body))
        }
        if (result.isNotEmpty()) return result
    } catch (_: Exception) {
        // Not JSON; fall through
    }

    return emptyList()
}

@Composable
fun ChatScreen(
    showDeveloper: Boolean,
    onToggleShowDeveloper: (Boolean) -> Unit,
    messages: List<ChatManager.ChatMessage>,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onSend: (String) -> Unit,
    inputText: String,
    onInputChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onClear) {
                Text("Clear History")
            }

            Button(onClick = onRefresh) {
                Text("Refresh")
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Checkbox(
                    checked = showDeveloper,
                    onCheckedChange = onToggleShowDeveloper
                )
                Text(
                    text = "Dev",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Message list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                MessageCard(message = message)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                singleLine = false,
                maxLines = 4
            )

            Button(
                onClick = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty()) {
                        onSend(trimmed)
                    }
                },
                enabled = inputText.trim().isNotEmpty()
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun MessageCard(message: ChatManager.ChatMessage) {
    // Track expansion state for developer messages
    var isExpanded by remember { mutableStateOf(false) }
    val isDeveloperMessage = message.role == "developer"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (message.role) {
                "user" -> MaterialTheme.colorScheme.primaryContainer
                "assistant" -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Message header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (message.role) {
                        "user" -> "You"
                        "assistant" -> "Ralsei"
                        else -> message.role.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase() else it.toString()
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Message content
            if (message.role == "assistant") {
                val parts = parseAssistantResponses(message.text)
                if (parts.isNotEmpty()) {
                    parts.forEach { (thinking, body) ->
                        if (!thinking.isNullOrBlank()) {
                            Text(
                                text = "💭 $thinking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!body.isNullOrBlank()) {
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (isDeveloperMessage) {
                // Developer messages: expandable if long
                val textLength = message.text.length
                val isLongMessage = textLength > 200

                if (isLongMessage) {
                    // Show truncated or full text based on expansion state
                    Text(
                        text = if (isExpanded) message.text else message.text.take(200) + "...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Expand/Collapse button
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isExpanded) "▲ Show Less" else "▼ Show More (${textLength} chars)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    // Short developer message - just show it
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}