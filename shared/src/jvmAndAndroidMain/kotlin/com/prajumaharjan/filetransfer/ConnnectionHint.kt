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
                    "1. On the phone, turn on \"Hotspot\" in this app and Receive files.\n" +
                    "2. Join the network shown on the phone (QR for another phone, name and " + "password for a PC).\n" +
                    "3. The device list fills in within about 10 s. If not, type the phone's IP."
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text("OK") } }
    )
}