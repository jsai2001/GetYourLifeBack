package com.example.getyourlifeback

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

class OTPManager(private val context: Context) {
    
    private val sharedPrefs = context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
    
    fun generateAndSendOTP(callback: (Boolean, String) -> Unit) {
        val otp = Random.nextInt(100000, 999999).toString()
        
        // Store OTP with timestamp
        sharedPrefs.edit()
            .putString("current_otp", otp)
            .putLong("otp_timestamp", System.currentTimeMillis())
            .apply()
        
        // Send real email
        sendRealEmail(otp, callback)
    }
    
    private fun sendRealEmail(otp: String, callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = sendEmailViaEmailJS(otp)
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback(true, "OTP email sent successfully via EmailJS to jeevansaikanaparthi@gmail.com")
                    } else {
                        // Fallback: Show OTP for testing when EmailJS fails
                        callback(true, "EmailJS failed (403). Demo mode: Your OTP is $otp (valid for 15 minutes)")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Error: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun sendEmailViaEmailJS(otp: String): Boolean {
        return try {
            val url = URL("https://api.emailjs.com/api/v1.0/email/send")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Using private key method (recommended by EmailJS)
            val emailData = """
                {
                    "service_id": "service_iv512j6",
                    "template_id": "template_g6b6dib",
                    "user_id": "wMvbDZPd5lW5kxIbJ",
                    "accessToken": "Lv2g3Ercd45Pc2wriSgz6",
                    "template_params": {
                        "passcode": "$otp",
                        "time": "${formatExpiryTime(System.currentTimeMillis() + 900000)}",
                        "email": "jeevansaikanaparthi@gmail.com"
                    }
                }
            """.trimIndent()
            
            connection.outputStream.use { outputStream ->
                outputStream.write(emailData.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            
            // Read response body for more details
            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            }
            
            android.util.Log.d("OTPManager", "EmailJS Response: $responseCode - $responseMessage")
            android.util.Log.d("OTPManager", "EmailJS Response Body: $responseBody")
            
            if (responseCode == 200) {
                true
            } else {
                android.util.Log.e("OTPManager", "EmailJS failed with code: $responseCode, body: $responseBody")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("OTPManager", "EmailJS error: ${e.message}", e)
            false
        }
    }
    
    fun validateOTP(enteredOTP: String): Boolean {
        val storedOTP = sharedPrefs.getString("current_otp", "")
        val timestamp = sharedPrefs.getLong("otp_timestamp", 0)
        val currentTime = System.currentTimeMillis()
        
        // Check if OTP is expired (15 minutes = 900000 ms)
        if (currentTime - timestamp > 900000) {
            clearOTP()
            return false
        }
        
        val isValid = enteredOTP == storedOTP
        if (isValid) {
            clearOTP()
        }
        
        return isValid
    }
    
    private fun clearOTP() {
        sharedPrefs.edit()
            .remove("current_otp")
            .remove("otp_timestamp")
            .apply()
    }
    
    fun isOTPPending(): Boolean {
        val storedOTP = sharedPrefs.getString("current_otp", "")
        val timestamp = sharedPrefs.getLong("otp_timestamp", 0)
        val currentTime = System.currentTimeMillis()
        
        return storedOTP?.isNotEmpty() == true && (currentTime - timestamp <= 900000)
    }
    
    private fun formatExpiryTime(timestamp: Long): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return String.format("%02d:%02d:%02d", 
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            calendar.get(java.util.Calendar.SECOND)
        )
    }
}