package br.com.joaolds.dnslocal.dns

/** Execute with kotlinc + java; no Android runtime or network needed. */
fun main() {
    val virtualDns = requireNotNull(DnsPacketCodec.parseIpv4Address("10.111.0.1"))
    val device = requireNotNull(DnsPacketCodec.parseIpv4Address("10.111.0.2"))
    check(DnsPacketCodec.parseIpv4Address("1.1.1.1") != null)
    for (bad in listOf("256.1.1.1", "1.2.3", "1.2.3.4.5", "1.2.3.a", "01.2.3.4", "1.2.3.-1", "1.2.3.4.example")) {
        check(DnsPacketCodec.parseIpv4Address(bad) == null) { bad }
    }
    // example.org A query (hand-built, fixed transaction ID)
    val query = byteArrayOf(
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
        'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
        0x03, 'o'.code.toByte(), 'r'.code.toByte(), 'g'.code.toByte(),
        0x00, 0x00, 0x01, 0x00, 0x01
    )
    val fail = requireNotNull(DnsPacketCodec.servfail(query))
    check(fail.size == query.size)
    check(fail[0] == 0x12.toByte() && fail[1] == 0x34.toByte())
    check(fail[2] == 0x81.toByte() && fail[3] == 2.toByte())
    check(DnsPacketCodec.isMatchingResponse(query, fail))
    check(!DnsPacketCodec.isMatchingResponse(query, query))
    check(DnsPacketCodec.servfail(byteArrayOf(1, 2)) == null)
    val malformed = query.copyOf().also { it[12] = 0xc0.toByte() }
    check(DnsPacketCodec.servfail(malformed)?.size == 12)

    // Encode DNS packet as if returning it from upstream, then reverse src/dst into a TUN query.
    val outgoing = DnsPacketCodec.Query(device, virtualDns, 54001, query)
    val answer = requireNotNull(DnsPacketCodec.encodeReply(outgoing, fail))
    check(answer.size == 28 + fail.size)
    check((answer[0].toInt() and 0xff) == 0x45)
    check((answer[9].toInt() and 0xff) == 17)
    check(answer.sliceArray(12..15).contentEquals(byteArrayOf(10, 111, 0, 1)))
    check(answer.sliceArray(16..19).contentEquals(byteArrayOf(10, 111, 0, 2)))
    check((answer[20].toInt() and 0xff) == 0 && (answer[21].toInt() and 0xff) == 53)
    check(checksum(answer, 0, 20) == 0) { "Invalid IPv4 checksum" }
    val pseudo = answer.sliceArray(12..19) + byteArrayOf(0, 17) + answer.sliceArray(24..25) + answer.sliceArray(20 until answer.size)
    check(checksum(pseudo, 0, pseudo.size) == 0) { "Invalid UDP checksum" }

    val input = answer.copyOf()
    // Switch IPs/ports to emulate an incoming query rather than a DNS reply.
    answer.copyInto(input, 12, 16, 20)
    answer.copyInto(input, 16, 12, 16)
    answer.copyInto(input, 20, 22, 24)
    answer.copyInto(input, 22, 20, 22)
    query.copyInto(input, 28)
    val parsed = requireNotNull(DnsPacketCodec.decodeQuery(input, input.size, virtualDns))
    check(parsed.sourceIp == device && parsed.destinationIp == virtualDns)
    check(parsed.sourcePort == 54001 && parsed.dnsPayload.contentEquals(query))
    check(DnsPacketCodec.decodeQuery(input, 10, virtualDns) == null)
    check(DnsPacketCodec.decodeQuery(input, input.size, device) == null)
    check(DnsPacketCodec.decodeQuery(input.copyOf().also { it[9] = 6 }, input.size, virtualDns) == null)
    check(DnsPacketCodec.decodeQuery(input.copyOf().also { it[6] = 0x20 }, input.size, virtualDns) == null)
    check(DnsPacketCodec.decodeQuery(input.copyOf().also { it[22] = 0x01 }, input.size, virtualDns) == null)
    check(DnsPacketCodec.decodeQuery(input.copyOf().also { it[2] = 0x7f }, input.size, virtualDns) == null)
    check(DnsPacketCodec.encodeReply(outgoing, ByteArray(1473)) == null)
    tunnelSmoke()
    upstreamSmoke()
    println("OK: DNS packet codec smoke checks passed")
}

private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
    var sum = 0L
    var i = offset
    while (i < offset + length) {
        val b1 = bytes[i].toInt() and 0xff
        val b2 = if (i + 1 < offset + length) bytes[i + 1].toInt() and 0xff else 0
        sum += ((b1 shl 8) or b2).toLong()
        i += 2
    }
    while (sum > 0xffffL) sum = (sum and 0xffffL) + (sum shr 16)
    return (sum.inv().toInt() and 0xffff)
}
