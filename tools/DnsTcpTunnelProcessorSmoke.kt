package br.com.joaolds.dnslocal.dns

/** Synthetic session-map tests: no network and no Android runtime. */
fun main() {
    var time = 0L
    var sequence = 200L
    val bridge = DnsTcpTunnelProcessor(2, { null }, maximumSessions = 2,
        idleNanos = 1_000L, nowNanos = { time }, initialSequence = { ++sequence })
    fun seg(port: Int, number: Long = 100L, flags: Int = DnsTcpSession.SYN, ip: Int = 1) =
        DnsTcpPacketCodec.Segment(ip, 2, port, number, 0L, flags, byteArrayOf())
    check(bridge.handle(seg(5001)).size == 1)
    check(bridge.handle(seg(5002)).size == 1)
    check(bridge.currentSessionCount() == 2)
    check(bridge.handle(seg(5001)).size == 1) // Duplicate SYN reuses initial sequence.
    check(bridge.handle(seg(5003, flags = DnsTcpSession.ACK)).isEmpty())
    check(bridge.currentSessionCount() == 2)
    bridge.handle(seg(5003)) // Evict least recently used entry, only after a new packet.
    check(bridge.currentSessionCount() == 2)
    check(bridge.handle(seg(5002, flags = DnsTcpSession.ACK)).isEmpty())
    check(bridge.handle(seg(5001)).size == 1)
    val previous = sequence
    check(bridge.handle(seg(5001, number = 123456)).size == 1)
    check(sequence == previous + 1) // Fresh SYN replaces stale state on reused tuple.
    bridge.handle(seg(5001, ip = 3)) // Source IP separates equal source ports.
    check(bridge.currentSessionCount() == 2)
    time += 1001L
    check(bridge.currentSessionCount() == 2) // No background expiry work.
    check(bridge.handle(seg(5009)).size == 1)
    check(bridge.currentSessionCount() == 1)
    check(bridge.handle(seg(5009, flags = DnsTcpSession.RST)).isEmpty())
    check(bridge.currentSessionCount() == 0)
    check(bridge.handle(seg(5009, flags = DnsTcpSession.ACK, ip = 33)).isEmpty())
    println("OK: TCP bounded session map, LRU, idle expiration, SYN reuse and RST")
}
