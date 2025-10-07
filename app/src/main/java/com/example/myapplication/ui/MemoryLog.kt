package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.MemoryEntry
import java.util.Locale

@Composable
fun MemoryLogScreen(
    memories: List<MemoryEntry>,
    onBack: () -> Unit = {},
    onClear: () -> Unit = {},
    onDelete: (MemoryEntry) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        // Back button row
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }

        Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text("Clear Memories")
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(memories) { m ->
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            text = String.format(Locale.US, "At %s", m.timestamp),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(text = m.entry, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))

                        // Delete button for this entry
                        Button(onClick = { onDelete(m) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
