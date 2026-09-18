package com.example.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiscoveredDevice
import com.example.data.StateManager
import com.example.network.GuardWebSocketClient
import com.example.network.NetworkScanner
import com.example.network.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject

class AdminDashboardViewModel : ViewModel() {
    private val _firebaseDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices = _firebaseDevices

    private val _firebaseCameraFeeds = MutableStateFlow<Map<String, String>>(emptyMap())
    val cameraFeeds = _firebaseCameraFeeds

    private val _firebaseScreenFeeds = MutableStateFlow<Map<String, String>>(emptyMap())
    val screenFeeds = _firebaseScreenFeeds

    private val _firebaseCameraAudio = MutableStateFlow<Map<String, String>>(emptyMap())
    val cameraAudio = _firebaseCameraAudio

    val isScanning = MutableStateFlow(false)
    val scanProgress = MutableStateFlow(1.0f)
    val scanProgressText = MutableStateFlow("Cloud Sync Active")

    fun startSubnetScan(context: Context) {
        // Core initialization of Firebase Admin
        try {
            FirebaseManager.initAdmin(context.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        viewModelScope.launch {
            // Attach state sync for Firebase live nodes
            launch {
                FirebaseManager.onlineDevices.collect { onlineList ->
                    _firebaseDevices.value = onlineList
                }
            }
            launch {
                FirebaseManager.activeCameraFeeds.collect { feedMap ->
                    _firebaseCameraFeeds.value = feedMap
                }
            }
            launch {
                FirebaseManager.activeScreenFeeds.collect { screenMap ->
                    _firebaseScreenFeeds.value = screenMap
                }
            }
            launch {
                FirebaseManager.activeCameraAudio.collect { audioMap ->
                    _firebaseCameraAudio.value = audioMap
                }
            }
        }
    }

    fun blockScreen(ip: String, message: String, password: String, timerSeconds: Long = -1L, imageBase64: String = "") {
        FirebaseManager.adminBlockDevice(ip, message, password, timerSeconds, imageBase64)
    }

    fun unblockScreen(ip: String) {
        FirebaseManager.adminUnblockDevice(ip)
    }

    fun toggleRing(ip: String, value: Boolean) {
        FirebaseManager.adminToggleRingDevice(ip, value)
    }

    fun retryConnect(ip: String) {
        // No-op for online Firebase devices
    }

    fun refreshAll(context: Context) {
        viewModelScope.launch {
            if (isScanning.value) return@launch
            isScanning.value = true
            
            // Re-initialize Firebase Admin connection to ensure we listen to latest status updates
            try {
                FirebaseManager.initAdmin(context.applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Increment progress dynamically for visual feedback
            val steps = 15
            for (i in 0..steps) {
                val progressValue = i.toFloat() / steps
                scanProgress.value = progressValue
                scanProgressText.value = when {
                    progressValue < 0.3f -> "Connecting to Firebase..."
                    progressValue < 0.6f -> "Querying paired devices..."
                    progressValue < 0.9f -> "Synchronizing state variables..."
                    else -> "Cloud Sync Complete!"
                }
                delay(80) // ~1.2s total duration
            }
            
            isScanning.value = false
        }
    }

    fun addManualDevice(ip: String) {
        // No-op for online Firebase devices
    }

    fun removeManualDevice(ip: String) {
        // No-op for online Firebase devices
    }

    fun removeDevice(ip: String) {
        FirebaseManager.adminRemoveDevice(ip)
    }

    fun clearAllDevices() {
        FirebaseManager.adminClearAllDevices()
    }

    fun startCameraStream(ip: String) {
        FirebaseManager.adminStartCamera(ip)
    }

    fun stopCameraStream(ip: String) {
        FirebaseManager.adminStopCamera(ip)
    }

    fun startViewingScreen(deviceId: String) {
        FirebaseManager.requestScreenStream(deviceId, true)
        FirebaseManager.writeDeviceLog(deviceId, "SCREEN_START", "Remote screen viewing started.")
    }

    fun stopViewingScreen(deviceId: String) {
        FirebaseManager.requestScreenStream(deviceId, false)
        FirebaseManager.writeDeviceLog(deviceId, "SCREEN_STOP", "Remote screen viewing stopped.")
    }

    fun setCameraLens(deviceId: String, lens: String) {
        FirebaseManager.adminSetCameraLens(deviceId, lens)
    }

    fun updateLockStyle(deviceId: String, theme: String, wallpaperUrl: String, warningIcon: String) {
        FirebaseManager.adminUpdateLockStyle(deviceId, theme, wallpaperUrl, warningIcon)
    }

    fun configureGeofence(deviceId: String, enabled: Boolean, baseLat: Double, baseLng: Double, radiusMeters: Double) {
        FirebaseManager.adminConfigureGeofence(deviceId, enabled, baseLat, baseLng, radiusMeters)
    }

    fun sendAdminMessage(deviceId: String, messageText: String) {
        FirebaseManager.sendChatMessage(deviceId, "admin", messageText)
    }

    fun clearLogs(deviceId: String) {
        FirebaseManager.adminClearLogs(deviceId)
    }

    fun cleanupClients() {
        // No-op for online Firebase devices
    }

    fun getSchedules(): List<com.example.data.LockSchedule> {
        return StateManager.getSchedulesFromPrefs()
    }

    fun addSchedule(schedule: com.example.data.LockSchedule) {
        val current = StateManager.getSchedulesFromPrefs().toMutableList()
        current.removeAll { it.id == schedule.id }
        current.add(schedule)
        StateManager.saveSchedules(current)
        com.example.network.FirebaseManager.saveScheduleToFirebase(schedule)
    }

    fun updateSchedule(schedule: com.example.data.LockSchedule) {
        val current = StateManager.getSchedulesFromPrefs().toMutableList()
        val index = current.indexOfFirst { it.id == schedule.id }
        if (index != -1) {
            current[index] = schedule
        } else {
            current.add(schedule)
        }
        StateManager.saveSchedules(current)
        com.example.network.FirebaseManager.saveScheduleToFirebase(schedule)
    }

    fun deleteSchedule(id: String) {
        val current = StateManager.getSchedulesFromPrefs().filter { it.id != id }
        StateManager.saveSchedules(current)
        com.example.network.FirebaseManager.deleteScheduleFromFirebase(id)
    }

    fun toggleSchedule(id: String, enabled: Boolean) {
        val current = StateManager.getSchedulesFromPrefs().map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        StateManager.saveSchedules(current)
        com.example.network.FirebaseManager.toggleScheduleInFirebase(id, enabled)
    }

    override fun onCleared() {
        try {
            FirebaseManager.cleanupAdmin()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onCleared()
    }
}
