package br.com.joaolds.dnslocal.dns

/**
 * Converts one IPv4/UDP DNS request from the TUN interface into an IP reply.
 * Pure Kotlin so the packet behavior can be tested outside Android.
 */
internal class DnsTunnelProcessor(
    private val virtualDnsIp: Int,
    private val resolve: (ByteArray) -> ByteArray?
) {
    fun reply(packet: ByteArray, length: Int): ByteArray? {
        val query = DnsPacketCodec.decodeQuery(packet, length, virtualDnsIp) ?: return null
        val upstream = try {
            resolve(query.dnsPayload)
        } catch (_: Exception) {
            null
        }
        val dnsResponse = upstream?.takeIf {
            it.size in 12..1472 && DnsPacketCodec.isMatchingResponse(query.dnsPayload, it)
        } ?: DnsPacketCodec.servfail(query.dnsPayload) ?: return null
        return DnsPacketCodec.encodeReply(query, dnsResponse)
    }
}
