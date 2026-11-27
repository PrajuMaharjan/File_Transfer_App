package com.prajumaharjan.filetransfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

private val skipNames = listOf("virtual", "vmware", "vethernet", "vpn", "tap-", "hyper-v", "bluetooth", "wsl", "loopback")

// /24 prefixes ("192.168.1") of the local Wi-Fi/LAN interfaces
fun localPrefixes(): List<Pair<String, String>> =
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .filterNot { n -> skipNames.any { n.displayName.contains(it, ignoreCase = true) } }
        .flatMap { n ->
            n.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .map { a ->
                    val parts = a.hostAddress!!.split(".")
                    parts.take(3).joinToString(".") to a.hostAddress!!
                }
        }

// returns (host, hello) for every address that answers /hello
suspend fun scanSubnets(port: Int = 8080): List<Pair<String, HelloResponse>> =
    withContext(Dispatchers.IO) {
        val gate = Semaphore(32)
        val prefixes = localPrefixes()
        coroutineScope {
            prefixes.flatMap { (prefix, self) ->
                (1..254).map { i ->
                    val host = "$prefix.$i"
                    async {
                        if (host == self) null
                        else gate.withPermit { fetchHello(host, port, 400) }?.let { host to it }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }