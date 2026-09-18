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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
            .background(Bg)
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
                modifier = Modifier.size(100.dp)
            ) {
                // Background glow
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(shieldScale)
                        .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(100))
                )
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield Logo",
                    tint = AccentBlue,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(shieldScale)
                )
            }

            // Brand Titles
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "GUARDLINK",
                    color = TextPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Local Network Device Control",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Border)
            )

            // Role selection cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RoleSelectionCard(
                    title = "ADMINISTRATOR",
                    subtitle = "Control and monitor other devices on your local network.",
                    icon = Icons.Default.Shield,
                    borderColor = AccentBlue,
                    iconColor = AccentBlue,
                    onClick = {
                        selectedRoleOption = "admin"
                        inputName = "Admin Console"
                        showDialog = true
                    }
                )

                RoleSelectionCard(
                    title = "USER DEVICE",
                    subtitle = "Install and run GuardLink on this device to be locked or monitored.",
                    icon = Icons.Default.Lock,
                    borderColor = Border,
                    iconColor = TextSecondary,
                    onClick = {
                        selectedRoleOption = "user"
                        inputName = Build.MODEL ?: "User Phone"
                        showDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Local IP Address display
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Local IP Address:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = localIp,
                    color = TextMono,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
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
                        text = if (selectedRoleOption == "admin") "ENTER ADMIN NAME" else "ENTER DEVICE NAME",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        singleLine = true,
                        label = { Text("Display Name", color = TextSecondary) },
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
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp),
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
                        Text("CONFIRM", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("CANCEL", color = TextSecondary)
                    }
                },
                modifier = Modifier.border(1.dp, Border, RoundedCornerShape(28.dp))
            )
        }
    }
}

@Composable
fun RoleSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    borderColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(SurfaceAlt, RoundedCornerShape(8.dp))
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
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
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
