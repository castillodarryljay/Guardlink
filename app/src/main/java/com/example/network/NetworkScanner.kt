package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object NetworkScanner {
    val scanProgress = MutableStateFlow(0f)
    val scanProgressText = MutableStateFlow("")
    val discoveredDevices = MutableStateFlow<List<String>>(emptyList())
    val isScanning = MutableStateFlow(false)

    fun getLocalIpAddress(context: Context): String {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wm?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    (ip shr 8) and 0xff,
                    (ip shr 16) and 0xff,
                    (ip shr 24) and 0xff
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty()) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    suspend fun startScan(context: Context) {
        if (isScanning.value) return
        isScanning.value = true
        scanProgress.value = 0f
        val localIp = getLocalIpAddress(context)
        discoveredDevices.value = emptyList()

        if (localIp == "127.0.0.1" || !localIp.contains(".")) {
            scanProgressText.value = "Not connected to WiFi"
            isScanning.value = false
            return
        }

        val parts = localIp.split(".")
        if (parts.size != 4) {
            scanProgressText.value = "Invalid IP format"
            isScanning.value = false
            return
        }

        val subnet = "${parts[0]}.${parts[1]}.${parts[2]}"
        val total = 254
        val scannedCount = AtomicInteger(0)
        val foundIps = java.util.Collections.synchronizedList(mutableListOf<String>())

        withContext(Dispatchers.IO) {
            val executor = Executors.newFixedThreadPool(50)
            for (i in 1..254) {
                val ip = "$subnet.$i"
                if (ip == localIp) {
                    val count = scannedCount.incrementAndGet()
                    scanProgress.value = count / total.toFloat()
                    continue
                }
                executor.submit {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(ip, 9999), 300)
                        socket.close()
                        foundIps.add(ip)
                    } catch (e: Exception) {
                        // Unreachable or closed
                    } finally {
                        val count = scannedCount.incrementAndGet()
                        scanProgress.value = count / total.toFloat()
                        scanProgressText.value = "Scanning $subnet.x... ($count/$total)"
                    }
                }
            }
            executor.shutdown()
            while (!executor.isTerminated) {
                try {
                    Thread.sleep(50)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        discoveredDevices.value = foundIps.toList()
        scanProgressText.value = "Found ${foundIps.size} devices."
        isScanning.value = false
    }
}
