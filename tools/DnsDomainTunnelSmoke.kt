package br.com.joaolds.dnslocal.dns

import br.com.joaolds.dnslocal.blocklist.DomainBlocklist
import java.io.StringReader

fun main() {
    val dns = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.1"))
    val device = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.2"))
    val request = byteArrayOf(
        0x44, 0x44, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        3, 'a'.code.toByte(), 'd'.code.toByte(), 's'.code.toByte(),
        7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
        'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
        3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
        0, 0, 1, 0, 1
    )
    val context = DnsPacketCodec.Query(device, dns, 50100, request)
    val sampleReply = requireNotNull(DnsPacketCodec.servfail(request))
    val replyFrame = requireNotNull(DnsPacketCodec.encodeReply(context, sampleReply))
    val frame = replyFrame.copyOf()
    replyFrame.copyInto(frame, 12, 16, 20)
    replyFrame.copyInto(frame, 16, 12, 16)
    replyFrame.copyInto(frame, 20, 22, 24)
    replyFrame.copyInto(frame, 22, 20, 22)
    request.copyInto(frame, 28)
    val list = DomainBlocklist.parse(StringReader("ads.example.com\n"))
    var upstreamCalls = 0
    val filtered = DnsDomainFilter(list) { upstreamCalls++; sampleReply }
    val processor = DnsTunnelProcessor(dns, filtered::resolve)
    val result = requireNotNull(processor.reply(frame, frame.size))
    check(result.size == replyFrame.size)
    check(result[31].toInt() and 0xf == 3) // DNS RCODE is NXDOMAIN.
    check(upstreamCalls == 0) // No primary OR secondary network call.
    val disabled = DnsTunnelProcessor(dns, DnsDomainFilter(null) { upstreamCalls++; sampleReply }::resolve)
    val allowedResult = requireNotNull(disabled.reply(frame, frame.size))
    check(allowedResult.contentEquals(replyFrame))
    check(upstreamCalls == 1)
    println("OK: end-to-end TUN block response checks passed")
}
