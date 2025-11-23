package com.example.getyourlifeback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

class OnePlusStartupHelper(private val context: Context) {
    
    fun enableAutoStart() {
        Toast.makeText(context, "Follow these steps:\n1. Battery → Don't optimize\n2. Recent apps → Lock app\n3. Settings → Privacy → Special app access → Device admin apps", Toast.LENGTH_LONG).show()
        
        // Direct to battery optimization
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${context.packageName}")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}