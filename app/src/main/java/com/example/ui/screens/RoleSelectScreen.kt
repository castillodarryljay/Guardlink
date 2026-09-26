package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StateManager
import com.example.network.NetworkScanner
import com.example.overlay.OverlayManager
import com.example.ui.theme.*

@Composable
fun RoleSelectScreen(
    onRoleSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var inputName by remember { mutableStateOf("") }
    var selectedRoleOption by remember { mutableStateOf<String?>(null) } // "admin" or "user"
    var showDialog by remember { mutableStateOf(false) }

    val localIp = remember<String> { NetworkScanner.getLocalIpAddress(context) }

    // Pulsing shield animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_select")
    val shieldScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header Shield Logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF1B2332), CircleShape)
                    .border(1.dp, Color(0xFF2B374E), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield Logo",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier
                        .size(42.dp)
                        .scale(shieldScale)
                )
            }

            // Brand Titles
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CONTROL CENTER",
                    color = Color(0xFF7E8B9E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "GuardLink",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Local Network & Firebase Device Mesh",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF252E40))
            )

            // Role selection cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RoleSelectionCard(
                    title = "ADMINISTRATOR CONSOLE",
                    subtitle = "Remote command center: Lock devices, inspect live camera & screen, send intercom broadcasts, and monitor GPS.",
                    icon = Icons.Default.Shield,
                    borderBrush = LiquidGlassChromaticBorder,
                    iconColor = AccentCyan,
                    badgeText = "MASTER HUB",
                    badgeColor = AccentCyan,
                    onClick = {
                        selectedRoleOption = "admin"
                        inputName = "Admin Console"
                        showDialog = true
                    }
                )

                RoleSelectionCard(
                    title = "MANAGED USER DEVICE",
                    subtitle = "Companion receiver: Connect via pairing code or QR to receive lockdown security policies and two-way intercom.",
                    icon = Icons.Default.Lock,
                    borderBrush = GlassGreenBorderBrush,
                    iconColor = AccentGreen,
                    badgeText = "PROTECTED NODE",
                    badgeColor = AccentGreen,
                    onClick = {
                        selectedRoleOption = "user"
                        inputName = Build.MODEL ?: "User Phone"
                        showDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Local IP Address display pill
            Box(
                modifier = Modifier
                    .background(Color(0xFF131722), RoundedCornerShape(100.dp))
                    .border(1.dp, Color(0xFF202737), RoundedCornerShape(100.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                    Text(
                        text = "LOCAL IP: $localIp",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Dialog for setting Device or Admin Name
        if (showDialog) {
            val themeBlur = LocalThemeBlur.current
            DisposableEffect(Unit) {
                themeBlur.value = true
                onDispose {
                    themeBlur.value = false
                }
            }
            AlertDialog(
                onDismissRequest = { showDialog = false },
                containerColor = Surface,
                title = {
                    Text(
                        text = if (selectedRoleOption == "admin") "SETUP ADMINISTRATOR CONSOLE" else "SETUP USER DEVICE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        color = Color.White
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (selectedRoleOption == "admin") 
                                "Enter a descriptive name for this admin console to identify yourself during live intercom and broadcasts."
                                else "Enter a device name for this phone (e.g. Living Room Tablet, Office Phone).",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            singleLine = true,
                            label = { Text("Display Name", color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceAlt,
                                unfocusedContainerColor = SurfaceAlt,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = Border
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .border(1.dp, GlassBorderBrush, RoundedCornerShape(100.dp)),
                        onClick = {
                            if (inputName.isNotBlank() && selectedRoleOption != null) {
                                val role = selectedRoleOption!!
                                StateManager.setRole(role)
                                StateManager.setDeviceName(inputName)

                                if (role == "user") {
                                    // Check or request alert permissions
                                    if (!OverlayManager.checkOverlayPermission(context)) {
                                        OverlayManager.requestOverlayPermission(context)
                                    }
                                }

                                showDialog = false
                                onRoleSelected(role)
                            }
                        }
                    ) {
                        Text("LAUNCH CONSOLE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("CANCEL", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 440.dp)
                    .border(1.dp, LiquidGlassChromaticBorder, RoundedCornerShape(24.dp))
            )
        }
    }
}

@Composable
fun RoleSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    borderBrush: Brush,
    iconColor: Color,
    badgeText: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, borderBrush, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .background(SurfaceAlt, CircleShape)
                    .border(1.dp, borderBrush, CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = iconColor.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
