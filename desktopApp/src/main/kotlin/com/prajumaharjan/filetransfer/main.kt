package com.prajumaharjan.filetransfer


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.io.File

private fun formatBytes(b:Long): String=when{
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b/1024.0)
    b < 1024L * 1024*1024 -> "%.1f MB".format(b/(1024.0*1024))
    else -> "%.2f GB".format(b/(1024.0*1024*1024))
}

fun main() = application {

    val logs=remember { mutableStateListOf<String>() }
    var transfer by remember {mutableStateOf<TransferProgress?>(null)}

    LaunchedEffect(Unit) {
        val dir=File(System.getProperty("user.home"),"Downloads/FileTransfer")
        startReceiver(
            port = 8080,
            saveDir = dir,
            onProgress = { transfer = it },
            onLog = { logs.add(it) }
        )

        logs.add("Listening on port 8080, saving to $dir")
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "FileTransfer"
    ) {
        MaterialTheme{
            Column(Modifier.padding(16.dp)){
                Text("Receiver running",style= MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))

                transfer?.let{t->
                    val fraction = if (t.total > 0) (t.received.toFloat() / t.total).coerceIn(0f, 1f) else 0f
                    val label = when {
                        t.failed -> "Failed: ${t.name}"
                        t.finished -> "Done: ${t.name} (${formatBytes(t.received)})"
                        t.total > 0 -> "Receiving ${t.name}: ${formatBytes(t.received)} / ${formatBytes(t.total)} (${(fraction * 100).toInt()}%)"
                        else -> "Receiving ${t.name}: ${formatBytes(t.received)}"
                    }

                    Text(label)
                    Spacer(Modifier.height(4.dp))
                    if (t.total > 0 || t.finished) {
                        LinearProgressIndicator(
                            progress = { if (t.finished) 1f else fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                }
                LazyColumn{items(logs){Text(it)}}
            }
        }
    }
}