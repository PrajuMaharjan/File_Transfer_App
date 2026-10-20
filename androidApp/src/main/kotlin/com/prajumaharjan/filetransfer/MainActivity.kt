package com.prajumaharjan.filetransfer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import  org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun requestOffer(ip: String,port:Int,name: String,size:Long): String{
    val c = URL("http://$ip:$port/offer").openConnection() as HttpURLConnection
    try{
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 5000
        c.readTimeout = 70_000
        c.setRequestProperty("Content-Type","application/json")
        c.outputStream.use{
            it.write(JSONObject().put("name",name).put("size",size).toString().toByteArray())
        }
        return when (c.responseCode){
            200 -> JSONObject(c.inputStream.bufferedReader().readText()).getString("token")
            403 -> error("Rejected by PC")
            else -> error("Server returned ${c.responseCode}")
        }
    }finally {
        c.disconnect()
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme{ SenderScreen() }
        }
    }
}

data class UploadProgress(     val name: String,
                               val sent:Long,
                               val total:Long,
                               val finished:Boolean=false,
                               val failed:Boolean=false,
                               val cancelled: Boolean = false,
                               val waiting: Boolean = false
)

private fun formatBytes(b:Long): String=when{
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b/1024.0)
    b < 1024L * 1024*1024 -> "%.1f MB".format(b/(1024.0*1024))
    else -> "%.2f GB".format(b/(1024.0*1024*1024))
}
@Composable
fun SenderScreen() {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var ip by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<String>() }
    var transfer by remember { mutableStateOf<UploadProgress?>(null) }
    var sending by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    val picker=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri:Uri?->
        if(uri != null){
            job = scope.launch {
                sending = true
                transfer = null
                try {
                    val done = withContext(Dispatchers.IO) {
                        uploadFile(context, uri, ip.trim(), 8080,coroutineContext) { transfer = it }
                    }
                    logs.add("Sent ${done.name} (${formatBytes(done.sent)})")
                } catch (e: CancellationException) {
                    logs.add("Cancelled")
                } catch (e: Exception) {
                    logs.add("Failed:${e.message}")
                }
                sending = false
                job = null
            }
        }
    }

    Column(Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ){
        Text("Send to PC",style= MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
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
                       port:Int,
                       ctx: CoroutineContext,
                       onProgress:(UploadProgress)->Unit
): UploadProgress{
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

    onProgress(UploadProgress(name,0,size, waiting = true))
    val token = try{
        requestOffer(ip, port, name, size)
    }catch(e: Exception){
        onProgress(UploadProgress(name,0,size, failed = true))
        throw e
    }
    var sent=0L
    var lastEmit=0L
    val conn=URL("http://$ip:$port/upload/${Uri.encode(name)}")
        .openConnection() as HttpURLConnection

    try {
        conn.requestMethod = "PUT"
        conn.setRequestProperty("X-Token", token)
        conn.doOutput = true
        conn.connectTimeout = 5000
        if (size >= 0) conn.setFixedLengthStreamingMode(size)
        else conn.setChunkedStreamingMode(64 * 1024)

        val input = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open file")

        input.use { inp ->
            conn.outputStream.use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    ctx.ensureActive()
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    val now = System.nanoTime()
                    if (now - lastEmit > 100_000_000L) {
                        lastEmit = now
                        onProgress(UploadProgress(name, sent, size))
                    }
                }
            }
        }

        val code = conn.responseCode
        if (code == 499) error("Cancelled by PC")
        if (code != 200) error("Server returned $code")
        val done = UploadProgress(name, sent, size, finished = true)
        onProgress(done)
        return done
    }catch (e: CancellationException){
        onProgress(UploadProgress(name, sent, size, cancelled = true))
        throw e
    }catch (e: Exception){
        onProgress(UploadProgress(name,sent,size,failed=true))
        throw e
    }finally {
        conn.disconnect()
    }
}
