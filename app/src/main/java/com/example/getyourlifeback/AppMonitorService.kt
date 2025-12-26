package com.example.getyourlifeback

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AppMonitorService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null
    private var selectedApps = setOf<String>()
    private var overlayService: OverlayService? = null
    
    companion object {
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                selectedApps = intent.getStringArrayListExtra("selectedApps")?.toSet() ?: emptySet()
                startMonitoring()
            }
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        return START_STICKY
    }
    
    private fun startMonitoring() {
        monitorRunnable = object : Runnable {
            override fun run() {
                checkCurrentApp()
                handler.postDelayed(this, 1000) // Check every second
            }
        }
        handler.post(monitorRunnable!!)
    }
    
    private fun checkCurrentApp() {
        // Check if Need Help session is active - skip monitoring if it is
        val sessionManager = SessionManager(this)
        if (sessionManager.isNeedHelpActive()) {
            return
        }
        
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = activityManager.getRunningTasks(1)
        
        if (runningTasks.isNotEmpty()) {
            val currentApp = runningTasks[0].topActivity?.packageName
            
            if (currentApp in selectedApps) {
                // Trigger overlay for this specific app
                val intent = Intent(this, OverlayService::class.java)
                intent.action = "SHOW_REMINDER_FOR_APP"
                intent.putExtra("appPackage", currentApp)
                startService(intent)
            }
        }
    }
    
    private fun stopMonitoring() {
        monitorRunnable?.let { handler.removeCallbacks(it) }
        stopSelf()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        monitorRunnable?.let { handler.removeCallbacks(it) }
    }
}