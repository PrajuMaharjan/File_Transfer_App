package com.prajumaharjan.filetransfer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// progress == null means indeterminate
@Composable
fun TransferCard(label: String, progress: Float?, cancellable: Boolean, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            if (cancellable) IconButton(onClick = onCancel) { Text("✕") }
        }
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun SendCard(t: SendProgress, onCancel: () -> Unit) {
    val f = if (t.total > 0) (t.sent.toFloat() / t.total).coerceIn(0f, 1f) else 0f
    val label = when {
        t.waiting -> "Waiting for approval: ${t.name}"
        t.cancelled -> "Cancelled: ${t.name}"
        t.failed -> "Failed: ${t.name}"
        t.finished -> "Sent: ${t.name} (${formatBytes(t.sent)})"
        t.total > 0 -> "Sending ${t.name}: ${formatBytes(t.sent)} / ${formatBytes(t.total)} (${(f * 100).toInt()}%)"
        else -> "Sending ${t.name}: ${formatBytes(t.sent)}"
    }
    val ended = t.finished || t.failed || t.cancelled
    val progress = when {
        t.finished -> 1f
        ended -> f
        t.waiting || t.total <= 0 -> null
        else -> f
    }
    TransferCard(label, progress, !ended && !t.waiting, onCancel)
}

@Composable
fun ReceiveCard(t: TransferProgress, onCancel: () -> Unit) {
    val f = if (t.total > 0) (t.received.toFloat() / t.total).coerceIn(0f, 1f) else 0f
    val label = when {
        t.cancelled -> "Cancelled: ${t.name}"
        t.failed -> "Failed: ${t.name}"
        t.finished -> "Received: ${t.name} (${formatBytes(t.received)})"
        t.total > 0 -> "Receiving ${t.name}: ${formatBytes(t.received)} / ${formatBytes(t.total)} (${(f * 100).toInt()}%)"
        else -> "Receiving ${t.name}: ${formatBytes(t.received)}"
    }
    val ended = t.finished || t.failed || t.cancelled
    val progress = when {
        t.finished -> 1f
        ended -> f
        t.total <= 0 -> null
        else -> f
    }
    TransferCard(label, progress, !ended, onCancel)
}