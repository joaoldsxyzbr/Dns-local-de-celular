package br.com.joaolds.dnslocal.dns

/** Pure Kotlin packet handling for a DNS-only IPv4/UDP TUN tunnel. */
internal object DnsPacketCodec {
    private const val IPV4_HEADER = 20
    private const val UDP_HEADER = 8
    private const val MAX_DNS_PAYLOAD = 1472 // 1500-byte tunnel MTU - IPv4 - UDP.

    data class Query(
        val sourceIp: Int,
        val destinationIp: Int,
        val sourcePort: Int,
        val dnsPayload: ByteArray
    )

    /** IPv4 dotted-decimal literal. Never resolves hostnames over the network. */
    fun parseIpv4Address(value: String): Int? {
        val octets = value.split('.')
        if (octets.size != 4) return null
        var address = 0
        for (octet in octets) {
            if (octet.isEmpty() || octet.length > 3 || !octet.all { it in '0'..'9' }) return null
            if (octet.length > 1 && octet.startsWith('0')) return null
            val number = octet.toIntOrNull() ?: return null
            if (number !in 0..255) return null
            address = (address shl 8) or number
        }
        return address
    }

    /** Returns null for irrelevant, fragmented or unsupported TUN traffic. */
    fun decodeQuery(packet: ByteArray, length: Int, virtualDnsIp: Int): Query? {
        if (length !in 28..packet.size) return null
        val versionAndIhl = byte(packet, 0)
        if (versionAndIhl ushr 4 != 4) return null
        val headerSize = (versionAndIhl and 0x0f) * 4
        if (headerSize < IPV4_HEADER || headerSize + UDP_HEADER > length) return null
        val totalLength = word(packet, 2)
        if (totalLength < headerSize + UDP_HEADER || totalLength > length) return null
        // Reject fragments (MF flag or a nonzero offset); don't misread partial UDP.
        if (word(packet, 6) and 0x3fff != 0) return null
        if (byte(packet, 9) != 17) return null // UDP only
        val destination = int32(packet, 16)
        if (destination != virtualDnsIp) return null
        if (word(packet, headerSize + 2) != 53) return null
        val udpLength = word(packet, headerSize + 4)
        if (udpLength < UDP_HEADER + 12 || udpLength > totalLength - headerSize) return null
        val payloadSize = udpLength - UDP_HEADER
        if (payloadSize > MAX_DNS_PAYLOAD) return null
        val dnsOffset = headerSize + UDP_HEADER
        val payload = packet.copyOfRange(dnsOffset, dnsOffset + payloadSize)
        if (byte(payload, 2) and 0x80 != 0) return null // Ignore responses.
        val sourcePort = word(packet, headerSize)
        if (sourcePort == 0) return null
        return Query(int32(packet, 12), destination, sourcePort, payload)
    }

    /** Restores matching IP/UDP endpoints and calculates both checksums. */
    fun encodeReply(query: Query, dnsResponse: ByteArray): ByteArray? {
        if (dnsResponse.size !in 12..MAX_DNS_PAYLOAD) return null
        val udpLength = UDP_HEADER + dnsResponse.size
        val packet = ByteArray(IPV4_HEADER + udpLength)
        packet[0] = 0x45
        writeWord(packet, 2, packet.size)
        packet[8] = 64 // TTL
        packet[9] = 17 // UDP
        writeInt(packet, 12, query.destinationIp)
        writeInt(packet, 16, query.sourceIp)
        writeWord(packet, 20, 53)
        writeWord(packet, 22, query.sourcePort)
        writeWord(packet, 24, udpLength)
        dnsResponse.copyInto(packet, 28)
        writeWord(packet, 10, checksum(packet, 0, IPV4_HEADER))
        val sum = OnesComplementSum()
        sum.addBytes(packet, 12, 8) // IPv4 pseudo-header addresses
        sum.add(17) // Zero byte + protocol value
        sum.add(udpLength)
        sum.addBytes(packet, IPV4_HEADER, udpLength)
        val udpChecksum = sum.result()
        writeWord(packet, 26, if (udpChecksum == 0) 0xffff else udpChecksum)
        return packet
    }

    /** Minimum syntactically valid DNS error; never pretends a failure is a successful resolution. */
    fun servfail(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val questionEnd = questionEnd(query)
        val response = ByteArray(questionEnd ?: 12)
        query.copyInto(response, 0, 0, response.size)
        response[2] = (0x80 or (byte(query, 2) and 0x79)).toByte() // QR, opcode, RD
        response[3] = 2 // RCODE SERVFAIL; other flags zero
        writeWord(response, 4, if (questionEnd == null) 0 else 1)
        writeWord(response, 6, 0)
        writeWord(response, 8, 0)
        writeWord(response, 10, 0)
        return response
    }

    fun isMatchingResponse(query: ByteArray, response: ByteArray): Boolean =
        DnsResponseValidator.matches(query, response)

    private fun questionEnd(data: ByteArray): Int? {
        if (word(data, 4) != 1) return null
        var at = 12
        while (at < data.size) {
            val labelSize = byte(data, at++)
            if (labelSize == 0) return if (at + 4 <= data.size) at + 4 else null
            if (labelSize > 63 || at + labelSize > data.size) return null
            at += labelSize
        }
        return null
    }

    private fun byte(data: ByteArray, at: Int) = data[at].toInt() and 0xff
    private fun word(data: ByteArray, at: Int) = (byte(data, at) shl 8) or byte(data, at + 1)
    private fun int32(data: ByteArray, at: Int): Int =
        (word(data, at) shl 16) or word(data, at + 2)

    private fun writeWord(data: ByteArray, at: Int, value: Int) {
        data[at] = (value ushr 8).toByte()
        data[at + 1] = value.toByte()
    }
    private fun writeInt(data: ByteArray, at: Int, value: Int) {
        writeWord(data, at, value ushr 16)
        writeWord(data, at + 2, value)
    }

    private fun checksum(data: ByteArray, start: Int, length: Int): Int =
        OnesComplementSum().apply { addBytes(data, start, length) }.result()

    private class OnesComplementSum {
        private var sum = 0L
        fun add(value: Int) { sum += value.toLong() and 0xffffL }
        fun addBytes(bytes: ByteArray, start: Int, length: Int) {
            var i = start
            val end = start + length
            while (i + 1 < end) {
                add(((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff))
                i += 2
            }
            if (i < end) add((bytes[i].toInt() and 0xff) shl 8)
        }
        fun result(): Int {
            while (sum ushr 16 != 0L) sum = (sum and 0xffffL) + (sum ushr 16)
            return sum.inv().toInt() and 0xffff
        }
    }
}
