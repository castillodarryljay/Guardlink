package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.data.StateManager
import com.example.ui.screens.RoleSelectScreen
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.AdminSettingsScreen
import com.example.ui.user.UserHomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize state manager SharedPreferences
        StateManager.init(applicationContext)

        // Initialize Text-To-Speech engine early
        try {
            com.example.tts.TextToSpeechManager.init(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Make sure device name is initialized
        if (StateManager.deviceName.value.isEmpty()) {
            StateManager.setDeviceName(Build.MODEL)
        }

        // Start foreground telemetry service automatically on launch
        try {
            val serviceIntent = android.content.Intent(applicationContext, com.example.service.GuardLinkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Request runtime permissions for notifications, camera, microphone, and precise GPS location
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (requiredPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, requiredPermissions.toTypedArray(), 101)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    GuardLinkApp()
                }
            }
        }
    }
}

@Composable
fun GuardLinkApp() {
    var currentRoute by remember { mutableStateOf("dashboard") } // "dashboard" or "settings"

    if (currentRoute == "dashboard") {
        AdminDashboardScreen(
            onNavigateToSettings = { currentRoute = "settings" }
        )
    } else {
        androidx.activity.compose.BackHandler {
            currentRoute = "dashboard"
        }
        AdminSettingsScreen(
            onNavigateToDashboard = { currentRoute = "dashboard" }
        )
    }
}
