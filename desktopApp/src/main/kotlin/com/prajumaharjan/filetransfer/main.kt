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
import java.awt.FileDialog
import java.awt.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun main() {

    val store = DesktopProfileStore()

    application {

        var profile by remember { mutableStateOf(store.load()) }
        var editing by remember { mutableStateOf(false) }
        val logs = remember { mutableStateListOf<String>() }
        var transfer by remember { mutableStateOf<TransferProgress?>(null) }
        val cancelFlag = remember { AtomicBoolean(false) }
        var pending by remember { mutableStateOf<Pair<OfferRequest, CompletableDeferred<Boolean>>?>(null) }
        val scope = rememberCoroutineScope()
        val deviceList = remember { DeviceList(scope) }
        val devices = deviceList.devices
        var showHotspot by remember { mutableStateOf(false) }
        var connectFailed by remember { mutableStateOf(false) }
        var outgoing by remember { mutableStateOf<SendProgress?>(null) }
        var sendJob by remember { mutableStateOf<Job?>(null) }
        val sendTo: (Device) -> Unit = { d ->
            val p = profile
            if (p != null && sendJob == null) {
                val dialog = FileDialog(null as Frame?, "Choose a file to send", FileDialog.LOAD)
                dialog.isVisible = true
                val dir = dialog.directory
                val fn = dialog.file
                if (dir != null && fn != null) {
                    val file = File(dir, fn)
                    sendJob = scope.launch {
                        outgoing = null
                        connectFailed = false
                        try {
                            val done = withContext(Dispatchers.IO) {
                                sendFile(file, d.host, d.port, p, coroutineContext) { outgoing = it }
                            }
                            logs.add("Sent ${done.name} (${formatBytes(done.sent)})")
                        } catch (e: CancellationException) {
                            logs.add("Cancelled")
                        } catch (e: CancelledbyReceiver) {
                                logs.add("Cancelled by receiver")
                        } catch (e: Exception) {
                            logs.add("Failed: ${e.message}")
                            if(isUnreachable(e)) connectFailed=true
                        }
                        sendJob = null
                    }
                }
            }
        }

        val serverLock = remember { Mutex() }
        LaunchedEffect(profile) {
            val p = profile ?: return@LaunchedEffect

            serverLock.withLock{
                val dir = File(System.getProperty("user.home"), "Downloads/FileTransfer")
                val server = try{
                    withContext(Dispatchers.IO) {
                        startReceiver(
                            port = 8080,
                            sink = DirFileSink(dir),
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
                    }
                } catch (e:Exception){
                    logs.add("Receiver failed: ${e.message}")
                    return@withLock
                }
                logs.add("Listening on port 8080, saving to $dir")
                try{
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {server.stop(300,1000) }
                }
            }
        }

        DisposableEffect(profile) {
            val p = profile ?: return@DisposableEffect onDispose {  }
            val discovery = DesktopDiscovery()
            val selfName = (System.getenv("COMPUTERNAME") ?: "Desktop") + "-" + (1000..9999).random()
            Thread {
                try {
                    discovery.start(
                        selfName = selfName,
                        profile = p,
                        port = 8080,
                        onFound = { d -> deviceList.onFound(d,p.id) },
                        onLost = { n -> deviceList.onLost(n) },
                        onLog = { logs.add(it) }
                    )
                    logs.add("Advertising as $selfName")
                } catch (e: Exception) {
                    logs.add("Discovery failed: ${e.message}")
                }
            }.start()

            onDispose { Thread{runCatching { discovery.stop() } }.start() }
        }

        LaunchedEffect(Unit){
            deviceList.runPruner()
        }
        LaunchedEffect(profile?.id){
            deviceList.runStuckWatcher(profile?.id ?: "")
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

                        transfer?.let {
                            ReceiveCard(it) { cancelFlag.set(true) }
                            Spacer(Modifier.height(12.dp))
                        }
                        outgoing?.let {
                            SendCard(it) { sendJob?.cancel() }
                            Spacer(Modifier.height(12.dp))
                        }

                        Text("Nearby devices", style = MaterialTheme.typography.titleSmall)
                        if (devices.isEmpty()) Text("No one found yet")
                        ConnectionHint(deviceList.stuck || connectFailed) {showHotspot=true}
                        if (showHotspot) HotspotInfoDialog { showHotspot = false }
                        devices.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(d.displayName, d.avatar, 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("${d.displayName} (${d.host}:${d.port})")
                                Spacer(Modifier.width(8.dp))
                                Button(enabled = sendJob == null, onClick = {sendTo(d)}) {Text("Send file") }
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