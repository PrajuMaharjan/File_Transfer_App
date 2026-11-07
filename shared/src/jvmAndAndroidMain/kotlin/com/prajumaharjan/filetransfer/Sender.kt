package com.prajumaharjan.filetransfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.CoroutineContext

data class SendProgress(
    val name: String,
    val sent: Long,
    val total: Long,
    val finished: Boolean = false,
    val failed: Boolean = false,
    val cancelled: Boolean = false,
    val waiting: Boolean = false
)

private val json = Json { ignoreUnknownKeys = true }

private fun requestOffer(host: String, port: Int, name: String, size: Long, profile: Profile): String {
    val c = URL("http://$host:$port/offer").openConnection() as HttpURLConnection
    try {
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 5000
        c.readTimeout = 70_000
        c.setRequestProperty("Content-Type", "application/json")
        val body = json.encodeToString(
            OfferRequest.serializer(),
            OfferRequest(name, size, profile.id, profile.name, profile.avatar.name)
        )
        c.outputStream.use { it.write(body.toByteArray()) }
        return when (c.responseCode) {
            200 -> json.decodeFromString(
                OfferResponse.serializer(),
                c.inputStream.bufferedReader().readText()
            ).token
            403 -> error("Rejected by receiver")
            else -> error("Server returned ${c.responseCode}")
        }
    } finally {
        c.disconnect()
    }
}

class CancelledbyReceiver : Exception("Cancelled by receiver")

private fun receiverCancelled(host: String,port: Int, encodedName: String): Boolean{
    return try{
        val c = URL("http://$host:$port/cancelled/$encodedName").openConnection() as HttpURLConnection
        try{
            c.connectTimeout = 1000
            c.readTimeout = 1000
            c.responseCode == 200 && c.getInputStream().bufferedReader().readText() == "true"
        } finally {
            c.disconnect()
        }
    } catch (e: Exception){
        false
    }
}

fun sendFile(
    file: File,
    host: String,
    port: Int,
    profile: Profile,
    ctx: CoroutineContext,
    onProgress: (SendProgress) -> Unit
): SendProgress = sendStream(
    file.name, file.length(),{file.inputStream() },
    host,port,profile,ctx,onProgress
)

fun sendStream(
    name: String,
    size: Long,
    open: ()-> InputStream?,
    host: String,
    port: Int,
    profile: Profile,
    ctx: CoroutineContext,
    onProgress: (SendProgress) -> Unit
): SendProgress {
    onProgress(SendProgress(name,0,size, waiting = true))

    val token = try {
        requestOffer(host, port, name, size, profile)
    } catch (e: Exception) {
        onProgress(SendProgress(name, 0, size, failed = true))
        throw e
    }

    var sent = 0L
    var lastEmit = 0L
    var lastPoll = System.nanoTime()
    val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    val conn = URL("http://$host:$port/upload/$encoded").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "PUT"
        conn.setRequestProperty("X-Token", token)
        conn.doOutput = true
        conn.connectTimeout = 5000
        if (size >= 0) conn.setFixedLengthStreamingMode(size)
        else conn.setChunkedStreamingMode(64 * 1024)

        val input = open() ?: error("Cannot open file")
        input.use { inp ->
            conn.outputStream.use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    ctx.ensureActive()
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    val now = System.nanoTime()
                    if (now - lastPoll > 500_000_000L){
                        lastPoll = now
                        if (receiverCancelled(host,port,encoded)) throw CancelledbyReceiver()
                    }
                    if (now - lastEmit > 100_000_000L) {
                        lastEmit = now
                        onProgress(SendProgress(name, sent, size))
                    }
                }
            }
        }

        val code = conn.responseCode
        if (code == 499) throw CancelledbyReceiver()
        if (code != 200) error("Server returned $code")
        val done = SendProgress(name, sent, size, finished = true)
        onProgress(done)
        return done
    } catch (e: CancellationException) {
        onProgress(SendProgress(name, sent, size, cancelled = true))
        throw e
    } catch (e: CancelledbyReceiver) {
        onProgress(SendProgress(name, sent, size, cancelled = true))
        throw e
    }
    catch (e: Exception) {
        onProgress(SendProgress(name, sent, size, failed = true))
        throw e
    } finally {
        conn.disconnect()
    }
}

