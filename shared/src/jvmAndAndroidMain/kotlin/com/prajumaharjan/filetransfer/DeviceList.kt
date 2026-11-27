package com.prajumaharjan.filetransfer

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DeviceList(private val scope: CoroutineScope) {
    val devices = mutableStateListOf<Device>()
    var stuck by mutableStateOf(false)
    private var emptySince = System.currentTimeMillis()
    private val misses = mutableMapOf<String, Int>()

    fun onFound(d: Device, selfId: String) {
        scope.launch {
            val h: HelloResponse? = withContext(Dispatchers.IO) {
                var r = fetchHello(d.host, d.port)
                if (r == null) {
                    delay(800)
                    r = fetchHello(d.host, d.port)
                }
                r
            }
            if (h == null) return@launch
            val full = d.withHello(h)
            if (full.id == selfId) return@launch
            devices.removeAll {
                it.name == full.name ||
                        (full.id.isNotBlank() && it.id == full.id) ||
                        (it.host == full.host && it.port == full.port)
            }
            devices.add(full)
        }
    }

    fun onLost(name: String) {
        scope.launch { devices.removeAll { it.name == name } }
    }

    // call from LaunchedEffect(Unit)
    suspend fun runPruner() {
        while (true) {
            delay(5000)
            for (d in devices.toList()) {
                val ok = withContext(Dispatchers.IO) { fetchHello(d.host, d.port) } != null
                val key = d.host + ":" + d.port
                if (ok) {
                    misses.remove(key)
                } else {
                    val n = (misses[key] ?: 0) + 1
                    if (n >= 2) { devices.remove(d); misses.remove(key) } else misses[key] = n
                }
            }
        }
    }

    suspend fun runStuckWatcher(selfId: String, afterMs:Long=15_000) {
        while (true) {
            delay(1000)
            val now=System.currentTimeMillis()
            if (devices.isNotEmpty()) {
                emptySince=now
                stuck=false
            } else {
                val empty = now - emptySince
                if(empty>5_000) scanNow(selfId)
                stuck = empty>afterMs
            }
        }
    }

    private var scanning = false

    fun scanNow(selfId: String) {
        if (scanning) return
        scanning = true
        scope.launch {
            try {
                val found = scanSubnets()
                for ((host, hello) in found) {
                    val d = Device(name = "$host:8080", host = host, port = 8080).withHello(hello)
                    if (d.id == selfId) continue
                    devices.removeAll {
                        (d.id.isNotBlank() && it.id == d.id) || (it.host == d.host && it.port == d.port)
                    }
                    devices.add(d)
                }
            } finally {
                scanning = false
            }
        }
    }
}