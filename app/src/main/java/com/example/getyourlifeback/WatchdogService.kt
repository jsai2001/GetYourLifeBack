package com.example.getyourlifeback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class WatchdogService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startWatchdog()
        return START_STICKY
    }
    
    private fun startWatchdog() {
        watchdogRunnable = object : Runnable {
            override fun run() {
                val sessionManager = SessionManager(this@WatchdogService)
                if (sessionManager.isSessionActive()) {
                    // Only restart if PersistentService is not running
                    if (!isServiceRunning(PersistentService::class.java)) {
                        val config = sessionManager.getSessionConfig()
                        config?.let {
                            val serviceIntent = Intent(this@WatchdogService, PersistentService::class.java)
                            serviceIntent.putExtra("focusDuration", it.focusDuration)
                            serviceIntent.putExtra("reminderInterval", it.reminderInterval)
                            serviceIntent.putExtra("cooldownTime", it.cooldownTime)
                            serviceIntent.putExtra("isSpecificAppsMode", it.isSpecificAppsMode)
                            serviceIntent.putStringArrayListExtra("selectedApps", ArrayList(it.selectedApps))
                            startForegroundService(serviceIntent)
                        }
                    }
                } else {
                    stopSelf()
                }
                handler.postDelayed(this, 10000L) // Check every 10 seconds
            }
        }
        handler.post(watchdogRunnable!!)
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any {
            serviceClass.name == it.service.className
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        
        // Restart watchdog if session is active
        val sessionManager = SessionManager(this)
        if (sessionManager.isSessionActive()) {
            val restartIntent = Intent(this, WatchdogService::class.java)
            startService(restartIntent)
        }
    }
}