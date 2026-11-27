package com.prajumaharjan.filetransfer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;")
    .replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

@Composable
fun QrCode(text: String) {
    val m = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0) }
    Canvas(Modifier.size(200.dp).background(Color.White)) {
        val cell = size.width / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
        }
    }
}

@Composable
fun HotspotPanel() {
    HotspotManager.error?.let { Text(it) }
    if (HotspotManager.starting) Text("Starting hotspot...")
    HotspotManager.info?.let { h ->
        Column(Modifier.padding(vertical = 4.dp)) {
            QrCode("WIFI:T:WPA;S:${esc(h.ssid)};P:${esc(h.password)};;")
            Text("Network: ${h.ssid}")
            Text("Password: ${h.password}")
            Text("Phone IP: ${h.ip ?: "see the PC's default gateway"}")
            Text("Phone to phone: scan the QR with the camera. PC: join the network by name, then it finds this phone on its own.")
        }
    }
}