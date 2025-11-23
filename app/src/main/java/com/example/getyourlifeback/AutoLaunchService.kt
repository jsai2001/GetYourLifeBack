package com.example.getyourlifeback

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AutoLaunchService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wait for system to be fully initialized (30 seconds)
        Handler(Looper.getMainLooper()).postDelayed({
            val sessionManager = SessionManager(this)
            if (sessionManager.isSessionActive()) {
                // Launch MainActivity as startup app
                val mainIntent = Intent(this, MainActivity::class.java)
                mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                 Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                 Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                mainIntent.putExtra("auto_launched", true)
                mainIntent.putExtra("startup_mode", true)
                startActivity(mainIntent)
            }
            stopSelf()
        }, 30000L) // 30 seconds for full system initialization
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}