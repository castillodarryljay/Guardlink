package com.example.ui.admin

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
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

    Scaffold(
        bottomBar = {
            AdminBottomBar(
                currentScreen = "settings",
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigateToSettings = {}
            )
        },
        containerColor = Bg
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            // Title Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GUARDLINK SETTINGS",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
            }

            // Section 1: Default Block Config
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DEFAULT BLOCK SETUP",
                    color = TextSecondary,
                    fontSize = 12.sp,
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
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Admin Name Keyed Input
                        OutlinedTextField(
                            value = adminName,
                            onValueChange = {
                                adminName = it
                                StateManager.setDeviceName(it)
                            },
                            label = { Text("Display Name / Console Name", color = TextSecondary) },
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

                        // Message text
                        OutlinedTextField(
                            value = defaultMsg,
                            onValueChange = {
                                defaultMsg = it
                                StateManager.setDefaultMessage(it)
                            },
                            label = { Text("Default Block Message", color = TextSecondary) },
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

                        // Password text
                        OutlinedTextField(
                            value = defaultPass,
                            onValueChange = {
                                defaultPass = it
                                StateManager.setDefaultPassword(it)
                            },
                            label = { Text("Default Unlock Passcode", color = TextSecondary) },
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
                    }
                }
            }

            // Section 2: Network Info Details
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LOCAL WiFi INFRASTRUCTURE",
                    color = TextSecondary,
                    fontSize = 12.sp,
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
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NetworkInfoRow(label = "Local IP Address", value = localIp, isMonoText = true)
                        NetworkInfoRow(label = "WebSocket Port", value = "9999", isMonoText = true)
                        NetworkInfoRow(label = "Scanning Scope", value = "Parallel Thread Executor (Capacity: 50)", isMonoText = false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 3: Connection Pairing Codes Setup (Moved to Admin Screen!)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CONNECTION & PAIRING CODES",
                    color = TextSecondary,
                    fontSize = 12.sp,
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
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "To let other Admin devices monitor or find this device, generate a code here and enter it on their device control screen.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        val pCode by com.example.network.FirebaseManager.pairingCode.collectAsState()

                        if (pCode != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "YOUR PAIRING CODE",
                                        color = AccentAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = pCode ?: "",
                                        color = TextPrimary,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 4.sp
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Visual QR Code generator representation
                                    com.example.ui.user.QrCodeView(
                                        data = "guardlink_pair:$pCode",
                                        modifier = Modifier.size(150.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Waiting for other admin handshake...",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pCode != null) AccentRed else AccentBlue
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
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
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (pCode != null) "CANCEL CODE" else "GENERATE CODE",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bidirectional Peer Mesh Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .iosLiquidGlass(cornerRadius = 16.dp, borderAlpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "BIDIRECTIONAL PEER MESH",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "All devices connected to this account have equal peer capabilities. Any device can view live screens, view cameras, dispatch intercom audio, and manage lockdown schedules with connected peer devices.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // App version info at bottom
            Box(
                modifier = Modifier.fillMaxWidth(),
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
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = if (isMonoText) TextMono else TextPrimary,
            fontSize = 13.sp,
            fontFamily = if (isMonoText) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (isMonoText) FontWeight.Bold else FontWeight.Medium
        )
    }
}
