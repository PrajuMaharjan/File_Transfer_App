package com.prajumaharjan.filetransfer

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

private const val TYPE="_filetransfer._tcp.local."

private fun pickLANAddress(onLog:(String) -> Unit): InetAddress?{
    val skip=listOf("virtual","vmware","vethernet","vpn","tap-","hyper-v","bluetooth","wsl","loopback")
    val found = NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { n-> n.inetAddresses.toList()
                                    .filterIsInstance<Inet4Address>()
                                    .filter { it.isSiteLocalAddress }
                                    .map{ n to it}
                    }

    val clean = found.filterNot { (n,_) -> skip.any{n.displayName.contains(it,ignoreCase=true) } }
    val wifi = clean.firstOrNull{ (n,_) ->
        listOf("wi-fi","wifi","wireless","wlan","802.11").any{n.displayName.contains(it, ignoreCase = true) }
    }
    return (wifi ?: clean.firstOrNull() ?: found.firstOrNull())?.second
}

class DesktopDiscovery{
    private var jmdns: JmDNS?=null

    fun start(selfName: String,
              profile: Profile,
              port:Int,
              onFound:(Device)->Unit,
              onLost:(String)->Unit,
              onLog:(String)-> Unit
    ){
        val addr=pickLANAddress(onLog)
        onLog("mDNS bound to ${addr?.hostAddress ?: "default adapter"}")
        val j=addr?.let { JmDNS.create(it,selfName) } ?: JmDNS.create(selfName)
        jmdns=j
        j.registerService(
            ServiceInfo.create(
                TYPE, selfName, port, 0, 0,
                mapOf(
                    "id" to profile.id,
                    "dn" to profile.name,
                    "av" to profile.avatar.name
                )
            )
        )
        j.addServiceListener(TYPE,object : ServiceListener {
            override fun serviceAdded(e: ServiceEvent) {
                j.requestServiceInfo(e.type,e.name,true)
            }
            override fun serviceRemoved(e: ServiceEvent)=onLost(e.name)
            override fun serviceResolved(e: ServiceEvent){
                if(e.name==selfName) return
                val ip=e.info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                onFound(
                    Device(
                        name = e.name,
                        host = ip,
                        port = e.info.port,
                        id = e.info.getPropertyString("id") ?: "",
                        displayName = e.info.getPropertyString("dn") ?: e.name,
                        avatar = try {
                            AvatarColor.valueOf(e.info.getPropertyString("av") ?: "")
                        }catch (ex: Exception){
                            AvatarColor.GREY
                        }
                    )
                )
            }
        })
    }

    fun stop(){
        jmdns?.unregisterAllServices()
        jmdns?.close()
    }
}