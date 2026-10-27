package com.prajumaharjan.filetransfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.Alignment
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File

private fun formatBytes(b:Long): String=when{
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b/1024.0)
    b < 1024L * 1024*1024 -> "%.1f MB".format(b/(1024.0*1024))
    else -> "%.2f GB".format(b/(1024.0*1024*1024))
}

fun main() {

    val store = DesktopProfileStore()

    application {

        var profile by remember { mutableStateOf(store.load()) }
        var editing by remember { mutableStateOf(false) }
        val logs = remember { mutableStateListOf<String>() }
        var transfer by remember { mutableStateOf<TransferProgress?>(null) }
        val cancelFlag = remember { AtomicBoolean(false) }
        val devices = remember { mutableStateListOf<Device>() }
        var pending by remember { mutableStateOf<Pair<OfferRequest, CompletableDeferred<Boolean>>?>(null) }

        LaunchedEffect(profile?.id) {
            val p = profile ?: return@LaunchedEffect
            val dir = File(System.getProperty("user.home"), "Downloads/FileTransfer")
            startReceiver(
                port = 8080,
                saveDir = dir,
                profile = p,
                cancelFlag = cancelFlag,
                onProgress = { transfer = it },
                onOffer = { offer ->
                    val decision = CompletableDeferred<Boolean>()
                    pending = offer to decision
                    try {
                        decision.await()
                    } finally {
                        pending = null
                    }
                },
                onLog = { logs.add(it) }
            )

            logs.add("Listening on port 8080, saving to $dir")
        }

        DisposableEffect(profile?.id) {
            val p = profile
            if (p == null) return@DisposableEffect onDispose {  }
            val discovery = DesktopDiscovery()
            val selfName = (System.getenv("COMPUTERNAME") ?: "Desktop") + "-" + (1000..9999).random()
            Thread {
                try {
                    discovery.start(
                        selfName = selfName,
                        profile = p,
                        port = 8080,
                        onFound = { d ->
                            if (d.id !=p.id) { devices.removeAll { it.name == d.name }; devices.add(d) }
                        },
                        onLost = { n -> devices.removeAll { it.name == n } },
                        onLog = { logs.add(it) }
                    )
                    logs.add("Advertising as $selfName")
                } catch (e: Exception) {
                    logs.add("Discovery failed: ${e.message}")
                }
            }.start()
            onDispose { discovery.stop() }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "FileTransfer"
        ) {
            MaterialTheme {
                val current = profile
                if (current == null || editing){
                    ProfileSetupScreen(
                        initial = current,
                        onDone = { store.save(it); profile = it; editing = false },
                        onCancel = if (current != null) ({ editing = false }) else null
                    )
                } else {
                    pending?.let { (offer, decision) ->
                        AlertDialog(
                            onDismissRequest = { decision.complete(false) },
                            title = { Text("Incoming file") },
                            text = { Text("${offer.senderName.ifBlank { "Someone" }} wants to send ${offer.name} (${formatBytes(offer.size)}). Accept?") },
                            confirmButton = { Button(onClick = { decision.complete(true) }) { Text("Accept") } },
                            dismissButton = { TextButton(onClick = { decision.complete(false) }) { Text("Reject") } }
                        )
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text("Receiver running", style = MaterialTheme.typography.headlineSmall)
                        TextButton(onClick = { editing = true }) { Text("Edit profile (${current.name})") }
                        Spacer(Modifier.height(12.dp))

                        transfer?.let { t ->
                            val fraction = if (t.total > 0) (t.received.toFloat() / t.total).coerceIn(0f, 1f) else 0f
                            val label = when {
                                t.cancelled -> "Cancelled: ${t.name}"
                                t.failed -> "Failed: ${t.name}"
                                t.finished -> "Done: ${t.name} (${formatBytes(t.received)})"
                                t.total > 0 -> "Receiving ${t.name}: ${formatBytes(t.received)} / ${formatBytes(t.total)} (${(fraction * 100).toInt()}%)"
                                else -> "Receiving ${t.name}: ${formatBytes(t.received)}"
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f))
                                if (!t.finished && !t.failed && !t.cancelled) {
                                    IconButton(onClick = { cancelFlag.set(true) }) { Text("✕") }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            if (t.total > 0 || t.finished) {
                                LinearProgressIndicator(
                                    progress = { if (t.finished) 1f else fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (!t.failed && !t.cancelled) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        Text("Nearby devices", style = MaterialTheme.typography.titleSmall)
                        if (devices.isEmpty()) Text("Noone found yet")
                        devices.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(d.displayName, d.avatar, 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("${d.displayName} (${d.host}:${d.port})")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn { items(logs) { Text(it) } }
                    }
                }
            }
        }
    }
}