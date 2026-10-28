package br.com.joaolds.dnslocal.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

/** Local simulated UDP/TCP DNS pair; never queries public resolvers. */
fun tcpFallbackSmoke() {
    val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)) as Inet4Address
    val query = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        1, 'a'.code.toByte(), 0, 0, 1, 0, 1)
    val good = requireNotNull(DnsPacketCodec.servfail(query)).also { it[3] = 0 }
    val tc = good.copyOf().also { it[2] = (it[2].toInt() or 2).toByte() }
    for (mode in listOf("valid", "invalid-id", "invalid-question", "oversize", "truncated", "denied", "udp-ok")) {
        val tcpAllowed = mode != "denied"
        val udpResponse = if (mode == "udp-ok") good else tc
        val errors = AtomicReference<Throwable?>()
        var udpProtected = 0
        var tcpProtected = 0
        ServerSocket(0, 5, loopback).use { tcpServer ->
            tcpServer.soTimeout = 1500
            DatagramSocket(tcpServer.localPort, loopback).use { udpServer ->
                udpServer.soTimeout = 1500
                val udpThread = Thread {
                    try {
                        val pkt = DatagramPacket(ByteArray(1472), 1472)
                        udpServer.receive(pkt)
                        check(pkt.data.copyOf(pkt.length).contentEquals(query))
                        udpServer.send(DatagramPacket(udpResponse, udpResponse.size, pkt.socketAddress))
                    } catch (t: Throwable) { errors.compareAndSet(null, t) }
                }.apply { start() }
                val tcpThread = if (tcpAllowed && mode != "udp-ok") Thread {
                    try {
                        tcpServer.accept().use { sock ->
                            sock.soTimeout = 1500
                            val input = sock.getInputStream()
                            val prefix = ByteArray(2)
                            var prefixOffset = 0
                            while (prefixOffset < 2) {
                                val count = input.read(prefix, prefixOffset, 2 - prefixOffset)
                                check(count > 0)
                                prefixOffset += count
                            }
                            val requestLength = ((prefix[0].toInt() and 255) shl 8) or (prefix[1].toInt() and 255)
                            check(requestLength == query.size)
                            val request = ByteArray(requestLength)
                            var offset = 0
                            while (offset < request.size) {
                                val count = input.read(request, offset, request.size - offset)
                                check(count > 0)
                                offset += count
                            }
                            check(request.contentEquals(query))
                            val reply = when (mode) {
                                "invalid-id" -> good.copyOf().also { it[0] = 0x30 }
                                "invalid-question" -> good.copyOf().also { it[13] = 'b'.code.toByte() }
                                "truncated" -> tc
                                else -> good
                            }
                            val sendLength = if (mode == "oversize") 1473 else reply.size
                            val output = sock.getOutputStream()
                            output.write(byteArrayOf((sendLength ushr 8).toByte(), sendLength.toByte()))
                            if (mode != "oversize") {
                                output.write(reply, 0, 2)
                                output.flush()
                                output.write(reply, 2, reply.size - 2)
                            }
                            output.flush()
                        }
                    } catch (t: Throwable) { errors.compareAndSet(null, t) }
                }.apply { start() } else null
                val client = DnsUpstreamClient(
                    protectSocket = { udpProtected++; true }, timeoutMillis = 1200,
                    protectTcpSocket = { tcpProtected++; tcpAllowed }
                )
                val result = client.resolve(query, loopback, tcpServer.localPort)
                when (mode) {
                    "valid", "udp-ok" -> check(result?.contentEquals(good) == true) { "Mode $mode: expected full answer" }
                    else -> check(result == null) { "Mode $mode: must reject invalid TCP result" }
                }
                check(udpProtected == 1)
                check(tcpProtected == if (mode == "udp-ok") 0 else 1)
                udpThread.join(2000)
                tcpThread?.join(2000)
                check(!udpThread.isAlive && tcpThread?.isAlive != true)
                errors.get()?.let { throw it }
            }
        }
    }
    println("OK: TCP-on-TC, protected sockets, invalid replies and UDP-only cases")
}
