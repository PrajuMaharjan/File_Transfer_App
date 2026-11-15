package com.prajumaharjan.filetransfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class ReceiverService : Service() {
    companion object {
        const val ACTION_ACCEPT = "com.prajumaharjan.filetransfer.ACCEPT"
        const val ACTION_REJECT = "com.prajumaharjan.filetransfer.REJECT"
        const val ACTION_STOP = "com.prajumaharjan.filetransfer.STOP"
        private const val CH_RUN = "receiving"
        private const val CH_OFFER = "offers"
        private const val ID_RUN = 1
        private const val ID_OFFER = 2
    }

    private var started = false
    private var server: EmbeddedServer<*,*>? = null
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var lock: WifiManager.MulticastLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> ReceiverHub.pending?.second?.complete(true)
            ACTION_REJECT -> ReceiverHub.pending?.second?.complete(false)
            ACTION_STOP -> stopSelf()
            else -> startServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer(){
        createChannels()
        if(Build.VERSION.SDK_INT >=29){
            ServiceCompat.startForeground(
                this,ID_RUN,runNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }else{
            startForeground(ID_RUN,runNotification())
        }

        if(started) return
        val profile = AndroidProfileStore(this).load()
        if (profile == null) { stopSelf(); return }
        started = true

        scope.launch(Dispatchers.IO) {
            try{
                server = startReceiver(
                    port = 8080,
                    sink = MediaStoreSink(this@ReceiverService),
                    profile = profile,
                    cancelFlag = ReceiverHub.cancelFlag,
                    onProgress = { ReceiverHub.transfer = it },
                    onOffer = { offer ->
                        val decision = CompletableDeferred<Boolean>()
                        ReceiverHub.pending = offer to decision
                        try {
                            decision.await()
                        } finally {
                            ReceiverHub.pending = null
                        }
                    },
                    onLog = { ReceiverHub.logs.add(it) }
                )
                ReceiverHub.logs.add("Listening on port 8080")
                advertise(profile)
                ReceiverHub.running = true
            } catch (e: Exception) {
                ReceiverHub.logs.add("Receiver failed: ${e.message}")
                started = false
                stopSelf()
            }
        }

        scope.launch {
            val nm = getSystemService(NotificationManager::class.java)
            snapshotFlow { ReceiverHub.pending }.collect { p ->
                if (p != null) nm.notify(ID_OFFER, offerNotification(p.first))
                else nm.cancel(ID_OFFER)
            }
        }
    }

    private fun advertise(profile: Profile){
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        lock = wifi.createMulticastLock("filetransfer").apply {
            setReferenceCounted(false)
            acquire()
        }
        val info = NsdServiceInfo().apply {
            serviceName = "FT-" + profile.id.take(8)
            serviceType = "_filetransfer._tcp"
            port = 8080
            setAttribute("id",profile.id)
            setAttribute("dn",profile.name)
            setAttribute("av",profile.avatar.name)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {
                ReceiverHub.logs.add("Advertising as ${i.serviceName}")
            }
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {
                ReceiverHub.logs.add("Advertising failed, code $code")
            }
            override fun onServiceUnregistered(i: NsdServiceInfo) { runCatching { lock?.release() } }
            override fun onUnregistrationFailed(i: NsdServiceInfo,code: Int) { runCatching { lock?.release() }}
        }
        regListener = l
        val m = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = m
        m.registerService(info,NsdManager.PROTOCOL_DNS_SD, l)
    }
    private fun createChannels(){
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RUN, "Receiving", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_OFFER, "Incoming files", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,0,
        Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun runNotification(): Notification =
        NotificationCompat.Builder(this,CH_RUN)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Ready to receive files")
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()

    private fun offerNotification(o: OfferRequest): Notification{
        fun act(a: String,code: Int) = PendingIntent.getService(
            this,code,
            Intent(this, ReceiverService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this,CH_OFFER)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("${o.senderName.ifBlank { "Someone" }} wants to send a file")
                .setContentText("${o.name} (${o.size / 1024} KB)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .addAction(0,"Accept",act(ACTION_ACCEPT,1))
                .addAction(0,"Reject",act(ACTION_REJECT,2))
                .build()
    }

    override fun onDestroy() {
        ReceiverHub.running = false
        ReceiverHub.pending?.second?.complete(false)
        val l =regListener
        if(l != null) runCatching { nsd?.unregisterService(l) }
        runCatching { server?.stop(300, 1000) }
        if(l == null) runCatching { lock?.release() }
        scope.cancel()
        super.onDestroy()
    }
}