package br.com.joaolds.dnslocal.dns

import java.security.SecureRandom

/** Bounded, event-driven collection of one-question TCP/53 sessions. */
internal class DnsTcpTunnelProcessor(
    private val virtualDnsIp: Int,
    private val resolve: (ByteArray) -> ByteArray?,
    private val maximumSessions: Int = 64,
    private val idleNanos: Long = 60_000_000_000L,
    private val nowNanos: () -> Long = System::nanoTime,
    private val initialSequence: () -> Long = {
        SecureRandomHolder.value.nextInt().toLong() and 0xffffffffL
    }
) {
    init {
        require(maximumSessions in 1..256)
        require(idleNanos > 0)
    }

    private object SecureRandomHolder { val value = SecureRandom() }
    private data class Key(val sourceIp: Int, val sourcePort: Int, val destinationIp: Int)
    private data class Entry(val session: DnsTcpSession, val synSequence: Long, var lastSeen: Long)
    private val sessions = LinkedHashMap<Key, Entry>(16, 0.75f, true)

    /** Decode only a valid IPv4/TCP packet to virtual DNS/53. Returns TCP reply packets. */
    fun reply(packet: ByteArray, length: Int): List<ByteArray> {
        val segment = DnsTcpPacketCodec.decode(packet, length, virtualDnsIp) ?: return emptyList()
        return handle(segment)
    }

    /** Explicit packet event; no polling or scheduled expiry. */
    internal fun handle(segment: DnsTcpPacketCodec.Segment): List<ByteArray> {
        if (segment.destinationIp != virtualDnsIp || segment.sourcePort == 0) return emptyList()
        val key = Key(segment.sourceIp, segment.sourcePort, segment.destinationIp)
        val current = nowNanos()
        val syn = segment.flags and DnsTcpSession.SYN != 0 &&
            segment.flags and DnsTcpSession.ACK == 0 && segment.payload.isEmpty()
        val entry = synchronized(sessions) {
            // Passive expiry only on incoming packets; at most maximumSessions entries.
            val iterator = sessions.entries.iterator()
            while (iterator.hasNext()) {
                if (current - iterator.next().value.lastSeen >= idleNanos) iterator.remove()
            }
            var found = sessions[key]
            if (syn && found != null && found.synSequence != segment.sequence) {
                sessions.remove(key)
                found = null
            }
            if (found == null && syn) {
                if (sessions.size == maximumSessions) {
                    val oldest = sessions.entries.iterator()
                    if (oldest.hasNext()) { oldest.next(); oldest.remove() }
                }
                found = Entry(DnsTcpSession(initialSequence(), resolve), segment.sequence, current)
                sessions[key] = found
            }
            if (found != null) found.lastSeen = current
            found
        } ?: return emptyList()
        val actions = entry.session.accept(segment)
        synchronized(sessions) {
            if (sessions[key] === entry && entry.session.isClosed) sessions.remove(key)
        }
        return actions.mapNotNull { action ->
            DnsTcpPacketCodec.encodeReply(
                segment, action.sequence, action.acknowledgment, action.flags, action.payload
            )
        }
    }

    internal fun currentSessionCount(): Int = synchronized(sessions) { sessions.size }
}
