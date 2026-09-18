package com.example.ui.admin

import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel = viewModel(),
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val scanProgressText by viewModel.scanProgressText.collectAsState()

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
                  showDeviceControlsForDevice != null
    ) {
        when {
            showScreenModalForDevice != null -> showScreenModalForDevice = null
            showCameraModalForDevice != null -> showCameraModalForDevice = null
            showBlockModalForDevice != null -> showBlockModalForDevice = null
            showScheduleManager -> showScheduleManager = false
            showManualDeviceManager -> showManualDeviceManager = false
            showDeviceControlsForDevice != null -> showDeviceControlsForDevice = null
        }
    }

    Scaffold(
        bottomBar = {
            if (showDeviceControlsForDevice == null) {
                AdminBottomBar(
                    currentScreen = "dashboard",
                    onNavigateToDashboard = {},
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        },
        containerColor = Bg
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
                    onScreenClick = { showScreenModalForDevice = freshDevice }
                )
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield Logo",
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "CONTROL CENTER",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "GUARDLINK",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Box(
                        modifier = Modifier
                            .background(AccentGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Cloud Mode",
                            color = AccentGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${StateManager.deviceName.value} (Admin)",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Cloud Sync System Card (Slim elegant ribbon style)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isConnected) AccentGreen else Color(0xFFFFA726), CircleShape)
                        )
                        Text(
                            text = if (isConnected) "CLOUD SYNC SYSTEM ACTIVE" else "CLOUD SYNC ACTIVE (SYSTEM RETRYNG)",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(if (isConnected) AccentGreen.copy(alpha = 0.1f) else Color(0xFFFFA726).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, if (isConnected) AccentGreen.copy(alpha = 0.2f) else Color(0xFFFFA726).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isConnected) "ONLINE" else "RECONNECTING",
                            color = if (isConnected) AccentGreen else Color(0xFFFFA726),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress state
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

            // Compact Side-by-Side row to save valuable vertical space
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Schedule Lockdowns Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showScheduleManager = true }
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Schedules Logo",
                                tint = AccentBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LOCKDOWNS",
                                color = AccentBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Automated blocks",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Add Device Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showManualDeviceManager = true }
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .background(AccentGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Device Logo",
                                tint = AccentGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ADD DEVICE",
                                color = AccentGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Register & pair",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Screen Sharing Broadcast Authorization Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
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
                        if (isScreenAuthorized) AccentGreen.copy(alpha = 0.4f) else AccentPurple.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isScreenAuthorized) AccentGreen.copy(alpha = 0.15f) else AccentPurple.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (isScreenAuthorized) Icons.Default.CheckCircle else Icons.Default.ScreenShare,
                                contentDescription = "Screen Share Status",
                                tint = if (isScreenAuthorized) AccentGreen else AccentPurple,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isScreenAuthorized) "SCREEN SHARING: READY" else "ENABLE SCREEN SHARING",
                                color = if (isScreenAuthorized) AccentGreen else AccentPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isScreenAuthorized) "Authorized • Peers can view screen live in RAM" else "Tap to authorize peer screen viewing",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    if (!isScreenAuthorized) {
                        Box(
                            modifier = Modifier
                                .background(AccentPurple.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, AccentPurple.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "AUTHORIZE",
                                color = AccentPurple,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results Headline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISCOVERED DEVICES",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (devices.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All Paired Devices",
                                tint = AccentRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.refreshAll(context) },
                        enabled = !isScanning,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isScanning) TextSecondary else AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(SurfaceAlt, RoundedCornerShape(100.dp))
                            .border(1.dp, Border, RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${devices.size} ONLINE",
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Device List
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
                    if (devices.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CellTower,
                                contentDescription = null,
                                tint = Border,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No GuardLink devices connected.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Only devices explicitly paired using past QR or 6-character code scanning will appear here.",
                                color = Color(0xFF5E6D82),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { showManualDeviceManager = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan QR",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "PAIR DEVICE (QR / CODE)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(devices) { device ->
                                DeviceCard(
                                    device = device,
                                    onClick = { showDeviceControlsForDevice = device }
                                )
                            }
                        }
                    }
                }
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
    onClick: () -> Unit
) {
    // Pulsing indicator for active blocks
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_card")
    val blockedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blocked_alpha"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (device.status == "blocked") AccentRed.copy(alpha = 0.6f) else Border,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device Icon relative status dots
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .background(SurfaceAlt, RoundedCornerShape(12.dp))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = if (device.status == "blocked") AccentRed else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Pulse status dot
                val dotColor = when (device.status) {
                    "active" -> AccentGreen
                    "blocked" -> AccentRed
                    "connecting" -> AccentAmber
                    else -> Color.Gray
                }
                val currentAlpha = if (device.status == "blocked") blockedAlpha else 1.0f

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .border(2.dp, Surface, CircleShape)
                        .background(dotColor.copy(alpha = currentAlpha), CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.ip,
                    color = TextMono,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status label badge
            Box(
                modifier = Modifier
                    .background(
                        when (device.status) {
                            "active" -> AccentGreen.copy(alpha = 0.1f)
                            "blocked" -> AccentRed.copy(alpha = 0.1f)
                            "connecting" -> AccentAmber.copy(alpha = 0.1f)
                            else -> Color.Gray.copy(alpha = 0.1f)
                        },
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = when (device.status) {
                            "active" -> AccentGreen.copy(alpha = 0.2f)
                            "blocked" -> AccentRed.copy(alpha = 0.2f)
                            "connecting" -> AccentAmber.copy(alpha = 0.2f)
                            else -> Color.Gray.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = device.status.uppercase(),
                    color = when (device.status) {
                        "active" -> AccentGreen
                        "blocked" -> AccentRed
                        "connecting" -> AccentAmber
                        else -> Color.Gray
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Open Control Center cue
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open Device Control Center",
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
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
    onScreenClick: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

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
                        text = "Remove Device?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently remove ${device.name}? This will clear all synchronization variables, live frames, active rules, and logs.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeDevice(device.ip)
                        showRemoveDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentRed)
                ) {
                    Text("REMOVE", fontWeight = FontWeight.Bold)
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
            containerColor = SurfaceAlt,
            iconContentColor = AccentRed
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(16.dp)
        ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "DEVICE CONTROL CENTER",
                        color = AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${device.name}",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(
                        when (device.status) {
                            "online" -> AccentGreen.copy(alpha = 0.15f)
                            "blocked" -> AccentRed.copy(alpha = 0.15f)
                            else -> Color.White.copy(alpha = 0.05f)
                        },
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = device.status.uppercase(),
                    color = when (device.status) {
                        "online" -> AccentGreen
                        "blocked" -> AccentRed
                        else -> TextSecondary
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
        Spacer(modifier = Modifier.height(16.dp))

        // Content Scrollable Column!
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
                        // 1. Connection Actions (Buttons)
                        Text(
                            text = "COMMAND HUB",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (device.status == "offline" || device.status == "connecting") {
                                Button(
                                    onClick = { viewModel.retryConnect(device.ip) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = device.status != "connecting"
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (device.status == "connecting") "CONNECTING..." else "RETRY CONNECTION",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        onBlockClick()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "BLOCK SCREEN",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Button(
                                    onClick = { viewModel.unblockScreen(device.ip) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = device.status == "blocked"
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "UNBLOCK",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        if (device.status != "offline" && device.status != "connecting") {
                            Button(
                                onClick = onScreenClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenShare,
                                        contentDescription = "View Live Screen",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "VIEW LIVE SCREEN STREAM",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Button(
                                onClick = onCameraClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "VIEW LIVE CAMERA FEED",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        // 2. Telemetry Section
                        Text(
                            text = "LIVE DEVICE TELEMETRY",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                            imageVector = if (device.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                            contentDescription = "Battery Status",
                                            tint = if (device.batteryLevel < 20) AccentRed else if (device.isCharging) AccentGreen else AccentAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Battery Status", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Text(
                                        text = "${if (device.isCharging) "⚡ " else "🔌 "}${device.batteryLevel}%",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = "Active App",
                                            tint = AccentBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Active App", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Text(
                                        text = device.activeApp,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Ringer Mode",
                                            tint = AccentGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Audio Mode", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Text(
                                        text = device.ringerMode,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // GPS Map and Find My Device Locating
                        FindMyDeviceMapCard(device = device, viewModel = viewModel)

                        // 3. Lock Screen Customization Editor
                        Text(
                            text = "LOCK SCREEN DESIGNER STUDIO",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                var activeTheme by remember(device.lockTheme) { mutableStateOf(device.lockTheme) }
                                var activeIcon by remember(device.lockWarningIcon) { mutableStateOf(device.lockWarningIcon) }
                                var activeWallpaper by remember(device.lockWallpaper) { mutableStateOf(device.lockWallpaper) }

                                Text("Lock Theme", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("slate", "cyberpunk", "stealth").forEach { t ->
                                        val isSel = activeTheme == t
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isSel) AccentBlue.copy(alpha = 0.2f) else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSel) AccentBlue else Border,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { activeTheme = t }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(t.uppercase(), color = if (isSel) AccentBlue else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Warning Icon", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("lock", "biohazard", "warning", "hourglass").forEach { iName ->
                                        val isSel = activeIcon == iName
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isSel) AccentBlue.copy(alpha = 0.2f) else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSel) AccentBlue else Border,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { activeIcon = iName }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(iName.uppercase(), color = if (isSel) AccentBlue else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Wallpaper Backdrop (Abstract URL)", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = activeWallpaper,
                                    onValueChange = { activeWallpaper = it },
                                    placeholder = { Text("https://example.com/wallpaper.jpg", fontSize = 11.sp, color = Color.Gray) },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentBlue,
                                        unfocusedBorderColor = Border
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        viewModel.updateLockStyle(device.ip, activeTheme, activeWallpaper, activeIcon)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                ) {
                                    Text("APPLY VISUAL STYLE", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 4. Admin Chat Terminal (Only visible if the user device screen is blocked)
                        if (device.status == "blocked") {
                            Text(
                                text = "ADMINISTRATOR RECON CHAT TERMINAL",
                                color = AccentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    var adminChatMessages by remember { mutableStateOf<List<com.example.data.ChatMessage>>(emptyList()) }
                                    var adminChatInputText by remember { mutableStateOf("") }

                                    DisposableEffect(device.ip) {
                                        val chatRef = FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.asia-southeast1.firebasedatabase.app/")
                                            .getReference("devices").child(device.ip).child("chat")
                                        val listener = object : ValueEventListener {
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
                                        chatRef.addValueEventListener(listener)
                                        onDispose {
                                            chatRef.removeEventListener(listener)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        if (adminChatMessages.isEmpty()) {
                                            Text(
                                                text = "No logs. Set a text line below and transmit to the block screen.",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        } else {
                                            val lazyListState = rememberLazyListState()
                                            LaunchedEffect(adminChatMessages.size) {
                                                if (adminChatMessages.isNotEmpty()) {
                                                    lazyListState.animateScrollToItem(adminChatMessages.size - 1)
                                                }
                                            }
                                            LazyColumn(
                                                state = lazyListState,
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                items(adminChatMessages) { msg ->
                                                    val isMe = msg.sender == "admin"
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    color = if (isMe) AccentBlue.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f),
                                                                    shape = RoundedCornerShape(
                                                                        topStart = 14.dp,
                                                                        topEnd = 14.dp,
                                                                        bottomStart = if (isMe) 14.dp else 2.dp,
                                                                        bottomEnd = if (isMe) 2.dp else 14.dp
                                                                    )
                                                                )
                                                                .border(
                                                                    width = 1.dp,
                                                                    color = if (isMe) AccentBlue else Border.copy(alpha = 0.4f),
                                                                    shape = RoundedCornerShape(
                                                                        topStart = 14.dp,
                                                                        topEnd = 14.dp,
                                                                        bottomStart = if (isMe) 14.dp else 2.dp,
                                                                        bottomEnd = if (isMe) 2.dp else 14.dp
                                                                    )
                                                                )
                                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                                                .widthIn(max = 200.dp)
                                                        ) {
                                                            Column {
                                                                Text(
                                                                    text = if (isMe) "Admin (You)" else "User",
                                                                    color = if (isMe) Color.White.copy(alpha = 0.8f) else Color(0xFF90A4AE),
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                Text(
                                                                    text = msg.message,
                                                                    color = Color.White,
                                                                    fontSize = 11.sp
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
                                            placeholder = { Text("Type reply...", color = Color.Gray, fontSize = 11.sp) },
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 11.sp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AccentBlue,
                                                unfocusedBorderColor = Border
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
                                                .size(36.dp)
                                                .background(AccentBlue, CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "Send Message",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Operational Logs List with Scroll Box and Clear logs button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ADMIN COMMAND OPERATIONAL LOGS",
                                color = AccentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            TextButton(
                                onClick = {
                                    viewModel.clearLogs(device.ip)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear Logs",
                                    tint = AccentRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CLEAR LOGS", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                var operationLogs by remember { mutableStateOf<List<com.example.data.AdminLog>>(emptyList()) }

                                DisposableEffect(device.ip) {
                                    val logsRef = FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.asia-southeast1.firebasedatabase.app/")
                                        .getReference("devices").child(device.ip).child("logs")
                                    val listener = object : ValueEventListener {
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
                                    logsRef.addValueEventListener(listener)
                                    onDispose {
                                        logsRef.removeEventListener(listener)
                                    }
                                }

                                if (operationLogs.isEmpty()) {
                                    Text(
                                        text = "No operational logs has been captured yet.",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp)
                                    )
                                } else {
                                    // Make logs box scrollable inside! The user asked: "dont expand the box when its more logs just make it scrollable inside the box"
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(operationLogs) { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(
                                                                when (item.action) {
                                                                    "LOCK", "GEOFENCE_VIOLATION" -> AccentRed
                                                                    "UNLOCK" -> AccentGreen
                                                                    else -> AccentBlue
                                                                },
                                                                CircleShape
                                                            )
                                                            .align(Alignment.CenterVertically)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${item.action} • ${item.details}",
                                                            color = TextPrimary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        val date = java.util.Date(item.timestamp)
                                                        val timeStr = java.text.SimpleDateFormat(
                                                            "hh:mm a",
                                                            java.util.Locale.getDefault()
                                                        ).format(date)
                                                        Text(
                                                            text = "Timestamp: $timeStr",
                                                            color = TextSecondary,
                                                            fontSize = 9.sp,
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

                        // Danger Zone / Remove Device trigger section
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "DANGER ZONE",
                            color = AccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Permanently remove this device from the telemetry mesh. The client device will instantly stop background location, active frames sync, and remote security tasks.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Button(
                                    onClick = { showRemoveDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Device",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "REMOVE DEVICE FROM MONITOR",
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Surface,
        title = {
            Text(
                text = "LOCK SCREEN NOW",
                color = AccentRed,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
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
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = { onSendBlock(message, password, timerSeconds, selectedImageBase64) }
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "🔒 LOCK SCREEN NOW",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onDismiss
            ) {
                Text("CANCEL", color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
        },
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 500.dp)
            .border(1.dp, Border, RoundedCornerShape(20.dp))
    )
}

@Composable
fun AdminBottomBar(
    currentScreen: String,
    onNavigateToDashboard: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    NavigationBar(
        containerColor = Surface,
        tonalElevation = 8.dp,
        modifier = Modifier.border(width = (0.5).dp, color = Border)
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
                    "Control Panel",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentBlue,
                unselectedIconColor = TextSecondary,
                indicatorColor = AccentBlue.copy(alpha = 0.1f)
            )
        )

        NavigationBarItem(
            selected = currentScreen == "settings",
            onClick = onNavigateToSettings,
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            },
            label = {
                Text(
                    "Settings",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentBlue,
                unselectedIconColor = TextSecondary,
                indicatorColor = AccentBlue.copy(alpha = 0.1f)
            )
        )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth()
            .padding(16.dp),
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "DISCONNECT FEED",
                    color = Color.Black,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
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
        },
        containerColor = Color(0xFF0F0F12),
        shape = RoundedCornerShape(16.dp)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(max = 520.dp)
            .border(1.dp, Border, RoundedCornerShape(20.dp)),
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CLOSE LIVE SCREEN FEED",
                    color = TextPrimary,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ScreenShare,
                        contentDescription = "Live Screen Indicator",
                        tint = if (decodedBitmap != null) AccentGreen else AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LIVE REMOTE SCREEN",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${device.name} • ${device.ip}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
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
                            .background(Color(0xFF07070A))
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
        },
        containerColor = Color(0xFF0F0F14),
        shape = RoundedCornerShape(20.dp)
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
            .border(1.dp, Border, RoundedCornerShape(24.dp)),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = AccentBlue
                    )
                    Text(
                        text = if (isEditorOpen) (if (editingScheduleId != null) "EDIT SCHEDULE" else "NEW SCHEDULE") else "LOCKDOWN SCHEDULER",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
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
                    .heightIn(max = 520.dp),
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
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(
                                        onClick = { applyPreset("bedtime") },
                                        label = { Text("Bedtime (21-07)", fontSize = 10.sp) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Surface)
                                    )
                                    AssistChip(
                                        onClick = { applyPreset("school") },
                                        label = { Text("School (08-15)", fontSize = 10.sp) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Surface)
                                    )
                                    AssistChip(
                                        onClick = { applyPreset("dinner") },
                                        label = { Text("Dinner (18-20)", fontSize = 10.sp) },
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
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val quickChips = listOf("All Devices") + devices.map { it.name }
                                    quickChips.distinct().take(4).forEach { name ->
                                        val isSelected = targetDeviceName.equals(name, ignoreCase = true)
                                        AssistChip(
                                            onClick = { targetDeviceName = name },
                                            label = { Text(name, fontSize = 10.sp) },
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

    var selectedTab by remember { mutableStateOf("scan") } // "scan", "my_qr", "local"
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

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(max = 480.dp)
            .border(1.dp, Border, RoundedCornerShape(24.dp)),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = AccentBlue
                    )
                    Text(
                        text = "PAIR GUARDLINK DEVICE",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tab Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceAlt, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabModifierScan = Modifier
                        .weight(1f)
                        .background(
                            if (selectedTab == "scan") AccentBlue.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = "scan"; isScanningQr = false }
                        .padding(vertical = 10.dp)

                    Box(modifier = tabModifierScan, contentAlignment = Alignment.Center) {
                        Text(
                            text = "SCAN / CODE",
                            color = if (selectedTab == "scan") AccentBlue else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    val tabModifierMyQr = Modifier
                        .weight(1f)
                        .background(
                            if (selectedTab == "my_qr") AccentAmber.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = "my_qr"; isScanningQr = false }
                        .padding(vertical = 10.dp)

                    Box(modifier = tabModifierMyQr, contentAlignment = Alignment.Center) {
                        Text(
                            text = "MY QR / CODE",
                            color = if (selectedTab == "my_qr") AccentAmber else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    val tabModifierLocal = Modifier
                        .weight(0.9f)
                        .background(
                            if (selectedTab == "local") AccentBlue.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = "local" }
                        .padding(vertical = 10.dp)

                    Box(modifier = tabModifierLocal, contentAlignment = Alignment.Center) {
                        Text(
                            text = "LOCAL IP",
                            color = if (selectedTab == "local") AccentBlue else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
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
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            if (hasCameraPermission) {
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(2.dp, AccentAmber, RoundedCornerShape(16.dp))
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
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceAlt),
                                    modifier = Modifier.border(1.dp, Border, RoundedCornerShape(12.dp))
                                ) {
                                    Text("Grant Camera Permission", color = TextPrimary)
                                }
                            }

                            TextButton(onClick = { isScanningQr = false }) {
                                Text("Cancel Scanner", color = AccentRed)
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
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF142416)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pair Another Device", color = TextPrimary)
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
                                        placeholder = { Text("Code e.g. GFX8M9") },
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
                                        shape = RoundedCornerShape(12.dp)
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
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        if (isPairingOnline) {
                                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp))
                                        } else {
                                            Text("PAIR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (firebaseError != null) {
                                    Text(firebaseError!!, color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                    onClick = { isScanningQr = true },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(AccentAmber.copy(alpha = 0.1f), CircleShape),
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
                                                "Instantly pair by scanning QR with camera",
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
                            text = "Display this QR code or 6-character code to the other device to pair.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
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
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 4.sp
                                )

                                if (myPairingCode != null) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White, RoundedCornerShape(12.dp))
                                            .padding(8.dp)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Border, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("GENERATE NEW CODE", color = AccentAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "If automatic scanning misses a device, configure its IP manually below to initiate full lock & control.",
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
                                placeholder = { Text("e.g. 192.168.1.50") },
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
                                shape = RoundedCornerShape(12.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("ADD", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            color = AccentAmber,
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
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(manualIps.toList()) { ip ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Border, RoundedCornerShape(12.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
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
                                                    tint = AccentBlue,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = ip,
                                                    color = Color.White,
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
                                                    tint = AccentRed.copy(alpha = 0.8f),
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
    var mapZoom by remember { mutableStateOf(16) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    
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
                
                /* Glowing Locator Dot */
                .marker-glow {
                    width: 14px;
                    height: 14px;
                    background-color: #03a9f4; /* AccentBlue */
                    border: 3px solid #ffffff;
                    border-radius: 50%;
                    box-shadow: 0 0 12px #03a9f4, 0 0 20px rgba(3,169,244,0.4);
                    position: absolute;
                    left: -3px;
                    top: -3px;
                    animation: pulse 2s infinite ease-in-out;
                }
                
                @keyframes pulse {
                    0% {
                        transform: scale(0.9);
                        box-shadow: 0 0 0 0 rgba(3, 169, 244, 0.7);
                    }
                    70% {
                        transform: scale(1.1);
                        box-shadow: 0 0 0 12px rgba(3, 169, 244, 0);
                    }
                    100% {
                        transform: scale(0.9);
                        box-shadow: 0 0 0 0 rgba(3, 169, 244, 0);
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
                
                try {
                    map = L.map('map', { 
                        zoomControl: false,
                        attributionControl: false
                    }).setView([${device.latitude}, ${device.longitude}], $mapZoom);
                    
                    // CartoBasemaps Dark Matter maps are native-dark, clean and bypass policy bans
                    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: ''
                    }).addTo(map);
                    
                    var customIcon = L.divIcon({
                        className: 'custom-icon',
                        html: '<div class="marker-glow"></div>',
                        iconSize: [20, 20],
                        iconAnchor: [10, 10]
                    });
                    
                    marker = L.marker([${device.latitude}, ${device.longitude}], {icon: customIcon}).addTo(map);
                    
                    circle = L.circle([${device.latitude}, ${device.longitude}], {
                        color: '#03a9f4',
                        fillColor: '#03a9f4',
                        fillOpacity: 0.12,
                        radius: 80
                    }).addTo(map);
                } catch(e) {
                    console.error("Map creation error", e);
                }
                
                function updatePosition(lat, lng) {
                    if (map && marker && circle) {
                        var latLng = L.latLng(lat, lng);
                        map.panTo(latLng);
                        marker.setLatLng(latLng);
                        circle.setLatLng(latLng);
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
                        tint = AccentBlue,
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
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
            ) {
                // Interactive OpenStreetMap render using WebView!
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
                                // Set modern high-quality User Agent to prevent any automated 418 bans
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36 GuardLink/1.2.0"
                            }
                            webViewClient = android.webkit.WebViewClient()
                            isHorizontalScrollBarEnabled = false
                            isVerticalScrollBarEnabled = false
                            
                            loadDataWithBaseURL("https://basemaps.cartocdn.com", htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        webView.evaluateJavascript("if(typeof updatePosition === 'function') { updatePosition(${device.latitude}, ${device.longitude}); }", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Hovered zoom controls (Google Maps styled overlays)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Compass",
                        tint = AccentAmber,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LATITUDE", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = String.format("%.6f", device.latitude), 
                        color = TextPrimary, 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
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
