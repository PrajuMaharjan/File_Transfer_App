package com.prajumaharjan.filetransfer

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.Inet4Address
import java.net.NetworkInterface

data class HotspotInfo(val ssid: String, val password: String, val ip: String?)

object HotspotManager {
    var info by mutableStateOf<HotspotInfo?>(null)
    var error by mutableStateOf<String?>(null)
    var starting by mutableStateOf(false)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    @SuppressLint("MissingPermission", "NewApi")
    fun start(context: Context) {
        if (Build.VERSION.SDK_INT < 26) { error = "Needs Android 8 or newer"; return }
        if (starting || info != null) return
        starting = true
        error = null
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val main = Handler(Looper.getMainLooper())
        try {
            wm.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    val (ssid, pass) = credentials(r)
                    info = HotspotInfo(ssid, pass, null)
                    starting = false
                    // the interface can come up a moment after onStarted
                    main.postDelayed({ info = info?.copy(ip = hotspotIp()) }, 1000)
                }
                override fun onStopped() { info = null; reservation = null; starting = false }
                override fun onFailed(reason: Int) {
                    error = "Hotspot failed (code $reason). Turn on Location and try again."
                    starting = false
                }
            }, main)
        } catch (e: Exception) {
            error = e.message ?: "Hotspot failed"
            starting = false
        }
    }

    fun stop() {
        runCatching { reservation?.close() }
        reservation = null
        info = null
        starting = false
    }

    @SuppressLint("NewApi")
    private fun credentials(r: WifiManager.LocalOnlyHotspotReservation): Pair<String, String> {
        if (Build.VERSION.SDK_INT >= 30) {
            val c = r.softApConfiguration
            val ssid = if (Build.VERSION.SDK_INT >= 33) c.wifiSsid?.toString() ?: "" else c.ssid ?: ""
            return ssid.trim('"') to (c.passphrase ?: "")
        }
        @Suppress("DEPRECATION")
        val c = r.wifiConfiguration
        return (c?.SSID ?: "").trim('"') to (c?.preSharedKey ?: "").trim('"')
    }

    private fun hotspotIp(): String? =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .filter { n -> listOf("ap", "swlan", "wlan1").any { n.name.startsWith(it) } }
            .flatMap { it.inetAddresses.toList().filterIsInstance<Inet4Address>() }
            .firstOrNull()?.hostAddress
}