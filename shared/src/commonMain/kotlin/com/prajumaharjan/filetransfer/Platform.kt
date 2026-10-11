package com.prajumaharjan.filetransfer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform