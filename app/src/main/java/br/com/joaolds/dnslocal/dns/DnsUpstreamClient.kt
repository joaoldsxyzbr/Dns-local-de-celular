package br.com.joaolds.dnslocal.dns

import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * DNS over UDP with TCP retry only for truncated responses (TC=1).
 * Both transports must be protected with VpnService.protect before connecting.
 * A missing TCP protection callback fails closed rather than bypassing the VPN.
 */
internal class DnsUpstreamClient(
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val timeoutMillis: Int = 1500,
    private val protectTcpSocket: (Socket) -> Boolean = { false }
) {
    init { require(timeoutMillis in 100..10000) }

    /** No implicit resolver; TCP retry targets the same configured IP and port. */
    fun resolve(request: ByteArray, server: Inet4Address, port: Int = 53): ByteArray? {
        if (request.size !in 12..1472 || port !in 1..65535 ||
            (request[2].toInt() and 0x80 != 0)) return null
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        return try {
            DatagramSocket().use { socket ->
                if (!protectSocket(socket)) return null
                socket.connect(server, port)
                socket.send(DatagramPacket(request, request.size))
                val buf = ByteArray(1472)
                while (true) {
                    val timeout = remainingMillis(deadline)
                    if (timeout <= 0) return null
                    socket.soTimeout = timeout
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val response = buf.copyOf(packet.length)
                    if (!DnsPacketCodec.isMatchingResponse(request, response)) continue
                    if (response[2].toInt() and 0x02 != 0) {
                        return resolveOverTcp(request, server, port, deadline)
                    }
                    return response
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun resolveOverTcp(
        request: ByteArray, server: Inet4Address, port: Int, deadline: Long
    ): ByteArray? = Socket().use { socket ->
        if (!protectTcpSocket(socket)) return null
        val timeout = remainingMillis(deadline)
        if (timeout <= 0) return null
        socket.connect(InetSocketAddress(server, port), timeout)
        val out = socket.getOutputStream()
        out.write(byteArrayOf((request.size ushr 8).toByte(), request.size.toByte()))
        out.write(request)
        out.flush()
        val input = socket.getInputStream()
        val prefix = ByteArray(2)
        if (!readFully(socket, input, prefix, deadline)) return null
        val size = ((prefix[0].toInt() and 255) shl 8) or (prefix[1].toInt() and 255)
        if (size !in 12..1472) return null // Current TUN MTU cannot return more.
        val response = ByteArray(size)
        if (!readFully(socket, input, response, deadline)) return null
        response.takeIf {
            DnsPacketCodec.isMatchingResponse(request, it) &&
                it[2].toInt() and 0x02 == 0
        }
    }

    private fun readFully(socket: Socket, input: InputStream, bytes: ByteArray, deadline: Long): Boolean {
        var read = 0
        while (read < bytes.size) {
            val timeout = remainingMillis(deadline)
            if (timeout <= 0) return false
            socket.soTimeout = timeout
            val count = input.read(bytes, read, bytes.size - read)
            if (count < 0) return false
            read += count
        }
        return true
    }

    private fun remainingMillis(deadline: Long): Int {
        val ns = deadline - System.nanoTime()
        if (ns <= 0) return 0
        return ((ns + 999_999) / 1_000_000).toInt().coerceAtLeast(1)
    }
}
