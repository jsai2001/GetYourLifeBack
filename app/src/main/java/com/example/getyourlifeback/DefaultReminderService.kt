package com.example.getyourlifeback

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class DefaultReminderService : Service() {
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reminderRunnable: Runnable? = null
    private var autoHideRunnable: Runnable? = null
    private var isRunning = false
    private var otpAttempts = 0
    private var isProcessingOTP = false
    private var lastOTPRequestTime = 0L
    private val OTP_REQUEST_COOLDOWN = 60000L // 1 minute cooldown between OTP requests
    private var overlayCreationTime = 0L
    private val MIN_OVERLAY_DURATION = 3000L // Minimum 3 seconds before allowing interactions
    private var dailyNeedHelpCount = 0
    private val MAX_DAILY_NEED_HELP = 5 // Maximum 5 Need Help sessions per day
    private var lastNeedHelpDate = ""
    
    companion object {
        private const val PREFS_NAME = "default_reminder_prefs"
        private const val KEY_NEED_HELP_COUNT = "need_help_count"
        private const val KEY_NEED_HELP_DATE = "need_help_date"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            loadNeedHelpStats()
            startDefaultReminders()
            isRunning = true
        }
        return START_STICKY
    }
    
    private fun loadNeedHelpStats() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Use IST timezone explicitly
        val istTimeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val calendar = java.util.Calendar.getInstance(istTimeZone)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply {
            timeZone = istTimeZone
        }.format(calendar.time)
        
        lastNeedHelpDate = prefs.getString(KEY_NEED_HELP_DATE, "") ?: ""
        
        if (lastNeedHelpDate == today) {
            dailyNeedHelpCount = prefs.getInt(KEY_NEED_HELP_COUNT, 0)
        } else {
            // New day - reset count
            dailyNeedHelpCount = 0
            lastNeedHelpDate = today
            prefs.edit()
                .putString(KEY_NEED_HELP_DATE, today)
                .putInt(KEY_NEED_HELP_COUNT, 0)
                .apply()
        }
    }
    
    private fun startDefaultReminders() {
        reminderRunnable = object : Runnable {
            override fun run() {
                showDefaultReminder()
                handler.postDelayed(this, 60000) // Every 60 seconds
            }
        }
        handler.post(reminderRunnable!!)
    }
    
    private fun getTotalUsageToday(): Int {
        return try {
            val usageManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTime = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = currentTime
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            
            val usageStatsList = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                currentTime
            )
            
            var totalUsageSeconds = 0
            for (usageStat in usageStatsList) {
                val packageName = usageStat.packageName
                
                // Get the actual app display name
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    packageName
                }
                
                // Exclude specific apps by display name
                val excludedApps = setOf(
                    "System Launcher",
                    "GetYourLifeBack", 
                    "Google Play services",
                    "Settings"
                )
                
                // Also exclude by package name
                val excludedPackages = setOf(
                    "com.nextbillion.groww",
                    "com.oplus.dialer",
                    "com.fusionmedia.investing"
                )
                
                if (!excludedApps.contains(appName) && 
                    !excludedPackages.contains(packageName) && 
                    packageName != this.packageName) {
                    totalUsageSeconds += (usageStat.totalTimeInForeground / 1000L).toInt()
                }
            }
            totalUsageSeconds
        } catch (e: Exception) {
            0 // Fallback if usage stats unavailable
        }
    }
    
    private fun showDefaultReminder() {
        try {
            // Check if mindful modes are active - skip if they are
            val sessionManager = SessionManager(this)
            if (sessionManager.isSessionActive()) {
                return
            }
            
            // Check if Need Help session is active - skip if it is
            if (sessionManager.isNeedHelpActive()) {
                return
            }
            
            // Additional safety: Check if OTP overlay is currently showing
            if (overlayView != null && isProcessingOTP) {
                return
            }
            
            // Safety check - ensure overlay permission still exists
            if (!android.provider.Settings.canDrawOverlays(this)) {
                return
            }
            
            // Hide any existing overlay first
            hideOverlay()
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_reminder, null)
            
            val quoteText = overlayView?.findViewById<TextView>(R.id.quote_text)
            val dailyBudgetText = overlayView?.findViewById<TextView>(R.id.daily_budget_text)
            val moneySpentText = overlayView?.findViewById<TextView>(R.id.money_spent_text)
            val potentialSavingsText = overlayView?.findViewById<TextView>(R.id.potential_savings_text)
            val moneySavedText = overlayView?.findViewById<TextView>(R.id.money_saved_text)
            val needHelpButton = overlayView?.findViewById<View>(R.id.need_help_button)
            
            quoteText?.text = MotivationalQuotes.getRandomQuote()
            
            // Get real usage data before showing overlay
            val dailyBudget = 86400
            val moneySpentOnApps = getTotalUsageToday()
            
            // Calculate Potential Savings (seconds left in day)
            val currentTimeMillis = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = currentTimeMillis
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            val endOfDayMillis = calendar.timeInMillis
            val millisLeft = endOfDayMillis - currentTimeMillis
            val potentialSavings = (millisLeft / 1000L).toInt()
            
            // Calculate percentages with proper rounding to ensure they add up to 100%
            val spentPercentage = kotlin.math.round(moneySpentOnApps * 100.0 / dailyBudget).toInt()
            val savingsPercentage = kotlin.math.round(potentialSavings * 100.0 / dailyBudget).toInt()
            // Time Saved should be: Time elapsed today - Time wasted on apps
            val timeElapsedToday = dailyBudget - potentialSavings
            var savedPercentage = kotlin.math.round((timeElapsedToday - moneySpentOnApps) * 100.0 / dailyBudget).toInt()
            
            // Ensure percentages add up to 100% by adjusting the largest value
            val total = spentPercentage + savingsPercentage + savedPercentage
            if (total != 100) {
                savedPercentage += (100 - total) // Adjust Time Saved to make total = 100%
            }
            
            dailyBudgetText?.text = "100%"
            moneySpentText?.text = "${spentPercentage}%"
            potentialSavingsText?.text = "${savingsPercentage}%"
            moneySavedText?.text = "${savedPercentage}%"
            
            // Set up Need Help button click
            needHelpButton?.setOnClickListener {
                // Prevent instant clicks - enforce minimum viewing time
                if (System.currentTimeMillis() - overlayCreationTime < MIN_OVERLAY_DURATION) {
                    return@setOnClickListener
                }
                
                // Check daily Need Help limit - TEMPORARILY DISABLED
                /*
                if (dailyNeedHelpCount >= MAX_DAILY_NEED_HELP) {
                    showToast("Daily Need Help limit reached (${MAX_DAILY_NEED_HELP}/day). Try mindful breathing instead.")
                    return@setOnClickListener
                }
                */
                
                // Prevent concurrent sessions
                if (sessionManager.isNeedHelpActive()) {
                    return@setOnClickListener
                }
                
                // Increment daily count
                dailyNeedHelpCount++
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putInt(KEY_NEED_HELP_COUNT, dailyNeedHelpCount).apply()
                
                // Cancel existing auto-hide timer
                autoHideRunnable?.let { handler.removeCallbacks(it) }
                
                // Cancel default reminder timer during Need Help session
                reminderRunnable?.let { handler.removeCallbacks(it) }
                
                sessionManager.startNeedHelpSession()
                showNeedHelpOverlay()
            }
            
            overlayCreationTime = System.currentTimeMillis()
            windowManager?.addView(overlayView, layoutParams)
            
            // Auto-hide after 5 seconds
            autoHideRunnable = Runnable {
                hideOverlay()
            }
            handler.postDelayed(autoHideRunnable!!, 5000)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun showNeedHelpOverlay() {
        try {
            hideOverlay()
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_otp, null)
            
            val quoteText = overlayView?.findViewById<TextView>(R.id.quote_text)
            val timerText = overlayView?.findViewById<TextView>(R.id.timer_text)
            val otpInput = overlayView?.findViewById<EditText>(R.id.otp_input)
            val verifyButton = overlayView?.findViewById<View>(R.id.verify_otp_button)
            val sendButton = overlayView?.findViewById<View>(R.id.send_otp_button)
            
            quoteText?.text = MotivationalQuotes.getRandomQuote()
            timerText?.text = "15-minute break - Enter OTP to exit early"
            
            val otpManager = OTPManager(this)
            
            // Send OTP button
            sendButton?.setOnClickListener {
                if (isProcessingOTP) return@setOnClickListener
                
                // Rate limiting - prevent OTP spam
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastOTPRequestTime < OTP_REQUEST_COOLDOWN) {
                    val remainingTime = (OTP_REQUEST_COOLDOWN - (currentTime - lastOTPRequestTime)) / 1000
                    showToast("Please wait ${remainingTime}s before requesting another OTP")
                    return@setOnClickListener
                }
                
                val sessionManager = SessionManager(this)
                
                // One OTP per Need Help session
                if (sessionManager.isOTPSentForCurrentSession()) {
                    showToast("OTP already sent for this session. Use existing OTP or wait for session to end.")
                    return@setOnClickListener
                }
                
                isProcessingOTP = true
                lastOTPRequestTime = currentTime
                showToast("Sending OTP...")
                
                try {
                    otpManager.generateAndSendOTP { success, message ->
                        isProcessingOTP = false
                        if (success) {
                            sessionManager.markOTPSentForCurrentSession()
                        } else {
                            // Reset cooldown on failure
                            lastOTPRequestTime = 0L
                        }
                        showToast(message)
                    }
                } catch (e: Exception) {
                    isProcessingOTP = false
                    lastOTPRequestTime = 0L // Reset cooldown on error
                    showToast("Failed to send OTP: Network error")
                }
            }
            
            // Verify OTP button
            verifyButton?.setOnClickListener {
                if (isProcessingOTP) return@setOnClickListener
                
                // Limit OTP attempts
                if (otpAttempts >= 3) {
                    showToast("Too many failed attempts. Session will end automatically.")
                    // Auto-end session after max attempts
                    handler.postDelayed({
                        autoHideRunnable?.let { handler.removeCallbacks(it) }
                        hideOverlay()
                        SessionManager(this).endNeedHelpSession()
                        otpAttempts = 0
                        
                        // Restart default reminder timer after failed attempts
                        startDefaultReminders()
                    }, 2000)
                    return@setOnClickListener
                }
                
                try {
                    val enteredOTP = otpInput?.text?.toString()?.trim() ?: ""
                    
                    // Input validation
                    if (enteredOTP.isEmpty()) {
                        showToast("Please enter OTP")
                        return@setOnClickListener
                    }
                    
                    if (enteredOTP.length != 6 || !enteredOTP.all { it.isDigit() }) {
                        showToast("Please enter valid 6-digit OTP")
                        otpInput?.text?.clear()
                        return@setOnClickListener
                    }
                    
                    isProcessingOTP = true
                    
                    // Verify OTP with error handling
                    val isValid = try {
                        otpManager.validateOTP(enteredOTP)
                    } catch (e: Exception) {
                        showToast("OTP verification failed: ${e.message}")
                        false
                    }
                    
                    isProcessingOTP = false
                    
                    if (isValid) {
                        // OTP success - end Need Help session
                        autoHideRunnable?.let { handler.removeCallbacks(it) }
                        otpAttempts = 0 // Reset attempts on success
                        hideOverlay()
                        SessionManager(this).endNeedHelpSession()
                        
                        // Restart default reminder timer after successful OTP
                        startDefaultReminders()
                        
                        showToast("OTP verified! Session ended. Remaining today: ${MAX_DAILY_NEED_HELP - dailyNeedHelpCount}")
                    } else {
                        otpAttempts++
                        showToast("Invalid or expired OTP (${otpAttempts}/3 attempts)")
                        otpInput?.text?.clear()
                    }
                } catch (e: Exception) {
                    isProcessingOTP = false
                    otpAttempts++
                    showToast("Verification error: Please try again (${otpAttempts}/3 attempts)")
                    otpInput?.text?.clear()
                }
            }
            
            windowManager?.addView(overlayView, layoutParams)
            
            // Disable interactions for first few seconds
            sendButton?.isEnabled = false
            verifyButton?.isEnabled = false
            handler.postDelayed({
                sendButton?.isEnabled = true
                verifyButton?.isEnabled = true
            }, MIN_OVERLAY_DURATION)
            
            // Focus on OTP input and show numeric keyboard
            handler.postDelayed({
                otpInput?.requestFocus()
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                inputMethodManager.showSoftInput(otpInput, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
            }, 200)
            
            // Auto-hide after 15 minutes with cleanup
            autoHideRunnable = Runnable {
                hideOverlay()
                SessionManager(this).endNeedHelpSession()
                otpAttempts = 0 // Reset attempts when session ends
                
                // Restart default reminder timer after Need Help session ends
                startDefaultReminders()
            }
            handler.postDelayed(autoHideRunnable!!, 900000) // 15 minutes = 900,000ms
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Ensure cleanup on crash
            SessionManager(this).endNeedHelpSession()
            otpAttempts = 0
        }
    }
    
    private fun hideOverlay() {
        try {
            // Hide keyboard before removing overlay
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(overlayView?.windowToken, 0)
            
            overlayView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
        }
    }
    
    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return "${hours}h ${minutes}m"
    }
    
    private fun showToast(message: String) {
        try {
            handler.post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Fallback: ignore toast if context issues
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        reminderRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        hideOverlay()
        
        // Reset safety state
        isProcessingOTP = false
        otpAttempts = 0
        overlayCreationTime = 0L
        lastOTPRequestTime = 0L
        
        // Safety cleanup - end any active Need Help session
        try {
            SessionManager(this).endNeedHelpSession()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        isRunning = false
    }
}