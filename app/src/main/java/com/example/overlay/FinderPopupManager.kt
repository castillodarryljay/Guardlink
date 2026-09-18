package com.example.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.Surface
import com.example.ui.theme.TextSecondary

object FinderPopupManager {
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    var isPopupShowing = false
        private set

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

    fun showFinderPopup(context: Context) {
        if (isPopupShowing) return

        // Fallback to only heads-up notification if overlay permission is missing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w("FinderPopup", "Cannot show overlay popup: permission Settings.canDrawOverlays is false")
            return
        }

        try {
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
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    blurBehindRadius = 50
                }

                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                format = PixelFormat.TRANSLUCENT
            }

            val view = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                val dummyOwner = DummyLifecycleOwner()
                setViewTreeLifecycleOwner(dummyOwner)
                setViewTreeViewModelStoreOwner(dummyOwner)
                setViewTreeSavedStateRegistryOwner(dummyOwner)

                setContent {
                    MyApplicationTheme {
                        FinderPopupLayout(
                            onStopClicked = {
                                try {
                                    com.example.network.FirebaseManager.userStopAlarmSelf(context)
                                    com.example.service.GuardLinkService.triggerAlarmOff()
                                } catch (e: Exception) {
                                    Log.e("FinderPopup", "Error stopping alarm on click", e)
                                }
                            }
                        )
                    }
                }
            }

            wm.addView(view, layoutParams)
            overlayView = view
            isPopupShowing = true
            Log.d("FinderPopup", "Finder Popup window overlay added successfully")
        } catch (e: Exception) {
            Log.e("FinderPopup", "Error showing finder popup overlay", e)
        }
    }

    fun hideFinderPopup() {
        val wm = windowManager ?: return
        val view = overlayView
        try {
            if (view != null) {
                wm.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            windowManager = null
            isPopupShowing = false
            Log.d("FinderPopup", "Finder Popup window overlay hidden successfully")
        }
    }
}

@Composable
fun FinderPopupLayout(onStopClicked: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 380.dp)
                .border(2.dp, AccentBlue.copy(alpha = 0.8f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(AccentBlue.copy(alpha = 0.15f), CircleShape)
                        .border(1.2.dp, AccentBlue.copy(alpha = 0.35f), CircleShape)
                        .scale(scaleFactor)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Finder Active",
                        tint = AccentBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Finder Sound Active",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "A diagnostic locator sound is playing on this device to help you search. The sound automatically shuts down in 10 seconds.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onStopClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "STOP SOUND",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
