package br.com.joaolds.dnslocal.dns

/** Build consistent IPv4 and UDP checksums for synthetic TUN test inputs. */
internal fun recalculateIpv4UdpChecksums(packet: ByteArray): ByteArray {
    val ihl = (packet[0].toInt() and 0x0f) * 4
    val udpLength = ((packet[ihl + 4].toInt() and 255) shl 8) or (packet[ihl + 5].toInt() and 255)
    fun checksum(start: Int, end: Int, seed: Long = 0): Int {
        var sum = seed
        var i = start
        while (i < end) {
            val a = packet[i].toInt() and 255
            val b = if (i + 1 < end) packet[i + 1].toInt() and 255 else 0
            sum += (a shl 8) or b
            i += 2
        }
        while (sum ushr 16 != 0L) sum = (sum and 65535) + (sum ushr 16)
        return sum.inv().toInt() and 65535
    }
    packet[10] = 0
    packet[11] = 0
    val ipChecksum = checksum(0, ihl)
    packet[10] = (ipChecksum ushr 8).toByte()
    packet[11] = ipChecksum.toByte()
    packet[ihl + 6] = 0
    packet[ihl + 7] = 0
    var pseudo = 17L + udpLength
    for (i in 12 until 20 step 2) {
        pseudo += ((packet[i].toInt() and 255) shl 8) or (packet[i + 1].toInt() and 255)
    }
    val udpChecksum = checksum(ihl, ihl + udpLength, pseudo)
    val stored = if (udpChecksum == 0) 65535 else udpChecksum
    packet[ihl + 6] = (stored ushr 8).toByte()
    packet[ihl + 7] = stored.toByte()
    return packet
}

internal fun makeIpv4DnsTunQuery(
    source: Int, destination: Int, sourcePort: Int, payload: ByteArray
): ByteArray {
    val context = DnsPacketCodec.Query(source, destination, sourcePort, payload)
    val reply = requireNotNull(DnsPacketCodec.encodeReply(context, payload))
    val frame = reply.copyOf()
    reply.copyInto(frame, 12, 16, 20)
    reply.copyInto(frame, 16, 12, 16)
    reply.copyInto(frame, 20, 22, 24)
    reply.copyInto(frame, 22, 20, 22)
    payload.copyInto(frame, 28)
    return recalculateIpv4UdpChecksums(frame)
}
