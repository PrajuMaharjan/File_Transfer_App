package com.prajumaharjan.filetransfer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val store =  remember { AndroidProfileStore(this@MainActivity) }
            var profile by remember { mutableStateOf(store.load()) }
            var editing by remember { mutableStateOf(false) }
            val current = profile

            if (current == null || editing){
                ProfileSetupScreen(
                    initial = current,
                    onDone = { store.save(it); profile = it; editing = false;},
                    onCancel = if (current != null) ({ editing = false}) else null
                )
            } else {
                SenderScreen(current,onEditProfile = {editing = true})
            }
        }
    }


}

private fun startReceiverService(context: Context){
    ContextCompat.startForegroundService(context, Intent(context, ReceiverService::class.java))
}

private fun stopReceiverService(context: Context){
    context.startService(
        Intent(context, ReceiverService::class.java).setAction(ReceiverService.ACTION_STOP)
    )
}

private fun formatBytes(b:Long): String=when{
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b/1024.0)
    b < 1024L * 1024*1024 -> "%.1f MB".format(b/(1024.0*1024))
    else -> "%.2f GB".format(b/(1024.0*1024*1024))
}
@Composable
fun SenderScreen(profile: Profile, onEditProfile:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableIntStateOf(8080) }
    val devices = remember { mutableStateListOf<Device>() }
    val logs = remember { mutableStateListOf<String>() }
    var discoveryStatus by remember { mutableStateOf("Starting discovery") }
    var transfer by remember { mutableStateOf<SendProgress?>(null) }
    var sending by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        val discovery = NsDiscovery(context)
        discovery.start(
            onFound = { d ->
                scope.launch{
                    val h: HelloResponse? = withContext(Dispatchers.IO) {
                        var r = fetchHello(d.host, d.port)
                        if (r == null) {
                            delay(800)
                            r = fetchHello(d.host, d.port)
                        }
                        r
                    }
                    if (h != null) {
                        val full = d.withHello(h)
                        if (full.id != profile.id) {
                            devices.removeAll {
                                it.name == full.name ||
                                        (full.id.isNotBlank() && it.id == full.id) ||
                                        (it.host == full.host && it.port == full.port)
                            }
                            devices.add(full)
                        }
                    }
                }
            },
            onLost = {n -> devices.removeAll { it.name == n } },
            onStatus = { discoveryStatus = it }
        )
        onDispose { discovery.stop() }
    }

    LaunchedEffect(Unit) {
        val misses = mutableMapOf<String, Int>()
        while (true) {
            delay(5000)
            for (d in devices.toList()) {
                val ok = withContext(Dispatchers.IO) { fetchHello(d.host, d.port) } != null
                val key = d.host + ":" + d.port
                if (ok) {
                    misses.remove(key)
                } else {
                    val n = (misses[key] ?: 0) + 1
                    if (n >= 2) { devices.remove(d); misses.remove(key) } else misses[key] = n
                }
            }
        }
    }

    val picker=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri:Uri?->
        if(uri != null){
            job = scope.launch {
                sending = true
                transfer = null
                try {
                    val done = withContext(Dispatchers.IO) {
                        uploadFile(context, uri, ip.trim(), port, profile,coroutineContext) { transfer = it }
                    }
                    logs.add("Sent ${done.name} (${formatBytes(done.sent)})")
                } catch (e: CancellationException) {
                    logs.add("Cancelled")
                } catch (e: CancelledbyReceiver) {
                    logs.add("Cancelled by receiver")
                } catch (e: Exception) {
                    logs.add("Failed: ${e.message}")
                }
                sending = false
                job = null
            }
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startReceiverService(context) }

    Column(Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ){
        ReceiverHub.pending?.let { (offer, decision) ->
            AlertDialog(
                onDismissRequest = { decision.complete(false) },
                title = { Text("Incoming file") },
                text = { Text("${offer.senderName.ifBlank { "Someone" }} wants to send ${offer.name} (${formatBytes(offer.size)}). Accept?") },
                confirmButton = { Button(onClick = { decision.complete(true) }) { Text("Accept") } },
                dismissButton = { TextButton(onClick = { decision.complete(false) }) { Text("Reject") } }
            )
        }

        Text("Send to PC",style= MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onEditProfile) { Text("Edit profile (${profile.name})") }
        Row(verticalAlignment = Alignment.CenterVertically){
            Text("Receive files", Modifier.weight(1f))
            Switch(
                checked = ReceiverHub.running,
                onCheckedChange = { on ->
                    if(on){
                        if(Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startReceiverService(context)
                    }else stopReceiverService(context)
                }
            )
        }
        ReceiverHub.transfer?.let {r ->
            val f = if (r.total > 0) (r.received.toFloat() / r.total).coerceIn(0f,1f) else 0f
            val text = when {
                r.cancelled -> "Cancelled: ${r.name}"
                r.failed -> "Failed: ${r.name}"
                r.finished -> "Received: ${r.name} (${formatBytes(r.received)})"
                else -> "Receiving ${r.name}: ${formatBytes(r.received)} / ${formatBytes(r.total)} (${(f * 100).toInt()}%)"
            }
            Row(verticalAlignment = Alignment.CenterVertically){
                Text(text,Modifier.weight(1f))
                if(!r.finished && !r.failed && !r.cancelled){
                    IconButton(onClick = { ReceiverHub.cancelFlag.set(true) }) {Text("✕") }
                }
            }
            LinearProgressIndicator(
                progress = { if (r.finished) 1f else f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Nearby devices",style= MaterialTheme.typography.titleSmall)
        if(devices.isEmpty()) Text("Searching...")
        Text(discoveryStatus, style = MaterialTheme.typography.bodySmall)
        devices.forEach { d ->
            OutlinedButton(
                onClick = {ip = d.host; port = d.port },
                modifier = Modifier.fillMaxWidth()
            ) {
                Avatar(d.displayName,d.avatar,28.dp)
                Spacer(Modifier.width(8.dp))
                Text("${d.displayName} (${d.host})")
            }
        }

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it; port = 8080 },
            label={Text("PC IP address (e.g. 192.168.1.23)")},
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {picker.launch(arrayOf("*/*"))},
                enabled=!sending && ip.isNotBlank()
        ){
            Text("Pick file and send")
        }

        val t=transfer
        if(t==null){
            Text("Enter the PC's IP, then pick a file")
        }else{
            val fraction=if (t.total>0)(t.sent.toFloat()/t.total).coerceIn(0f,1f) else 0f

            val label=when{
                t.waiting -> "Waiting for approval on PC: ${t.name}"
                t.cancelled -> "Cancelled: ${t.name}"
                t.failed -> "Failed: ${t.name}"
                t.finished -> "Done: ${t.name} (${formatBytes(t.sent)})"
                t.total > 0 -> "Sending ${t.name}: ${formatBytes(t.sent)} / ${formatBytes(t.total)} (${(fraction * 100).toInt()}%)"
                else -> "Sending ${t.name}: ${formatBytes(t.sent)}"
            }

            Row(verticalAlignment = Alignment.CenterVertically){
                Text(label, Modifier.weight(1f))
                if(!t.finished && !t.failed && !t.cancelled && !t.waiting){
                    IconButton(onClick = {job?.cancel() }) { Text("✕") }
                }
            }
            if (t.waiting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            else if (t.total > 0 || t.finished) {
                LinearProgressIndicator(
                    progress = { if (t.finished) 1f else fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        LazyColumn { items(logs) {Text(it) } }
    }
}

private fun uploadFile(context: Context,
                       uri:Uri,
                       ip: String,
                       port: Int,
                       profile: Profile,
                       ctx: CoroutineContext,
                       onProgress:(SendProgress)->Unit
): SendProgress{
    var name="file"
    var size=-1L
    context.contentResolver.query(uri,null,null,null,null)?.use{c->
        if(c.moveToFirst()){
            val n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s=c.getColumnIndex(OpenableColumns.SIZE)
            if(n>=0) name=c.getString(n)
            if(s>=0  && !c.isNull(s)) size=c.getLong(s)
        }
    }

    return sendStream(
        name, size, { context.contentResolver.openInputStream(uri) },
        ip, port, profile, ctx, onProgress
    )
}
