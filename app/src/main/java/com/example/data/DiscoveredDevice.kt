package com.example.data

data class DiscoveredDevice(
    val ip: String,
    val port: Int = 9999,
    val name: String,
    val status: String, // "active" | "blocked" | "offline" | "connecting"
    val blocked: Boolean,
    val lastSeen: Long = System.currentTimeMillis(),
    
    // Telemetry fields
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val activeApp: String = "GuardLink Shield",
    val ringerMode: String = "Normal Ringer",
    
    // Location / Geofencing fields
    val latitude: Double = 14.555060,
    val longitude: Double = 121.011993,
    val isGeofenceTriggered: Boolean = false,
    val ringRequested: Boolean = false,
    
    // Styling fields
    val lockTheme: String = "slate", // "slate" | "cyberpunk" | "stealth"
    val lockWallpaper: String = "",
    val lockWarningIcon: String = "lock", // "lock" | "biohazard" | "warning" | "hourglass"
    val cameraLens: String = "front",
    val deviceSpeaking: Boolean = false
)

data class ChatMessage(
    val id: String = "",
    val sender: String = "", // "admin" | "user"
    val message: String = "",
    val timestamp: Long = 0L
)

data class AdminLog(
    val id: String = "",
    val action: String = "", // "LOCK" | "UNLOCK" | "CAMERA_START" | "CAMERA_STOP" | "GEOFENCE_VIOLATION"
    val details: String = "",
    val timestamp: Long = 0L
)
