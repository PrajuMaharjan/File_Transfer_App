package com.prajumaharjan.filetransfer

import  kotlinx.serialization.Serializable
import  kotlin.uuid.ExperimentalUuidApi
import  kotlin.uuid.Uuid

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val avatar: AvatarColor
){
    companion object{
        const val MAX_NAME=24

        fun cleanName(raw: String): String = raw.trim().take(MAX_NAME)

        @OptIn(ExperimentalUuidApi::class)
        fun create(name: String,avatar: AvatarColor): Profile =
            Profile(Uuid.random().toString(),cleanName(name),avatar)
    }
}