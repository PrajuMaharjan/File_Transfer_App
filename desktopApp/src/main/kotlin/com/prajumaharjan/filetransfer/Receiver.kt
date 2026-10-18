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
import kotlinx.coroutines.withContext
import  kotlinx.serialization.Serializable
import  java.io.File
import  java.io.FileOutputStream

 @Serializable
 data class HelloResponse(val name: String)
 data class TransferProgress(   val name: String,
                                val received:Long,
                                val total:Long,
                                val finished:Boolean=false,
                                val failed:Boolean=false
 )


 fun startReceiver(
     port:Int=8080,
     saveDir: File,
     onProgress:(TransferProgress)->Unit,
     onLog:(String)->Unit
 ): EmbeddedServer<*,*> {
     saveDir.mkdirs()

     return embeddedServer(CIO, port = port, host = "0.0.0.0") {
         install(ContentNegotiation) { json() }
         routing {
             get("/hello") {
                 call.respond(HelloResponse(System.getenv("COMPUTERNAME") ?: "Desktop"))
             }
             put("/upload/{name}") {
                 val safeName = File(call.parameters["name"] ?: "unnamed").name
                 val target = File(saveDir, safeName)
                 val total = call.request.contentLength() ?: -1L
                 val channel = call.receiveChannel()

                 var received = 0L
                 var lastEmit = 0L

                 try {
                     withContext(Dispatchers.IO) {
                         FileOutputStream(target).use { out ->
                             val buf = ByteArray(64 * 1024)
                             while (true) {
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
                         }
                     }
                     onProgress(TransferProgress(safeName, received, total, finished = true))
                     onLog("Received $safeName ($received bytes)")
                     call.respond(HttpStatusCode.OK)
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