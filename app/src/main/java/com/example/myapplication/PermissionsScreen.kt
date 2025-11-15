package com.example.myapplication

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Permission information screen
 * Shows all required permissions with status and quick access to grant them
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(getAllPermissions(context)) }
    var lastRefresh by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Refresh permissions when screen comes back to foreground
    DisposableEffect(Unit) {
        onDispose { }
    }
    
    // Manual refresh
    fun refreshPermissions() {
        permissions = getAllPermissions(context)
        lastRefresh = System.currentTimeMillis()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with refresh button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Required Permissions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { refreshPermissions() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
            
            item {
                Text(
                    "This app requires several permissions to function properly. " +
                    "Tap 'Grant' to open settings and enable each permission.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Permission cards
            items(permissions) { permission ->
                PermissionCard(
                    permission = permission,
                    onGrantClick = {
                        openPermissionSettings(context, permission.type)
                        // Refresh after a delay (user will come back from settings)
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(500)
                            refreshPermissions()
                        }
                    }
                )
            }
            
            // Grant All Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                val grantedCount = permissions.count { it.isGranted }
                val totalCount = permissions.size
                
                if (grantedCount < totalCount) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "⚠️ Some permissions are missing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "The app will not function correctly without all permissions. " +
                                "Please grant each permission above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "✅ All permissions granted!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "The app is ready to use.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: PermissionInfo,
    onGrantClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (permission.isGranted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with icon and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = permission.icon,
                        contentDescription = null,
                        tint = if (permission.isGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            permission.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (permission.isGranted) "✅ Granted" else "❌ Not granted",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (permission.isGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
                
                // Grant button
                if (!permission.isGranted) {
                    Button(
                        onClick = onGrantClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Grant")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Description
            Text(
                permission.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Technical details (collapsed by default)
            if (permission.technicalDetails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Technical: ${permission.technicalDetails}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Data class for permission information
 */
data class PermissionInfo(
    val type: PermissionType,
    val name: String,
    val description: String,
    val technicalDetails: String,
    val isGranted: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

enum class PermissionType {
    OVERLAY,
    FOREGROUND_SERVICE,
    USAGE_STATS,
    NOTIFICATIONS,
    MEDIA_PROJECTION
}

/**
 * Get all permissions with current status
 */
private fun getAllPermissions(context: Context): List<PermissionInfo> {
    return listOf(
        PermissionInfo(
            type = PermissionType.OVERLAY,
            name = "Display over other apps",
            description = "Required to show Ralsei's dialogue overlays and soft intervention screens on top of other apps. " +
                    "Without this, you won't see warnings or be able to chat with Ralsei while using other apps.",
            technicalDetails = "SYSTEM_ALERT_WINDOW permission",
            isGranted = Settings.canDrawOverlays(context),
            icon = Icons.Default.Star // Star icon for overlay/display
        ),
        PermissionInfo(
            type = PermissionType.FOREGROUND_SERVICE,
            name = "Run in background",
            description = "Allows the app to run continuously in the background to monitor your screen time. " +
                    "This is required for automatic screenshot analysis and pattern detection.",
            technicalDetails = "FOREGROUND_SERVICE permission (always granted on install)",
            isGranted = true, // Automatically granted on install
            icon = Icons.Default.PlayArrow // Play icon for running/active
        ),
        PermissionInfo(
            type = PermissionType.USAGE_STATS,
            name = "Usage access",
            description = "Required to read which apps you're using and for how long. " +
                    "This data is used to detect patterns like long YouTube sessions or late-night TikTok usage. " +
                    "Without this, pattern detection will be limited to screenshot analysis only.",
            technicalDetails = "PACKAGE_USAGE_STATS permission",
            isGranted = hasUsageStatsPermission(context),
            icon = Icons.Default.Info // Info icon for usage stats
        ),
        PermissionInfo(
            type = PermissionType.NOTIFICATIONS,
            name = "Notifications",
            description = "Required to show notifications about screen time warnings and Ralsei's messages. " +
                    "The foreground service also requires a persistent notification to run.",
            technicalDetails = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "POST_NOTIFICATIONS permission (Android 13+)"
            } else {
                "Automatically granted on Android 12 and below"
            },
            isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Auto-granted on older versions
            },
            icon = Icons.Default.Notifications
        ),
        PermissionInfo(
            type = PermissionType.MEDIA_PROJECTION,
            name = "Screen capture",
            description = "Required to take screenshots for AI analysis. " +
                    "Screenshots are analyzed locally and used to understand what you're viewing. " +
                    "Only a few frames are sent to the AI API for commentary generation.",
            technicalDetails = "MediaProjection permission (granted on service start)",
            isGranted = true, // User grants this when starting the service
            icon = Icons.Default.Phone // Phone icon for screen/device capture
        )
    )
}

/**
 * Check if usage stats permission is granted
 */
private fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }
}

/**
 * Open the appropriate settings page for each permission
 */
private fun openPermissionSettings(context: Context, type: PermissionType) {
    val intent = when (type) {
        PermissionType.OVERLAY -> {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        }
        PermissionType.USAGE_STATS -> {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
        PermissionType.NOTIFICATIONS -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
        PermissionType.FOREGROUND_SERVICE,
        PermissionType.MEDIA_PROJECTION -> {
            // These don't have specific settings pages, open app info
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to app settings if specific intent fails
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(fallbackIntent)
    }
}

