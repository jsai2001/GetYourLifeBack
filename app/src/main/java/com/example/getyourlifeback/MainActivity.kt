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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
        
        // Handle startup scenarios
        val isAutoLaunched = intent.getBooleanExtra("auto_launched", false)
        val isStartupMode = intent.getBooleanExtra("startup_mode", false)
        val isWatchdogRestart = intent.getBooleanExtra("watchdog_restart", false)
        
        if ((isAutoLaunched && isStartupMode) || isWatchdogRestart) {
            // App was auto-launched after boot or restarted by watchdog - ensure session continuity
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
        var validationError by remember { mutableStateOf("") }
        var showOTPDialog by remember { mutableStateOf(false) }
        var otpInput by remember { mutableStateOf("") }
        var otpStatus by remember { mutableStateOf("") }
        var isOTPSending by remember { mutableStateOf(false) }
        var isOTPVerifying by remember { mutableStateOf(false) }
        var otpSent by remember { mutableStateOf(false) }
        val otpManager = remember { OTPManager(this@MainActivity) }
        
        LaunchedEffect(Unit) {
            usageStats = usageStatsManager.getDailyUsageStats()
            
            // Ensure session continuity - this handles any missed restarts
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
                
                // Enhanced OTP Test Button
                ElevatedButton(
                    onClick = { 
                        showOTPDialog = true
                        otpInput = ""
                        otpStatus = ""
                        isOTPSending = false
                        isOTPVerifying = false
                        otpSent = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "📧 Test OTP Email System",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
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
                                    cooldownTime = 30
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
                                                validationError = validateConfiguration(focusDuration, reminderInterval, cooldownTime, isSpecificAppsMode, selectedApps)
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
                            onValueChange = { 
                                focusDuration = it.toInt()
                                validationError = validateConfiguration(focusDuration, reminderInterval, cooldownTime, isSpecificAppsMode, selectedApps)
                            },
                            valueRange = 5f..120f,
                            steps = 22
                        )
                        
                        Text("Reminder Interval: ${reminderInterval/60}:${String.format("%02d", reminderInterval%60)} minutes")
                        Slider(
                            value = reminderInterval.toFloat(),
                            onValueChange = { 
                                reminderInterval = it.toInt()
                                validationError = validateConfiguration(focusDuration, reminderInterval, cooldownTime, isSpecificAppsMode, selectedApps)
                            },
                            valueRange = 30f..300f,
                            steps = 26
                        )
                        
                        Text("Cooldown Time: ${cooldownTime/60}:${String.format("%02d", cooldownTime%60)} minutes")
                        Slider(
                            value = cooldownTime.toFloat(),
                            onValueChange = { 
                                cooldownTime = it.toInt()
                                validationError = validateConfiguration(focusDuration, reminderInterval, cooldownTime, isSpecificAppsMode, selectedApps)
                            },
                            valueRange = 30f..180f,
                            steps = 14
                        )
                        
                        if (validationError.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ $validationError",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val error = validateConfiguration(focusDuration, reminderInterval, cooldownTime, isSpecificAppsMode, selectedApps)
                            if (error.isEmpty()) {
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
                            } else {
                                validationError = error
                            }
                        },
                        enabled = validateConfiguration(focusDuration, reminderInterval, cooldownTime, isSpecificAppsMode, selectedApps).isEmpty()
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
        
        // Enhanced OTP Dialog
        if (showOTPDialog) {
            AlertDialog(
                onDismissRequest = { 
                    if (!isOTPSending && !isOTPVerifying) {
                        showOTPDialog = false
                        otpInput = ""
                        otpStatus = ""
                        otpSent = false
                    }
                },
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📧 OTP Email Test",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        // Email info card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "📮 Email Address",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "jeevansaikanaparthi@gmail.com",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "⏱️ OTP valid for 15 minutes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // OTP Input Field
                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { 
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    otpInput = it
                                    if (otpStatus.contains("❌") || otpStatus.contains("Please enter")) {
                                        otpStatus = ""
                                    }
                                }
                            },
                            label = { Text("🔢 Enter 6-Digit OTP") },
                            placeholder = { Text("000000") },
                            singleLine = true,
                            enabled = !isOTPSending && !isOTPVerifying,
                            isError = otpStatus.contains("❌") || otpStatus.contains("Invalid"),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (otpInput.length == 6) {
                                    Text(
                                        text = "✓",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        )
                        
                        // Status Messages
                        if (otpStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        otpStatus.contains("✅") -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                        otpStatus.contains("❌") -> Color(0xFFF44336).copy(alpha = 0.1f)
                                        otpStatus.contains("Sending") -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isOTPSending || isOTPVerifying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = otpStatus,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = when {
                                            otpStatus.contains("✅") -> Color(0xFF4CAF50)
                                            otpStatus.contains("❌") -> Color(0xFFF44336)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Send OTP Button
                            if (!otpSent || otpStatus.contains("❌")) {
                                ElevatedButton(
                                    onClick = {
                                        isOTPSending = true
                                        otpStatus = "📤 Sending OTP email..."
                                        otpManager.generateAndSendOTP { success, message ->
                                            isOTPSending = false
                                            otpSent = success
                                            otpStatus = if (success) {
                                                "✅ $message"
                                            } else {
                                                "❌ $message"
                                            }
                                        }
                                    },
                                    enabled = !isOTPSending && !isOTPVerifying,
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isOTPSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (isOTPSending) "Sending..." else "📤 Send OTP",
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            // Verify Button
                            Button(
                                onClick = {
                                    when {
                                        otpInput.length != 6 -> {
                                            otpStatus = "❌ Please enter complete 6-digit OTP"
                                        }
                                        !otpSent && !otpStatus.contains("Demo mode") -> {
                                            otpStatus = "❌ Please send OTP first"
                                        }
                                        else -> {
                                            isOTPVerifying = true
                                            otpStatus = "🔍 Verifying OTP..."
                                            
                                            // Simulate verification delay for better UX
                                            CoroutineScope(Dispatchers.Main).launch {
                                                delay(1000)
                                                isOTPVerifying = false
                                                
                                                if (otpManager.validateOTP(otpInput)) {
                                                    otpStatus = "✅ OTP Verified Successfully! Email system is working perfectly."
                                                } else {
                                                    otpStatus = "❌ Invalid or expired OTP. Please try again."
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isOTPSending && !isOTPVerifying && otpInput.length == 6,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isOTPVerifying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isOTPVerifying) "Verifying..." else "🔍 Verify OTP",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            if (!isOTPSending && !isOTPVerifying) {
                                showOTPDialog = false
                                otpInput = ""
                                otpStatus = ""
                                otpSent = false
                            }
                        },
                        enabled = !isOTPSending && !isOTPVerifying
                    ) {
                        Text(
                            text = "❌ Close",
                            fontWeight = FontWeight.Medium
                        )
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
    
    private fun validateTimings(focusDuration: Int, reminderInterval: Int, cooldownTime: Int): String {
        val focusSeconds = focusDuration * 60
        return when {
            focusSeconds <= reminderInterval -> "Focus Duration must be greater than Reminder Interval"
            reminderInterval <= cooldownTime -> "Reminder Interval must be greater than Cooldown Time"
            focusSeconds - reminderInterval < 30 -> "Focus Duration must be at least 30 seconds longer than Reminder Interval"
            reminderInterval - cooldownTime < 30 -> "Reminder Interval must be at least 30 seconds longer than Cooldown Time"
            else -> ""
        }
    }
    
    private fun validateConfiguration(focusDuration: Int, reminderInterval: Int, cooldownTime: Int, isSpecificAppsMode: Boolean, selectedApps: Set<String>): String {
        val timingError = validateTimings(focusDuration, reminderInterval, cooldownTime)
        if (timingError.isNotEmpty()) return timingError
        
        if (isSpecificAppsMode && selectedApps.isEmpty()) {
            return "Please select at least one app for Specific Apps mode"
        }
        
        return ""
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
