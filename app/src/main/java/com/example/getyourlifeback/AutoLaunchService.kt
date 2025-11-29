package com.example.getyourlifeback

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AutoLaunchService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wait for system to be fully initialized (10 seconds for testing)
        Handler(Looper.getMainLooper()).postDelayed({
            // Always launch for testing (remove session check temporarily)
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                             Intent.FLAG_ACTIVITY_CLEAR_TOP or
                             Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            mainIntent.putExtra("auto_launched", true)
            mainIntent.putExtra("startup_mode", true)
            startActivity(mainIntent)
            stopSelf()
        }, 10000L) // 10 seconds for testing
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}