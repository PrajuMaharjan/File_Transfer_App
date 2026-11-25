package com.prajumaharjan.filetransfer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun isUnreachable(e: Exception): Boolean =
    e is ConnectException || e is SocketTimeoutException ||
            e is NoRouteToHostException || e is UnknownHostException

@Composable
fun ConnectionHint(visible: Boolean, onOpen: () -> Unit) {
    if (!visible) return
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("No devices reachable. This network may block device-to-device traffic.")
        OutlinedButton(onClick = onOpen) { Text("Can't connect? Use hotspot mode") }
    }
}

@Composable
fun HotspotInfoDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Use a hotspot") },
        text = {
            Text(
                "Some Wi-Fi networks stop devices from talking to each other.\n\n" +
                        "1. Turn on the phone's Wi-Fi hotspot.\n" +
                        "2. Connect the PC to that hotspot.\n" +
                        "3. Open the app on both. If the device list stays empty, type the other " +
                        "device's IP address manually.\n\n" +
                        "One-tap hotspot with a QR code is coming in the next update."
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text("OK") } }
    )
}