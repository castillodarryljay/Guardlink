package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.StateManager
import com.example.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress

class GuardWebSocketServer(private val context: Context, port: Int = 9999) : WebSocketServer(InetSocketAddress(port)) {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    val activeAdminIp = MutableStateFlow<String?>(null)
    val activeAdminName = MutableStateFlow<String?>(null)

    // Helper to keep reference of the socket connection
    private val activeConnections = java.util.Collections.synchronizedSet(mutableSetOf<WebSocket>())

    override fun onStart() {
        Log.d("GuardWS", "WebSocket Server started on port $port")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val ipAddress = conn.remoteSocketAddress?.address?.hostAddress ?: ""
        Log.d("GuardWS", "Connection opened from IP: $ipAddress")
        activeConnections.add(conn)
        
        // Immediately send HANDSHAKE to the connecting Admin
        try {
            val handshakeMsg = JSONObject().apply {
                put("type", "HANDSHAKE")
                put("deviceName", StateManager.deviceName.value)
                put("role", "user")
            }
            conn.send(handshakeMsg.toString())

            // Send current blocked status
            val statusMsg = JSONObject().apply {
                put("type", "STATUS")
                put("blocked", StateManager.isBlocked.value)
            }
            conn.send(statusMsg.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        scope.launch {
            activeAdminIp.value = ipAddress
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        val ipAddress = conn.remoteSocketAddress?.address?.hostAddress ?: ""
        Log.d("GuardWS", "Connection closed for IP: $ipAddress")
        activeConnections.remove(conn)
        com.example.camera.BackgroundCameraManager.stopCameraStream()
        scope.launch {
            if (activeAdminIp.value == ipAddress) {
                activeAdminIp.value = null
                activeAdminName.value = null
            }
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d("GuardWS", "Received message: $message")
        try {
            val json = JSONObject(message)
            val type = json.optString("type")
            val ipAddress = conn.remoteSocketAddress?.address?.hostAddress ?: ""
            
            when (type) {
                "BLOCK" -> {
                    val blockMessage = json.optString("message", "This device has been restricted.")
                    val blockPassword = json.optString("password", "1234")
                    val timerSeconds = json.optLong("timerSeconds", -1L)
                    val blockImage = json.optString("blockImage", "")
                    
                    val untilTimestamp = if (timerSeconds > 0L) {
                        System.currentTimeMillis() + (timerSeconds * 1000L)
                    } else {
                        0L
                    }
                    
                    scope.launch {
                        StateManager.setBlocked(true, blockMessage, blockPassword, untilTimestamp, blockImage)
                        
                        // Show overlay
                        OverlayManager.showOverlay(context, blockMessage, blockPassword) {
                            // Correct password entered -> overlay dismissed. Send UNBLOCK_ACK to Admin!
                            scope.launch {
                                StateManager.setBlocked(false)
                                try {
                                    val ack = JSONObject().apply {
                                        put("type", "UNBLOCK_ACK")
                                    }
                                    conn.send(ack.toString())
                                    
                                    // Send updated status to admin
                                    val statusMsg = JSONObject().apply {
                                        put("type", "STATUS")
                                        put("blocked", false)
                                    }
                                    conn.send(statusMsg.toString())
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        
                        // Send active blocked status update back to admin
                        try {
                            val statusMsg = JSONObject().apply {
                                put("type", "STATUS")
                                put("blocked", true)
                            }
                            conn.send(statusMsg.toString())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                "UNBLOCK" -> {
                    scope.launch {
                        StateManager.setBlocked(false)
                        OverlayManager.hideOverlay()
                        
                        // Send updated status
                        try {
                            val statusMsg = JSONObject().apply {
                                put("type", "STATUS")
                                put("blocked", false)
                            }
                            conn.send(statusMsg.toString())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                "PING" -> {
                    try {
                        val pong = JSONObject().apply {
                            put("type", "PONG")
                        }
                        conn.send(pong.toString())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                "HANDSHAKE_ADMIN" -> {
                    val adminNameValue = json.optString("adminName", "Admin")
                    scope.launch {
                        activeAdminName.value = adminNameValue
                        StateManager.setAdminName(adminNameValue)
                    }
                }
                "SYNC_SCHEDULES" -> {
                    val arr = json.optJSONArray("schedules")
                    if (arr != null) {
                        val list = mutableListOf<com.example.data.LockSchedule>()
                        for (i in 0 until arr.length()) {
                            list.add(com.example.data.LockSchedule.fromJsonObject(arr.getJSONObject(i)))
                        }
                        scope.launch {
                            StateManager.saveSchedules(list)
                        }
                    }
                }
                "START_CAMERA_STREAM" -> {
                    scope.launch {
                        com.example.camera.BackgroundCameraManager.startCameraStream(context, conn)
                    }
                }
                "STOP_CAMERA_STREAM" -> {
                    scope.launch {
                        com.example.camera.BackgroundCameraManager.stopCameraStream()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("GuardWS", "Error occurred", ex)
        if (conn != null) {
            activeConnections.remove(conn)
        }
    }

    fun broadcastMessage(message: String) {
        synchronized(activeConnections) {
            for (conn in activeConnections) {
                if (conn.isOpen) {
                    try {
                        conn.send(message)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
