package br.com.joaolds.dnslocal.dns

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.SocketTimeoutException

/**
 * UDP/53 proof of concept. The caller MUST pass VpnService.protect(socket) in
 * production: no fallback to an unprotected socket is permitted.
 */
internal class DnsUpstreamClient(
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val timeoutMillis: Int = 1500
) {
    init { require(timeoutMillis in 100..10000) }

    /** IP literal only; no hostname lookup or hidden secondary resolver. */
    fun resolve(request: ByteArray, server: Inet4Address, port: Int = 53): ByteArray? {
        if (request.size !in 12..1472 || port !in 1..65535 ||
            (request[2].toInt() and 0x80 != 0)) return null
        return try {
            DatagramSocket().use { socket ->
                if (!protectSocket(socket)) return null
                socket.connect(server, port) // Only accepts datagrams from this upstream.
                val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
                socket.send(DatagramPacket(request, request.size))
                val buf = ByteArray(1472)
                while (true) {
                    val remainingNs = deadline - System.nanoTime()
                    if (remainingNs <= 0) return null
                    socket.soTimeout = ((remainingNs + 999_999L) / 1_000_000L).toInt()
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val response = buf.copyOf(packet.length)
                    if (DnsPacketCodec.isMatchingResponse(request, response)) return response
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
}
