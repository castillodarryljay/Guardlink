package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.StateManager
import com.example.service.GuardLinkService

class GuardLinkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        Log.i("GuardReceiver", "Received broadcast action: $action")
        
        // Initialize StateManager SharedPreferences if not already ready
        try {
            StateManager.init(context.applicationContext)
        } catch (e: Exception) {
            Log.e("GuardReceiver", "Failed initializing StateManager in receiver", e)
        }
        
        val role = StateManager.role.value
        Log.i("GuardReceiver", "Current saved role detected: $role")
        
        if (role == "user") {
            val isRunning = GuardLinkService.isServiceRunning.value
            Log.i("GuardReceiver", "User role holds service status running = $isRunning")
            if (!isRunning) {
                Log.i("GuardReceiver", "Service is not running! Waking up GuardLinkService...")
                try {
                    val serviceIntent = Intent(context, GuardLinkService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("GuardReceiver", "Failed to start GuardLinkService from broadcast receiver", e)
                }
            } else {
                Log.d("GuardReceiver", "GuardLinkService is already active and healthy.")
            }
        }
    }
}
