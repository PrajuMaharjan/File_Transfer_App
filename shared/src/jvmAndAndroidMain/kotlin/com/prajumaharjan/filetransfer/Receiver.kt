 package com.prajumaharjan.filetransfer

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

 @Serializable
 data class HelloResponse(val name: String, val id: String = "", val avatar: String = "GREY")

 @Serializable
 data class OfferRequest(
     val name: String,
     val size: Long,
     val senderId: String = "",
     val senderName: String = "",
     val senderAvatar: String = "GREY"
 )

 @Serializable
 data class OfferResponse(val token: String)

 data class TransferProgress(   val name : String,
                                val received : Long,
                                val total : Long,
                                val finished : Boolean = false,
                                val failed : Boolean = false,
                                val cancelled : Boolean = false
 )

 class TransferCancelled : Exception("Cancelled")

 interface FileSink {
     fun open(name: String): java.io.OutputStream
     fun commit(name: String) {}
     fun discard(name: String)
 }

 class DirFileSink(private val dir: File) : FileSink {
     init { dir.mkdirs() }
     override fun open(name: String): java.io.OutputStream = FileOutputStream(File(dir,name))
     override fun discard(name: String) { File(dir,name).delete() }
 }

 fun startReceiver(
     port : Int = 8080,
     sink : FileSink,
     profile: Profile,
     cancelFlag : AtomicBoolean,
     onProgress : (TransferProgress) -> Unit,
     onOffer : suspend (OfferRequest) -> Boolean,
     onLog : (String) -> Unit
 ) : EmbeddedServer<*,*> {

     val tokens = ConcurrentHashMap<String, String>()
     val cancelledNames = ConcurrentHashMap.newKeySet<String>()
     val senderCancelled = ConcurrentHashMap.newKeySet<String>()

     return embeddedServer(CIO, port = port, host = "0.0.0.0") {
         install(ContentNegotiation) { json() }
         routing {

             get("/hello") {
                 call.respond(HelloResponse(profile.name, profile.id, profile.avatar.name))
             }

             get("/cancelled/{name}") {
                 val n = File(call.parameters["name"] ?: "").name
                 call.respondText(if (cancelledNames.contains(n)) "true" else "false")
             }

             post("/cancel/{name}") {
                 senderCancelled.add(File(call.parameters["name"] ?: "").name)
                 call.respond(HttpStatusCode.OK)
             }

             post("/offer") {
                 val offer = call.receive<OfferRequest>()
                 val safeName = File(offer.name).name

                 // Counts as rejected if not accepted within 60 seconds
                 val accepted = withTimeoutOrNull(60.seconds) {
                     onOffer(offer.copy(name = safeName))
                 } ?: false

                 if (accepted) {
                     val token = UUID.randomUUID().toString()
                     tokens[token] = safeName
                     onLog("Accepted $safeName")
                     call.respond(OfferResponse(token))
                 } else {
                     onLog("Rejected $safeName")
                     call.respond(HttpStatusCode.Forbidden)
                 }
             }

             put("/upload/{name}") {
                 val safeName = File(call.parameters["name"] ?: "unnamed").name
                 val token = call.request.headers["X-Token"]
                 if (token == null || tokens.remove(token) != safeName) {
                     call.respond(HttpStatusCode.Unauthorized)
                     return@put
                 }
                 cancelFlag.set(false)
                 cancelledNames.remove(safeName)
                 val total = call.request.contentLength() ?: -1L
                 val channel = call.receiveChannel()

                 var received = 0L
                 var lastEmit = 0L
                 var stalledMs = 0

                 try {
                     withContext(Dispatchers.IO) {
                         sink.open(safeName).use { out ->
                             val buf = ByteArray(64 * 1024)
                             while (true) {
                                 if (cancelFlag.get()) throw TransferCancelled()
                                 val n = withTimeoutOrNull(1000.milliseconds){
                                     channel.readAvailable(buf, 0, buf.size)
                                 }
                                 if(n==null){
                                     stalledMs += 1000
                                     if(stalledMs >= 10_000) throw IOException("Connection stalled")
                                     continue
                                 }
                                 stalledMs = 0

                                 if (n < 0) break
                                 out.write(buf, 0, n)
                                 received += n

                                 val now = System.nanoTime()
                                 if (now - lastEmit > 100_000_000L) {
                                     lastEmit = now
                                     onProgress(TransferProgress(safeName, received, total))
                                 }
                             }
                             if (total >= 0 && received < total) throw IOException("Connection lost")
                         }
                     }
                     sink.commit(safeName)
                     onProgress(TransferProgress(safeName, received, total, finished = true))
                     onLog("Received $safeName ($received bytes)")
                     call.respond(HttpStatusCode.OK)
                 } catch (e: TransferCancelled) {
                     sink.discard(safeName)
                     cancelledNames.add(safeName)
                     senderCancelled.remove(safeName)
                     onProgress(TransferProgress(safeName, received, total, cancelled = true))
                     onLog("Cancelled $safeName")
                     call.respond(HttpStatusCode(499,"Cancelled"))
                 } catch (e: Exception) {
                     delay(500.milliseconds)
                     val bySender = senderCancelled.remove(safeName)
                     sink.discard(safeName)
                     if(bySender){
                         onProgress(TransferProgress(safeName, received, total, cancelled = true))
                         onLog("Cancelled by sender: $safeName")
                     }else{
                         onProgress(TransferProgress(safeName, received, total, failed = true))
                         onLog("Failed $safeName: ${e.message}")
                     }
                     runCatching { call.respond(HttpStatusCode.InternalServerError) }
                 }
             }
         }
     }.start(wait = false)
 }