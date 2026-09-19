package com.example.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Schedule
import com.example.data.StateManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.ui.theme.OverlayBg
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.SurfaceAlt
import com.example.ui.theme.Border
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.SurfaceAlt
import com.example.ui.theme.Border
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.HourglassEmpty
import com.example.data.ChatMessage
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue

object OverlayManager {
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var statusBarBlockerView: View? = null
    private var collapseJob: kotlinx.coroutines.Job? = null
    var isOverlayShowing = false
        private set

    private fun collapseStatusBar(context: Context) {
        try {
            @Suppress("WrongConstant")
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val collapseMethod = statusBarManager.getMethod("collapsePanels")
            collapseMethod.invoke(statusBarService)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class DummyLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val controller = SavedStateRegistryController.create(this)

        init {
            controller.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    }

    fun checkOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun showOverlay(
        context: Context,
        message: String,
        correctPassword: String,
        onCorrectPasswordEntered: () -> Unit
    ) {
        if (isOverlayShowing) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 50
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            format = PixelFormat.TRANSLUCENT
        }

        val view = ComposeView(context).apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)

            setOnSystemUiVisibilityChangeListener { visibility ->
                if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setOnApplyWindowInsetsListener { v, insets ->
                    v.windowInsetsController?.let { controller ->
                        controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                        controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                    insets
                }
            }

            viewTreeObserver.addOnWindowFocusChangeListener { hasWindowFocus ->
                if (!hasWindowFocus) {
                    collapseStatusBar(context)
                    requestFocus()
                }
            }

            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val dummyOwner = DummyLifecycleOwner()
            setViewTreeLifecycleOwner(dummyOwner)
            setViewTreeViewModelStoreOwner(dummyOwner)
            setViewTreeSavedStateRegistryOwner(dummyOwner)

            setContent {
                MyApplicationTheme {
                    LockOverlayLayout(
                        message = message,
                        correctPassword = correctPassword,
                        onUnlocked = {
                            hideOverlay()
                            onCorrectPasswordEntered()
                        }
                    )
                }
            }

            // Intercept standard hardware back clicks
            isFocusable = true
            requestFocus()
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                    true // Consume event
                } else {
                    false
                }
            }
        }

        try {
            wm.addView(view, layoutParams)
            overlayView = view

            // Add an invisible but touch-consuming block at the top status-bar area to prevent pulling down the notification bar / quick settings
            try {
                val blockerParams = WindowManager.LayoutParams().apply {
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    val density = context.resources.displayMetrics.density
                    height = (25 * density).toInt() // Covering the thin top 25dp status bar area completely prevents pulling down system notifications, while letting our overlay capture drawer swipe gestures
                    gravity = android.view.Gravity.TOP
                    format = PixelFormat.TRANSLUCENT
                }
                val blocker = View(context).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setOnTouchListener { _, _ -> 
                        collapseStatusBar(context)
                        true 
                    } // Swallow/consume top gesture pull-down and trigger force collapse
                }
                wm.addView(blocker, blockerParams)
                statusBarBlockerView = blocker
            } catch (ex: Exception) {
                ex.printStackTrace()
            }

            isOverlayShowing = true

            // Start periodic background collapse task to auto-collapse system bar pull downs
            collapseJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                while (isOverlayShowing) {
                    collapseStatusBar(context)
                    delay(300)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideOverlay() {
        collapseJob?.cancel()
        collapseJob = null
        val wm = windowManager ?: return
        val view = overlayView
        val blocker = statusBarBlockerView
        try {
            if (view != null) {
                wm.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (blocker != null) {
                wm.removeView(blocker)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            statusBarBlockerView = null
            windowManager = null
            isOverlayShowing = false
        }
    }
}

@Composable
fun LockOverlayLayout(
    message: String,
    correctPassword: String,
    onUnlocked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var inputPassword by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var showError by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    // Countdown state for automatic unlock
    val blockedUntil by StateManager.blockedUntil.collectAsState()
    val blockedImage by StateManager.blockedImage.collectAsState()
    var remainingSeconds by remember { mutableStateOf(0L) }

    // Live CUSTOM STYLING nodes synced from Firebase
    val lockTheme by StateManager.lockTheme.collectAsState()
    val lockWallpaper by StateManager.lockWallpaper.collectAsState()
    val lockWarningIcon by StateManager.lockWarningIcon.collectAsState()

    // Retrieve Pairing Device ID to bind Direct Chat
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceId = remember {
        val prefs = context.getSharedPreferences("guardlink_firebase_prefs", Context.MODE_PRIVATE)
        prefs.getString("firebase_paired_device_id", null) ?: prefs.getString("my_permanent_device_uuid", null) ?: com.example.network.FirebaseManager.getOrCreateDeviceId(context)
    }

    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var chatInputText by remember { mutableStateOf("") }

    DisposableEffect(deviceId) {
        if (deviceId != null) {
            val chatRef = FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("devices").child(deviceId).child("chat")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<ChatMessage>()
                    for (child in snapshot.children) {
                        val id = child.child("id").getValue(String::class.java) ?: ""
                        val sender = child.child("sender").getValue(String::class.java) ?: ""
                        val msg = child.child("message").getValue(String::class.java) ?: ""
                        val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        list.add(ChatMessage(id, sender, msg, ts))
                    }
                    chatMessages = list.sortedBy { it.timestamp }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            chatRef.addValueEventListener(listener)
            onDispose {
                chatRef.removeEventListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    fun sendUserMessage(text: String) {
        if (text.isEmpty() || deviceId == null) return
        val chatRef = FirebaseDatabase.getInstance("https://guard-link-81956-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("devices").child(deviceId).child("chat").push()
        val chatData = mapOf(
            "id" to chatRef.key,
            "sender" to "user",
            "message" to text,
            "timestamp" to ServerValue.TIMESTAMP
        )
        chatRef.setValue(chatData)
    }

    LaunchedEffect(blockedUntil) {
        if (blockedUntil > 0L) {
            while (true) {
                val current = System.currentTimeMillis()
                val diff = (blockedUntil - current) / 1000
                if (diff <= 0) {
                    remainingSeconds = 0
                    onUnlocked()
                    break
                }
                remainingSeconds = diff
                delay(250)
            }
        } else {
            remainingSeconds = 0L
        }
    }

    // Pulsing and glow animations
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_pulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_glow"
    )

    // STYLING MAPS BASED ON ADMIN CHOICES
    val themeBgBrush = remember(lockTheme) {
        when (lockTheme) {
            "cyberpunk" -> Brush.verticalGradient(colors = listOf(Color(0x3324040A), Color(0x33070001)))
            "stealth" -> Brush.verticalGradient(colors = listOf(Color(0x33140330), Color(0x3304000B)))
            else -> Brush.verticalGradient(colors = listOf(Color(0x330E1624), Color(0x3304060A))) // slate style
        }
    }

    val themeAccentColor = remember(lockTheme) {
        when (lockTheme) {
            "cyberpunk" -> Color(0xFFFF0055)
            "stealth" -> Color(0xFF9E00FF)
            else -> AccentBlue // slate
        }
    }

    val themeBorderColor = themeAccentColor.copy(alpha = glowAlpha)

    val themeIconVector = remember(lockWarningIcon) {
        when (lockWarningIcon) {
            "biohazard" -> Icons.Default.Warning
            "warning" -> Icons.Default.Dangerous
            "hourglass" -> Icons.Default.HourglassEmpty
            else -> Icons.Default.Lock
        }
    }

    val themeIconTint = remember(lockTheme) {
        when (lockTheme) {
            "cyberpunk" -> Color(0xFFFF2E75)
            "stealth" -> Color(0xFFBD54FF)
            else -> Color.White
        }
    }

    // Shake offset for wrong attempts
    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        coroutineScope.launch {
            repeat(4) {
                shakeOffset.animateTo(20f, tween(50, easing = LinearOutSlowInEasing))
                shakeOffset.animateTo(-20f, tween(50, easing = LinearOutSlowInEasing))
            }
            shakeOffset.animateTo(0f, tween(50, easing = LinearOutSlowInEasing))
        }
    }

    val focusRequester = remember { FocusRequester() }

    var showCustomDrawer by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableStateOf(0f) }
    val drawerHeight = 520.dp

    val drawerOffset by animateDpAsState(
        targetValue = if (showCustomDrawer) 0.dp else (-520).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "drawer_slide"
    )

    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onDragEnd = {
                if (dragAccumulator > 80f) {
                    showCustomDrawer = true
                } else if (dragAccumulator < -80f) {
                    showCustomDrawer = false
                }
            },
            onVerticalDrag = { change, dragAmount ->
                dragAccumulator += dragAmount
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBgBrush),
        contentAlignment = Alignment.Center
    ) {
        // Main Lock screen content offset to leave space for status bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.Surface),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 420.dp)
                    .border(1.dp, themeBorderColor, RoundedCornerShape(28.dp))
                    .offset(x = shakeOffset.value.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Sleek padlock icon or chosen warning vectors
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .scale(scaleFactor)
                ) {
                    Icon(
                        imageVector = themeIconVector,
                        contentDescription = "Device Locked Indicator",
                        tint = themeIconTint,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (lockTheme == "cyberpunk") "CRITICAL LOCKOUT" else "Device Locked",
                    color = if (lockTheme == "cyberpunk") themeAccentColor else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "GUARDLINK REMOTE LINK",
                    color = themeAccentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Administrator Sent Image Box (if any)
                val imageBitmap = remember(blockedImage) {
                    if (!blockedImage.isNullOrEmpty()) {
                        try {
                            val decodedBytes = android.util.Base64.decode(blockedImage, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            bitmap?.asImageBitmap()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    } else null
                }

                if (imageBitmap != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .border(1.dp, Border, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceAlt)
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Admin uploaded image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Administrator Message Panel
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Border, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ADMINISTRATOR MESSAGE",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message.ifEmpty { "This device has been restricted." },
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Dynamic Counter Panel
                if (remainingSeconds > 0L) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    val timerString = String.format("%02d:%02d", mins, secs)

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Timer Icon",
                                    tint = AccentGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "AUTO-UNLOCK IN",
                                    color = AccentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Default,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = timerString,
                                color = AccentGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Enter Unlock Passcode",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Password Field
                OutlinedTextField(
                    value = inputPassword,
                    onValueChange = {
                        inputPassword = it
                        showError = false
                    },
                    placeholder = {
                        Text(
                            "••••••••",
                            color = Color.White.copy(alpha = 0.2f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceAlt,
                        unfocusedContainerColor = SurfaceAlt,
                        focusedBorderColor = themeAccentColor,
                        unfocusedBorderColor = Border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(14.dp)
                )

                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Incorrect passcode.",
                        color = AccentRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                if (attempts > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Failed attempts: $attempts",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Unlock Button
                Button(
                    onClick = {
                        if (inputPassword == correctPassword) {
                            onUnlocked()
                        } else {
                            attempts++
                            showError = true
                            inputPassword = ""
                            triggerShake()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "UNLOCK DEVICE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Contact your network administrator to unlock this device.",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

        // Simulated Status Bar (visible on lock overlay screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable { showCustomDrawer = !showCustomDrawer }
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .align(Alignment.TopCenter)
                .then(dragModifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated Clock
            val calendar = remember { java.util.Calendar.getInstance() }
            val timeStr = String.format("%02d:%02d", calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))
            
            Text(
                text = timeStr,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            // Subtle grab handle indicator line
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.5.dp))
            )

            // Dynamic Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Safe Network Active",
                    tint = AccentGreen,
                    modifier = Modifier.size(14.dp)
                )
                Icon(
                    imageVector = Icons.Default.BatteryFull,
                    contentDescription = "Battery Status",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Custom sliding permitted notification center drawer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(drawerHeight)
                .offset(y = drawerOffset)
                .background(
                    color = Color(0xF5000000), // Pure OLED pitch black frosted glass
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .border(
                    width = 1.dp,
                    color = Border,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .align(Alignment.TopCenter)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragEnd = {
                            if (dragAccumulator < -60f) {
                                showCustomDrawer = false
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            dragAccumulator += dragAmount
                        }
                    )
                }
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PERMITTED WIDGETS",
                                color = AccentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "Secure System Status",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Date
                        val dCalendar = remember { java.util.Calendar.getInstance() }
                        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        val dateStr = "${months[dCalendar.get(java.util.Calendar.MONTH)]} ${dCalendar.get(java.util.Calendar.DAY_OF_MONTH)}"
                        
                        Text(
                            text = dateStr,
                            color = AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .background(AccentGreen.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Divider line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Border)
                    )

                    // 3 Interactive Clean Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // WiFi Indicator
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Border, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("WiFi Secure", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("Active link", color = TextSecondary, fontSize = 9.sp)
                            }
                        }

                        // Battery Indicator
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Border, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.BatteryFull, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Battery", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("87% OK", color = TextSecondary, fontSize = 9.sp)
                            }
                        }

                        // DND Status
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Border, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("DND Mode", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("Enforced", color = TextSecondary, fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "SAFE ALERTS & NOTIFICATIONS FEED",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    // Secure Alerts List
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(AccentGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("GuardLink Service Active", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("Local device monitoring framework running securely.", color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(AccentBlue, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Secure System State", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("Simulated status bar swipe recognized successfully.", color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Simulated Brightness Control
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceAlt, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.LightMode, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Backlight Level",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                        .width(100.dp)
                                        .height(6.dp)
                                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .fillMaxHeight()
                                    .background(AccentAmber, CircleShape)
                            )
                        }
                    }
                }

                // Handle at bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomDrawer = false }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Close Panel",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "CLOSE SECURE STATUS WIDGETS",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // --- MESSENGER STYLE FLOATING CHAT BUBBLE ---
        var bubbleOffsetX by remember { mutableStateOf(-30f) }
        var bubbleOffsetY by remember { mutableStateOf(-350f) }
        var isFloatingChatOpen by remember { mutableStateOf(false) }

        // Unread message indicator badge
        var lastReadMessageId by remember { mutableStateOf("") }
        val unreadMessagesCount = remember(chatMessages, lastReadMessageId, isFloatingChatOpen) {
            if (isFloatingChatOpen) {
                0
            } else {
                val lastIdIndex = chatMessages.indexOfLast { it.id == lastReadMessageId }
                if (lastIdIndex == -1) chatMessages.size else chatMessages.size - 1 - lastIdIndex
            }
        }

        // Auto-update last read message ID when chat is open
        LaunchedEffect(isFloatingChatOpen, chatMessages) {
            if (isFloatingChatOpen && chatMessages.isNotEmpty()) {
                lastReadMessageId = chatMessages.last().id
            }
        }

        // The Floating Bubble (visible when chat box is collapsed or even when open)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        bubbleOffsetX = (bubbleOffsetX + dragAmount.x).coerceIn(-1000f, 0f)
                        bubbleOffsetY = (bubbleOffsetY + dragAmount.y).coerceIn(-1500f, 0f)
                    }
                }
                .size(64.dp)
                .clickable {
                    isFloatingChatOpen = !isFloatingChatOpen
                },
            contentAlignment = Alignment.Center
        ) {
            // Pulse & Glow behind the bubble
            val bubbleTransition = rememberInfiniteTransition(label = "bubble_pulse")
            val bubbleScale by bubbleTransition.animateFloat(
                initialValue = 0.98f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bubble_scale"
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(bubbleScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(themeAccentColor.copy(alpha = 0.4f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )

            // Primary Bubble Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(themeAccentColor, themeAccentColor.copy(alpha = 0.8f))
                        ),
                        shape = CircleShape
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "Floating Chat Bubble",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Unread Badge
            if (unreadMessagesCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .background(AccentRed, CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unreadMessagesCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Floating popup chatbox (like a Messenger chat pop-up)
        if (isFloatingChatOpen) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFA08080A) // pure black obsidian glass surface
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 120.dp, end = 16.dp)
                    .width(320.dp)
                    .heightIn(max = 400.dp)
                    .border(
                        width = 1.5.dp,
                        color = themeAccentColor.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Chatbox Header
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
                                    .size(8.dp)
                                    .background(AccentGreen, CircleShape)
                            )
                            Column {
                                Text(
                                    text = "ADMIN LIVE CHAT",
                                    color = themeAccentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Messenger Mode active",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { isFloatingChatOpen = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close chatbox",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Message feed (takes remaining vertical space)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (chatMessages.isEmpty()) {
                            Text(
                                text = "No messages yet.\nType below to request an unlock.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            val lazyListState = rememberLazyListState()
                            
                            // Auto scroll to bottom
                            LaunchedEffect(chatMessages.size) {
                                if (chatMessages.isNotEmpty()) {
                                    lazyListState.animateScrollToItem(chatMessages.size - 1)
                                }
                            }

                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(chatMessages) { msg ->
                                    val isMe = msg.sender == "user"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (isMe) themeAccentColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(
                                                        topStart = 14.dp,
                                                        topEnd = 14.dp,
                                                        bottomStart = if (isMe) 14.dp else 2.dp,
                                                        bottomEnd = if (isMe) 2.dp else 14.dp
                                                    )
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isMe) themeAccentColor else Border.copy(alpha = 0.4f),
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
                                                    text = if (isMe) "You" else "Admin",
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input field
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            placeholder = { Text("Ask admin...", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                focusedBorderColor = themeAccentColor,
                                unfocusedBorderColor = Border.copy(alpha = 0.4f)
                            ),
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        )

                        IconButton(
                            onClick = {
                                if (chatInputText.trim().isNotEmpty()) {
                                    sendUserMessage(chatInputText.trim())
                                    chatInputText = ""
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(themeAccentColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send message",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
