package br.com.joaolds.dnslocal.dns

import java.net.Inet4Address

/**
 * Pure Kotlin policy for the optional backup resolver. Never queries a
 * secondary server while the primary succeeds, or when backup is disabled.
 */
internal class DnsFailoverResolver(
    private val ask: (ByteArray, Inet4Address) -> ByteArray?
) {
    fun resolve(
        request: ByteArray,
        primary: Inet4Address,
        secondary: Inet4Address?,
        secondaryEnabled: Boolean
    ): ByteArray? {
        val primaryReply = querySafely(request, primary)
        if (primaryReply != null) return primaryReply
        if (!secondaryEnabled || secondary == null || secondary == primary) return null
        return querySafely(request, secondary)
    }

    private fun querySafely(request: ByteArray, address: Inet4Address): ByteArray? {
        val reply = try {
            ask(request, address)
        } catch (_: Exception) {
            null
        }
        return reply?.takeIf {
            if (it.size !in 12..1472) return@takeIf false
            val rcode = it[3].toInt() and 0x0f
            DnsPacketCodec.isMatchingResponse(request, it) &&
                (it[2].toInt() and 0x02) == 0 && // TC=1 requires TCP, not supported yet.
                rcode != 2 && rcode != 5 // SERVFAIL/REFUSED allow optional fallback.
        }
    }
}
