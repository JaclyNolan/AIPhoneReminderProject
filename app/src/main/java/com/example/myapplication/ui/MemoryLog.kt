package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.memory.MemoryEntry
import com.example.myapplication.memory.EnhancedMemoryManager

/**
 * MemoryLogScreen - Display and manage all memory types
 *
 * Features:
 * - Tab-based navigation for different memory types
 * - Scene Timeline, Condensed Memories, Recent Intents, Dialogue Summaries
 * - Individual and bulk memory management
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryLogScreen(
    memories: List<MemoryEntry>,
    sceneTimeline: List<EnhancedMemoryManager.SceneTimelineEntry>,
    condensedMemories: List<EnhancedMemoryManager.CondensedMemoryItem>,
    recentIntents: List<EnhancedMemoryManager.RecentIntent>,
    dialogueSummaries: List<String>,
    onBack: () -> Unit = {},
    onClear: () -> Unit = {},
    onClearEnhanced: () -> Unit = {},
    onDelete: (MemoryEntry) -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory System") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back")
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh) {
                        Text("Refresh")
                    }
                    TextButton(
                        onClick = { showClearDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear")
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Basic Memories")
                            Text(
                                text = "${memories.size} entries",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Scene Timeline")
                            Text(
                                text = "${sceneTimeline.size} scenes",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Condensed")
                            Text(
                                text = "${condensedMemories.size} items",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Recent Intents")
                            Text(
                                text = "${recentIntents.size} intents",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Summaries")
                            Text(
                                text = "${dialogueSummaries.size} items",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            }

            HorizontalDivider()

            // Display content based on selected tab
            when (selectedTab) {
                0 -> BasicMemoriesTab(memories, onDelete)
                1 -> SceneTimelineTab(sceneTimeline)
                2 -> CondensedMemoriesTab(condensedMemories)
                3 -> RecentIntentsTab(recentIntents)
                4 -> DialogueSummariesTab(dialogueSummaries)
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear ${getTabName(selectedTab)}") },
            text = {
                Text(
                    if (selectedTab == 0) {
                        "Clear all basic memories? This cannot be undone."
                    } else {
                        "Clear all Enhanced Memory data (Scene Timeline, Condensed Memories, Intents, Summaries)? This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedTab == 0) onClear() else onClearEnhanced()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
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

private fun getTabName(tab: Int): String = when (tab) {
    0 -> "Basic Memories"
    1 -> "Scene Timeline"
    2 -> "Condensed Memories"
    3 -> "Recent Intents"
    4 -> "Dialogue Summaries"
    else -> "Memory"
}

@Composable
private fun BasicMemoriesTab(memories: List<MemoryEntry>, onDelete: (MemoryEntry) -> Unit) {
    if (memories.isEmpty()) {
        EmptyPlaceholder(
            emoji = "📝",
            title = "No basic memories yet",
            subtitle = "Basic memories will appear here"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(memories) { memory ->
                BasicMemoryCard(memory = memory, onDelete = { onDelete(memory) })
            }
        }
    }
}

@Composable
private fun SceneTimelineTab(timeline: List<EnhancedMemoryManager.SceneTimelineEntry>) {
    if (timeline.isEmpty()) {
        EmptyPlaceholder(
            emoji = "🎬",
            title = "No scene timeline entries",
            subtitle = "Scene observations will appear here"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(timeline) { entry ->
                SceneTimelineCard(entry)
            }
        }
    }
}

@Composable
private fun CondensedMemoriesTab(memories: List<EnhancedMemoryManager.CondensedMemoryItem>) {
    if (memories.isEmpty()) {
        EmptyPlaceholder(
            emoji = "💡",
            title = "No condensed memories",
            subtitle = "Important facts and emotional moments will appear here"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(memories) { item ->
                CondensedMemoryCard(item)
            }
        }
    }
}

@Composable
private fun RecentIntentsTab(intents: List<EnhancedMemoryManager.RecentIntent>) {
    if (intents.isEmpty()) {
        EmptyPlaceholder(
            emoji = "🎯",
            title = "No recent intents",
            subtitle = "Recent dialogue intents will appear here"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(intents) { intent ->
                RecentIntentCard(intent)
            }
        }
    }
}

@Composable
private fun DialogueSummariesTab(summaries: List<String>) {
    if (summaries.isEmpty()) {
        EmptyPlaceholder(
            emoji = "💬",
            title = "No dialogue summaries",
            subtitle = "Compressed chat history will appear here"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(summaries.size) { index ->
                DialogueSummaryCard(index + 1, summaries[index])
            }
        }
    }
}

// Card Components

@Composable
private fun BasicMemoryCard(memory: MemoryEntry, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📅 ${memory.timestamp}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = memory.entry,
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider()
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("🗑️ Delete")
            }
        }
    }
}

@Composable
private fun SceneTimelineCard(entry: EnhancedMemoryManager.SceneTimelineEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎬 ${entry.sceneLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "confidence: ${String.format("%.2f", entry.confidence)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = entry.shortText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CondensedMemoryCard(item: EnhancedMemoryManager.CondensedMemoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "💡 ${item.source}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "conf: ${String.format("%.2f", item.confidence)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RecentIntentCard(intent: EnhancedMemoryManager.RecentIntent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🎯 ${intent.intent}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = intent.length,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = intent.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Hash: ${intent.phrasingHash}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DialogueSummaryCard(index: Int, summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "💬 Summary #$index",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EmptyPlaceholder(emoji: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
