package com.example.ui.admin

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.example.data.DiscoveredDevice
import com.example.data.StateManager
import com.example.network.NetworkScanner
import com.example.ui.theme.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val scanProgressText by viewModel.scanProgressText.collectAsState()

    var selectedTab by remember { mutableStateOf("home") } // "home", "list", "settings"
    var showBlockModalForDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showCameraModalForDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showScreenModalForDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showScheduleManager by remember { mutableStateOf(false) }
    var showManualDeviceManager by remember { mutableStateOf(false) }
    var showDeviceControlsForDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    val cameraFeeds by viewModel.cameraFeeds.collectAsState(initial = emptyMap())
    val screenFeeds by viewModel.screenFeeds.collectAsState(initial = emptyMap())
    val cameraAudio by viewModel.cameraAudio.collectAsState(initial = emptyMap())
    val isConnected by com.example.network.FirebaseManager.isFirebaseConnected.collectAsState(initial = false)
    val isScreenAuthorized by com.example.camera.ScreenCaptureManager.isScreenCaptureAuthorized.collectAsState()
    val localIp = "Cloud Mode"

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val pairedDeviceIds by StateManager.pairedDeviceIds.collectAsState()

    val connectedDevices = remember(devices, pairedDeviceIds, currentTime) {
        devices.filter { dev ->
            val isNotSelf = dev.ip != com.example.network.FirebaseManager.currentMyDeviceId &&
                            !dev.name.equals(StateManager.deviceName.value, ignoreCase = true) &&
                            !dev.name.equals(StateManager.adminName.value, ignoreCase = true)
            val isStrictlyPaired = pairedDeviceIds.contains(dev.ip)
            isNotSelf && isStrictlyPaired
        }.map { dev ->
            val isLive = (currentTime - dev.lastSeen) <= 60000L && dev.status != "offline" && dev.status != "inactive"
            if (!isLive) {
                dev.copy(status = "inactive")
            } else {
                dev
            }
        }
    }

    val adminLabel = remember {
        derivedStateOf {
            val name = StateManager.adminName.value.ifEmpty {
                StateManager.deviceName.value.ifEmpty { "This Phone" }
            }
            name
        }
    }

    // High-tech pulsating beacon animation for live connectivity
    val pulseTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Smooth spinning animation for refresh icon when scan is in progress
    val scanInfiniteTransition = rememberInfiniteTransition(label = "scanInfiniteTransition")
    val scanRotation by scanInfiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanRotation"
    )

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            com.example.camera.ScreenCaptureManager.setScreenCaptureIntentData(result.resultCode, result.data!!)
        }
    }

    // On launch, scan automatically once
    LaunchedEffect(Unit) {
        if (devices.isEmpty() && !isScanning) {
            viewModel.startSubnetScan(context)
        }
    }

    // Intercept back key pressed / back swipe gesture to dynamically navigate back
    androidx.activity.compose.BackHandler(
        enabled = showScreenModalForDevice != null ||
                  showCameraModalForDevice != null ||
                  showBlockModalForDevice != null ||
                  showScheduleManager ||
                  showManualDeviceManager ||
                  showDeviceControlsForDevice != null ||
                  selectedTab != "home"
    ) {
        when {
            showScreenModalForDevice != null -> showScreenModalForDevice = null
            showCameraModalForDevice != null -> showCameraModalForDevice = null
            showBlockModalForDevice != null -> showBlockModalForDevice = null
            showScheduleManager -> showScheduleManager = false
            showManualDeviceManager -> showManualDeviceManager = false
            showDeviceControlsForDevice != null -> showDeviceControlsForDevice = null
            selectedTab != "home" -> selectedTab = "home"
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (showDeviceControlsForDevice != null) {
                val device = showDeviceControlsForDevice!!
                val freshDevice = devices.find { it.ip == device.ip } ?: device
                DeviceControlsScreen(
                    device = freshDevice,
                    viewModel = viewModel,
                    onDismiss = { showDeviceControlsForDevice = null },
                    onBlockClick = { showBlockModalForDevice = freshDevice },
                    onCameraClick = { showCameraModalForDevice = freshDevice },
                    onScreenClick = { showScreenModalForDevice = freshDevice },
                    onScheduleClick = { showScheduleManager = true }
                )
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                         scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = LinearOutSlowInEasing)))
                            .togetherWith(fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)))
                    },
                    label = "tabTransition",
                    modifier = Modifier.fillMaxSize()
                ) { currentTab ->
                    when (currentTab) {
                    "home" -> {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 8.dp)
                        ) {
                            // Space-Maximized Compact Top Header (Relocated Sync & Name Indicator)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, bottom = if (isLandscape) 2.dp else 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Relocated Sync Status & Phone Name Indicator
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF141B26), RoundedCornerShape(100.dp))
                                        .border(1.dp, Color(0xFF263347), RoundedCornerShape(100.dp))
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (isConnected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .graphicsLayer {
                                                            scaleX = pulseScale
                                                            scaleY = pulseScale
                                                            alpha = pulseAlpha
                                                        }
                                                        .background(Color(0xFF22C55E), CircleShape)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B), CircleShape)
                                            )
                                        }
                                        Text(
                                            text = if (isConnected) "Synced" else "Connecting",
                                            color = if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .background(Color(0xFF4A5568), CircleShape)
                                        )
                                        Text(
                                            text = adminLabel.value,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Text(
                                    text = "GuardLink",
                                    color = Color.White,
                                    fontSize = if (isLandscape) 20.sp else 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 10.dp))

            // Progress state if scanning
            AnimatedVisibility(
                visible = isScanning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ScanProgressSection(progress = scanProgress, text = scanProgressText)
            }

            if (isScanning) {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // QUICK ACTIONS Headline
            Text(
                text = "QUICK ACTIONS",
                color = Color(0xFF7E8B9E),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Actions (2x2 Grid)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Remote Lock & Pair Device
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Remote Lock
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF241016)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(116.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val target = connectedDevices.firstOrNull() ?: devices.firstOrNull()
                                if (target != null) {
                                    showBlockModalForDevice = target
                                } else {
                                    showScheduleManager = true
                                }
                            }
                            .border(1.2.dp, Color(0xFF5A1A24), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Remote Lock",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Remote Lock",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Instantly lock screen",
                                    color = Color(0xFF8F98A8),
                                    fontSize = 11.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 2. Lockdown Scheduler (replaces Pair Device)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(116.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showScheduleManager = true }
                            .border(1.2.dp, Color(0xFF252E40), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Lockdown Scheduler",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Lock Schedule",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Automated timed locks",
                                    color = Color(0xFF8F98A8),
                                    fontSize = 11.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Row 2: Share Screen & Voice Alert
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 3. Share Screen
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(116.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (!isScreenAuthorized) {
                                    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? android.media.projection.MediaProjectionManager
                                    val intent = mpm?.createScreenCaptureIntent()
                                    if (intent != null) {
                                        mediaProjectionLauncher.launch(intent)
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Screen sharing is active", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .border(1.2.dp, if (isScreenAuthorized) Color(0xFF1E3A5F) else Color(0xFF252E40), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .border(1.5.dp, Color(0xFF94A3B8), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Share Screen",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Share Screen",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isScreenAuthorized) "Broadcasting active" else "Broadcast screen live",
                                    color = Color(0xFF8F98A8),
                                    fontSize = 11.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 4. Voice Alert
                    var showBroadcastDialog by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF241C10)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(116.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val targetDevices = if (connectedDevices.isNotEmpty()) connectedDevices else devices
                                if (targetDevices.isNotEmpty()) {
                                    showBroadcastDialog = true
                                } else {
                                    android.widget.Toast.makeText(context, "No connected devices to alert", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .border(1.2.dp, Color(0xFF4D3818), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "Voice Alert",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Voice Alert",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Speak message out loud",
                                    color = Color(0xFF8F98A8),
                                    fontSize = 11.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (showBroadcastDialog) {
                        val themeBlur = LocalThemeBlur.current
                        DisposableEffect(Unit) {
                            themeBlur.value = true
                            onDispose {
                                themeBlur.value = false
                            }
                        }
                        var broadcastMsg by remember { mutableStateOf("") }
                        val quickPresets = listOf(
                            "Dinner is ready, please come down! 🍽️",
                            "Screen time is up, please put the device away. ⏳",
                            "Please call me back immediately. 📞",
                            "Emergency alert: Check your device now. 🚨",
                            "Time for homework and studying. 📚",
                            "Bedtime: Please plug your device into the charger. 🛌"
                        )

                        val configuration = LocalConfiguration.current
                        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                        AlertDialog(
                            onDismissRequest = { showBroadcastDialog = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false),
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .widthIn(max = 480.dp)
                                .heightIn(max = if (isLandscape) 340.dp else 660.dp)
                                .border(1.dp, Color(0xFF2B364A), RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(Color(0xFF33230C), CircleShape)
                                                .border(1.dp, Color(0xFF5C3C15), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Campaign,
                                                contentDescription = null,
                                                tint = Color(0xFFFBBF24),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(
                                                text = "VOICE ALERT",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Speak message out loud on companion",
                                                color = Color(0xFF8896AB),
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { showBroadcastDialog = false },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Target Companion Device Pill
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF131722), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0xFF263245), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(Color(0xFF4ADE80), CircleShape)
                                            )
                                            val targetCount = if (connectedDevices.isNotEmpty()) connectedDevices.size else devices.size
                                            Text(
                                                text = if (connectedDevices.isNotEmpty()) {
                                                    "Targeting $targetCount active companion phone(s)"
                                                } else {
                                                    "Targeting all registered devices"
                                                },
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Quick Presets Header with Equalizer Visualizer
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "QUICK PRESETS",
                                            color = Color(0xFF7E8B9E),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                        if (broadcastMsg.isNotBlank()) {
                                            AudioEqualizerVisualizer()
                                        }
                                    }

                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(quickPresets) { preset ->
                                            val isSelected = broadcastMsg == preset
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isSelected) Color(0xFF33230C) else Color(0xFF131722),
                                                        RoundedCornerShape(100.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Color(0xFFF59E0B) else Color(0xFF2B364A),
                                                        RoundedCornerShape(100.dp)
                                                    )
                                                    .clip(RoundedCornerShape(100.dp))
                                                    .clickable { broadcastMsg = preset }
                                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    text = preset,
                                                    color = if (isSelected) Color(0xFFFBBF24) else Color(0xFFCBD5E1),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = broadcastMsg,
                                        onValueChange = { broadcastMsg = it },
                                        placeholder = { Text("Type announcement to read aloud...", color = Color(0xFF64748B), fontSize = 13.sp) },
                                        shape = RoundedCornerShape(16.dp),
                                        trailingIcon = {
                                            if (broadcastMsg.isNotEmpty()) {
                                                IconButton(onClick = { broadcastMsg = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF131722),
                                            unfocusedContainerColor = Color(0xFF131722),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFFF59E0B),
                                            unfocusedBorderColor = Color(0xFF2B364A)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4
                                    )

                                    // Preview local audio
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${broadcastMsg.length} characters",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                        Button(
                                            onClick = {
                                                if (broadcastMsg.isNotBlank()) {
                                                    com.example.tts.TextToSpeechManager.speak(context, broadcastMsg)
                                                } else {
                                                    android.widget.Toast.makeText(context, "Type text to test audio preview", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF212B3B),
                                                contentColor = Color(0xFF93C5FD)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2F3C52)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF60A5FA))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("PREVIEW TTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD))
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                val targetIps = if (connectedDevices.isNotEmpty()) connectedDevices.map { it.ip } else devices.map { it.ip }
                                Button(
                                    onClick = {
                                        if (broadcastMsg.isNotBlank()) {
                                            val adminName = StateManager.adminName.value.ifEmpty { "Admin" }
                                            com.example.network.FirebaseManager.sendBroadcastAnnouncement(targetIps, broadcastMsg, adminName)
                                            android.widget.Toast.makeText(context, "📢 Voice alert dispatched to ${targetIps.size} device(s)", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        showBroadcastDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD97706),
                                        disabledContainerColor = Color(0xFF242A36)
                                    ),
                                    shape = RoundedCornerShape(100.dp),
                                    enabled = broadcastMsg.isNotBlank(),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = null,
                                        tint = if (broadcastMsg.isNotBlank()) Color.Black else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TRANSMIT VOICE (${targetIps.size})",
                                        fontWeight = FontWeight.Bold,
                                        color = if (broadcastMsg.isNotBlank()) Color.Black else Color(0xFF64748B),
                                        fontSize = 12.sp
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showBroadcastDialog = false },
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("CANCEL", color = Color(0xFF8896AB), fontWeight = FontWeight.Medium)
                                }
                            },
                            containerColor = Color(0xFF191F2C)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results Headline
            // CONNECTED DEVICES Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONNECTED DEVICES",
                    color = Color(0xFF7E8B9E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (connectedDevices.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A1519))
                                .border(1.dp, Color(0xFF5A2228), CircleShape)
                                .clickable { showClearAllDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All Devices",
                                tint = AccentRed.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161E2E))
                            .border(1.dp, Color(0xFF2B3A52), CircleShape)
                            .clickable(enabled = !isScanning) { viewModel.refreshAll(context) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isScanning) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = if (isScanning) scanRotation else 0f }
                        )
                    }

                    val activeCount = connectedDevices.count { it.status == "active" || it.status == "online" }
                    val hasActive = activeCount > 0
                    Row(
                        modifier = Modifier
                            .background(if (hasActive) Color(0xFF07271A) else Color(0xFF1E2638), RoundedCornerShape(100.dp))
                            .border(1.dp, if (hasActive) Color(0xFF135D38) else Color(0xFF2B3A52), RoundedCornerShape(100.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (hasActive) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .graphicsLayer {
                                            scaleX = pulseScale
                                            scaleY = pulseScale
                                            alpha = pulseAlpha
                                        }
                                        .background(Color(0xFF22C55E), CircleShape)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        when {
                                            hasActive -> Color(0xFF22C55E)
                                            connectedDevices.isNotEmpty() -> Color(0xFF94A3B8)
                                            else -> Color(0xFF60A5FA)
                                        },
                                        CircleShape
                                    )
                            )
                        }
                        Text(
                            text = when {
                                hasActive -> "$activeCount ACTIVE"
                                connectedDevices.isNotEmpty() -> "${connectedDevices.size} PAIRED"
                                else -> "READY"
                            },
                            color = when {
                                hasActive -> Color(0xFF22C55E)
                                connectedDevices.isNotEmpty() -> Color(0xFF94A3B8)
                                else -> Color(0xFF60A5FA)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Connected Device List (Filtered to show only connected companion devices)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PullToRefreshBox(
                    state = rememberPullToRefreshState(),
                    isRefreshing = isScanning,
                    onRefresh = { viewModel.refreshAll(context) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (connectedDevices.isEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF191F2C)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFF2B364A),
                                        shape = RoundedCornerShape(20.dp)
                                    ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(Color(0xFF2B3547), CircleShape)
                                                .border(1.dp, Color(0xFF38455A), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "Device",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "No Connected Devices",
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Tap 'Pair Device' to link companion phone",
                                                color = Color(0xFF8896AB),
                                                fontSize = 12.sp
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF0F3824), RoundedCornerShape(100.dp))
                                                .border(1.dp, Color(0xFF166534), RoundedCornerShape(100.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "READY",
                                                color = Color(0xFF4ADE80),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color(0xFF1E3A5F))
                                            .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                                            .clickable { showManualDeviceManager = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+ Pair Device (QR / Code)",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(90.dp))
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(connectedDevices, key = { it.ip }) { device ->
                                DeviceCard(
                                    device = device,
                                    onViewScreen = { showScreenModalForDevice = device },
                                    onMoreOptions = { showDeviceControlsForDevice = device }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(90.dp))
                            }
                        }
                    }
                }
            }
        }
                    }
                    "list" -> {
                        FullDeviceListTab(
                            connectedDevices = connectedDevices,
                            devices = devices,
                            viewModel = viewModel,
                            isScanning = isScanning,
                            pulseScale = pulseScale,
                            pulseAlpha = pulseAlpha,
                            isConnected = isConnected,
                            adminLabel = adminLabel,
                            onViewScreen = { showScreenModalForDevice = it },
                            onMoreOptions = { showDeviceControlsForDevice = it },
                            onClearAll = { showClearAllDialog = true },
                            onPairClick = { showManualDeviceManager = true }
                        )
                    }
                    "settings" -> {
                        AdminSettingsScreen(
                            pulseScale = pulseScale,
                            pulseAlpha = pulseAlpha,
                            isConnected = isConnected,
                            adminLabel = adminLabel,
                            onNavigateToDashboard = { selectedTab = "home" }
                        )
                    }
                }
            }
        }

        // Clear All Devices Confirmation Dialog
        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Clear All Paired Devices?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "This will disconnect and unpair all devices currently linked to this dashboard. Only devices you explicitly re-pair via QR code or 6-digit code scanning will appear.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllDevices()
                            showClearAllDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentRed)
                    ) {
                        Text("CLEAR ALL", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearAllDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("CANCEL")
                    }
                },
                containerColor = SurfaceAlt,
                iconContentColor = AccentRed
            )
        }

        // Custom beautiful Block Modal Dialog
        if (showBlockModalForDevice != null) {
            val device = showBlockModalForDevice!!
            BlockModal(
                device = device,
                onDismiss = { showBlockModalForDevice = null },
                onSendBlock = { message, password, timerSeconds, imageBase64 ->
                    viewModel.blockScreen(device.ip, message, password, timerSeconds, imageBase64 ?: "")
                    showBlockModalForDevice = null
                }
            )
        }

        // Live Camera Recon Dialog
        if (showCameraModalForDevice != null) {
            val device = showCameraModalForDevice!!
            val freshDevice = devices.find { it.ip == device.ip } ?: device
            LaunchedEffect(freshDevice.ip) {
                viewModel.startCameraStream(freshDevice.ip)
            }
            CameraStreamModal(
                device = freshDevice,
                cameraFeedBase64 = cameraFeeds[freshDevice.ip],
                cameraFeedAudioBase64 = cameraAudio[freshDevice.ip],
                onToggleLens = { newLens ->
                    viewModel.setCameraLens(freshDevice.ip, newLens)
                },
                onDismiss = {
                    viewModel.stopCameraStream(freshDevice.ip)
                    showCameraModalForDevice = null
                }
            )
        }

        // Live Screen Stream Modal
        if (showScreenModalForDevice != null) {
            val device = showScreenModalForDevice!!
            val freshDevice = devices.find { it.ip == device.ip } ?: device
            LaunchedEffect(freshDevice.ip) {
                viewModel.startViewingScreen(freshDevice.ip)
            }
            ScreenStreamModal(
                device = freshDevice,
                screenFeedBase64 = screenFeeds[freshDevice.ip],
                onDismiss = {
                    viewModel.stopViewingScreen(freshDevice.ip)
                    showScreenModalForDevice = null
                }
            )
        }

        // Custom Schedule Manager Modal
        if (showScheduleManager) {
            ScheduleManagerModal(
                viewModel = viewModel,
                onDismiss = { showScheduleManager = false }
            )
        }

        // Custom Manual Device Manager Modal
        if (showManualDeviceManager) {
            DevicePairingAndManagerModal(
                viewModel = viewModel,
                onDismiss = { showManualDeviceManager = false }
            )
        }

        // Floating Navigation Bar: |Home|List|settings| |+|
        if (showDeviceControlsForDevice == null &&
            showScreenModalForDevice == null &&
            showCameraModalForDevice == null &&
            showBlockModalForDevice == null &&
            !showScheduleManager &&
            !showManualDeviceManager
        ) {
            FloatingPillBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onPlusClick = { showManualDeviceManager = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            )
        }
    }
}
}

@Composable
fun FloatingPillBottomNav(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val plusInteractionSource = remember { MutableInteractionSource() }
    val isPlusPressed by plusInteractionSource.collectIsPressedAsState()
    val plusScale by animateFloatAsState(
        targetValue = if (isPlusPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "plusScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Segmented Navigation Pill: | Home | List | Settings |
        Surface(
            color = Color(0xFF121722).copy(alpha = 0.96f),
            shape = RoundedCornerShape(100.dp),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF2A364A)),
            shadowElevation = 14.dp,
            modifier = Modifier.height(if (isLandscape) 48.dp else 56.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = if (isLandscape) 3.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val navItems = listOf(
                    Triple("home", "Home", Icons.Default.Home),
                    Triple("list", "List", Icons.Default.FormatListBulleted),
                    Triple("settings", "Settings", Icons.Default.Settings)
                )

                navItems.forEach { (id, label, icon) ->
                    val isSelected = selectedTab == id
                    val animatedBg by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF1E2D48) else Color.Transparent,
                        animationSpec = tween(220, easing = LinearOutSlowInEasing),
                        label = "animatedBg"
                    )
                    val animatedBorder by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color.Transparent,
                        animationSpec = tween(220, easing = LinearOutSlowInEasing),
                        label = "animatedBorder"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF38BDF8) else Color(0xFF8F9CAE),
                        animationSpec = tween(220),
                        label = "contentColor"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(animatedBg)
                            .border(1.dp, animatedBorder, RoundedCornerShape(100.dp))
                            .clickable { onTabSelected(id) }
                            .padding(
                                horizontal = if (isLandscape) 12.dp else 15.dp,
                                vertical = if (isLandscape) 6.dp else 8.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(if (isLandscape) 16.dp else 18.dp)
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(180)) + expandHorizontally(),
                                exit = fadeOut(tween(140)) + shrinkHorizontally()
                            ) {
                                Text(
                                    text = label,
                                    color = contentColor,
                                    fontSize = if (isLandscape) 12.sp else 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            if (!isSelected) {
                                Text(
                                    text = label,
                                    color = contentColor,
                                    fontSize = if (isLandscape) 11.sp else 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Distinct "+" Button Pill: |+|
        Surface(
            color = Color(0xFF2563EB),
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF60A5FA).copy(alpha = 0.6f)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .size(if (isLandscape) 46.dp else 54.dp)
                .graphicsLayer {
                    scaleX = plusScale
                    scaleY = plusScale
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = plusInteractionSource,
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onPlusClick
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Pair Companion Device",
                    tint = Color.White,
                    modifier = Modifier.size(if (isLandscape) 22.dp else 26.dp)
                )
            }
        }
    }
}

@Composable
fun FullDeviceListTab(
    connectedDevices: List<DiscoveredDevice>,
    devices: List<DiscoveredDevice>,
    viewModel: AdminDashboardViewModel,
    isScanning: Boolean,
    pulseScale: Float,
    pulseAlpha: Float,
    isConnected: Boolean,
    adminLabel: State<String>,
    onViewScreen: (DiscoveredDevice) -> Unit,
    onMoreOptions: (DiscoveredDevice) -> Unit,
    onClearAll: () -> Unit,
    onPairClick: () -> Unit
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val scanInfiniteTransition = rememberInfiniteTransition(label = "scanListTransition")
    val scanRotation by scanInfiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanListRotation"
    )

    var searchQuery by remember { mutableStateOf("") }
    val filteredDevices = remember(connectedDevices, searchQuery) {
        if (searchQuery.isBlank()) {
            connectedDevices
        } else {
            val q = searchQuery.trim().lowercase()
            connectedDevices.filter {
                it.name.lowercase().contains(q) ||
                it.ip.lowercase().contains(q) ||
                it.activeApp.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 8.dp)
    ) {
        // Space-Maximized Compact Top Header (Relocated Sync & Name Indicator)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = if (isLandscape) 2.dp else 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Relocated Sync Status & Phone Name Indicator
            Box(
                modifier = Modifier
                    .background(Color(0xFF141B26), RoundedCornerShape(100.dp))
                    .border(1.dp, Color(0xFF263347), RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isConnected) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                        alpha = pulseAlpha
                                    }
                                    .background(Color(0xFF22C55E), CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B), CircleShape)
                        )
                    }
                    Text(
                        text = if (isConnected) "Synced" else "Connecting",
                        color = if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(Color(0xFF4A5568), CircleShape)
                    )
                    Text(
                        text = adminLabel.value,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = "Connected Devices",
                color = Color.White,
                fontSize = if (isLandscape) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search connected devices...", color = Color(0xFF64748B), fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141923),
                unfocusedContainerColor = Color(0xFF141923),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF243042),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF38BDF8)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 46.dp else 52.dp)
        )

        Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 12.dp))

        // Section Bar: Count & Actions (Scan & Clear)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ALL COMPANIONS",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(100.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${connectedDevices.size}",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Scan / Refresh Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161E2E))
                        .border(1.dp, Color(0xFF2B3A52), CircleShape)
                        .clickable(enabled = !isScanning) { viewModel.startSubnetScan(context) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh scan",
                        tint = if (isScanning) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = if (isScanning) scanRotation else 0f }
                    )
                }

                // Clear All Button
                if (connectedDevices.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A1519))
                            .border(1.dp, Color(0xFF5A2228), CircleShape)
                            .clickable { onClearAll() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF191F2C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF2B364A), RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF2B3547), CircleShape)
                                .border(1.dp, Color(0xFF38455A), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = if (searchQuery.isNotEmpty()) "No Matching Devices" else "No Connected Devices",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (searchQuery.isNotEmpty())
                                "No companion devices found matching \"$searchQuery\"."
                            else
                                "Tap '+' or the button below to link a companion device via QR code or 6-digit PIN.",
                            color = Color(0xFF8896AB),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        if (searchQuery.isEmpty()) {
                            Button(
                                onClick = onPairClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pair Companion Device",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredDevices, key = { it.ip }) { device ->
                    DeviceCard(
                        device = device,
                        onViewScreen = { onViewScreen(device) },
                        onMoreOptions = { onMoreOptions(device) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        }
    }
}

@Composable
fun ScanProgressSection(progress: Float, text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    color = AccentAmber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = AccentAmber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = progress,
                color = AccentAmber,
                trackColor = Color(0xFF231E17),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun DeviceCard(
    device: DiscoveredDevice,
    onViewScreen: () -> Unit = {},
    onMoreOptions: () -> Unit = {}
) {
    val context = LocalContext.current
    val truncatedId = remember(device.ip) {
        val clean = device.ip.filter { it.isLetterOrDigit() }
        if (clean.length > 4) "ID: ••••${clean.takeLast(4)}" else "ID: $clean"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "device_card_scale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191F2C)),
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = Color(0xFF2B364A),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onMoreOptions() },
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Row: Avatar | Name & ID | Status Badge & Battery Indicator
            val isInactive = device.status == "inactive" || device.status == "offline"
            val isActive = !isInactive && (device.status == "active" || device.status == "online")
            val isBlocked = !isInactive && device.status == "blocked"

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Avatar Container with Animated Beacon Ripple when Active
                Box(contentAlignment = Alignment.Center) {
                    if (isActive) {
                        val beaconTransition = rememberInfiniteTransition(label = "beacon_ripple")
                        val beaconScale by beaconTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.38f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "beacon_scale"
                        )
                        val beaconAlpha by beaconTransition.animateFloat(
                            initialValue = 0.55f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "beacon_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .scale(beaconScale)
                                .background(Color(0xFF10B981).copy(alpha = beaconAlpha), CircleShape)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isInactive) Color(0xFF1B2333) else if (isActive) Color(0xFF122C2A) else Color(0xFF2B3547),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (isInactive) Color(0xFF263249) else if (isActive) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF38455A),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Device",
                            tint = if (isInactive) Color(0xFF64748B) else if (isActive) Color(0xFF34D399) else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & Truncated UUID with copy action
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Device ID", device.ip))
                            android.widget.Toast.makeText(context, "ID copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = truncatedId,
                            color = Color(0xFF8896AB),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy ID",
                            tint = Color(0xFF8896AB),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge & Battery Indicator
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(
                                when {
                                    isActive -> Color(0xFF0F3824)
                                    isBlocked -> Color(0xFF381016)
                                    isInactive -> Color(0xFF1E2638)
                                    else -> Color(0xFF33230C)
                                },
                                RoundedCornerShape(100.dp)
                            )
                            .border(
                                1.dp,
                                when {
                                    isActive -> Color(0xFF166534)
                                    isBlocked -> Color(0xFF651624)
                                    isInactive -> Color(0xFF334155)
                                    else -> Color(0xFF5C3C15)
                                },
                                RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        when {
                                            isActive -> Color(0xFF22C55E)
                                            isBlocked -> Color(0xFFEF4444)
                                            isInactive -> Color(0xFF94A3B8)
                                            else -> Color(0xFFFBBF24)
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = when {
                                    isActive -> "ACTIVE"
                                    isBlocked -> "BLOCKED"
                                    isInactive -> "INACTIVE"
                                    else -> device.status.uppercase()
                                },
                                color = when {
                                    isActive -> Color(0xFF4ADE80)
                                    isBlocked -> Color(0xFFF87171)
                                    isInactive -> Color(0xFF94A3B8)
                                    else -> Color(0xFFFBBF24)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Battery Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (device.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Battery",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${device.batteryLevel}%",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Split Action Pill Buttons: View Screen & Manage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // View Screen (Electric Blue pill button / Muted when inactive)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (isInactive) Color(0xFF142236) else Color(0xFF1E3A5F))
                        .border(1.dp, if (isInactive) Color(0xFF1E3352) else Color(0xFF2563EB).copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                        .clickable { onViewScreen() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "View Screen",
                        color = if (isInactive) Color(0xFF7E8B9E) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Manage (Dark Slate pill button)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFF262E3E))
                        .border(1.dp, Color(0xFF374358), RoundedCornerShape(100.dp))
                        .clickable { onMoreOptions() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Manage",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceControlsScreen(
    device: DiscoveredDevice,
    viewModel: AdminDashboardViewModel,
    onDismiss: () -> Unit,
    onBlockClick: () -> Unit,
    onCameraClick: () -> Unit,
    onScreenClick: () -> Unit,
    onScheduleClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showRemoveDialog by remember { mutableStateOf(false) }

    // Intercom Audio State & Walkie-Talkie logic
    val cameraAudio by viewModel.cameraAudio.collectAsState(initial = emptyMap())
    val deviceAudioSegment = cameraAudio[device.ip]
    var isSpeaking by remember { mutableStateOf(false) }

    // Automatically play target device's voice through speaker when user is transmitting
    LaunchedEffect(deviceAudioSegment, device.deviceSpeaking, isSpeaking) {
        if (!deviceAudioSegment.isNullOrEmpty() && device.deviceSpeaking && !isSpeaking) {
            com.example.network.AudioStreamManager.playAudioSegment(deviceAudioSegment)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isSpeaking = true
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required for Intercom", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                com.example.network.AudioStreamManager.startRecording(context)
                try {
                    while (isSpeaking) {
                        val base64Bytes = com.example.network.AudioStreamManager.getLatestAudioSegmentBase64()
                        if (base64Bytes.isNotEmpty()) {
                            com.example.network.FirebaseManager.sendIntercomAudio(device.ip, base64Bytes, true)
                        }
                        kotlinx.coroutines.delay(180L)
                    }
                } finally {
                    com.example.network.AudioStreamManager.stopRecording()
                    com.example.network.FirebaseManager.sendIntercomAudio(device.ip, "", false)
                }
            } else {
                isSpeaking = false
            }
        }
    }

    // Soundwave infinite transition for intercom & beacon
    val intercomTransition = rememberInfiniteTransition(label = "intercom_wave")
    val waveScale by intercomTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_scale"
    )

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = AccentRed
                    )
                    Text(
                        text = "Unpair & Remove Device?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently unpair ${device.name}? This removes all local device registrations, synchronization channels, real-time monitoring feeds, and security locks.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeDevice(device.ip)
                        showRemoveDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CONFIRM UNPAIR", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    Text("CANCEL")
                }
            },
            containerColor = Color(0xFF131722),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, Border, RoundedCornerShape(20.dp))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val isControlInactive = device.status == "inactive" || device.status == "offline"
            val isControlActive = !isControlInactive && (device.status == "online" || device.status == "active")
            val isControlBlocked = !isControlInactive && device.status == "blocked"

            // --- Top Hero Navigation Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color(0xFF141923), CircleShape)
                            .border(1.dp, Border, CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Device Avatar with glowing status ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF1B2332), CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = when {
                                    isControlActive -> AccentGreen
                                    isControlBlocked -> AccentRed
                                    else -> Color(0xFF64748B)
                                },
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Device",
                            tint = when {
                                isControlActive -> AccentGreen
                                isControlBlocked -> AccentRed
                                else -> TextSecondary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = device.name,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Device ID", device.ip))
                                android.widget.Toast.makeText(context, "Device ID copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            val shortId = remember(device.ip) {
                                val clean = device.ip.filter { it.isLetterOrDigit() }
                                if (clean.length > 6) "ID: ••••${clean.takeLast(6)}" else "ID: $clean"
                            }
                            Text(
                                text = shortId,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Live Status Pill Badge
                Box(
                    modifier = Modifier
                        .background(
                            when {
                                isControlActive -> Color(0xFF0D3320)
                                isControlBlocked -> Color(0xFF381016)
                                else -> Color(0xFF1E2638)
                            },
                            RoundedCornerShape(100.dp)
                        )
                        .border(
                            1.dp,
                            when {
                                isControlActive -> Color(0xFF166534)
                                isControlBlocked -> Color(0xFF651624)
                                else -> Color(0xFF334155)
                            },
                            RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    when {
                                        isControlActive -> Color(0xFF22C55E)
                                        isControlBlocked -> Color(0xFFEF4444)
                                        else -> Color(0xFF94A3B8)
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            text = when {
                                isControlActive -> "ONLINE"
                                isControlBlocked -> "BLOCKED"
                                else -> "INACTIVE"
                            },
                            color = when {
                                isControlActive -> Color(0xFF4ADE80)
                                isControlBlocked -> Color(0xFFF87171)
                                else -> Color(0xFF94A3B8)
                            },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- Real-Time Telemetry & Status HUD Bar ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131824)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Border, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Battery item
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (device.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Battery",
                            tint = if (device.batteryLevel < 20) AccentRed else if (device.isCharging) AccentGreen else Color(0xFF60A5FA),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text("BATTERY", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                            Text("${device.batteryLevel}%${if (device.isCharging) " ⚡" else ""}", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderSubtle))

                    // App item
                    Row(
                        modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Active App",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text("FOREGROUND", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                            Text(device.activeApp.take(12), color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderSubtle))

                    // Audio Mode item
                    Row(
                        modifier = Modifier.weight(0.9f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Audio Mode",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text("RINGER", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                            Text(device.ringerMode.take(10), color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- Quick Action Shortcuts Grid ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Mirror Screen Quick Button
                Button(
                    onClick = onScreenClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283C)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.ScreenShare, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Screen", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // 2. Camera Recon Quick Button
                Button(
                    onClick = onCameraClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283C)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // 3. Siren Alarm Quick Toggle Button
                Button(
                    onClick = { viewModel.toggleRing(device.ip, !device.ringRequested) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (device.ringRequested) AccentRed else Color(0xFF1E283C)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(1.dp, if (device.ringRequested) AccentRed else Color(0xFF334155), RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = if (device.ringRequested) TextPrimary else AccentAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (device.ringRequested) "Stop Ring" else "Siren",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            var selectedTab by remember { mutableIntStateOf(0) }
            val tabTitles = listOf("Live Feeds", "Command Hub", "Lock Studio", "Audit Logs")
            val tabIcons = listOf(Icons.Default.Videocam, Icons.Default.Bolt, Icons.Default.Palette, Icons.Default.History)

            var activeTheme by remember(device.lockTheme) { mutableStateOf(device.lockTheme) }
            var activeIcon by remember(device.lockWarningIcon) { mutableStateOf(device.lockWarningIcon) }
            var activeWallpaper by remember(device.lockWallpaper) { mutableStateOf(device.lockWallpaper) }

            var adminChatMessages by remember { mutableStateOf<List<com.example.data.ChatMessage>>(emptyList()) }
            var adminChatInputText by remember { mutableStateOf("") }
            var reconBroadcasts by remember { mutableStateOf<List<com.example.data.ReconBroadcast>>(emptyList()) }
            var reconTerminalInputText by remember { mutableStateOf("") }
            var selectedCommMode by remember { mutableIntStateOf(0) } // 0: Direct Chat, 1: Recon Broadcast Terminal
            var operationLogs by remember { mutableStateOf<List<com.example.data.AdminLog>>(emptyList()) }

            // Persistent Firebase listeners for real-time telemetry & sync
            DisposableEffect(device.ip) {
                val chatRef = FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("devices").child(device.ip).child("chat")
                val chatListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = mutableListOf<com.example.data.ChatMessage>()
                        for (child in snapshot.children) {
                            val id = child.child("id").getValue(String::class.java) ?: ""
                            val sender = child.child("sender").getValue(String::class.java) ?: ""
                            val msg = child.child("message").getValue(String::class.java) ?: ""
                            val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            list.add(com.example.data.ChatMessage(id, sender, msg, ts))
                        }
                        adminChatMessages = list.sortedBy { it.timestamp }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                chatRef.addValueEventListener(chatListener)

                val reconRef = FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("devices").child(device.ip).child("recon_broadcasts")
                val reconListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = mutableListOf<com.example.data.ReconBroadcast>()
                        for (child in snapshot.children) {
                            val id = child.child("id").getValue(String::class.java) ?: ""
                            val sender = child.child("sender").getValue(String::class.java) ?: "Admin"
                            val msg = child.child("message").getValue(String::class.java) ?: ""
                            val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val status = child.child("status").getValue(String::class.java) ?: "TRANSMITTED"
                            list.add(com.example.data.ReconBroadcast(id, sender, msg, ts, status))
                        }
                        reconBroadcasts = list.sortedByDescending { it.timestamp }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                reconRef.addValueEventListener(reconListener)

                val logsRef = FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("devices").child(device.ip).child("logs")
                val logsListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = mutableListOf<com.example.data.AdminLog>()
                        for (child in snapshot.children) {
                            val id = child.child("id").getValue(String::class.java) ?: ""
                            val action = child.child("action").getValue(String::class.java) ?: ""
                            val details = child.child("details").getValue(String::class.java) ?: ""
                            val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            list.add(com.example.data.AdminLog(id, action, details, ts))
                        }
                        operationLogs = list.sortedByDescending { it.timestamp }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                logsRef.addValueEventListener(logsListener)

                onDispose {
                    chatRef.removeEventListener(chatListener)
                    reconRef.removeEventListener(reconListener)
                    logsRef.removeEventListener(logsListener)
                }
            }

            // --- 4-Pill Segmented Navigation Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF131722))
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isTabSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isTabSelected) Color(0xFF253347) else Color.Transparent)
                            .border(
                                width = if (isTabSelected) 1.dp else 0.dp,
                                color = if (isTabSelected) Color(0xFF3B82F6).copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedTab = index }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = tabIcons[index],
                                contentDescription = null,
                                tint = if (isTabSelected) Color(0xFF60A5FA) else TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = title,
                                color = if (isTabSelected) TextPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Tab Contents Container ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // ==================== TAB 0: LIVE FEEDS ====================
                        // 1. Live Screen Stream Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF28354D), RoundedCornerShape(16.dp))
                                .clickable { onScreenClick() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(AccentPurple.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                                .border(1.dp, AccentPurple.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ScreenShare,
                                                contentDescription = "Screen Stream",
                                                tint = AccentPurple,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "LIVE SCREEN STREAM",
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "Zero-latency remote screen capture in RAM",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                                }

                                Button(
                                    onClick = onScreenClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C1E45)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("LAUNCH SCREEN MIRROR", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 2. Live Camera Recon Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF28354D), RoundedCornerShape(16.dp))
                                .clickable { onCameraClick() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(AccentBlue.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                                .border(1.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = "Camera Feed",
                                                tint = AccentBlue,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "LIVE CAMERA RECON",
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "Real-time Front / Rear optical stream with audio",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val nextLens = if (device.cameraLens == "front") "back" else "front"
                                            viewModel.setCameraLens(device.ip, nextLens)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF60A5FA)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                    ) {
                                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("${device.cameraLens.uppercase()} LENS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = onCameraClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .height(42.dp)
                                            .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("START CAMERA", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // 3. Intercom Push-to-Talk Walkie-Talkie Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSpeaking -> Color(0xFF152A15)
                                    device.deviceSpeaking -> Color(0xFF2A1D15)
                                    else -> Color(0xFF141A26)
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    when {
                                        isSpeaking -> AccentGreen.copy(alpha = 0.6f)
                                        device.deviceSpeaking -> Color(0xFFE57373).copy(alpha = 0.6f)
                                        else -> Color(0xFF28354D)
                                    },
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    if (isSpeaking) AccentGreen.copy(alpha = 0.25f)
                                                    else if (device.deviceSpeaking) Color(0xFFE57373).copy(alpha = 0.25f)
                                                    else AccentTeal.copy(alpha = 0.18f),
                                                    RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isSpeaking) Icons.Default.Mic else if (device.deviceSpeaking) Icons.Default.VolumeUp else Icons.Default.GraphicEq,
                                                contentDescription = "Intercom",
                                                tint = if (isSpeaking) AccentGreen else if (device.deviceSpeaking) Color(0xFFE57373) else AccentTeal,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = when {
                                                    isSpeaking -> "TRANSMITTING TO DEVICE..."
                                                    device.deviceSpeaking -> "DEVICE IS SPEAKING..."
                                                    else -> "TWO-WAY WALKIE-TALKIE"
                                                },
                                                color = if (isSpeaking) AccentGreen else if (device.deviceSpeaking) Color(0xFFE57373) else TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = if (device.deviceSpeaking) "Target user audio streaming live via speaker" else "Hold push-to-talk to speak directly to target",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // Interactive Push-to-Talk Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSpeaking) AccentGreen else if (device.deviceSpeaking) Color(0xFF4A2525) else Color(0xFF1B2636)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSpeaking) AccentGreen else if (device.deviceSpeaking) Color(0xFFE57373) else Color(0xFF2E3E56),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .pointerInput(device.deviceSpeaking) {
                                            detectTapGestures(
                                                onPress = {
                                                    if (!device.deviceSpeaking) {
                                                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                                                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                            isSpeaking = true
                                                            tryAwaitRelease()
                                                            isSpeaking = false
                                                        } else {
                                                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeaking) Icons.Default.Mic else Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = if (isSpeaking) Color.Black else if (device.deviceSpeaking) TextSecondary else Color(0xFF60A5FA),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = when {
                                                isSpeaking -> "TRANSMITTING LIVE AUDIO (RELEASE TO STOP)"
                                                device.deviceSpeaking -> "USER SPEAKING (MIC MUTED)"
                                                else -> "HOLD TO SPEAK TO TARGET PHONE"
                                            },
                                            color = if (isSpeaking) Color.Black else if (device.deviceSpeaking) TextSecondary else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Find My Device Mini-map Card
                        FindMyDeviceMapCard(device = device, viewModel = viewModel)
                    }

                    1 -> {
                        // ==================== TAB 1: COMMAND HUB ====================
                        Text(
                            text = "SECURITY INTERVENTIONS",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        // Block / Unblock Primary Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (device.status == "blocked") {
                                Button(
                                    onClick = { viewModel.unblockScreen(device.ip) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("INSTANT UNBLOCK SCREEN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black, letterSpacing = 0.5.sp)
                                }
                            } else {
                                Button(
                                    onClick = onBlockClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("LOCK SCREEN NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = 0.5.sp)
                                }

                                Button(
                                    onClick = { viewModel.toggleRing(device.ip, !device.ringRequested) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (device.ringRequested) AccentRed else Color(0xFF1E283C)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .border(1.dp, if (device.ringRequested) AccentRed else Color(0xFF334155), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = if (device.ringRequested) TextPrimary else AccentAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (device.ringRequested) "STOP SIREN" else "LOUD SIREN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                        }

                        // Voice Broadcast & Text-To-Speech Card
                        var deviceTtsText by remember(device.ip) { mutableStateOf("") }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF28354D), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(AccentAmber.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Campaign, contentDescription = "Voice Broadcast", tint = AccentAmber, modifier = Modifier.size(22.dp))
                                    }
                                    Column {
                                        Text("VOICE BROADCAST (TTS)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Type text to read aloud on this phone in real time", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }

                                // Quick preset chips
                                val targetPresets = listOf(
                                    "Screen time is up! ⏳",
                                    "Dinner is ready! 🍽️",
                                    "Please call me back. 📞",
                                    "Come here right now. 🏃",
                                    "Time for bed. 🌙"
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(targetPresets) { preset ->
                                        Surface(
                                            color = Color(0xFF1B2332),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .border(1.dp, Color(0xFF2E3D54), RoundedCornerShape(14.dp))
                                                .clickable { deviceTtsText = preset }
                                        ) {
                                            Text(
                                                text = preset,
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = deviceTtsText,
                                    onValueChange = { deviceTtsText = it },
                                    placeholder = { Text("Type announcement for ${device.name}...", color = TextSecondary, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF0F131C),
                                        unfocusedContainerColor = Color(0xFF0F131C),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = AccentAmber,
                                        unfocusedBorderColor = Border
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (deviceTtsText.isNotBlank()) {
                                                com.example.tts.TextToSpeechManager.speak(context, deviceTtsText)
                                            } else {
                                                android.widget.Toast.makeText(context, "Type text to test audio preview", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("TEST AUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (deviceTtsText.isNotBlank()) {
                                                val adminName = StateManager.adminName.value.ifEmpty { "Admin" }
                                                com.example.network.FirebaseManager.sendBroadcastAnnouncement(
                                                    listOf(device.ip),
                                                    deviceTtsText,
                                                    adminName
                                                )
                                                android.widget.Toast.makeText(context, "📢 Voice broadcast sent to ${device.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                deviceTtsText = ""
                                            }
                                        },
                                        enabled = deviceTtsText.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("SPEAK ON DEVICE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }
                        }

                        // Automated Lock Schedules Shortcut Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF28354D), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(AccentBlue.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(22.dp))
                                    }
                                    Column {
                                        Text("AUTOMATED SCHEDULES", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Bedtime, study hours, and recurring rules", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                                Button(
                                    onClick = onScheduleClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("MANAGE", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Tactical Communications Sub-Navigation Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0D121C))
                                .border(1.dp, Color(0xFF222E42), RoundedCornerShape(12.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Direct Chat Tab (1-on-1 private messaging, no broadcast clutter)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selectedCommMode == 0) Color(0xFF1E3A5F) else Color.Transparent)
                                    .clickable { selectedCommMode = 0 },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        tint = if (selectedCommMode == 0) Color(0xFF60A5FA) else TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "DIRECT CHAT",
                                        color = if (selectedCommMode == 0) TextPrimary else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Administrator Recon Terminal Tab (Dedicated Broadcast & Speech Terminal)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selectedCommMode == 1) Color(0xFF064E3B) else Color.Transparent)
                                    .clickable { selectedCommMode = 1 },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CellTower,
                                        contentDescription = null,
                                        tint = if (selectedCommMode == 1) Color(0xFF34D399) else TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "RECON TERMINAL",
                                        color = if (selectedCommMode == 1) TextPrimary else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (reconBroadcasts.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .background(if (selectedCommMode == 1) Color(0xFF10B981) else Color(0xFF263347), CircleShape)
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "${reconBroadcasts.size}",
                                                color = if (selectedCommMode == 1) Color.Black else TextSecondary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = selectedCommMode,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInHorizontally { width -> width / 3 } + fadeIn(animationSpec = tween(220)))
                                        .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut(animationSpec = tween(180)))
                                } else {
                                    (slideInHorizontally { width -> -width / 3 } + fadeIn(animationSpec = tween(220)))
                                        .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut(animationSpec = tween(180)))
                                }
                            },
                            label = "comm_mode_anim"
                        ) { mode ->
                            if (mode == 0) {
                                // ==================== MODE 0: DIRECT ADMINISTRATOR CHAT ====================
                                // Strictly 1-to-1 conversation; ALL broadcast announcements are filtered out
                                val directChatMessages = remember(adminChatMessages) {
                                    adminChatMessages.filterNot { it.message.startsWith("[BROADCAST]") }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "DIRECT 1-ON-1 COMM CHAT",
                                            color = Color(0xFF60A5FA),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "BROADCASTS EXCLUDED",
                                            color = Color(0xFF64748B),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFF28354D), RoundedCornerShape(16.dp)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(150.dp)
                                                    .background(Color(0xFF0F131C), RoundedCornerShape(10.dp))
                                                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                                                    .padding(8.dp)
                                            ) {
                                                if (directChatMessages.isEmpty()) {
                                                    Text(
                                                        text = "No direct chat messages yet.\nType below to message ${device.name} privately.",
                                                        color = TextSecondary,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.align(Alignment.Center),
                                                        textAlign = TextAlign.Center
                                                    )
                                                } else {
                                                    val lazyListState = rememberLazyListState()
                                                    LaunchedEffect(directChatMessages.size) {
                                                        if (directChatMessages.isNotEmpty()) {
                                                            lazyListState.animateScrollToItem(directChatMessages.size - 1)
                                                        }
                                                    }
                                                    LazyColumn(
                                                        state = lazyListState,
                                                        modifier = Modifier.fillMaxSize(),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        items(directChatMessages) { msg ->
                                                            val isMe = msg.sender == "admin"
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(
                                                                            color = if (isMe) Color(0xFF1E3A5F) else Color(0xFF1E283C),
                                                                            shape = RoundedCornerShape(
                                                                                topStart = 12.dp,
                                                                                topEnd = 12.dp,
                                                                                bottomStart = if (isMe) 12.dp else 2.dp,
                                                                                bottomEnd = if (isMe) 2.dp else 12.dp
                                                                            )
                                                                        )
                                                                        .border(
                                                                            width = 1.dp,
                                                                            color = if (isMe) Color(0xFF2563EB).copy(alpha = 0.5f) else Border,
                                                                            shape = RoundedCornerShape(
                                                                                topStart = 12.dp,
                                                                                topEnd = 12.dp,
                                                                                bottomStart = if (isMe) 12.dp else 2.dp,
                                                                                bottomEnd = if (isMe) 2.dp else 12.dp
                                                                            )
                                                                        )
                                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                                        .widthIn(max = 240.dp)
                                                                ) {
                                                                    Column {
                                                                        Text(
                                                                            text = if (isMe) "Admin (You)" else device.name,
                                                                            color = if (isMe) Color(0xFF60A5FA) else TextSecondary,
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                        Spacer(modifier = Modifier.height(2.dp))
                                                                        Text(
                                                                            text = msg.message,
                                                                            color = TextPrimary,
                                                                            fontSize = 12.sp
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = adminChatInputText,
                                                    onValueChange = { adminChatInputText = it },
                                                    placeholder = { Text("Direct message to ${device.name}...", color = TextSecondary, fontSize = 12.sp) },
                                                    singleLine = true,
                                                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 12.sp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = AccentBlue,
                                                        unfocusedBorderColor = Border,
                                                        focusedContainerColor = Color(0xFF0F131C),
                                                        unfocusedContainerColor = Color(0xFF0F131C)
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        if (adminChatInputText.trim().isNotEmpty()) {
                                                            viewModel.sendAdminMessage(device.ip, adminChatInputText.trim())
                                                            adminChatInputText = ""
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .background(AccentBlue, CircleShape)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Send,
                                                        contentDescription = "Send Message",
                                                        tint = TextPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // ==================== MODE 1: ADMINISTRATOR RECON BROADCAST TERMINAL ====================
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Terminal Header Card with Audio Visualizer
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B141E)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            // Status line with Equalizer
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .background(Color(0xFF10B981), CircleShape)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = "RECON BROADCAST TERMINAL",
                                                            color = Color(0xFF34D399),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace,
                                                            letterSpacing = 1.sp
                                                        )
                                                        Text(
                                                            text = "ENCRYPTED TTS UPLINK // CH-433.92 MHz",
                                                            color = Color(0xFF6EE7B7),
                                                            fontSize = 9.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }

                                                // Dynamic Audio Equalizer Animation
                                                AudioEqualizerVisualizer(
                                                    color = Color(0xFF34D399)
                                                )
                                            }

                                            // Tactical Quick Directives Chips
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = "QUICK TACTICAL DIRECTIVES",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                val reconPresets = listOf(
                                                    "🚨 EMERGENCY LOCKDOWN INITIATED",
                                                    "🛌 BEDTIME SCHEDULE ACTIVE",
                                                    "📚 STUDY TIME: RESTRICTING APPS",
                                                    "⚠️ RETURN HOME IMMEDIATELY",
                                                    "🔕 SILENCE NOTIFICATIONS"
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    reconPresets.forEach { preset ->
                                                        Surface(
                                                            color = Color(0xFF13232C),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier
                                                                .border(1.dp, Color(0xFF1F4448), RoundedCornerShape(8.dp))
                                                                .clickable { reconTerminalInputText = preset }
                                                        ) {
                                                            Text(
                                                                text = preset,
                                                                color = Color(0xFFE2E8F0),
                                                                fontSize = 10.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Terminal Input Box
                                            OutlinedTextField(
                                                value = reconTerminalInputText,
                                                onValueChange = { reconTerminalInputText = it },
                                                placeholder = {
                                                    Text(
                                                        text = "Enter recon broadcast speech directive...",
                                                        color = Color(0xFF64748B),
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                },
                                                leadingIcon = {
                                                    Text(
                                                        text = "TX>",
                                                        color = Color(0xFF34D399),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                                                    )
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = Color(0xFF090E16),
                                                    unfocusedContainerColor = Color(0xFF090E16),
                                                    focusedTextColor = Color(0xFFECFDF5),
                                                    unfocusedTextColor = Color(0xFFECFDF5),
                                                    focusedBorderColor = Color(0xFF10B981),
                                                    unfocusedBorderColor = Color(0xFF1E3A3A)
                                                ),
                                                textStyle = LocalTextStyle.current.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                maxLines = 3
                                            )

                                            // Action Buttons: Audio Test + Transmit
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        if (reconTerminalInputText.isNotBlank()) {
                                                            com.example.tts.TextToSpeechManager.speak(context, reconTerminalInputText)
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Type directive to preview audio", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
                                                    modifier = Modifier.height(36.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("PREVIEW TTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Button(
                                                    onClick = {
                                                        if (reconTerminalInputText.isNotBlank()) {
                                                            val adminName = StateManager.adminName.value.ifEmpty { "Admin" }
                                                            com.example.network.FirebaseManager.sendBroadcastAnnouncement(
                                                                listOf(device.ip),
                                                                reconTerminalInputText,
                                                                adminName
                                                            )
                                                            android.widget.Toast.makeText(context, "📡 Recon broadcast dispatched to ${device.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                            reconTerminalInputText = ""
                                                        }
                                                    },
                                                    enabled = reconTerminalInputText.isNotBlank(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF10B981),
                                                        disabledContainerColor = Color(0xFF1E2D2B)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(36.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "TRANSMIT BROADCAST",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (reconTerminalInputText.isNotBlank()) Color.Black else Color(0xFF64748B),
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Recon Broadcasts Stream Feed Card
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1522)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFF1F2B3E), RoundedCornerShape(16.dp)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "TRANSMISSION FEED",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = "(${reconBroadcasts.size})",
                                                        color = Color(0xFF34D399),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                if (reconBroadcasts.isNotEmpty()) {
                                                    TextButton(
                                                        onClick = {
                                                            viewModel.clearReconBroadcasts(device.ip)
                                                            android.widget.Toast.makeText(context, "Recon terminal history cleared", android.widget.Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("CLEAR FEED", color = Color(0xFFF87171), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                    }
                                                }
                                            }

                                            if (reconBroadcasts.isEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(110.dp)
                                                        .background(Color(0xFF0A0F18), RoundedCornerShape(10.dp))
                                                        .border(1.dp, Color(0xFF1A2636), RoundedCornerShape(10.dp))
                                                        .padding(12.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Icon(Icons.Default.CellTower, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(22.dp))
                                                        Text(
                                                            text = "TERMINAL STANDBY // NO BROADCASTS TRANSMITTED",
                                                            color = Color(0xFF64748B),
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            textAlign = TextAlign.Center
                                                        )
                                                        Text(
                                                            text = "Use the console above to transmit priority audio broadcasts.",
                                                            color = Color(0xFF475569),
                                                            fontSize = 9.sp,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(180.dp)
                                                        .background(Color(0xFF090E17), RoundedCornerShape(10.dp))
                                                        .border(1.dp, Color(0xFF1E2D40), RoundedCornerShape(10.dp))
                                                        .padding(8.dp)
                                                ) {
                                                    LazyColumn(
                                                        modifier = Modifier.fillMaxSize(),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        items(reconBroadcasts) { b ->
                                                            val isAck = b.status == "ACKNOWLEDGED"
                                                            val date = java.util.Date(b.timestamp)
                                                            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(date)

                                                            Card(
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .border(
                                                                        1.dp,
                                                                        if (isAck) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.4f),
                                                                        RoundedCornerShape(8.dp)
                                                                    )
                                                            ) {
                                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "[$timeStr]",
                                                                                color = Color(0xFF64748B),
                                                                                fontSize = 10.sp,
                                                                                fontFamily = FontFamily.Monospace,
                                                                                fontWeight = FontWeight.Bold
                                                                            )
                                                                            Text(
                                                                                text = "BY ${b.sender.uppercase()}",
                                                                                color = Color(0xFF94A3B8),
                                                                                fontSize = 10.sp,
                                                                                fontFamily = FontFamily.Monospace
                                                                            )
                                                                        }

                                                                        // Telemetry status pill
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .background(
                                                                                    if (isAck) Color(0xFF064E3B) else Color(0xFF451A03),
                                                                                    RoundedCornerShape(4.dp)
                                                                                )
                                                                                .border(
                                                                                    1.dp,
                                                                                    if (isAck) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                                                    RoundedCornerShape(4.dp)
                                                                                )
                                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                        ) {
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = if (isAck) Icons.Default.CheckCircle else Icons.Default.Campaign,
                                                                                    contentDescription = null,
                                                                                    tint = if (isAck) Color(0xFF34D399) else Color(0xFFFBBF24),
                                                                                    modifier = Modifier.size(10.dp)
                                                                                )
                                                                                Text(
                                                                                    text = if (isAck) "ACKNOWLEDGED" else "TRANSMITTED",
                                                                                    color = if (isAck) Color(0xFF34D399) else Color(0xFFFBBF24),
                                                                                    fontSize = 8.sp,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    fontFamily = FontFamily.Monospace
                                                                                )
                                                                            }
                                                                        }
                                                                    }

                                                                    Text(
                                                                        text = b.message,
                                                                        color = Color.White,
                                                                        fontSize = 12.sp,
                                                                        fontFamily = FontFamily.Monospace
                                                                    )

                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.End,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        TextButton(
                                                                            onClick = {
                                                                                com.example.tts.TextToSpeechManager.speak(context, b.message)
                                                                            },
                                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                                        ) {
                                                                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                                                            Spacer(modifier = Modifier.width(3.dp))
                                                                            Text("LISTEN", color = Color(0xFF38BDF8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                        }

                                                                        Spacer(modifier = Modifier.width(4.dp))

                                                                        TextButton(
                                                                            onClick = {
                                                                                reconTerminalInputText = b.message
                                                                            },
                                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                                        ) {
                                                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(12.dp))
                                                                            Spacer(modifier = Modifier.width(3.dp))
                                                                            Text("RE-DISPATCH", color = Color(0xFF34D399), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // ==================== TAB 2: LOCK STUDIO ====================
                        Text(
                            text = "LOCK SCREEN DESIGNER STUDIO",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        // 1. Theme Picker Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF28354D), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Select Theme Aesthetic", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                val themes = listOf("slate", "cyberpunk", "stealth", "matrix", "crimson")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    themes.forEach { t ->
                                        val isSel = activeTheme == t
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isSel) Color(0xFF1E3A5F) else Color(0xFF1B2332),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSel) Color(0xFF3B82F6) else Border,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { activeTheme = t }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = t.uppercase(),
                                                color = if (isSel) Color(0xFF60A5FA) else TextSecondary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Warning Icon Glyph", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                val icons = listOf(
                                    "lock" to Icons.Default.Lock,
                                    "biohazard" to Icons.Default.Warning,
                                    "warning" to Icons.Default.Warning,
                                    "hourglass" to Icons.Default.Schedule,
                                    "shield" to Icons.Default.Shield
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    icons.forEach { (iName, iVector) ->
                                        val isSel = activeIcon == iName
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isSel) Color(0xFF1E3A5F) else Color(0xFF1B2332),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSel) Color(0xFF3B82F6) else Border,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { activeIcon = iName }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = iVector,
                                                contentDescription = iName,
                                                tint = if (isSel) Color(0xFF60A5FA) else TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Wallpaper Backdrop Presets", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                val wallpaperPresets = listOf(
                                    "Default Dark" to "",
                                    "Obsidian Void" to "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800",
                                    "Neon Matrix" to "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800",
                                    "Crimson Pulse" to "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=800"
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(wallpaperPresets) { (pName, pUrl) ->
                                        Surface(
                                            color = if (activeWallpaper == pUrl) Color(0xFF1E3A5F) else Color(0xFF1B2332),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .border(
                                                    1.dp,
                                                    if (activeWallpaper == pUrl) Color(0xFF3B82F6) else Border,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { activeWallpaper = pUrl }
                                        ) {
                                            Text(
                                                text = pName,
                                                color = if (activeWallpaper == pUrl) Color(0xFF60A5FA) else TextPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = activeWallpaper,
                                    onValueChange = { activeWallpaper = it },
                                    placeholder = { Text("Custom image URL (https://...)", fontSize = 11.sp, color = TextSecondary) },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentBlue,
                                        unfocusedBorderColor = Border,
                                        focusedContainerColor = Color(0xFF0F131C),
                                        unfocusedContainerColor = Color(0xFF0F131C)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Mini Phone Mockup Preview Box
                                Text("Real-Time Lock Mockup Preview", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when (activeTheme) {
                                                "cyberpunk" -> Color(0xFF120B24)
                                                "matrix" -> Color(0xFF08180E)
                                                "crimson" -> Color(0xFF240A0F)
                                                "stealth" -> Color(0xFF05070A)
                                                else -> Color(0xFF0F131C)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when (activeTheme) {
                                                "cyberpunk" -> AccentPurple
                                                "matrix" -> AccentGreen
                                                "crimson" -> AccentRed
                                                else -> AccentBlue
                                            }.copy(alpha = 0.5f),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (activeIcon) {
                                                "biohazard", "warning" -> Icons.Default.Warning
                                                "hourglass" -> Icons.Default.Schedule
                                                "shield" -> Icons.Default.Shield
                                                else -> Icons.Default.Lock
                                            },
                                            contentDescription = null,
                                            tint = when (activeTheme) {
                                                "cyberpunk" -> AccentPurple
                                                "matrix" -> AccentGreen
                                                "crimson" -> AccentRed
                                                else -> AccentBlue
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "SCREEN RESTRICTED BY ADMIN",
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "Theme: ${activeTheme.uppercase()} • Icon: ${activeIcon.uppercase()}",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.updateLockStyle(device.ip, activeTheme, activeWallpaper, activeIcon)
                                        android.widget.Toast.makeText(context, "Lock screen style applied to ${device.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    Text("APPLY VISUAL STYLE TO DEVICE", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    3 -> {
                        // ==================== TAB 3: AUDIT LOGS ====================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ADMIN COMMAND AUDIT LOGS",
                                color = AccentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            TextButton(
                                onClick = {
                                    viewModel.clearLogs(device.ip)
                                    android.widget.Toast.makeText(context, "Audit logs cleared", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear Logs",
                                    tint = AccentRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CLEAR LOGS", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A26)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF28354D), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                if (operationLogs.isEmpty()) {
                                    Text(
                                        text = "No operational logs captured yet for this device.",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(240.dp)
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(operationLogs) { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .background(
                                                                when (item.action) {
                                                                    "LOCK", "GEOFENCE_VIOLATION" -> AccentRed
                                                                    "UNLOCK" -> AccentGreen
                                                                    "SCREEN_START", "CAMERA_START" -> AccentPurple
                                                                    else -> Color(0xFF60A5FA)
                                                                },
                                                                CircleShape
                                                            )
                                                            .align(Alignment.CenterVertically)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${item.action} • ${item.details}",
                                                            color = TextPrimary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        val date = java.util.Date(item.timestamp)
                                                        val timeStr = java.text.SimpleDateFormat(
                                                            "MMM d, hh:mm a",
                                                            java.util.Locale.getDefault()
                                                        ).format(date)
                                                        Text(
                                                            text = timeStr,
                                                            color = TextSecondary,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Danger Zone / Unpair Device at the bottom
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1418)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentRed.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "DANGER ZONE",
                            color = AccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Unpair and permanently remove this device from remote monitoring. The client device will cease telemetry, background sync, and security locks.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedButton(
                            onClick = { showRemoveDialog = true },
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AccentRed.copy(alpha = 0.1f),
                                contentColor = AccentRed
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Unpair Device",
                                tint = AccentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "UNPAIR & REMOVE DEVICE",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = AccentRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun BlockModal(
    device: DiscoveredDevice,
    onDismiss: () -> Unit,
    onSendBlock: (String, String, Long, String?) -> Unit
) {
    val themeBlur = LocalThemeBlur.current
    DisposableEffect(Unit) {
        themeBlur.value = true
        onDispose {
            themeBlur.value = false
        }
    }
    var message by remember { mutableStateOf(StateManager.defaultMessage.value) }
    var password by remember { mutableStateOf(StateManager.defaultPassword.value) }
    var showPassword by remember { mutableStateOf(false) }

    var selectedPreset by remember { mutableStateOf("Indefinite") }
    var customMinutesInput by remember { mutableStateOf("") }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(it)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (originalBitmap != null) {
                    val maxDim = 500
                    val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                    val (newWidth, newHeight) = if (originalBitmap.width > originalBitmap.height) {
                        val w = minOf(originalBitmap.width, maxDim)
                        w to (w / ratio).toInt()
                    } else {
                        val h = minOf(originalBitmap.height, maxDim)
                        (h * ratio).toInt() to h
                    }
                    
                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                    val outStream = java.io.ByteArrayOutputStream()
                    scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outStream)
                    val bytes = outStream.toByteArray()
                    val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    
                    selectedImageBase64 = base64Str
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val previewBitmap = remember(selectedImageBase64) {
        if (!selectedImageBase64.isNullOrEmpty()) {
            try {
                val decodedBytes = android.util.Base64.decode(selectedImageBase64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val timerSeconds: Long = when (selectedPreset) {
        "1m" -> 60L
        "5m" -> 300L
        "15m" -> 900L
        "30m" -> 1800L
        "Custom" -> {
            val mins = customMinutesInput.toIntOrNull() ?: 0
            if (mins > 0) mins * 60L else -1L
        }
        else -> -1L
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(max = 480.dp)
            .heightIn(max = if (isLandscape) 340.dp else 660.dp)
            .border(1.dp, GlassRedBorderBrush, RoundedCornerShape(24.dp)),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(AccentRed.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, GlassRedBorderBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "LOCK SCREEN NOW",
                    color = AccentRed,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Requesting Lock Overlay on name: ${device.name} [IP: ${device.ip}]",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                // Message Text Layout
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Display block message", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceAlt,
                        unfocusedContainerColor = SurfaceAlt,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password Layout
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Unlock password", color = TextSecondary) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceAlt,
                        unfocusedContainerColor = SurfaceAlt,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Custom Unlock Option Preset Chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "UNLOCK TIMER OPTION:",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    
                    val presets = listOf(
                        "Indefinite" to "🔒 Perm",
                        "1m" to "⏱️ 1 Min",
                        "5m" to "⏱️ 5 Min",
                        "15m" to "⏱️ 15 Min",
                        "30m" to "⏱️ 30 Min",
                        "Custom" to "⚙️ Custom"
                    )
                    
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presets.size) { index ->
                            val (preset, display) = presets[index]
                            val isSelected = selectedPreset == preset
                            val backgroundColor = if (isSelected) AccentRed.copy(alpha = 0.15f) else SurfaceAlt
                            val borderColor = if (isSelected) AccentRed else Border
                            
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .background(backgroundColor, RoundedCornerShape(8.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .clickable { selectedPreset = preset }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = display,
                                    color = if (isSelected) AccentRed else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    if (selectedPreset == "Custom") {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customMinutesInput,
                            onValueChange = { customMinutesInput = it.filter { char -> char.isDigit() } },
                            label = { Text("Unlock after (Minutes)", color = TextSecondary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceAlt,
                                unfocusedContainerColor = SurfaceAlt,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = Border
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Image Uploader Controls
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "LOCK SCREEN IMAGE (OPTIONAL):",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    
                    if (previewBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(SurfaceAlt, RoundedCornerShape(8.dp))
                                .border(1.dp, Border, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Selected Block Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                            // Remove Button overlay
                            IconButton(
                                onClick = { selectedImageBase64 = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Image",
                                    tint = AccentRed
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { launcher.launch("image/*") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Add Image",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("UPLOAD BLOCK IMAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Mini Preview Box
                Column {
                    Text(
                        text = "LOCK SCREEN PREVIEW:",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OverlayBg, RoundedCornerShape(8.dp))
                            .border(1.dp, AccentRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "DEVICE LOCKED",
                                color = AccentRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                message.ifEmpty { "This device has been restricted." },
                                color = TextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            
                            if (previewBitmap != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "Preview Image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(60.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                            
                            // Visual hint about timer options
                            if (timerSeconds > 0L) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    val durationText = if (selectedPreset == "Custom") {
                                        "$customMinutesInput min"
                                    } else {
                                        val mins = timerSeconds / 60
                                        "$mins min"
                                    }
                                    Text(
                                        text = "Automatically unlocks after $durationText",
                                        color = AccentGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, GlassRedBorderBrush, RoundedCornerShape(100.dp)),
                onClick = { onSendBlock(message, password, timerSeconds, selectedImageBase64) }
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "LOCK SCREEN NOW",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                onClick = onDismiss
            ) {
                Text("CANCEL", color = TextSecondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun AdminBottomBar(
    currentScreen: String,
    onNavigateToDashboard: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        NavigationBar(
            containerColor = Color(0xFF191F2C),
            tonalElevation = 0.dp,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .border(width = 1.dp, color = Color(0xFF2B364A), shape = RoundedCornerShape(100.dp))
        ) {
            NavigationBarItem(
                selected = currentScreen == "dashboard",
                onClick = onNavigateToDashboard,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Control Center"
                    )
                },
                label = {
                    Text(
                        "Control",
                        fontSize = 11.5.sp,
                        fontWeight = if (currentScreen == "dashboard") FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color(0xFF8896AB),
                    unselectedTextColor = Color(0xFF8896AB),
                    indicatorColor = Color(0xFF1E3A5F)
                )
            )

            NavigationBarItem(
                selected = currentScreen == "settings",
                onClick = onNavigateToSettings,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Settings"
                    )
                },
                label = {
                    Text(
                        "Settings",
                        fontSize = 11.5.sp,
                        fontWeight = if (currentScreen == "settings") FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color(0xFF8896AB),
                    unselectedTextColor = Color(0xFF8896AB),
                    indicatorColor = Color(0xFF1E3A5F)
                )
            )
        }
    }
}

@Composable
fun CameraStreamModal(
    device: DiscoveredDevice,
    cameraFeedBase64: String?,
    cameraFeedAudioBase64: String?,
    onToggleLens: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeBlur = LocalThemeBlur.current
    val context = LocalContext.current
    
    val onlineDevices by com.example.network.FirebaseManager.onlineDevices.collectAsState()
    val liveDevice = onlineDevices.find { it.ip == device.ip } ?: device
    val isDeviceSpeaking = liveDevice.deviceSpeaking
    
    DisposableEffect(Unit) {
        themeBlur.value = true
        com.example.network.AudioStreamManager.startPlayback()
        onDispose {
            themeBlur.value = false
            com.example.network.AudioStreamManager.stopPlayback()
            com.example.network.AudioStreamManager.stopRecording()
            com.example.network.FirebaseManager.sendIntercomAudio(device.ip, "", false)
        }
    }
    
    var isSpeaking by remember { mutableStateOf(false) }

    // Start and stop playback when the device starts and stops speaking
    LaunchedEffect(isDeviceSpeaking) {
        if (isDeviceSpeaking) {
            isSpeaking = false
            com.example.network.AudioStreamManager.startPlayback()
        } else {
            com.example.network.AudioStreamManager.stopPlayback()
        }
    }
    
    // Play device audio feed in real-time only when device is holding push-to-talk and admin is not actively speaking to avoid echo/leakage
    LaunchedEffect(cameraFeedAudioBase64, isSpeaking) {
        if (!cameraFeedAudioBase64.isNullOrEmpty() && isDeviceSpeaking && !isSpeaking) {
            com.example.network.AudioStreamManager.playAudioSegment(cameraFeedAudioBase64)
        }
    }

    var decodedBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isSpeaking = true
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required to speak", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                com.example.network.AudioStreamManager.startRecording(context)
                try {
                    while (isSpeaking) {
                        val base64Bytes = com.example.network.AudioStreamManager.getLatestAudioSegmentBase64()
                        if (base64Bytes.isNotEmpty()) {
                            com.example.network.FirebaseManager.sendIntercomAudio(device.ip, base64Bytes, true)
                        }
                        kotlinx.coroutines.delay(180L)
                    }
                } finally {
                    com.example.network.AudioStreamManager.stopRecording()
                    com.example.network.FirebaseManager.sendIntercomAudio(device.ip, "", false)
                }
            } else {
                isSpeaking = false
            }
        }
    }

    LaunchedEffect(cameraFeedBase64) {
        if (!cameraFeedBase64.isNullOrEmpty()) {
            try {
                val bitmapOpt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        val bytes = android.util.Base64.decode(cameraFeedBase64, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
                decodedBitmap = bitmapOpt
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            decodedBitmap = null
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(max = 500.dp)
            .heightIn(max = if (isLandscape) 360.dp else 680.dp)
            .border(1.dp, GlassGreenBorderBrush, RoundedCornerShape(24.dp)),
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, GlassRedBorderBrush, RoundedCornerShape(100.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISCONNECT FEED",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Live Camera Indicator",
                        tint = if (decodedBitmap != null) AccentGreen else AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (device.cameraLens == "back") "LIVE BACK CAMERA" else "LIVE FRONT CAMERA",
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                val infiniteTransition = rememberInfiniteTransition(label = "feed_dot")
                val signalAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "signal_alpha"
                )
                
                Box(
                    modifier = Modifier
                        .background(
                            if (decodedBitmap != null) AccentGreen.copy(alpha = 0.1f * signalAlpha) else AccentAmber.copy(alpha = 0.1f * signalAlpha),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (decodedBitmap != null) AccentGreen.copy(alpha = signalAlpha) else AccentAmber.copy(alpha = signalAlpha),
                            CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (decodedBitmap != null) "STREAMING" else "CONNECTING",
                        color = if (decodedBitmap != null) AccentGreen else AccentAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${device.name} [${device.ip}]",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )

                // High-fidelity active lens switcher row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceAlt, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var localLens by remember(device.cameraLens) { mutableStateOf(device.cameraLens) }
                    val isFrontSelected = localLens != "back"
                    
                    // Front Lens Selector Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFrontSelected) AccentBlue.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, if (isFrontSelected) AccentBlue.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable {
                                localLens = "front"
                                onToggleLens("front")
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Front Camera Option",
                                tint = if (isFrontSelected) AccentBlue else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "FRONT LENS",
                                color = if (isFrontSelected) AccentBlue else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
 
                    // Back Lens Selector Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isFrontSelected) AccentBlue.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, if (!isFrontSelected) AccentBlue.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable {
                                localLens = "back"
                                onToggleLens("back")
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Back Camera Option",
                                tint = if (!isFrontSelected) AccentBlue else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "BACK LENS",
                                color = if (!isFrontSelected) AccentBlue else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                val currentBitmap = decodedBitmap
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val frameHeight = if (maxHeight > 0.dp && maxHeight < 600.dp) {
                        (maxHeight * 0.60f).coerceIn(180.dp, 360.dp)
                    } else {
                        360.dp
                    }
                    Box(
                        modifier = Modifier
                            .height(frameHeight)
                            .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceAlt)
                            .border(1.dp, if (currentBitmap != null) AccentGreen.copy(alpha = 0.4f) else Border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentBitmap != null) {
                            Image(
                                bitmap = currentBitmap,
                                contentDescription = "Live camera frame",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val scanlineHeight = 2.dp.toPx()
                                val gap = 6.dp.toPx()
                                var y = 0f
                                while (y < size.height) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.05f),
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                                        size = androidx.compose.ui.geometry.Size(size.width, scanlineHeight)
                                    )
                                    y += gap
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = AccentAmber,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "ESTABLISHING SECURE FEED...",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Make sure client camera permissions are granted.",
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSpeaking) AccentRed else if (isDeviceSpeaking) Color(0xFF152A15) else SurfaceAlt)
                        .border(1.dp, if (isSpeaking) AccentRed else if (isDeviceSpeaking) AccentGreen.copy(alpha = 0.5f) else Border, RoundedCornerShape(12.dp))
                        .pointerInput(isDeviceSpeaking) {
                            detectTapGestures(
                                onPress = {
                                    if (!isDeviceSpeaking) {
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
                                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            isSpeaking = true
                                        } else {
                                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                        try {
                                            awaitRelease()
                                        } finally {
                                            isSpeaking = false
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Mic else if (isDeviceSpeaking) Icons.Default.VolumeUp else Icons.Default.MicOff,
                            contentDescription = "Speak Button",
                            tint = if (isSpeaking) Color.White else if (isDeviceSpeaking) AccentGreen else AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isSpeaking) "SPEAKING (RELEASE TO MUTE)" else if (isDeviceSpeaking) "DEVICE IS SPEAKING..." else "HOLD TO SPEAK (INTERCOM)",
                            color = if (isSpeaking) Color.White else if (isDeviceSpeaking) AccentGreen else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        // A blinking dot when speaking
                        if (isSpeaking) {
                            val blinkTransition = rememberInfiniteTransition(label = "blink_dot")
                            val blinkAlpha by blinkTransition.animateFloat(
                                initialValue = 0.2f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(450, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "blink_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = blinkAlpha))
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ScreenStreamModal(
    device: DiscoveredDevice,
    screenFeedBase64: String?,
    onDismiss: () -> Unit
) {
    val themeBlur = LocalThemeBlur.current
    DisposableEffect(Unit) {
        themeBlur.value = true
        onDispose {
            themeBlur.value = false
        }
    }

    var frameCount by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(0) }
    var lastFpsTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val decodedBitmap = remember(screenFeedBase64) {
        if (!screenFeedBase64.isNullOrEmpty()) {
            try {
                frameCount++
                val now = System.currentTimeMillis()
                if (now - lastFpsTime >= 1000) {
                    fps = frameCount
                    frameCount = 0
                    lastFpsTime = now
                }
                val decodedBytes = android.util.Base64.decode(screenFeedBase64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val infiniteTransition = rememberInfiniteTransition(label = "screen_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(24.dp),
        containerColor = Surface,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(max = 520.dp)
            .heightIn(max = if (isLandscape) 360.dp else 680.dp)
            .border(1.dp, LiquidGlassChromaticBorder, RoundedCornerShape(24.dp)),
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, GlassRedBorderBrush, RoundedCornerShape(100.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CLOSE LIVE SCREEN FEED",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenShare,
                        contentDescription = "Live Screen Indicator",
                        tint = if (decodedBitmap != null) AccentGreen else AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "LIVE REMOTE SCREEN",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${device.name} • ${device.ip}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (decodedBitmap != null) AccentGreen.copy(alpha = 0.12f * pulseAlpha) else AccentAmber.copy(alpha = 0.12f * pulseAlpha),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (decodedBitmap != null) AccentGreen.copy(alpha = pulseAlpha) else AccentAmber.copy(alpha = pulseAlpha),
                            CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (decodedBitmap != null) "LIVE STREAMING" else "REQUESTING",
                        color = if (decodedBitmap != null) AccentGreen else AccentAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stream metrics pill row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceAlt, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BANDWIDTH: OPTIMIZED (540p)",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (decodedBitmap != null) "${fps.coerceAtLeast(8)} FPS • LOW LATENCY" else "WAITING PEER...",
                        color = if (decodedBitmap != null) AccentGreen else AccentAmber,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live Screen View Frame
                val currentBitmap = decodedBitmap
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val containerHeight = maxHeight
                    val frameHeight = if (containerHeight > 0.dp && containerHeight < 620.dp) {
                        (containerHeight * 0.65f).coerceIn(200.dp, 420.dp)
                    } else {
                        420.dp
                    }
                    Box(
                        modifier = Modifier
                            .height(frameHeight)
                            .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF000000))
                            .border(
                                1.dp,
                                if (currentBitmap != null) AccentGreen.copy(alpha = 0.4f) else Border,
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap,
                            contentDescription = "Live remote phone screen",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Subtle scanline texture overlay for sleek visual craft
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val scanlineHeight = 2.dp.toPx()
                            val gap = 6.dp.toPx()
                            var y = 0f
                            while (y < size.height) {
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.04f),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                                    size = androidx.compose.ui.geometry.Size(size.width, scanlineHeight)
                                )
                                y += gap
                            }
                        }

                        // Watermark pill in bottom corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "RAM ONLY • NO STORAGE USED",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (screenFeedBase64 == "STATUS:PERMISSION_REQUIRED") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Permission Required",
                                tint = AccentAmber,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "SCREEN BROADCAST PERMISSION NEEDED",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Screen mirroring requires one-time permission on '${device.name}'.\n\nOpen GuardLink on that device and tap 'ENABLE REMOTE SCREEN STREAM' to grant access.",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    } else if (screenFeedBase64 == "STATUS:FAILED_INIT") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Capture Failed",
                                tint = AccentRed,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "INITIALIZATION FAILED",
                                color = AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "The screen capture session could not be established on '${device.name}'. Ensure the device is awake and has granted capture permissions.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = AccentPurple,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "ESTABLISHING LIVE SCREEN FEED...",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Connecting to peer screen stream in real-time.\nEnsure '${device.name}' is online with screen broadcasting enabled.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManagerModal(
    viewModel: AdminDashboardViewModel,
    onDismiss: () -> Unit
) {
    val themeBlur = LocalThemeBlur.current
    DisposableEffect(Unit) {
        themeBlur.value = true
        onDispose {
            themeBlur.value = false
        }
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val schedules by StateManager.schedules.collectAsState()
    val devices by viewModel.devices.collectAsState(initial = emptyList())

    var isEditorOpen by remember { mutableStateOf(false) }
    var editingScheduleId by remember { mutableStateOf<String?>(null) }
    var scheduleToDelete by remember { mutableStateOf<com.example.data.LockSchedule?>(null) }

    // State for the editor
    var targetDeviceName by remember { mutableStateOf("All Devices") }
    var startHour by remember { mutableStateOf("21") }
    var startMinute by remember { mutableStateOf("00") }
    var endHour by remember { mutableStateOf("07") }
    var endMinute by remember { mutableStateOf("00") }
    var message by remember { mutableStateOf("Locked by Schedule: Bedtime hours restrictions.") }
    var passcode by remember { mutableStateOf("1234") }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) } // Daily by default
    var validationError by remember { mutableStateOf<String?>(null) }

    fun openAdd() {
        editingScheduleId = null
        targetDeviceName = "All Devices"
        startHour = "21"
        startMinute = "00"
        endHour = "07"
        endMinute = "00"
        message = "Locked by Schedule: Bedtime hours restrictions."
        passcode = "1234"
        selectedDays = setOf(1, 2, 3, 4, 5, 6, 7)
        validationError = null
        isEditorOpen = true
    }

    fun openEdit(s: com.example.data.LockSchedule) {
        editingScheduleId = s.id
        targetDeviceName = s.deviceName
        startHour = String.format("%02d", s.startHour)
        startMinute = String.format("%02d", s.startMinute)
        endHour = String.format("%02d", s.endHour)
        endMinute = String.format("%02d", s.endMinute)
        message = s.message
        passcode = s.passcode
        selectedDays = s.daysOfWeek.toSet()
        validationError = null
        isEditorOpen = true
    }

    fun applyPreset(name: String) {
        when (name) {
            "bedtime" -> {
                startHour = "21"
                startMinute = "00"
                endHour = "07"
                endMinute = "00"
                selectedDays = setOf(1, 2, 3, 4, 5, 6, 7)
                message = "Locked by Schedule: Bedtime hours restriction."
            }
            "school" -> {
                startHour = "08"
                startMinute = "00"
                endHour = "15"
                endMinute = "00"
                selectedDays = setOf(2, 3, 4, 5, 6)
                message = "Locked by Schedule: School/Study hours."
            }
            "dinner" -> {
                startHour = "18"
                startMinute = "00"
                endHour = "20"
                endMinute = "00"
                selectedDays = setOf(1, 2, 3, 4, 5, 6, 7)
                message = "Locked by Schedule: Family dinner time."
            }
            "evening" -> {
                startHour = "19"
                startMinute = "00"
                endHour = "22"
                endMinute = "00"
                selectedDays = setOf(1, 2, 3, 4, 5, 6, 7)
                message = "Locked by Schedule: Evening focus time."
            }
        }
    }

    fun saveSchedule() {
        val sH = startHour.trim().toIntOrNull()
        val sM = startMinute.trim().toIntOrNull()
        val eH = endHour.trim().toIntOrNull()
        val eM = endMinute.trim().toIntOrNull()

        if (sH == null || sH !in 0..23) {
            validationError = "Start hour must be between 00 and 23"
            return
        }
        if (sM == null || sM !in 0..59) {
            validationError = "Start minute must be between 00 and 59"
            return
        }
        if (eH == null || eH !in 0..23) {
            validationError = "End hour must be between 00 and 23"
            return
        }
        if (eM == null || eM !in 0..59) {
            validationError = "End minute must be between 00 and 59"
            return
        }
        if (selectedDays.isEmpty()) {
            validationError = "Please select at least one day of the week"
            return
        }

        val targetDevice = if (targetDeviceName.isBlank()) "All Devices" else targetDeviceName.trim()
        val lockMsg = if (message.isBlank()) "Locked by scheduled restriction." else message.trim()
        val lockPwd = if (passcode.isBlank()) "1234" else passcode.trim()

        val id = editingScheduleId ?: java.util.UUID.randomUUID().toString()
        val currentSchedule = schedules.find { it.id == id }
        val isEnabled = currentSchedule?.enabled ?: true

        val scheduleObj = com.example.data.LockSchedule(
            id = id,
            deviceName = targetDevice,
            startHour = sH,
            startMinute = sM,
            endHour = eH,
            endMinute = eM,
            daysOfWeek = selectedDays.toList(),
            message = lockMsg,
            passcode = lockPwd,
            enabled = isEnabled
        )

        if (editingScheduleId != null) {
            viewModel.updateSchedule(scheduleObj)
            android.widget.Toast.makeText(context, "Schedule updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addSchedule(scheduleObj)
            android.widget.Toast.makeText(context, "Schedule created successfully!", android.widget.Toast.LENGTH_SHORT).show()
        }

        isEditorOpen = false
        editingScheduleId = null
        validationError = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(max = 520.dp)
            .heightIn(max = if (isLandscape) 340.dp else 680.dp)
            .border(1.dp, LiquidGlassChromaticBorder, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        containerColor = Surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = AccentBlue
                    )
                    Text(
                        text = if (isEditorOpen) (if (editingScheduleId != null) "EDIT SCHEDULE" else "NEW SCHEDULE") else "LOCKDOWN SCHEDULER",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isLandscape) 260.dp else 520.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isEditorOpen) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (editingScheduleId != null) AccentAmber.copy(alpha = 0.5f) else AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (editingScheduleId != null) "MODIFY EXISTING WINDOW" else "DEFINE TIME RESTRICTION",
                                        color = if (editingScheduleId != null) AccentAmber else AccentBlue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    )
                                    if (editingScheduleId != null) {
                                        Text(
                                            text = "ID: ${editingScheduleId!!.take(8)}",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick presets
                                Text(
                                    text = "Quick Presets",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                 Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(
                                        onClick = { applyPreset("bedtime") },
                                        label = { Text("Bedtime (21-07)", fontSize = 10.sp, maxLines = 1) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Surface)
                                    )
                                    AssistChip(
                                        onClick = { applyPreset("school") },
                                        label = { Text("School (08-15)", fontSize = 10.sp, maxLines = 1) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Surface)
                                    )
                                    AssistChip(
                                        onClick = { applyPreset("dinner") },
                                        label = { Text("Dinner (18-20)", fontSize = 10.sp, maxLines = 1) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Surface)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Target Device
                                Text(
                                    text = "Target Device",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = targetDeviceName,
                                    onValueChange = { targetDeviceName = it },
                                    placeholder = { Text("e.g. All Devices or device name") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Surface,
                                        unfocusedContainerColor = Surface,
                                        focusedBorderColor = AccentBlue,
                                        unfocusedBorderColor = Border
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Device Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val quickChips = listOf("All Devices") + devices.map { it.name }
                                    quickChips.distinct().take(4).forEach { name ->
                                        val isSelected = targetDeviceName.equals(name, ignoreCase = true)
                                        AssistChip(
                                            onClick = { targetDeviceName = name },
                                            label = { Text(name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                labelColor = if (isSelected) AccentBlue else TextPrimary,
                                                containerColor = if (isSelected) AccentBlue.copy(alpha = 0.15f) else Surface
                                            ),
                                            border = AssistChipDefaults.assistChipBorder(
                                                borderColor = if (isSelected) AccentBlue else Border,
                                                enabled = true
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Time windows input
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Start Time (HH:MM)",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedTextField(
                                                value = startHour,
                                                onValueChange = { if (it.length <= 2) startHour = it },
                                                placeholder = { Text("21") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border)
                                            )
                                            Text(":", color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                                            OutlinedTextField(
                                                value = startMinute,
                                                onValueChange = { if (it.length <= 2) startMinute = it },
                                                placeholder = { Text("00") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "End Time (HH:MM)",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedTextField(
                                                value = endHour,
                                                onValueChange = { if (it.length <= 2) endHour = it },
                                                placeholder = { Text("07") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border)
                                            )
                                            Text(":", color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                                            OutlinedTextField(
                                                value = endMinute,
                                                onValueChange = { if (it.length <= 2) endMinute = it },
                                                placeholder = { Text("00") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Days of Week input
                                Text(
                                    text = "Active Days",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val dayValues = listOf(
                                    2 to "M",
                                    3 to "T",
                                    4 to "W",
                                    5 to "T",
                                    6 to "F",
                                    7 to "S",
                                    1 to "S"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    dayValues.forEach { (value, label) ->
                                        val isSelected = selectedDays.contains(value)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) AccentBlue else Surface)
                                                .border(1.dp, if (isSelected) AccentBlue else Border, CircleShape)
                                                .clickable {
                                                    selectedDays = if (isSelected) {
                                                        selectedDays - value
                                                    } else {
                                                        selectedDays + value
                                                    }
                                                }
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(onClick = { selectedDays = setOf(2, 3, 4, 5, 6) }) {
                                        Text("Weekdays", color = AccentBlue, fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { selectedDays = setOf(7, 1) }) {
                                        Text("Weekends", color = AccentBlue, fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { selectedDays = setOf(1, 2, 3, 4, 5, 6, 7) }) {
                                        Text("Daily", color = AccentBlue, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Lock message
                                Text(
                                    text = "Lock Screen Warning Message",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = message,
                                    onValueChange = { message = it },
                                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Passcode
                                Text(
                                    text = "Emergency Bypass Passcode",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = passcode,
                                    onValueChange = { passcode = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (validationError != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().border(1.dp, AccentRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = validationError ?: "",
                                                color = AccentRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            isEditorOpen = false
                                            editingScheduleId = null
                                            validationError = null
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel")
                                    }

                                    Button(
                                        onClick = { saveSchedule() },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (editingScheduleId != null) AccentAmber else AccentBlue
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (editingScheduleId != null) "Update" else "Save")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Button(
                            onClick = { openAdd() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("ADD LOCKDOWN WINDOW", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (schedules.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.EventNote,
                                        contentDescription = null,
                                        tint = Border,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No active scheduled lockdowns.",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add a schedule to automatically lock device(s) during bedtime, study, or school hours.",
                                        color = Color(0xFF5E6D82),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(schedules, key = { it.id }) { schedule ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Border, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (schedule.enabled) AccentGreen else AccentRed)
                                            )
                                            Text(
                                                text = schedule.deviceName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Switch(
                                                checked = schedule.enabled,
                                                onCheckedChange = { viewModel.toggleSchedule(schedule.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = AccentGreen,
                                                    uncheckedThumbColor = TextSecondary,
                                                    uncheckedTrackColor = Border
                                                ),
                                                modifier = Modifier.scale(0.8f)
                                            )

                                            // EDIT BUTTON
                                            IconButton(
                                                onClick = { openEdit(schedule) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit",
                                                    tint = AccentBlue,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }

                                            // DELETE BUTTON
                                            IconButton(
                                                onClick = { scheduleToDelete = schedule },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = AccentRed.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Border)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "RESTRICTED WINDOW",
                                                color = AccentAmber,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "${String.format("%02d:%02d", schedule.startHour, schedule.startMinute)} — ${String.format("%02d:%02d", schedule.endHour, schedule.endMinute)}",
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "DAYS ACTIVE",
                                                color = AccentAmber,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = schedule.formatDays(),
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    if (schedule.message.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Message: \"${schedule.message}\"",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DONE", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    )

    // Delete Confirmation Dialog
    if (scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AccentRed)
                    Text("Delete Lockdown Window", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete this lockdown schedule for \"${scheduleToDelete?.deviceName}\" (${String.format("%02d:%02d", scheduleToDelete?.startHour ?: 0, scheduleToDelete?.startMinute ?: 0)} - ${String.format("%02d:%02d", scheduleToDelete?.endHour ?: 0, scheduleToDelete?.endMinute ?: 0)})?",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = scheduleToDelete
                        if (toDelete != null) {
                            viewModel.deleteSchedule(toDelete.id)
                            android.widget.Toast.makeText(context, "Schedule deleted", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        scheduleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun DevicePairingAndManagerModal(
    viewModel: AdminDashboardViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val themeBlur = LocalThemeBlur.current
    DisposableEffect(Unit) {
        themeBlur.value = true
        onDispose {
            themeBlur.value = false
        }
    }

    var selectedTab by remember { mutableStateOf("scan") } // "scan", "my_qr", "paired", "local"
    val devices by viewModel.devices.collectAsState()
    var showClearAllConfirm by remember { mutableStateOf(false) }
    val manualIps by StateManager.manualIps.collectAsState()
    var inputIp by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    // Firebase pairing inputs
    var inputCode by remember { mutableStateOf("") }
    var firebaseError by remember { mutableStateOf<String?>(null) }
    var isPairingOnline by remember { mutableStateOf(false) }
    var pairingSuccess by remember { mutableStateOf(false) }

    val myPairingCode by com.example.network.FirebaseManager.pairingCode.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == "my_qr" && myPairingCode == null) {
            com.example.network.FirebaseManager.generateAndPublishPairingCode(context)
        }
    }

    // QR scanner toggle / permission state
    var isScanningQr by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(max = 480.dp)
            .heightIn(max = if (isLandscape) 340.dp else 680.dp)
            .border(1.dp, LiquidGlassChromaticBorder, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        containerColor = Surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SurfaceAlt, CircleShape)
                            .border(1.dp, GlassAccentBorderBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "PAIR COMPANION DEVICE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Liquid Glass Cyber Pill Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceAlt, RoundedCornerShape(100.dp))
                        .border(1.dp, GlassBorderBrush, RoundedCornerShape(100.dp))
                        .padding(4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isScan = selectedTab == "scan"
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isScan) AccentBlue else Color.Transparent)
                            .then(
                                if (isScan) Modifier.border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(100.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = "scan"; isScanningQr = false }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Code/Scan",
                            color = if (isScan) Color.White else TextSecondary,
                            fontWeight = if (isScan) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    val isMyQr = selectedTab == "my_qr"
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isMyQr) AccentAmber.copy(alpha = 0.25f) else Color.Transparent)
                            .then(
                                if (isMyQr) Modifier.border(1.dp, GlassAmberBorderBrush, RoundedCornerShape(100.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = "my_qr"; isScanningQr = false }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "My QR",
                            color = if (isMyQr) AccentAmber else TextSecondary,
                            fontWeight = if (isMyQr) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    val isPaired = selectedTab == "paired"
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isPaired) AccentGreen.copy(alpha = 0.25f) else Color.Transparent)
                            .then(
                                if (isPaired) Modifier.border(1.dp, AccentGreen.copy(alpha = 0.6f), RoundedCornerShape(100.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = "paired"; isScanningQr = false }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Linked (${devices.size})",
                            color = if (isPaired) AccentGreen else TextSecondary,
                            fontWeight = if (isPaired) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    val isLocal = selectedTab == "local"
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isLocal) AccentBlue else Color.Transparent)
                            .then(
                                if (isLocal) Modifier.border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(100.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = "local" }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "IP",
                            color = if (isLocal) Color.White else TextSecondary,
                            fontWeight = if (isLocal) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (selectedTab == "scan") {
                    if (isScanningQr) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "POINT CAMERA AT TARGET QR CODE",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            if (hasCameraPermission) {
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(20.dp))
                                ) {
                                    CameraScannerPreview(
                                        onCodeScanned = { rawCode ->
                                            val pairingCode = rawCode.removePrefix("guardlink_pair:").trim().uppercase()
                                            if (pairingCode.isNotEmpty() && !isPairingOnline) {
                                                isScanningQr = false
                                                inputCode = pairingCode
                                                isPairingOnline = true
                                                firebaseError = null
                                                com.example.network.FirebaseManager.pairDeviceByCode(context, pairingCode,
                                                    onSuccess = {
                                                        isPairingOnline = false
                                                        pairingSuccess = true
                                                        inputCode = ""
                                                    },
                                                    onFailure = { error ->
                                                        isPairingOnline = false
                                                        firebaseError = error
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { launcher.launch(android.Manifest.permission.CAMERA) },
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
                                onClick = { isScanningQr = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.border(1.dp, GlassBorderBrush, RoundedCornerShape(100.dp))
                            ) {
                                Text("CANCEL SCANNING", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Scan companion device QR code or enter their 6-character code to pair securely.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            if (pairingSuccess) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, AccentGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                                        Column {
                                            Text("SUCCESSFULLY PAIRED!", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            Text("Device is now paired and visible in your dashboard.", color = TextPrimary, fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { pairingSuccess = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt),
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, GlassBorderBrush, RoundedCornerShape(100.dp))
                                ) {
                                    Text("Pair Another Device", color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = inputCode,
                                        onValueChange = {
                                            inputCode = it.uppercase().take(6)
                                            firebaseError = null
                                        },
                                        placeholder = { Text("Code e.g. GFX8M9", color = TextSecondary.copy(alpha = 0.6f)) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = SurfaceAlt,
                                            unfocusedContainerColor = SurfaceAlt,
                                            focusedBorderColor = AccentBlue,
                                            unfocusedBorderColor = Border,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(100.dp)
                                    )

                                    Button(
                                        enabled = inputCode.length >= 4 && !isPairingOnline,
                                        onClick = {
                                            isPairingOnline = true
                                            firebaseError = null
                                            com.example.network.FirebaseManager.pairDeviceByCode(context, inputCode,
                                                onSuccess = {
                                                    isPairingOnline = false
                                                    pairingSuccess = true
                                                    inputCode = ""
                                                },
                                                onFailure = { error ->
                                                    isPairingOnline = false
                                                    firebaseError = error
                                                }
                                            )
                                        },
                                        shape = RoundedCornerShape(100.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(100.dp))
                                    ) {
                                        if (isPairingOnline) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                        } else {
                                            Text("PAIR", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }

                                if (firebaseError != null) {
                                    Text(firebaseError!!, color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                    onClick = { isScanningQr = true },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, GlassAmberBorderBrush, RoundedCornerShape(20.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(AccentAmber.copy(alpha = 0.15f), CircleShape)
                                                .border(1.dp, GlassAmberBorderBrush, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentAmber)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "SCAN COMPANION QR CODE",
                                                color = AccentAmber,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                "Instantly pair by scanning companion screen",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTab == "my_qr") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Display this QR code or 6-character code to the companion phone to link.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GlassAmberBorderBrush, RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "YOUR PAIRING CODE",
                                    color = AccentAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = myPairingCode ?: "GENERATING...",
                                    color = TextPrimary,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 4.sp
                                )

                                if (myPairingCode != null) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White, RoundedCornerShape(16.dp))
                                            .padding(10.dp)
                                    ) {
                                        com.example.ui.user.QrCodeView(
                                            data = "guardlink_pair:$myPairingCode",
                                            modifier = Modifier.size(150.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = AccentAmber,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Waiting for companion handshake...",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                com.example.network.FirebaseManager.generateAndPublishPairingCode(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GlassAmberBorderBrush, RoundedCornerShape(100.dp))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("GENERATE NEW CODE", color = AccentAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (selectedTab == "paired") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STRICTLY PAIRED DEVICES",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            if (devices.isNotEmpty()) {
                                Text(
                                    text = "${devices.size} CONNECTED",
                                    color = AccentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (devices.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, GlassBorderBrush, RoundedCornerShape(18.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "NO DEVICES CURRENTLY LINKED",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Only devices that you connect using QR code or pairing code will appear here.",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 15.sp
                                    )
                                    Button(
                                        onClick = { selectedTab = "scan" },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                        shape = RoundedCornerShape(100.dp),
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(100.dp))
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("LINK VIA QR / CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                devices.forEach { dev ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, if (dev.status == "active") GlassAccentBorderBrush else GlassBorderBrush, RoundedCornerShape(16.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(if (dev.status == "active") AccentGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f), CircleShape)
                                                        .border(1.dp, if (dev.status == "active") AccentGreen.copy(alpha = 0.4f) else Border, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Smartphone,
                                                        contentDescription = null,
                                                        tint = if (dev.status == "active") AccentGreen else TextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = dev.name,
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(if (dev.status == "active") AccentGreen else AccentAmber, CircleShape)
                                                        )
                                                    }
                                                    Text(
                                                        text = "ID: ${dev.ip.take(14)} • ${dev.status.uppercase()}",
                                                        color = TextSecondary,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.removeDevice(dev.ip)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.15f)),
                                                shape = RoundedCornerShape(100.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .height(30.dp)
                                                    .border(1.dp, AccentRed.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Unpair", tint = AccentRed, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("UNPAIR", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedButton(
                                    onClick = { showClearAllConfirm = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(100.dp))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("UNPAIR ALL COMPANION DEVICES", color = AccentRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "If automatic discovery is unavailable, enter the device local IP address manually.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputIp,
                                onValueChange = {
                                    inputIp = it.filter { char -> char.isDigit() || char == '.' }
                                    inputError = null
                                },
                                placeholder = { Text("e.g. 192.168.1.50", color = TextSecondary.copy(alpha = 0.6f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceAlt,
                                    unfocusedContainerColor = SurfaceAlt,
                                    focusedBorderColor = AccentBlue,
                                    unfocusedBorderColor = Border,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(100.dp)
                            )

                            Button(
                                onClick = {
                                    val cleanIp = inputIp.trim()
                                    if (cleanIp.isEmpty()) {
                                        inputError = "IP cannot be empty."
                                    } else if (!cleanIp.contains(".") || cleanIp.length < 7) {
                                        inputError = "Invalid IP address format."
                                    } else {
                                        viewModel.addManualDevice(cleanIp)
                                        inputIp = ""
                                        inputError = null
                                    }
                                },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                modifier = Modifier
                                    .height(48.dp)
                                    .border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(100.dp))
                            ) {
                                Text("ADD", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                        }

                        inputError?.let { err ->
                            Text(
                                text = err,
                                color = AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Border)
                        )

                        Text(
                            text = "REGISTERED MANUAL IPS",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        if (manualIps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No manually added IP addresses.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                manualIps.forEach { ip ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, GlassBorderBrush, RoundedCornerShape(16.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CellTower,
                                                    contentDescription = null,
                                                    tint = AccentCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = ip,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.removeManualDevice(ip) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove IP",
                                                    tint = AccentRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showClearAllConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearAllConfirm = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed)
                            Text("Unpair All Devices?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Text(
                            "Are you sure you want to unpair all companion devices? This disconnects all connected devices and resets local and cloud pairing registrations.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearAllDevices()
                                showClearAllConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text("CONFIRM UNPAIR ALL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearAllConfirm = false }) {
                            Text("CANCEL", color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    },
                    containerColor = Surface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(AccentBlue)
                    .border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(100.dp))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("DONE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
fun CameraScannerPreview(
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            val previewView = androidx.camera.view.PreviewView(ctx)
            val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().apply {
                        setAnalyzer(
                            java.util.concurrent.Executors.newSingleThreadExecutor(),
                            QrCodeAnalyzer { result ->
                                onCodeScanned(result)
                            }
                        )
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

class QrCodeAnalyzer(private val onResult: (String) -> Unit) : androidx.camera.core.ImageAnalysis.Analyzer {
    private val reader = com.google.zxing.MultiFormatReader().apply {
        setHints(mapOf(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: androidx.camera.core.ImageProxy) {
        val yBuffer = image.planes[0].buffer
        val data = ByteArray(yBuffer.remaining())
        yBuffer.get(data)

        val source = com.google.zxing.PlanarYUVLuminanceSource(
            data,
            image.width,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false
        )
        val binaryMap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))

        try {
            val result = reader.decode(binaryMap)
            onResult(result.text)
        } catch (e: Exception) {
            // failed to decode
        } finally {
            image.close()
        }
    }
}

@Composable
fun FindMyDeviceMapCard(
    device: DiscoveredDevice,
    viewModel: AdminDashboardViewModel
) {
    val context = LocalContext.current
    var mapZoom by remember { mutableStateOf(16) }
    var isSatelliteMode by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    
    // Live real-time map updates bound to latitude/longitude
    LaunchedEffect(device.latitude, device.longitude) {
        webViewRef?.evaluateJavascript(
            "if(typeof updatePosition === 'function') { updatePosition(${device.latitude}, ${device.longitude}); }",
            null
        )
    }

    // Delayed invalidateSize trigger to prevent Android WebView zero-height layout distortion
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        webViewRef?.evaluateJavascript(
            "if(typeof map !== 'undefined' && map) { map.invalidateSize(); }",
            null
        )
    }

    val htmlContent = remember(device.ip) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body { margin: 0; padding: 0; background: #0b0f19; overflow: hidden; }
                #map { width: 100vw; height: 100vh; background: #0b0f19; }
                
                /* Native dark theme elements */
                .leaflet-container {
                    background: #0b0f19 !important;
                }
                .leaflet-control-attribution { 
                    display: none !important; 
                }
                .leaflet-div-icon, .custom-tactical-radar {
                    background: transparent !important;
                    border: none !important;
                }

                /* Crisp High-DPI non-blurry tile rendering */
                .leaflet-tile {
                    image-rendering: -webkit-optimize-contrast !important;
                    image-rendering: crisp-edges !important;
                }

                /* Tactical Dark Mode Filter: completely removes Carto watermark & blur */
                .tactical-dark-tile {
                    filter: invert(100%) hue-rotate(180deg) brightness(92%) contrast(108%) saturate(45%) !important;
                    -webkit-filter: invert(100%) hue-rotate(180deg) brightness(92%) contrast(108%) saturate(45%) !important;
                }

                /* High-resolution satellite styling */
                .satellite-tile {
                    filter: brightness(90%) contrast(105%) !important;
                    -webkit-filter: brightness(90%) contrast(105%) !important;
                }
                
                /* Tactical Radar Container - 64x64 mathematically centered */
                .tactical-radar-container {
                    position: relative;
                    width: 64px;
                    height: 64px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    pointer-events: none;
                }

                /* 360° Rotating Radar Beam with Conic Gradient */
                .radar-sweep-beam {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 64px;
                    height: 64px;
                    border-radius: 50%;
                    background: conic-gradient(
                        from 0deg,
                        rgba(0, 240, 255, 0.45) 0deg,
                        rgba(0, 240, 255, 0.16) 35deg,
                        rgba(0, 240, 255, 0.02) 65deg,
                        transparent 75deg,
                        transparent 360deg
                    );
                    animation: radar-beam-rotate 2.4s linear infinite;
                    pointer-events: none;
                }
                @keyframes radar-beam-rotate {
                    from { transform: rotate(0deg); }
                    to { transform: rotate(360deg); }
                }

                /* Concentric Radar Wave Pulses: 3 Staggered Expanding Rings with cubic-bezier */
                .radar-wave {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    width: 14px;
                    height: 14px;
                    border-radius: 50%;
                    border: 1.5px solid #00f0ff;
                    box-sizing: border-box;
                    opacity: 0;
                    pointer-events: none;
                    animation: radar-wave-pulse 2.4s cubic-bezier(0.1, 0.7, 0.2, 1) infinite;
                }
                .radar-wave.wave-1 { animation-delay: 0s; }
                .radar-wave.wave-2 { animation-delay: 0.8s; }
                .radar-wave.wave-3 { animation-delay: 1.6s; }

                @keyframes radar-wave-pulse {
                    0% {
                        width: 14px;
                        height: 14px;
                        opacity: 0.85;
                        border-color: #00f0ff;
                        box-shadow: 0 0 8px rgba(0, 240, 255, 0.8);
                    }
                    50% {
                        opacity: 0.45;
                    }
                    100% {
                        width: 64px;
                        height: 64px;
                        opacity: 0;
                        border-color: rgba(0, 240, 255, 0);
                        box-shadow: 0 0 16px rgba(0, 240, 255, 0);
                    }
                }

                /* High-Visibility Core Beacon: Electric Cyan Core, 2px Pure White Border */
                .radar-core-beacon {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    width: 14px;
                    height: 14px;
                    background: #00f0ff;
                    border: 2px solid #ffffff;
                    border-radius: 50%;
                    box-shadow: 0 0 8px #00f0ff, 0 0 16px rgba(0, 240, 255, 0.85), 0 0 28px rgba(0, 240, 255, 0.5);
                    animation: beacon-breathe 1.8s ease-in-out infinite alternate;
                    z-index: 10;
                }
                @keyframes beacon-breathe {
                    0% {
                        transform: translate(-50%, -50%) scale(0.92);
                        box-shadow: 0 0 6px #00f0ff, 0 0 14px rgba(0, 240, 255, 0.7);
                    }
                    100% {
                        transform: translate(-50%, -50%) scale(1.08);
                        box-shadow: 0 0 10px #00f0ff, 0 0 22px rgba(0, 240, 255, 0.95), 0 0 32px rgba(0, 240, 255, 0.6);
                    }
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map;
                var marker;
                var circle;
                var tacticalLayer;
                var satelliteLayer;
                var currentLayer = 'tactical';
                
                try {
                    map = L.map('map', { 
                        zoomControl: false,
                        attributionControl: false
                    }).setView([${device.latitude}, ${device.longitude}], $mapZoom);
                    
                    // Crystal clear OpenStreetMap Tactical Dark layer (no watermark, no API key required)
                    tacticalLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        maxNativeZoom: 19,
                        attribution: '',
                        className: 'tactical-dark-tile'
                    });

                    // High-resolution Esri Satellite layer (no watermark, no API key required)
                    satelliteLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                        maxZoom: 19,
                        maxNativeZoom: 19,
                        attribution: '',
                        className: 'satellite-tile'
                    });

                    tacticalLayer.addTo(map);
                    
                    // Subpixel Center Alignment: Mathematically centered [32, 32] on 64x64 container
                    var customIcon = L.divIcon({
                        className: 'custom-tactical-radar',
                        html: '<div class="tactical-radar-container">' +
                              '  <div class="radar-sweep-beam"></div>' +
                              '  <div class="radar-wave wave-1"></div>' +
                              '  <div class="radar-wave wave-2"></div>' +
                              '  <div class="radar-wave wave-3"></div>' +
                              '  <div class="radar-core-beacon"></div>' +
                              '</div>',
                        iconSize: [64, 64],
                        iconAnchor: [32, 32]
                    });
                    
                    marker = L.marker([${device.latitude}, ${device.longitude}], {icon: customIcon}).addTo(map);
                    
                    circle = L.circle([${device.latitude}, ${device.longitude}], {
                        color: '#00f0ff',
                        fillColor: '#00f0ff',
                        fillOpacity: 0.08,
                        weight: 1,
                        radius: 65
                    }).addTo(map);
                } catch(e) {
                    console.error("Map creation error", e);
                }
                
                function setMapLayer(type) {
                    if (!map) return;
                    if (type === 'satellite') {
                        if (map.hasLayer(tacticalLayer)) map.removeLayer(tacticalLayer);
                        if (!map.hasLayer(satelliteLayer)) satelliteLayer.addTo(map);
                        currentLayer = 'satellite';
                    } else {
                        if (map.hasLayer(satelliteLayer)) map.removeLayer(satelliteLayer);
                        if (!map.hasLayer(tacticalLayer)) tacticalLayer.addTo(map);
                        currentLayer = 'tactical';
                    }
                }

                // Smooth glide transitions with linear easing flyTo
                function updatePosition(lat, lng) {
                    if (map && marker && circle) {
                        var latLng = L.latLng(lat, lng);
                        map.flyTo(latLng, map.getZoom(), {
                            animate: true,
                            duration: 1.2,
                            easeLinearity: 0.25
                        });
                        marker.setLatLng(latLng);
                        circle.setLatLng(latLng);
                    }
                }

                // In-map Recenter Target function
                function recenterTarget(lat, lng, zoom) {
                    if (map) {
                        var latLng = L.latLng(lat, lng);
                        var targetZoom = zoom || 17;
                        map.flyTo(latLng, targetZoom, {
                            animate: true,
                            duration: 1.2,
                            easeLinearity: 0.25
                        });
                        if (marker) marker.setLatLng(latLng);
                        if (circle) circle.setLatLng(latLng);
                    }
                }
                
                function setZoomLevel(zoom) {
                    if (map) {
                        map.setZoom(zoom);
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Border, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Map Location",
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Device Location",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "GPS LOCK",
                    color = AccentGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(AccentGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF000000), RoundedCornerShape(8.dp))
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
            ) {
                // Interactive OpenStreetMap render using WebView
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36 GuardLink/1.2.0"
                            }
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Prevent zero-height layout distortion by forcing layout invalidation
                                    view?.evaluateJavascript(
                                        "if(typeof map !== 'undefined' && map) { setTimeout(function(){ map.invalidateSize(); }, 200); }",
                                        null
                                    )
                                }
                            }
                            isHorizontalScrollBarEnabled = false
                            isVerticalScrollBarEnabled = false
                            
                            loadDataWithBaseURL("https://tile.openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        webView.evaluateJavascript(
                            "if(typeof updatePosition === 'function') { updatePosition(${device.latitude}, ${device.longitude}); }",
                            null
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // In-map Hovered Controls: Recenter Target (GpsFixed) & Zoom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Recenter Target button (GpsFixed) to re-lock camera onto device at zoom 17
                    IconButton(
                        onClick = { 
                            mapZoom = 17
                            webViewRef?.evaluateJavascript(
                                "if(typeof recenterTarget === 'function') { recenterTarget(${device.latitude}, ${device.longitude}, 17); }",
                                null
                            )
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Recenter Target",
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { 
                            mapZoom = (mapZoom + 1).coerceAtMost(19)
                            webViewRef?.evaluateJavascript("if(typeof setZoomLevel === 'function') { setZoomLevel($mapZoom); }", null)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    ) {
                        Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = { 
                            mapZoom = (mapZoom - 1).coerceAtLeast(1)
                            webViewRef?.evaluateJavascript("if(typeof setZoomLevel === 'function') { setZoomLevel($mapZoom); }", null)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    ) {
                        Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                // In-map Top Controls: Compass & Satellite/Tactical Layer Switcher
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Compass",
                            tint = AccentAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Layer Switch Pill: Tactical Dark <-> Satellite Recon
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(0.5.dp, Color(0xFF00F0FF).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable {
                                isSatelliteMode = !isSatelliteMode
                                val layer = if (isSatelliteMode) "satellite" else "tactical"
                                webViewRef?.evaluateJavascript("if(typeof setMapLayer === 'function') { setMapLayer('$layer'); }", null)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Map Style",
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isSatelliteMode) "SATELLITE" else "TACTICAL",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text("LATITUDE", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = String.format("%.6f", device.latitude), 
                            color = TextPrimary, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column {
                        Text("LONGITUDE", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = String.format("%.6f", device.longitude), 
                            color = TextPrimary, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Open Maps button that launches external turn-by-turn navigation directly
                OutlinedButton(
                    onClick = {
                        val lat = device.latitude
                        val lng = device.longitude
                        val label = Uri.encode(device.name.ifBlank { "Tracked Device" })
                        val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00F0FF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f)),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open Maps",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF00F0FF)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "OPEN MAPS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val ringing = device.ringRequested
            Button(
                onClick = { viewModel.toggleRing(device.ip, !ringing) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ringing) AccentRed else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(1.dp, if (ringing) AccentRed else Border, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = if (ringing) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (ringing) Color.White else AccentGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (ringing) "STOP FINDER SOUND" else "PLAY LOUD FINDER SOUND",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AudioEqualizerVisualizer(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFBBF24)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioEq")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 16f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h4"
    )

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
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
