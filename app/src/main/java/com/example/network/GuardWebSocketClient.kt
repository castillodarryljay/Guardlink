package com.example.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GuardWebSocketClient(
    val ip: String,
    private val onMessageReceived: (JSONObject) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        onStatusChanged("connecting")
        val request = Request.Builder()
            .url("ws://$ip:9999")
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("GuardClient", "Connected to $ip")
                onStatusChanged("active")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("GuardClient", "Received text from $ip: $text")
                try {
                    val json = JSONObject(text)
                    onMessageReceived(json)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("GuardClient", "Closed connection to $ip")
                onStatusChanged("offline")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GuardClient", "Connection failure for $ip", t)
                onStatusChanged("offline")
            }
        })
    }

    fun sendBlock(message: String, password: String, timerSeconds: Long = -1L, imageBase64: String = "") {
        try {
            val blockMsg = JSONObject().apply {
                put("type", "BLOCK")
                put("message", message)
                put("password", password)
                put("timerSeconds", timerSeconds)
                put("blockImage", imageBase64)
                put("issuedAt", System.currentTimeMillis())
            }
            webSocket?.send(blockMsg.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendUnblock() {
        try {
            val unblockMsg = JSONObject().apply {
                put("type", "UNBLOCK")
            }
            webSocket?.send(unblockMsg.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendHandshake(adminName: String) {
        try {
            val hsMsg = JSONObject().apply {
                put("type", "HANDSHAKE_ADMIN")
                put("adminName", adminName)
            }
            webSocket?.send(hsMsg.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncSchedules(schedules: List<com.example.data.LockSchedule>) {
        try {
            val arr = org.json.JSONArray()
            for (s in schedules) {
                arr.put(s.toJsonObject())
            }
            val syncMsg = JSONObject().apply {
                put("type", "SYNC_SCHEDULES")
                put("schedules", arr)
            }
            webSocket?.send(syncMsg.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendStartCameraStream() {
        try {
            val msg = JSONObject().apply {
                put("type", "START_CAMERA_STREAM")
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendStopCameraStream() {
        try {
            val msg = JSONObject().apply {
                put("type", "STOP_CAMERA_STREAM")
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Goodbye")
            webSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
