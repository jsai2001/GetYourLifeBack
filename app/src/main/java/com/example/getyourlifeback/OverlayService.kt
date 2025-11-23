package com.example.getyourlifeback

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var reminderView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reminderRunnable: Runnable? = null
    private var hideReminderRunnable: Runnable? = null
    private var stopServiceRunnable: Runnable? = null
    private var focusDuration = 30 // minutes
    private var reminderInterval = 15 // seconds
    private var cooldownTime = 5 // seconds
    private var isSpecificAppsMode = false
    private var selectedApps = setOf<String>()
    private var lastReminderTime = 0L
    private var monitoringRunnable: Runnable? = null
    private var blockedApps = mutableMapOf<String, Long>() // packageName to unblock time
    private var blockCheckRunnable: Runnable? = null
    private var isServiceStarted = false
    
    companion object {
        const val ACTION_START_OVERLAY = "START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "STOP_OVERLAY"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OVERLAY -> {
                if (isServiceStarted) return START_STICKY
                
                val sessionManager = SessionManager(this)
                if (!sessionManager.isSessionActive()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                
                isServiceStarted = true
                focusDuration = intent.getIntExtra("focusDuration", 30)
                reminderInterval = intent.getIntExtra("reminderInterval", 15)
                cooldownTime = intent.getIntExtra("cooldownTime", 5)
                isSpecificAppsMode = intent.getBooleanExtra("isSpecificAppsMode", false)
                selectedApps = intent.getStringArrayListExtra("selectedApps")?.toSet() ?: emptySet()
                
                if (isSpecificAppsMode) {
                    startAppSpecificMode()
                } else {
                    startOverlayReminders()
                }
            }
            ACTION_STOP_OVERLAY -> stopOverlayReminders()
        }
        return START_STICKY
    }
    
    private fun startOverlayReminders() {
        createStatusOverlay()
        scheduleReminders()
    }
    
    private fun stopOverlayReminders() {
        reminderRunnable?.let { handler.removeCallbacks(it) }
        hideReminderRunnable?.let { handler.removeCallbacks(it) }
        stopServiceRunnable?.let { handler.removeCallbacks(it) }
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        blockCheckRunnable?.let { handler.removeCallbacks(it) }
        blockedApps.clear()
        removeAllOverlays()
        isServiceStarted = false
        stopSelf()
    }
    
    private fun createStatusOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        overlayView = LayoutInflater.from(this).inflate(
            android.R.layout.simple_list_item_1, null
        ).apply {
            findViewById<TextView>(android.R.id.text1).apply {
                text = if (isSpecificAppsMode) {
                    "📱 Focus: ${focusDuration}m | Apps: ${selectedApps.size}"
                } else {
                    "📱 Focus: ${focusDuration}m | Whole Mobile"
                }
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setPadding(20, 10, 20, 10)
            }
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 100
        }
        
        windowManager?.addView(overlayView, params)
    }
    
    private fun scheduleReminders() {
        val sessionManager = SessionManager(this@OverlayService)
        val remainingTime = sessionManager.getRemainingTime()
        
        if (remainingTime <= 0) {
            sessionManager.endSession()
            stopOverlayReminders()
            return
        }
        
        reminderRunnable = object : Runnable {
            override fun run() {
                showReminderOverlay()
                handler.postDelayed(this, reminderInterval * 1000L)
            }
        }
        handler.post(reminderRunnable!!)
        
        stopServiceRunnable = Runnable {
            sessionManager.endSession()
            stopOverlayReminders()
        }
        handler.postDelayed(stopServiceRunnable!!, remainingTime)
    }
    
    private fun showReminderOverlay() {
        reminderView?.let { windowManager?.removeView(it) }
        
        reminderView = LayoutInflater.from(this).inflate(
            R.layout.overlay_reminder, null
        ).apply {
            findViewById<TextView>(R.id.quote_text).apply {
                val randomQuote = MotivationalQuotes.getRandomQuote()
                text = "💡 $randomQuote"
            }
            
            findViewById<TextView>(R.id.timer_text).apply {
                text = "⏱️ Take a ${cooldownTime}-second break!"
            }
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        windowManager?.addView(reminderView, params)
        
        hideReminderRunnable = Runnable {
            reminderView?.let { windowManager?.removeView(it) }
            reminderView = null
        }
        handler.postDelayed(hideReminderRunnable!!, cooldownTime * 1000L)
    }
    
    private fun startAppSpecificMode() {
        createStatusOverlay()
        startAppMonitoring()
    }
    
    private fun startAppMonitoring() {
        lastReminderTime = System.currentTimeMillis()
        
        monitoringRunnable = object : Runnable {
            override fun run() {
                checkCurrentAppAndShowReminder()
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(monitoringRunnable!!)
        
        blockCheckRunnable = object : Runnable {
            override fun run() {
                checkAndBlockApps()
                handler.postDelayed(this, 200L)
            }
        }
        handler.post(blockCheckRunnable!!)
        
        val sessionManager = SessionManager(this@OverlayService)
        val remainingTime = sessionManager.getRemainingTime()
        
        if (remainingTime <= 0) {
            sessionManager.endSession()
            stopOverlayReminders()
            return
        }
        
        stopServiceRunnable = Runnable {
            sessionManager.endSession()
            stopOverlayReminders()
        }
        handler.postDelayed(stopServiceRunnable!!, remainingTime)
    }
    
    private fun checkCurrentAppAndShowReminder() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            currentTime - 2000,
            currentTime
        )
        
        val currentApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        
        if (currentApp in selectedApps && 
            (currentTime - lastReminderTime) >= (reminderInterval * 1000L)) {
            
            showReminderOverlay()
            lastReminderTime = currentTime
            
            currentApp?.let { packageName ->
                killApp(packageName)
                blockApp(packageName, currentTime + (cooldownTime * 1000L))
            }
        }
    }
    
    private fun killApp(packageName: String) {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        activityManager.killBackgroundProcesses(packageName)
        
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
    }
    
    private fun blockApp(packageName: String, unblockTime: Long) {
        blockedApps[packageName] = unblockTime
    }
    
    private fun checkAndBlockApps() {
        val currentTime = System.currentTimeMillis()
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            currentTime - 1000,
            currentTime
        )
        
        val currentApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        
        currentApp?.let { packageName ->
            val unblockTime = blockedApps[packageName]
            if (unblockTime != null && currentTime < unblockTime) {
                killApp(packageName)
            } else if (unblockTime != null && currentTime >= unblockTime) {
                blockedApps.remove(packageName)
            }
        }
    }
    
    private fun removeAllOverlays() {
        overlayView?.let { windowManager?.removeView(it) }
        reminderView?.let { windowManager?.removeView(it) }
        overlayView = null
        reminderView = null
    }
}