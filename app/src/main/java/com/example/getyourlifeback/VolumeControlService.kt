package com.example.getyourlifeback

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class VolumeControlService : Service() {
    
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var volumeCheckRunnable: Runnable? = null
    private var isExternalDeviceConnected = false
    
    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    isExternalDeviceConnected = state == 1
                    android.util.Log.d("VolumeControl", "Headset connected: $isExternalDeviceConnected")
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                    isExternalDeviceConnected = state == AudioManager.SCO_AUDIO_STATE_CONNECTED
                    android.util.Log.d("VolumeControl", "Bluetooth SCO connected: $isExternalDeviceConnected")
                }
                "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" -> {
                    val state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0)
                    isExternalDeviceConnected = state == 2 // BluetoothProfile.STATE_CONNECTED
                    android.util.Log.d("VolumeControl", "Bluetooth A2DP connected: $isExternalDeviceConnected")
                }
            }
            
            if (isExternalDeviceConnected) {
                startVolumeMonitoring()
            } else {
                stopVolumeMonitoring()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerAudioDeviceReceiver()
        checkInitialAudioState()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun registerAudioDeviceReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        }
        registerReceiver(audioDeviceReceiver, filter)
    }
    
    private fun checkInitialAudioState() {
        // Check if external device is already connected
        isExternalDeviceConnected = audioManager.isWiredHeadsetOn || 
                                   audioManager.isBluetoothScoOn || 
                                   audioManager.isBluetoothA2dpOn
        
        if (isExternalDeviceConnected) {
            startVolumeMonitoring()
        }
    }
    
    private fun startVolumeMonitoring() {
        volumeCheckRunnable = object : Runnable {
            override fun run() {
                enforceVolumeLimit()
                handler.postDelayed(this, 500) // Check every 500ms
            }
        }
        handler.post(volumeCheckRunnable!!)
        android.util.Log.d("VolumeControl", "Started volume monitoring")
    }
    
    private fun stopVolumeMonitoring() {
        volumeCheckRunnable?.let { handler.removeCallbacks(it) }
        android.util.Log.d("VolumeControl", "Stopped volume monitoring")
    }
    
    private fun enforceVolumeLimit() {
        if (!isExternalDeviceConnected) return
        
        // Check media volume
        val maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxAllowedMedia = (maxMediaVolume * 0.7).toInt()
        
        if (currentMediaVolume > maxAllowedMedia) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxAllowedMedia, 0)
            android.util.Log.d("VolumeControl", "Media volume limited to 70%: $maxAllowedMedia")
        }
        
        // Check call volume
        val maxCallVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val currentCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val maxAllowedCall = (maxCallVolume * 0.7).toInt()
        
        if (currentCallVolume > maxAllowedCall) {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxAllowedCall, 0)
            android.util.Log.d("VolumeControl", "Call volume limited to 70%: $maxAllowedCall")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(audioDeviceReceiver)
        stopVolumeMonitoring()
    }
}