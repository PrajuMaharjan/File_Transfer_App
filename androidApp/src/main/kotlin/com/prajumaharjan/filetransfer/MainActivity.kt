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
import java.net.HttpURLConnection
import java.net.URL

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
                               val failed:Boolean=false
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

    val picker=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri:Uri?->
        if(uri != null){
            scope.launch {
                sending = true
                transfer = null
                try {
                    val done = withContext(Dispatchers.IO) {
                        uploadFile(context, uri, ip.trim(), 8080) { transfer = it }
                    }
                    logs.add("Sent ${done.name} (${formatBytes(done.sent)})")
                } catch (e: Exception) {
                    logs.add("Failed:${e.message}")
                }
                sending = false
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
                t.failed -> "Failed: ${t.name}"
                t.finished -> "Done: ${t.name} (${formatBytes(t.sent)})"
                t.total > 0 -> "Sending ${t.name}: ${formatBytes(t.sent)} / ${formatBytes(t.total)} (${(fraction * 100).toInt()}%)"
                else -> "Sending ${t.name}: ${formatBytes(t.sent)}"
            }

            Text(label)
            if (t.total > 0 || t.finished) {
                LinearProgressIndicator(
                    progress = { if (t.finished) 1f else fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        LazyColumn { items(logs){Text(it)} } }
    }

private fun uploadFile(context: Context,
                       uri:Uri,
                       ip: String,
                       port:Int,
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

    var sent=0L
    var lastEmit=0L
    val conn=URL("http://$ip:$port/upload/${Uri.encode(name)}")
        .openConnection() as HttpURLConnection

    try {
        conn.requestMethod = "PUT"
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
        if (code != 200) error("Server returned $code")
        val done = UploadProgress(name, sent, size, finished = true)
        onProgress(done)
        return done
    }catch (e: Exception){
        onProgress(UploadProgress(name,sent,size,failed=true))
        throw e
    }finally {
        conn.disconnect()
    }
}
