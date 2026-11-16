package com.prajumaharjan.filetransfer

import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private val helloJson = Json { ignoreUnknownKeys = true }

fun fetchHello(host : String, port : Int): HelloResponse? {
    return try {
        val c = URL("http://$host:$port/hello").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 1500
            c.readTimeout = 1500
            if (c.responseCode == 200){
                helloJson.decodeFromString(
                    HelloResponse.serializer(),
                    c.inputStream.bufferedReader().readText()
                )
            } else null
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}

fun Device.withHello(h: HelloResponse): Device = copy(
    id = h.id,
    displayName = h.name,
    avatar = runCatching { AvatarColor.valueOf(h.avatar) }.getOrDefault(AvatarColor.GREY)
)