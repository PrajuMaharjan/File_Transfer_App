package com.prajumaharjan.filetransfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address

class NsDiscovery(context: Context){
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener : NsdManager.DiscoveryListener?=null
    private var statusCallback : (String) -> Unit = {}

    fun start(onFound:(Device)->Unit, onLost:(String)->Unit, onStatus:(String)->Unit) {
        statusCallback = onStatus
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {main.post{onStatus("Discovery started") } }
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {main.post{onStatus("Discovery failed, code $code") } }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post { onStatus("Found ${info.serviceName}, resolving") }
                synchronized(queue) { queue.add(info) }
                resolveNext(onFound)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { onLost(info.serviceName) }
            }
        }
        listener = l
        nsd.discoverServices("_filetransfer._tcp.", NsdManager.PROTOCOL_DNS_SD,l)
    }

    private fun resolveNext(onFound: (Device) -> Unit){
        val info = synchronized(queue){
            if(resolving || queue.isEmpty()) return
            resolving = true
            queue.removeFirst()
        }
        @Suppress("DEPRECATION")
        nsd.resolveService(info,object:NsdManager.ResolveListener{
            override fun onResolveFailed(i: NsdServiceInfo, code: Int) {
                main.post{ statusCallback("Resolve failed, code $code") }
                finish(onFound)
            }

            override fun onServiceResolved(i: NsdServiceInfo) {
                val host = i.host
                if(host is Inet4Address){
                    fun attr(k: String): String? = i.attributes[k]?.let {String(it,Charsets.UTF_8) }
                    val d = Device(
                        name = i.serviceName,
                        host = host.hostAddress ?: "",
                        port = i.port,
                        id = attr("id") ?: "",
                        displayName = attr("dn") ?: i.serviceName,
                        avatar = try{
                            AvatarColor.valueOf(attr("av") ?: "")
                        }catch (e: Exception){
                            AvatarColor.GREY
                        }
                    )
                    main.post { onFound(d) }
                }
                finish(onFound)
            }
        })
    }

    private fun finish(onFound: (Device) -> Unit){
        synchronized(queue){resolving = false }
        resolveNext(onFound)
    }

    fun stop(){
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener=null
    }
}