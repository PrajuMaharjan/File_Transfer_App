package com.prajumaharjan.filetransfer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "File-Transfer-App",
    ) {
        App()
    }
}