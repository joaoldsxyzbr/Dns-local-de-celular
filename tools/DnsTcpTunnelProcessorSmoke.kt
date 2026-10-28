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
    endToEndTcpTunSmoke()
    println("OK: TCP bounded session map, LRU, idle expiration, SYN reuse and RST")
}

/** Synthetic IPv4/TCP frames enter via the real packet codec, not a mock transport. */
private fun endToEndTcpTunSmoke() {
    val clientIp = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.2"))
    val dnsIp = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.1"))
    val dnsQuestion = byteArrayOf(
        0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 'a'.code.toByte(), 0, 0, 1, 0, 1
    )
    val dnsResponse = requireNotNull(DnsPacketCodec.servfail(dnsQuestion))
    val framedRequest = byteArrayOf(0, dnsQuestion.size.toByte()) + dnsQuestion
    var upstreamCalls = 0
    val processor = DnsTcpTunnelProcessor(dnsIp, {
        upstreamCalls++
        check(it.contentEquals(dnsQuestion))
        dnsResponse
    }, initialSequence = { 2000L })

    fun send(sequence: Long, acknowledgment: Long, flags: Int, payload: ByteArray = byteArrayOf()): List<ByteArray> {
        val original = DnsTcpPacketCodec.Segment(clientIp, dnsIp, 53001, sequence, acknowledgment, flags, payload)
        val outgoing = requireNotNull(DnsTcpPacketCodec.encodeReply(original, sequence, acknowledgment, flags, payload))
        val incoming = outgoing.copyOf()
        outgoing.copyInto(incoming, 12, 16, 20)
        outgoing.copyInto(incoming, 16, 12, 16)
        outgoing.copyInto(incoming, 20, 22, 24)
        outgoing.copyInto(incoming, 22, 20, 22)
        rewriteTcpTunChecksums(incoming)
        return processor.reply(incoming, incoming.size)
    }

    val synAck = send(1000, 0, DnsTcpSession.SYN).single()
    check(synAck.size == 44) // SYN-ACK advertises an MSS option.
    check((synAck[33].toInt() and 255) == (DnsTcpSession.SYN or DnsTcpSession.ACK))
    check(processor.currentSessionCount() == 1)
    check(send(1001, 2001, DnsTcpSession.ACK).isEmpty())
    val replies = send(1001, 2001, DnsTcpSession.ACK or DnsTcpSession.PSH, framedRequest)
    check(replies.size == 2 && upstreamCalls == 1)
    val first = replies[0]
    check(first.copyOfRange(40, first.size).contentEquals(
        byteArrayOf(0, dnsResponse.size.toByte()) + dnsResponse
    ))
    check((replies[1][33].toInt() and 255) == (DnsTcpSession.FIN or DnsTcpSession.ACK))
    check(send(1001, 2001, DnsTcpSession.ACK or DnsTcpSession.PSH, framedRequest).size == 2)
    check(upstreamCalls == 1) // Duplicate segment must not query upstream again.
    check(send(1001 + framedRequest.size.toLong(), 2001 + dnsResponse.size + 3L,
        DnsTcpSession.ACK).isEmpty())
    check(processor.currentSessionCount() == 0)
    println("OK: IPv4/TCP TUN SYN, query, reply, retransmission, FIN, checksum")
}

private fun rewriteTcpTunChecksums(packet: ByteArray) {
    fun checksum(data: ByteArray): Int {
        var sum = 0L
        for (index in data.indices step 2) {
            val high = data[index].toInt() and 255
            val low = if (index + 1 < data.size) data[index + 1].toInt() and 255 else 0
            sum += ((high shl 8) or low).toLong()
        }
        while (sum > 65535L) sum = (sum and 65535L) + (sum ushr 16)
        return sum.inv().toInt() and 65535
    }
    fun set16(at: Int, value: Int) {
        packet[at] = (value ushr 8).toByte()
        packet[at + 1] = value.toByte()
    }
    set16(10, 0)
    set16(10, checksum(packet.copyOfRange(0, 20)))
    set16(36, 0)
    val tcpLength = packet.size - 20
    val pseudo = packet.copyOfRange(12, 20) +
        byteArrayOf(0, 6, (tcpLength ushr 8).toByte(), tcpLength.toByte()) +
        packet.copyOfRange(20, packet.size)
    val value = checksum(pseudo)
    set16(36, if (value == 0) 65535 else value)
}
