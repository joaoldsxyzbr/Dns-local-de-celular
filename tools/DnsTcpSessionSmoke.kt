package br.com.joaolds.dnslocal.dns

/** Simulated packet events; no sockets, no Android and no polling. */
private fun packet(seq: Long, ack: Long, flags: Int, payload: ByteArray = byteArrayOf()) =
    DnsTcpPacketCodec.Segment(1, 2, 51515, seq, ack, flags, payload)

fun main() {
    val query = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 'a'.code.toByte(), 0, 0, 1, 0, 1)
    val answer = query.copyOf().also { it[2] = 0x81.toByte() }
    val wire = byteArrayOf(0, query.size.toByte()) + query
    var calls = 0
    val flow = DnsTcpSession(500) { request ->
        calls++
        check(request.contentEquals(query))
        answer
    }
    val syn = flow.accept(packet(1000, 0, DnsTcpSession.SYN)).single()
    check(syn.sequence == 500L && syn.acknowledgment == 1001L && syn.flags == 0x12)
    check(flow.accept(packet(1000, 0, DnsTcpSession.SYN)).single().sequence == 500L)
    check(flow.accept(packet(1001, 501, DnsTcpSession.ACK)).isEmpty())
    val partial = flow.accept(packet(1001, 501, 0x18, wire.copyOfRange(0, 5))).single()
    check(partial.sequence == 501L && partial.acknowledgment == 1006L && partial.flags == 0x10)
    val outOfOrder = flow.accept(packet(1020, 501, 0x18, wire.copyOfRange(5, wire.size))).single()
    check(outOfOrder.acknowledgment == 1006L && calls == 0)
    val replies = flow.accept(packet(1006, 501, 0x18, wire.copyOfRange(5, wire.size)))
    check(replies.size == 2 && calls == 1)
    check(replies[0].flags == 0x18 && replies[0].payload.copyOfRange(2, replies[0].payload.size).contentEquals(answer))
    check(replies[1].flags == 0x11 && replies[1].sequence == 501 + answer.size + 2L)
    // Wrong ACK or altered bytes cannot replay a completed DNS answer.
    check(flow.accept(packet(1006, 800, 0x18, wire.copyOfRange(5, wire.size))).isEmpty())
    val altered = wire.copyOfRange(5, wire.size).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
    check(flow.accept(packet(1006, 501, 0x18, altered)).isEmpty())
    check(flow.accept(packet(1006, 501, 0x18, wire.copyOfRange(5, wire.size))).size == 2)
    check(calls == 1) // A retransmission never repeats a DNS upstream query.
    check(flow.accept(packet(1020, replies[1].sequence + 1, DnsTcpSession.ACK)).isEmpty())
    check(!flow.isClosed) // Incorrect sequence cannot close a TCP flow.
    check(flow.accept(packet(1001 + wire.size.toLong(), replies[1].sequence + 1, DnsTcpSession.ACK)).isEmpty())
    check(!flow.isClosed) // Server FIN was ACKed, but the client has not sent FIN.
    val clientFinAck = flow.accept(packet(1001 + wire.size.toLong(),
        replies[1].sequence + 1, DnsTcpSession.FIN or DnsTcpSession.ACK)).single()
    check(clientFinAck.flags == DnsTcpSession.ACK)
    check(clientFinAck.acknowledgment == 1002 + wire.size.toLong())
    check(flow.isClosed)

    // Client FIN combined with the ACK of our FIN is answered immediately.
    val combined = DnsTcpSession(10) { answer }
    combined.accept(packet(100, 0, DnsTcpSession.SYN))
    combined.accept(packet(101, 11, DnsTcpSession.ACK))
    val combinedReplies = combined.accept(packet(101, 11, 0x18, wire))
    val serverFinAck = combined.accept(packet(101 + wire.size.toLong(),
        combinedReplies.last().sequence + 1, DnsTcpSession.FIN or DnsTcpSession.ACK)).single()
    check(serverFinAck.flags == DnsTcpSession.ACK && combined.isClosed)

    val invalid = DnsTcpSession(12) { error("Malformed DNS cannot reach upstream") }
    invalid.accept(packet(100, 0, DnsTcpSession.SYN))
    invalid.accept(packet(101, 13, DnsTcpSession.ACK))
    check(invalid.accept(packet(101, 13, 0x18, byteArrayOf(0, 11, 1, 1, 1, 1))).single().flags == 0x14)
    check(invalid.isClosed)

    val earlyFin = DnsTcpSession(7) { error("No query was provided") }
    earlyFin.accept(packet(77, 0, DnsTcpSession.SYN))
    earlyFin.accept(packet(78, 8, DnsTcpSession.ACK))
    check(earlyFin.accept(packet(78, 8, DnsTcpSession.FIN or DnsTcpSession.ACK)).single().flags == 0x11)

    val failing = DnsTcpSession(90) { null }
    failing.accept(packet(300, 0, DnsTcpSession.SYN))
    failing.accept(packet(301, 91, DnsTcpSession.ACK))
    val failReply = failing.accept(packet(301, 91, 0x18, wire))
    check(failReply.size == 2)
    val errorDns = failReply[0].payload.copyOfRange(2, failReply[0].payload.size)
    check(errorDns[3].toInt() and 15 == 2) // Explicit SERVFAIL, not fake success.

    val wrap = DnsTcpSession(0xffffffffL) { null }
    check(wrap.accept(packet(0xffffffffL, 0, DnsTcpSession.SYN)).single().acknowledgment == 0L)
    check(wrap.accept(packet(0, 0, DnsTcpSession.ACK)).isEmpty())

    val largeDns = ByteArray(1472).also {
        query.copyInto(it, 0)
        it[2] = 0x81.toByte()
    }
    val largeSession = DnsTcpSession(400) { largeDns }
    val synSegment = packet(800, 0, DnsTcpSession.SYN).copy(maximumSegmentSize = 536)
    largeSession.accept(synSegment)
    largeSession.accept(packet(801, 401, DnsTcpSession.ACK))
    val chunked = largeSession.accept(packet(801, 401, 0x18, wire))
    check(chunked.size == 4) // Three TCP data segments <=536 plus FIN.
    check(chunked.dropLast(1).all { it.payload.size <= 536 })
    println("OK: event-driven TCP session SYN/ACK, framing, replay, FIN, wrap and failures")
}
