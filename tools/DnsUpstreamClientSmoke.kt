package br.com.joaolds.dnslocal.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicReference

fun upstreamSmoke() {
    val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)) as Inet4Address
    val query = byteArrayOf(
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0,
        0x01, 'a'.code.toByte(), 0, 0, 1, 0, 1
    )
    val unexpected = AtomicReference<Throwable?>()
    var protectCalls = 0
    DatagramSocket(0, loopback).use { server ->
        server.soTimeout = 1500
        val serverThread = Thread {
            try {
                val received = DatagramPacket(ByteArray(1500), 1500)
                server.receive(received)
                check(received.data.copyOf(received.length).contentEquals(query))
                val valid = requireNotNull(DnsPacketCodec.servfail(query))
                val wrong = valid.copyOf().also { it[1] = 0x77 }
                server.send(DatagramPacket(wrong, wrong.size, received.socketAddress))
                server.send(DatagramPacket(valid, valid.size, received.socketAddress))
            } catch (t: Throwable) {
                unexpected.set(t)
            }
        }.apply { start() }
        val client = DnsUpstreamClient(protectSocket = { protectCalls++; true }, timeoutMillis = 900)
        val response = client.resolve(query, loopback, server.localPort)
        serverThread.join(2000)
        check(!serverThread.isAlive) { "Local test DNS server did not finish" }
        unexpected.get()?.let { throw it }
        check(protectCalls == 1)
        check(response?.get(1) == 0x34.toByte()) { "Expected valid reply after wrong transaction ID" }
    }
    var callsDenied = 0
    val denied = DnsUpstreamClient(protectSocket = { callsDenied++; false })
    check(denied.resolve(query, loopback, 53) == null)
    check(callsDenied == 1)
    check(DnsUpstreamClient({ true }).resolve(byteArrayOf(1, 2), loopback, 53) == null)
    println("OK: protected UDP upstream transport smoke checks passed")
}
