package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.DiscoveredDevice
import com.example.data.StateManager
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private val database: FirebaseDatabase by lazy {
        val db = try {
            // Point explicitly to the regional Realtime Database URL
            FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.asia-southeast1.firebasedatabase.app/")
        } catch (e: Exception) {
            try {
                FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.firebaseio.com/")
            } catch (ex: Exception) {
                FirebaseDatabase.getInstance()
            }
        }
        try {
            db.setPersistenceEnabled(true)
            Log.d(TAG, "FirebaseDatabase setPersistenceEnabled(true) succeeded.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed setPersistenceEnabled on Firebase Realtime DB", e)
        }
        db
    }
    
    // Admin structures
    private val _onlineDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val onlineDevices = _onlineDevices.asStateFlow()

    private val _activeCameraFeeds = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeCameraFeeds = _activeCameraFeeds.asStateFlow()

    private val _activeScreenFeeds = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeScreenFeeds = _activeScreenFeeds.asStateFlow()

    private val _activeCameraAudio = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeCameraAudio = _activeCameraAudio.asStateFlow()

    // Real-time server connection status flow
    val isFirebaseConnected = MutableStateFlow(false)

    private var devicesListener: ValueEventListener? = null
    
    // User structures
    val pairingCode = MutableStateFlow<String?>(null)
    val isPaired = MutableStateFlow(false)
    val adminName = MutableStateFlow("")
    var isCurrentAdminSpeaking = false
    val isAdminSpeaking = MutableStateFlow(false)
    var currentMyDeviceId: String = ""

    private var userDeviceListener: ValueEventListener? = null
    private var pairingListener: ValueEventListener? = null
    private var userScope: CoroutineScope? = null
    private var adminScope: CoroutineScope? = null
    private var activeLocationListener: android.location.LocationListener? = null

    // SharedPreferences keys for Firebase Pairing
    private const val PREFS_FIREBASE = "guardlink_firebase_prefs"
    private const val KEY_PAIRED_DEVICE_ID = "firebase_paired_device_id"
    private const val KEY_PAIRED_ADMIN_ID = "firebase_paired_admin_id"
    private const val KEY_PAIRED_ADMIN_NAME = "firebase_paired_admin_name"

    // Reconnection monitoring state
    private var isConnectivityCallbackRegistered = false
    private var infoConnectedListener: ValueEventListener? = null

    fun registerConnectivityObserver(context: Context) {
        if (isConnectivityCallbackRegistered) return
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (connectivityManager != null) {
                val request = android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                
                connectivityManager.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        super.onAvailable(network)
                        Log.i(TAG, "[AUTO-RECONNECT] Device connected to the Internet! Forcing Firebase DATABASE ONLINE...")
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                database.goOnline()
                                Log.i(TAG, "[AUTO-RECONNECT] Firebase DATABASE forced online successfully on available connection.")
                            } catch (e: Exception) {
                                Log.e(TAG, "[AUTO-RECONNECT] Failed database.goOnline on network restore", e)
                            }
                        }
                    }

                    override fun onLost(network: android.net.Network) {
                        super.onLost(network)
                        Log.w(TAG, "[AUTO-RECONNECT] Network connection lost on device.")
                    }
                })
                isConnectivityCallbackRegistered = true
                Log.d(TAG, "Network connectivity registered successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    fun startInfoConnectedListener() {
        if (infoConnectedListener != null) return
        try {
            val connectedRef = database.getReference(".info/connected")
            infoConnectedListener = connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    isFirebaseConnected.value = connected
                    Log.i(TAG, "[FEEDBACK] Firebase info connection trace toggled: connected = $connected")
                    if (!connected) {
                        // Nudge the WebSocket connection online immediately
                        Log.w(TAG, "[FEEDBACK] Disconnected from Firebase Server. Nudging online...")
                        try {
                            database.goOnline()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed database.goOnline during info connection nudge", e)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Listener for .info/connected cancelled", error.toException())
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error starting info connection listener", e)
        }
    }

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_FIREBASE, Context.MODE_PRIVATE)
        var id = prefs.getString("my_permanent_device_uuid", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("my_permanent_device_uuid", id).apply()
        }
        return id
    }

    fun getOrCreateAdminId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_FIREBASE, Context.MODE_PRIVATE)
        var id = prefs.getString("admin_uuid", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("admin_uuid", id).apply()
        }
        return id
    }

    fun initApp(context: Context) {
        registerConnectivityObserver(context)
        startInfoConnectedListener()

        try {
            database.goOnline()
        } catch (e: Exception) {
            Log.e(TAG, "Failed database.goOnline in initApp", e)
        }

        val myAdminId = getOrCreateAdminId(context)
        val myDeviceId = getOrCreateDeviceId(context)
        currentMyDeviceId = myDeviceId

        // 1. Listen for target devices under my Admin status
        adminScope?.cancel()
        adminScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        listenToAdminDevices(myAdminId)

        // 2. Perform user synchronization for our own device telemetry so others can find/monitor us!
        userScope?.cancel()
        userScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        // Start user synchronization loop under our static/permanent deviceId!
        startUserSync(context, myDeviceId)

        // 3. Synchronize lockdown schedules in real-time across all devices
        listenToSchedules(context)
    }

    fun initUser(context: Context) {
        initApp(context)
    }

    fun initAdmin(context: Context) {
        initApp(context)
    }

    // USER SIDE FUNCTIONS
    fun generateAndPublishPairingCode(context: Context) {
        val myDeviceId = getOrCreateDeviceId(context)
        val myAdminId = getOrCreateAdminId(context)
        val code = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        pairingCode.value = code

        val ref = database.getReference("pairing_codes").child(code)
        val data = mapOf(
            "code" to code,
            "deviceId" to myDeviceId,
            "adminId" to myAdminId,
            "deviceName" to StateManager.deviceName.value.ifEmpty { android.os.Build.MODEL },
            "localIp" to NetworkScanner.getLocalIpAddress(context),
            "status" to "pending",
            "createdAt" to ServerValue.TIMESTAMP
        )
        ref.setValue(data)

        // Listen for pairing completion
        pairingListener?.let {
            try { ref.removeEventListener(it) } catch (e: Exception) {}
        }
        pairingListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val status = snapshot.child("status").value as? String ?: "pending"
                if (status == "paired") {
                    val partnerDevId = snapshot.child("adminDeviceId").getValue(String::class.java)
                        ?: snapshot.child("partnerDeviceId").getValue(String::class.java)
                    if (!partnerDevId.isNullOrEmpty()) {
                        StateManager.addPairedDeviceId(partnerDevId)
                        database.getReference("devices").child(myDeviceId)
                            .child("pairedDevices").child(partnerDevId).setValue(true)
                    }
                    pairingCode.value = null

                    // Cleanup code node
                    ref.removeValue()
                    if (pairingListener != null) {
                        ref.removeEventListener(pairingListener!!)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Pairing code listener cancelled", error.toException())
            }
        })
    }

    private fun getDeviceLocation(context: Context): Pair<Double, Double>? {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (lm != null) {
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    val providers = lm.getProviders(true)
                    var bestLocation: android.location.Location? = null
                    for (provider in providers) {
                        val l = lm.getLastKnownLocation(provider) ?: continue
                        if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                            bestLocation = l
                        }
                    }
                    if (bestLocation != null) {
                        return Pair(bestLocation.latitude, bestLocation.longitude)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed device location direct GPS query", e)
        }
        return null
    }

    private fun startUserSync(context: Context, deviceId: String) {
        val ref = database.getReference("devices").child(deviceId)
        try {
            ref.keepSynced(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed keepSynced in startUserSync", e)
        }
        
        // Remove old location updates listener if any exists
        activeLocationListener?.let { listener ->
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                lm?.removeUpdates(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed removing old activeLocationListener inside startUserSync", e)
            }
        }
        activeLocationListener = null

        // Request active precise location updates on Main Thread to feed GPS sensor
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                if (lm != null) {
                    val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasFine || hasCoarse) {
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(loc: android.location.Location) {
                                Log.i(TAG, "Active GPS/Network location update: ${loc.latitude}, ${loc.longitude}")
                            }
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }
                        activeLocationListener = listener
                        
                        if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                            lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 5000L, 5f, listener)
                        }
                        if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                            val netProvider = android.location.LocationManager.NETWORK_PROVIDER
                            lm.requestLocationUpdates(netProvider, 5000L, 5f, listener)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission missing", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed registering active location listener", e)
            }
        }
        
        // Start heartbeat scope
        userScope?.launch {
            var locationSeedLat = StateManager.lastLatitude.value
            var locationSeedLng = StateManager.lastLongitude.value
            while (isActive) {
                try {
                    // Telemetry calculations
                    val batteryStatus: android.content.Intent? = try {
                        context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    } catch (e: Exception) { null }
                    val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 87
                    val statusVal = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = statusVal == android.os.BatteryManager.BATTERY_STATUS_CHARGING || statusVal == android.os.BatteryManager.BATTERY_STATUS_FULL
 
                    // Ringer mode
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    val ringerMode = when (audioManager?.ringerMode) {
                        android.media.AudioManager.RINGER_MODE_SILENT -> "Silent Mode"
                        android.media.AudioManager.RINGER_MODE_VIBRATE -> "Vibrate Mode"
                        else -> "Normal Ringer"
                    }
 
                    // Foreground app simulation/reading
                    val activeApp = try {
                        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                        val time = System.currentTimeMillis()
                        val stats = usm?.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)
                        if (!stats.isNullOrEmpty()) {
                            val sorted = stats.sortedByDescending { it.lastTimeUsed }
                            val pkg = sorted[0].packageName
                            val label = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                            if (label == "Launcher" || label == "Nexuslauncher") "Workspace Home" else label
                        } else {
                            val randomApps = listOf("Chrome Browser", "YouTube Playback", "TikTok Active", "Message Center", "GuardLink Shield")
                            randomApps[java.util.Random().nextInt(randomApps.size)]
                        }
                    } catch (e: Exception) {
                        "GuardLink Shield"
                    }
 
                    // Location query (Google Find My Device style): Live sensor coordinates if permissions granted!
                    val realLoc = getDeviceLocation(context)
                    val finalLat: Double
                    val finalLng: Double
                    if (realLoc != null) {
                        finalLat = realLoc.first
                        finalLng = realLoc.second
                        locationSeedLat = finalLat
                        locationSeedLng = finalLng
                        StateManager.saveLastKnownLocation(finalLat, finalLng)
                    } else {
                        locationSeedLat = StateManager.lastLatitude.value
                        locationSeedLng = StateManager.lastLongitude.value
                        finalLat = locationSeedLat
                        finalLng = locationSeedLng
                    }
 
                    // Update to database
                    val telemetryMap = mapOf(
                        "lastSeen" to ServerValue.TIMESTAMP,
                        "localIp" to NetworkScanner.getLocalIpAddress(context),
                        "name" to StateManager.deviceName.value,
                        "batteryLevel" to pct,
                        "isCharging" to isCharging,
                        "activeApp" to activeApp,
                        "ringerMode" to ringerMode,
                        "latitude" to finalLat,
                        "longitude" to finalLng,
                        "isGeofenceTriggered" to false
                    )
                    ref.updateChildren(telemetryMap)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error inside user sync loop", t)
                }
 
                delay(10000) // 10 seconds heartbeat
            }
        }
 
        userDeviceListener?.let {
            try {
                ref.removeEventListener(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing old userDeviceListener", e)
            }
        }
        userDeviceListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    return
                }
                 
                val blocked = snapshot.child("blocked").getValue(Boolean::class.java) ?: false
                val msg = snapshot.child("blockedMessage").getValue(String::class.java) ?: ""
                val pwd = snapshot.child("blockedPassword").getValue(String::class.java) ?: ""
                val until = snapshot.child("blockedUntil").getValue(Long::class.java) ?: 0L
                val img = snapshot.child("blockedImage").getValue(String::class.java) ?: ""
 
                // Custom styling configuration
                val theme = snapshot.child("lockTheme").getValue(String::class.java) ?: "slate"
                val wallpaper = snapshot.child("lockWallpaper").getValue(String::class.java) ?: ""
                val warnIcon = snapshot.child("lockWarningIcon").getValue(String::class.java) ?: "lock"
                if (StateManager.lockTheme.value != theme || StateManager.lockWallpaper.value != wallpaper || StateManager.lockWarningIcon.value != warnIcon) {
                    StateManager.setLockStyle(theme, wallpaper, warnIcon)
                }
 
                // Update physical state
                if (StateManager.isBlocked.value != blocked || StateManager.blockedMessage.value != msg) {
                    StateManager.setBlocked(blocked, msg, pwd, until, img)
                }
 
                // Dynamically display or dismiss the full screen block screen overlay!
                if (blocked) {
                    if (!com.example.overlay.OverlayManager.isOverlayShowing) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            com.example.overlay.OverlayManager.showOverlay(context, msg, pwd) {
                                // Manual unlock correct password entered
                                StateManager.setBlocked(false)
                                userUnblockSelf(context)
                            }
                        }
                    }
                } else {
                    if (com.example.overlay.OverlayManager.isOverlayShowing) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            com.example.overlay.OverlayManager.hideOverlay()
                        }
                    }
                }
 
                // Ring sound alarm checker (Find My Device Feature)
                val ringRequested = snapshot.child("ringRequested").getValue(Boolean::class.java) ?: false
                if (ringRequested) {
                    com.example.service.GuardLinkService.triggerAlarmOn(context)
                } else {
                    com.example.service.GuardLinkService.triggerAlarmOff()
                }

                // Camera stream checker
                val streamReq = snapshot.child("cameraStreamRequested").getValue(Boolean::class.java) ?: false
                val cameraLens = snapshot.child("cameraLens").getValue(String::class.java) ?: "front"
                if (streamReq) {
                    com.example.service.GuardLinkService.triggerCameraStreamOn(cameraLens)
                    com.example.camera.BackgroundCameraManager.startCameraStreamFirebase(context, cameraLens)
                } else {
                    com.example.service.GuardLinkService.triggerCameraStreamOff()
                    com.example.camera.BackgroundCameraManager.stopCameraStreamFirebase()
                }

                // Screen stream checker
                val screenStreamReq = snapshot.child("screenStreamRequested").getValue(Boolean::class.java) ?: false
                if (screenStreamReq) {
                    com.example.camera.ScreenCaptureManager.startScreenCapture(context)
                } else {
                    com.example.camera.ScreenCaptureManager.stopScreenCapture(context)
                }

                // Intercom audio checker (Admin speaking -> Play on device)
                val adminSpeaking = snapshot.child("adminSpeaking").getValue(Boolean::class.java) ?: false
                isCurrentAdminSpeaking = adminSpeaking
                isAdminSpeaking.value = adminSpeaking
                val intercomAudio = snapshot.child("intercomAudio").getValue(String::class.java) ?: ""
                if (adminSpeaking && intercomAudio.isNotEmpty()) {
                    AudioStreamManager.playAudioSegment(intercomAudio)
                } else if (!adminSpeaking) {
                    AudioStreamManager.stopPlayback()
                }
            }
 
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "User device sync cancelled", error.toException())
            }
        })
    }
 
    fun userUnblockSelf(context: Context) {
        val deviceId = getOrCreateDeviceId(context)
        val devRef = database.getReference("devices").child(deviceId)
        devRef.updateChildren(
            mapOf(
                "blocked" to false,
                "blockedUntil" to 0L
            )
        )
    }

    fun updateUserBlockedState(context: Context, blocked: Boolean, message: String = "", password: String = "") {
        val deviceId = getOrCreateDeviceId(context)
        val devRef = database.getReference("devices").child(deviceId)
        val updates = mutableMapOf<String, Any>(
            "blocked" to blocked
        )
        if (blocked) {
            updates["blockedMessage"] = message
            updates["blockedPassword"] = password
        }
        devRef.updateChildren(updates)
    }

    fun userStopAlarmSelf(context: Context) {
        val deviceId = getOrCreateDeviceId(context)
        val devRef = database.getReference("devices").child(deviceId)
        devRef.updateChildren(
            mapOf(
                "ringRequested" to false
            )
        )
    }

    private val isUploadingFrame = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastFrameUploadTime = 0L

    fun uploadUserFrame(context: Context, base64Frame: String, base64Audio: String = "") {
        val now = System.currentTimeMillis()
        if (base64Frame.isNotEmpty() && isUploadingFrame.get() && (now - lastFrameUploadTime < 500L)) {
            // Drop frame if previous payload is actively sending within short timeout window
            return
        }
        isUploadingFrame.set(true)
        lastFrameUploadTime = now

        val deviceId = getOrCreateDeviceId(context)
        val devRef = database.getReference("devices").child(deviceId)
        val updates = mutableMapOf<String, Any>(
            "cameraFeedFrame" to base64Frame
        )
        if (base64Audio.isNotEmpty()) {
            updates["cameraFeedAudio"] = base64Audio
        } else {
            updates["cameraFeedAudio"] = ""
        }
        devRef.updateChildren(updates).addOnCompleteListener {
            isUploadingFrame.set(false)
        }.addOnFailureListener {
            isUploadingFrame.set(false)
        }
    }

    private val isUploadingScreenFrame = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastScreenUploadTime = 0L

    fun uploadUserScreenFrame(context: Context, base64Frame: String) {
        val now = System.currentTimeMillis()
        if (base64Frame.isNotEmpty() && isUploadingScreenFrame.get() && (now - lastScreenUploadTime < 500L)) {
            return
        }
        isUploadingScreenFrame.set(true)
        lastScreenUploadTime = now

        val deviceId = getOrCreateDeviceId(context)
        val devRef = database.getReference("devices").child(deviceId)
        devRef.child("screenFeedFrame").setValue(base64Frame).addOnCompleteListener {
            isUploadingScreenFrame.set(false)
        }.addOnFailureListener {
            isUploadingScreenFrame.set(false)
        }
    }

    fun requestScreenStream(deviceId: String, enable: Boolean) {
        val childRef = database.getReference("devices").child(deviceId)
        childRef.child("screenStreamRequested").setValue(enable)
    }

    // LOCKDOWN SCHEDULE SYNCHRONIZATION METHODS
    private var schedulesListener: ValueEventListener? = null

    fun saveScheduleToFirebase(schedule: com.example.data.LockSchedule) {
        val ref = database.getReference("schedules").child(schedule.id)
        val map = mapOf(
            "id" to schedule.id,
            "deviceName" to schedule.deviceName,
            "startHour" to schedule.startHour,
            "startMinute" to schedule.startMinute,
            "endHour" to schedule.endHour,
            "endMinute" to schedule.endMinute,
            "daysOfWeek" to schedule.daysOfWeek,
            "message" to schedule.message,
            "passcode" to schedule.passcode,
            "enabled" to schedule.enabled,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        ref.setValue(map).addOnFailureListener {
            Log.e(TAG, "Failed to save schedule to Firebase: ${it.message}")
        }
    }

    fun deleteScheduleFromFirebase(id: String) {
        database.getReference("schedules").child(id).removeValue().addOnFailureListener {
            Log.e(TAG, "Failed to delete schedule from Firebase: ${it.message}")
        }
    }

    fun toggleScheduleInFirebase(id: String, enabled: Boolean) {
        database.getReference("schedules").child(id).child("enabled").setValue(enabled).addOnFailureListener {
            Log.e(TAG, "Failed to toggle schedule in Firebase: ${it.message}")
        }
    }

    fun listenToSchedules(context: Context) {
        schedulesListener?.let {
            database.getReference("schedules").removeEventListener(it)
        }
        val ref = database.getReference("schedules")
        schedulesListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.example.data.LockSchedule>()
                for (child in snapshot.children) {
                    try {
                        val id = child.child("id").getValue(String::class.java) ?: child.key ?: continue
                        val deviceName = child.child("deviceName").getValue(String::class.java) ?: "All Devices"
                        val startHour = child.child("startHour").getValue(Int::class.java) ?: 21
                        val startMinute = child.child("startMinute").getValue(Int::class.java) ?: 0
                        val endHour = child.child("endHour").getValue(Int::class.java) ?: 7
                        val endMinute = child.child("endMinute").getValue(Int::class.java) ?: 0
                        val message = child.child("message").getValue(String::class.java) ?: ""
                        val passcode = child.child("passcode").getValue(String::class.java) ?: "1234"
                        val enabled = child.child("enabled").getValue(Boolean::class.java) ?: true

                        val days = mutableListOf<Int>()
                        val daysSnapshot = child.child("daysOfWeek")
                        if (daysSnapshot.exists()) {
                            for (d in daysSnapshot.children) {
                                val dayVal = d.getValue(Int::class.java)
                                if (dayVal != null) days.add(dayVal)
                            }
                        }
                        if (days.isEmpty()) {
                            days.addAll(listOf(2, 3, 4, 5, 6))
                        }
                        list.add(
                            com.example.data.LockSchedule(
                                id = id,
                                deviceName = deviceName,
                                startHour = startHour,
                                startMinute = startMinute,
                                endHour = endHour,
                                endMinute = endMinute,
                                daysOfWeek = days,
                                message = message,
                                passcode = passcode,
                                enabled = enabled
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing schedule from firebase", e)
                    }
                }

                // Update local state to match cloud
                StateManager.saveSchedules(list)

                // Immediately trigger schedule evaluation
                try {
                    com.example.service.GuardLinkService.evaluateSchedulesNow(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Schedules fetch cancelled", error.toException())
            }
        })
    }

    fun sendDeviceIntercomAudio(context: Context, base64Audio: String, deviceSpeaking: Boolean) {
        val deviceId = getOrCreateDeviceId(context)
        val devRef = database.getReference("devices").child(deviceId)
        val updates = mapOf(
            "cameraFeedAudio" to base64Audio,
            "deviceSpeaking" to deviceSpeaking
        )
        devRef.updateChildren(updates)
    }

    fun sendIntercomAudio(deviceId: String, base64Audio: String, adminSpeaking: Boolean) {
        val childRef = database.getReference("devices").child(deviceId)
        val updates = mapOf(
            "intercomAudio" to base64Audio,
            "adminSpeaking" to adminSpeaking
        )
        childRef.updateChildren(updates)
    }

    fun stopUserSyncOnly(context: Context) {
        val deviceId = getOrCreateDeviceId(context)
        
        userDeviceListener?.let {
            database.getReference("devices").child(deviceId).removeEventListener(it)
        }
        pairingListener?.let {
            pairingCode.value?.let { code ->
                database.getReference("pairing_codes").child(code).removeEventListener(it)
            }
        }
        
        activeLocationListener?.let { listener ->
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                lm?.removeUpdates(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove activeLocationListener on stopUserSyncOnly", e)
            }
        }
        activeLocationListener = null

        userScope?.cancel()
    }

    fun stopUserAndReset(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_FIREBASE, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(KEY_PAIRED_DEVICE_ID, null)
        
        userDeviceListener?.let {
            if (deviceId != null) {
                database.getReference("devices").child(deviceId).removeEventListener(it)
            }
        }
        pairingListener?.let {
            pairingCode.value?.let { code ->
                database.getReference("pairing_codes").child(code).removeEventListener(it)
            }
        }
        
        activeLocationListener?.let { listener ->
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                lm?.removeUpdates(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove activeLocationListener on stopUserAndReset", e)
            }
        }
        activeLocationListener = null

        userScope?.cancel()

        if (deviceId != null) {
            database.getReference("devices").child(deviceId).removeValue()
        }

        prefs.edit().clear().apply()
        isPaired.value = false
        pairingCode.value = null
    }

    // ADMIN SIDE FUNCTIONS
    fun pairDeviceByCode(context: Context, code: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val cleanCode = code.trim().uppercase()
        val pairingRef = database.getReference("pairing_codes").child(cleanCode)

        var hasFinished = false
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        val timeoutRunnable = Runnable {
            if (!hasFinished) {
                hasFinished = true
                onFailure("Connection timed out. Please check that the companion device is online and showing the pairing code.")
            }
        }
        
        // Start 15 second timeout safety net
        handler.postDelayed(timeoutRunnable, 15000)

        pairingRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (hasFinished) return
                hasFinished = true
                handler.removeCallbacks(timeoutRunnable)

                if (!snapshot.exists()) {
                    onFailure("Invalid code or connection expired.")
                    return
                }

                val status = snapshot.child("status").value as? String ?: "pending"
                if (status != "pending") {
                    onFailure("This code has already been used.")
                    return
                }

                val A_devId = snapshot.child("deviceId").value as? String ?: ""
                val A_adminId = snapshot.child("adminId").value as? String ?: ""
                val A_devName = snapshot.child("deviceName").value as? String ?: "Unknown Device"
                val A_devIp = snapshot.child("localIp").value as? String ?: "0.0.0.0"

                val B_adminId = getOrCreateAdminId(context)
                val B_adminNameStr = StateManager.deviceName.value.ifEmpty { "Admin Portal" }
                val B_devId = getOrCreateDeviceId(context)
                val B_devName = StateManager.deviceName.value.ifEmpty { android.os.Build.MODEL }
                val B_devIp = NetworkScanner.getLocalIpAddress(context)

                // Save pairing locally on B
                if (A_devId.isNotEmpty()) {
                    StateManager.addPairedDeviceId(A_devId)
                }

                val devRefA = database.getReference("devices").child(A_devId)
                val devDataA = mapOf(
                    "id" to A_devId,
                    "name" to A_devName,
                    "adminId" to B_adminId, // A is seen by B
                    "localIp" to A_devIp,
                    "status" to "active",
                    "blocked" to false,
                    "blockedMessage" to "This device has been restricted.",
                    "blockedPassword" to "1234",
                    "blockedUntil" to 0L,
                    "blockedImage" to "",
                    "cameraStreamRequested" to false,
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "pairedDevices" to mapOf(B_devId to true)
                )

                val devRefB = database.getReference("devices").child(B_devId)
                val devDataB = mapOf(
                    "id" to B_devId,
                    "name" to B_devName,
                    "adminId" to A_adminId.ifEmpty { B_adminId }, // B is seen by A (or falls back to self if none)
                    "localIp" to B_devIp,
                    "status" to "active",
                    "blocked" to false,
                    "blockedMessage" to "This device has been restricted.",
                    "blockedPassword" to "1234",
                    "blockedUntil" to 0L,
                    "blockedImage" to "",
                    "cameraStreamRequested" to false,
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "pairedDevices" to mapOf(A_devId to true)
                )

                devRefA.updateChildren(devDataA).addOnCompleteListener { taskA ->
                    if (taskA.isSuccessful) {
                        devRefB.updateChildren(devDataB).addOnCompleteListener { taskB ->
                            if (taskB.isSuccessful) {
                                pairingRef.child("adminId").setValue(B_adminId)
                                pairingRef.child("adminDeviceId").setValue(B_devId)
                                pairingRef.child("adminName").setValue(B_adminNameStr)
                                pairingRef.child("status").setValue("paired").addOnCompleteListener { pTask ->
                                    if (pTask.isSuccessful) {
                                        onSuccess()
                                    } else {
                                        onFailure("Failed to update pairing handshake.")
                                    }
                                }
                            } else {
                                onFailure("Failed to initialize local device record in remote host.")
                            }
                        }
                    } else {
                        onFailure("Failed to initialize remote device record.")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (hasFinished) return
                hasFinished = true
                handler.removeCallbacks(timeoutRunnable)
                onFailure(error.message)
            }
        })
    }

    private fun listenToAdminDevices(adminId: String) {
        val ref = database.getReference("devices")
        try {
            ref.keepSynced(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed keepSynced in listenToAdminDevices", e)
        }
        
        devicesListener?.let {
            try {
                ref.removeEventListener(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing old devicesListener", e)
            }
        }
        devicesListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<DiscoveredDevice>()
                val feeds = mutableMapOf<String, String>()
                val screenFeeds = mutableMapOf<String, String>()
                val audioFeeds = mutableMapOf<String, String>()
                val myPairedSet = StateManager.pairedDeviceIds.value
                val myNodeSnapshot = snapshot.child(currentMyDeviceId)
                val myFirebasePaired = myNodeSnapshot.child("pairedDevices")

                for (child in snapshot.children) {
                    val devId = child.child("id").getValue(String::class.java) ?: child.key ?: ""
                    if (devId.isEmpty() || devId == currentMyDeviceId) {
                        continue // Exclude own device from the peer devices list
                    }

                    // STRICT: Only show devices explicitly paired via QR code or pairing code on this admin device!
                    val isPairedLocally = myPairedSet.contains(devId)
                    val isPairedInMyNode = myFirebasePaired.child(devId).getValue(Boolean::class.java) == true

                    if (!isPairedLocally && !isPairedInMyNode) {
                        continue // Ignore unpaired devices from database
                    }

                    if (!isPairedLocally) {
                        StateManager.addPairedDeviceId(devId)
                    }

                    val name = child.child("name").getValue(String::class.java) ?: "GuardLink Client"
                    val localIp = child.child("localIp").getValue(String::class.java) ?: "0.0.0.0"
                    val blocked = child.child("blocked").getValue(Boolean::class.java) ?: false
                    val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L

                    val isOnline = (System.currentTimeMillis() - lastSeen) < 60000
                    val status = if (!isOnline) "offline" else if (blocked) "blocked" else "active"

                    // Decode telemetry and styles
                    val batteryLevel = child.child("batteryLevel").getValue(Int::class.java) ?: 100
                    val isCharging = child.child("isCharging").getValue(Boolean::class.java) ?: false
                    val activeApp = child.child("activeApp").getValue(String::class.java) ?: "GuardLink Shield"
                    val ringerMode = child.child("ringerMode").getValue(String::class.java) ?: "Normal Ringer"
                    val latitude = child.child("latitude").getValue(Double::class.java) ?: 14.555060
                    val longitude = child.child("longitude").getValue(Double::class.java) ?: 121.011993
                    val isGeofenceTriggered = child.child("isGeofenceTriggered").getValue(Boolean::class.java) ?: false
                    val ringRequested = child.child("ringRequested").getValue(Boolean::class.java) ?: false
                    val lockTheme = child.child("lockTheme").getValue(String::class.java) ?: "slate"
                    val lockWallpaper = child.child("lockWallpaper").getValue(String::class.java) ?: ""
                    val lockWarningIcon = child.child("lockWarningIcon").getValue(String::class.java) ?: "lock"
                    val cameraLens = child.child("cameraLens").getValue(String::class.java) ?: "front"
                    val deviceSpeaking = child.child("deviceSpeaking").getValue(Boolean::class.java) ?: false

                    list.add(
                        DiscoveredDevice(
                            ip = devId, 
                            name = name,
                            status = status,
                            blocked = blocked,
                            port = 9999,
                            lastSeen = lastSeen,
                            batteryLevel = batteryLevel,
                            isCharging = isCharging,
                            activeApp = activeApp,
                            ringerMode = ringerMode,
                            latitude = latitude,
                            longitude = longitude,
                            isGeofenceTriggered = isGeofenceTriggered,
                            ringRequested = ringRequested,
                            lockTheme = lockTheme,
                            lockWallpaper = lockWallpaper,
                            lockWarningIcon = lockWarningIcon,
                            cameraLens = cameraLens,
                            deviceSpeaking = deviceSpeaking
                        )
                    )

                    val frame = child.child("cameraFeedFrame").getValue(String::class.java)
                    if (!frame.isNullOrEmpty()) {
                        feeds[devId] = frame
                    }
                    val screen = child.child("screenFeedFrame").getValue(String::class.java)
                    if (!screen.isNullOrEmpty()) {
                        screenFeeds[devId] = screen
                    }
                    val audio = child.child("cameraFeedAudio").getValue(String::class.java)
                    if (!audio.isNullOrEmpty()) {
                        audioFeeds[devId] = audio
                    }
                }
                _onlineDevices.value = list
                _activeCameraFeeds.value = feeds
                _activeScreenFeeds.value = screenFeeds
                _activeCameraAudio.value = audioFeeds
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Admin devices fetch cancelled", error.toException())
            }
        })
    }

    fun writeDeviceLog(deviceId: String, action: String, details: String) {
        val logRef = database.getReference("devices").child(deviceId).child("logs").push()
        val logData = mapOf(
            "id" to logRef.key,
            "action" to action,
            "details" to details,
            "timestamp" to ServerValue.TIMESTAMP
        )
        logRef.setValue(logData)
    }

    fun adminClearLogs(deviceId: String) {
        database.getReference("devices").child(deviceId).child("logs").removeValue()
    }

    fun sendChatMessage(deviceId: String, sender: String, messageText: String) {
        val chatRef = database.getReference("devices").child(deviceId).child("chat").push()
        val chatData = mapOf(
            "id" to chatRef.key,
            "sender" to sender,
            "message" to messageText,
            "timestamp" to ServerValue.TIMESTAMP
        )
        chatRef.setValue(chatData)
    }

    fun adminConfigureGeofence(deviceId: String, enabled: Boolean, baseLat: Double, baseLng: Double, radiusMeters: Double) {
        val devRef = database.getReference("devices").child(deviceId)
        devRef.updateChildren(
            mapOf(
                "geofenceEnabled" to enabled,
                "centerLatitude" to baseLat,
                "centerLongitude" to baseLng,
                "geofenceRadius" to radiusMeters
            )
        )
        writeDeviceLog(deviceId, "GEOFENCE_SET", "Geofence safe center set to: Lat: $baseLat, Lng: $baseLng with radius ${radiusMeters.toInt()}m. State: ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun adminUpdateLockStyle(deviceId: String, theme: String, wallpaperUrl: String, warningIcon: String) {
        val devRef = database.getReference("devices").child(deviceId)
        devRef.updateChildren(
            mapOf(
                "lockTheme" to theme,
                "lockWallpaper" to wallpaperUrl,
                "lockWarningIcon" to warningIcon
            )
        )
        writeDeviceLog(deviceId, "CUSTOM_STYLE", "Lock theme changed: theme='$theme', warning_icon='$warningIcon'.")
    }

    fun adminBlockDevice(deviceId: String, message: String, password: String, timerSeconds: Long = -1L, imageBase64: String = "") {
        val devRef = database.getReference("devices").child(deviceId)
        val blockUntilValue = if (timerSeconds > 0) System.currentTimeMillis() + (timerSeconds * 1000) else 0L
        devRef.updateChildren(
            mapOf(
                "blocked" to true,
                "blockedMessage" to message,
                "blockedPassword" to password,
                "blockedUntil" to blockUntilValue,
                "blockedImage" to imageBase64
            )
        )
        // Reset/clear chat node on every lock
        database.getReference("devices").child(deviceId).child("chat").removeValue()
        
        writeDeviceLog(deviceId, "LOCK", "Lock commanded by admin. Banner: '$message'. Bypass password configured.")
    }

    fun adminUnblockDevice(deviceId: String) {
        val devRef = database.getReference("devices").child(deviceId)
        devRef.updateChildren(
            mapOf(
                "blocked" to false,
                "blockedUntil" to 0L,
                "blockedImage" to ""
            )
        )
        writeDeviceLog(deviceId, "UNLOCK", "Unlock commanded by administrator.")
    }

    fun adminRemoveDevice(deviceId: String) {
        StateManager.removePairedDeviceId(deviceId)
        if (currentMyDeviceId.isNotEmpty()) {
            database.getReference("devices").child(currentMyDeviceId)
                .child("pairedDevices").child(deviceId).removeValue()
        }
        database.getReference("devices").child(deviceId)
            .child("pairedDevices").child(currentMyDeviceId).removeValue()
    }

    fun adminClearAllDevices() {
        val currentSet = StateManager.pairedDeviceIds.value.toSet()
        StateManager.clearPairedDevices()
        if (currentMyDeviceId.isNotEmpty()) {
            database.getReference("devices").child(currentMyDeviceId)
                .child("pairedDevices").removeValue()
        }
        for (devId in currentSet) {
            database.getReference("devices").child(devId)
                .child("pairedDevices").child(currentMyDeviceId).removeValue()
        }
    }

    fun adminStartCamera(deviceId: String) {
        database.getReference("devices").child(deviceId).child("cameraStreamRequested").setValue(true)
        writeDeviceLog(deviceId, "CAMERA_START", "Camera feedback stream initiated.")
    }

    fun adminStopCamera(deviceId: String) {
        val childRef = database.getReference("devices").child(deviceId)
        childRef.child("cameraStreamRequested").setValue(false)
        childRef.child("cameraFeedFrame").setValue("")
        childRef.child("cameraFeedAudio").setValue("")
        childRef.child("intercomAudio").setValue("")
        childRef.child("adminSpeaking").setValue(false)
        writeDeviceLog(deviceId, "CAMERA_STOP", "Camera feedback stream stopped.")
    }

    fun adminSetCameraLens(deviceId: String, lens: String) {
        database.getReference("devices").child(deviceId).child("cameraLens").setValue(lens)
        writeDeviceLog(deviceId, "CAMERA_LENS_CHANGE", "Camera lens switched to $lens.")
    }

    fun adminToggleRingDevice(deviceId: String, value: Boolean) {
        database.getReference("devices").child(deviceId).child("ringRequested").setValue(value)
        writeDeviceLog(deviceId, if (value) "FIND_ALERT_START" else "FIND_ALERT_STOP", if (value) "Audible finding alert dispatched." else "Audible finding alert stopped.")
    }

    fun cleanupAdmin() {
        devicesListener?.let {
            database.getReference("devices").removeEventListener(it)
        }
        adminScope?.cancel()
    }
}
