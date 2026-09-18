package com.example.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import com.example.network.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/**
 * ScreenCaptureManager
 *
 * Implements Android's official MediaProjection screen capture with explicit user authorization.
 * Optimized for low-latency live streaming:
 * - Direct in-memory frame encoding (no persistent storage / files on disk or server)
 * - Immediate buffer release and Bitmap recycling (low RAM footprint)
 * - Scaled 540p resolution and optimized JPEG compression for smooth bandwidth efficiency
 */
object ScreenCaptureManager {
    private const val TAG = "ScreenCaptureManager"

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var permissionResultCode: Int = 0
    private var permissionResultData: Intent? = null

    private val _isStreamingScreen = MutableStateFlow(false)
    val isStreamingScreen = _isStreamingScreen.asStateFlow()

    private val _isScreenCaptureAuthorized = MutableStateFlow(false)
    val isScreenCaptureAuthorized = _isScreenCaptureAuthorized.asStateFlow()

    private var lastFrameTime = 0L
    private var lastCachedFrameBase64: String? = null
    private var heartbeatRunnable: Runnable? = null

    fun isAuthorized(): Boolean = (permissionResultData != null && permissionResultCode == Activity.RESULT_OK) || mediaProjection != null

    private fun startHeartbeat(context: Context) {
        stopHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                try {
                    if (_isScreenCaptureAuthorized.value && _isStreamingScreen.value) {
                        val cached = lastCachedFrameBase64
                        if (!cached.isNullOrEmpty()) {
                            FirebaseManager.uploadUserScreenFrame(context, cached)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat frame upload error", e)
                }
                backgroundHandler?.postDelayed(this, 1500L)
            }
        }
        backgroundHandler?.postDelayed(heartbeatRunnable!!, 1500L)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { backgroundHandler?.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    fun setScreenCaptureIntentData(resultCode: Int, data: Intent, context: Context? = null) {
        permissionResultCode = resultCode
        permissionResultData = data
        _isScreenCaptureAuthorized.value = (resultCode == Activity.RESULT_OK)
        Log.i(TAG, "Screen capture authorization stored successfully.")

        if (resultCode == Activity.RESULT_OK) {
            _isStreamingScreen.value = true
            com.example.service.GuardLinkService.promoteToMediaProjection()
            context?.let { ctx ->
                prepareCapturePipeline(ctx)
                startHeartbeat(ctx)
            }
        }
    }

    @Synchronized
    fun prepareCapturePipeline(context: Context) {
        val data = permissionResultData ?: return
        if (permissionResultCode != Activity.RESULT_OK) return

        try {
            if (mediaProjectionManager == null) {
                mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            }

            if (handlerThread == null || !handlerThread!!.isAlive) {
                handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
                backgroundHandler = Handler(handlerThread!!.looper)
            }

            // Always ensure foreground service holds MEDIA_PROJECTION type
            com.example.service.GuardLinkService.promoteToMediaProjection()

            if (mediaProjection == null) {
                mediaProjection = mediaProjectionManager?.getMediaProjection(permissionResultCode, data)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i(TAG, "MediaProjection system callback onStop.")
                        _isScreenCaptureAuthorized.value = false
                        _isStreamingScreen.value = false
                        stopHeartbeat()
                        mediaProjection = null
                        virtualDisplay = null
                        imageReader = null
                    }
                }, backgroundHandler)
                Log.i(TAG, "MediaProjection instance created and retained.")
            }

            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val densityDpi = displayMetrics.densityDpi

            val targetWidth = 540
            val targetHeight = ((targetWidth.toFloat() / screenWidth.toFloat()) * screenHeight).toInt().coerceAtLeast(720)

            if (imageReader == null) {
                imageReader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
                imageReader?.setOnImageAvailableListener({ reader ->
                    val image = try {
                        reader.acquireLatestImage()
                    } catch (e: Exception) {
                        null
                    } ?: return@setOnImageAvailableListener

                    try {
                        val now = System.currentTimeMillis()
                        // When streaming is active, throttle to ~10 FPS for optimal performance and battery
                        if (_isStreamingScreen.value && (now - lastFrameTime >= 100L)) {
                            lastFrameTime = now
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * targetWidth

                            val bitmap = Bitmap.createBitmap(
                                targetWidth + rowPadding / pixelStride,
                                targetHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)

                            val finalBitmap = if (rowPadding == 0) {
                                bitmap
                            } else {
                                Bitmap.createBitmap(bitmap, 0, 0, targetWidth, targetHeight)
                            }

                            val outputStream = ByteArrayOutputStream()
                            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
                            val base64Bytes = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                            lastCachedFrameBase64 = base64Bytes

                            FirebaseManager.uploadUserScreenFrame(context, base64Bytes)

                            if (finalBitmap != bitmap) {
                                finalBitmap.recycle()
                            }
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error encoding live screen frame", e)
                    } finally {
                        image.close()
                    }
                }, backgroundHandler)
            }

            if (virtualDisplay == null && mediaProjection != null && imageReader != null) {
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "GuardLinkScreenMirror",
                    targetWidth,
                    targetHeight,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface,
                    null,
                    backgroundHandler
                )
                Log.i(TAG, "VirtualDisplay created and armed for continuous background access.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare capture pipeline", e)
        }
    }

    fun startScreenCapture(context: Context) {
        if (!isAuthorized()) {
            Log.w(TAG, "Cannot start screen capture: Permission not yet authorized by user.")
            FirebaseManager.uploadUserScreenFrame(context, "STATUS:PERMISSION_REQUIRED")
            return
        }

        try {
            prepareCapturePipeline(context)
            _isStreamingScreen.value = true
            _isScreenCaptureAuthorized.value = true
            startHeartbeat(context)

            // Send immediately cached frame if available so viewer gets instant feedback
            lastCachedFrameBase64?.let { cached ->
                FirebaseManager.uploadUserScreenFrame(context, cached)
            }
            Log.i(TAG, "Live screen capture session active and streaming frames.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start live screen capture", e)
            FirebaseManager.uploadUserScreenFrame(context, "STATUS:FAILED_INIT")
        }
    }

    fun stopScreenCapture(context: Context? = null) {
        Log.i(TAG, "Screen capture viewer closed - keeping continuous stream active in background.")
        // Keep streaming and media projection active so that closing and reopening the modal works immediately
    }

    fun fullTeardown() {
        _isStreamingScreen.value = false
        stopHeartbeat()
        com.example.service.GuardLinkService.demoteFromMediaProjection()
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) { }
        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) { }
        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) { }
        try {
            handlerThread?.quitSafely()
            handlerThread = null
            backgroundHandler = null
        } catch (e: Exception) { }
    }
}
