package com.example.getyourlifeback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.example.getyourlifeback.ui.theme.GetYourLifeBackTheme

class MainActivity : ComponentActivity() {
    private lateinit var permissionManager: PermissionManager
    private lateinit var usageStatsManager: AppUsageStatsManager
    private lateinit var sessionManager: SessionManager
    private lateinit var startupManager: StartupManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        permissionManager = PermissionManager(this)
        usageStatsManager = AppUsageStatsManager(this)
        sessionManager = SessionManager(this)
        startupManager = StartupManager(this)
        
        // Handle startup mode from auto-launch
        val isAutoLaunched = intent.getBooleanExtra("auto_launched", false)
        val isStartupMode = intent.getBooleanExtra("startup_mode", false)
        
        if (isAutoLaunched && isStartupMode) {
            // App was auto-launched after boot - ensure session continuity
            if (sessionManager.isSessionActive()) {
                val config = sessionManager.getSessionConfig()
                config?.let {
                    // Restart services if needed
                    val persistentIntent = Intent(this, PersistentService::class.java)
                    persistentIntent.putExtra("focusDuration", it.focusDuration)
                    persistentIntent.putExtra("reminderInterval", it.reminderInterval)
                    persistentIntent.putExtra("cooldownTime", it.cooldownTime)
                    persistentIntent.putExtra("isSpecificAppsMode", it.isSpecificAppsMode)
                    persistentIntent.putStringArrayListExtra("selectedApps", ArrayList(it.selectedApps))
                    startForegroundService(persistentIntent)
                }
            }
        }
        
        enableEdgeToEdge()
        setContent {
            GetYourLifeBackTheme {
                AppUsageScreen()
            }
        }
        
        permissionManager.requestAllPermissions()
    }
    
    @Composable
    fun AppUsageScreen() {
        var usageStats by remember { mutableStateOf(emptyList<AppUsageStatsManager.AppUsageInfo>()) }
        var isOverlayActive by remember { mutableStateOf(sessionManager.isSessionActive()) }
        var showConfigDialog by remember { mutableStateOf(false) }
        var remainingTime by remember { mutableStateOf(sessionManager.getRemainingTime()) }
        var focusDuration by remember { mutableStateOf(10) } // minutes
        var reminderInterval by remember { mutableStateOf(120) } // seconds (2 minutes)
        var cooldownTime by remember { mutableStateOf(60) } // seconds (1 minute)
        var isSpecificAppsMode by remember { mutableStateOf(false) }
        var selectedApps by remember { mutableStateOf(setOf<String>()) }
        
        LaunchedEffect(Unit) {
            usageStats = usageStatsManager.getDailyUsageStats()
            
            // Check for active session and resume if needed
            if (sessionManager.isSessionActive()) {
                val config = sessionManager.getSessionConfig()
                config?.let {
                    val persistentIntent = Intent(this@MainActivity, PersistentService::class.java)
                    persistentIntent.putExtra("focusDuration", it.focusDuration)
                    persistentIntent.putExtra("reminderInterval", it.reminderInterval)
                    persistentIntent.putExtra("cooldownTime", it.cooldownTime)
                    persistentIntent.putExtra("isSpecificAppsMode", it.isSpecificAppsMode)
                    persistentIntent.putStringArrayListExtra("selectedApps", ArrayList(it.selectedApps))
                    startForegroundService(persistentIntent)
                }
            }
            
            // Auto-refresh every second for real-time updates
            while (true) {
                kotlinx.coroutines.delay(1000)
                usageStats = usageStatsManager.getDailyUsageStats()
                isOverlayActive = sessionManager.isSessionActive()
                remainingTime = sessionManager.getRemainingTime()
            }
        }
        
        val gradientColors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary
        )
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                // Enhanced Header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.tertiary
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(1000f, 1000f)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Text(
                                text = "🌱 Get Your Life Back",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "✨ Master your digital wellness journey",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.95f),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔥 Take control today",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Enhanced Action Button
                if (!isOverlayActive) {
                    Button(
                        onClick = {
                            if (!sessionManager.isSessionActive()) {
                                showConfigDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow, 
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "🧘 Start Mindful Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🧘 Mindful Mode Active",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⏰ ${formatTime(remainingTime)} remaining",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Session will end automatically",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📊 Today's App Usage",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    if (isOverlayActive) {
                        Text(
                            text = "🟢 Mindful Mode ON",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(usageStats) { app ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.elevatedCardElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📱 ${app.appName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "⏱️ ${usageStatsManager.formatTime(app.totalTimeInForeground)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                
                                // Usage indicator
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            color = when {
                                                app.totalTimeInForeground > 3600000 -> Color.Red // > 1 hour
                                                app.totalTimeInForeground > 1800000 -> Color(0xFFFF9800) // > 30 min
                                                else -> Color(0xFF4CAF50) // < 30 min
                                            },
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Configuration Dialog
        if (showConfigDialog) {
            AlertDialog(
                onDismissRequest = { showConfigDialog = false },
                title = { Text("⚙️ Configure Mindful Mode") },
                text = {
                    Column {
                        // Mode Selection
                        Text("Mindful Mode Type:", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !isSpecificAppsMode,
                                onClick = { 
                                    isSpecificAppsMode = false
                                    focusDuration = 10
                                    reminderInterval = 120
                                    cooldownTime = 60
                                }
                            )
                            Text("Whole Mobile")
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(
                                selected = isSpecificAppsMode,
                                onClick = { 
                                    isSpecificAppsMode = true
                                    focusDuration = 15
                                    reminderInterval = 60
                                    cooldownTime = 90
                                }
                            )
                            Text("Specific Apps")
                        }
                        
                        if (isSpecificAppsMode) {
                            Text("Selected Apps: ${selectedApps.size}")
                            LazyColumn(
                                modifier = Modifier.height(100.dp)
                            ) {
                                items(usageStats.take(10)) { app ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = selectedApps.contains(app.packageName),
                                            onCheckedChange = { checked ->
                                                selectedApps = if (checked) {
                                                    selectedApps + app.packageName
                                                } else {
                                                    selectedApps - app.packageName
                                                }
                                            }
                                        )
                                        Text(
                                            text = app.appName,
                                            modifier = Modifier.padding(start = 8.dp),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Focus Duration: $focusDuration minutes")
                        Slider(
                            value = focusDuration.toFloat(),
                            onValueChange = { focusDuration = it.toInt() },
                            valueRange = 5f..120f,
                            steps = 22
                        )
                        
                        Text("Reminder Interval: ${reminderInterval/60}:${String.format("%02d", reminderInterval%60)} minutes")
                        Slider(
                            value = reminderInterval.toFloat(),
                            onValueChange = { reminderInterval = it.toInt() },
                            valueRange = 30f..300f,
                            steps = 26
                        )
                        
                        Text("Cooldown Time: ${cooldownTime/60}:${String.format("%02d", cooldownTime%60)} minutes")
                        Slider(
                            value = cooldownTime.toFloat(),
                            onValueChange = { cooldownTime = it.toInt() },
                            valueRange = 30f..180f,
                            steps = 14
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Start session with SessionManager
                            sessionManager.startSession(
                                focusDuration, reminderInterval, cooldownTime,
                                isSpecificAppsMode, selectedApps
                            )
                            
                            // Start persistent service
                            val persistentIntent = Intent(this@MainActivity, PersistentService::class.java)
                            persistentIntent.putExtra("focusDuration", focusDuration)
                            persistentIntent.putExtra("reminderInterval", reminderInterval)
                            persistentIntent.putExtra("cooldownTime", cooldownTime)
                            persistentIntent.putExtra("isSpecificAppsMode", isSpecificAppsMode)
                            persistentIntent.putStringArrayListExtra("selectedApps", ArrayList(selectedApps))
                            startForegroundService(persistentIntent)
                            
                            // Start watchdog service
                            val watchdogIntent = Intent(this@MainActivity, WatchdogService::class.java)
                            startService(watchdogIntent)
                            
                            isOverlayActive = true
                            showConfigDialog = false
                        }
                    ) {
                        Text("Start")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfigDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    
    private fun requestDeviceAdmin() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = android.content.ComponentName(this, DeviceAdminReceiver::class.java)
        
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            startActivity(intent)
        }
    }
    
    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%02d:%02d", minutes, seconds)
        }
    }
}
