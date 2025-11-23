package com.example.getyourlifeback

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.Calendar

class AppUsageStatsManager(private val context: Context) {
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    
    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val totalTimeInForeground: Long,
        val lastTimeUsed: Long
    )
    
    fun getDailyUsageStats(): List<AppUsageInfo> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        return usageStats.filter { it.totalTimeInForeground > 0 }
            .map { stats ->
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(stats.packageName, 0)
                    ).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    stats.packageName
                }
                
                AppUsageInfo(
                    packageName = stats.packageName,
                    appName = appName,
                    totalTimeInForeground = stats.totalTimeInForeground,
                    lastTimeUsed = stats.lastTimeUsed
                )
            }
            .sortedByDescending { it.totalTimeInForeground }
    }
    
    private fun isSystemApp(packageName: String): Boolean {
        val systemApps = setOf(
            "com.example.getyourlifeback", // Our own app
            "com.google.android.gms", // Google Play Services
            "com.android.vending", // Play Store
            "com.android.settings", // Settings
            "com.android.systemui", // System UI
            "com.android.launcher", // Default launcher
            "com.android.launcher3", // Pixel launcher
            "com.android.phone", // Phone app
            "com.android.contacts", // Contacts
            "com.android.dialer", // Dialer
            "com.android.messaging", // Messages
            "com.android.camera2", // Camera
            "com.android.gallery3d", // Gallery
            "com.android.calculator2", // Calculator
            "com.android.calendar", // Calendar
            "com.android.deskclock", // Clock
            "com.google.android.inputmethod.latin", // Gboard
            "com.android.inputmethod.latin" // Default keyboard
        )
        
        return packageName in systemApps || 
               packageName.startsWith("android.") ||
               (packageName.startsWith("com.android.") && !packageName.contains("chrome"))
    }
    
    fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}