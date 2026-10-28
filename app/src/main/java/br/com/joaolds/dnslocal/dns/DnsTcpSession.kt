package br.com.joaolds.dnslocal.dns

/** Event-driven single-request DNS/TCP session, invoked from the VPN's bounded TCP worker. */
internal class DnsTcpSession(
    serverInitialSequence: Long,
    private val resolve: (ByteArray) -> ByteArray?
) {
    init { require(serverInitialSequence in 0..0xffffffffL) }

    data class Reply(val sequence: Long, val acknowledgment: Long, val flags: Int, val payload: ByteArray = byteArrayOf())

    companion object {
        const val FIN = 0x01
        const val SYN = 0x02
        const val RST = 0x04
        const val PSH = 0x08
        const val ACK = 0x10
        private const val MASK = 0xffffffffL
        private fun plus(sequence: Long, amount: Int) = (sequence + amount) and MASK
    }

    private enum class Phase { WAIT_SYN, WAIT_ACK, RECEIVING, FIN_SENT, WAIT_PEER_FIN, CLOSED }
    @Volatile private var phase = Phase.WAIT_SYN
    private val framer = DnsTcpMessageFramer()
    private val initialSeq = serverInitialSequence
    private var serverNext = initialSeq
    private var clientNext = 0L
    private var originalSyn = 0L
    private var peerMss = 536
    private var peerFinSeen = false
    private var lastClientDataSequence = -1L
    private var lastClientData = byteArrayOf()
    private var finalReplies: List<Reply> = emptyList()

    val isClosed get() = phase == Phase.CLOSED

    /** Caller supplies decoded and checksum-validated in-order TCP segments. */
    @Synchronized
    fun accept(segment: DnsTcpPacketCodec.Segment): List<Reply> {
        if (phase == Phase.CLOSED) return emptyList()
        if (segment.flags and RST != 0) {
            phase = Phase.CLOSED
            return emptyList()
        }
        when (phase) {
            Phase.WAIT_SYN -> {
                if (segment.flags and SYN == 0 || segment.flags and ACK != 0 || segment.payload.isNotEmpty()) return emptyList()
                originalSyn = segment.sequence
                peerMss = segment.maximumSegmentSize.coerceIn(64,1460)
                clientNext = plus(originalSyn, 1)
                serverNext = plus(initialSeq, 1)
                phase = Phase.WAIT_ACK
                return listOf(Reply(initialSeq, clientNext, SYN or ACK))
            }
            Phase.WAIT_ACK -> {
                if (segment.flags and SYN != 0 && segment.flags and ACK == 0 && segment.sequence == originalSyn) {
                    return listOf(Reply(initialSeq, clientNext, SYN or ACK))
                }
                if (segment.flags and ACK == 0 || segment.flags and SYN != 0 ||
                    segment.sequence != clientNext || segment.acknowledgment != serverNext) return emptyList()
                phase = Phase.RECEIVING
            }
            Phase.FIN_SENT -> {
                if (segment.flags and ACK != 0 && segment.sequence == clientNext &&
                    segment.acknowledgment == serverNext) {
                    if (segment.flags and FIN != 0) {
                        clientNext = plus(clientNext, 1)
                        phase = Phase.CLOSED
                        return listOf(Reply(serverNext, clientNext, ACK))
                    }
                    if (peerFinSeen) {
                        phase = Phase.CLOSED
                    } else {
                        phase = Phase.WAIT_PEER_FIN
                    }
                    return emptyList()
                }
                // Sender can retransmit last DNS payload if response/FIN was lost.
                return if (segment.flags and ACK != 0 && segment.payload.isNotEmpty() &&
                    segment.sequence == lastClientDataSequence &&
                    segment.payload.contentEquals(lastClientData) &&
                    finalReplies.isNotEmpty() && segment.acknowledgment == finalReplies[0].sequence
                ) finalReplies else emptyList()
            }
            Phase.WAIT_PEER_FIN -> {
                if (segment.flags and ACK != 0 && segment.sequence == clientNext &&
                    segment.acknowledgment == serverNext && segment.flags and FIN != 0) {
                    clientNext = plus(clientNext, 1)
                    phase = Phase.CLOSED
                    return listOf(Reply(serverNext, clientNext, ACK))
                }
                return emptyList()
            }
            Phase.RECEIVING -> Unit
            Phase.CLOSED -> return emptyList()
        }

        if (segment.flags and ACK == 0 || segment.flags and SYN != 0 || segment.acknowledgment != serverNext) return emptyList()
        if (segment.sequence != clientNext) return listOf(Reply(serverNext, clientNext, ACK))
        if (segment.payload.isNotEmpty()) {
            lastClientDataSequence = segment.sequence
            lastClientData = segment.payload.copyOf()
            clientNext = plus(clientNext, segment.payload.size)
            val query = framer.accept(segment.payload)
            if (framer.isInvalid) {
                phase = Phase.CLOSED
                return listOf(Reply(serverNext, clientNext, RST or ACK))
            }
            if (segment.flags and FIN != 0) {
                peerFinSeen = true
                clientNext = plus(clientNext, 1)
            }
            if (query != null) {
                val candidate = try { resolve(query) } catch (_: Exception) { null }
                val response = candidate?.takeIf {
                    it.size in 12..1472 && DnsPacketCodec.isMatchingResponse(query, it) &&
                        it[2].toInt() and 2 == 0
                } ?: DnsPacketCodec.servfail(query)
                val chunks = response?.let { DnsTcpMessageFramer.frameResponse(it, peerMss) }
                if (chunks == null) {
                    phase = Phase.CLOSED
                    return listOf(Reply(serverNext, clientNext, RST or ACK))
                }
                val replies = ArrayList<Reply>(chunks.size + 1)
                for (chunk in chunks) {
                    replies.add(Reply(serverNext, clientNext, PSH or ACK, chunk))
                    serverNext = plus(serverNext, chunk.size)
                }
                replies.add(Reply(serverNext, clientNext, FIN or ACK))
                serverNext = plus(serverNext, 1)
                finalReplies = replies
                phase = Phase.FIN_SENT
                return replies
            }
            if (segment.flags and FIN != 0) return finishEarly()
            return listOf(Reply(serverNext, clientNext, ACK))
        }
        if (segment.flags and FIN != 0) {
            peerFinSeen = true
            clientNext = plus(clientNext, 1)
            return finishEarly()
        }
        return emptyList()
    }

    private fun finishEarly(): List<Reply> {
        val fin = Reply(serverNext, clientNext, FIN or ACK)
        serverNext = plus(serverNext, 1)
        finalReplies = listOf(fin)
        phase = Phase.FIN_SENT
        return finalReplies
    }
}
