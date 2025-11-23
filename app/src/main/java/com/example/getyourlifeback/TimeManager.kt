package com.example.getyourlifeback

import android.os.SystemClock
import kotlinx.coroutines.*
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

object TimeManager {
    private var networkTimeOffset = 0L
    private var lastSyncTime = 0L
    private var bootTimeOffset = 0L
    private var isUsingBootTime = false
    
    suspend fun getCurrentTime(): Long {
        return withContext(Dispatchers.IO) {
            try {
                // Sync network time if not synced in last 5 minutes
                if (System.currentTimeMillis() - lastSyncTime > 300000) {
                    syncNetworkTime()
                }
                
                if (!isUsingBootTime) {
                    // Return network-adjusted time
                    System.currentTimeMillis() + networkTimeOffset
                } else {
                    // Use boot time (tamper-proof)
                    SystemClock.elapsedRealtime() + bootTimeOffset
                }
            } catch (e: Exception) {
                // Switch to boot time if network fails
                switchToBootTime()
                SystemClock.elapsedRealtime() + bootTimeOffset
            }
        }
    }
    
    private suspend fun syncNetworkTime() {
        try {
            val connection = URL("https://worldtimeapi.org/api/timezone/UTC").openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val response = connection.getInputStream().bufferedReader().readText()
            val unixtime = response.substringAfter("\"unixtime\":").substringBefore(",").toLong()
            val networkTime = unixtime * 1000L
            
            networkTimeOffset = networkTime - System.currentTimeMillis()
            lastSyncTime = System.currentTimeMillis()
            isUsingBootTime = false
        } catch (e: Exception) {
            // Switch to boot time if network fails
            switchToBootTime()
        }
    }
    
    private fun switchToBootTime() {
        if (!isUsingBootTime) {
            bootTimeOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            isUsingBootTime = true
        }
    }
    
    fun getElapsedRealtime(): Long {
        return SystemClock.elapsedRealtime()
    }
}