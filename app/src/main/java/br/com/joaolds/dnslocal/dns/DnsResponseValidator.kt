package br.com.joaolds.dnslocal.dns

/** Confirms a UDP reply belongs to exactly the DNS question that was sent. */
internal object DnsResponseValidator {
    fun matches(query: ByteArray, response: ByteArray): Boolean {
        if (query.size < 17 || response.size < 17) return false
        if (query[0] != response[0] || query[1] != response[1]) return false
        val queryFlags = u8(query, 2)
        val responseFlags = u8(response, 2)
        if (queryFlags and 0x80 != 0 || responseFlags and 0x80 == 0) return false
        if (queryFlags and 0x78 != responseFlags and 0x78) return false
        if (word(query, 4) != 1 || word(response, 4) != 1) return false
        val queryEnd = questionEnd(query) ?: return false
        val replyEnd = questionEnd(response) ?: return false
        if (queryEnd != replyEnd) return false
        // DNS labels are case-insensitive; the QTYPE and QCLASS must match exactly.
        for (index in 12 until queryEnd) {
            val a = u8(query, index)
            val b = u8(response, index)
            if (index >= queryEnd - 4) {
                if (a != b) return false
            } else if (asciiLower(a) != asciiLower(b)) {
                return false
            }
        }
        return true
    }

    private fun questionEnd(packet: ByteArray): Int? {
        var pos = 12
        var length = 0
        while (pos < packet.size) {
            val labelLength = u8(packet, pos++)
            if (labelLength == 0) return if (pos + 4 <= packet.size) pos + 4 else null
            // Reject compressed question names, malformed labels and oversized names.
            if (labelLength > 63 || pos + labelLength > packet.size) return null
            length += labelLength + 1
            if (length > 253) return null
            pos += labelLength
        }
        return null
    }

    private fun asciiLower(value: Int): Int = if (value in 65..90) value + 32 else value
    private fun u8(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xff
    private fun word(bytes: ByteArray, index: Int): Int = (u8(bytes, index) shl 8) or u8(bytes, index + 1)
}
