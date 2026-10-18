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

fun main() = application {

    val logs=remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        val dir=File(System.getProperty("user.home"),"Downloads/FileTransfer")
        startReceiver(8080,dir){logs.add(it)}
        logs.add("Listening on port 8080, saving to $dir")
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "FileTransfer"
    ) {
        MaterialTheme{
            Column(Modifier.padding(16.dp)){
                Text("Receiver running",style= MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn{items(logs){Text(it)}}
            }
        }
    }
}