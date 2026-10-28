package br.com.joaolds.dnslocal.dns

fun main() {
    val query = byteArrayOf(0x12,0x34,1,0,0,1,0,0,0,0,0,0,1,'a'.code.toByte(),0,0,1,0,1)
    val framed = byteArrayOf(0,query.size.toByte()) + query
    val framer = DnsTcpMessageFramer()
    check(framer.accept(framed.copyOfRange(0,1)) == null)
    check(framer.accept(framed.copyOfRange(1,4)) == null)
    check(framer.accept(framed.copyOfRange(4,13)) == null)
    check(framer.accept(framed.copyOfRange(13,framed.size))?.contentEquals(query) == true)
    check(framer.accept(framed) == null)
    val extra = DnsTcpMessageFramer()
    check(extra.accept(framed + 1) == null && extra.isInvalid)
    check(extra.accept(framed) == null)
    val huge = DnsTcpMessageFramer()
    check(huge.accept(byteArrayOf(0x06,0x00)) == null && huge.isInvalid)
    val tooShort = DnsTcpMessageFramer()
    check(tooShort.accept(byteArrayOf(0,11)) == null && tooShort.isInvalid)
    val wrongDirection = DnsTcpMessageFramer()
    check(wrongDirection.accept(framed.copyOf().also { it[4]=0x81.toByte() }) == null && wrongDirection.isInvalid)
    check(DnsTcpMessageFramer.frameResponse(ByteArray(11)) == null)
    val answer = ByteArray(1472).also { it[0] = 0x01 }
    val parts = requireNotNull(DnsTcpMessageFramer.frameResponse(answer))
    check(parts.map { it.size } == listOf(1460,14))
    val merged = parts.flatMap { it.toList() }.toByteArray()
    check(merged[0] == 0x05.toByte() && merged[1] == 0xc0.toByte())
    check(merged.copyOfRange(2,merged.size).contentEquals(answer))
    // A 1472-byte DNS query cannot fit into one 1460-byte TCP payload,
    // but is valid when two in-order segments are reassembled.
    val maximalQuery = ByteArray(1472).also { it[2] = 1 }
    val framedMaximal = byteArrayOf(0x05, 0xc0.toByte()) + maximalQuery
    val maximal = DnsTcpMessageFramer()
    check(maximal.accept(framedMaximal.copyOfRange(0, 1000)) == null)
    check(maximal.accept(framedMaximal.copyOfRange(1000, framedMaximal.size))
        ?.contentEquals(maximalQuery) == true)
    check(!maximal.isInvalid)
    check(DnsTcpPacketCodec.singleDnsQuery(framedMaximal)?.contentEquals(maximalQuery) == true)
    check(DnsTcpPacketCodec.singleDnsQuery(byteArrayOf(0x05, 0xc1.toByte()) + ByteArray(1473)) == null)
    val limited = requireNotNull(DnsTcpMessageFramer.frameResponse(answer, 536))
    check(limited.map { it.size } == listOf(536,536,402))
    check(DnsTcpMessageFramer.frameResponse(answer, 1) == null)
    println("OK: bounded TCP DNS framing and segmented reply smoke checks passed")
}
