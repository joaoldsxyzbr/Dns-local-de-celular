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
    println("OK: bounded TCP DNS framing and segmented reply smoke checks passed")
}
