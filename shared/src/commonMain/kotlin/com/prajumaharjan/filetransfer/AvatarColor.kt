package com.prajumaharjan.filetransfer

import  kotlinx.serialization.Serializable

@Serializable
enum class AvatarColor(val argb:Long) {
    RED(0xFFE53935),
    ORANGE(0xFFFB8C00),
    AMBER(0xFFFFB300),
    GREEN(0xFF43A047),
    TEAL(0xFF00897B),
    BLUE(0xFF1E88E5),
    INDIGO(0xFF3949AB),
    PURPLE(0xFF8E24AA),
    PINK(0xFFD81B60),
    GREY(0xFF546E7A)
}