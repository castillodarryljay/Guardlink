package com.example.ui.user

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StateManager
import com.example.overlay.OverlayManager
import com.example.service.GuardLinkService
import com.example.ui.theme.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.window.DialogProperties
import com.example.network.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UserHomeScreen(
    onStopServiceAndExit: () -> Unit
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            )
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                val loc = FirebaseManager.getDeviceLocation(context)
                if (loc != null) {
                    StateManager.saveLastKnownLocation(loc.first, loc.second)
                }
            } catch (_: Exception) {}
        }
    }

    val isBlocked by StateManager.isBlocked.collectAsState()
    val isServiceRunning by GuardLinkService.isServiceRunning.collectAsState()
    val isScreenAuthorized by com.example.camera.ScreenCaptureManager.isScreenCaptureAuthorized.collectAsState()
    val activeBroadcast by StateManager.activeBroadcast.collectAsState()

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            com.example.camera.ScreenCaptureManager.setScreenCaptureIntentData(result.resultCode, result.data!!, context)
        }
    }

    // Firebase flow observers
    val isPairedFirebase by FirebaseManager.isPaired.collectAsState()
    val pairingCodeFirebase by FirebaseManager.pairingCode.collectAsState()
    val adminNameFirebase by FirebaseManager.adminName.collectAsState()
    val isFirebaseConnected by FirebaseManager.isFirebaseConnected.collectAsState(initial = false)
    val isAdminSpeaking by FirebaseManager.isAdminSpeaking.collectAsState(initial = false)
    var isDeviceSpeaking by remember { mutableStateOf(false) }

    val micPermissionLauncherForUser = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isDeviceSpeaking = true
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required to speak", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(isDeviceSpeaking) {
        if (isDeviceSpeaking) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                com.example.network.AudioStreamManager.startRecording(context)
                try {
                    while (isDeviceSpeaking) {
                        val base64Bytes = com.example.network.AudioStreamManager.getLatestAudioSegmentBase64()
                        if (base64Bytes.isNotEmpty()) {
                            com.example.network.FirebaseManager.sendDeviceIntercomAudio(context, base64Bytes, true)
                        }
                        kotlinx.coroutines.delay(180L)
                    }
                } finally {
                    com.example.network.AudioStreamManager.stopRecording()
                    com.example.network.FirebaseManager.sendDeviceIntercomAudio(context, "", false)
                }
            } else {
                isDeviceSpeaking = false
            }
        }
    }

    // Direct pre-emption: If admin starts speaking, immediately drop device-speaking to avoid conflicts and echo
    LaunchedEffect(isAdminSpeaking) {
        if (isAdminSpeaking) {
            isDeviceSpeaking = false
        }
    }

    var editDeviceName by remember { mutableStateOf(StateManager.deviceName.value) }

    var isIgnoringBatteryOptimizations by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                true
            }
        )
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val showAdminName = adminNameFirebase ?: "Remote Administrator"
    val showAdminIpStr = if (isFirebaseConnected) "Cloud Sync (Online)" else "Cloud Sync (Reconnecting...)"
    val isAttachedToAdmin = isPairedFirebase

    var showPauseConfirmDialog by remember { mutableStateOf(false) }
    var showUnpairConfirmDialog by remember { mutableStateOf(false) }

    // On launch, start the background service immediately if not already active
    LaunchedEffect(Unit) {
        if (!isServiceRunning) {
            val intent = Intent(context, GuardLinkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // Heartbeat pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_heartbeat")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            if (!isPairedFirebase) {
                UnpairedFirebaseView(
                    deviceName = editDeviceName,
                    onDeviceNameChange = {
                        editDeviceName = it
                        StateManager.setDeviceName(it)
                    },
                    pairingCode = pairingCodeFirebase,
                    onRegenerate = {
                        FirebaseManager.generateAndPublishPairingCode(context)
                    },
                    onExit = {
                        val intent = Intent(context, GuardLinkService::class.java)
                        context.stopService(intent)
                        StateManager.clearAll()
                        onStopServiceAndExit()
                    }
                )
            } else {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GUARDLINK USER MODE",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    )

                    // Status Badge (Active locks vs normal running)
                    Box(
                        modifier = Modifier
                            .background(
                                if (isBlocked) AccentRed.copy(alpha = 0.1f) else AccentGreen.copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isBlocked) AccentRed.copy(alpha = 0.2f) else AccentGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isBlocked) "LOCKED" else "STANDBY",
                            color = if (isBlocked) AccentRed else AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Active Voice Broadcast Alert Banner with Tactical Animation
                AnimatedVisibility(
                    visible = activeBroadcast != null,
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(350)),
                    exit = fadeOut(animationSpec = tween(250)) + shrinkVertically(animationSpec = tween(300))
                ) {
                    activeBroadcast?.let { b ->
                        val infiniteBroadcastAnim = rememberInfiniteTransition(label = "broadcast_fx")
                        val megaphoneAngle by infiniteBroadcastAnim.animateFloat(
                            initialValue = -12f,
                            targetValue = 12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(220, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "megaphone_angle"
                        )
                        val glowAlpha by infiniteBroadcastAnim.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 0.85f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "glow_alpha"
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E170A)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, Color(0xFFFFB300).copy(alpha = glowAlpha), RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(0xFFFFB300).copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Campaign,
                                                contentDescription = null,
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .graphicsLayer { rotationZ = megaphoneAngle }
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "PRIORITY BROADCAST ALERT",
                                                    color = Color(0xFFFFB300),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    letterSpacing = 1.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                UserBroadcastEqualizer(color = Color(0xFFFFB300))
                                            }
                                            Text(
                                                text = "From ${b.sender} • Dispatched via Recon Uplink",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            StateManager.dismissActiveBroadcast()
                                            FirebaseManager.acknowledgeActiveBroadcast(b.id)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = b.message,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "STATUS: BROADCAST ACTIVE",
                                        color = Color(0xFF34D399),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                StateManager.dismissActiveBroadcast()
                                                FirebaseManager.acknowledgeActiveBroadcast(b.id)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF34D399))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ACKNOWLEDGE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = {
                                                com.example.tts.TextToSpeechManager.speak(context, b.message)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("REPLAY SPEECH", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Battery Optimization Warning Card
                if (!isIgnoringBatteryOptimizations) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2416)), // Deep warm warning amber/brown
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Battery Warning",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CONTINUOUS POWER BLOCKED",
                                    color = Color(0xFFFFB300),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Android may terminate GuardLink in the background when your screen is off. Change battery usage to \"Unrestricted\" for a 24/7 uninterrupted connection.",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                                }
                                                context.startActivity(intent)
                                            } catch (ex: Exception) {
                                                ex.printStackTrace()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "SET UNRESTRICTED",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Remote Screen Sharing Permission Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isScreenAuthorized) SurfaceAlt else Color(0xFF1F1A2C)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isScreenAuthorized) {
                                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? android.media.projection.MediaProjectionManager
                                val intent = mpm?.createScreenCaptureIntent()
                                if (intent != null) {
                                    mediaProjectionLauncher.launch(intent)
                                }
                            }
                        }
                        .border(
                            1.dp,
                            if (isScreenAuthorized) AccentGreen.copy(alpha = 0.3f) else AccentPurple.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isScreenAuthorized) AccentGreen.copy(alpha = 0.15f) else AccentPurple.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenShare,
                                contentDescription = "Screen Broadcast",
                                tint = if (isScreenAuthorized) AccentGreen else AccentPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isScreenAuthorized) "REMOTE SCREEN STREAM: ALWAYS ACTIVE" else "ENABLE REMOTE SCREEN STREAM",
                                color = if (isScreenAuthorized) AccentGreen else AccentPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isScreenAuthorized) "Screen stream is active in background. Admin can view anytime on demand." else "Tap to grant 1-time screen mirroring permission for admin.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        if (isScreenAuthorized) {
                            Box(
                                modifier = Modifier
                                    .background(AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .border(1.dp, AccentGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = AccentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(AccentPurple.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "GRANT",
                                    color = AccentPurple,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Main Status Details Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Border, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = StateManager.deviceName.value,
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val activeText = if (!isServiceRunning) {
                                "Paused Status"
                            } else if (isFirebaseConnected) {
                                "Cloud Active"
                            } else {
                                "Connecting..."
                            }
                            val activeColor = if (!isServiceRunning) {
                                AccentRed
                            } else if (isFirebaseConnected) {
                                TextMono
                            } else {
                                Color(0xFFFFA726)
                            }
                            Text(
                                    text = activeText,
                                    color = activeColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Pulsing Heartbeat dot / Service State indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val pulseColor = if (!isServiceRunning) {
                                    AccentRed.copy(alpha = 0.15f)
                                } else if (isFirebaseConnected) {
                                    AccentGreen.copy(alpha = 0.3f)
                                } else {
                                    Color(0xFFFFA726).copy(alpha = 0.2f)
                                }
                                val dotColor = if (!isServiceRunning) {
                                    AccentRed
                                } else if (isFirebaseConnected) {
                                    AccentGreen
                                } else {
                                    Color(0xFFFFA726)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .scale(if (isServiceRunning) dotScale else 1.0f)
                                        .background(pulseColor, CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(dotColor, CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            val serviceStatusText = if (!isServiceRunning) {
                                "GuardLink Service Paused"
                            } else if (isFirebaseConnected) {
                                "GuardLink Service Running"
                            } else {
                                "Auto-Reconnecting..."
                            }
                            Text(
                                text = serviceStatusText,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val explanationText = if (!isServiceRunning) {
                            "The background monitoring service is offline."
                        } else if (isFirebaseConnected) {
                            "Syncing commands through Firebase (Secure Link)."
                        } else {
                            "Auto-reconnect active. Checking internet connection..."
                        }
                        Text(
                            text = explanationText,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        if (!isServiceRunning) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(context, GuardLinkService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "RESUME MONITORING SERVICE",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

            // ALWAYS-ON CONNECTION & BATTERY GUARD CARD
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isIgnoringBatteryOptimizations) Color(0xFF0F1E19) else Color(0xFF22160C)
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isIgnoringBatteryOptimizations) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOptimizations) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ALWAYS-ON LINK GUARD",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isIgnoringBatteryOptimizations) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "UNRESTRICTED" else "ACTION NEEDED",
                                color = if (isIgnoringBatteryOptimizations) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isIgnoringBatteryOptimizations) {
                            "Unrestricted background execution enabled. Android Doze/Deep Sleep will not kill or disconnect this device even if unused with screen off for long periods."
                        } else {
                            "Android battery optimization suspends CPU and network when the screen is turned off or idle for a long time. Grant background execution exemption to keep the connection permanently active."
                        },
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    if (!isIgnoringBatteryOptimizations) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    try {
                                        val fallbackIntent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallbackIntent)
                                    } catch (ex: Exception) {
                                        android.util.Log.e("UserHome", "Failed opening battery optimization intent", ex)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "KEEP RUNNING IN BACKGROUND 24/7",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // WALKIE-TALKIE PUSH TO TALK INTERCOM CARD
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SECURE LIVE INTERCOM (WALKIE-TALKIE)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAdminSpeaking) Color(0xFF2A1D15) else if (isDeviceSpeaking) Color(0xFF152A15) else Surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isAdminSpeaking) Color(0xFFE57373).copy(alpha = 0.5f) else if (isDeviceSpeaking) AccentGreen.copy(alpha = 0.5f) else Border,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isAdminSpeaking) Icons.Default.PlayArrow else if (isDeviceSpeaking) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (isAdminSpeaking) Color(0xFFE57373) else if (isDeviceSpeaking) AccentGreen else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isAdminSpeaking) "ADMIN IS SPEAKING" else if (isDeviceSpeaking) "MICROPHONE TRANSMITTING" else "INTERCOM STANDBY",
                                    color = if (isAdminSpeaking) Color(0xFFE57373) else if (isDeviceSpeaking) AccentGreen else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isAdminSpeaking) "Admin speaking. Your mic is muted to avoid echo." else if (isDeviceSpeaking) "You are speaking to Admin. Release button when done." else "Hold button to speak to the Admin device.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // The PUSH TO TALK BUTTON
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    color = if (isAdminSpeaking) ChoiceGreyState.copy(alpha = 0.3f) else if (isDeviceSpeaking) AccentGreen else AccentGreen.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isDeviceSpeaking) AccentGreen else AccentGreen.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .pointerInput(isAdminSpeaking) {
                                    detectTapGestures(
                                        onPress = {
                                            if (!isAdminSpeaking) {
                                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
                                                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    isDeviceSpeaking = true
                                                    try {
                                                        awaitRelease()
                                                    } finally {
                                                        isDeviceSpeaking = false
                                                    }
                                                } else {
                                                    micPermissionLauncherForUser.launch(android.Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isDeviceSpeaking) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = if (isAdminSpeaking) TextSecondary else if (isDeviceSpeaking) Color.Black else AccentGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAdminSpeaking) "ADMIN SPEAKING (BLOCKED)" else if (isDeviceSpeaking) "RELEASE TO SEND" else "HOLD TO SPEAK",
                                    color = if (isAdminSpeaking) TextSecondary else if (isDeviceSpeaking) Color.Black else AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Camera permission state card
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "BACKGROUND MONITORING CAPABILITY",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasCameraPermission) Surface else Color(0xFF2A1515)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (hasCameraPermission) AccentGreen.copy(alpha = 0.4f) else AccentRed.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (hasCameraPermission) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = if (hasCameraPermission) AccentGreen else AccentRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (hasCameraPermission) "FRONT CAMERA PIPELINE SECURE" else "CAMERA ACCESS RESTRICTED",
                                    color = if (hasCameraPermission) TextPrimary else AccentRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (hasCameraPermission) 
                                        "Admin can view active live streams of front camera." 
                                        else "Grant camera access to allow background device monitoring features.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        if (!hasCameraPermission) {
                            Button(
                                onClick = { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "GRANT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }

            // WARNING BANNERS
            // Connections details Card
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ADMINISTRATIVE CONNECTION STATE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isAttachedToAdmin) AccentGreen.copy(alpha = 0.4f) else Border,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isAttachedToAdmin) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(AccentGreen, CircleShape)
                                        .scale(dotScale)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connection: $showAdminIpStr",
                                    color = TextMono,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Admin: $showAdminName",
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                              )
                          } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(ChoiceGreyState, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Waiting for administrator connections...",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // System Notification Assistant (One-Tap Settings Deep Link)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SYSTEM NOTIFICATION CONTROL",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Border, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "MUTE SYSTEM 'OVERLAY' ALERTS",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "To protect transparency, Android displays a persistent notification saying 'GuardLink is displaying over other apps' on the lockscreen. You can permanently hide this category by following these steps:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .background(SurfaceAlt, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text("1. ", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Tap the button below to open GuardLink's system settings page.", color = TextSecondary, fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Text("2. ", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Scroll and tap 'Notification categories' (or 'Channels').", color = TextSecondary, fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Text("3. ", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Turn off the toggle named 'GuardLink Service' (or matching overlay alerts category) to conceal it from your lockscreen forever with absolutely zero functional loss.", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val intent = Intent().apply {
                                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallbackIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(fallbackIntent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Launch,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LAUNCH CHANNEL SYSTEM SETTINGS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Warning Security Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E140A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentAmber.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SECURITY WARNING",
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This device is registered to be remotely locked by the network administrator. Your screen content can be completely blocked if a BLOCK command is issued.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Exit Controls
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                        .height(48.dp),
                    onClick = { showPauseConfirmDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pause Service & Exit",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                    )
                }

                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                        .height(48.dp),
                    onClick = { showUnpairConfirmDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Disconnect & Unpair Device",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
                    )
                }
            }
        }

        // Pause Confirmation Dialogue
        if (showPauseConfirmDialog) {
            val themeBlur = LocalThemeBlur.current
            DisposableEffect(Unit) {
                themeBlur.value = true
                onDispose {
                    themeBlur.value = false
                }
            }
            AlertDialog(
                onDismissRequest = { showPauseConfirmDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                containerColor = Surface,
                title = {
                    Text(
                        text = "PAUSE MONITORING SERVICE?",
                        color = AccentBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Text(
                        text = "This will temporarily stop the background monitoring service and exit the app. However, your pairing settings are kept secure so the app immediately reconnects next time you launch it. Are you sure?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp),
                        onClick = {
                            // Stop background service, but DO NOT CLEAR PAIRING
                            val intent = Intent(context, GuardLinkService::class.java)
                            context.stopService(intent)

                            showPauseConfirmDialog = false
                            onStopServiceAndExit()
                        }
                    ) {
                        Text("PAUSE & EXIT", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.height(48.dp),
                        onClick = { showPauseConfirmDialog = false }
                    ) {
                        Text("CANCEL", color = TextSecondary)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 480.dp)
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
            )
        }

        // Unpair Confirmation Dialogue
        if (showUnpairConfirmDialog) {
            val themeBlur = LocalThemeBlur.current
            DisposableEffect(Unit) {
                themeBlur.value = true
                onDispose {
                    themeBlur.value = false
                }
            }
            AlertDialog(
                onDismissRequest = { showUnpairConfirmDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                containerColor = Surface,
                title = {
                    Text(
                        text = "UNPAIR & RESET PAIRING?",
                        color = AccentRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Text(
                        text = "This will completely delete this connection, wipe pairing credentials, and remove your record from the administrator dashboard. You will need to re-scan/pair with a new code to reconnect. Are you sure?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp),
                        onClick = {
                            // Stop background service
                            val intent = Intent(context, GuardLinkService::class.java)
                            context.stopService(intent)

                            // Wipe all pairing credentials
                            FirebaseManager.stopUserAndReset(context)
                            StateManager.clearAll()
                            
                            showUnpairConfirmDialog = false
                            onStopServiceAndExit()
                        }
                    ) {
                        Text("UNPAIR & WIPE", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.height(48.dp),
                        onClick = { showUnpairConfirmDialog = false }
                    ) {
                        Text("CANCEL", color = TextSecondary)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 480.dp)
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
            )
        }
    }
}
}
}

@Composable
fun UnpairedFirebaseView(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    pairingCode: String?,
    onRegenerate: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("my_code") }
    var adminCodeInput by remember { mutableStateOf("") }
    var isPairingOnline by remember { mutableStateOf(false) }
    var pairError by remember { mutableStateOf<String?>(null) }
    var isScanningAdminQr by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasCameraPermission = isGranted
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App State title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GUARDLINK PAIRING",
                color = AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp
            )
            Box(
                modifier = Modifier
                    .background(AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "AWAITING LINK",
                    color = AccentCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Liquid Glass Cyber Pill Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceAlt, RoundedCornerShape(100.dp))
                .border(1.dp, GlassBorderBrush, RoundedCornerShape(100.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isMyCode = selectedTab == "my_code"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (isMyCode) AccentCyan else Color.Transparent)
                    .then(
                        if (isMyCode) Modifier.border(1.dp, AccentCyan.copy(alpha = 0.6f), RoundedCornerShape(100.dp))
                        else Modifier
                    )
                    .clickable { selectedTab = "my_code"; isScanningAdminQr = false },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MY QR & CODE",
                    color = if (isMyCode) Color.Black else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            val isEnterCode = selectedTab == "enter_code"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (isEnterCode) AccentBlue else Color.Transparent)
                    .then(
                        if (isEnterCode) Modifier.border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(100.dp))
                        else Modifier
                    )
                    .clickable { selectedTab = "enter_code"; isScanningAdminQr = false },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LINK TO ADMIN",
                    color = if (isEnterCode) Color.White else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        if (selectedTab == "my_code") {
            // Device Display Name Field
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorderBrush, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DEVICE DISPLAY NAME",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = onDeviceNameChange,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = Border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = SurfaceAlt,
                            unfocusedContainerColor = SurfaceAlt
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("e.g. Living Room Tablet", color = TextTertiary) }
                    )
                }
            }

            // Pairing Code & QR Code display Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LiquidGlassChromaticBorder, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PAIRING CODE",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (pairingCode == null) {
                        CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Registering with Firebase...", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        Box(
                            modifier = Modifier
                                .background(SurfaceAlt, RoundedCornerShape(14.dp))
                                .border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(14.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pairingCode,
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Or scan this QR code on the admin console:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .border(2.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(10.dp)
                        ) {
                            QrCodeView(
                                data = "guardlink_pair:$pairingCode",
                                modifier = Modifier.size(170.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onRegenerate,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt),
                            modifier = Modifier
                                .border(1.dp, GlassBorderBrush, RoundedCornerShape(100.dp))
                                .height(42.dp),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Regenerate Code", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Connection waiting details Info
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassGreenBorderBrush, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "WAITING FOR ADMIN HANDSHAKE",
                            color = AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Enter this pairing code or scan the QR code from the Administrator's Dashboard to secure the connection online.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            // LINK TO ADMIN VIEW
            if (isScanningAdminQr) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "POINT CAMERA AT ADMIN DASHBOARD QR CODE",
                        color = AccentAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )

                    if (hasCameraPermission) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(2.dp, GlassAmberBorderBrush, RoundedCornerShape(20.dp))
                        ) {
                            com.example.ui.admin.CameraScannerPreview(
                                onCodeScanned = { rawCode ->
                                    val code = rawCode.removePrefix("guardlink_pair:").trim().uppercase()
                                    if (code.isNotEmpty() && !isPairingOnline) {
                                        isScanningAdminQr = false
                                        adminCodeInput = code
                                        isPairingOnline = true
                                        pairError = null
                                        FirebaseManager.pairDeviceByCode(context, code,
                                            onSuccess = {
                                                isPairingOnline = false
                                                adminCodeInput = ""
                                            },
                                            onFailure = { err ->
                                                isPairingOnline = false
                                                pairError = err
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    } else {
                        Button(
                            onClick = { cameraLauncher.launch(android.Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(100.dp))
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Camera Permission", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { isScanningAdminQr = false },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.border(1.dp, GlassBorderBrush, RoundedCornerShape(100.dp))
                    ) {
                        Text("CANCEL SCANNING", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "ENTER ADMIN PAIRING CODE",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "If you have the Admin's QR or 6-character code (from their 'My QR' tab), enter it below to initiate direct link.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = adminCodeInput,
                                onValueChange = {
                                    adminCodeInput = it.uppercase().take(6)
                                    pairError = null
                                },
                                placeholder = { Text("e.g. ADM7K2", color = TextTertiary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceAlt,
                                    unfocusedContainerColor = SurfaceAlt,
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = Border,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(100.dp)
                            )

                            Button(
                                enabled = adminCodeInput.length >= 4 && !isPairingOnline,
                                onClick = {
                                    isPairingOnline = true
                                    pairError = null
                                    FirebaseManager.pairDeviceByCode(context, adminCodeInput,
                                        onSuccess = {
                                            isPairingOnline = false
                                            adminCodeInput = ""
                                        },
                                        onFailure = { err ->
                                            isPairingOnline = false
                                            pairError = err
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                modifier = Modifier
                                    .height(48.dp)
                                    .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                            ) {
                                if (isPairingOnline) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp))
                                } else {
                                    Text("CONNECT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        pairError?.let { err ->
                            Text(err, color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            onClick = { isScanningAdminQr = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GlassAmberBorderBrush, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(AccentAmber.copy(alpha = 0.15f), CircleShape)
                                        .border(1.dp, GlassAmberBorderBrush, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SCAN ADMIN SCREEN QR", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("Point camera at admin dashboard QR", color = TextSecondary, fontSize = 11.sp)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Exit button
        Button(
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt),
            shape = RoundedCornerShape(100.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GlassRedBorderBrush, RoundedCornerShape(100.dp))
                .height(48.dp),
            onClick = onExit
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Cancel & Stop Service",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = AccentRed
            )
        }
    }
}

@Composable
fun QrCodeView(data: String, modifier: Modifier = Modifier) {
    var qrBitmap by remember(data) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var hasError by remember(data) { mutableStateOf(false) }

    LaunchedEffect(data) {
        if (data.isBlank()) {
            qrBitmap = null
            hasError = false
            return@LaunchedEffect
        }
        try {
            val bitmap = withContext(Dispatchers.Default) {
                val size = 256
                val hints = mapOf(
                    com.google.zxing.EncodeHintType.MARGIN to 1,
                    com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
                )
                val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                    data,
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    size,
                    size,
                    hints
                )
                val width = bitMatrix.width
                val height = bitMatrix.height
                val pixels = IntArray(width * height)
                val black = android.graphics.Color.BLACK
                val white = android.graphics.Color.WHITE
                for (y in 0 until height) {
                    val offset = y * width
                    for (x in 0 until width) {
                        pixels[offset + x] = if (bitMatrix.get(x, y)) black else white
                    }
                }
                val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                bmp.setPixels(pixels, 0, width, 0, 0, width, height)
                bmp.asImageBitmap()
            }
            qrBitmap = bitmap
            hasError = false
        } catch (e: Exception) {
            hasError = true
            qrBitmap = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = qrBitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = "QR Code",
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        } else if (hasError) {
            Text(
                text = "Unable to render QR",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = Color(0xFF2563EB),
                strokeWidth = 2.5.dp
            )
        }
    }
}

// Simple color helper for ChoiceGreyState
private val ChoiceGreyState = Color(0xFF323F4E)

@Composable
fun UserBroadcastEqualizer(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFB300)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "user_audio_eq")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(310, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 5f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h4"
    )

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(h1, h2, h3, h4).forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(color)
            )
        }
    }
}

