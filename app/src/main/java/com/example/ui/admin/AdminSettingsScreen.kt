package com.example.ui.admin

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StateManager
import com.example.network.NetworkScanner
import com.example.ui.theme.*

@Composable
fun AdminSettingsScreen(
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val localIp = remember<String> { NetworkScanner.getLocalIpAddress(context) }

    var defaultMsg by remember { mutableStateOf(StateManager.defaultMessage.value) }
    var defaultPass by remember { mutableStateOf(StateManager.defaultPassword.value) }
    var showPassword by remember { mutableStateOf(false) }
    var adminName by remember { mutableStateOf(StateManager.deviceName.value) }
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isDiagnosticsExpanded) 180f else 0f,
        label = "diag_chevron"
    )

    Scaffold(
        bottomBar = {
            AdminBottomBar(
                currentScreen = "settings",
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigateToSettings = {}
            )
        },
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
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Screen Title Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, Border, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings Icon",
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "GUARDLINK SETTINGS",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // 1. HERO PAIRING CARD: Prominent 6-digit code generator with clear instructions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEVICE PAIRING & SYNC",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(LiquidGlassGlareGradient)
                            .border(1.2.dp, LiquidGlassChromaticBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "To pair another phone or tablet, generate a 6-digit sync code and enter it on the other device's control screen.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            val pCode by com.example.network.FirebaseManager.pairingCode.collectAsState()

                            if (pCode != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LiquidGlassGlareGradient)
                                        .border(1.2.dp, GlassAccentBorderBrush, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "PAIRING CODE",
                                            color = AccentBlue,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = pCode ?: "",
                                            color = TextPrimary,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 4.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // QR Code representation
                                        com.example.ui.user.QrCodeView(
                                            data = "guardlink_pair:$pCode",
                                            modifier = Modifier.size(140.dp)
                                        )

                                        Text(
                                            text = "Waiting for peer device handshake...",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (pCode != null) AccentRed else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .then(
                                        if (pCode == null) {
                                            Modifier
                                                .background(GlassButtonGradient, RoundedCornerShape(8.dp))
                                                .border(1.dp, GlassAccentBorderBrush, RoundedCornerShape(8.dp))
                                        } else {
                                            Modifier.border(1.dp, GlassRedBorderBrush, RoundedCornerShape(8.dp))
                                        }
                                    ),
                                onClick = {
                                    if (pCode != null) {
                                        com.example.network.FirebaseManager.stopUserSyncOnly(context)
                                        com.example.network.FirebaseManager.pairingCode.value = null
                                    } else {
                                        com.example.network.FirebaseManager.generateAndPublishPairingCode(context)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (pCode != null) Icons.Default.Cancel else Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (pCode != null) "Cancel Pairing Code" else "Generate 6-Digit Code",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // 2. DEFAULT BLOCK CONFIGURATION: Cleanly padded Material input fields
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEFAULT BLOCK CONFIGURATION",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(LiquidGlassGlareGradient)
                            .border(1.2.dp, LiquidGlassChromaticBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Admin Display Name
                            OutlinedTextField(
                                value = adminName,
                                onValueChange = {
                                    adminName = it
                                    StateManager.setDeviceName(it)
                                },
                                label = { Text("Display Name / Console Name", color = TextSecondary) },
                                shape = RoundedCornerShape(8.dp),
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

                            // Default Block Message
                            OutlinedTextField(
                                value = defaultMsg,
                                onValueChange = {
                                    defaultMsg = it
                                    StateManager.setDefaultMessage(it)
                                },
                                label = { Text("Default Block Message", color = TextSecondary) },
                                shape = RoundedCornerShape(8.dp),
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

                            // Default Unlock Passcode
                            OutlinedTextField(
                                value = defaultPass,
                                onValueChange = {
                                    defaultPass = it
                                    StateManager.setDefaultPassword(it)
                                },
                                label = { Text("Default Unlock Passcode", color = TextSecondary) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
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
                        }
                    }
                }

                // 3. ADVANCED NETWORK DIAGNOSTICS: Collapsible Accordion Layout
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SYSTEM & DIAGNOSTICS",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(LiquidGlassGlareGradient)
                            .border(1.2.dp, LiquidGlassChromaticBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Accordion Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDiagnosticsExpanded = !isDiagnosticsExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lan,
                                        contentDescription = null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Advanced Network Diagnostics",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand Diagnostics",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .rotate(chevronRotation)
                                )
                            }

                            // Collapsible Content
                            AnimatedVisibility(
                                visible = isDiagnosticsExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HorizontalDivider(color = Border, thickness = 1.dp)
                                    NetworkInfoRow(label = "Local IP Address", value = localIp, isMonoText = true)
                                    NetworkInfoRow(label = "WebSocket Port", value = "9999", isMonoText = true)
                                    NetworkInfoRow(label = "Thread Pool Capacity", value = "50 Parallel Workers", isMonoText = true)
                                }
                            }
                        }
                    }
                }

                // Bidirectional Peer Mesh Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LiquidGlassGlareGradient)
                        .border(1.2.dp, GlassAccentBorderBrush, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "BIDIRECTIONAL PEER MESH",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "All devices connected to this account have equal peer capabilities. Any device can view live screens, view cameras, dispatch intercom audio, and manage lockdown schedules with connected peer devices.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                // App version info at bottom
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GuardLink v1.2.0 — Secure Admin Mesh",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkInfoRow(label: String, value: String, isMonoText: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Text(
            text = value,
            color = if (isMonoText) TextMono else TextPrimary,
            fontSize = 12.sp,
            fontFamily = if (isMonoText) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (isMonoText) FontWeight.Bold else FontWeight.Medium
        )
    }
}

