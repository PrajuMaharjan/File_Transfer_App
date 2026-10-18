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
import  io.ktor.util.cio.*
import  io.ktor.utils.io.*
import  kotlinx.serialization.Serializable
import  java.io.File

 @Serializable
 data class HelloResponse(val name: String)

 fun startReceiver(
     port:Int=8080,
     saveDir: File,
     onLog:(String)->Unit
 ): EmbeddedServer<*,*>{
     saveDir.mkdirs()

     return  embeddedServer(CIO,port=port, host = "0.0.0.0"){
         install(ContentNegotiation){json()}
         routing {
             get("/hello") {
                 call.respond(HelloResponse(System.getenv("COMPUTERNAME") ?: "Desktop"))
             }
             put("/upload/{name}") {
                 val safeName = File(call.parameters["name"] ?: "unnamed").name
                 val target = File(saveDir, safeName)
                 call.receiveChannel().copyAndClose(target.writeChannel())
                 onLog("Received $safeName (${target.length()} bytes)")
                 call.respond(HttpStatusCode.OK)
             }
         }
     }.start(wait=false)
 }