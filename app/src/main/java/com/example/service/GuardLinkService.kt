package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.StateManager
import com.example.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GuardLinkService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    companion object {
        const val CHANNEL_ID = "guardlink_service_channel"
        const val ALARM_CHANNEL_ID = "guardlink_alarm_channel"
        const val BROADCAST_CHANNEL_ID = "guardlink_broadcast_channel"
        const val NOTIFICATION_ID = 1001
        const val ALARM_NOTIFICATION_ID = 1002
        const val BROADCAST_NOTIFICATION_ID = 1003
        
        val isServiceRunning = MutableStateFlow(false)
        private var instance: GuardLinkService? = null
        private var audioPlayer: android.media.MediaPlayer? = null
        private var autoStopRunnable: java.lang.Runnable? = null
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())

        fun showBroadcastNotification(context: Context, message: String) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channel = NotificationChannel(
                        BROADCAST_CHANNEL_ID,
                        "GuardLink Voice Broadcasts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Displays priority voice announcements from administrator."
                        enableVibration(true)
                        setBypassDnd(true)
                    }
                    manager.createNotificationChannel(channel)
                }

                val openIntent = Intent(context, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    3001,
                    openIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, BROADCAST_CHANNEL_ID)
                    .setContentTitle("📢 Admin Broadcast Alert")
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(BROADCAST_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e("GuardService", "Failed to post broadcast notification", e)
            }
        }

        fun triggerCameraStreamOn(lensFacing: String = "front") {
            instance?.let { ctx ->
                com.example.camera.BackgroundCameraManager.startCameraStreamFirebase(ctx.applicationContext, lensFacing)
            }
        }

        fun triggerCameraStreamOff() {
            com.example.camera.BackgroundCameraManager.stopCameraStreamFirebase()
        }

        fun promoteToMediaProjection() {
            instance?.updateServiceTypeWithMediaProjection(true)
        }

        fun demoteFromMediaProjection() {
            if (!com.example.camera.ScreenCaptureManager.isAuthorized()) {
                instance?.updateServiceTypeWithMediaProjection(false)
            }
        }

        @JvmStatic
        fun evaluateSchedulesNow(context: Context) {
            evaluateSchedules(context)
        }

        private fun evaluateSchedules(context: Context) {
            val schedules = StateManager.getSchedulesFromPrefs()
            if (schedules.isEmpty()) {
                if (StateManager.blockedBySchedule.value) {
                    Log.d("GuardScheduler", "No schedules present. Automatically releasing scheduled lock.")
                    StateManager.setBlockedBySchedule(false)
                    StateManager.setBlocked(false)
                    handler.post {
                        OverlayManager.hideOverlay()
                    }
                    com.example.network.FirebaseManager.updateUserBlockedState(context, false)
                }
                return
            }

            val calendar = java.util.Calendar.getInstance()
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) // 1 (Sun) to 7 (Sat)
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)

            val currentDeviceName = StateManager.deviceName.value

            val activeSchedule = schedules.firstOrNull { s ->
                s.enabled &&
                (s.deviceName == "*" || s.deviceName == "All Devices" || s.deviceName.equals(currentDeviceName, ignoreCase = true)) &&
                s.isTimeInSchedule(dayOfWeek, hour, minute)
            }

            if (activeSchedule != null) {
                if (!StateManager.isBlocked.value || !StateManager.blockedBySchedule.value) {
                    Log.d("GuardScheduler", "Schedule ACTIVE: ${activeSchedule.id}. Enforcing device lockdown!")
                    StateManager.setBlockedBySchedule(true)
                    val lockMsg = activeSchedule.message.ifEmpty { "Locked by scheduled restriction." }
                    val lockPwd = activeSchedule.passcode.ifEmpty { "1234" }
                    StateManager.setBlocked(true, lockMsg, lockPwd)
                    handler.post {
                        OverlayManager.showOverlay(context, lockMsg, lockPwd) {
                            StateManager.setBlocked(false)
                            StateManager.setBlockedBySchedule(false)
                            com.example.network.FirebaseManager.userUnblockSelf(context)
                        }
                    }
                    com.example.network.FirebaseManager.updateUserBlockedState(context, true, lockMsg, lockPwd)
                }
            } else {
                if (StateManager.blockedBySchedule.value) {
                    Log.d("GuardScheduler", "Scheduled window ended. Automatically releasing device lock...")
                    StateManager.setBlockedBySchedule(false)
                    StateManager.setBlocked(false)
                    handler.post {
                        OverlayManager.hideOverlay()
                    }
                    com.example.network.FirebaseManager.updateUserBlockedState(context, false)
                }
            }
        }

        @JvmStatic
        fun triggerAlarmOn(context: Context) {
            if (audioPlayer == null) {
                try {
                    val alertUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                    
                    audioPlayer = android.media.MediaPlayer().apply {
                        setDataSource(context, alertUri)
                        setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                        isLooping = true
                        prepare()
                        start()
                    }
                    Log.d("GuardService", "Find My Device: Alarm audio stream started successfully")

                    // Show screen-level floating dialog overlay
                    handler.post {
                        com.example.overlay.FinderPopupManager.showFinderPopup(context)
                    }

                    // Show active Heads-up notification with action button
                    showHeadsUpNotification(context)

                    // Enforce the 10-second limit
                    autoStopRunnable?.let { handler.removeCallbacks(it) }
                    autoStopRunnable = java.lang.Runnable {
                        Log.d("GuardService", "10-second finder alert limit reached. Silencing...")
                        try {
                            com.example.network.FirebaseManager.userStopAlarmSelf(context)
                            triggerAlarmOff()
                        } catch (e: Exception) {
                            Log.e("GuardService", "Error during automatic alert shutdown", e)
                        }
                    }
                    handler.postDelayed(autoStopRunnable!!, 10000L)

                } catch (e: Exception) {
                    Log.e("GuardService", "Error playing alarm audio", e)
                }
            }
        }

        @JvmStatic
        fun triggerAlarmOff() {
            try {
                autoStopRunnable?.let {
                    handler.removeCallbacks(it)
                    autoStopRunnable = null
                }

                // Dismiss center overlay popup dialog safely
                handler.post {
                    com.example.overlay.FinderPopupManager.hideFinderPopup()
                }

                // Clear/dismiss heads-up high importance notification
                instance?.let { srv ->
                    val notificationManager = srv.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    notificationManager.cancel(ALARM_NOTIFICATION_ID)
                }

                audioPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                }
            } catch (e: Exception) {
                Log.e("GuardService", "Error stopping alarm audio", e)
            } finally {
                audioPlayer = null
                Log.d("GuardService", "Find My Device: Alarm audio stream stopped successfully")
            }
        }

        private fun showHeadsUpNotification(context: Context) {
            val stopIntent = Intent(context, GuardLinkService::class.java).apply {
                action = "com.example.action.STOP_ALARM"
            }
            val stopPendingIntent = android.app.PendingIntent.getService(
                context,
                2001,
                stopIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val alarmNotification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setContentTitle("Finder Sound Playing")
                .setContentText("A diagnostic locator sound is active. Tap STOP to silence it.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "STOP SOUND",
                    stopPendingIntent
                )
                .setFullScreenIntent(stopPendingIntent, true) // Peeking banner delivery
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(ALARM_NOTIFICATION_ID, alarmNotification)
        }
    }
    
    private var isForegroundActive = false

    fun updateServiceType(enableCamera: Boolean = true) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GuardLink Service running")
            .setContentText(if (enableCamera) "Streaming live feed..." else "Device is being monitored")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // If foreground service is already running, only update the notification content to avoid
        // Android 14 ForegroundServiceStartNotAllowedException when called from the background
        if (isForegroundActive) {
            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
                return
            } catch (e: Exception) {
                Log.w("GuardService", "Failed to update notification banner", e)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                
                val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                if (hasCamera) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }

                val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasMic) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                
                if (com.example.camera.ScreenCaptureManager.isAuthorized()) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                
                startForeground(NOTIFICATION_ID, notification, type)
                isForegroundActive = true
                Log.d("GuardService", "Successfully updated/started foreground with type: $type")
            } else {
                startForeground(NOTIFICATION_ID, notification)
                isForegroundActive = true
            }
        } catch (e: Exception) {
            Log.e("GuardService", "Graceful warning: Failed to start/update foreground service with request: camera=$enableCamera", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val fallbackType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    startForeground(NOTIFICATION_ID, notification, fallbackType)
                    isForegroundActive = true
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                    isForegroundActive = true
                }
            } catch (ex: Exception) {
                Log.e("GuardService", "Final fallback failure to start foreground service", ex)
            }
        }
    }

    fun updateServiceTypeWithMediaProjection(enableMediaProjection: Boolean) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GuardLink Service running")
            .setContentText(if (enableMediaProjection) "Screen mirroring active" else "Device is being monitored")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasCamera) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

                val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasMic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

                if (enableMediaProjection) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(NOTIFICATION_ID, notification, type)
                isForegroundActive = true
                Log.d("GuardService", "Updated foreground type with media projection: $type")
            }
        } catch (e: Exception) {
            Log.e("GuardService", "Error updating foreground type for media projection", e)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("GuardService", "Foreground service onCreate called")
        
        // Acquire partial WakeLock to keep the CPU running when the screen is off
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "GuardLink:ServiceWakeLock").apply {
                acquire()
            }
            Log.d("GuardService", "Partial WakeLock acquired successfully")
        } catch (e: Exception) {
            Log.e("GuardService", "Failed to acquire WakeLock", e)
        }
        
        createNotificationChannel()

        // Eagerly initialize TextToSpeech engine so announcements play immediately without delay
        try {
            com.example.tts.TextToSpeechManager.init(this)
        } catch (e: Exception) {
            Log.e("GuardService", "Error initializing TextToSpeechManager", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GuardService", "Foreground service onStartCommand called")
        
        if (intent != null && intent.action == "com.example.action.STOP_ALARM") {
            Log.d("GuardService", "STOP_ALARM action received from notification/pop_up action.")
            try {
                com.example.network.FirebaseManager.userStopAlarmSelf(applicationContext)
                triggerAlarmOff()
            } catch (e: Exception) {
                Log.e("GuardService", "Failed to cancel alarm via intent action", e)
            }
            return START_STICKY
        }
        
        updateServiceType(enableCamera = true)
        
        isServiceRunning.value = true
        
        // Schedule periodic keep-alive heartbeat alarm
        scheduleKeepAliveAlarm()
        
        // Initialize Firebase Unified Sync
        try {
            com.example.network.FirebaseManager.initApp(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Active background lockdown scheduler loop: checks every 10 seconds
        serviceScope.launch {
            while (isActive) {
                try {
                    evaluateSchedules(applicationContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(10_000L)
            }
        }
        
        // Safety: If device was blocked before state, re-display overlay!
        if (StateManager.isBlocked.value) {
            val msg = StateManager.blockedMessage.value
            val pwd = StateManager.blockedPassword.value
            OverlayManager.showOverlay(applicationContext, msg, pwd) {
                serviceScope.launch {
                    StateManager.setBlocked(false)
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.d("GuardService", "Foreground service onDestroy called")
        instance = null
        
        // Cancel the keepalive alarm
        cancelKeepAliveAlarm()
        
        // Release WakeLock
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            Log.d("GuardService", "WakeLock released")
        } catch (e: Exception) {
            Log.e("GuardService", "Failed to release WakeLock", e)
        }
        
        try {
            com.example.network.FirebaseManager.stopUserSyncOnly(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        OverlayManager.hideOverlay()
        isServiceRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("GuardService", "onTaskRemoved called - scheduling 2-second quick reboot alarm")
        try {
            val restartServiceIntent = Intent(applicationContext, this.javaClass).apply {
                setPackage(packageName)
            }
            val restartServicePendingIntent = android.app.PendingIntent.getService(
                applicationContext, 
                2, 
                restartServiceIntent, 
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmService.set(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 2000,
                restartServicePendingIntent
            )
        } catch (e: Exception) {
            Log.e("GuardService", "Failed scheduling onTaskRemoved service restart", e)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleKeepAliveAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(applicationContext, com.example.receiver.GuardLinkReceiver::class.java).apply {
                action = "com.example.action.KEEP_ALIVE_HEARTBEAT"
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                applicationContext,
                1002,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val interval = 5 * 60 * 1000L // 5 minutes
            alarmManager.setInexactRepeating(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pendingIntent
            )
            Log.d("GuardService", "Keep-alive alarms initialized beautifully.")
        } catch (e: Exception) {
            Log.e("GuardService", "Error setting keep-alive alarm", e)
        }
    }

    private fun cancelKeepAliveAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(applicationContext, com.example.receiver.GuardLinkReceiver::class.java).apply {
                action = "com.example.action.KEEP_ALIVE_HEARTBEAT"
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                applicationContext,
                1002,
                intent,
                android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d("GuardService", "Keep-alive heartbeat alarm successfully unregistered.")
            }
        } catch (e: Exception) {
            Log.e("GuardService", "Error cancelling keep-alive alarm", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // persistent service background monitoring channel
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GuardLink Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors local network commands from administrator"
            }
            manager.createNotificationChannel(channel)

            // active high priority alarms / finder peeking notification channel
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "GuardLink Active Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays Heads-up alerts when finder sound is triggered."
                enableVibration(true)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(alarmChannel)

            // priority voice broadcast announcements channel
            val broadcastChannel = NotificationChannel(
                BROADCAST_CHANNEL_ID,
                "GuardLink Voice Broadcasts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays priority voice announcements from administrator."
                enableVibration(true)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(broadcastChannel)
        }
    }
}
