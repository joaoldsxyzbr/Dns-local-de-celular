package br.com.joaolds.dnslocal.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import br.com.joaolds.dnslocal.MainActivity
import br.com.joaolds.dnslocal.R
import br.com.joaolds.dnslocal.dns.DnsPacketCodec
import br.com.joaolds.dnslocal.dns.DnsTunnelProcessor
import br.com.joaolds.dnslocal.dns.DnsUpstreamClient
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Experimental DNS-only VPN. No default route is installed: only traffic to
 * the configured virtual DNS IP (198.18.0.1/32) enters the TUN interface.
 * Real-device behavior, especially Private DNS and IPv6, is NOT yet verified.
 */
class LocalDnsVpnService : VpnService() {
    companion object {
        const val ACTION_START = "br.com.joaolds.dnslocal.action.START_DNS"
        const val ACTION_STATUS = "br.com.joaolds.dnslocal.action.DNS_STATUS"
        const val EXTRA_PRIMARY = "primaryIpv4"
        const val DNS_IP = "198.18.0.1"
        private const val LOCAL_IP = "198.18.0.2"
        private const val CHANNEL_ID = "dns_local_connection"
        private const val NOTIFICATION_ID = 42
        @Volatile var status: Status = Status.OFF
            private set
    }

    enum class Status { OFF, STARTING, RUNNING, ERROR }

    @Volatile private var running = false
    private val generation = AtomicLong(0L)
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var workers: ThreadPoolExecutor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        stopTunnel()
        publish(Status.STARTING)
        try {
            createForegroundNotification()
            val server = parseServer(intent.getStringExtra(EXTRA_PRIMARY))
                ?: throw IllegalArgumentException("Invalid primary IPv4 DNS")
            val dnsAddress = checkNotNull(DnsPacketCodec.parseIpv4Address(DNS_IP))
            val descriptor = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress(LOCAL_IP, 32)
                .addDnsServer(DNS_IP)
                .addRoute(DNS_IP, 32)
                .establish() ?: throw IllegalStateException("VPN was not authorized")
            tun = descriptor
            val upstream = DnsUpstreamClient(protectSocket = { socket -> protect(socket) })
            val processor = DnsTunnelProcessor(dnsAddress) {
                upstream.resolve(it, server)
            }
            val pool = ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(48),
                ThreadPoolExecutor.AbortPolicy()
            )
            workers = pool
            running = true
            val epoch = generation.incrementAndGet()
            reader = Thread({ pumpPackets(descriptor, processor, pool, dnsAddress, epoch) }, "dns-tun-reader")
                .apply { start() }
            publish(Status.RUNNING)
        } catch (_: Exception) {
            stopTunnel()
            publish(Status.ERROR)
            stopSelf()
        }
        return START_NOT_STICKY // Never restart without explicit user action.
    }

    private fun pumpPackets(
        descriptor: ParcelFileDescriptor,
        processor: DnsTunnelProcessor,
        pool: ThreadPoolExecutor,
        dnsAddress: Int,
        epoch: Long
    ) {
        val output = FileOutputStream(descriptor.fileDescriptor)
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val buffer = ByteArray(1500)
                while (isCurrent(epoch) && !Thread.currentThread().isInterrupted) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    val packet = buffer.copyOf(count)
                    val task = Runnable {
                        val answer = processor.reply(packet, packet.size)
                        if (answer != null && isCurrent(epoch)) writePacket(output, answer, epoch)
                    }
                    try {
                        pool.execute(task)
                    } catch (_: RejectedExecutionException) {
                        // Bounded workload: reject overload rather than accumulating DNS jobs.
                        val query = DnsPacketCodec.decodeQuery(packet, count, dnsAddress)
                        if (query != null) {
                            val error = DnsPacketCodec.servfail(query.dnsPayload)
                            val reply = error?.let { DnsPacketCodec.encodeReply(query, it) }
                            if (reply != null && isCurrent(epoch)) writePacket(output, reply, epoch)
                        }
                    }
                }
            }
        } catch (_: IOException) {
            // Closing TUN during stop/revoke also interrupts a blocking read.
        } finally {
            if (isCurrent(epoch)) {
                publish(Status.ERROR)
                stopSelf()
            }
        }
    }

    private fun isCurrent(epoch: Long): Boolean = running && generation.get() == epoch

    private fun writePacket(output: FileOutputStream, packet: ByteArray, epoch: Long) {
        synchronized(output) {
            if (isCurrent(epoch)) {
                try {
                    output.write(packet)
                } catch (_: IOException) {
                    // Closed tunnel: do not retry in a busy loop.
                }
            }
        }
    }

    private fun createForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_description))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun parseServer(value: String?): Inet4Address? {
        val ip = DnsPacketCodec.parseIpv4Address(value ?: return null) ?: return null
        if (value == DNS_IP || value == LOCAL_IP || value == "255.255.255.255") return null
        val bytes = byteArrayOf(
            (ip ushr 24).toByte(), (ip ushr 16).toByte(),
            (ip ushr 8).toByte(), ip.toByte()
        )
        val address = InetAddress.getByAddress(bytes) as Inet4Address
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isMulticastAddress || address.isLinkLocalAddress) return null
        return address
    }

    private fun publish(next: Status) {
        status = next
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
    }

    private fun stopTunnel() {
        running = false
        generation.incrementAndGet()
        workers?.shutdownNow()
        workers = null
        try { tun?.close() } catch (_: IOException) {}
        tun = null
        reader?.interrupt()
        reader = null
    }

    override fun onRevoke() {
        stopTunnel()
        publish(Status.OFF)
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
        if (status != Status.ERROR) publish(Status.OFF)
        super.onDestroy()
    }

}
