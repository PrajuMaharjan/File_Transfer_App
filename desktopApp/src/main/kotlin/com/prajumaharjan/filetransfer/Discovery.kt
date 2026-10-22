package com.prajumaharjan.filetransfer

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

data class Device(val name: String,val host: String,val port: Int)

private const val TYPE="_filetransfer._tcp.local."

private fun pickLANAddress(): InetAddress?{
    val skip=listOf("virtual","vmware","vethernet","vpn","tap","hyper-v","bluetooth")
    val usable= NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .filterNot { n->skip.any{n.displayName.contains(it, ignoreCase = true) } }
    val wifi=usable.firstOrNull{n->
        listOf("wi-fi","wireless","wlan").any{n.displayName.contains(it, ignoreCase = true) }
    }
    return (wifi ?: usable.firstOrNull())
        ?.inetAddresses?.toList()
        ?.firstOrNull{ it is Inet4Address && it.isSiteLocalAddress }
}

class DesktopDiscovery{
    private var jmdns: JmDNS?=null

    fun start(selfName: String,
              port:Int,
              onFound:(Device)->Unit,
              onLost:(String)->Unit,
              onLog:(String)-> Unit
    ){
        val addr=pickLANAddress()
        onLog("mDNS bound to ${addr?.hostAddress ?: "default adapter"}")
        val j=addr?.let { JmDNS.create(it,selfName) } ?: JmDNS.create(selfName)
        jmdns=j
        j.registerService(ServiceInfo.create(TYPE, selfName, port, "FileTransfer"))
        j.addServiceListener(TYPE,object : ServiceListener {
            override fun serviceAdded(e: ServiceEvent) {
                j.requestServiceInfo(e.type,e.name,true)
            }
            override fun serviceRemoved(e: ServiceEvent)=onLost(e.name)
            override fun serviceResolved(e: ServiceEvent){
                if(e.name==selfName) return
                val ip=e.info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                onFound(Device(e.name,ip,e.info.port))
            }
        })
    }

    fun stop(){
        jmdns?.unregisterAllServices()
        jmdns?.close()
    }
}