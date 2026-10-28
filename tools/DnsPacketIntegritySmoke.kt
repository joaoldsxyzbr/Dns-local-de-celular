package br.com.joaolds.dnslocal.dns

fun packetIntegritySmoke() {
    val device = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.2"))
    val dns = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.1"))
    val query = byteArrayOf(
        0x53, 0x20, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0,
        3, 'a'.code.toByte(), 'd'.code.toByte(), 's'.code.toByte(),
        7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
        'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
        3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0, 0, 1, 0, 1
    )
    val frame = makeIpv4DnsTunQuery(device, dns, 49312, query)
    check(DnsPacketCodec.decodeQuery(frame, frame.size, dns)?.dnsPayload?.contentEquals(query) == true)
    val tamperedIp = frame.copyOf().also { it[8] = 1 }
    check(DnsPacketCodec.decodeQuery(tamperedIp, tamperedIp.size, dns) == null)
    val tamperedUdp = frame.copyOf().also { it[28 + 14] = 'z'.code.toByte() }
    check(DnsPacketCodec.decodeQuery(tamperedUdp, tamperedUdp.size, dns) == null)
    val noUdpChecksum = frame.copyOf().also { it[26] = 0; it[27] = 0 }
    check(DnsPacketCodec.decodeQuery(noUdpChecksum, noUdpChecksum.size, dns) != null) // IPv4 permits 0.
    val changedChecksum = frame.copyOf().also { it[26] = (it[26].toInt() xor 1).toByte() }
    check(DnsPacketCodec.decodeQuery(changedChecksum, changedChecksum.size, dns) == null)
    // A short UDP datagram inside a larger IPv4 payload is not a valid DNS frame.
    // Recalculate its UDP checksum so the rejection tests length, not corruption.
    val shortenedUdp = frame.copyOf().also {
        val shortLength = frame.size - 20 - 1
        it[24] = (shortLength ushr 8).toByte()
        it[25] = shortLength.toByte()
        recalculateIpv4UdpChecksums(it)
    }
    check(DnsPacketCodec.decodeQuery(shortenedUdp, shortenedUdp.size, dns) == null)
    val trailingBytes = frame + byteArrayOf(1, 2, 3)
    check(DnsPacketCodec.decodeQuery(trailingBytes, trailingBytes.size, dns) == null)
    val oddFrame = makeIpv4DnsTunQuery(device, dns, 49312, query + 0.toByte())
    check(DnsPacketCodec.decodeQuery(oddFrame, oddFrame.size, dns) != null)
    check(DnsPacketCodec.decodeQuery(
        oddFrame.copyOf().also { it[it.lastIndex] = 9 }, oddFrame.size, dns
    ) == null)
    println("OK: IPv4/UDP incoming checksum integrity smoke checks passed")
}
