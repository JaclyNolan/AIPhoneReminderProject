package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.ChatManager
import org.json.JSONArray
import org.json.JSONObject

// Helper to parse assistant JSON responses into a list of (thinking, text) pairs.
private fun parseAssistantResponses(text: String): List<Pair<String?, String?>> {
    val result = mutableListOf<Pair<String?, String?>>()
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
        // ignore and try as array below
    }

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
        // not JSON; fall through
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
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClear) { Text("Clear History") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onRefresh) { Text("Refresh") }
            Spacer(modifier = Modifier.width(12.dp))
            Checkbox(checked = showDeveloper, onCheckedChange = onToggleShowDeveloper)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Show developer messages (debug)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    val displayName = when (m.role) {
                        "user" -> "You"
                        "assistant" -> "Ralsei"
                        else -> m.role.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }

                    Text(text = "$displayName @ ${m.timestamp}", style = MaterialTheme.typography.labelSmall)

                    if (m.role == "assistant") {
                        val parts = parseAssistantResponses(m.text)
                        if (parts.isNotEmpty()) {
                            for ((thinking, body) in parts) {
                                if (!thinking.isNullOrBlank()) {
                                    Text(
                                        text = "Thinking: $thinking",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!body.isNullOrBlank()) {
                                    Text(text = body, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            Text(text = m.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        // non-assistant messages: show plain text (user/developer)
                        Text(text = m.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(value = inputText, onValueChange = onInputChange, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { val t = inputText.trim(); if (t.isNotEmpty()) onSend(t) }) { Text("Send") }
        }
    }
}
