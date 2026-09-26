package com.example.ui.admin

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    pulseScale: Float = 1f,
    pulseAlpha: Float = 0.5f,
    isConnected: Boolean = false,
    adminLabel: State<String>? = null,
    onNavigateToDashboard: () -> Unit = {}
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

    val resolvedAdminLabel = adminLabel ?: remember {
        derivedStateOf {
            StateManager.adminName.value.ifEmpty {
                StateManager.deviceName.value.ifEmpty { "This Phone" }
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 8.dp)
    ) {
        // Space-Maximized Compact Top Header (Identical alignment to Home and List)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = if (isLandscape) 2.dp else 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Relocated Compact Pill combining Sync Status & Phone Name
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
                        text = resolvedAdminLabel.value,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = "GuardLink Settings",
                color = Color.White,
                fontSize = if (isLandscape) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 10.dp))

        // Scrollable settings content filling remaining vertical space
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. HERO PAIRING CARD: Prominent 6-digit code generator with clear instructions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEVICE PAIRING & SYNC",
                        color = Color(0xFF7E8B9E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF191F2C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF2B364A), RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "To pair another phone or tablet, generate a 6-digit sync code and enter it on the other device's control screen.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            val pCode by com.example.network.FirebaseManager.pairingCode.collectAsState()

                            if (pCode != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF252E40), RoundedCornerShape(14.dp)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "PAIRING CODE",
                                            color = Color(0xFF60A5FA),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = pCode ?: "",
                                            color = Color.White,
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 4.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // QR Code representation
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF0F131C), RoundedCornerShape(12.dp))
                                                .border(1.dp, Color(0xFF252E40), RoundedCornerShape(12.dp))
                                                .padding(8.dp)
                                        ) {
                                            com.example.ui.user.QrCodeView(
                                                data = "guardlink_pair:$pCode",
                                                modifier = Modifier.size(140.dp)
                                            )
                                        }

                                        Text(
                                            text = "Waiting for peer device handshake...",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (pCode != null) Color(0xFF3B161B) else Color(0xFF1E3A5F)
                                ),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .border(
                                        1.dp,
                                        if (pCode != null) Color(0xFF7F1D1D) else Color(0xFF2E5B8F),
                                        RoundedCornerShape(100.dp)
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
                                    tint = if (pCode != null) Color(0xFFF87171) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (pCode != null) "Cancel Pairing Code" else "Generate 6-Digit Code",
                                    fontWeight = FontWeight.Bold,
                                    color = if (pCode != null) Color(0xFFF87171) else Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // 2. DEFAULT BLOCK CONFIGURATION
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEFAULT BLOCK CONFIGURATION",
                        color = Color(0xFF7E8B9E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF191F2C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF2B364A), RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp)
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
                                label = { Text("Display Name / Console Name", color = Color(0xFF8896AB)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF131722),
                                    unfocusedContainerColor = Color(0xFF131722),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF2B364A)
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
                                label = { Text("Default Block Message", color = Color(0xFF8896AB)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF131722),
                                    unfocusedContainerColor = Color(0xFF131722),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF2B364A)
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
                                label = { Text("Default Unlock Passcode", color = Color(0xFF8896AB)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Visibility",
                                            tint = Color(0xFF8896AB)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF131722),
                                    unfocusedContainerColor = Color(0xFF131722),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF2B364A)
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
                        color = Color(0xFF7E8B9E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF191F2C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF2B364A), RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp)
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
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Advanced Network Diagnostics",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand Diagnostics",
                                    tint = Color(0xFF8896AB),
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
                                    HorizontalDivider(color = Color(0xFF252E40), thickness = 1.dp)
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF191F2C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF2B364A), RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "BIDIRECTIONAL PEER MESH",
                            color = Color(0xFF60A5FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "All devices connected to this account have equal peer capabilities. Any device can view live screens, view cameras, dispatch intercom audio, and manage lockdown schedules with connected peer devices.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
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
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Bottom padding for floating navigation bar
                Spacer(modifier = Modifier.height(90.dp))
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

