package com.example.getyourlifeback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class PersistentService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private var appRestartRunnable: Runnable? = null
    private var sessionManager: SessionManager? = null
    
    companion object {
        const val CHANNEL_ID = "mindful_mode_channel"
        const val NOTIFICATION_ID = 1
    }
    
    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if session is still active before starting
        if (!sessionManager!!.isSessionActive()) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Get session config if no intent provided (restart scenario)
        val config = sessionManager!!.getSessionConfig()
        val focusDuration = intent?.getIntExtra("focusDuration", config?.focusDuration ?: 30) ?: 30
        val reminderInterval = intent?.getIntExtra("reminderInterval", config?.reminderInterval ?: 15) ?: 15
        val cooldownTime = intent?.getIntExtra("cooldownTime", config?.cooldownTime ?: 5) ?: 5
        val isSpecificAppsMode = intent?.getBooleanExtra("isSpecificAppsMode", config?.isSpecificAppsMode ?: false) ?: false
        val selectedApps = intent?.getStringArrayListExtra("selectedApps") ?: ArrayList(config?.selectedApps ?: emptySet())
        
        // Start overlay service
        val overlayIntent = Intent(this, OverlayService::class.java)
        overlayIntent.action = OverlayService.ACTION_START_OVERLAY
        overlayIntent.putExtra("focusDuration", focusDuration)
        overlayIntent.putExtra("reminderInterval", reminderInterval)
        overlayIntent.putExtra("cooldownTime", cooldownTime)
        overlayIntent.putExtra("isSpecificAppsMode", isSpecificAppsMode)
        overlayIntent.putStringArrayListExtra("selectedApps", selectedApps)
        startService(overlayIntent)
        
        // Monitor app and restart if needed
        startAppMonitoring()
        
        // Start watchdog service
        val watchdogIntent = Intent(this, WatchdogService::class.java)
        startService(watchdogIntent)
        
        return START_STICKY
    }
    
    private fun startAppMonitoring() {
        appRestartRunnable = object : Runnable {
            override fun run() {
                // Check if MainActivity is running, if not restart it
                if (sessionManager?.isSessionActive() == true && !isMainActivityRunning()) {
                    restartMainActivity()
                }
                handler.postDelayed(this, 5000L) // Check every 5 seconds
            }
        }
        handler.post(appRestartRunnable!!)
    }
    
    private fun isMainActivityRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val tasks = activityManager.getRunningTasks(10)
        return tasks.any { it.topActivity?.className == MainActivity::class.java.name }
    }
    
    private fun restartMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mindful Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps mindful mode running in background"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🧘 Mindful Mode Active")
            .setContentText("Digital wellness session in progress")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Restart both services when app is removed from recent apps
        if (sessionManager?.isSessionActive() == true) {
            val restartIntent = Intent(this, PersistentService::class.java)
            startForegroundService(restartIntent)
            
            val watchdogIntent = Intent(this, WatchdogService::class.java)
            startService(watchdogIntent)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        appRestartRunnable?.let { handler.removeCallbacks(it) }
        
        // Always restart both services if session is active
        if (sessionManager?.isSessionActive() == true) {
            val restartIntent = Intent(this, PersistentService::class.java)
            startForegroundService(restartIntent)
            
            val watchdogIntent = Intent(this, WatchdogService::class.java)
            startService(watchdogIntent)
        }
    }
}