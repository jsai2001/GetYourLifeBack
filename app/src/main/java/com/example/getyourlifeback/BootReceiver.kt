package com.example.getyourlifeback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val sessionManager = SessionManager(context)
                
                // Always start watchdog service for monitoring
                val watchdogIntent = Intent(context, WatchdogService::class.java)
                context.startService(watchdogIntent)
                
                if (sessionManager.isSessionActive()) {
                    // Resume active session after boot/update
                    val config = sessionManager.getSessionConfig()
                    config?.let {
                        val serviceIntent = Intent(context, PersistentService::class.java)
                        serviceIntent.putExtra("focusDuration", it.focusDuration)
                        serviceIntent.putExtra("reminderInterval", it.reminderInterval)
                        serviceIntent.putExtra("cooldownTime", it.cooldownTime)
                        serviceIntent.putExtra("isSpecificAppsMode", it.isSpecificAppsMode)
                        serviceIntent.putStringArrayListExtra("selectedApps", ArrayList(it.selectedApps))
                        context.startForegroundService(serviceIntent)
                        
                        // Auto-launch app after system initialization
                        val autoLaunchIntent = Intent(context, AutoLaunchService::class.java)
                        context.startService(autoLaunchIntent)
                    }
                }
            }
        }
    }
}