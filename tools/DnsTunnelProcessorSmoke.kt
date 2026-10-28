package br.com.joaolds.dnslocal.dns

/** End-to-end packet translation without Android/VPN, using an upstream stub. */
fun tunnelSmoke() {
    val local = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.2"))
    val virtual = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.1"))
    val request = byteArrayOf(
        0x33, 0x55, 0x01, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0,
        0x01, 'b'.code.toByte(), 0, 0, 1, 0, 1
    )
    val requestContext = DnsPacketCodec.Query(local, virtual, 49200, request)
    val sampleResponse = requireNotNull(DnsPacketCodec.servfail(request))
    val outgoingReply = requireNotNull(DnsPacketCodec.encodeReply(requestContext, sampleResponse))
    // Flip endpoints back and replace DNS response with the original request,
    // simulating a DNS query read from the Android TUN device.
    val queryFrame = outgoingReply.copyOf()
    outgoingReply.copyInto(queryFrame, 12, 16, 20)
    outgoingReply.copyInto(queryFrame, 16, 12, 16)
    outgoingReply.copyInto(queryFrame, 20, 22, 24)
    outgoingReply.copyInto(queryFrame, 22, 20, 22)
    request.copyInto(queryFrame, 28)
    recalculateIpv4UdpChecksums(queryFrame)

    var calls = 0
    val processor = DnsTunnelProcessor(virtual) { payload ->
        calls += 1
        check(payload.contentEquals(request))
        sampleResponse
    }
    val actual = requireNotNull(processor.reply(queryFrame, queryFrame.size))
    check(actual.contentEquals(outgoingReply))
    check(calls == 1)
    check(processor.reply(queryFrame, 4) == null)
    check(calls == 1) // An invalid packet must not reach upstream.
    val wrongDestination = DnsTunnelProcessor(local) { error("must not query upstream") }
    check(wrongDestination.reply(queryFrame, queryFrame.size) == null)

    val failed = DnsTunnelProcessor(virtual) { null }
    val fallback = requireNotNull(failed.reply(queryFrame, queryFrame.size))
    check(fallback.contentEquals(outgoingReply)) // SERVFAIL: no silent bypass.
    val wrongId = DnsTunnelProcessor(virtual) { sampleResponse.copyOf().also { it[0] = 0x10 } }
    check(requireNotNull(wrongId.reply(queryFrame, queryFrame.size)).contentEquals(outgoingReply))
    val oversized = DnsTunnelProcessor(virtual) { sampleResponse + ByteArray(1473) }
    check(requireNotNull(oversized.reply(queryFrame, queryFrame.size)).contentEquals(outgoingReply))
    val exception = DnsTunnelProcessor(virtual) { throw IllegalStateException("upstream error") }
    check(requireNotNull(exception.reply(queryFrame, queryFrame.size)).contentEquals(outgoingReply))
    println("OK: DNS tunnel processor smoke checks passed")
}
