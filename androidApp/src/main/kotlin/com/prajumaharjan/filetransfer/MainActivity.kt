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

@Composable
fun SenderScreen() {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var ip by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter the PC's IP, then pick a file") }
    var progress by remember { mutableFloatStateOf(0f) }
    var sending by remember { mutableStateOf(false) }

    val picker=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri:Uri?->
        if(uri != null){
            scope.launch {
                sending = true
                progress = 0f
                status = "Sending..."
                status = try {
                    withContext(Dispatchers.IO) {
                        uploadFile(context, uri, ip.trim(), 8080) { progress = it }
                    }
                    "Sent successfully"
                } catch (e: Exception) {
                    "Failed:${e.message}"
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

        LinearProgressIndicator(progress = {progress},modifier=Modifier.fillMaxWidth())
        Text(status)
    }
}

private fun uploadFile(context: Context,
                       uri:Uri,
                       ip: String,
                       port:Int,
                       onProgress:(Float)->Unit
){
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

    val conn=URL("http://$ip:$port/upload/${Uri.encode(name)}")
        .openConnection() as HttpURLConnection

    try{
        conn.requestMethod="PUT"
        conn.doOutput=true
        conn.connectTimeout=5000
        if(size>=0) conn.setFixedLengthStreamingMode(size)
        else conn.setChunkedStreamingMode(64*1024)

        val input=context.contentResolver.openInputStream(uri)
                    ?: error("Cannot open file")

        input.use{inp->
            conn.outputStream.use{out->
                val buf=ByteArray(64*1024)
                var sent=0L
                while(true){
                    val n=inp.read(buf)
                    if(n<0) break
                    out.write(buf,0,n)
                    sent += n
                    if(size>0) onProgress(sent.toFloat()/size)
                }
            }
        }

        val code=conn.responseCode
        if(code != 200) error("Server returned $code")
        onProgress(1f)
    }finally {
        conn.disconnect()
    }
}
