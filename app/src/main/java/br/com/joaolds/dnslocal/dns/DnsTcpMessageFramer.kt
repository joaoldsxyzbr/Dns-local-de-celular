package br.com.joaolds.dnslocal.dns

/** Bounded DNS length-prefix handling; not a TCP transport/session by itself. */
internal class DnsTcpMessageFramer(private val maxDnsBytes: Int = 1472) {
    init { require(maxDnsBytes in 12..1472) }
    private val data = ByteArray(maxDnsBytes + 2)
    private var collected = 0
    private var finished = false
    private var invalid = false

    /** Accept in-order TCP payload chunks. Emits at most one complete query. */
    fun accept(chunk: ByteArray): ByteArray? {
        if (finished || invalid) return null
        if (chunk.size > data.size - collected) { invalid = true; return null }
        chunk.copyInto(data, collected)
        collected += chunk.size
        if (collected < 2) return null
        val dnsLength = ((data[0].toInt() and 255) shl 8) or (data[1].toInt() and 255)
        if (dnsLength !in 12..maxDnsBytes || collected > dnsLength + 2) {
            invalid = true
            return null
        }
        if (collected != dnsLength + 2) return null
        val query = DnsTcpPacketCodec.singleDnsQuery(data.copyOf(collected))
        if (query == null) invalid = true else finished = true
        return query
    }

    val isInvalid: Boolean get() = invalid

    companion object {
        /** Split a framed DNS reply into TCP-sized chunks, without exceeding IPv4 MTU 1500. */
        fun frameResponse(response: ByteArray): List<ByteArray>? {
            if (response.size !in 12..1472) return null
            val framed = ByteArray(response.size + 2)
            framed[0] = (response.size ushr 8).toByte()
            framed[1] = response.size.toByte()
            response.copyInto(framed, 2)
            return (framed.indices step 1460).map { offset ->
                framed.copyOfRange(offset, minOf(offset + 1460, framed.size))
            }
        }
    }
}
