package br.com.joaolds.dnslocal.dns

import br.com.joaolds.dnslocal.blocklist.DomainBlocklist

/** Block an ordinary IN-class DNS question locally, before upstream DNS/failover. */
internal class DnsDomainFilter(
    private val blocklist: DomainBlocklist?,
    private val upstream: (ByteArray) -> ByteArray?
) {
    fun resolve(request: ByteArray): ByteArray? {
        if (blocklist != null) {
            val question = parseQuestion(request)
            if (question != null && blocklist.blocks(question.domain)) {
                return nxdomain(request, question.end)
            }
        }
        return upstream(request)
    }

    private data class Question(val domain: String, val end: Int)

    private fun parseQuestion(data: ByteArray): Question? {
        if (data.size < 17 || u8(data, 2) and 0xf8 != 0 || u16(data, 4) != 1) return null
        var offset = 12
        val labels = ArrayList<String>(4)
        var wireLength = 0
        while (offset < data.size) {
            val length = u8(data, offset++)
            if (length == 0) break
            if (length > 63 || offset + length > data.size) return null // No pointers/compression.
            wireLength += length + 1
            if (wireLength > 253) return null
            val label = StringBuilder(length)
            for (i in offset until offset + length) {
                val char = u8(data, i).toChar()
                if (!((char in 'a'..'z') || (char in 'A'..'Z') ||
                            (char in '0'..'9') || char == '-')) return null
                label.append(char)
            }
            labels.add(label.toString())
            offset += length
        }
        if (labels.size < 2 || offset + 4 > data.size || u16(data, offset + 2) != 1) return null
        return Question(labels.joinToString("."), offset + 4)
    }

    private fun nxdomain(request: ByteArray, questionEnd: Int): ByteArray =
        request.copyOfRange(0, questionEnd).apply {
            this[2] = (0x80 or (u8(request, 2) and 1)).toByte() // QR and RD.
            this[3] = 0x83.toByte() // RA and NXDOMAIN, not a successful DNS answer.
            for (index in 6..11) this[index] = 0 // Clear ANCOUNT, NSCOUNT and ARCOUNT.
        }

    private fun u8(data: ByteArray, i: Int): Int = data[i].toInt() and 255
    private fun u16(data: ByteArray, i: Int): Int = u8(data, i) * 256 + u8(data, i + 1)
}
