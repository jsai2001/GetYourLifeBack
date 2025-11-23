package com.example.getyourlifeback

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking

class SessionManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("mindful_session", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_SESSION_END_TIME = "session_end_time"
        private const val KEY_FOCUS_DURATION = "focus_duration"
        private const val KEY_REMINDER_INTERVAL = "reminder_interval"
        private const val KEY_COOLDOWN_TIME = "cooldown_time"
        private const val KEY_IS_SPECIFIC_APPS_MODE = "is_specific_apps_mode"
        private const val KEY_SELECTED_APPS = "selected_apps"
    }
    
    fun startSession(
        focusDuration: Int,
        reminderInterval: Int,
        cooldownTime: Int,
        isSpecificAppsMode: Boolean,
        selectedApps: Set<String>
    ) {
        val currentTime = runBlocking { TimeManager.getCurrentTime() }
        val endTime = currentTime + (focusDuration * 60 * 1000L)
        
        prefs.edit().apply {
            putBoolean(KEY_SESSION_ACTIVE, true)
            putLong(KEY_SESSION_END_TIME, endTime)
            putInt(KEY_FOCUS_DURATION, focusDuration)
            putInt(KEY_REMINDER_INTERVAL, reminderInterval)
            putInt(KEY_COOLDOWN_TIME, cooldownTime)
            putBoolean(KEY_IS_SPECIFIC_APPS_MODE, isSpecificAppsMode)
            putStringSet(KEY_SELECTED_APPS, selectedApps)
            apply()
        }
    }
    
    fun isSessionActive(): Boolean {
        if (!prefs.getBoolean(KEY_SESSION_ACTIVE, false)) return false
        
        val endTime = prefs.getLong(KEY_SESSION_END_TIME, 0)
        val currentTime = runBlocking { TimeManager.getCurrentTime() }
        
        if (currentTime >= endTime) {
            endSession()
            return false
        }
        
        return true
    }
    
    fun getSessionEndTime(): Long = prefs.getLong(KEY_SESSION_END_TIME, 0)
    
    fun getRemainingTime(): Long {
        val endTime = prefs.getLong(KEY_SESSION_END_TIME, 0)
        val currentTime = runBlocking { TimeManager.getCurrentTime() }
        val remaining = endTime - currentTime
        return if (remaining > 0) remaining else 0
    }
    
    fun getSessionConfig(): SessionConfig? {
        if (!isSessionActive()) return null
        
        return SessionConfig(
            focusDuration = prefs.getInt(KEY_FOCUS_DURATION, 30),
            reminderInterval = prefs.getInt(KEY_REMINDER_INTERVAL, 15),
            cooldownTime = prefs.getInt(KEY_COOLDOWN_TIME, 5),
            isSpecificAppsMode = prefs.getBoolean(KEY_IS_SPECIFIC_APPS_MODE, false),
            selectedApps = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        )
    }
    
    fun endSession() {
        prefs.edit().clear().apply()
    }
    
    data class SessionConfig(
        val focusDuration: Int,
        val reminderInterval: Int,
        val cooldownTime: Int,
        val isSpecificAppsMode: Boolean,
        val selectedApps: Set<String>
    )
}