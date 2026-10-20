 package com.prajumaharjan.filetransfer

import  io.ktor.http.*
import  io.ktor.serialization.kotlinx.json.*
import  io.ktor.server.application.*
import  io.ktor.server.cio.*
import  io.ktor.server.engine.*
import  io.ktor.server.plugins.contentnegotiation.*
import  io.ktor.server.request.*
import  io.ktor.server.response.*
import  io.ktor.server.routing.*
import  io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import  kotlinx.serialization.Serializable
import  java.io.File
import  java.io.FileOutputStream
import  java.io.IOException
import  java.util.concurrent.atomic.AtomicBoolean
import  java.util.UUID
import  java.util.concurrent.ConcurrentHashMap

 @Serializable
 data class HelloResponse(val name: String)

 @Serializable
 data class OfferRequest(val name: String,val size:Long)

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
 fun startReceiver(
     port : Int = 8080,
     saveDir : File,
     cancelFlag : AtomicBoolean,
     onProgress : (TransferProgress) -> Unit,
     onOffer : suspend (OfferRequest) -> Boolean,
     onLog : (String) -> Unit
 ) : EmbeddedServer<*,*> {
     saveDir.mkdirs()

     val tokens = ConcurrentHashMap<String, String>()

     return embeddedServer(CIO, port = port, host = "0.0.0.0") {
         install(ContentNegotiation) { json() }
         routing {

             get("/hello") {
                 call.respond(HelloResponse(System.getenv("COMPUTERNAME") ?: "Desktop"))
             }

             post("/offer") {
                 val offer = call.receive<OfferRequest>()
                 val safeName = File(offer.name).name

                 // Counts as rejected if not accepted within 60 seconds
                 val accepted = withTimeoutOrNull(60_000) {
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
                 val target = File(saveDir, safeName)
                 cancelFlag.set(false)
                 val total = call.request.contentLength() ?: -1L
                 val channel = call.receiveChannel()

                 var received = 0L
                 var lastEmit = 0L

                 try {
                     withContext(Dispatchers.IO) {
                         FileOutputStream(target).use { out ->
                             val buf = ByteArray(64 * 1024)
                             while (true) {
                                 if (cancelFlag.get()) throw TransferCancelled()
                                 val n = channel.readAvailable(buf, 0, buf.size)
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
                     onProgress(TransferProgress(safeName, received, total, finished = true))
                     onLog("Received $safeName ($received bytes)")
                     call.respond(HttpStatusCode.OK)
                 } catch (e: TransferCancelled) {
                     target.delete()
                     onProgress(TransferProgress(safeName, received, total, cancelled = true))
                     onLog("Cancelled $safeName")
                     call.respond(HttpStatusCode(499,"Cancelled"))
                 } catch (e: Exception) {
                     target.delete()
                     onProgress(TransferProgress(safeName, received, total, failed = true))
                     onLog("Failed $safeName: ${e.message}")
                     call.respond(HttpStatusCode.InternalServerError)
                 }
             }
         }
     }.start(wait = false)
 }