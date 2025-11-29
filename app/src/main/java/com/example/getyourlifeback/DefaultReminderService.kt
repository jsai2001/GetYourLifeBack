package com.example.getyourlifeback

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class DefaultReminderService : Service() {
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reminderRunnable: Runnable? = null
    private var isRunning = false
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            startDefaultReminders()
            isRunning = true
            android.util.Log.d("DefaultReminder", "Service started - reminders will show every 60 seconds")
        }
        return START_STICKY
    }
    
    private fun startDefaultReminders() {
        reminderRunnable = object : Runnable {
            override fun run() {
                android.util.Log.d("DefaultReminder", "Showing reminder at ${System.currentTimeMillis()}")
                showDefaultReminder()
                handler.postDelayed(this, 60000) // Every 60 seconds
            }
        }
        handler.post(reminderRunnable!!)
    }
    
    private fun showDefaultReminder() {
        try {
            // Check if mindful modes are active - skip if they are
            val sessionManager = SessionManager(this)
            if (sessionManager.isSessionActive()) {
                android.util.Log.d("DefaultReminder", "Skipping reminder - mindful mode active")
                return
            }
            
            // Hide any existing overlay first
            hideOverlay()
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_reminder, null)
            
            val quoteText = overlayView?.findViewById<TextView>(R.id.quote_text)
            val warningText = overlayView?.findViewById<TextView>(R.id.warning_text)
            val timerText = overlayView?.findViewById<TextView>(R.id.timer_text)
            
            quoteText?.text = "🌱 Take a mindful breath and reflect"
            warningText?.text = "📱 MINDFUL MOMENT"
            timerText?.text = "5-second awareness break"
            
            windowManager?.addView(overlayView, layoutParams)
            
            // Auto-hide after 5 seconds
            handler.postDelayed({
                hideOverlay()
            }, 5000)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun hideOverlay() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        reminderRunnable?.let { handler.removeCallbacks(it) }
        hideOverlay()
        isRunning = false
        android.util.Log.d("DefaultReminder", "Service destroyed")
    }
}