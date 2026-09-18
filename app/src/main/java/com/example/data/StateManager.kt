package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LockSchedule(
    val id: String,
    val deviceName: String, // Target device name, or "*" for All Devices
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: List<Int>, // 1 (Sunday) to 7 (Saturday)
    val message: String,
    val passcode: String,
    val enabled: Boolean = true
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("deviceName", deviceName)
            put("startHour", startHour)
            put("startMinute", startMinute)
            put("endHour", endHour)
            put("endMinute", endMinute)
            put("daysOfWeek", JSONArray(daysOfWeek))
            put("message", message)
            put("passcode", passcode)
            put("enabled", enabled)
        }
    }

    // Helper to format days beautifully (e.g. "Mon, Tue, Wed" or "Weekdays", "Weekends", "Daily")
    fun formatDays(): String {
        if (daysOfWeek.size == 7) return "Daily"
        val weekDays = listOf(2, 3, 4, 5, 6)
        val weekends = listOf(7, 1)
        if (daysOfWeek.sorted() == weekDays) return "Weekdays"
        if (daysOfWeek.sorted() == weekends) return "Weekends"
        
        val dayNames = mapOf(
            1 to "Sun",
            2 to "Mon",
            3 to "Tue",
            4 to "Wed",
            5 to "Thu",
            6 to "Fri",
            7 to "Sat"
        )
        return daysOfWeek.sorted().map { dayNames[it] ?: "" }.joinToString(", ")
    }

    fun isTimeInSchedule(dayOfWeek: Int, hour: Int, minute: Int): Boolean {
        if (!enabled) return false

        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        val currentMinutes = hour * 60 + minute

        if (startMinutes <= endMinutes) {
            // Same day schedule
            val isTodayScheduled = daysOfWeek.contains(dayOfWeek)
            if (isTodayScheduled && currentMinutes in startMinutes..endMinutes) {
                return true
            }
        } else {
            // Overnight window (e.g. 21:00 to 07:00 next day)
            val isTodayStartDay = daysOfWeek.contains(dayOfWeek)
            
            val yesterdayDayOfWeek = if (dayOfWeek == 1) 7 else dayOfWeek - 1
            val isYesterdayStartDay = daysOfWeek.contains(yesterdayDayOfWeek)

            if (isTodayStartDay && currentMinutes >= startMinutes) {
                return true
            }
            if (isYesterdayStartDay && currentMinutes <= endMinutes) {
                return true
            }
        }
        return false
    }

    companion object {
        fun fromJsonObject(json: JSONObject): LockSchedule {
            val days = mutableListOf<Int>()
            val daysJson = json.optJSONArray("daysOfWeek")
            if (daysJson != null) {
                for (i in 0 until daysJson.length()) {
                    days.add(daysJson.optInt(i))
                }
            }
            return LockSchedule(
                id = json.optString("id"),
                deviceName = json.optString("deviceName"),
                startHour = json.optInt("startHour"),
                startMinute = json.optInt("startMinute"),
                endHour = json.optInt("endHour"),
                endMinute = json.optInt("endMinute"),
                daysOfWeek = days,
                message = json.optString("message"),
                passcode = json.optString("passcode"),
                enabled = json.optBoolean("enabled", true)
            )
        }
    }
}

object StateManager {
    private const val PREFS_NAME = "guardlink_prefs"
    private const val KEY_ROLE = "role"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_ADMIN_NAME = "admin_name"
    private const val KEY_DEFAULT_MESSAGE = "default_message"
    private const val KEY_DEFAULT_PASSWORD = "default_password"
    private const val KEY_IS_BLOCKED = "is_blocked"
    private const val KEY_BLOCKED_MESSAGE = "blocked_message"
    private const val KEY_BLOCKED_PASSWORD = "blocked_password"
    private const val KEY_BLOCKED_UNTIL = "blocked_until"
    private const val KEY_BLOCKED_IMAGE = "blocked_image"
    private const val KEY_SCHEDULES = "lock_schedules"
    private const val KEY_BLOCKED_BY_SCHEDULE = "blocked_by_schedule"
    private const val KEY_MANUAL_IPS = "manual_ips"
    private const val KEY_PAIRED_DEVICES = "paired_device_ids"

    private lateinit var prefs: SharedPreferences

    val role = MutableStateFlow<String?>(null)
    val deviceName = MutableStateFlow("")
    val adminName = MutableStateFlow("")
    val defaultMessage = MutableStateFlow("This device has been restricted.")
    val defaultPassword = MutableStateFlow("1234")
    val isBlocked = MutableStateFlow(false)
    val blockedMessage = MutableStateFlow("")
    val blockedPassword = MutableStateFlow("")
    val blockedUntil = MutableStateFlow<Long>(0L)
    val blockedImage = MutableStateFlow<String?>("")
    val schedules = MutableStateFlow<List<LockSchedule>>(emptyList())
    val blockedBySchedule = MutableStateFlow(false)
    val manualIps = MutableStateFlow<Set<String>>(emptySet())
    val pairedDeviceIds = MutableStateFlow<Set<String>>(emptySet())

    val lockTheme = MutableStateFlow("slate")
    val lockWallpaper = MutableStateFlow("")
    val lockWarningIcon = MutableStateFlow("lock")

    val lastLatitude = MutableStateFlow(14.555060)
    val lastLongitude = MutableStateFlow(121.011993)

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        role.value = prefs.getString(KEY_ROLE, null)
        deviceName.value = prefs.getString(KEY_DEVICE_NAME, "") ?: ""
        adminName.value = prefs.getString(KEY_ADMIN_NAME, "") ?: ""
        defaultMessage.value = prefs.getString(KEY_DEFAULT_MESSAGE, "This device has been restricted.") ?: "This device has been restricted."
        defaultPassword.value = prefs.getString(KEY_DEFAULT_PASSWORD, "1234") ?: "1234"
        blockedBySchedule.value = prefs.getBoolean(KEY_BLOCKED_BY_SCHEDULE, false)
        manualIps.value = prefs.getStringSet(KEY_MANUAL_IPS, emptySet())?.toSet() ?: emptySet()
        pairedDeviceIds.value = prefs.getStringSet(KEY_PAIRED_DEVICES, emptySet())?.toSet() ?: emptySet()
        
        lockTheme.value = prefs.getString("lock_theme", "slate") ?: "slate"
        lockWallpaper.value = prefs.getString("lock_wallpaper", "") ?: ""
        lockWarningIcon.value = prefs.getString("lock_warning_icon", "lock") ?: "lock"

        val savedLatStr = prefs.getString("last_latitude", "14.555060") ?: "14.555060"
        val savedLngStr = prefs.getString("last_longitude", "121.011993") ?: "121.011993"
        lastLatitude.value = savedLatStr.toDoubleOrNull() ?: 14.555060
        lastLongitude.value = savedLngStr.toDoubleOrNull() ?: 121.011993
        
        // Load initial schedules
        schedules.value = getSchedulesFromPrefs()

        val storedBlocked = prefs.getBoolean(KEY_IS_BLOCKED, false)
        val storedUntil = prefs.getLong(KEY_BLOCKED_UNTIL, 0L)
        if (storedBlocked && storedUntil > 0L && System.currentTimeMillis() >= storedUntil) {
            isBlocked.value = false
            blockedMessage.value = ""
            blockedPassword.value = ""
            blockedUntil.value = 0L
            blockedImage.value = ""
            blockedBySchedule.value = false
            prefs.edit()
                .putBoolean(KEY_IS_BLOCKED, false)
                .putString(KEY_BLOCKED_MESSAGE, "")
                .putString(KEY_BLOCKED_PASSWORD, "")
                .putLong(KEY_BLOCKED_UNTIL, 0L)
                .putString(KEY_BLOCKED_IMAGE, "")
                .putBoolean(KEY_BLOCKED_BY_SCHEDULE, false)
                .apply()
        } else {
            isBlocked.value = storedBlocked
            blockedMessage.value = prefs.getString(KEY_BLOCKED_MESSAGE, "") ?: ""
            blockedPassword.value = prefs.getString(KEY_BLOCKED_PASSWORD, "") ?: ""
            blockedUntil.value = storedUntil
            blockedImage.value = prefs.getString(KEY_BLOCKED_IMAGE, "") ?: ""
        }
    }

    fun setRole(r: String?) {
        role.value = r
        prefs.edit().putString(KEY_ROLE, r).apply()
    }

    fun setDeviceName(name: String) {
        deviceName.value = name
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun setAdminName(name: String) {
        adminName.value = name
        prefs.edit().putString(KEY_ADMIN_NAME, name).apply()
    }

    fun setDefaultMessage(message: String) {
        defaultMessage.value = message
        prefs.edit().putString(KEY_DEFAULT_MESSAGE, message).apply()
    }

    fun setDefaultPassword(password: String) {
        defaultPassword.value = password
        prefs.edit().putString(KEY_DEFAULT_PASSWORD, password).apply()
    }

    fun setBlockedBySchedule(bySchedule: Boolean) {
        blockedBySchedule.value = bySchedule
        prefs.edit().putBoolean(KEY_BLOCKED_BY_SCHEDULE, bySchedule).apply()
    }

    fun setBlocked(blocked: Boolean, message: String = "", password: String = "", until: Long = 0L, imageBase64: String = "") {
        isBlocked.value = blocked
        if (blocked) {
            blockedMessage.value = message
            blockedPassword.value = password
            blockedUntil.value = until
            blockedImage.value = imageBase64
            prefs.edit()
                .putBoolean(KEY_IS_BLOCKED, true)
                .putString(KEY_BLOCKED_MESSAGE, message)
                .putString(KEY_BLOCKED_PASSWORD, password)
                .putLong(KEY_BLOCKED_UNTIL, until)
                .putString(KEY_BLOCKED_IMAGE, imageBase64)
                .apply()
        } else {
            blockedUntil.value = 0L
            blockedImage.value = ""
            blockedBySchedule.value = false
            prefs.edit()
                .putBoolean(KEY_IS_BLOCKED, false)
                .putString(KEY_BLOCKED_MESSAGE, "")
                .putString(KEY_BLOCKED_PASSWORD, "")
                .putLong(KEY_BLOCKED_UNTIL, 0L)
                .putString(KEY_BLOCKED_IMAGE, "")
                .putBoolean(KEY_BLOCKED_BY_SCHEDULE, false)
                .apply()
        }
    }

    fun getSchedulesFromPrefs(): List<LockSchedule> {
        val list = mutableListOf<LockSchedule>()
        if (!::prefs.isInitialized) return schedules.value
        val jsonStr = prefs.getString(KEY_SCHEDULES, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    list.add(LockSchedule.fromJsonObject(arr.getJSONObject(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    fun saveSchedules(list: List<LockSchedule>) {
        schedules.value = list
        if (!::prefs.isInitialized) return
        try {
            val arr = JSONArray()
            for (s in list) {
                arr.put(s.toJsonObject())
            }
            prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addManualIp(ip: String) {
        val current = manualIps.value.toMutableSet()
        current.add(ip)
        manualIps.value = current
        prefs.edit().putStringSet(KEY_MANUAL_IPS, current).apply()
    }

    fun removeManualIp(ip: String) {
        val current = manualIps.value.toMutableSet()
        current.remove(ip)
        manualIps.value = current
        prefs.edit().putStringSet(KEY_MANUAL_IPS, current).apply()
    }

    fun addPairedDeviceId(deviceId: String) {
        if (deviceId.isBlank()) return
        val current = pairedDeviceIds.value.toMutableSet()
        current.add(deviceId)
        pairedDeviceIds.value = current
        prefs.edit().putStringSet(KEY_PAIRED_DEVICES, current).apply()
    }

    fun removePairedDeviceId(deviceId: String) {
        if (deviceId.isBlank()) return
        val current = pairedDeviceIds.value.toMutableSet()
        current.remove(deviceId)
        pairedDeviceIds.value = current
        prefs.edit().putStringSet(KEY_PAIRED_DEVICES, current).apply()
    }

    fun isDevicePaired(deviceId: String): Boolean {
        return pairedDeviceIds.value.contains(deviceId)
    }

    fun clearPairedDevices() {
        pairedDeviceIds.value = emptySet()
        prefs.edit().remove(KEY_PAIRED_DEVICES).apply()
    }

    fun setLockStyle(theme: String, wallpaper: String, warningIcon: String) {
        lockTheme.value = theme
        lockWallpaper.value = wallpaper
        lockWarningIcon.value = warningIcon
        prefs.edit()
            .putString("lock_theme", theme)
            .putString("lock_wallpaper", wallpaper)
            .putString("lock_warning_icon", warningIcon)
            .apply()
    }

    fun saveLastKnownLocation(lat: Double, lng: Double) {
        lastLatitude.value = lat
        lastLongitude.value = lng
        prefs.edit()
            .putString("last_latitude", lat.toString())
            .putString("last_longitude", lng.toString())
            .apply()
    }

    fun clearAll() {
        setRole(null)
        setDeviceName("")
        setAdminName("")
        setBlocked(false)
        setBlockedBySchedule(false)
        prefs.edit().clear().apply()
    }
}
