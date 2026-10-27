package com.prajumaharjan.filetransfer

data class Device(
    val name: String,
    val host: String,
    val port: Int,
    val id: String = "",
    val displayName: String = name,
    val avatar: AvatarColor = AvatarColor.GREY
)